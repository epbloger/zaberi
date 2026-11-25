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
import androidx.compose.ui.draw.clip
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

@Composable internal fun Detail(o:Order,history:List<History>,vm:Model,close:()->Unit){
    var action by rememberSaveable(o.id){mutableStateOf("")}
    var deleting by remember(o.id){mutableStateOf(false)}
    var date by rememberSaveable(o.id){mutableStateOf("")}
    var amount by rememberSaveable(o.id){mutableStateOf("")}
    val ctx=LocalContext.current
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    fun ask(event:String,initial:Long?=null){action=event;date=when(event){"pay","refund"->today();"adjust_refund"->o.deadline.orEmpty();else->""};amount=number(initial)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UiSpacing.Page),verticalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
        Text(o.store,color=MaterialTheme.colorScheme.primary)
        Text(money(o.price),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
        Metric(o.state.label,if(o.paid>0)"Оплачено: "+money(o.paid) else "Оплата не отмечена",Modifier.fillMaxWidth())
        if(o.state.deadlineLabel!=null)Text(o.state.deadlineLabel+": "+dateText(o.deadline))
        if(o.point.isNotBlank()){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text("Пункт выдачи",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(o.point,style=MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(o.point))}){Icon(Icons.Default.ContentCopy,"Скопировать адрес ПВЗ")}
            }
            OutlinedButton(onClick={openPickupMap(ctx,o.point,o.pointLat,o.pointLon)},modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch)){Icon(Icons.Default.Map,null);Spacer(Modifier.width(8.dp));Text("Открыть на карте")}
        }
        if(o.category.isNotBlank())Text("Категория: "+o.category)
        if(o.note.isNotBlank())Text(o.note)
        if(o.link.startsWith("https://")||o.link.startsWith("http://"))OutlinedButton(onClick={runCatching{ctx.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(o.link)))}},modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch)){Text("Открыть ссылку")}
        if(o.state==Status.REFUND)Metric("Осталось вернуть",money(remaining(o.expectedRefund,o.receivedRefund)),Modifier.fillMaxWidth())
        when(o.state){
            Status.TRANSIT->{Primary("Заказ прибыл"){ask("arrived")};OutlinedButton(onClick={ask("received")},modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch)){Text("Уже получил")};SecondaryAction("Отменить заказ",{ask("cancel",o.paid)})}
            Status.READY->{Primary("Забрал заказ"){ask("received")};SecondaryAction("Отменить заказ",{ask("cancel",o.paid)})}
            Status.RECEIVED->{Primary("Оставить себе"){vm.act(o.id,"keep")};OutlinedButton(onClick={ask("return")},modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch)){Text("Вернуть товар")};SecondaryAction("Исправить: заказ ещё не получен",{ask("undo_received")})}
            Status.RETURN_PLANNED->{Primary("Товар возвращён"){ask("returned",o.paid)};SecondaryAction("Отказаться от возврата",{ask("cancel_return")})}
            Status.REFUND->{Primary("Деньги поступили"){ask("refund",remaining(o.expectedRefund,o.receivedRefund))};OutlinedButton(onClick={ask("adjust_refund",o.expectedRefund)},modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch)){Text("Уточнить сумму и срок возврата")}}
            Status.DONE->if(!o.returned&&!o.cancelled){OutlinedButton(onClick={ask("return")},modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch)){Text("Вернуть товар")};SecondaryAction("Вернуть в активные",{ask("reopen")})}
            Status.CANCELLED->Unit
        }
        if(canMarkPayment(o.state,o.paid,o.returned,o.cancelled))OutlinedButton(onClick={ask("pay",o.price)},modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch)){Text("Отметить оплату")}
        key(o.id){Photos(o.id,vm)}
        HorizontalDivider()
        Text("История заказа",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        history.forEach{Text(it.date.take(10)+" · "+it.label,style=MaterialTheme.typography.bodyMedium)}
        SecondaryAction("Удалить заказ",{if(!deleting)ask("delete")},Modifier.testTag("delete_order"),destructive=true)
    }
    if(action.isNotEmpty()){
        val a=action
        val monetary=a in listOf("refund","pay","adjust_refund")||(a in listOf("cancel","returned")&&o.paid>0)
        val needsDate=a in listOf("arrived","return","undo_received","adjust_refund","pay","refund")||(a in listOf("returned","cancel")&&o.paid>0)
        val parsed=parseMoney(amount)
        val valid=(!needsDate||validDate(date))&&(a !in listOf("pay","refund")||validOperationDate(date,LocalDate.now()))&&(!monetary||(parsed!=null&&when(a){"refund"->parsed in 1..remaining(o.expectedRefund,o.receivedRefund);"pay"->parsed>0&&(o.price==null||parsed==o.price);"adjust_refund"->refundTargetValid(o.paid,o.receivedRefund,parsed);else->parsed in 0..o.paid}))
        AlertDialog(onDismissRequest={if(!deleting)action=""},title={Text(when(a){"arrived"->"Заказ прибыл";"return"->"Запланировать возврат";"returned"->"Товар возвращён";"cancel"->"Отменить заказ?";"refund"->"Полученные деньги";"pay"->"Оплата";"delete"->"Удалить заказ?";"adjust_refund"->"Уточнить возврат";"undo_received"->"Заказ ещё не получен";"cancel_return"->"Отказаться от возврата?";"reopen"->"Вернуть в активные?";else->"Заказ получен?"})},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(UiSpacing.Related)){
                if(a=="delete")Text("Заказ, фотографии, история и связанные операции будут удалены без возможности отмены. Восстановить данные можно из ранее созданной резервной копии.")
                if(a=="adjust_refund")Text("Укажите общую сумму возврата, включая уже полученные деньги: "+money(o.receivedRefund)+". Если остаток равен нулю, возврат завершится.")
                if(monetary)Field(amount,{amount=it},if(a=="refund")"Получено, ₽" else if(a=="pay")"Оплачено, ₽" else "Ожидается возврат, ₽",true)
                if(needsDate)DateField(date,{date=it},when(a){"arrived","undo_received"->"Забрать до";"return"->"Вернуть до";"pay"->"Дата оплаты";"refund"->"Дата поступления";else->"Ожидается возврат"})
                if(!monetary&&!needsDate&&a!="delete")Text("Подтвердите изменение статуса.")
            }
        },confirmButton={Button(enabled=valid&&!deleting,colors=if(a=="delete")ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error,contentColor=MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(),onClick={
            when(a){"delete"->{deleting=true;vm.remove(o.id){success->deleting=false;if(success){action="";close()}}};"refund"->vm.refund(o.id,parsed!!,date);"pay"->vm.pay(o.id,parsed!!,date);"adjust_refund"->vm.adjustRefund(o.id,parsed!!,date.trim().takeIf{it.isNotEmpty()});else->vm.act(o.id,a,date.trim().takeIf{it.isNotEmpty()},parsed)}
            if(a!="delete")action=""
        },modifier=Modifier.testTag(if(a=="delete")"confirm_delete_order" else "confirm_order_action")){Text(if(a=="delete")"Удалить" else "Подтвердить")}},dismissButton={TextButton(enabled=!deleting,onClick={action=""}){Text("Отмена")}})
    }
}
@Composable internal fun Photos(id:String,vm:Model){
    val flow=remember(id){vm.photos(id)}
    val photos by flow.collectAsStateWithLifecycle(initialValue=emptyList())
    var preview by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var pendingCamera by rememberSaveable{mutableStateOf<String?>(null)}
    var importing by remember{mutableStateOf(false)}
    var sourceError by rememberSaveable{mutableStateOf("")}
    fun importPhoto(uri:android.net.Uri,temp:java.io.File?=null){
        importing=true;sourceError=""
        val job=vm.addPhoto(id,uri)
        job.invokeOnCompletion{temp?.delete()}
        scope.launch{job.join();importing=false}
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)importPhoto(uri)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){success->
        val file=CameraPhoto.restore(context,pendingCamera)
        pendingCamera=null
        if(success&&file!=null&&file.length()>0L){
            try{importPhoto(CameraPhoto.uri(context,file),file)}
            catch(e:Exception){file.delete();sourceError="Не удалось открыть снимок."}
        }else{
            file?.delete()
            if(success)sourceError="Камера не сохранила снимок. Попробуйте ещё раз."
        }
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text("Фото заказа",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Text("${photos.size}/5",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(photos.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Related)){
        photos.forEach{a->
            val bitmap=remember(a.id){android.graphics.BitmapFactory.decodeByteArray(a.jpeg,0,a.jpeg.size)?.asImageBitmap()}
            if(bitmap!=null)Image(bitmap,"Фото заказа",Modifier.size(90.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable{preview=a.id},contentScale=androidx.compose.ui.layout.ContentScale.Crop)
        }
    }
    if(photos.size<5){
        val enabled=!importing&&pendingCamera==null
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
            OutlinedButton(enabled=enabled,onClick={
                sourceError=""
                try{picker.launch("image/*")}catch(e:Exception){sourceError="Галерея недоступна."}
            },modifier=Modifier.weight(1f).heightIn(min=UiSpacing.Touch).testTag("photo_gallery")){Text("Галерея")}
            OutlinedButton(enabled=enabled,onClick={
                sourceError=""
                try{
                    val file=CameraPhoto.create(context)
                    pendingCamera=file.name
                    camera.launch(CameraPhoto.uri(context,file))
                }catch(e:Exception){
                    CameraPhoto.restore(context,pendingCamera)?.delete();pendingCamera=null
                    sourceError="Камера недоступна. Можно выбрать фото из галереи."
                }
            },modifier=Modifier.weight(1f).heightIn(min=UiSpacing.Touch).testTag("photo_camera")){Text("Камера")}
        }
    }
    if(importing)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(sourceError.isNotBlank())Text(sourceError,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    val current=photos.firstOrNull{it.id==preview}
    if(current!=null){
        val bitmap=remember(current.id){android.graphics.BitmapFactory.decodeByteArray(current.jpeg,0,current.jpeg.size)?.asImageBitmap()}
        AlertDialog(onDismissRequest={preview=null},title={Text("Фото заказа")},text={if(bitmap!=null)Image(bitmap,"Фото заказа",Modifier.fillMaxWidth())},
            confirmButton={TextButton(onClick={vm.deletePhoto(current.id);preview=null}){Text("Удалить",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={preview=null}){Text("Закрыть")}})
    }
}
