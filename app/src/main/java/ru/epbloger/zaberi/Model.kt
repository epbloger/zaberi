package ru.epbloger.zaberi
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
fun uid() = UUID.randomUUID().toString()
fun today() = LocalDate.now().toString()
class Model(app: Application): AndroidViewModel(app) {
    private val db = ZaberiDb.get(app)
    private val dao = db.orders()
    val orders = dao.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val transactions = dao.transactions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = dao.history().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val catalog = dao.catalog().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val errorChannel = Channel<String>(Channel.BUFFERED)
    val errors = errorChannel.receiveAsFlow()
    private val _formSaving = MutableStateFlow(false)
    val formSaving = _formSaving.asStateFlow()
    private val _savedForm = MutableStateFlow<String?>(null)
    val savedForm = _savedForm.asStateFlow()
    private val _pendingShare=MutableStateFlow<SharedProductRequest?>(null)
    internal val pendingShare=_pendingShare.asStateFlow()
    private val _productImport=MutableStateFlow<ProductImportState?>(null)
    internal val productImport=_productImport.asStateFlow()
    private var importJob:Job?=null
    internal var lookupProductDetails:suspend (SharedProductSeed,Boolean)->ProductLookup=ProductImporter::lookup
    internal fun receiveShare(request:SharedProductRequest){_pendingShare.value=request}
    internal fun dismissShare(id:String){_pendingShare.value?.takeIf{it.id==id}?.let{_pendingShare.compareAndSet(it,null)}}
    internal fun clearProductImport(id:String?){
        if(id!=null&&_productImport.value?.requestId==id){
            importJob?.cancel();importJob=null;_productImport.value=null
        }
    }
    internal fun cancelProductImport(id:String){
        val state=_productImport.value?.takeIf{it.requestId==id&&it.loading}?:return
        importJob?.cancel();importJob=null
        _productImport.value=state.copy(loading=false,attempted=true,message="Загрузка остановлена. Можно сохранить заказ без недостающих сведений.")
    }
    internal fun restoreProductImport(id:String,text:String){
        if(_productImport.value!=null)return
        _productImport.value=ProductImportState(id,sharedProductSeed(text),message="Черновик восстановлен после перезапуска. Несохранённое фото нужно загрузить заново или добавить после сохранения.")
    }
    internal fun acceptShare(id:String,online:Boolean):SharedProductRequest?{
        val request=_pendingShare.value?.takeIf{it.id==id}?:return null
        if(!_pendingShare.compareAndSet(request,null))return null
        importJob?.cancel()
        val seed=sharedProductSeed(request.text)
        val fetchOnline=online&&publicProductUrl(seed.link,seed.store)!=null
        _productImport.value=ProductImportState(id,seed,loading=request.image!=null||fetchOnline)
        importJob=viewModelScope.launch{
            val photo=try{ProductImporter.readSharedImage(getApplication(),request)}
                catch(e:CancellationException){throw e}catch(_:Exception){null}
            val current=_productImport.value?.takeIf{it.requestId==id}?:return@launch
            _productImport.value=current.copy(photo=photo,loading=fetchOnline,message=if(request.image!=null&&photo==null)"Не удалось открыть переданное фото. Можно добавить его вручную." else null)
            if(fetchOnline)lookupProduct(id)
        }
        return request
    }
    internal fun enrichSharedProduct(id:String){
        val state=_productImport.value?.takeIf{it.requestId==id&&!it.loading}?:return
        if(publicProductUrl(state.seed.link,state.seed.store)==null)return
        importJob?.cancel()
        importJob=viewModelScope.launch{lookupProduct(id)}
    }
    private suspend fun lookupProduct(id:String){
        val state=_productImport.value?.takeIf{it.requestId==id}?:return
        if(publicProductUrl(state.seed.link,state.seed.store)==null)return
        if(state.seed.name.isNotBlank()&&state.seed.price!=null&&state.photo!=null){
            _productImport.value=state.copy(loading=false)
            return
        }
        _productImport.value=state.copy(loading=true,attempted=true,message=null)
        val result=try{lookupProductDetails(state.seed,state.photo==null)}
            catch(e:CancellationException){throw e}
            catch(_:Exception){ProductLookup(null,null,null,"Не удалось загрузить сведения. Можно продолжить вручную.")}
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val current=_productImport.value?.takeIf{it.requestId==id}?:return
        _productImport.value=current.copy(title=result.title,price=result.price,photo=current.photo?:result.photo,loading=false,message=result.warning)
    }

