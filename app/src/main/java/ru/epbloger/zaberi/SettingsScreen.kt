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

@Composable internal fun SettingsPage(preferences:Preferences,catalog:List<CatalogEntry>,vm:Model,page:String,open:(String)->Unit){
    val ctx=LocalContext.current
    val scroll=rememberScrollState()
    LaunchedEffect(page){scroll.scrollTo(0)}
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(UiSpacing.Page),verticalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
        when(page){
            ""->{
                Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(UiSpacing.CardRadius),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
                    Row(Modifier.fillMaxWidth().padding(UiSpacing.Card),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
                        Icon(Icons.Default.Inventory2,null,Modifier.size(38.dp));Column{Text("ЗАБЕРИ.",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("Все покупки под контролем",style=MaterialTheme.typography.bodySmall)}
                    }
                }
                SettingsLink(Icons.Default.HelpOutline,"Обучение","Как добавлять заказы через «Поделиться»"){open("Обучение")}
                SettingsLink(Icons.Default.Share,"Автозаполнение товара","Загрузка цены и фотографии из карточки"){open("Автозаполнение товара")}
                SettingsLink(Icons.Default.Notifications,"Уведомления","Сроки и конфиденциальность"){open("Уведомления")}
                SettingsLink(Icons.Default.Palette,"Оформление","Светлая, тёмная и системная тема"){open("Оформление")}
                SettingsLink(Icons.Default.Store,"Справочники","Магазины, категории и ПВЗ"){open("Справочники")}
                SettingsLink(Icons.Default.Backup,"Мои данные","Резервное копирование и CSV"){open("Мои данные")}
                SettingsLink(Icons.Default.Gavel,"Юридическая информация","Конфиденциальность, условия и реклама"){open("Юридическая информация")}
                SettingsLink(Icons.Default.Campaign,"Реклама","Информация о показах и данных"){open("Реклама")}
                SettingsLink(Icons.Default.Info,"О приложении","Версия и обратная связь"){open("О приложении")}
            }
            "Автозаполнение товара"->{
                Text("Цена и фото из магазина",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("При импорте через «Поделиться» ЗАБЕРИ. может открыть общедоступную карточку товара и загрузить недостающую цену и фотографию. Запросы отправляются магазину и его сервису изображений; они получают IP-адрес и технические сведения. Вход в аккаунт не требуется.")
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text("Автоматически дополнять карточки",Modifier.weight(1f))
                    Switch(preferences.onlineProductImport,{preferences.onlineProductImport(it)})
                }
                Text("Если автоматический запрос не удался, можно открыть публичную карточку во встроенном браузере и перенести доступные данные. Входить в аккаунт не нужно. Без интернета ручной учёт работает по-прежнему.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
            "Мои данные"->{BackupPanel();MapCacheSettings();ClearLocalData(vm)}
            "Реклама"->AdsSettings(preferences)
            "Юридическая информация"->LegalMenu(open)
            "Конфиденциальность","Условия использования","Реклама и сторонние сервисы","Разработчик и контакты"->LegalDocument(page)
            "О приложении"->{
                Text("ЗАБЕРИ. "+BuildConfig.VERSION_NAME,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                Text("Заказы хранятся на устройстве. Интернет и учётная запись для основных функций не нужны. Реклама помогает поддерживать приложение.")
                Text("Moorkis Development",style=MaterialTheme.typography.titleMedium)
                ProjectLinks()
                SettingsLink(Icons.Default.Feedback,"Написать в поддержку",SUPPORT_EMAIL){openSupportEmail(ctx)}
                SettingsLink(Icons.Default.Gavel,"Юридическая информация","Документы приложения"){open("Юридическая информация")}
            }
        }
    }
}
@Composable internal fun SettingsLink(icon:ImageVector,title:String,subtitle:String,click:()->Unit){
    OutlinedCard(onClick=click,shape=RoundedCornerShape(UiSpacing.CardRadius),modifier=Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){
        Row(Modifier.padding(UiSpacing.Card),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
            Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer){Icon(icon,null,Modifier.padding(UiSpacing.Related),tint=MaterialTheme.colorScheme.onPrimaryContainer)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(UiSpacing.Tight)){Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun BackupPanel(){
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
                message=when(kind){
                    "export"->{PrivateFiles.export(ctx,uri,pass);"Защищённая копия сохранена"}
                    "restore"->if(PrivateFiles.restore(ctx,uri,pass))"Данные восстановлены" else "Данные восстановлены, но не удалось обновить напоминания."
                    else->{PrivateFiles.csv(ctx,uri);"Таблица сохранена"}
                }
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
    OrderAction("Создать защищённую копию",Icons.Default.Backup,ActionTone.DELIVERY,enabled=!busy){action="export";password="";confirm=""}
    OrderAction("Восстановить из копии",Icons.Default.Restore,ActionTone.RETURN,filled=false,enabled=!busy){action="restore";password="";confirm=""}
    OrderAction("Экспорт операций CSV",Icons.Default.FileDownload,ActionTone.NEUTRAL,filled=false,enabled=!busy){action="csv"}
    if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    if(message.isNotBlank())Text(message)
    if(action.isNotEmpty()){
        val kind=action
        AlertDialog(onDismissRequest={action="";password="";confirm=""},title={Text(when(kind){"export"->"Пароль резервной копии";"restore"->"Заменить текущие данные?";else->"Экспорт операций"})},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(UiSpacing.Related)){
                if(kind=="csv")Text("CSV не зашифрован и содержит названия покупок и суммы. Сохраняйте его только в доверенном месте.")
                else{
                    if(kind=="restore")Text("Все текущие заказы, операции и фото будут заменены содержимым копии. Сначала сохраните текущие данные, если они нужны.")
                    OutlinedTextField(password,{password=it},label={Text("Пароль (от 8 символов)")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                    if(kind=="export")OutlinedTextField(confirm,{confirm=it},label={Text("Повторите пароль")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
                }
            }
        },confirmButton={Button(colors=actionButtonColors(if(kind=="restore")ActionTone.RETURN else ActionTone.DELIVERY),enabled=kind=="csv"||(password.length>=8&&(kind!="export"||password==confirm)),onClick={
            action=""
            when(kind){"export"->export.launch("zaberi-"+today()+".zaberi");"restore"->restore.launch(arrayOf("application/octet-stream","*/*"));else->csv.launch("zaberi-"+today()+".csv")}
        }){Text(when(kind){"export"->"Создать копию";"restore"->"Выбрать копию";else->"Сохранить CSV"})}},dismissButton={TextButton(onClick={action="";password="";confirm=""}){Text("Отмена")}})
    }
}

@Composable internal fun CatalogSettings(catalog:List<CatalogEntry>,vm:Model,initialExpanded:Boolean=false){
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
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Related)){listOf("store" to "Магазины","category" to "Категории","point" to "ПВЗ").forEach{(k,label)->FilterChip(kind==k,{kind=k;value="";parent="";lat=null;lon=null},{Text(label)})}}
        if(kind=="point"){
            Field(parent,{if(parent!=it){value="";lat=null;lon=null};parent=it},"Магазин пункта выдачи")
            PickupSelector(parent,value,lat,lon,catalog){address,y,x->value=address;lat=y;lon=x}
        }else Field(value,{value=it},"Добавить название")
        Button(enabled=value.isNotBlank()&&value.length<=200&&(kind!="point"||parent.isNotBlank()),onClick={vm.addCatalog(kind,parent,value,lat,lon);value="";lat=null;lon=null},modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch)){Text("Добавить")}
        catalog.filter{it.kind==kind}.forEach{entry->
            Row(verticalAlignment=Alignment.CenterVertically){
                Text((if(entry.kind=="point")entry.parent+" · " else "")+entry.value,Modifier.weight(1f))
                IconButton(onClick={deleting=entry.id}){Icon(Icons.Default.Delete,"Убрать из подсказок")}
            }
        }
    }
    if(deleting!=null)AlertDialog(onDismissRequest={deleting=null},title={Text("Убрать из подсказок?")},text={Text("Существующие заказы не изменятся.")},confirmButton={TextButton(onClick={deleting?.let{vm.forgetCatalog(it)};deleting=null}){Text("Убрать")}},dismissButton={TextButton(onClick={deleting=null}){Text("Отмена")}})
}

@Composable internal fun ClearLocalData(vm:Model){
    var confirm by remember{mutableStateOf(false)}
    HorizontalDivider()
    Text("Удаление данных",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    Text("Можно очистить все заказы, фото, историю, финансовые операции и сохранённые справочники. Ранее экспортированные файлы останутся в выбранных местах.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    OrderAction("Удалить все данные заказов",Icons.Default.DeleteOutline,ActionTone.DANGER,filled=false){confirm=true}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Удалить все заказы?")},text={Text("Это действие нельзя отменить без резервной копии. Настройки оформления и уведомлений сохранятся.")},confirmButton={Button(colors=actionButtonColors(ActionTone.DANGER),onClick={vm.clearData();confirm=false}){Text("Удалить всё")}},dismissButton={TextButton(onClick={confirm=false}){Text("Отмена")}})
}

@Composable internal fun MapCacheSettings(){
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var cacheMessage by remember{mutableStateOf("")}
    OutlinedButton(onClick={scope.launch{cacheMessage=if(withContext(kotlinx.coroutines.Dispatchers.IO){clearPickupMapCache(ctx)})"Кэш карты очищен. Сохранённые ПВЗ не изменились." else "Не удалось очистить кэш карты."}},modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch)){Text("Очистить кэш карты")}
    if(cacheMessage.isNotBlank())Text(cacheMessage,style=MaterialTheme.typography.bodySmall)
}
