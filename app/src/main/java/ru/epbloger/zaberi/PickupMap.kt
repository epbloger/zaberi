package ru.epbloger.zaberi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.location.Geocoder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.draw.clipToBounds
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.*

private const val MAX_LAT=85.05112878
internal fun lonToX(lon:Double)=(lon+180.0)/360.0
internal fun latToY(lat:Double):Double {
    val r=Math.toRadians(lat.coerceIn(-MAX_LAT,MAX_LAT))
    return ((1.0-ln(tan(r)+1.0/cos(r))/PI)/2.0).coerceIn(0.0,1.0)
}
internal fun xToLon(x:Double)=x.coerceIn(0.0,1.0)*360.0-180.0
internal fun yToLat(y:Double)=Math.toDegrees(atan(sinh(PI*(1.0-2.0*y.coerceIn(0.0,1.0))))).coerceIn(-MAX_LAT,MAX_LAT)
private fun wrapX(x:Double)=((x%1.0)+1.0)%1.0
private data class Tile(val z:Int,val x:Int,val y:Int)
private data class VisibleTile(val tile:Tile,val rawX:Int)
private object MapTiles {
    @Volatile private var client:OkHttpClient?=null
    @Synchronized private fun http(context:Context):OkHttpClient {
        return client?:OkHttpClient.Builder()
            .cache(Cache(File(context.applicationContext.cacheDir,"map-tiles"),64L*1024*1024))
            .connectTimeout(8,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).callTimeout(15,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
            .build().also{client=it}
    }
    suspend fun image(context:Context,tile:Tile):ImageBitmap?=suspendCancellableCoroutine { continuation->
        val request=Request.Builder().url("https://tile.openstreetmap.org/"+tile.z+"/"+tile.x+"/"+tile.y+".png")
            .header("User-Agent","Zaberi/1.0 (+https://github.com/epbloger/zaberi)").build()
        val call=http(context).newCall(request)
        continuation.invokeOnCancellation{call.cancel()}
        call.enqueue(object:Callback{
            override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resume(null)}
            override fun onResponse(call:Call,response:Response){
                val image=try{
                    response.use{r->
                        if(!r.isSuccessful||!continuation.isActive)null
                        else r.body?.let{body->
                            val source=body.source()
                            if(source.request(1024L*1024L+1L))null
                            else{
                                val bytes=source.readByteArray()
                                val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                                BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
                                if(options.outWidth!=256||options.outHeight!=256)null else BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()
                            }
                        }
                    }
                }catch(e:Exception){null}
                if(continuation.isActive)continuation.resume(image)
            }
        })
    }
    fun clear(context:Context):Boolean=runCatching{http(context).cache?.evictAll();true}.getOrDefault(false)

}
fun clearPickupMapCache(context:Context):Boolean=MapTiles.clear(context)
private fun visibleTiles(x:Double,y:Double,z:Int,size:IntSize,tilePx:Float):List<VisibleTile>{
    if(size.width<=0||size.height<=0)return emptyList()
    val n=1 shl z
    val cx=x*n;val cy=y*n
    val left=floor(cx-size.width/(2.0*tilePx)).toInt()
    val right=floor(cx+size.width/(2.0*tilePx)).toInt()
    val top=floor(cy-size.height/(2.0*tilePx)).toInt().coerceAtLeast(0)
    val bottom=floor(cy+size.height/(2.0*tilePx)).toInt().coerceAtMost(n-1)
    return (top..bottom).flatMap{row->(left..right).map{col->VisibleTile(Tile(z,((col%n)+n)%n,row),col)}}.sortedBy{abs(it.rawX+.5-cx)+abs(it.tile.y+.5-cy)}
}
fun openPickupMap(context:Context,address:String,lat:Double?,lon:Double?){
    val uri=if(lat!=null&&lon!=null)Uri.parse("geo:"+lat+","+lon+"?q="+lat+","+lon+"("+Uri.encode(address)+")")else Uri.parse("geo:0,0?q="+Uri.encode(address))
    if(runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,uri))}.isFailure){
        val fallback=if(lat!=null&&lon!=null)"https://www.openstreetmap.org/?mlat="+lat+"&mlon="+lon+"#map=18/"+lat+"/"+lon else "https://www.openstreetmap.org/search?query="+Uri.encode(address)
        runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(fallback)))}
    }
}
@Composable fun PickupSelector(store:String,address:String,lat:Double?,lon:Double?,catalog:List<CatalogEntry>,change:(String,Double?,Double?)->Unit){
    var chooser by rememberSaveable{mutableStateOf(false)}
    var manual by rememberSaveable{mutableStateOf(false)}
    val openMap=LocalPickupMap.current
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    fun showMap(){focus.clearFocus();keyboard?.hide();openMap(PickupMapRequest(address,lat,lon,change))}
    var query by rememberSaveable{mutableStateOf("")}
    var draft by rememberSaveable{mutableStateOf("")}
    val saved=catalog.filter{it.kind=="point"&&it.parent.equals(store.trim(),true)}
    Text("Пункт выдачи",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
    OutlinedCard(shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Icon(Icons.Default.LocationOn,null,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).clickable{query="";chooser=true}){
                Text(if(address.isBlank())"Выбрать ПВЗ" else address,fontWeight=FontWeight.Medium)
                Text(if(lat!=null&&lon!=null)"Точка сохранена на карте" else if(address.isBlank())"Необязательно" else "Адрес введён вручную",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(address.isNotBlank())IconButton(onClick={change("",null,null)}){Icon(Icons.Default.Close,"Убрать ПВЗ")}
            else IconButton(onClick={query="";chooser=true}){Icon(Icons.Default.ChevronRight,"Выбрать ПВЗ")}
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(onClick={draft=address;manual=true},modifier=Modifier.weight(1f)){Text("Вручную")}
        Button(onClick={showMap()},modifier=Modifier.weight(1f).testTag("open_pickup_map")){Icon(Icons.Default.Map,null,Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text("На карте")}
    }
    if(saved.isNotEmpty())TextButton(onClick={query="";chooser=true}){Text("Сохранённые ПВЗ ("+saved.size+")")}
    if(chooser)AlertDialog(onDismissRequest={chooser=false},title={Text("Выбрать ПВЗ")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(query,{query=it},singleLine=true,label={Text("Поиск среди сохранённых")},modifier=Modifier.fillMaxWidth())
            if(saved.isEmpty())Text("Пока нет сохранённых пунктов. Добавьте свой адрес или точку на карте.")
            LazyColumn(Modifier.heightIn(max=240.dp)){
                items(saved.filter{it.value.contains(query,true)},key={it.id}){entry->
                    ListItem(headlineContent={Text(entry.value)},supportingContent={Text(if(entry.pointLat!=null)"Точка на карте" else "Адрес")},leadingContent={Icon(Icons.Default.LocationOn,null)},modifier=Modifier.clickable{change(entry.value,entry.pointLat,entry.pointLon);chooser=false})
                }
            }
            TextButton(onClick={chooser=false;draft=address;manual=true}){Text("Ввести вручную")}
            TextButton(onClick={chooser=false;showMap()}){Text("Указать на карте")}
        }
    },confirmButton={TextButton(onClick={chooser=false}){Text("Закрыть")}})
    if(manual)AlertDialog(onDismissRequest={manual=false},title={Text("Адрес пункта выдачи")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedTextField(draft,{draft=it},label={Text("Адрес или название")},modifier=Modifier.fillMaxWidth())
        if(lat!=null)Text("При ручном изменении адреса точка на карте будет сброшена. При необходимости выберите её снова.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={Button(enabled=draft.isNotBlank()&&draft.length<=200,onClick={change(draft.trim(),if(draft.trim()==address)lat else null,if(draft.trim()==address)lon else null);manual=false}){Text("Сохранить")}},dismissButton={TextButton(onClick={manual=false}){Text("Отмена")}})
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MapPicker(address:String,lat:Double?,lon:Double?,close:()->Unit,save:(String,Double,Double)->Unit){
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val leaveMap:()->Unit={focus.clearFocus();keyboard?.hide();close()}
    BackHandler(onBack=leaveMap)
    var editLabel by rememberSaveable{mutableStateOf(false)}
    val ctx=LocalContext.current
    val scope=rememberCoroutineScope()
    var x by rememberSaveable{mutableDoubleStateOf(lonToX(lon?:0.0))}
    var y by rememberSaveable{mutableDoubleStateOf(latToY(lat?:30.0))}
    var zoom by rememberSaveable{mutableIntStateOf(if(lat!=null&&lon!=null)17 else 2)}
    var search by rememberSaveable{mutableStateOf(if(lat==null)address else "")}
    var label by rememberSaveable{mutableStateOf(address)}
    var searching by remember{mutableStateOf(false)}
    var locating by remember{mutableStateOf(false)}
    var locationMessage by remember{mutableStateOf("")}
    var userLat by remember{mutableStateOf<Double?>(null)}
    var userLon by remember{mutableStateOf<Double?>(null)}
    fun locate(){
        if(locating)return
        locating=true;locationMessage="Определяем местоположение…"
        scope.launch{
            try{
                val position=locateForMap(ctx)
                if(position==null){locationMessage="Местоположение недоступно. Проверьте геолокацию в настройках телефона или найдите адрес.";return@launch}
                userLat=position.latitude;userLon=position.longitude
                x=lonToX(position.longitude);y=latToY(position.latitude);zoom=if(position.accuracy>1000f)13 else 17
                locationMessage="Найдите нужный ПВЗ рядом с вашим местоположением."
            }finally{locating=false}
        }
    }
    val locationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->
        if(grants.values.any{it})locate() else locationMessage="Доступ к местоположению не предоставлен. Можно найти адрес вручную."
    }
    var searchError by remember{mutableStateOf("")}
    var results by remember{mutableStateOf<List<android.location.Address>>(emptyList())}
    var size by remember{mutableStateOf(IntSize.Zero)}
    val tilePx=with(LocalDensity.current){256.dp.toPx()}
    val images=remember{mutableStateMapOf<Tile,ImageBitmap>()}
    var loading by remember{mutableStateOf(false)}
    var failed by remember{mutableStateOf(false)}
    var retry by remember{mutableIntStateOf(0)}
    val tiles=visibleTiles(x,y,zoom,size,tilePx)
    val center=Tile(zoom,(x*(1 shl zoom)).toInt().coerceIn(0,(1 shl zoom)-1),(y*(1 shl zoom)).toInt().coerceIn(0,(1 shl zoom)-1))
    LaunchedEffect(tiles.map{it.tile}.distinct(),retry){
        if(tiles.isEmpty())return@LaunchedEffect
        delay(350)
        loading=true;failed=false
        val needed=tiles.map{it.tile}.toSet()
        try{
            for(tile in needed){
                if(images.containsKey(tile))continue
                val bitmap=MapTiles.image(ctx,tile)
                if(bitmap!=null)images[tile]=bitmap else failed=true
            }
        }finally{
            loading=false
            if(images.size>48)images.keys.filter{it !in needed}.take(images.size-48).forEach{images.remove(it)}
        }
    }
    AdaptiveActionScreen(title="Выбрать точку",close=leaveMap,action={
        Button(enabled=zoom>=14&&images.containsKey(center)&&label.length<=200,onClick={
            val selectedLat=yToLat(y);val selectedLon=xToLon(x)
            val name=label.trim().ifBlank{String.format(Locale.ROOT,"Точка %.6f, %.6f",selectedLat,selectedLon)}
            focus.clearFocus();keyboard?.hide();save(name,selectedLat,selectedLon)
        },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("save_pickup")){Text("Сохранить точку")}
    }){
        BoxWithConstraints(Modifier.fillMaxSize()){
            val compact=maxHeight<350.dp || maxWidth<340.dp || LocalDensity.current.fontScale>=1.5f
            val veryShort=maxHeight<155.dp
            Column(Modifier.fillMaxSize()){
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
                    OutlinedTextField(search,{search=it},singleLine=true,label={Text("Город или адрес")},modifier=Modifier.weight(1f).testTag("map_search"))
                    IconButton(enabled=search.isNotBlank()&&!searching,onClick={
                        focus.clearFocus();keyboard?.hide();searching=true;searchError="";results=emptyList()
                        val q=search.trim().take(200)
                        scope.launch{
                            try{
                                @Suppress("DEPRECATION")
                                val found=withContext(Dispatchers.IO){if(Geocoder.isPresent())Geocoder(ctx,Locale.getDefault()).getFromLocationName(q,5).orEmpty() else emptyList()}
                                results=found.filter{it.hasLatitude()&&it.hasLongitude()&&validPoint(it.latitude,it.longitude)}
                                if(results.isEmpty())searchError="Адрес не найден. Можно переместить карту вручную."
                            }catch(e:CancellationException){throw e}catch(e:Exception){searchError="Поиск недоступен. Переместите карту вручную."}
                            finally{searching=false}
                        }
                    }){Icon(Icons.Default.Search,"Найти адрес")}
                    if(compact&&!veryShort)IconButton(onClick={focus.clearFocus();keyboard?.hide();editLabel=true}){Icon(Icons.Default.Edit,"Название ПВЗ")}
                }
                if(!veryShort&&locationMessage.isNotBlank())Text(locationMessage,Modifier.padding(horizontal=12.dp,vertical=4.dp),style=MaterialTheme.typography.bodySmall)
                if(searching||locating)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(!veryShort&&searchError.isNotBlank())Text(searchError,Modifier.padding(horizontal=12.dp),style=MaterialTheme.typography.bodySmall)
                if(results.isNotEmpty())LazyColumn(Modifier.heightIn(max=if(compact)72.dp else 140.dp)){items(results){a->
                    val title=a.getAddressLine(0)?:a.locality?:search
                    ListItem(headlineContent={Text(title)},modifier=Modifier.clickable{x=lonToX(a.longitude);y=latToY(a.latitude);zoom=17;if(label.isBlank())label=title.take(200);results=emptyList()})
                }}
                if(!compact)Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(if(zoom<14)"Приблизьте карту до здания" else "Нажмите на нужное здание",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=2)
                    TextButton(onClick={editLabel=true}){Text("Название")}
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds().background(Color(0xFFE5E9E5))){
                    Canvas(Modifier.fillMaxSize().onSizeChanged{size=it}.pointerInput(tilePx){
                        detectTapGestures{tap->val n=(1 shl zoom).toDouble();x=wrapX(x+(tap.x-this.size.width/2f)/(tilePx*n));y=(y+(tap.y-this.size.height/2f)/(tilePx*n)).coerceIn(0.0,1.0)}
                    }.pointerInput(tilePx){
                        var accumulated=1f
                        detectTransformGestures{_,pan,scale,_->
                            val n=(1 shl zoom).toDouble()
                            x=wrapX(x-pan.x/(tilePx*n));y=(y-pan.y/(tilePx*n)).coerceIn(0.0,1.0)
                            accumulated*=scale
                            if(accumulated>=1.35f){zoom=(zoom+1).coerceAtMost(19);accumulated=1f}
                            else if(accumulated<=.74f){zoom=(zoom-1).coerceAtLeast(2);accumulated=1f}
                        }
                    }){
                        val n=(1 shl zoom).toDouble()
                        for(v in tiles){
                            val bitmap=images[v.tile]?:continue
                            val left=((v.rawX-x*n)*tilePx+this.size.width/2).roundToInt()
                            val top=((v.tile.y-y*n)*tilePx+this.size.height/2).roundToInt()
                            drawImage(bitmap,dstOffset=IntOffset(left,top),dstSize=IntSize(tilePx.roundToInt()+1,tilePx.roundToInt()+1))
                        }
                        val gy=userLat;val gx=userLon
                        if(gy!=null&&gx!=null){
                            val dx=wrapX(lonToX(gx)-x+.5)-.5
                            val point=androidx.compose.ui.geometry.Offset((this.size.width/2+dx*n*tilePx).toFloat(),(this.size.height/2+(latToY(gy)-y)*n*tilePx).toFloat())
                            drawCircle(Color.White,10.dp.toPx(),point);drawCircle(Color(0xFF1976D2),7.dp.toPx(),point)
                        }
                    }
                    Icon(Icons.Default.LocationOn,"Выбранная точка",Modifier.align(Alignment.Center).offset(y=(-17).dp).size(40.dp),tint=Color(0xFFB42318))
                    val controls:@Composable ()->Unit={
                        FilledTonalIconButton(enabled=!locating,onClick={
                            if(ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)locate()
                            else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
                        }){Icon(Icons.Default.MyLocation,"Моё местоположение")}
                        FilledTonalIconButton(onClick={zoom=(zoom+1).coerceAtMost(19)},enabled=zoom<19){Icon(Icons.Default.Add,"Приблизить")}
                        FilledTonalIconButton(onClick={zoom=(zoom-1).coerceAtLeast(2)},enabled=zoom>2){Icon(Icons.Default.Remove,"Отдалить")}
                    }
                    if(maxHeight>=168.dp)Column(Modifier.align(Alignment.CenterEnd).padding(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){controls()}
                    else if(maxHeight>=48.dp)Row(Modifier.align(Alignment.BottomCenter),horizontalArrangement=Arrangement.spacedBy(2.dp)){controls()}
                    if(loading)LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth())
                    if(failed&&!loading)FilledTonalButton(onClick={retry++},modifier=Modifier.align(Alignment.TopCenter).padding(8.dp)){Text("Повторить загрузку")}
                }
                Text("© OpenStreetMap contributors",Modifier.fillMaxWidth().clickable{runCatching{ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.openstreetmap.org/copyright")))}}.padding(6.dp),style=MaterialTheme.typography.labelSmall)
            }
        }
    }
    if(editLabel)AlertDialog(onDismissRequest={editLabel=false},title={Text("Название ПВЗ")},text={OutlinedTextField(label,{if(it.length<=200)label=it},label={Text("Адрес или название")},modifier=Modifier.fillMaxWidth())},confirmButton={TextButton(onClick={editLabel=false}){Text("Готово")}})
}