    // Form save state belongs to the ViewModel, not to a particular Activity composition.
    fun saveForm(order: Order, editing: Boolean, importedPhoto:ByteArray?=null) {
        if (!_formSaving.compareAndSet(false, true)) return
        val complete: (Boolean) -> Unit = { ok ->
            if (ok) _savedForm.value = order.id
            _formSaving.value = false
        }
        if (editing) edit(order.id, order.name, order.store, order.price, order.deadline,
            order.point, order.link, order.note, order.category, order.pointLat, order.pointLon, complete)
        else addWithPhoto(order,importedPhoto,complete)
    }

    fun consumeSavedForm(id: String) { _savedForm.compareAndSet(id, null) }

    private fun reportError(message: String) { errorChannel.trySend(message) }
    private fun cancelReminder(id: String) {
        getApplication<Application>().getSystemService(android.app.NotificationManager::class.java)?.cancel(id, 0)
    }

    /** Database mutations are atomic; system notifications never cause a rollback. */
    private fun change(
        completed: ((Boolean) -> Unit)? = null,
        afterCommit: () -> Unit = {},
        block: suspend () -> Unit
    ) = viewModelScope.launch {
        reminderMutationMutex.withLock {
        try {
            db.withTransaction { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            completed?.invoke(false)
            reportError(if (e is IllegalArgumentException) e.message ?: "Проверьте введённые данные" else "Не удалось сохранить данные. Попробуйте снова.")
            return@withLock
        }
        completed?.invoke(true)
        try {
            afterCommit()
            Reminders.enqueue(getApplication())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportError("Данные сохранены, но не удалось обновить напоминания")
        }
        }
    }
    private suspend fun log(id: String, label: String) = dao.history(History(uid(),id,LocalDateTime.now().toString(),label))
    private suspend fun rememberValues(o:Order){
        dao.remember(CatalogEntry(uid(),"store","",o.store))
        if(o.point.isNotBlank()){
            dao.remember(CatalogEntry(uid(),"point",o.store,o.point,o.pointLat,o.pointLon))
            if(o.pointLat!=null&&o.pointLon!=null)dao.updatePoint(o.store,o.point,o.pointLat,o.pointLon)
        }
        if(o.category.isNotBlank())dao.remember(CatalogEntry(uid(),"category","",o.category))
    }
    fun addCatalog(kind:String,parent:String,value:String,lat:Double?=null,lon:Double?=null)=change{
        require(validPoint(lat,lon)&&(kind=="point"||lat==null))
        require(kind in listOf("store","point","category")&&value.isNotBlank()&&value.length<=200)
        require(kind!="point"||parent.isNotBlank())
        dao.remember(CatalogEntry(uid(),kind,parent.trim(),value.trim(),lat,lon))
        if(kind=="point"&&lat!=null&&lon!=null)dao.updatePoint(parent.trim(),value.trim(),lat,lon)
    }
    fun forgetCatalog(id:String)=change{dao.forget(id)}
    fun add(order: Order,completed:((Boolean)->Unit)?=null) = addWithPhoto(order,null,completed)
    private fun addWithPhoto(order:Order,photo:ByteArray?,completed:((Boolean)->Unit)?=null)=change(completed) {
        validateNewOrder(order)
        val existing = dao.get(order.id)
        if (existing != null) {
            require(existing == order) { "Заказ уже сохранён. Откройте его из списка." }
            return@change
        }
        dao.insert(order)
        if(photo!=null)PrivateFiles.attachPreparedPhoto(dao,order.id,photo)
        rememberValues(order)
        if (order.paid > 0) dao.transaction(Transaction(uid(),order.id,today(),order.paid))
        log(order.id,"Добавлен: ${order.state.label}")
    }
    fun edit(id: String, name: String, store: String, price: Long?, deadline: String?, point: String, link: String, note: String, category:String,pointLat:Double?=null,pointLon:Double?=null,completed:((Boolean)->Unit)?=null) = change(completed, afterCommit={cancelReminder(id)}) {
        require(validPoint(pointLat,pointLon)&&(pointLat==null||point.isNotBlank()))
        val o = dao.get(id) ?: error("Заказ не найден")
        require(name.isNotBlank() && store.isNotBlank())
        require(price == null || price >= o.paid) { "Цена не может быть меньше оплаченной суммы" }
        val updated=o.copy(name=name,store=store,price=price,deadline=if(o.state.deadlineLabel!=null) deadline else null,point=point,link=link,note=note,category=category,pointLat=pointLat,pointLon=pointLon)
        validateOrder(updated)
        dao.update(updated);rememberValues(updated)
        log(id,"Изменены сведения")
    }
    fun act(id: String, event: String, deadline: String? = null, amount: Long? = null) = change(afterCommit={cancelReminder(id)}) {
        deadline?.let{LocalDate.parse(it)}
        val o = dao.get(id) ?: error("Заказ не найден")
        require(event !in listOf("return","reopen") || !o.returned && !o.cancelled) { "Возврат уже завершён" }
        val state = nextStatus(o.state,event,o.paid)
        val expected = if(state == Status.REFUND) (amount ?: o.paid) else 0L
        require(expected in 0..o.paid) { "Сумма возврата превышает оплату" }
        val actualState = if(state==Status.REFUND && expected==0L) (if(event=="cancel") Status.CANCELLED else Status.DONE) else state
        dao.update(o.copy(status=actualState.name, deadline=if(actualState.deadlineLabel!=null) deadline else null, expectedRefund=expected, receivedRefund=0, cancelled=event=="cancel", returned=o.returned || event=="returned"))
        log(id,actualState.label)
    }
    fun pay(id: String, amount: Long, operationDate:String=today()) = change {
        val o = dao.get(id) ?: error("Заказ не найден")
        require(canMarkPayment(o.state,o.paid,o.returned,o.cancelled)&&amount in 1..MAX_ORDER_AMOUNT) { "Оплата недоступна" }
        require(validOperationDate(operationDate,LocalDate.now())) { "Проверьте дату оплаты" }
        require(o.price==null || amount==o.price) { "Укажите полную стоимость заказа" }
        dao.update(o.copy(paid=amount,price=o.price?:amount))
        dao.transaction(Transaction(uid(),id,operationDate,amount)); log(id,"Оплачен: "+operationDate)
    }
    fun refund(id: String, amount: Long, operationDate:String=today()) = change(afterCommit={cancelReminder(id)}) {
        val o = dao.get(id) ?: error("Заказ не найден")
        require(o.state==Status.REFUND && amount>0 && amount<=remaining(o.expectedRefund,o.receivedRefund)) { "Проверьте сумму возврата" }
        require(validOperationDate(operationDate,LocalDate.now())) { "Проверьте дату возврата" }
        val received=o.receivedRefund+amount
        val state=if(received==o.expectedRefund) (if(o.cancelled) Status.CANCELLED else Status.DONE) else Status.REFUND
        dao.update(o.copy(receivedRefund=received,status=state.name,deadline=if(state==Status.REFUND) o.deadline else null))
        dao.transaction(Transaction(uid(),id,operationDate,-amount)); log(id,if(state==Status.REFUND) "Частичный возврат" else "Возврат завершён")
    }
    fun adjustRefund(id:String,target:Long,deadline:String?)=change(afterCommit={cancelReminder(id)}){
        val o=dao.get(id)?:error("Заказ не найден")
        require(o.state==Status.REFUND&&refundTargetValid(o.paid,o.receivedRefund,target)){"Укажите сумму не меньше полученной и не больше оплаты"}
        deadline?.let{LocalDate.parse(it)}
        val state=if(target==o.receivedRefund){if(o.cancelled)Status.CANCELLED else Status.DONE}else Status.REFUND
        dao.update(o.copy(expectedRefund=target,status=state.name,deadline=if(state==Status.REFUND)deadline else null))
        log(id,"Уточнена сумма ожидаемого возврата")
    }
    fun clearData()=change(afterCommit={
        val app=getApplication<Application>()
        app.getSystemService(android.app.NotificationManager::class.java)?.cancelAll()
        app.getSharedPreferences("reminders",android.content.Context.MODE_PRIVATE).edit().clear().apply()
    }){
        dao.clearOrders()
        dao.clearCatalog()
    }
    fun photos(id:String)=dao.photos(id)
    internal fun cover(id:String)=dao.cover(id)
    fun addPhoto(id:String,uri:android.net.Uri)=viewModelScope.launch{
        try{PrivateFiles.addPhoto(getApplication(),id,uri)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){reportError("Не удалось добавить фото: "+(e.message?:"ошибка"))}
    }
    fun deletePhoto(id:String)=change{dao.removePhoto(id)}
    fun remove(id: String, completed: ((Boolean) -> Unit)? = null) = change(completed=completed,afterCommit={cancelReminder(id)}) { dao.get(id)?.let { dao.delete(it) } }
}