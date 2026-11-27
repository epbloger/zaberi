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
    val link=sharedUrl.find(bounded)?.value?.trimEnd('.',',',';',')',']').orEmpty()
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
    if(intent?.action!=Intent.ACTION_SEND)return null
    val text=runCatching{intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()}.getOrDefault("")
    val subject=runCatching{intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()}.getOrDefault("")
    val combined=combinedSharedText(text,subject)
    val image=if(intent.type?.startsWith("image/")==true)runCatching{
        (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?:intent.clipData?.takeIf{it.itemCount>0}?.getItemAt(0)?.uri)?.takeIf{it.scheme=="content"}
    }.getOrNull() else null
    if(combined.isBlank()&&image==null)return null
    return SharedProductRequest(uid(),combined,image)
}
