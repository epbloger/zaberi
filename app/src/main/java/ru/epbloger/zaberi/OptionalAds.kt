package ru.epbloger.zaberi
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.YandexAds

@Composable fun AdsSettings(preferences:Preferences){
    val context=LocalContext.current
    HorizontalDivider()
    Text("Персонализация рекламы",style=MaterialTheme.typography.titleMedium)
    Text("Реклама показывается по умолчанию. Вы можете разрешить подбор объявлений с учётом интересов или отказаться от него в любой момент. Без согласия персонализация выключена, обычные объявления остаются. SDK Яндекса может обрабатывать технические сведения; приложение не передаёт ему содержимое заказов и не разрешает отслеживание геолокации.")
    TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://yandex.ru/legal/confidential/")))}}){Text("Политика конфиденциальности Яндекса")}
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
        Text("Разрешить персонализацию",Modifier.weight(1f))
        Switch(preferences.personalizedAds,{preferences.personalizedAds(it)})
    }
}
@Composable fun OptionalBanner(personalized:Boolean){
    if(BuildConfig.BANNER_ID.isBlank())return
    val context=LocalContext.current
    var ready by remember{mutableStateOf(false)}
    var failed by remember{mutableStateOf(false)}
    var loaded by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){
        runCatching{
            YandexAds.setUserConsent(personalized)
            YandexAds.setLocationTracking(false)
            YandexAds.initialize(context.applicationContext){ready=true}
        }.onFailure{failed=true}
    }
    if(ready&&!failed){
        BoxWithConstraints(Modifier.fillMaxWidth()){
            val width=maxWidth.value.toInt()
            if(width<120)return@BoxWithConstraints
            val view=remember(width,context){
                runCatching{BannerAdView(context).apply{
                    visibility=View.GONE
                    setAdSize(BannerAdSize.inline(context,width,100))
                    setBannerAdEventListener(object:BannerAdEventListener{
                        override fun onAdLoaded(){loaded=true;visibility=View.VISIBLE}
                        override fun onAdFailedToLoad(adRequestError:AdRequestError){failed=true;visibility=View.GONE}
                        override fun onAdClicked(){}
                        override fun onImpression(impressionData:ImpressionData?){}
                    })
                }}.getOrNull()
            } ?: return@BoxWithConstraints
            DisposableEffect(view){
                runCatching{view.loadAd(AdRequest.Builder(BuildConfig.BANNER_ID).build())}.onFailure{failed=true}
                onDispose{runCatching{view.setBannerAdEventListener(null);view.destroy()}}
            }
            Column{
                if(loaded)Text(if(BuildConfig.DEBUG)"Тестовая реклама" else "Реклама",style=MaterialTheme.typography.labelSmall)
                AndroidView(factory={view},modifier=Modifier.fillMaxWidth())
            }
        }
    }
}
