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
    Text("Необязательная реклама",style=MaterialTheme.typography.titleMedium)
    Text("Реклама выключена по умолчанию. Если разрешить объявления, SDK Яндекса использует интернет и может обрабатывать технические данные устройства. Сведения о заказах, фото и суммы не передаются приложением в рекламный запрос. Согласие на персонализацию не передаётся, отслеживание геолокации отключено.")
    TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://yandex.ru/legal/confidential/")))}}){Text("Политика конфиденциальности Яндекса")}
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
        Text(if(BuildConfig.DEBUG)"Разрешить демонстрационные объявления" else "Разрешить объявления",Modifier.weight(1f))
        Switch(preferences.adsEnabled,{preferences.adsEnabled(it)},enabled=BuildConfig.BANNER_ID.isNotBlank())
    }
}
@Composable fun OptionalBanner(){
    if(BuildConfig.BANNER_ID.isBlank())return
    val context=LocalContext.current
    var ready by remember{mutableStateOf(false)}
    var failed by remember{mutableStateOf(false)}
    var loaded by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){
        runCatching{
            YandexAds.setUserConsent(false)
            YandexAds.setLocationTracking(false)
            YandexAds.initialize(context.applicationContext){ready=true}
        }.onFailure{failed=true}
    }
    if(ready&&!failed){
        BoxWithConstraints(Modifier.fillMaxWidth()){
            val width=maxWidth.value.toInt().coerceAtLeast(1)
            val view=remember(width){
                BannerAdView(context).apply{
                    visibility=View.GONE
                    setAdSize(BannerAdSize.inline(context,width,100))
                    setBannerAdEventListener(object:BannerAdEventListener{
                        override fun onAdLoaded(){loaded=true;visibility=View.VISIBLE}
                        override fun onAdFailedToLoad(adRequestError:AdRequestError){failed=true;visibility=View.GONE}
                        override fun onAdClicked(){}
                        override fun onImpression(impressionData:ImpressionData?){}
                    })
                }
            }
            DisposableEffect(view){
                runCatching{view.loadAd(AdRequest.Builder(BuildConfig.BANNER_ID).build())}.onFailure{failed=true}
                onDispose{view.setBannerAdEventListener(null);view.destroy()}
            }
            Column{
                if(loaded)Text(if(BuildConfig.DEBUG)"Тестовая реклама" else "Реклама",style=MaterialTheme.typography.labelSmall)
                AndroidView(factory={view},modifier=Modifier.fillMaxWidth())
            }
        }
    }
}
