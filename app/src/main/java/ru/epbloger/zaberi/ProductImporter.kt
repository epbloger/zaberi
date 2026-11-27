package ru.epbloger.zaberi
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit

internal data class ProductMetadata(val title:String?,val price:Long?,val image:String?)
internal data class ProductLookup(val title:String?,val price:Long?,val photo:ByteArray?,val warning:String?=null)
private val shopDomains=mapOf(
    "Ozon" to listOf("ozon.ru","ozon.com"),"Wildberries" to listOf("wildberries.ru","wb.ru"),
    "Яндекс Маркет" to listOf("market.yandex.ru"),"Авито" to listOf("avito.ru"),
    "Мегамаркет" to listOf("megamarket.ru"),"AliExpress" to listOf("aliexpress.ru"),"Lamoda" to listOf("lamoda.ru")
)
private val imageDomains=mapOf(
    "Ozon" to listOf("ozon.ru","ozone.ru","ozonusercontent.com","ozon.com"),
    "Wildberries" to listOf("wildberries.ru","wb.ru","wbstatic.net","wbbasket.ru","wbbasket.com"),
    "Яндекс Маркет" to listOf("market.yandex.ru","yandex.net","yastatic.net"),
    "Авито" to listOf("avito.ru","avito.st"),"Мегамаркет" to listOf("megamarket.ru","sbermegamarket.ru"),
    "AliExpress" to listOf("aliexpress.ru","alicdn.com","aliexpress-media.com"),"Lamoda" to listOf("lamoda.ru")
)
private fun hostMatches(host:String,domains:List<String>)=domains.any{host==it||host.endsWith(".$it")}
private fun safeHttps(raw:String):HttpUrl?=raw.toHttpUrlOrNull()?.takeIf{it.isHttps&&it.port==443&&it.username.isEmpty()&&it.password.isEmpty()}
private fun productPath(store:String,path:String):Boolean {
    return when(store){
        "Ozon"->path.startsWith("/product/")||path.startsWith("/t/")
        "Wildberries"->wildberriesArticle(path)!=null
        "Яндекс Маркет"->path.startsWith("/product--")||path.startsWith("/product/")||path.startsWith("/cc/")
        "Авито"->Regex("/.+_\\d+/?").matches(path)&&!path.startsWith("/profile/")
        "Мегамаркет"->path.startsWith("/catalog/details/")
        "AliExpress"->path.startsWith("/item/")
        "Lamoda"->path.startsWith("/p/")
        else->false
    }
}
internal fun publicProductUrl(raw:String,store:String):HttpUrl? {
    val url=safeHttps(raw)?:return null
    if(!hostMatches(url.host,shopDomains[store].orEmpty())||!productPath(store,url.encodedPath))return null
    if(store=="Wildberries")wildberriesArticle(url.encodedPath)?.let{
        return "https://www.wildberries.ru/catalog/$it/detail.aspx".toHttpUrlOrNull()
    }
    return url.newBuilder().query(null).fragment(null).build()
}
internal fun publicImageUrl(raw:String,store:String):HttpUrl? =
    safeHttps(raw)?.takeIf{hostMatches(it.host,imageDomains[store].orEmpty())}?.newBuilder()?.fragment(null)?.build()
private fun publicAddress(address:InetAddress):Boolean {
    if(address.isAnyLocalAddress||address.isLoopbackAddress||address.isLinkLocalAddress||address.isSiteLocalAddress||address.isMulticastAddress)return false
    val b=address.address.map{it.toInt() and 255}
    if(b.size==4&&(b[0] in listOf(0,10,127)||b[0]>=224||b[0]==100&&b[1] in 64..127||b[0]==198&&b[1] in 18..19))return false
    if(b.size==16&&((b[0] and 0xfe)==0xfc||b[0]==0&&b[1]==0))return false
    return true
}
private val importClient=OkHttpClient.Builder()
    .dns(object:Dns{
        override fun lookup(hostname:String):List<InetAddress>{
            val addresses=Dns.SYSTEM.lookup(hostname)
            if(addresses.isEmpty()||addresses.any{!publicAddress(it)})throw UnknownHostException("Non-public address")
            return addresses
        }
    })
    .followRedirects(false).followSslRedirects(false)
    .connectTimeout(4,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS).callTimeout(7,TimeUnit.SECONDS)
    .retryOnConnectionFailure(false).build()
