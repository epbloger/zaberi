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

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun OrderForm(old:Order?,imported:String,catalog:List<CatalogEntry>,orders:List<Order>,saving:Boolean,close:()->Unit,save:(Order)->Unit){
    val draftId=rememberSaveable{old?.id?:uid()}
    val draftCreated=rememberSaveable{old?.created?:today()}
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
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    AdaptiveActionScreen(title=if(old==null)"Новый заказ" else "Редактировать",close=close,action={
        Button(colors=actionButtonColors(ActionTone.DELIVERY),enabled=valid&&!saving,onClick={
                val d=if(state.deadlineLabel!=null)deadline.trim().takeIf{it.isNotEmpty()}else null
                val o=old?.copy(name=name.trim(),store=store.trim(),price=parsed,deadline=d,point=point.trim(),link=link.trim(),note=note.trim(),category=category.trim(),pointLat=pointLat,pointLon=pointLon)?:Order(draftId,name.trim(),store.trim(),parsed,if(paid)parsed?:0 else 0,status,d,point.trim(),link.trim(),note.trim(),draftCreated,category=category.trim(),pointLat=pointLat,pointLon=pointLon)
                focus.clearFocus();keyboard?.hide();save(o)
            },modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch).testTag("save_order")){Text(if(saving)"Сохраняем…" else if(old==null)"Добавить заказ" else "Сохранить",maxLines=2)}
    }){
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=LocalActionPagePadding.current,vertical=LocalActionPagePadding.current),verticalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
            Text("Основное",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Название, магазин и текущий статус",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name,{name=it},modifier=Modifier.fillMaxWidth().testTag("order_name"),label={Text("Название товара")},singleLine=true)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Related)){(catalog.filter{it.kind=="store"}.map{it.value}+stores).distinct().take(30).forEach{s->FilterChip(store==s,{if(store!=s){point="";pointLat=null;pointLon=null};store=s},{Text(s)})}}
            if(store !in stores || advanced)OutlinedTextField(store,{if(store!=it){point="";pointLat=null;pointLon=null};store=it},modifier=Modifier.fillMaxWidth().testTag("order_store"),label={Text("Название магазина")},singleLine=true)
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
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Related)){(catalog.filter{it.kind=="category"}.map{it.value}+categories).distinct().take(30).forEach{v->FilterChip(category==v,{category=if(category==v)"" else v},{Text(v)})}}
                Field(category,{category=it},"Категория")
                Field(link,{link=it;duplicateConfirmed=false},"Ссылка на товар")
                OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text("Заметка")})
            }
            if(duplicate)Row(verticalAlignment=Alignment.CenterVertically){Checkbox(duplicateConfirmed,{duplicateConfirmed=it});Text("Похожий заказ уже есть. Добавить ещё один?")}
        }
    }
}
