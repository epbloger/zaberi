package ru.epbloger.zaberi
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val errors = MutableSharedFlow<String>(extraBufferCapacity=1)
    private fun change(block: suspend () -> Unit) = viewModelScope.launch {
        try { db.withTransaction { block() }; Reminders.enqueue(getApplication()) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { errors.emit(e.message ?: "Не удалось сохранить") }
    }
    private suspend fun log(id: String, label: String) = dao.history(History(uid(),id,LocalDateTime.now().toString(),label))
    private suspend fun rememberValues(o:Order){
        dao.remember(CatalogEntry(uid(),"store","",o.store))
        if(o.point.isNotBlank())dao.remember(CatalogEntry(uid(),"point",o.store,o.point))
        if(o.category.isNotBlank())dao.remember(CatalogEntry(uid(),"category","",o.category))
    }
    fun addCatalog(kind:String,parent:String,value:String)=change{
        require(kind in listOf("store","point","category")&&value.isNotBlank()&&value.length<=200)
        require(kind!="point"||parent.isNotBlank())
        dao.remember(CatalogEntry(uid(),kind,parent.trim(),value.trim()))
    }
    fun forgetCatalog(id:String)=change{dao.forget(id)}
    fun add(order: Order) = change {
        require(order.name.isNotBlank() && order.store.isNotBlank())
        dao.insert(order)
        rememberValues(order)
        if (order.paid > 0) dao.transaction(Transaction(uid(),order.id,today(),order.paid))
        log(order.id,"Добавлен: ${order.state.label}")
    }
    fun edit(id: String, name: String, store: String, price: Long?, deadline: String?, point: String, link: String, note: String, category:String) = change {
        val o = dao.get(id) ?: error("Заказ не найден")
        require(name.isNotBlank() && store.isNotBlank())
        require(price == null || price >= o.paid) { "Цена не может быть меньше оплаченной суммы" }
        val updated=o.copy(name=name,store=store,price=price,deadline=if(o.state.deadlineLabel!=null) deadline else null,point=point,link=link,note=note,category=category)
        dao.update(updated);rememberValues(updated)
        log(id,"Изменены сведения")
        getApplication<Application>().getSystemService(android.app.NotificationManager::class.java).cancel(id.hashCode())
    }
    fun act(id: String, event: String, deadline: String? = null, amount: Long? = null) = change {
        val o = dao.get(id) ?: error("Заказ не найден")
        require(event !in listOf("return","reopen") || !o.returned && !o.cancelled) { "Возврат уже завершён" }
        val state = nextStatus(o.state,event,o.paid)
        val expected = if(state == Status.REFUND) (amount ?: o.paid) else 0L
        require(expected in 0..o.paid) { "Сумма возврата превышает оплату" }
        val actualState = if(state==Status.REFUND && expected==0L) (if(event=="cancel") Status.CANCELLED else Status.DONE) else state
        dao.update(o.copy(status=actualState.name, deadline=if(actualState.deadlineLabel!=null) deadline else null, expectedRefund=expected, receivedRefund=0, cancelled=event=="cancel", returned=o.returned || event=="returned"))
        log(id,actualState.label)
        getApplication<Application>().getSystemService(android.app.NotificationManager::class.java).cancel(id.hashCode())
    }
    fun pay(id: String, amount: Long, operationDate:String=today()) = change {
        val o = dao.get(id) ?: error("Заказ не найден")
        require(canMarkPayment(o.state,o.paid,o.returned,o.cancelled)&&amount>0) { "Оплата недоступна" }
        require(validOperationDate(operationDate,LocalDate.now())) { "Проверьте дату оплаты" }
        require(o.price==null || amount==o.price) { "Укажите полную стоимость заказа" }
        dao.update(o.copy(paid=amount,price=o.price?:amount))
        dao.transaction(Transaction(uid(),id,operationDate,amount)); log(id,"Оплачен: "+operationDate)
    }
    fun refund(id: String, amount: Long, operationDate:String=today()) = change {
        val o = dao.get(id) ?: error("Заказ не найден")
        require(o.state==Status.REFUND && amount>0 && amount<=remaining(o.expectedRefund,o.receivedRefund)) { "Проверьте сумму возврата" }
        require(validOperationDate(operationDate,LocalDate.now())) { "Проверьте дату возврата" }
        val received=o.receivedRefund+amount
        val state=if(received==o.expectedRefund) (if(o.cancelled) Status.CANCELLED else Status.DONE) else Status.REFUND
        dao.update(o.copy(receivedRefund=received,status=state.name,deadline=if(state==Status.REFUND) o.deadline else null))
        dao.transaction(Transaction(uid(),id,operationDate,-amount)); log(id,if(state==Status.REFUND) "Частичный возврат" else "Возврат завершён")
        if(state!=Status.REFUND)getApplication<Application>().getSystemService(android.app.NotificationManager::class.java).cancel(id.hashCode())
    }
    fun adjustRefund(id:String,target:Long,deadline:String?)=change{
        val o=dao.get(id)?:error("Заказ не найден")
        require(o.state==Status.REFUND&&refundTargetValid(o.paid,o.receivedRefund,target)){"Укажите сумму не меньше полученной и не больше оплаты"}
        deadline?.let{LocalDate.parse(it)}
        val state=if(target==o.receivedRefund){if(o.cancelled)Status.CANCELLED else Status.DONE}else Status.REFUND
        dao.update(o.copy(expectedRefund=target,status=state.name,deadline=if(state==Status.REFUND)deadline else null))
        log(id,"Уточнена сумма ожидаемого возврата")
        getApplication<Application>().getSystemService(android.app.NotificationManager::class.java).cancel(id.hashCode())
    }
    fun clearData()=change{
        dao.clearOrders()
        dao.clearCatalog()
        val app=getApplication<Application>()
        app.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        app.getSharedPreferences("reminders",android.content.Context.MODE_PRIVATE).edit().clear().apply()
    }
    fun photos(id:String)=dao.photos(id)
    fun addPhoto(id:String,uri:android.net.Uri)=viewModelScope.launch{
        try{PrivateFiles.addPhoto(getApplication(),id,uri)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){errors.emit("Не удалось добавить фото: "+(e.message?:"ошибка"))}
    }
    fun deletePhoto(id:String)=change{dao.removePhoto(id)}
    fun remove(id: String) = change { dao.get(id)?.let { dao.delete(it) };getApplication<Application>().getSystemService(android.app.NotificationManager::class.java).cancel(id.hashCode()) }
}