private fun InputStream.readLimited(max:Int):ByteArray{
    val out=ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(true){
        val n=read(buffer)
        if(n<0)break
        require(out.size()+n<=max){"Response too large"}
        out.write(buffer,0,n)
    }
    return out.toByteArray()
}
private data class PublicResponse(val url:HttpUrl,val bytes:ByteArray)
private data class DownloadStep(val redirect:HttpUrl?=null,val bytes:ByteArray?=null)
private suspend fun downloadStep(url:HttpUrl,image:Boolean,json:Boolean=false):DownloadStep=suspendCancellableCoroutine{continuation->
    val request=Request.Builder().url(url)
        .header("Accept",when{image->"image/jpeg,image/png,image/webp";json->"application/json";else->"text/html,application/xhtml+xml"})
        .header("Accept-Language","ru-RU,ru;q=0.9")
        .header("User-Agent","Zaberi/1.1 (Android; product import)")
        .build()
    val call=importClient.newCall(request)
    continuation.invokeOnCancellation{call.cancel()}
    call.enqueue(object:Callback{
        override fun onFailure(call:Call,e:IOException){continuation.resumeWith(Result.failure(e))}
        override fun onResponse(call:Call,response:Response){
            val result=runCatching{
                response.use{r->
                    if(r.isRedirect){
                        DownloadStep(redirect=r.header("Location")?.let{url.resolve(it)}?:error("Invalid redirect"))
                    }else{
                        require(r.isSuccessful)
                        val body=requireNotNull(r.body)
                        val type=body.contentType()?:error("No content type")
                        require(when{
                            image->type.type=="image"&&type.subtype in setOf("jpeg","png","webp")
                            json->type.subtype=="json"||type.subtype.endsWith("+json")
                            else->(type.type=="text"&&type.subtype=="html")||(type.type=="application"&&type.subtype=="xhtml+xml")
                        })
                        val max=if(image)6*1024*1024 else 2*1024*1024
                        require(body.contentLength()<=max.toLong())
                        DownloadStep(bytes=body.byteStream().use{it.readLimited(max)})
                    }
                }
            }
            continuation.resumeWith(result)
        }
    })
}
private suspend fun publicResponse(start:HttpUrl,store:String,image:Boolean):PublicResponse {
    var url=start
    repeat(4){
        currentCoroutineContext().ensureActive()
        val step=downloadStep(url,image)
        val next=step.redirect
        if(next!=null){
            url=(if(image)publicImageUrl(next.toString(),store)else publicProductUrl(next.toString(),store))?:error("Unsupported redirect")
        }else return PublicResponse(url,requireNotNull(step.bytes))
    }
    error("Too many redirects")
}

