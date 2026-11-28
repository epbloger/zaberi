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

class MainActivity:ComponentActivity(){
    private val model by lazy{androidx.lifecycle.ViewModelProvider(this)[Model::class.java]}
    private var openId by mutableStateOf<String?>(null)
    private fun handle(i:Intent?){
        sharedProductRequest(i)?.let{model.receiveShare(it)}
        if(i?.action !in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE))openId=runCatching{i?.getStringExtra("orderId")}.getOrNull()
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        if(savedInstanceState==null)handle(intent) else openId=savedInstanceState.getString("pending_order_id")
        Reminders.enqueue(this)
        setContent{
            val preferences=remember{Preferences(applicationContext)}
            val dark=when(preferences.theme){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
            MaterialTheme(colorScheme=if(dark)darkColorScheme(primary=Color(0xFFA9B2FF))else lightColorScheme(primary=Brand,onPrimary=Color.White,primaryContainer=Color(0xFFE6E9FF),onPrimaryContainer=Color(0xFF202B78),background=Color(0xFFF6F7FB),surface=Color.White,surfaceVariant=Color(0xFFF0F1F6),onSurfaceVariant=Color(0xFF4E5263),outlineVariant=Color(0xFFE0E3EC))){
                SafeAppViewport{MapOverlayHost{Home(openId,{openId=null},preferences,model)}}
            }
        }
    }
    override fun onSaveInstanceState(outState:Bundle){
        outState.putString("pending_order_id",openId)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);handle(intent)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun Home(openId:String?,consumeId:()->Unit,preferences:Preferences,vm:Model=viewModel()){
    val shared by vm.pendingShare.collectAsStateWithLifecycle()
    val productImport by vm.productImport.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val tx by vm.transactions.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    var tab by rememberSaveable{mutableIntStateOf(0)}
    var selected by rememberSaveable{mutableStateOf<String?>(null)}
    var add by rememberSaveable{mutableStateOf(false)}
    var editing by rememberSaveable{mutableStateOf(false)}
    val saving by vm.formSaving.collectAsStateWithLifecycle()
    val savedForm by vm.savedForm.collectAsStateWithLifecycle()
    var imported by rememberSaveable{mutableStateOf("")}
    var shareId by rememberSaveable{mutableStateOf<String?>(null)}
    var guideStep by rememberSaveable{mutableIntStateOf(if(!preferences.tutorialSeen&&shared==null&&openId==null)0 else -1)}
    var guidePractice by rememberSaveable{mutableStateOf(false)}
    fun clearImport(){vm.clearProductImport(shareId);shareId=null;imported=""}
    fun stopGuide(){guideStep=-1;guidePractice=false;preferences.finishTutorial()}
    var settingsPage by rememberSaveable{mutableStateOf("")}
    var searchVisible by rememberSaveable{mutableStateOf(false)}
    val snack=remember{SnackbarHostState()}
    val ctx=LocalContext.current
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    val importScope=rememberCoroutineScope()
    val adsReady=rememberAdsReady(preferences.personalizedAds,AdPrivacyPolicy.ageRestricted(BuildConfig.STORE_AGE,preferences.adultAdsConfirmed))
    val adController=remember(ctx,preferences.personalizedAds,preferences.adultAdsEligible,adsReady){
        if(adsReady&&ctx is ComponentActivity&&BuildConfig.INTERSTITIAL_ID.isNotBlank())runCatching{InterstitialController(ctx,BuildConfig.INTERSTITIAL_ID)}.getOrNull() else null
    }
    DisposableEffect(adController){runCatching{adController?.preload()};onDispose{adController?.close()}}
    var adOpportunity by remember{mutableStateOf(false)}
    var openedStatus by rememberSaveable{mutableStateOf<String?>(null)}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)Reminders.enqueue(ctx)}
    LaunchedEffect(guideStep,guidePractice,add,editing,shared?.id){
        if(guideStep<0&&!guidePractice&&!add&&!editing&&shared==null&&Build.VERSION.SDK_INT>=33&&preferences.enabled&&ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!preferences.notificationAsked){
            preferences.markNotificationAsked()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit){vm.errors.collect{snack.showSnackbar(it)}}
    fun openShared(request:SharedProductRequest){
        val accepted=vm.acceptShare(request.id,preferences.onlineProductImport)?:return
        guideStep=-1;guidePractice=false
        selected=null;editing=false;settingsPage="";tab=0
        imported=accepted.text;shareId=accepted.id;add=true
    }
    LaunchedEffect(savedForm){savedForm?.let{id->if(add&&!editing&&shared==null&&openId==null&&guideStep<0&&!guidePractice)adOpportunity=true;add=false;editing=false;tab=0;vm.consumeSavedForm(id)}}
    LaunchedEffect(shared?.id,saving,add,editing){if(!saving&&!add&&!editing)shared?.let{openShared(it)}}
    LaunchedEffect(add,shareId,productImport?.requestId){
        val id=shareId
        if(add&&id!=null&&productImport==null)vm.restoreProductImport(id,imported)
    }
    LaunchedEffect(add,editing,shareId){
        if(!add&&!editing){
            if(shareId!=null)clearImport()
            if(guidePractice){guidePractice=false;guideStep=GuideStep.ORDERS.ordinal;tab=0}
        }
    }
    LaunchedEffect(openId,orders,saving,add,editing,shared?.id){if(!saving&&!add&&!editing&&shared==null&&openId!=null&&orders.any{it.id==openId}){guideStep=-1;guidePractice=false;add=false;editing=false;settingsPage="";tab=0;selected=openId;openedStatus=null;consumeId()}}
    val order=orders.firstOrNull{it.id==selected}
    fun closeDetail(){
        if(tab==0&&openedStatus!=null&&order!=null&&openedStatus!=order.status&&order.state in listOf(Status.READY,Status.RECEIVED,Status.DONE)&&!order.returned&&!order.cancelled)adOpportunity=true
        openedStatus=null;selected=null
    }
    LaunchedEffect(adOpportunity,add,editing,selected,tab,shared,openId){
        if(adOpportunity){
            adOpportunity=false
            if(!add&&!editing&&selected==null&&tab==0&&shared==null&&openId==null&&settingsPage.isEmpty()&&guideStep<0&&!guidePractice)runCatching{adController?.atNaturalPause()}
        }
    }
    BackHandler(enabled=guideStep>=0||add||editing||order!=null||settingsPage.isNotEmpty()||searchVisible){if(saving)Unit else if(guideStep>=0)stopGuide() else if(add)add=false else if(editing)editing=false else if(order!=null)closeDetail() else if(settingsPage.isNotEmpty())settingsPage=if(settingsPage in listOf("Конфиденциальность","Условия использования","Реклама и сторонние сервисы","Разработчик и контакты"))"Юридическая информация" else "" else searchVisible=false}
    val nav=listOf("Заказы","Календарь","Статистика","Настройки")
    val icons=listOf(Icons.Default.Inventory2,Icons.Default.CalendarMonth,Icons.Default.BarChart,Icons.Default.Settings)
    if(guideStep>=0&&!add&&!editing&&shared==null&&openId==null){
        key(guideStep){
            val step=GuideStep.entries[guideStep.coerceIn(0,GuideStep.entries.lastIndex)]
            GuidePanel(step,next={
                if(step==GuideStep.SETTINGS){stopGuide();tab=0}
                else{guideStep++;tab=GuideStep.entries[guideStep].tab}
            },back={if(guideStep>0){guideStep--;tab=GuideStep.entries[guideStep].tab}},skip={stopGuide();tab=0},practice={
                guideStep=-1;guidePractice=true;clearImport();tab=0;add=true
            })
        }
    }else if(add||editing&&order!=null){
        Box(Modifier.fillMaxSize()){
            key(if(add)"new:"+(shareId?:"manual") else order?.id){
                val state=productImport?.takeIf{add&&it.requestId==shareId}
                OrderForm(if(add)null else order,if(add)imported else "",catalog,orders,saving,{if(!saving){add=false;editing=false}},
                    sharedState=state,enableLookup=state?.let{current->{preferences.onlineProductImport(true);vm.enrichSharedProduct(current.requestId)}},
                    cancelLookup=state?.let{current->{vm.cancelProductImport(current.requestId)}},
                    renderedLookup=state?.let{current->{result:ProductLookup->vm.applyRenderedProduct(current.requestId,result)}},practice=guidePractice){o,photo->
                    if(guidePractice){add=false;editing=false}else vm.saveForm(o,!add,photo)
                }
            }
            SnackbarHost(snack,Modifier.align(Alignment.TopCenter))
        }
    }else Scaffold(
        contentWindowInsets=WindowInsets(0,0,0,0),
        containerColor=MaterialTheme.colorScheme.background,
        snackbarHost={SnackbarHost(snack)},
        topBar={TopAppBar(
            windowInsets=WindowInsets(0,0,0,0),
            title={Text(order?.name?:if(tab==0)"ЗАБЕРИ." else if(tab==3&&settingsPage.isNotEmpty())settingsPage else nav[tab],fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)},
            navigationIcon={if(order!=null||settingsPage.isNotEmpty())IconButton(onClick={if(order!=null)closeDetail() else settingsPage=if(settingsPage in listOf("Конфиденциальность","Условия использования","Реклама и сторонние сервисы","Разработчик и контакты"))"Юридическая информация" else ""}){Icon(Icons.Default.ArrowBack,"Назад")}},
            actions={if(order!=null)IconButton(onClick={editing=true}){Icon(Icons.Default.Edit,"Редактировать")}else if(tab==0){
                IconButton(onClick={
                    val text=clipboard.getText()?.text.orEmpty().take(8192)
                    val seed=sharedProductSeed(text)
                    if(seed.link.isNotBlank()&&seed.store.isNotBlank())vm.receiveShare(SharedProductRequest(uid(),text,null))
                    else importScope.launch{snack.showSnackbar("Скопируйте ссылку на товар в магазине")}
                },modifier=Modifier.testTag("paste_product_link")){Icon(Icons.Default.ContentPaste,"Импорт ссылки из буфера")}
                IconButton(onClick={searchVisible=!searchVisible}){Icon(if(searchVisible)Icons.Default.Close else Icons.Default.Search,"Поиск")}
            }},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background)
        )},
        bottomBar={if(order==null&&settingsPage.isEmpty())NavigationBar(windowInsets=WindowInsets(0,0,0,0),containerColor=MaterialTheme.colorScheme.surface){nav.forEachIndexed{i,s->NavigationBarItem(selected=tab==i,onClick={tab=i;searchVisible=false},icon={Icon(icons[i],s)},label={Text(s,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)})}}},
        floatingActionButton={if(order==null&&tab==0)ExtendedFloatingActionButton(containerColor=actionColor(ActionTone.DELIVERY),contentColor=Color.White,onClick={clearImport();add=true},modifier=Modifier.testTag("new_order"),icon={Icon(Icons.Default.Add,null)},text={Text("Добавить заказ")})}
    ){pad->
        Box(Modifier.fillMaxSize().padding(pad)){
            when{order!=null->Detail(order,history.filter{it.orderId==order.id},vm,{selected=null},{editing=true});tab==0->key(preferences.adultAdsEligible,preferences.personalizedAds){Orders(orders,{selected=it.id;openedStatus=it.status},{o,event->vm.act(o.id,event,expectedState=o.state)},searchVisible,{clearImport();add=true},preferences.personalizedAds,adsReady,vm::cover)};tab==1->CalendarPage(orders,{selected=it.id;openedStatus=it.status});tab==2->Stats(orders,tx);else->SettingsPage(preferences,catalog,vm,settingsPage,{if(it=="Обучение"){settingsPage="";guideStep=0;tab=0}else settingsPage=it})}
        }
    }
    val waitingShare=shared
    if(waitingShare!=null&&(add||editing)&&!saving){
        AlertDialog(onDismissRequest={vm.dismissShare(waitingShare.id)},
            title={Text("Открыть новый товар?")},text={Text("У вас открыт несохранённый заказ. Если открыть новую карточку, изменения текущего черновика не сохранятся.")},
            confirmButton={TextButton(onClick={clearImport();openShared(waitingShare)}){Text("Открыть новый")}},
            dismissButton={TextButton(onClick={vm.dismissShare(waitingShare.id)}){Text("Оставить черновик")}})
    }

}

