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
                SafeAppViewport{MapOverlayHost{Home(shared,openId,{shared=null},{openId=null},preferences)}}
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
    val saving by vm.formSaving.collectAsStateWithLifecycle()
    val savedForm by vm.savedForm.collectAsStateWithLifecycle()
    var imported by rememberSaveable{mutableStateOf("")}
    var settingsPage by rememberSaveable{mutableStateOf("")}
    var searchVisible by rememberSaveable{mutableStateOf(false)}
    val snack=remember{SnackbarHostState()}
    val ctx=LocalContext.current
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)Reminders.enqueue(ctx)}
    LaunchedEffect(Unit){
        if(Build.VERSION.SDK_INT>=33&&preferences.enabled&&ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!preferences.notificationAsked){
            preferences.markNotificationAsked()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit){vm.errors.collect{snack.showSnackbar(it)}}
    LaunchedEffect(savedForm){savedForm?.let{id->add=false;editing=false;tab=0;vm.consumeSavedForm(id)}}
    LaunchedEffect(shared,saving){if(shared!=null&&!saving){selected=null;editing=false;settingsPage="";tab=0;imported=shared;add=true;consume()}}
    LaunchedEffect(openId,orders,saving){if(!saving&&openId!=null&&orders.any{it.id==openId}){add=false;editing=false;settingsPage="";tab=0;selected=openId;consumeId()}}
    val order=orders.firstOrNull{it.id==selected}
    BackHandler(enabled=add||editing||order!=null||settingsPage.isNotEmpty()||searchVisible){if(saving)Unit else if(add)add=false else if(editing)editing=false else if(order!=null)selected=null else if(settingsPage.isNotEmpty())settingsPage=if(settingsPage in listOf("Конфиденциальность","Условия использования","Реклама и сторонние сервисы","Разработчик и контакты"))"Юридическая информация" else "" else searchVisible=false}
    val nav=listOf("Заказы","Календарь","Статистика","Настройки")
    val icons=listOf(Icons.Default.Inventory2,Icons.Default.CalendarMonth,Icons.Default.BarChart,Icons.Default.Settings)
    if(add||editing&&order!=null){
        Box(Modifier.fillMaxSize()){
            key(if(add)"new:"+imported else order?.id){
                OrderForm(if(add)null else order,if(add)imported else "",catalog,orders,saving,{if(!saving){add=false;editing=false}}){o->vm.saveForm(o,!add)}
            }
            SnackbarHost(snack,Modifier.align(Alignment.TopCenter))
        }
    }else Scaffold(
        contentWindowInsets=WindowInsets(0,0,0,0),
        containerColor=MaterialTheme.colorScheme.background,
        snackbarHost={SnackbarHost(snack)},
        topBar={TopAppBar(
            windowInsets=WindowInsets(0,0,0,0),
            title={Text(order?.name?:if(tab==0)"Забери" else if(tab==3&&settingsPage.isNotEmpty())settingsPage else nav[tab],fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)},
            navigationIcon={if(order!=null||settingsPage.isNotEmpty())IconButton(onClick={if(order!=null)selected=null else settingsPage=if(settingsPage in listOf("Конфиденциальность","Условия использования","Реклама и сторонние сервисы","Разработчик и контакты"))"Юридическая информация" else ""}){Icon(Icons.Default.ArrowBack,"Назад")}},
            actions={if(order!=null)IconButton(onClick={editing=true}){Icon(Icons.Default.Edit,"Редактировать")}else if(tab==0)IconButton(onClick={searchVisible=!searchVisible}){Icon(if(searchVisible)Icons.Default.Close else Icons.Default.Search,"Поиск")}},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background)
        )},
        bottomBar={if(order==null&&settingsPage.isEmpty())NavigationBar(windowInsets=WindowInsets(0,0,0,0),containerColor=MaterialTheme.colorScheme.surface){nav.forEachIndexed{i,s->NavigationBarItem(selected=tab==i,onClick={tab=i;searchVisible=false},icon={Icon(icons[i],s)},label={Text(s,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)})}}},
        floatingActionButton={if(order==null&&tab==0)ExtendedFloatingActionButton(onClick={imported="";add=true},modifier=Modifier.testTag("new_order"),icon={Icon(Icons.Default.Add,null)},text={Text("Добавить заказ")})}
    ){pad->
        Box(Modifier.fillMaxSize().padding(pad)){
            when{order!=null->Detail(order,history.filter{it.orderId==order.id},vm,{selected=null});tab==0->Orders(orders,{selected=it.id},{vm.act(it.id,"received")},searchVisible,{imported="";add=true},preferences.personalizedAds);tab==1->CalendarPage(orders,{selected=it.id});tab==2->Stats(orders,tx);else->SettingsPage(preferences,catalog,vm,settingsPage,{settingsPage=it})}
        }
    }
}

