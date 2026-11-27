package ru.epbloger.zaberi
import android.content.Intent
import android.net.Uri

internal data class SharedProductRequest(val id:String,val text:String,val image:Uri?)
internal data class SharedProductSeed(val name:String,val store:String,val link:String,val price:Long?)
internal data class ProductImportState(val requestId:String,val seed:SharedProductSeed,
    val title:String?=null,val price:Long?=null,val photo:ByteArray?=null,
    val loading:Boolean=false,val attempted:Boolean=false,val message:String?=null)
private val sharedUrl=Regex("https?://[^\\s<>\"']+",RegexOption.IGNORE_CASE)
private val rublePrice=Regex("(?<![\\d.,])([+\\-−]?[ \\u00a0\\u202f]*[0-9][0-9., \\u00a0\\u202f]*?)\\s*(?:₽|руб(?:\\.|лей|ля|ль)?(?![\\p{L}])|RUB\\b)",RegexOption.IGNORE_CASE)
private val validSharedAmount=Regex("(?:[0-9]+|[0-9]{1,3}(?:[ \\u00a0\\u202f][0-9]{3})+)(?:[.,][0-9]{1,2})?")
private const val MAX_SHARED_TEXT=8192
internal fun sharedProductSeed(text:String):SharedProductSeed {
    val bounded=text.take(MAX_SHARED_TEXT)
    val links=sharedUrl.findAll(bounded).map{it.value.trimEnd('.',',',';',')',']')}.toList()
    val link=links.firstOrNull{candidate->val store=detectStore(candidate);store!=null&&publicProductUrl(candidate,store)!=null}
        ?:links.firstOrNull{detectStore(it)!=null}?:links.firstOrNull().orEmpty()
    val clean=sharedUrl.replace(bounded," ")
    val candidates=rublePrice.findAll(clean).map{it.groupValues[1].trim()}.toList()
    val prices=candidates.mapNotNull{value->value.takeIf{validSharedAmount.matches(it)}?.let{parseMoney(it.replace('\u00a0',' ').replace('\u202f',' '))}}
    val price=prices.distinct().singleOrNull()?.takeIf{prices.size==candidates.size}
    val ambiguous=Regex("(?:от|до)\\s*\\d|\\d\\s*[-–—]\\s*\\d",RegexOption.IGNORE_CASE).containsMatchIn(clean)
    val title=clean.lineSequence().map{rublePrice.replace(it,"").trim(' ',':','-','–','—','\t')}
        .firstOrNull{it.isNotBlank()&&it.any(Char::isLetter)&&!it.matches(Regex("(?i)(цена|стоимость|поделиться|ссылка|посмотри(?:те)?(?: товар)?|смотри(?:те)?(?: товар)?|товар|купить)[!:. ]*"))}
        ?.take(120).orEmpty()
    return SharedProductSeed(title,detectStore(link).orEmpty(),link,if(ambiguous)null else price)
}
internal fun combinedSharedText(text:String,subject:String):String {
    val bounded=text.take(MAX_SHARED_TEXT)
    val title=subject.take(200).trim()
    if(title.isBlank()||sharedProductSeed(bounded).name.isNotBlank())return bounded
    return title+"\n"+bounded.take(MAX_SHARED_TEXT-title.length-1)
}
@Suppress("DEPRECATION")
internal fun sharedProductRequest(intent:Intent?):SharedProductRequest? {
    if(intent==null||intent.action !in setOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE))return null
    val chunks=mutableListOf<String>()
    fun add(value:CharSequence?){value?.toString()?.take(MAX_SHARED_TEXT)?.takeIf{it.isNotBlank()&&!chunks.contains(it)}?.let{chunks.add(it)}}
    fun addUri(uri:Uri?){if(uri?.scheme in setOf("http","https"))add(uri.toString())}
    add(runCatching{intent.getCharSequenceExtra(Intent.EXTRA_TEXT)}.getOrNull())
    if(intent.action==Intent.ACTION_SEND_MULTIPLE){
        runCatching{intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)}.getOrNull()?.take(8)?.forEach{add(it)}
    }
    val html=runCatching{intent.getStringExtra(Intent.EXTRA_HTML_TEXT)}.getOrNull()?.take(MAX_SHARED_TEXT)
    if(!html.isNullOrBlank()){
        val doc=org.jsoup.Jsoup.parse(html)
        if(chunks.isEmpty())add(doc.text())
        doc.select("a[href]").take(8).forEach{addUri(Uri.parse(it.attr("href")))}
    }
    addUri(intent.data)
    val clip=intent.clipData
    var image:Uri?=null
    if(clip!=null)for(i in 0 until minOf(clip.itemCount,8)){
        val item=clip.getItemAt(i)
        add(item.text);addUri(item.uri)
        if(image==null&&item.uri?.scheme=="content")image=item.uri
    }
    val stream=if(intent.action==Intent.ACTION_SEND_MULTIPLE)
        runCatching{intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)}.getOrNull()?.firstOrNull{it.scheme=="content"}
        else runCatching{intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)}.getOrNull()?.takeIf{it.scheme=="content"}
    image=stream?:image
    val subject=runCatching{intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString().orEmpty()}.getOrDefault("")
    val combined=combinedSharedText(chunks.joinToString("\n").take(MAX_SHARED_TEXT),subject)
    if(combined.isBlank()&&image==null)return null
    return SharedProductRequest(uid(),combined,image)
}
