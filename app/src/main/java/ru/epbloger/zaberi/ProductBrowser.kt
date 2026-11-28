package ru.epbloger.zaberi

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.json.JSONObject
import org.json.JSONTokener

// Read only the visible public card and explicit product metadata. No JS bridge,
// credentials, background browsing, CAPTCHA automation, or third-party server.
internal val readCardScript="""
(function(){
 try {
  const parts=[];
  let size=0;
  function append(e){
    const html=e.outerHTML;
    if(html.length>700000||size+html.length>1600000)return;
    parts.push(html);size+=html.length;
  }
  document.querySelectorAll('meta[property],meta[name],script[type="application/ld+json"],h1,[itemscope][itemtype*="Product"],[id^="state-webPrice-"][data-state],[id^="state-webGallery-"][data-state],[data-widget="webGallery"],[data-widget="webProductHeading"]').forEach(append);
  const priceText=[];
  const scopedPrices=[];
  const scope=document.querySelector('main')||document.body;
  if(scope){
    function visibleUnstruckText(el){
      if(!el.getClientRects().length)return '';
      const css=getComputedStyle(el);
      if(css.visibility==='hidden'||css.display==='none'||String(css.textDecorationLine||css.textDecoration||'').includes('line-through')||el.matches('s,del,strike'))return '';
      if(el.childElementCount===0)return el.innerText||el.textContent||'';
      let parts=[];
      for(const child of el.childNodes){
        if(child.nodeType===3)parts.push(child.textContent||'');
        else if(child.nodeType===1)parts.push(visibleUnstruckText(child));
      }
      return parts.join(' ').replace(/\s+/g,' ').trim();
    }
    const nodes=scope.querySelectorAll('span,div,p,label,button,strong,b');
    const priceLabel=/(без\s+(?:(?:ozon|озон|wb|яндекс)\s+карты|карты|пэй|кошелька|яндекс|wb)|обычная\s+цена|с\s+другими\s+банками|с\s+любой\s+картой|при\s+оплате\s+любым\s+способом)/i;
    const priceScopes=document.querySelectorAll('[data-widget="webPrice"],[data-auto="price-value"],[data-auto="mainPrice"],[data-auto="price-block"],[data-testid="price"],[data-marker="item-view/item-price"]');
    for(const e of Array.from(priceScopes).slice(0,32)){
      append(e);
      const t=visibleUnstruckText(e);
      if(t.length>0&&t.length<800&&!scopedPrices.includes(t))scopedPrices.push(t);
    }
    for(let i=0;i<nodes.length&&i<18000&&priceText.length<24;i++){
      const e=nodes[i];
      if(e.childElementCount>8||!e.getClientRects().length)continue;
      const t=(e.innerText||'').trim();
      if(t.length>0&&t.length<240&&priceLabel.test(t)){
        let context=visibleUnstruckText(e);
        let p=e.parentElement;
        for(let level=0;level<4&&!/(₽|руб\.?)/i.test(context)&&p;level++,p=p.parentElement){
          const raw=(p.innerText||'').trim();
          if(raw.length>240)break;
          context=visibleUnstruckText(p);
        }
        if(context.length<240&&/(₽|руб\.?)/i.test(context)&&!priceText.includes(context))priceText.push(context);
      }
    }
  }
  return JSON.stringify({html:parts.join('\n'),prices:priceText.join('\n'),scopedPrices:scopedPrices});
 } catch(e) {
  return JSON.stringify({captureError:String(e.name||'Error').slice(0,40)});
 }
})()
""".trimIndent()

