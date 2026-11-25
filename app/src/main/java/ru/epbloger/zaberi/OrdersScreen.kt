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
import kotlinx.coroutines.withContext
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

@Composable internal fun Orders(orders:List<Order>,open:(Order)->Unit,received:(Order)->Unit,searchVisible:Boolean,addOrder:()->Unit,personalizedAds:Boolean,adsReady:Boolean){
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
    LazyColumn(Modifier.fillMaxSize().testTag("orders_list"),contentPadding=PaddingValues(start=UiSpacing.Page,top=UiSpacing.Page,end=UiSpacing.Page,bottom=UiSpacing.Page+72.dp),verticalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
        item{OrderOverview(active,waiting,{filter="Можно забрать";shop="";category=""},{filter="Возвраты";shop="";category=""})}
        if(searchVisible)item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Товар, магазин или ПВЗ")},leadingIcon={Icon(Icons.Default.Search,null)},shape=RoundedCornerShape(UiSpacing.CardRadius))}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Related)){listOf("Активные","В пути","Можно забрать","Возвраты","История","Все").forEach{FilterChip(selected=filter==it,onClick={filter=it},label={Text(it)})}}}
        if(orders.isNotEmpty())item{OrderFilters(orders,shop,{shop=it},category,{category=it},grouped,{grouped=it})}
        item(key="orders-heading"){
            Column(Modifier.fillMaxWidth()){
                if(BuildConfig.BANNER_ID.isNotBlank())key(personalizedAds,adsReady){OptionalBanner(adsReady)}
                if(visible.isEmpty())EmptyOrders(orders.isEmpty()){if(orders.isEmpty())addOrder() else {filter="Все";shop="";category="";query=""}}
                else Text(if(filter=="Активные")"Мои заказы" else filter,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            }
        }
        if(grouped){
            visible.groupBy{if(it.point.isBlank())"Пункт не указан" else it.store+" · "+it.point}.forEach{(title,group)->
                item(key="group:"+title){Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(UiSpacing.Tight)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("Можно забрать: "+group.count{it.state==Status.READY},style=MaterialTheme.typography.bodySmall)}}
                items(group,key={it.id}){o->OrderCard(o,if(o.state==Status.READY){{confirmId=o.id}}else null){open(o)}}
            }
        }else items(visible,key={it.id}){o->OrderCard(o,if(o.state==Status.READY){{confirmId=o.id}}else null){open(o)}}

    }
    val pending=orders.firstOrNull{it.id==confirmId}
    if(pending!=null)AlertDialog(onDismissRequest={confirmId=null},title={Text("Заказ получен?")},text={Text(pending.name)},confirmButton={Button(colors=actionButtonColors(ActionTone.DELIVERY),onClick={received(pending);confirmId=null}){Text("Забрал")}},dismissButton={TextButton(onClick={confirmId=null}){Text("Отмена")}})
}
@Composable internal fun OrderCard(o:Order,quick:(()->Unit)?=null,open:()->Unit){
    val date=o.deadline?.let{LocalDate.parse(it)}
    val days=date?.let{java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),it)}
    val overdue=isOverdue(o.state,date,LocalDate.now())
    val urgent=days!=null&&days<=1&&o.state in listOf(Status.READY,Status.RETURN_PLANNED,Status.REFUND)
    val statusColor=when{overdue||urgent->MaterialTheme.colorScheme.error;o.state==Status.READY->MaterialTheme.colorScheme.primary;else->MaterialTheme.colorScheme.onSurfaceVariant}
    OutlinedCard(onClick=open,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(UiSpacing.CardRadius),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Column(Modifier.padding(UiSpacing.Card),verticalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
                StoreTile(o.store)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(UiSpacing.Tight)){
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
            if(date!=null)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(UiSpacing.Related)){
                Icon(Icons.Default.Schedule,null,Modifier.size(16.dp),tint=statusColor)
                Text(when{overdue->"Срок истёк · "+dateText(o.deadline);days==0L->(o.state.deadlineLabel?:"Срок")+": сегодня";days==1L->(o.state.deadlineLabel?:"Срок")+": завтра";else->(o.state.deadlineLabel?:"Срок")+": "+dateText(o.deadline)},color=statusColor,style=MaterialTheme.typography.bodySmall)
            }
            if(quick!=null)OrderAction("Забрал заказ",Icons.Default.Check,ActionTone.DELIVERY,click=quick)
        }
    }
}
@Composable internal fun StoreTile(store:String){
    val accent=when(store.lowercase(Locale.ROOT)){"ozon"->Color(0xFF005BDB);"wildberries"->Color(0xFF9B278F);"яндекс маркет"->Color(0xFF9A6900);"авито"->Color(0xFF007E65);"dns"->Color(0xFFB75400);else->MaterialTheme.colorScheme.primary}
    Surface(shape=RoundedCornerShape(16.dp),color=accent.copy(alpha=.12f),modifier=Modifier.size(52.dp)){
        Box(contentAlignment=Alignment.Center){Icon(Icons.Default.Inventory2,null,tint=if(isSystemInDarkTheme())MaterialTheme.colorScheme.primary else accent,modifier=Modifier.size(25.dp))}
    }
}
@Composable internal fun OrderOverview(active:List<Order>,waiting:Long,readyClick:()->Unit,refundClick:()->Unit){
    val urgent=active.count{orderPriority(it.state,it.deadline?.let{d->LocalDate.parse(d)},LocalDate.now())==0}
    Column(verticalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale("ru"))).replaceFirstChar{it.titlecase(Locale("ru"))},style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Card(modifier=Modifier.fillMaxWidth().testTag("overview_summary"),shape=RoundedCornerShape(UiSpacing.CardRadius),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
            Column(Modifier.padding(UiSpacing.Card),verticalArrangement=Arrangement.spacedBy(UiSpacing.Tight)){
                Text("Покупки под контролем",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)
                Text(if(active.isEmpty())"Пока нет активных заказов" else "Активных заказов: "+active.size,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                if(urgent>0)Text("Требуют внимания: "+urgent,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)else Text("Важные сроки будут здесь",style=MaterialTheme.typography.bodySmall)
            }
        }
        AdaptiveCardPair(
            first={modifier->OverviewTile("Можно забрать",active.count{it.state==Status.READY}.toString(),Icons.Default.Inventory2,modifier.testTag("overview_ready"),readyClick)},
            second={modifier->OverviewTile("Ждут возврата",money(waiting),Icons.Default.AccountBalanceWallet,modifier.testTag("overview_refund"),refundClick)}
        )
    }
}
@Composable internal fun OverviewTile(label:String,value:String,icon:ImageVector,modifier:Modifier,click:()->Unit){
    OutlinedCard(onClick=click,modifier=modifier,shape=RoundedCornerShape(UiSpacing.CardRadius),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Column(Modifier.padding(UiSpacing.Card),verticalArrangement=Arrangement.spacedBy(UiSpacing.Related)){
            Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
            Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun EmptyOrders(first:Boolean,action:()->Unit){
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
        Surface(shape=RoundedCornerShape(UiSpacing.CardRadius),color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Default.Inventory2,null,Modifier.padding(22.dp).size(38.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)}
        Text(if(first)"Добавьте первый заказ" else "Ничего не найдено",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        Text(if(first)"Выберите магазин и укажите срок. Забери напомнит, когда пора за покупкой." else "Попробуйте сбросить фильтры.",style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(first)OrderAction("Добавить заказ",Icons.Default.Add,ActionTone.DELIVERY,click=action)else SecondaryAction("Сбросить фильтры",action)
    }
}
@Composable internal fun OrderFilters(orders:List<Order>,shop:String,setShop:(String)->Unit,category:String,setCategory:(String)->Unit,grouped:Boolean,setGrouped:(Boolean)->Unit){
    var expanded by rememberSaveable{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(UiSpacing.Related)){
        Row(verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick={expanded=!expanded}){Icon(Icons.Default.FilterList,null);Text("Фильтры"+if(shop.isNotBlank()||category.isNotBlank())" •" else "")}
            Spacer(Modifier.weight(1f))
            TextButton(onClick={setGrouped(!grouped)}){Text(if(grouped)"Обычный список" else "По ПВЗ")}
        }
        if(expanded){
            Text("Магазин",style=MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Tight)){FilterChip(shop.isBlank(),{setShop("")},{Text("Все")});orders.map{it.store}.distinct().sorted().forEach{v->FilterChip(shop==v,{setShop(v)},{Text(v)})}}
            Text("Категория",style=MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Tight)){FilterChip(category.isBlank(),{setCategory("")},{Text("Все")});orders.map{it.category}.filter{it.isNotBlank()}.distinct().sorted().forEach{v->FilterChip(category==v,{setCategory(v)},{Text(v)})}}
        }
    }
}
