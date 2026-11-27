package ru.epbloger.zaberi

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.foundation.layout.*
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
private val readCardScript="""
(function(){
  const parts=[];
  let size=0;
  function append(e){
    const html=e.outerHTML;
    if(html.length>700000||size+html.length>1600000)return;
    parts.push(html);size+=html.length;
  }
  document.querySelectorAll('meta[property],meta[name],script[type="application/ld+json"],h1,[itemscope][itemtype*="Product"],[id^="state-webPrice-"][data-state],[data-widget="webGallery"],[data-widget="webProductHeading"]').forEach(append);
  const priceText=[];
  const scope=document.querySelector('main')||document.body;
  if(scope){
    const nodes=scope.querySelectorAll('span,div,p');
    for(let i=0;i<nodes.length&&i<18000&&priceText.length<24;i++){
      const e=nodes[i];
      if(e.childElementCount>8||!e.getClientRects().length)continue;
      const t=(e.innerText||'').trim();
      if(t.length>0&&t.length<170&&/(без\s+(карты|пэй|кошелька|яндекс|wb)|обычная\s+цена)/i.test(t)){
        let context=t;
        if(!/₽/.test(t)&&e.parentElement){
          const p=(e.parentElement.innerText||'').trim();
          if(p.length<170)context=p;
        }
        if(!priceText.includes(context))priceText.push(context);
      }
    }
  }
  return JSON.stringify({html:parts.join('\n'),prices:priceText.join('\n')});
})()
""".trimIndent()

@Composable internal fun ProductBrowserDialog(seed:SharedProductSeed,needPhoto:Boolean,close:()->Unit,apply:(ProductLookup)->Unit){
    val start=publicProductUrl(seed.link,seed.store)?:return
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var ready by remember{mutableStateOf(false)}
    var busy by remember{mutableStateOf(false)}
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
        web.loadUrl(start.toString())
        onDispose{
            active=false
            web.stopLoading()
            web.webViewClient=WebViewClient()
            web.destroy()
        }
    }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize()){
            Column(Modifier.fillMaxSize().padding(12.dp)){
                Text("Карточка товара",style=MaterialTheme.typography.titleLarge)
                Text("Публичная страница магазина. Не вводите данные аккаунта.",style=MaterialTheme.typography.bodySmall)
                Text(message,style=MaterialTheme.typography.bodySmall)
                AndroidView(factory={web},modifier=Modifier.weight(1f).fillMaxWidth().testTag("product_browser"))
                if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(enabled=ready&&!busy,onClick={
                    val page=web.url?.let{allowed(it)}
                    if(page==null||isShortProductUrl(page,seed.store)){
                        message="Дождитесь открытия карточки.";return@Button
                    }
                    busy=true
                    web.evaluateJavascript(readCardScript){raw->
                        if(!active)return@evaluateJavascript
                        scope.launch{
                            try{
                                val current=web.url?.let{allowed(it)}
                                if(current!=page)error("Page changed")
                                val json=JSONTokener(raw).nextValue() as? String?:error("No page")
                                require(json.length<=2000000)
                                val data=JSONObject(json)
                                val result=ProductImporter.fromRenderedPage(data.getString("html"),data.getString("prices"),page,seed.store,needPhoto)
                                if(!active)return@launch
                                if(result.title==null&&result.price==null&&result.photo==null){
                                    message="Данные пока не видны. Дождитесь загрузки или вернитесь к заказу."
                                }else apply(result)
                            }catch(e:kotlinx.coroutines.CancellationException){throw e}
                            catch(_:Exception){if(active)message="Не удалось прочитать карточку. Попробуйте после загрузки."}
                            finally{if(active)busy=false}
                        }
                    }
                },modifier=Modifier.fillMaxWidth().testTag("capture_product")){Text("Перенести данные")}
                TextButton(onClick=close,modifier=Modifier.fillMaxWidth()){Text("Вернуться к заказу")}
            }
        }
    }
}
