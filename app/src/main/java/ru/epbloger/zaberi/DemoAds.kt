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

@Composable fun AdTestSettings(preferences:Preferences){
    val context=LocalContext.current
    HorizontalDivider()
    Text("Тестовая реклама",style=MaterialTheme.typography.titleMedium)
    Text("При включении тестового баннера SDK Яндекса использует интернет и может обрабатывать технические сведения об устройстве. Данные заказов, фото и суммы не передаются в рекламный запрос. Персонализация и геолокация отключены в настройках интеграции. Это не настоящая монетизация.")
    TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://yandex.ru/legal/confidential/")))}}){Text("Политика конфиденциальности Яндекса")}
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
        Text("Разрешить тестовые объявления",Modifier.weight(1f))
        Switch(preferences.adsTest,{preferences.adsTest(it)})
    }
}
@Composable fun DemoBanner(){
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
                runCatching{view.loadAd(AdRequest.Builder("demo-banner-yandex").build())}.onFailure{failed=true}
                onDispose{view.setBannerAdEventListener(null);view.destroy()}
            }
            Column{
                if(loaded)Text("Тестовая реклама",style=MaterialTheme.typography.labelSmall)
                AndroidView(factory={view},modifier=Modifier.fillMaxWidth())
            }
        }
    }
}
