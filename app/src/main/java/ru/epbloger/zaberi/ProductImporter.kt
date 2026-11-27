package ru.epbloger.zaberi
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        "Wildberries"->Regex("/catalog/\\d+/detail\\.aspx/?",RegexOption.IGNORE_CASE).matches(path)
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
private fun publicResponse(start:HttpUrl,store:String,image:Boolean):PublicResponse {
    var url=start
    repeat(4){
        val request=Request.Builder().url(url).header("Accept",if(image)"image/jpeg,image/png,image/webp" else "text/html,application/xhtml+xml").build()
        importClient.newCall(request).execute().use{response->
            if(response.isRedirect){
                val next=response.header("Location")?.let{url.resolve(it)}?:error("Invalid redirect")
                url=(if(image)publicImageUrl(next.toString(),store)else publicProductUrl(next.toString(),store))?:error("Unsupported redirect")
            }else{
                require(response.isSuccessful)
                val body=requireNotNull(response.body)
                val type=body.contentType()?:error("No content type")
                require(if(image)type.type=="image"&&type.subtype in setOf("jpeg","png","webp") else (type.type=="text"&&type.subtype=="html")||(type.type=="application"&&type.subtype=="xhtml+xml"))
                val max=if(image)6*1024*1024 else 2*1024*1024
                require(body.contentLength()<=max.toLong())
                return PublicResponse(url,body.byteStream().use{it.readLimited(max)})
            }
        }
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
internal fun parseProductMetadata(html:String,page:HttpUrl,store:String):ProductMetadata{
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
            val page=publicResponse(url,seed.store,false)
            val metadata=parseProductMetadata(page.bytes.toString(Charsets.UTF_8),page.url,seed.store)
            val photo=if(needPhoto&&metadata.image!=null)try{
                val image=publicImageUrl(metadata.image,seed.store)?:error("Unsupported image")
                PrivateFiles.preparePhoto(publicResponse(image,seed.store,true).bytes)
            }catch(e:CancellationException){throw e}catch(_:Exception){null}else null
            ProductLookup(metadata.title,metadata.price,photo,if((seed.price==null&&metadata.price==null)||(needPhoto&&photo==null))"Не все сведения доступны. Проверьте цену и при необходимости добавьте фото вручную." else null)
        }catch(e:CancellationException){throw e}catch(_:Exception){
            ProductLookup(null,null,null,"Магазин не предоставил данные карточки. Можно продолжить вручную.")
        }
    }
}
