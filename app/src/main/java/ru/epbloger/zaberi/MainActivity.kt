package ru.epbloger.zaberi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Brand=Color(0xFF5366EB)
private val stores=listOf("Ozon","Wildberries","Яндекс Маркет","Мегамаркет","Авито","AliExpress","Lamoda","DNS")
private fun money(n:Long?)=n?.let{java.text.NumberFormat.getCurrencyInstance(Locale("ru","RU")).format(java.math.BigDecimal.valueOf(it,2))}?:"Цена не указана"
private fun number(n:Long?)=n?.let{java.math.BigDecimal.valueOf(it,2).toPlainString()}.orEmpty()
private fun dateText(s:String?)=s?.let{runCatching{LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale("ru")))}.getOrDefault(it)}?:"Не указан"
private fun validDate(s:String)=s.isBlank()||runCatching{LocalDate.parse(s)}.isSuccess

class MainActivity:ComponentActivity(){
    private var shared by mutableStateOf<String?>(null)
    private var openId by mutableStateOf<String?>(null)
    private fun handle(i:Intent?){
        if(i?.action==Intent.ACTION_SEND)shared=i.getStringExtra(Intent.EXTRA_TEXT)?.take(8192)
        openId=i?.getStringExtra("orderId")
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        if(savedInstanceState==null)handle(intent)
        Reminders.enqueue(this)
        setContent{
            MaterialTheme(colorScheme=if(isSystemInDarkTheme())darkColorScheme(primary=Color(0xFFA9B2FF))else lightColorScheme(primary=Brand,background=Color(0xFFF7F8FC),surface=Color.White)){
                Home(shared,openId,{shared=null},{openId=null})
            }
        }
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);handle(intent)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Home(shared:String?,openId:String?,consume:()->Unit,consumeId:()->Unit,vm:Model=viewModel()){
    val orders by vm.orders.collectAsStateWithLifecycle()
    val tx by vm.transactions.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var tab by rememberSaveable{mutableIntStateOf(0)}
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var add by rememberSaveable{mutableStateOf(false)}
    var editing by rememberSaveable{mutableStateOf(false)}
    var imported by rememberSaveable{mutableStateOf("")}
    val snack=remember{SnackbarHostState()}
    LaunchedEffect(Unit){vm.errors.collect{snack.showSnackbar(it)}}
    LaunchedEffect(shared){if(shared!=null){imported=shared;add=true;consume()}}
    LaunchedEffect(openId,orders){if(openId!=null&&orders.any{it.id==openId}){selected=openId;consumeId()}}
    val order=orders.firstOrNull{it.id==selected}
    BackHandler(enabled=add||editing||order!=null){if(add)add=false else if(editing)editing=false else selected=null}
    val nav=listOf("Заказы","Календарь","Статистика","Настройки")
    val icons=listOf(Icons.Default.Inventory2,Icons.Default.CalendarMonth,Icons.Default.BarChart,Icons.Default.Settings)
    Scaffold(
        snackbarHost={SnackbarHost(snack)},
        topBar={TopAppBar(title={Text(order?.name?:if(tab==0)"Забери." else nav[tab],fontWeight=FontWeight.Bold,maxLines=1)},navigationIcon={if(order!=null)IconButton(onClick={selected=null}){Icon(Icons.Default.ArrowBack,"Назад")}},actions={if(order!=null)IconButton(onClick={editing=true}){Icon(Icons.Default.Edit,"Редактировать")}})},
        bottomBar={if(order==null)NavigationBar{nav.forEachIndexed{i,s->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Icon(icons[i],s)},label={Text(s)})}}},
        floatingActionButton={if(order==null&&tab==0)ExtendedFloatingActionButton(onClick={imported="";add=true},icon={Icon(Icons.Default.Add,null)},text={Text("Заказ")})}
    ){pad->
        Box(Modifier.fillMaxSize().padding(pad)){
            when{order!=null->Detail(order,history.filter{it.orderId==order.id},vm,{selected=null});tab==0->Orders(orders,{selected=it.id});tab==1->CalendarPage(orders,{selected=it.id});tab==2->Stats(orders,tx);else->SettingsPage()}
        }
    }
    if(add)OrderForm(null,imported,{add=false}){o->vm.add(o);add=false;tab=0}
    if(editing&&order!=null)OrderForm(order,"",{editing=false}){o->vm.edit(o.id,o.name,o.store,o.price,o.deadline,o.point,o.link,o.note);editing=false}
}