private fun JSONObject.string(key:String):String?=opt(key).let{v->when(v){is String->v.trim().takeIf{it.isNotEmpty()};is Number->v.toString();else->null}}
private fun schemaTypes(value:Any?):List<String> = when(value){
    is String->listOf(value.substringAfterLast('/').substringAfterLast('#'))
    is JSONArray->(0 until minOf(value.length(),32)).mapNotNull{value.opt(it) as? String}.map{it.substringAfterLast('/').substringAfterLast('#')}
    else->emptyList()
}
private fun JSONObject.hasType(type:String)=schemaTypes(opt("@type")).any{it.equals(type,true)}
private fun topProducts(value:Any?,out:MutableList<JSONObject>,depth:Int=0){
    if(depth>6||out.size>8)return
    when(value){
        is JSONArray->for(i in 0 until minOf(value.length(),64))topProducts(value.opt(i),out,depth+1)
        is JSONObject->if(value.hasType("Product"))out.add(value)else{
            topProducts(value.opt("@graph"),out,depth+1)
            topProducts(value.opt("mainEntity"),out,depth+1)
        }
    }
}
private fun explicitRuble(value:String?,currency:String?):Long?{
    if(currency?.uppercase(Locale.ROOT) !in setOf("RUB","RUR")||value==null)return null
    val n=value.replace('\u00a0',' ').replace('\u202f',' ').trim()
    if(!Regex("[0-9]+(?: [0-9]{3})*(?:[.,][0-9]{1,2})?").matches(n))return null
    return parseMoney(n)
}
private fun offers(value:Any?):List<JSONObject> = when(value){
    is JSONObject->listOf(value)
    is JSONArray->(0 until minOf(value.length(),64)).mapNotNull{value.optJSONObject(it)}
    else->emptyList()
}
private fun schemaImage(value:Any?,depth:Int=0):String?=if(depth>4)null else when(value){
    is String->value.takeIf{it.isNotBlank()}
    is JSONObject->value.string("url")?:value.string("contentUrl")
    is JSONArray->if(value.length()>0)schemaImage(value.opt(0),depth+1)else null
    else->null
}
private fun parseSchemaMetadata(html:String,page:HttpUrl,store:String):ProductMetadata{
    val doc=Jsoup.parse(html,page.toString())
    fun metas(key:String)=doc.select("meta").filter{it.attr("property").equals(key,true)||it.attr("name").equals(key,true)}.map{it.attr("content").trim()}.filter{it.isNotEmpty()}.distinct()
    fun meta(key:String)=metas(key).singleOrNull()
    val products=mutableListOf<JSONObject>()
    for(script in doc.select("script[type=application/ld+json]").take(32)){
        val json=script.data()
        if(json.length<=512*1024)runCatching{topProducts(JSONTokener(json).nextValue(),products)}
    }
    if(products.size>1)return ProductMetadata(null,null,null)
    val product=products.singleOrNull()
    val ogType=meta("og:type")
    val canonical=meta("og:url")?.let{publicProductUrl(it,store)}
    val canonicalMatches=canonical!=null&&canonical.host==page.host&&canonical.encodedPath.trimEnd('/')==page.encodedPath.trimEnd('/')
    val productEvidence=product!=null||ogType.equals("product",true)||ogType.equals("og:product",true)||meta("product:price:amount")!=null||canonicalMatches
    if(!productEvidence)return ProductMetadata(null,null,null)
    val title=(product?.string("name")?:meta("og:title"))?.let{Jsoup.parse(it).text().trim().take(200)}?.takeIf{
        it.isNotBlank()&&!Regex("captcha|access denied|robot|доступ ограничен|подтвердите, что вы|проверка браузера|страница не найдена",RegexOption.IGNORE_CASE).containsMatchIn(it)
    }?:return ProductMetadata(null,null,null)
    val offerList=offers(product?.opt("offers"))
    val offerPrices=offerList.mapNotNull{offer->if(offer.hasType("Offer"))explicitRuble(offer.string("price"),offer.string("priceCurrency"))else null}
    val schemaPrice=offerPrices.distinct().singleOrNull()?.takeIf{offerList.size==offerPrices.size}
    val ogAmounts=metas("product:price:amount")
    val ogPrice=explicitRuble(ogAmounts.singleOrNull(),meta("product:price:currency"))
    val price=when{
        offerList.isNotEmpty()&&schemaPrice==null->null
        ogAmounts.size>1->null
        schemaPrice!=null&&ogPrice!=null&&schemaPrice!=ogPrice->null
        else->schemaPrice?:ogPrice
    }
    val rawImage=schemaImage(product?.opt("image"))?:meta("og:image:secure_url")?:meta("og:image")
    val image=rawImage?.let{page.resolve(it)?.toString()}?.let{publicImageUrl(it,store)?.toString()}
    return ProductMetadata(title,price,image)
}

internal fun wildberriesArticle(path:String):Long? =
    Regex("^/catalog/(\\d{1,12})(?:/details?\\.aspx)?/?$",RegexOption.IGNORE_CASE)
        .matchEntire(path)?.groupValues?.get(1)?.toLongOrNull()?.takeIf{it>0}