@Composable internal fun ProductBrowserDialog(seed:SharedProductSeed,needPhoto:Boolean,needPrice:Boolean,close:()->Unit,
    loadPage:(WebView,HttpUrl)->Unit={view,url->view.loadUrl(url.toString())},
    localResponse:((WebResourceRequest)->WebResourceResponse?)?=null,
    captureDiagnostic:((String)->Unit)?=null,apply:(ProductLookup)->Unit){
    val start=publicProductUrl(seed.link,seed.store)?:return
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var ready by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
    var partial by remember{mutableStateOf<ProductLookup?>(null)}
    var manualPrice by remember{mutableStateOf("")}
    var message by remember{mutableStateOf("Открываем карточку…")}
    var locked by remember{mutableStateOf<HttpUrl?>(start.takeUnless{isShortProductUrl(it,seed.store)})}
    var active by remember{mutableStateOf(true)}
    fun allowed(raw:String):HttpUrl?{
        val url=publicProductUrl(raw,seed.store)?:return null
        val previous=locked
        if(previous!=null&&url.encodedPath.trimEnd('/')!=previous.encodedPath.trimEnd('/'))return null
        return url
    }
    val web=remember{
        WebView(context).apply{
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            settings.javaScriptCanOpenWindowsAutomatically=false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.cacheMode=WebSettings.LOAD_NO_CACHE
            settings.setGeolocationEnabled(false)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
            webViewClient=object:WebViewClient(){
                // Internal deterministic fixture transport. Production callers supply no override.
                override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? =
                    localResponse?.invoke(request)?:super.shouldInterceptRequest(view,request)
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean{
                    if(!request.isForMainFrame)return request.url.scheme!="https"
                    val url=allowed(request.url.toString())
                    if(url==null){message="Можно открывать только выбранную карточку товара.";return true}
                    return false
                }
                override fun onPageStarted(view:WebView,url:String?,favicon:Bitmap?){
                    ready=false
                    if(url==null||allowed(url)==null){view.stopLoading();message="Переход за пределы карточки остановлен."}
                }
                override fun onPageFinished(view:WebView,url:String?){
                    val page=url?.let{allowed(it)}
                    if(page!=null&&!isShortProductUrl(page,seed.store)){
                        if(locked==null)locked=page
                        ready=true
                        message="Когда цена и фото видны, нажмите «Перенести данные»."
                    }
                }
                override fun onReceivedError(view:WebView,request:WebResourceRequest,error:WebResourceError){
                    if(request.isForMainFrame){ready=false;message="Магазин не открыл карточку. Можно вернуться к заказу."}
                }
            }
        }
    }
    DisposableEffect(web){
        loadPage(web,start)
        onDispose{
            active=false
            web.stopLoading()
            web.webViewClient=WebViewClient()
            web.destroy()
        }
    }
    fun capture(){
        val page=web.url?.let{allowed(it)}
        if(page==null||isShortProductUrl(page,seed.store)){
            message="Дождитесь открытия карточки.";return
        }
        busy=true
        message="Читаем данные карточки…"
        web.evaluateJavascript(readCardScript){raw->
            if(!active)return@evaluateJavascript
            scope.launch{
                var stage="navigation"
                try{
                    if(web.url?.let{allowed(it)}!=page)error("Page changed")
                    stage="decode"
                    require(raw!=null&&raw.length<=4000000)
                    val data=when(val decoded=JSONTokener(raw).nextValue()){
                        is JSONObject->decoded
                        is String->JSONObject(decoded)
                        else->error("Empty capture")
                    }
                    stage="script"
                    if(data.has("captureError"))error("Script error")
                    stage="price"
                    val explicit=data.getString("prices")
                    val scoped=data.optJSONArray("scopedPrices")
                    val scopedValues=(0 until minOf(scoped?.length()?:0,32))
                        .mapNotNull{scopedProductPrice(scoped?.optString(it).orEmpty())}.distinct()
                    val priceText=if(explicit.isBlank()&&scopedValues.size==1)
                        "Обычная цена: "+number(scopedValues.single())+" ₽" else explicit
                    stage="metadata"
                    val result=ProductImporter.fromRenderedPage(data.getString("html"),priceText,page,seed.store,needPhoto&&partial?.photo==null)
                    if(!active)return@launch
                    stage="navigation_after_read"
                    if(web.url?.let{allowed(it)}!=page)error("Page changed")
                    val previous=partial
                    val combined=result.copy(title=result.title?:previous?.title,photo=previous?.photo?:result.photo)
                    if(combined.title==null&&combined.price==null&&combined.photo==null){
                        message="Данные пока не видны. Дождитесь загрузки и повторите."
                    }else if(needPrice&&combined.price==null){
                        partial=combined
                        message="Цена не распознана. Укажите её по карточке или повторите чтение."
                    }else apply(combined)
                }catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){if(active){
                    val diagnostic=stage+":"+e.javaClass.simpleName
                    captureDiagnostic?.invoke(diagnostic)
                    android.util.Log.w("ProductImport","Rendered capture failed: "+diagnostic)
                    message="Не удалось прочитать карточку. Попробуйте после загрузки."
                }}
                finally{if(active)busy=false}
            }
        }
    }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        SafeAppViewport{
            Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=8.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Text("Карточка товара",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,maxLines=1)
                    IconButton(onClick=close,modifier=Modifier.testTag("close_product_browser")){Icon(Icons.Default.Close,"Вернуться к заказу")}
                }
                val pending=partial
                if(pending==null){
                    Button(enabled=ready&&!busy,onClick={capture()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("capture_product")){Text("Перенести данные")}
                }else{
                    val parsed=parseMoney(manualPrice)
                    OutlinedTextField(manualPrice,{manualPrice=it},Modifier.fillMaxWidth().testTag("browser_manual_price"),
                        label={Text("Цена на карточке, ₽")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=manualPrice.isNotBlank()&&parsed==null)
                    Button(enabled=!busy&&parsed!=null,onClick={apply(pending.copy(price=parsed,warning="Цена указана вами по карточке. Проверьте данные."))},
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("capture_product")){Text("Перенести данные")}
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        TextButton(enabled=ready&&!busy,onClick={capture()}){Text("Повторить")}
                        TextButton(enabled=!busy,onClick={apply(pending)}){Text("Без цены")}
                    }
                }
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(message,modifier=Modifier.testTag("product_browser_message"),style=MaterialTheme.typography.bodySmall,maxLines=3)
                AndroidView(factory={web},modifier=Modifier.weight(1f).fillMaxWidth().testTag("product_browser"))
            }
        }
    }
}