@Composable private fun Metric(label:String,value:String,modifier:Modifier=Modifier,accent:Boolean=false){
    Card(modifier,colors=CardDefaults.cardColors(containerColor=if(accent)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(label,style=MaterialTheme.typography.labelMedium);Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
    }
}
@Composable private fun Orders(orders:List<Order>,open:(Order)->Unit){
    var filter by rememberSaveable{mutableStateOf("Активные")}
    var query by rememberSaveable{mutableStateOf("")}
    val active=orders.filterNot{it.state.archived}
    val waiting=active.filter{it.state==Status.REFUND}.sumOf{remaining(it.expectedRefund,it.receivedRefund)}
    val visible=orders.filter{when(filter){"Активные"->!it.state.archived;"История"->it.state.archived;"Можно забрать"->it.state==Status.READY;"Возвраты"->it.state in listOf(Status.REFUND,Status.RETURN_PLANNED);else->true}}.filter{(it.name+" "+it.store+" "+it.point).contains(query,true)}.sortedWith(compareBy<Order>{it.state.archived}.thenBy{it.deadline?:"9999"})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric("Можно забрать",active.count{it.state==Status.READY}.toString(),Modifier.weight(1f),true);Metric("Ждут возврата",money(waiting),Modifier.weight(1f))}}
        item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Поиск заказов")},leadingIcon={Icon(Icons.Default.Search,null)})}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Активные","Можно забрать","Возвраты","История","Все").forEach{FilterChip(selected=filter==it,onClick={filter=it},label={Text(it)})}}}
        if(visible.isEmpty())item{Text(if(orders.isEmpty())"Добавьте первый заказ — Забери напомнит о сроках." else "Нет подходящих заказов",Modifier.padding(vertical=32.dp))}
        items(visible,key={it.id}){OrderCard(it){open(it)}}
        item{Spacer(Modifier.height(72.dp))}
    }
}
@Composable private fun OrderCard(o:Order,open:()->Unit){
    val overdue=isOverdue(o.state,o.deadline?.let{LocalDate.parse(it)},LocalDate.now())
    Card(onClick=open,modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Default.Inventory2,null,Modifier.padding(14.dp))}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                Text(o.store,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                Text(o.name,fontWeight=FontWeight.SemiBold)
                Text(money(o.price)+" · "+o.state.label,style=MaterialTheme.typography.bodySmall)
                if(o.deadline!=null)Text((if(overdue)"Срок истёк" else o.state.deadlineLabel)+": "+dateText(o.deadline),style=MaterialTheme.typography.bodySmall,color=if(overdue)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight,null)
        }
    }
}
@Composable private fun Field(v:String,set:(String)->Unit,label:String,decimal:Boolean=false){
    OutlinedTextField(v,set,modifier=Modifier.fillMaxWidth(),label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=if(decimal)KeyboardType.Decimal else KeyboardType.Text),singleLine=true)
}
@Composable private fun DateField(v:String,set:(String)->Unit,label:String){
    val ctx=LocalContext.current
    Row(verticalAlignment=Alignment.CenterVertically){
        OutlinedTextField(v,set,Modifier.weight(1f),label={Text(label)},placeholder={Text("ГГГГ-ММ-ДД")},singleLine=true,isError=!validDate(v))
        IconButton(onClick={val d=runCatching{LocalDate.parse(v)}.getOrDefault(LocalDate.now());android.app.DatePickerDialog(ctx,{_,y,m,day->set(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()}){Icon(Icons.Default.CalendarMonth,"Выбрать дату")}
    }
}
@Composable private fun OrderForm(old:Order?,imported:String,close:()->Unit,save:(Order)->Unit){
    var name by rememberSaveable{mutableStateOf(old?.name?:imported.lineSequence().firstOrNull{!it.contains("http",true)}?.take(120).orEmpty())}
    var store by rememberSaveable{mutableStateOf(old?.store?:detectStore(imported)?:"Ozon")}
    var price by rememberSaveable{mutableStateOf(number(old?.price))}
    var paid by rememberSaveable{mutableStateOf((old?.paid?:0)>0)}
    var status by rememberSaveable{mutableStateOf(old?.status?:Status.READY.name)}
    var deadline by rememberSaveable{mutableStateOf(old?.deadline.orEmpty())}
    var point by rememberSaveable{mutableStateOf(old?.point.orEmpty())}
    var link by rememberSaveable{mutableStateOf(old?.link?:Regex("https?://[^\\s]+").find(imported)?.value.orEmpty())}
    var note by rememberSaveable{mutableStateOf(old?.note.orEmpty())}
    val parsed=parseMoney(price)
    val state=Status.valueOf(status)
    val valid=name.isNotBlank()&&store.isNotBlank()&&(price.isBlank()||parsed!=null)&&(!paid||parsed!=null)&&validDate(deadline)&&(old==null||parsed==null||parsed>=old.paid)
    AlertDialog(onDismissRequest=close,title={Text(if(old==null)"Новый заказ" else "Редактировать заказ")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Field(name,{name=it},"Название товара")
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){stores.forEach{s->FilterChip(store==s,{store=s},{Text(s)})}}
            Field(store,{store=it},"Магазин")
            Field(price,{price=it},"Стоимость, ₽",true)
            if(old==null){
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(paid,{paid=it});Text("Оплачен")}
                listOf(Status.TRANSIT,Status.READY,Status.RECEIVED).forEach{s->Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.clickable{status=s.name;deadline=""}){RadioButton(status==s.name,{status=s.name;deadline=""});Text(s.label)}}
            }
            if(state.deadlineLabel!=null)DateField(deadline,{deadline=it},state.deadlineLabel)
            Field(point,{point=it},"Пункт выдачи")
            Field(link,{link=it},"Ссылка на товар")
            OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text("Заметка")})
        }
    },confirmButton={Button(enabled=valid,onClick={
        val d=if(state.deadlineLabel!=null)deadline.trim().takeIf{it.isNotEmpty()}else null
        val o=old?.copy(name=name.trim(),store=store.trim(),price=parsed,deadline=d,point=point.trim(),link=link.trim(),note=note.trim())?:Order(uid(),name.trim(),store.trim(),parsed,if(paid)parsed?:0 else 0,status,d,point.trim(),link.trim(),note.trim(),today())
        save(o)
    }){Text("Сохранить")}},dismissButton={TextButton(onClick=close){Text("Отмена")}})
}
@Composable private fun Primary(label:String,click:()->Unit){Button(onClick=click,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(label)}}
@Composable private fun Detail(o:Order,history:List<History>,vm:Model,close:()->Unit){
    var action by rememberSaveable(o.id){mutableStateOf("")}
    var date by rememberSaveable(o.id){mutableStateOf("")}
    var amount by rememberSaveable(o.id){mutableStateOf("")}
    val ctx=LocalContext.current
    fun ask(event:String,initial:Long?=null){action=event;date="";amount=number(initial)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(o.store,color=MaterialTheme.colorScheme.primary)
        Text(money(o.price),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Metric(o.state.label,if(o.paid>0)"Оплачено: "+money(o.paid) else "Оплата не отмечена",Modifier.fillMaxWidth())
        if(o.state.deadlineLabel!=null)Text(o.state.deadlineLabel+": "+dateText(o.deadline))
        if(o.point.isNotBlank())Text("Пункт выдачи: "+o.point)
        if(o.note.isNotBlank())Text(o.note)
        if(o.link.startsWith("https://")||o.link.startsWith("http://"))OutlinedButton(onClick={runCatching{ctx.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(o.link)))}},modifier=Modifier.fillMaxWidth()){Text("Открыть ссылку")}
        if(o.state==Status.REFUND)Metric("Осталось вернуть",money(remaining(o.expectedRefund,o.receivedRefund)),Modifier.fillMaxWidth())
        when(o.state){
            Status.TRANSIT->{Primary("Заказ прибыл"){ask("arrived")};OutlinedButton(onClick={ask("received")}){Text("Уже получил")};TextButton(onClick={ask("cancel",o.paid)}){Text("Отменить заказ")}}
            Status.READY->{Primary("Забрал заказ"){ask("received")};TextButton(onClick={ask("cancel",o.paid)}){Text("Отменить заказ")}}
            Status.RECEIVED->{Primary("Оставить себе"){vm.act(o.id,"keep")};OutlinedButton(onClick={ask("return")}){Text("Вернуть товар")}}
            Status.RETURN_PLANNED->Primary("Товар возвращён"){ask("returned",o.paid)}
            Status.REFUND->Primary("Деньги поступили"){ask("refund",remaining(o.expectedRefund,o.receivedRefund))}
            Status.DONE->if(!o.returned&&!o.cancelled)OutlinedButton(onClick={ask("return")}){Text("Вернуть товар")}
            Status.CANCELLED->Unit
        }
        if(o.paid==0L&&o.state in listOf(Status.TRANSIT,Status.READY,Status.RECEIVED))OutlinedButton(onClick={ask("pay",o.price)}){Text("Отметить оплату")}
        Photos(o.id,vm)
        HorizontalDivider()
        Text("История заказа",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        history.forEach{Text(it.date.take(10)+" · "+it.label,style=MaterialTheme.typography.bodyMedium)}
        TextButton(onClick={ask("delete")}){Text("Удалить заказ",color=MaterialTheme.colorScheme.error)}
    }
    if(action.isNotEmpty()){
        val a=action
        val monetary=a in listOf("refund","pay")||(a in listOf("cancel","returned")&&o.paid>0)
        val needsDate=a in listOf("arrived","return")||(a in listOf("returned","cancel")&&o.paid>0)
        val parsed=parseMoney(amount)
        val valid=(!needsDate||validDate(date))&&(!monetary||(parsed!=null&&when(a){"refund"->parsed in 1..remaining(o.expectedRefund,o.receivedRefund);"pay"->parsed>0&&(o.price==null||parsed==o.price);else->parsed in 0..o.paid}))
        AlertDialog(onDismissRequest={action=""},title={Text(when(a){"arrived"->"Заказ прибыл";"return"->"Запланировать возврат";"returned"->"Товар возвращён";"cancel"->"Отменить заказ?";"refund"->"Полученные деньги";"pay"->"Оплата";"delete"->"Удалить заказ?";else->"Заказ получен?"})},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(a=="delete")Text("Заказ и связанные операции будут удалены из истории и статистики.")
                if(monetary)Field(amount,{amount=it},if(a=="refund")"Получено, ₽" else if(a=="pay")"Оплачено, ₽" else "Ожидается возврат, ₽",true)
                if(needsDate)DateField(date,{date=it},when(a){"arrived"->"Забрать до";"return"->"Вернуть до";else->"Ожидается возврат"})
                if(!monetary&&!needsDate&&a!="delete")Text("Подтвердите изменение статуса.")
            }
        },confirmButton={Button(enabled=valid,onClick={
            when(a){"delete"->{vm.remove(o.id);close()};"refund"->vm.refund(o.id,parsed!!);"pay"->vm.pay(o.id,parsed!!);else->vm.act(o.id,a,date.trim().takeIf{it.isNotEmpty()},parsed)}
            action=""
        }){Text("Подтвердить")}},dismissButton={TextButton(onClick={action=""}){Text("Отмена")}})
    }
}
@Composable private fun CalendarPage(orders:List<Order>,open:(Order)->Unit){
    var monthString by rememberSaveable{mutableStateOf(YearMonth.now().toString())}
    var dayString by rememberSaveable{mutableStateOf(today())}
    val month=YearMonth.parse(monthString)
    val active=orders.filter{!it.state.archived&&it.deadline!=null}
    val offset=month.atDay(1).dayOfWeek.value-1
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={monthString=month.minusMonths(1).toString();dayString=month.minusMonths(1).atDay(1).toString()}){Icon(Icons.Default.ChevronLeft,"Предыдущий месяц")}
                Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy",Locale("ru"))),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                IconButton(onClick={monthString=month.plusMonths(1).toString();dayString=month.plusMonths(1).atDay(1).toString()}){Icon(Icons.Default.ChevronRight,"Следующий месяц")}
            }
            Row{listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach{Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text(it)}}}
            repeat(6){r->Row{repeat(7){c->
                val n=r*7+c-offset+1
                if(n in 1..month.lengthOfMonth()){
                    val d=month.atDay(n).toString()
                    Surface(modifier=Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clickable{dayString=d},shape=RoundedCornerShape(10.dp),color=if(dayString==d)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface){
                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(n.toString());if(active.any{it.deadline==d})Text("•",color=MaterialTheme.colorScheme.primary)}
                    }
                }else Spacer(Modifier.weight(1f).aspectRatio(1f))
            }}}
        }
        item{Text(dateText(dayString),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
        val dayOrders=active.filter{it.deadline==dayString}
        if(dayOrders.isEmpty())item{Text("На этот день ничего не запланировано")}
        items(dayOrders,key={it.id}){OrderCard(it){open(it)}}
    }
}
@Composable private fun Stats(orders:List<Order>,tx:List<Transaction>){
    var period by rememberSaveable{mutableStateOf("Месяц")}
    val now=LocalDate.now()
    val subset=tx.filter{when(period){"Месяц"->it.date.startsWith(now.toString().take(7));"Год"->it.date.startsWith(now.year.toString());else->true}}
    val spent=subset.filter{it.amount>0}.sumOf{it.amount}
    val refund=-subset.filter{it.amount<0}.sumOf{it.amount}
    val pending=orders.filter{it.state==Status.REFUND}.sumOf{remaining(it.expectedRefund,it.receivedRefund)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Месяц","Год","Всё время").forEach{FilterChip(period==it,{period=it},{Text(it)})}}}
        item{Metric("Фактические расходы",money(spent-refund),Modifier.fillMaxWidth(),true)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric("Оплачено",money(spent),Modifier.weight(1f));Metric("Возвращено",money(refund),Modifier.weight(1f))}}
        item{Metric("Ожидаются возвраты (все заказы)",money(pending),Modifier.fillMaxWidth())}
        item{Text("По магазинам",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
        val byStore=subset.groupBy{t->orders.firstOrNull{it.id==t.orderId}?.store?:"Другой"}.mapValues{(_,v)->v.sumOf{it.amount}}.toList().sortedByDescending{it.second}
        items(byStore){(store,total)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(store);Text(money(total),fontWeight=FontWeight.SemiBold)}}
    }
}
@Composable private fun SettingsPage(){
    val ctx=LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Напоминания",style=MaterialTheme.typography.titleLarge)
        Text("Проверяем сроки в дневное время. Android может задерживать уведомления при энергосбережении.")
        Primary("Настройки уведомлений"){
            if(Build.VERSION.SDK_INT>=33&&ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)(ctx as? ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
            else ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,ctx.packageName))
        }
        HorizontalDivider()
        Text("О приложении",style=MaterialTheme.typography.titleLarge)
        Text("Забери 0.2.0 · тестовая версия")
        Text("Заказы хранятся на устройстве. Интернет и учётная запись не требуются. Реклама в этой версии не подключена.")
        BackupPanel()
    }
}

