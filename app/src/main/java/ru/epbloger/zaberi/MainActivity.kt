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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
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

private val Brand=Color(0xFF4658D6)
private val categories=listOf("Одежда","Техника","Дом","Красота","Животные","Авто","Спорт","Хобби","Подарки","Продукты","Работа","Другое")
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
            val preferences=remember{Preferences(applicationContext)}
            val dark=when(preferences.theme){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
            MaterialTheme(colorScheme=if(dark)darkColorScheme(primary=Color(0xFFA9B2FF))else lightColorScheme(primary=Brand,onPrimary=Color.White,primaryContainer=Color(0xFFE6E9FF),onPrimaryContainer=Color(0xFF202B78),background=Color(0xFFF6F7FB),surface=Color.White,surfaceVariant=Color(0xFFF0F1F6),onSurfaceVariant=Color(0xFF4E5263),outlineVariant=Color(0xFFE0E3EC))){
                Home(shared,openId,{shared=null},{openId=null},preferences)
            }
        }
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);handle(intent)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Home(shared:String?,openId:String?,consume:()->Unit,consumeId:()->Unit,preferences:Preferences,vm:Model=viewModel()){
    val orders by vm.orders.collectAsStateWithLifecycle()
    val tx by vm.transactions.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    var tab by rememberSaveable{mutableIntStateOf(0)}
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var add by rememberSaveable{mutableStateOf(false)}
    var editing by rememberSaveable{mutableStateOf(false)}
    var imported by rememberSaveable{mutableStateOf("")}
    var settingsPage by rememberSaveable{mutableStateOf("")}
    var searchVisible by rememberSaveable{mutableStateOf(false)}
    val snack=remember{SnackbarHostState()}
    val ctx=LocalContext.current
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)Reminders.enqueue(ctx)}
    var notificationPrompt by rememberSaveable{mutableStateOf(false)}
    LaunchedEffect(Unit){vm.errors.collect{snack.showSnackbar(it)}}
    LaunchedEffect(shared){if(shared!=null){imported=shared;add=true;consume()}}
    LaunchedEffect(openId,orders){if(openId!=null&&orders.any{it.id==openId}){selected=openId;consumeId()}}
    val order=orders.firstOrNull{it.id==selected}
    BackHandler(enabled=add||editing||order!=null||settingsPage.isNotEmpty()||searchVisible){if(add)add=false else if(editing)editing=false else if(order!=null)selected=null else if(settingsPage.isNotEmpty())settingsPage=if(settingsPage in listOf("Конфиденциальность","Условия использования","Реклама и сторонние сервисы","Разработчик и контакты"))"Юридическая информация" else "" else searchVisible=false}
    val nav=listOf("Заказы","Календарь","Статистика","Настройки")
    val icons=listOf(Icons.Default.Inventory2,Icons.Default.CalendarMonth,Icons.Default.BarChart,Icons.Default.Settings)
    Scaffold(
        containerColor=MaterialTheme.colorScheme.background,
        snackbarHost={SnackbarHost(snack)},
        topBar={TopAppBar(
            title={Text(order?.name?:if(tab==0)"Забери" else if(tab==3&&settingsPage.isNotEmpty())settingsPage else nav[tab],fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)},
            navigationIcon={if(order!=null||settingsPage.isNotEmpty())IconButton(onClick={if(order!=null)selected=null else settingsPage=if(settingsPage in listOf("Конфиденциальность","Условия использования","Реклама и сторонние сервисы","Разработчик и контакты"))"Юридическая информация" else ""}){Icon(Icons.Default.ArrowBack,"Назад")}},
            actions={if(order!=null)IconButton(onClick={editing=true}){Icon(Icons.Default.Edit,"Редактировать")}else if(tab==0)IconButton(onClick={searchVisible=!searchVisible}){Icon(if(searchVisible)Icons.Default.Close else Icons.Default.Search,"Поиск")}},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background)
        )},
        bottomBar={if(order==null&&settingsPage.isEmpty())NavigationBar(containerColor=MaterialTheme.colorScheme.surface){nav.forEachIndexed{i,s->NavigationBarItem(selected=tab==i,onClick={tab=i;searchVisible=false},icon={Icon(icons[i],s)},label={Text(s,style=MaterialTheme.typography.labelSmall)})}}},
        floatingActionButton={if(order==null&&tab==0)ExtendedFloatingActionButton(onClick={imported="";add=true},icon={Icon(Icons.Default.Add,null)},text={Text("Добавить заказ")})}
    ){pad->
        Box(Modifier.fillMaxSize().padding(pad)){
            when{order!=null->Detail(order,history.filter{it.orderId==order.id},vm,{selected=null});tab==0->Orders(orders,{selected=it.id},{vm.act(it.id,"received")},searchVisible,{imported="";add=true},preferences.adsEnabled);tab==1->CalendarPage(orders,{selected=it.id});tab==2->Stats(orders,tx);else->SettingsPage(preferences,catalog,vm,settingsPage,{settingsPage=it})}
        }
    }
    if(add)OrderForm(null,imported,catalog,orders,{add=false}){o->vm.add(o);add=false;tab=0;if(o.deadline!=null&&preferences.enabled&&Build.VERSION.SDK_INT>=33&&ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notificationPrompt=true}
    if(notificationPrompt)AlertDialog(onDismissRequest={notificationPrompt=false},title={Text("Напомнить о сроке?")},text={Text("Разрешите уведомления, чтобы Забери мог напомнить о получении или возврате. Это можно изменить в настройках.")},confirmButton={Button(onClick={notificationPrompt=false;if(Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Разрешить")}},dismissButton={TextButton(onClick={notificationPrompt=false}){Text("Не сейчас")}})
    if(editing&&order!=null)OrderForm(order,"",catalog,orders,{editing=false}){o->vm.edit(o.id,o.name,o.store,o.price,o.deadline,o.point,o.link,o.note,o.category,o.pointLat,o.pointLon);editing=false}
}

@Composable private fun Metric(label:String,value:String,modifier:Modifier=Modifier,accent:Boolean=false){
    Card(modifier,colors=CardDefaults.cardColors(containerColor=if(accent)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(label,style=MaterialTheme.typography.labelMedium);Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
    }
}
@Composable private fun Orders(orders:List<Order>,open:(Order)->Unit,received:(Order)->Unit,searchVisible:Boolean,addOrder:()->Unit,adsEnabled:Boolean){
    var filter by rememberSaveable{mutableStateOf("Активные")}
    var query by rememberSaveable{mutableStateOf("")}
    var shop by rememberSaveable{mutableStateOf("")}
    var category by rememberSaveable{mutableStateOf("")}
    var grouped by rememberSaveable{mutableStateOf(false)}
    var confirmId by rememberSaveable{mutableStateOf<String?>(null)}
    LaunchedEffect(searchVisible){if(!searchVisible)query=""}
    val active=orders.filterNot{it.state.archived}
    val waiting=active.filter{it.state==Status.REFUND}.sumOf{remaining(it.expectedRefund,it.receivedRefund)}
    val visible=orders.filter{when(filter){"Активные"->!it.state.archived;"История"->it.state.archived;"В пути"->it.state==Status.TRANSIT;"Можно забрать"->it.state==Status.READY;"Возвраты"->it.state in listOf(Status.REFUND,Status.RETURN_PLANNED);else->true}}.filter{(it.name+" "+it.store+" "+it.point+" "+it.category).contains(query,true)&&(shop.isBlank()||it.store==shop)&&(category.isBlank()||it.category==category)}.sortedWith(compareBy<Order>{it.state.archived}.thenBy{orderPriority(it.state,it.deadline?.let{d->LocalDate.parse(d)},LocalDate.now())}.thenBy{it.deadline?:"9999"}.thenByDescending{it.created})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{OrderOverview(active,waiting,{filter="Можно забрать";shop="";category=""},{filter="Возвраты";shop="";category=""})}
        if(searchVisible)item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Товар, магазин или ПВЗ")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(18.dp))}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Активные","В пути","Можно забрать","Возвраты","История","Все").forEach{FilterChip(selected=filter==it,onClick={filter=it},label={Text(it)})}}}
        if(orders.isNotEmpty())item{OrderFilters(orders,shop,{shop=it},category,{category=it},grouped,{grouped=it})}
        if(visible.isEmpty())item{
            EmptyOrders(orders.isEmpty()){if(orders.isEmpty())addOrder() else {filter="Все";shop="";category="";query=""}}
        }else item{Text(if(filter=="Активные")"Мои заказы" else filter,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        if(grouped){
            visible.groupBy{if(it.point.isBlank())"Пункт не указан" else it.store+" · "+it.point}.forEach{(title,group)->
                item(key="group:"+title){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("Можно забрать: "+group.count{it.state==Status.READY},style=MaterialTheme.typography.bodySmall)}
                items(group,key={it.id}){o->OrderCard(o,if(o.state==Status.READY){{confirmId=o.id}}else null){open(o)}}
            }
        }else items(visible,key={it.id}){o->OrderCard(o,if(o.state==Status.READY){{confirmId=o.id}}else null){open(o)}}
        if(adsEnabled&&visible.isNotEmpty()&&BuildConfig.BANNER_ID.isNotBlank())item(key="banner"){OptionalBanner()}
        item{Spacer(Modifier.height(72.dp))}
    }
    val pending=orders.firstOrNull{it.id==confirmId}
    if(pending!=null)AlertDialog(onDismissRequest={confirmId=null},title={Text("Заказ получен?")},text={Text(pending.name)},confirmButton={Button(onClick={received(pending);confirmId=null}){Text("Забрал")}},dismissButton={TextButton(onClick={confirmId=null}){Text("Отмена")}})
}
@Composable private fun OrderCard(o:Order,quick:(()->Unit)?=null,open:()->Unit){
    val date=o.deadline?.let{LocalDate.parse(it)}
    val days=date?.let{java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),it)}
    val overdue=isOverdue(o.state,date,LocalDate.now())
    val urgent=days!=null&&days<=1&&o.state in listOf(Status.READY,Status.RETURN_PLANNED,Status.REFUND)
    val statusColor=when{overdue||urgent->MaterialTheme.colorScheme.error;o.state==Status.READY->MaterialTheme.colorScheme.primary;else->MaterialTheme.colorScheme.onSurfaceVariant}
    OutlinedCard(onClick=open,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                StoreTile(o.store)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                    Text(o.store,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(o.name,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(o.point.isNotBlank())Text(o.point,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Icon(Icons.Default.ChevronRight,"Открыть заказ",tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment=Alignment.CenterVertically){
                Surface(shape=RoundedCornerShape(9.dp),color=statusColor.copy(alpha=0.10f)){Text(o.state.label,Modifier.padding(horizontal=9.dp,vertical=5.dp),color=statusColor,style=MaterialTheme.typography.labelMedium)}
                Spacer(Modifier.weight(1f))
                Text(money(o.price),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall)
            }
            if(date!=null)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
                Icon(Icons.Default.Schedule,null,Modifier.size(16.dp),tint=statusColor)
                Text(when{overdue->"Срок истёк · "+dateText(o.deadline);days==0L->(o.state.deadlineLabel?:"Срок")+": сегодня";days==1L->(o.state.deadlineLabel?:"Срок")+": завтра";else->(o.state.deadlineLabel?:"Срок")+": "+dateText(o.deadline)},color=statusColor,style=MaterialTheme.typography.bodySmall)
            }
            if(quick!=null)FilledTonalButton(onClick=quick,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Check,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Забрал заказ")}
        }
    }
}
@Composable private fun StoreTile(store:String){
    val accent=when(store.lowercase(Locale.ROOT)){"ozon"->Color(0xFF005BDB);"wildberries"->Color(0xFF9B278F);"яндекс маркет"->Color(0xFF9A6900);"авито"->Color(0xFF007E65);"dns"->Color(0xFFB75400);else->MaterialTheme.colorScheme.primary}
    Surface(shape=RoundedCornerShape(16.dp),color=accent.copy(alpha=.12f),modifier=Modifier.size(52.dp)){
        Box(contentAlignment=Alignment.Center){Icon(Icons.Default.Inventory2,null,tint=if(isSystemInDarkTheme())MaterialTheme.colorScheme.primary else accent,modifier=Modifier.size(25.dp))}
    }
}
@Composable private fun OrderOverview(active:List<Order>,waiting:Long,readyClick:()->Unit,refundClick:()->Unit){
    val urgent=active.count{orderPriority(it.state,it.deadline?.let{d->LocalDate.parse(d)},LocalDate.now())==0}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale("ru"))).replaceFirstChar{it.titlecase(Locale("ru"))},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text("Покупки под контролем",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if(active.isEmpty())"Пока нет активных заказов" else "Активных заказов: "+active.size,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                if(urgent>0)Text("Требуют внимания: "+urgent,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)else Text("Важные сроки будут здесь",style=MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            OverviewTile("Можно забрать",active.count{it.state==Status.READY}.toString(),Icons.Default.Inventory2,Modifier.weight(1f),readyClick)
            OverviewTile("Ждут возврата",money(waiting),Icons.Default.AccountBalanceWallet,Modifier.weight(1f),refundClick)
        }
    }
}
@Composable private fun OverviewTile(label:String,value:String,icon:ImageVector,modifier:Modifier,click:()->Unit){
    OutlinedCard(onClick=click,modifier=modifier,shape=RoundedCornerShape(20.dp),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
            Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun EmptyOrders(first:Boolean,action:()->Unit){
    Column(Modifier.fillMaxWidth().padding(vertical=26.dp,horizontal=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
        Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Default.Inventory2,null,Modifier.padding(22.dp).size(38.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)}
        Text(if(first)"Добавьте первый заказ" else "Ничего не найдено",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text(if(first)"Выберите магазин и укажите срок. Забери напомнит, когда пора за покупкой." else "Попробуйте сбросить фильтры.",style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(first)Button(onClick=action){Text("Добавить заказ")}else OutlinedButton(onClick=action){Text("Сбросить фильтры")}
    }
}
@Composable private fun Field(v:String,set:(String)->Unit,label:String,decimal:Boolean=false){
    OutlinedTextField(v,set,modifier=Modifier.fillMaxWidth(),label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=if(decimal)KeyboardType.Decimal else KeyboardType.Text),singleLine=true)
}
@Composable private fun DateField(v:String,set:(String)->Unit,label:String){
    val ctx=LocalContext.current
    val current=runCatching{LocalDate.parse(v)}.getOrNull()
    val openCalendar:()->Unit={
        val d=current?:LocalDate.now()
        android.app.DatePickerDialog(ctx,{_,y,m,day->set(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()
    }
    OutlinedCard(shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
            Row(Modifier.weight(1f).heightIn(min=58.dp).clickable(role=Role.Button,onClick=openCalendar),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Icon(Icons.Default.CalendarMonth,"Выбрать дату",tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(if(current==null)"Выбрать дату" else dateText(v),style=MaterialTheme.typography.bodyLarge)}
            }
            if(v.isNotBlank())IconButton(onClick={set("")}){Icon(Icons.Default.Close,"Очистить дату")}
            else IconButton(onClick=openCalendar){Icon(Icons.Default.ChevronRight,"Открыть календарь")}
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun OrderForm(old:Order?,imported:String,catalog:List<CatalogEntry>,orders:List<Order>,close:()->Unit,save:(Order)->Unit){
    var name by rememberSaveable{mutableStateOf(old?.name?:imported.lineSequence().firstOrNull{!it.contains("http",true)}?.take(120).orEmpty())}
    var store by rememberSaveable{mutableStateOf(old?.store?:detectStore(imported).orEmpty())}
    var price by rememberSaveable{mutableStateOf(number(old?.price))}
    var paid by rememberSaveable{mutableStateOf((old?.paid?:0)>0)}
    var status by rememberSaveable{mutableStateOf(old?.status?:Status.READY.name)}
    var deadline by rememberSaveable{mutableStateOf(old?.deadline.orEmpty())}
    var point by rememberSaveable{mutableStateOf(old?.point.orEmpty())}
    var pointLat by rememberSaveable{mutableStateOf(old?.pointLat)}
    var pointLon by rememberSaveable{mutableStateOf(old?.pointLon)}
    var link by rememberSaveable{mutableStateOf(old?.link?:Regex("https?://[^\\s]+").find(imported)?.value.orEmpty())}
    var note by rememberSaveable{mutableStateOf(old?.note.orEmpty())}
    var category by rememberSaveable{mutableStateOf(old?.category.orEmpty())}
    var advanced by rememberSaveable{mutableStateOf(old!=null||imported.isNotBlank())}
    var duplicateConfirmed by rememberSaveable{mutableStateOf(false)}
    val duplicate=old==null&&link.isNotBlank()&&orders.any{!it.state.archived&&it.link.trim()==link.trim()}
    val parsed=parseMoney(price)
    val state=Status.valueOf(status)
    val valid=name.isNotBlank()&&name.length<=200&&store.isNotBlank()&&store.length<=200&&category.length<=200&&point.length<=200&&validPoint(pointLat,pointLon)&&link.length<=8192&&note.length<=10000&&(!duplicate||duplicateConfirmed)&&(price.isBlank()||parsed!=null)&&(!paid||parsed!=null)&&validDate(deadline)&&(old==null||parsed==null||parsed>=old.paid)
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
      Scaffold(modifier=Modifier.fillMaxSize().imePadding(),containerColor=MaterialTheme.colorScheme.background,
        topBar={TopAppBar(title={Text(if(old==null)"Новый заказ" else "Редактировать",fontWeight=FontWeight.Bold)},navigationIcon={IconButton(onClick=close){Icon(Icons.Default.Close,"Закрыть")}})},
        bottomBar={Surface(shadowElevation=2.dp){Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)){
            Button(enabled=valid,onClick={
                val d=if(state.deadlineLabel!=null)deadline.trim().takeIf{it.isNotEmpty()}else null
                val o=old?.copy(name=name.trim(),store=store.trim(),price=parsed,deadline=d,point=point.trim(),link=link.trim(),note=note.trim(),category=category.trim(),pointLat=pointLat,pointLon=pointLon)?:Order(uid(),name.trim(),store.trim(),parsed,if(paid)parsed?:0 else 0,status,d,point.trim(),link.trim(),note.trim(),today(),category=category.trim(),pointLat=pointLat,pointLon=pointLon)
                save(o)
            },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text(if(old==null)"Добавить заказ" else "Сохранить")}
        }}}
      ){pad->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text("Основное",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Название, магазин и текущий статус",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Field(name,{name=it},"Название товара")
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){(catalog.filter{it.kind=="store"}.map{it.value}+stores).distinct().take(30).forEach{s->FilterChip(store==s,{if(store!=s){point="";pointLat=null;pointLon=null};store=s},{Text(s)})}}
            if(store !in stores || advanced)Field(store,{if(store!=it){point="";pointLat=null;pointLon=null};store=it},"Название магазина")
            else TextButton(onClick={advanced=true;store="";point="";pointLat=null;pointLon=null}){Text("Другой магазин")}
            Field(price,{price=it},"Стоимость, ₽ (необязательно)",true)
            if(old==null){
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(paid,{paid=it});Text("Оплачен")}
                Text("Статус заказа",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                listOf(Status.TRANSIT,Status.READY,Status.RECEIVED).forEach{s->Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.clickable{status=s.name;deadline=""}){RadioButton(status==s.name,{status=s.name;deadline=""});Text(s.label)}}
            }
            if(state.deadlineLabel!=null){Text("Срок",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);DateField(deadline,{deadline=it},state.deadlineLabel);Text("Можно указать позже",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            PickupSelector(store,point,pointLat,pointLon,catalog){address,lat,lon->point=address;pointLat=lat;pointLon=lon}
            TextButton(onClick={advanced=!advanced}){Text(if(advanced)"Скрыть дополнительные сведения" else "Дополнительные сведения")}
            if(advanced){
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){(catalog.filter{it.kind=="category"}.map{it.value}+categories).distinct().take(30).forEach{v->FilterChip(category==v,{category=if(category==v)"" else v},{Text(v)})}}
                Field(category,{category=it},"Категория")
                Field(link,{link=it;duplicateConfirmed=false},"Ссылка на товар")
                OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text("Заметка")})
            }
            if(duplicate)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(duplicateConfirmed,{duplicateConfirmed=it});Text("Похожий заказ уже есть. Добавить ещё один?")}
        }
      }
    }
}
@Composable private fun Primary(label:String,click:()->Unit){Button(onClick=click,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(label)}}
@Composable private fun Detail(o:Order,history:List<History>,vm:Model,close:()->Unit){
    var action by rememberSaveable(o.id){mutableStateOf("")}
    var date by rememberSaveable(o.id){mutableStateOf("")}
    var amount by rememberSaveable(o.id){mutableStateOf("")}
    val ctx=LocalContext.current
    fun ask(event:String,initial:Long?=null){action=event;date=when(event){"pay","refund"->today();"adjust_refund"->o.deadline.orEmpty();else->""};amount=number(initial)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(o.store,color=MaterialTheme.colorScheme.primary)
        Text(money(o.price),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Metric(o.state.label,if(o.paid>0)"Оплачено: "+money(o.paid) else "Оплата не отмечена",Modifier.fillMaxWidth())
        if(o.state.deadlineLabel!=null)Text(o.state.deadlineLabel+": "+dateText(o.deadline))
        if(o.point.isNotBlank()){
            Text("Пункт выдачи: "+o.point)
            OutlinedButton(onClick={openPickupMap(ctx,o.point,o.pointLat,o.pointLon)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Map,null);Spacer(Modifier.width(8.dp));Text("Открыть на карте")}
        }
        if(o.category.isNotBlank())Text("Категория: "+o.category)
        if(o.note.isNotBlank())Text(o.note)
        if(o.link.startsWith("https://")||o.link.startsWith("http://"))OutlinedButton(onClick={runCatching{ctx.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(o.link)))}},modifier=Modifier.fillMaxWidth()){Text("Открыть ссылку")}
        if(o.state==Status.REFUND)Metric("Осталось вернуть",money(remaining(o.expectedRefund,o.receivedRefund)),Modifier.fillMaxWidth())
        when(o.state){
            Status.TRANSIT->{Primary("Заказ прибыл"){ask("arrived")};OutlinedButton(onClick={ask("received")}){Text("Уже получил")};TextButton(onClick={ask("cancel",o.paid)}){Text("Отменить заказ")}}
            Status.READY->{Primary("Забрал заказ"){ask("received")};TextButton(onClick={ask("cancel",o.paid)}){Text("Отменить заказ")}}
            Status.RECEIVED->{Primary("Оставить себе"){vm.act(o.id,"keep")};OutlinedButton(onClick={ask("return")}){Text("Вернуть товар")};TextButton(onClick={ask("undo_received")}){Text("Исправить: заказ ещё не получен")}}
            Status.RETURN_PLANNED->{Primary("Товар возвращён"){ask("returned",o.paid)};TextButton(onClick={ask("cancel_return")}){Text("Отказаться от возврата")}}
            Status.REFUND->{Primary("Деньги поступили"){ask("refund",remaining(o.expectedRefund,o.receivedRefund))};OutlinedButton(onClick={ask("adjust_refund",o.expectedRefund)}){Text("Уточнить сумму и срок возврата")}}
            Status.DONE->if(!o.returned&&!o.cancelled){OutlinedButton(onClick={ask("return")}){Text("Вернуть товар")};TextButton(onClick={ask("reopen")}){Text("Вернуть в активные")}}
            Status.CANCELLED->Unit
        }
        if(canMarkPayment(o.state,o.paid,o.returned,o.cancelled))OutlinedButton(onClick={ask("pay",o.price)}){Text("Отметить оплату")}
        Photos(o.id,vm)
        HorizontalDivider()
        Text("История заказа",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        history.forEach{Text(it.date.take(10)+" · "+it.label,style=MaterialTheme.typography.bodyMedium)}
        TextButton(onClick={ask("delete")}){Text("Удалить заказ",color=MaterialTheme.colorScheme.error)}
    }
    if(action.isNotEmpty()){
        val a=action
        val monetary=a in listOf("refund","pay","adjust_refund")||(a in listOf("cancel","returned")&&o.paid>0)
        val needsDate=a in listOf("arrived","return","undo_received","adjust_refund","pay","refund")||(a in listOf("returned","cancel")&&o.paid>0)
        val parsed=parseMoney(amount)
        val valid=(!needsDate||validDate(date))&&(a !in listOf("pay","refund")||validOperationDate(date,LocalDate.now()))&&(!monetary||(parsed!=null&&when(a){"refund"->parsed in 1..remaining(o.expectedRefund,o.receivedRefund);"pay"->parsed>0&&(o.price==null||parsed==o.price);"adjust_refund"->refundTargetValid(o.paid,o.receivedRefund,parsed);else->parsed in 0..o.paid}))
        AlertDialog(onDismissRequest={action=""},title={Text(when(a){"arrived"->"Заказ прибыл";"return"->"Запланировать возврат";"returned"->"Товар возвращён";"cancel"->"Отменить заказ?";"refund"->"Полученные деньги";"pay"->"Оплата";"delete"->"Удалить заказ?";"adjust_refund"->"Уточнить возврат";"undo_received"->"Заказ ещё не получен";"cancel_return"->"Отказаться от возврата?";"reopen"->"Вернуть в активные?";else->"Заказ получен?"})},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(a=="delete")Text("Заказ и связанные операции будут удалены из истории и статистики.")
                if(a=="adjust_refund")Text("Укажите общую сумму возврата, включая уже полученные деньги: "+money(o.receivedRefund)+". Если остаток равен нулю, возврат завершится.")
                if(monetary)Field(amount,{amount=it},if(a=="refund")"Получено, ₽" else if(a=="pay")"Оплачено, ₽" else "Ожидается возврат, ₽",true)
                if(needsDate)DateField(date,{date=it},when(a){"arrived","undo_received"->"Забрать до";"return"->"Вернуть до";"pay"->"Дата оплаты";"refund"->"Дата поступления";else->"Ожидается возврат"})
                if(!monetary&&!needsDate&&a!="delete")Text("Подтвердите изменение статуса.")
            }
        },confirmButton={Button(enabled=valid,onClick={
            when(a){"delete"->{vm.remove(o.id);close()};"refund"->vm.refund(o.id,parsed!!,date);"pay"->vm.pay(o.id,parsed!!,date);"adjust_refund"->vm.adjustRefund(o.id,parsed!!,date.trim().takeIf{it.isNotEmpty()});else->vm.act(o.id,a,date.trim().takeIf{it.isNotEmpty()},parsed)}
            action=""
        }){Text("Подтвердить")}},dismissButton={TextButton(onClick={action=""}){Text("Отмена")}})
    }
}
@Composable private fun CalendarPage(orders:List<Order>,open:(Order)->Unit){
    var monthString by rememberSaveable{mutableStateOf(YearMonth.now().toString())}
    var dayString by rememberSaveable{mutableStateOf(today())}
    var upcoming by rememberSaveable{mutableStateOf(false)}
    val month=YearMonth.parse(monthString)
    val now=LocalDate.now()
    val active=orders.filter{!it.state.archived&&it.deadline!=null}.sortedBy{it.deadline}
    val byDay=active.groupBy{it.deadline}
    val offset=month.atDay(1).dayOfWeek.value-1
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!upcoming,{upcoming=false},{Text("Календарь")});FilterChip(upcoming,{upcoming=true},{Text("Все сроки")})}}
        if(!upcoming){
            item{
                OutlinedCard(shape=RoundedCornerShape(24.dp),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            IconButton(onClick={monthString=month.minusMonths(1).toString();dayString=month.minusMonths(1).atDay(1).toString()}){Icon(Icons.Default.ChevronLeft,"Предыдущий месяц")}
                            Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy",Locale("ru"))).replaceFirstChar{it.titlecase(Locale("ru"))},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick={monthString=month.plusMonths(1).toString();dayString=month.plusMonths(1).atDay(1).toString()}){Icon(Icons.Default.ChevronRight,"Следующий месяц")}
                        }
                        Row{listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach{Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text(it,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
                        repeat((offset+month.lengthOfMonth()+6)/7){r->Row{repeat(7){c->
                            val n=r*7+c-offset+1
                            if(n in 1..month.lengthOfMonth()){
                                val date=month.atDay(n);val ds=date.toString();val count=byDay[ds]?.size?:0
                                val selected=dayString==ds
                                Surface(modifier=Modifier.weight(1f).aspectRatio(.85f).padding(2.dp).clickable{dayString=ds},shape=RoundedCornerShape(12.dp),color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,border=if(date==now)BorderStroke(1.dp,MaterialTheme.colorScheme.primary)else null){
                                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                        Text(n.toString(),fontWeight=if(selected||date==now)FontWeight.Bold else FontWeight.Normal,color=if(selected)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                        if(count>0)Text(if(count>9)"9+" else count.toString(),style=MaterialTheme.typography.labelSmall,color=if(date.isBefore(now))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }else Spacer(Modifier.weight(1f).aspectRatio(.85f))
                        }}}
                        TextButton(onClick={monthString=YearMonth.now().toString();dayString=today()},modifier=Modifier.align(Alignment.End)){Text("Сегодня")}
                    }
                }
            }
            item{Text(dateText(dayString),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
            val selected=byDay[dayString].orEmpty()
            if(selected.isEmpty())item{Text("На этот день нет сроков.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(selected,key={it.id}){OrderCard(it){open(it)}}
        }else{
            if(active.isEmpty())item{Text("Нет заказов с указанным сроком.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            active.groupBy{it.deadline}.forEach{(date,group)->
                item(key="day:"+date){Text(dateText(date),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=if(date!=null&&date<today())MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)}
                items(group,key={it.id}){OrderCard(it){open(it)}}
            }
        }
    }
}
@Composable private fun Stats(orders:List<Order>,tx:List<Transaction>){
    var period by rememberSaveable{mutableStateOf("Месяц")}
    var offset by rememberSaveable{mutableIntStateOf(0)}
    var grouping by rememberSaveable{mutableStateOf("Магазины")}
    var showOperations by rememberSaveable{mutableStateOf(false)}
    val now=LocalDate.now()
    val month=YearMonth.from(now).plusMonths(offset.toLong())
    val year=now.year+offset
    val subset=tx.filter{when(period){"Месяц"->it.date.startsWith(month.toString());"Год"->it.date.startsWith(year.toString());else->true}}
    val spent=subset.filter{it.amount>0}.sumOf{it.amount}
    val refund=-subset.filter{it.amount<0}.sumOf{it.amount}
    val pending=orders.filter{it.state==Status.REFUND}.sumOf{remaining(it.expectedRefund,it.receivedRefund)}
    val index=orders.associateBy{it.id}
    val groups=subset.groupBy{t->if(grouping=="Магазины")index[t.orderId]?.store?:"Другой" else index[t.orderId]?.category?.ifBlank{"Без категории"}?:"Без категории"}.mapValues{(_,v)->v.sumOf{it.amount}}.toList().sortedByDescending{it.second}
    val largest=groups.maxOfOrNull{kotlin.math.abs(it.second)}?.coerceAtLeast(1L)?:1L
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Месяц","Год","Всё время").forEach{FilterChip(period==it,{period=it;offset=0},{Text(it)})}}}
        if(period!="Всё время")item{Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(offset> -1200)offset--}){Icon(Icons.Default.ChevronLeft,"Предыдущий период")}
            Text(if(period=="Месяц")month.format(DateTimeFormatter.ofPattern("LLLL yyyy",Locale("ru"))).replaceFirstChar{it.titlecase(Locale("ru"))}else year.toString(),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick={offset++},enabled=offset<0){Icon(Icons.Default.ChevronRight,"Следующий период")}
        }}
        item{Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Фактические расходы",style=MaterialTheme.typography.labelLarge)
                Text(money(spent-refund),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text("Оплата минус полученные возвраты за период",style=MaterialTheme.typography.bodySmall)
            }
        }}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Оплачено",money(spent),Modifier.weight(1f));Metric("Возвращено",money(refund),Modifier.weight(1f))}}
        item{Metric("Ожидаются возвраты · все заказы",money(pending),Modifier.fillMaxWidth())}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Магазины","Категории").forEach{FilterChip(grouping==it,{grouping=it},{Text(it)})}}}
        if(groups.isEmpty())item{Text("Нет операций за выбранный период.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(groups){(label,total)->
            OutlinedCard(shape=RoundedCornerShape(18.dp),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontWeight=FontWeight.Medium);Text(money(total),fontWeight=FontWeight.Bold)}
                    LinearProgressIndicator(progress={kotlin.math.abs(total).toFloat()/largest.toFloat()},modifier=Modifier.fillMaxWidth().height(5.dp),color=if(total<0)MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                }
            }
        }
        item{TextButton(onClick={showOperations=!showOperations}){Text(if(showOperations)"Скрыть операции" else "Показать операции ("+subset.size+")")}}
        if(showOperations)items(subset.sortedByDescending{it.date},key={it.id}){t->
            Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(index[t.orderId]?.name?:"Заказ",maxLines=2,overflow=TextOverflow.Ellipsis);Text(dateText(t.date),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Text((if(t.amount<0)"−" else "+")+money(kotlin.math.abs(t.amount)),fontWeight=FontWeight.SemiBold)
            }
        }
    }
}
@Composable private fun SettingsPage(preferences:Preferences,catalog:List<CatalogEntry>,vm:Model,page:String,open:(String)->Unit){
    val ctx=LocalContext.current
    val scroll=rememberScrollState()
    LaunchedEffect(page){scroll.scrollTo(0)}
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        when(page){
            ""->{
                Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
                    Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                        Icon(Icons.Default.Inventory2,null,Modifier.size(38.dp));Column{Text("Забери",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Все покупки под контролем",style=MaterialTheme.typography.bodySmall)}
                    }
                }
                SettingsLink(Icons.Default.Notifications,"Уведомления","Сроки и конфиденциальность"){open("Уведомления")}
                SettingsLink(Icons.Default.Palette,"Оформление","Светлая, тёмная и системная тема"){open("Оформление")}
                SettingsLink(Icons.Default.Store,"Справочники","Магазины, категории и ПВЗ"){open("Справочники")}
                SettingsLink(Icons.Default.Backup,"Мои данные","Резервное копирование и CSV"){open("Мои данные")}
                SettingsLink(Icons.Default.Gavel,"Юридическая информация","Конфиденциальность, условия и реклама"){open("Юридическая информация")}
                SettingsLink(Icons.Default.Campaign,"Реклама","Управление необязательными объявлениями"){open("Реклама")}
                SettingsLink(Icons.Default.Info,"О приложении","Версия и обратная связь"){open("О приложении")}
            }
            "Уведомления"->{
        Row(verticalAlignment=Alignment.CenterVertically){Text("Напоминания о сроках",Modifier.weight(1f));Switch(preferences.enabled,{preferences.enabled(it)})}
        if(preferences.enabled){
            Row(verticalAlignment=Alignment.CenterVertically){Text("Начинать с",Modifier.weight(1f));IconButton(onClick={preferences.hour(preferences.hour-1)},enabled=preferences.hour>7){Icon(Icons.Default.Remove,"Раньше")};Text("%02d:00".format(preferences.hour));IconButton(onClick={preferences.hour(preferences.hour+1)},enabled=preferences.hour<21){Icon(Icons.Default.Add,"Позже")}}
            Row(verticalAlignment=Alignment.CenterVertically){Text("Показывать названия товаров в уведомлениях",Modifier.weight(1f));Switch(preferences.showDetails,{preferences.details(it)})}
        }
        if(preferences.enabled){
            Row(verticalAlignment=Alignment.CenterVertically){Text("Напоминать заранее, дней",Modifier.weight(1f));IconButton(onClick={preferences.leadDays(preferences.leadDays-1)},enabled=preferences.leadDays>0){Icon(Icons.Default.Remove,"Меньше")};Text(preferences.leadDays.toString());IconButton(onClick={preferences.leadDays(preferences.leadDays+1)},enabled=preferences.leadDays<7){Icon(Icons.Default.Add,"Больше")}}
            Row(verticalAlignment=Alignment.CenterVertically){Text("Повторять после истечения срока",Modifier.weight(1f));Switch(preferences.overdue,{preferences.overdue(it)})}
        }
        Text("Напоминания проверяются в выбранное дневное время. Android может задерживать их при энергосбережении.")
        Primary("Настройки уведомлений"){
            if(Build.VERSION.SDK_INT>=33&&ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)(ctx as? ComponentActivity)?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
            else ctx.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,ctx.packageName))
        }

            }
            "Оформление"->{
        listOf("system" to "Как в системе","light" to "Светлая","dark" to "Тёмная").forEach{(key,label)->
            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().clickable{preferences.theme(key)}){RadioButton(preferences.theme==key,{preferences.theme(key)});Text(label)}
        }

            }
            "Справочники"->CatalogSettings(catalog,vm,initialExpanded=true)
            "Мои данные"->{BackupPanel();ClearLocalData(vm)}
            "Реклама"->AdsSettings(preferences)
            "Юридическая информация"->LegalMenu(open)
            "Конфиденциальность","Условия использования","Реклама и сторонние сервисы","Разработчик и контакты"->LegalDocument(page)
            "О приложении"->{
                Text("Забери "+BuildConfig.VERSION_NAME,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("Кандидат на релиз. Заказы хранятся на устройстве. Интернет и учётная запись для основных функций не нужны. Реклама необязательна.")
                SettingsLink(Icons.Default.Feedback,"Обратная связь","Не отправляйте чеки, пароли и личные сведения"){runCatching{ctx.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/epbloger/zaberi/issues")))}}
                SettingsLink(Icons.Default.Gavel,"Юридическая информация","Документы приложения"){open("Юридическая информация")}
            }
        }
    }
}
@Composable internal fun SettingsLink(icon:ImageVector,title:String,subtitle:String,click:()->Unit){
    OutlinedCard(onClick=click,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(icon,null,Modifier.padding(11.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
    Text("Копия заказов, операций, истории, справочников и фото защищена паролем. Восстановление заменяет текущие заказы. Пароль восстановить нельзя.")
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

@Composable private fun OrderFilters(orders:List<Order>,shop:String,setShop:(String)->Unit,category:String,setCategory:(String)->Unit,grouped:Boolean,setGrouped:(Boolean)->Unit){
    var expanded by rememberSaveable{mutableStateOf(false)}
    Column{
        Row(verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={expanded=!expanded}){Icon(Icons.Default.FilterList,null);Text("Фильтры"+if(shop.isNotBlank()||category.isNotBlank())" •" else "")}
            Spacer(Modifier.weight(1f))
            TextButton(onClick={setGrouped(!grouped)}){Text(if(grouped)"Обычный список" else "По ПВЗ")}
        }
        if(expanded){
            Text("Магазин",style=MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){FilterChip(shop.isBlank(),{setShop("")},{Text("Все")});orders.map{it.store}.distinct().sorted().forEach{v->FilterChip(shop==v,{setShop(v)},{Text(v)})}}
            Text("Категория",style=MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){FilterChip(category.isBlank(),{setCategory("")},{Text("Все")});orders.map{it.category}.filter{it.isNotBlank()}.distinct().sorted().forEach{v->FilterChip(category==v,{setCategory(v)},{Text(v)})}}
        }
    }
}
@Composable private fun CatalogSettings(catalog:List<CatalogEntry>,vm:Model,initialExpanded:Boolean=false){
    var expanded by rememberSaveable{mutableStateOf(initialExpanded)}
    var kind by rememberSaveable{mutableStateOf("store")}
    var parent by rememberSaveable{mutableStateOf("")}
    var value by rememberSaveable{mutableStateOf("")}
    var lat by rememberSaveable{mutableStateOf<Double?>(null)}
    var lon by rememberSaveable{mutableStateOf<Double?>(null)}
    var deleting by rememberSaveable{mutableStateOf<String?>(null)}
    HorizontalDivider()
    TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Скрыть справочники" else "Магазины, категории и пункты выдачи")}
    if(expanded){
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("store" to "Магазины","category" to "Категории","point" to "ПВЗ").forEach{(k,label)->FilterChip(kind==k,{kind=k;value="";parent="";lat=null;lon=null},{Text(label)})}}
        if(kind=="point"){
            Field(parent,{if(parent!=it){value="";lat=null;lon=null};parent=it},"Магазин пункта выдачи")
            PickupSelector(parent,value,lat,lon,catalog){address,y,x->value=address;lat=y;lon=x}
        }else Field(value,{value=it},"Добавить название")
        Button(enabled=value.isNotBlank()&&value.length<=200&&(kind!="point"||parent.isNotBlank()),onClick={vm.addCatalog(kind,parent,value,lat,lon);value="";lat=null;lon=null}){Text("Добавить")}
        catalog.filter{it.kind==kind}.forEach{entry->
            Row(verticalAlignment=Alignment.CenterVertically){
                Text((if(entry.kind=="point")entry.parent+" · " else "")+entry.value,Modifier.weight(1f))
                IconButton(onClick={deleting=entry.id}){Icon(Icons.Default.Delete,"Убрать из подсказок")}
            }
        }
    }
    if(deleting!=null)AlertDialog(onDismissRequest={deleting=null},title={Text("Убрать из подсказок?")},text={Text("Существующие заказы не изменятся.")},confirmButton={TextButton(onClick={deleting?.let{vm.forgetCatalog(it)};deleting=null}){Text("Убрать")}},dismissButton={TextButton(onClick={deleting=null}){Text("Отмена")}})
}

@Composable private fun ClearLocalData(vm:Model){
    var confirm by remember{mutableStateOf(false)}
    HorizontalDivider()
    Text("Удаление данных",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    Text("Можно очистить все заказы, фото, историю, финансовые операции и сохранённые справочники. Ранее экспортированные файлы останутся в выбранных местах.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick={confirm=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.error);Spacer(Modifier.width(8.dp));Text("Удалить все данные заказов",color=MaterialTheme.colorScheme.error)}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Удалить все заказы?")},text={Text("Это действие нельзя отменить без резервной копии. Настройки оформления и уведомлений сохранятся.")},confirmButton={TextButton(onClick={vm.clearData();confirm=false}){Text("Удалить всё",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={confirm=false}){Text("Отмена")}})
}