internal fun parseWildberriesDetails(json:String,article:Long):ProductMetadata {
    val root=JSONObject(json)
    val products=root.optJSONArray("products")?:root.optJSONObject("data")?.optJSONArray("products")?:return ProductMetadata(null,null,null)
    val product=(0 until minOf(products.length(),100)).mapNotNull{products.optJSONObject(it)}
        .filter{it.optLong("id",-1)==article}.singleOrNull()?:return ProductMetadata(null,null,null)
    val sizes=product.optJSONArray("sizes")
    val prices=mutableListOf<Long>()
    var unknown=false
    if(sizes!=null&&sizes.length()<=200)for(i in 0 until sizes.length()){
        val size=sizes.optJSONObject(i)?:continue
        val stocks=size.optJSONArray("stocks")
        val available=if(stocks!=null)(0 until minOf(stocks.length(),1000)).any{(stocks.optJSONObject(it)?.optLong("qty",0)?:0)>0}
            else product.optLong("totalQuantity",0)>0
        if(!available)continue
        val amount=size.optJSONObject("price")?.opt("product")?.toString()?.toLongOrNull()
        if(amount!=null&&amount in 1..MAX_ORDER_AMOUNT)prices.add(amount)else unknown=true
    }
    return ProductMetadata(product.string("name")?.take(200),if(!unknown)prices.distinct().singleOrNull()else null,null)
}
internal fun wildberriesBasketHost(json:String,article:Long):String?{
    val routes=JSONObject(json).optJSONObject("origin")?.optJSONArray("mediabasket_route_map")?:return null
    val vol=article/100000
    val candidates=mutableListOf<String>()
    for(i in 0 until minOf(routes.length(),32)){
        val route=routes.optJSONObject(i)?:continue
        if(route.optString("method","range")!="range")continue
        val hosts=route.optJSONArray("hosts")?:continue
        for(j in 0 until minOf(hosts.length(),200)){
            val entry=hosts.optJSONObject(j)?:continue
            if(vol in entry.optLong("vol_range_from",Long.MAX_VALUE)..entry.optLong("vol_range_to",Long.MIN_VALUE)){
                val host=entry.optString("host").lowercase(Locale.ROOT)
                if(Regex("basket-\\d{1,3}\\.wbbasket\\.ru").matches(host))candidates.add(host)
            }
        }
    }
    return candidates.distinct().singleOrNull()
}
private fun knownBasket(article:Long):String?{
    val ends=listOf(143,287,431,719,1007,1061,1115,1169,1313,1601,1655,1919,2045,2189,2405,2621,2837,3053,3269,3485,3701,3917,4133,4349,4565,4877,5189,5501,5813,6125,6437,6749,7061,7373,7685,7997,8309,8741,9173,9605,10373,11141,11909,12677,13445,14213)
    val index=ends.indexOfFirst{article/100000<=it}
    return if(index<0)null else "basket-"+(index+1).toString().padStart(2,'0')+".wbbasket.ru"
}
private suspend fun <T> optionalDownload(block:suspend ()->T):T?=try{block()}catch(e:CancellationException){throw e}catch(_:Exception){null}
private suspend fun fixedJson(url:String):String{
    val response=downloadStep(requireNotNull(url.toHttpUrlOrNull()),false,true)
    require(response.redirect==null)
    return requireNotNull(response.bytes).toString(Charsets.UTF_8)
}
private suspend fun downloadProductPhoto(url:String?,store:String):ByteArray?{
    val image=url?.let{publicImageUrl(it,store)}?:return null
    return optionalDownload{withTimeoutOrNull(7000){PrivateFiles.preparePhoto(publicResponse(image,store,true).bytes)}}
}
private suspend fun lookupWildberries(article:Long,needPhoto:Boolean):ProductLookup=coroutineScope{
    val details=async{optionalDownload{withTimeoutOrNull(8000){
        parseWildberriesDetails(fixedJson("https://card.wb.ru/cards/v4/detail?appType=1&curr=rub&dest=-1257786&locale=ru&spp=30&nm=$article"),article)
    }}}
    val media=async{
        val host=optionalDownload{withTimeoutOrNull(5000){
            wildberriesBasketHost(fixedJson("https://cdn.wbbasket.ru/api/v3/upstreams"),article)
        }}?:knownBasket(article)
        if(host==null)return@async Pair<String?,ByteArray?>(null,null)
        val base="https://$host/vol${article/100000}/part${article/1000}/$article"
        coroutineScope{
            val card=async{optionalDownload{withTimeoutOrNull(7000){JSONObject(fixedJson("$base/info/ru/card.json")).string("name")?.take(200)}}}
            val photo=async{if(needPhoto)downloadProductPhoto("$base/images/c516x688/1.webp","Wildberries")
                ?:downloadProductPhoto("$base/images/big/1.webp","Wildberries")else null}
            Pair(card.await(),photo.await())
        }
    }
    val d=details.await()
    val (title,photo)=media.await()
    val message=if(d?.price!=null)"Цена открытой карточки для Москвы. Проверьте свою цену с учётом региона, размера и скидок."
        else "Магазин не предоставил однозначную цену."
    ProductLookup(d?.title?:title,d?.price,photo,message+(if(needPhoto&&photo==null)" Главное фото недоступно." else ""))
}
private fun rubleText(raw:String?):Long?{
    val value=raw?.replace('\u2009',' ')?.replace('\u202f',' ')?.replace('\u00a0',' ')?.trim()?:return null
    val match=Regex("^([0-9]+(?: [0-9]{3})*(?:[.,][0-9]{1,2})?)\\s*₽$").matchEntire(value)?:return null
    return parseMoney(match.groupValues[1])
}
internal fun parseProductMetadata(html:String,page:HttpUrl,store:String):ProductMetadata{
    val base=parseSchemaMetadata(html,page,store)
    if(store!="Ozon"||!page.encodedPath.startsWith("/product/"))return base
    val doc=Jsoup.parse(html,page.toString())
    val states=doc.select("[id^=state-webPrice-][data-state]").take(8)
    val gallery=doc.select("[data-widget=webGallery]").firstOrNull()
    if(states.isEmpty()&&gallery==null)return base
    val prices=states.mapNotNull{element->runCatching{rubleText(JSONObject(element.attr("data-state")).string("price"))}.getOrNull()}
    val price=prices.distinct().singleOrNull()?.takeIf{prices.size==states.size}
    val title=doc.select("[data-widget=webProductHeading] h1").firstOrNull()?.text()?.trim()?.take(200)?.takeIf{it.isNotBlank()}
    val image=gallery?.select("img")?.asSequence()?.mapNotNull{img->
        sequenceOf(img.attr("src"),img.attr("data-src")).filter{it.isNotBlank()}
            .mapNotNull{raw->page.resolve(raw)?.toString()?.let{publicImageUrl(it,"Ozon")?.toString()}}.firstOrNull()
    }?.firstOrNull()
    return ProductMetadata(title?:base.title,if(states.isNotEmpty())price else base.price,base.image?:image)
}