@Composable private fun Photos(id:String,vm:Model){
    val flow=remember(id){vm.photos(id)}
    val photos by flow.collectAsStateWithLifecycle(initialValue=emptyList())
    var preview by remember{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)vm.addPhoto(id,uri)}
    Text("Фото заказа",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        photos.forEach{a->
            val bitmap=remember(a.id){android.graphics.BitmapFactory.decodeByteArray(a.jpeg,0,a.jpeg.size)?.asImageBitmap()}
            if(bitmap!=null)Image(bitmap,"Фото заказа",Modifier.size(90.dp).clickable{preview=a.id})
        }
    }
    if(photos.size<5)OutlinedButton(onClick={picker.launch("image/*")}){Icon(Icons.Default.Add,null);Text("Добавить фото")}
    val current=photos.firstOrNull{it.id==preview}
    if(current!=null){
        val bitmap=remember(current.id){android.graphics.BitmapFactory.decodeByteArray(current.jpeg,0,current.jpeg.size)?.asImageBitmap()}
        AlertDialog(onDismissRequest={preview=null},title={Text("Фото заказа")},text={if(bitmap!=null)Image(bitmap,"Фото заказа",Modifier.fillMaxWidth())},
            confirmButton={TextButton(onClick={vm.deletePhoto(current.id);preview=null}){Text("Удалить",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={preview=null}){Text("Закрыть")}})
    }
}
@Composable private fun BackupPanel(){
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    var action by remember{mutableStateOf("")}
    var password by remember{mutableStateOf("")}
    var confirm by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var message by remember{mutableStateOf("")}
    fun run(uri:android.net.Uri?,kind:String){
        val pass=password;password="";confirm="";action=""
        if(uri==null)return
        busy=true
        scope.launch{
            try{
                when(kind){"export"->PrivateFiles.export(ctx,uri,pass);"restore"->PrivateFiles.restore(ctx,uri,pass);else->PrivateFiles.csv(ctx,uri)}
                message=when(kind){"restore"->"Данные восстановлены";"export"->"Защищённая копия сохранена";else->"Таблица сохранена"}
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){message=if(kind=="restore")"Не удалось восстановить: проверьте пароль и файл. При ошибке проверки данные не заменяются." else "Не удалось сохранить файл."}
            finally{busy=false}
        }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){run(it,"export")}
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){run(it,"restore")}
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){run(it,"csv")}
    HorizontalDivider()
    Text("Резервное копирование",style=MaterialTheme.typography.titleLarge)
    Text("Копия заказов, операций, истории и фото защищена паролем. Восстановление заменяет текущие заказы. Пароль восстановить нельзя.")
    OutlinedButton(enabled=!busy,onClick={action="export";password="";confirm=""},modifier=Modifier.fillMaxWidth()){Text("Создать защищённую копию")}
    OutlinedButton(enabled=!busy,onClick={action="restore";password="";confirm=""},modifier=Modifier.fillMaxWidth()){Text("Восстановить из копии")}
    OutlinedButton(enabled=!busy,onClick={action="csv"},modifier=Modifier.fillMaxWidth()){Text("Экспорт операций CSV")}
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(message.isNotBlank())Text(message)
    if(action.isNotEmpty()){
        val kind=action
        AlertDialog(onDismissRequest={action="";password="";confirm=""},title={Text(when(kind){"export"->"Пароль резервной копии";"restore"->"Заменить текущие данные?";else->"Экспорт операций"})},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(kind=="csv")Text("CSV не зашифрован и содержит названия покупок и суммы. Сохраняйте его только в доверенном месте.")
                else{
                    if(kind=="restore")Text("Все текущие заказы, операции и фото будут заменены содержимым копии. Сначала сохраните текущие данные, если они нужны.")
                    OutlinedTextField(password,{password=it},label={Text("Пароль (от 8 символов)")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                    if(kind=="export")OutlinedTextField(confirm,{confirm=it},label={Text("Повторите пароль")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                }
            }
        },confirmButton={Button(enabled=kind=="csv"||(password.length>=8&&(kind!="export"||password==confirm)),onClick={
            action=""
            when(kind){"export"->export.launch("zaberi-"+today()+".zaberi");"restore"->restore.launch(arrayOf("application/octet-stream","*/*"));else->csv.launch("zaberi-"+today()+".csv")}
        }){Text("Продолжить")}},dismissButton={TextButton(onClick={action="";password="";confirm=""}){Text("Отмена")}})
    }
}