internal object ProductImporter {
    suspend fun readSharedImage(context:Context,request:SharedProductRequest):ByteArray?=withContext(Dispatchers.IO){
        val uri=request.image?:return@withContext null
        require(uri.scheme=="content")
        val raw=context.contentResolver.openInputStream(uri)?.use{it.readLimited(20*1024*1024)}?:error("Не удалось открыть фото")
        PrivateFiles.preparePhoto(raw)
    }
    suspend fun lookup(seed:SharedProductSeed,needPhoto:Boolean):ProductLookup=withContext(Dispatchers.IO){
        val url=publicProductUrl(seed.link,seed.store)?:return@withContext ProductLookup(null,null,null,"Для этой ссылки автозагрузка недоступна. Можно заполнить заказ вручную.")
        try{
            if(seed.store=="Wildberries")wildberriesArticle(url.encodedPath)?.let{return@withContext lookupWildberries(it,needPhoto)}
            val page=withTimeoutOrNull(11000){publicResponse(url,seed.store,false)}
                ?:return@withContext ProductLookup(null,null,null,"Магазин не ответил вовремя. Можно продолжить вручную.")
            val metadata=parseProductMetadata(page.bytes.toString(Charsets.UTF_8),page.url,seed.store)
            val photo=if(needPhoto&&metadata.image!=null)try{
                val image=publicImageUrl(metadata.image,seed.store)?:error("Unsupported image")
                withTimeoutOrNull(7000){PrivateFiles.preparePhoto(publicResponse(image,seed.store,true).bytes)}
            }catch(e:CancellationException){throw e}catch(_:Exception){null}else null
            ProductLookup(metadata.title,metadata.price,photo,if((seed.price==null&&metadata.price==null)||(needPhoto&&photo==null))"Не все сведения доступны. Проверьте цену и при необходимости добавьте фото вручную." else null)
        }catch(e:CancellationException){throw e}catch(_:Exception){
            ProductLookup(null,null,null,"Магазин не предоставил данные карточки. Можно продолжить вручную.")
        }
    }
}
