package ru.epbloger.zaberi
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.delay
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
    var consentDialog by remember{mutableStateOf(false)}
    HorizontalDivider()
    Text("Реклама",style=MaterialTheme.typography.titleMedium)
    Text("Баннеры и редкие полноэкранные объявления помогают сохранять приложение бесплатным. Отключить рекламу в настройках нельзя. SDK Яндекса может обрабатывать технические сведения для показа и оценки эффективности объявлений. Содержимое заказов не передаётся рекламному SDK. Геолокация для рекламы отключена.")
    TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://yandex.ru/legal/confidential/")))}}){Text("Политика конфиденциальности Яндекса")}
    if(BuildConfig.STORE_AGE>=12){
        Text("Персонализация доступна только совершеннолетним по отдельному добровольному согласию. Без него приложение передаёт SDK отсутствие согласия и продолжает рекламные запросы; доступность и обработка данных зависят от правил SDK.")
        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
            Text("Мне исполнилось 18 лет",Modifier.weight(1f))
            Switch(preferences.adultAdsConfirmed,{preferences.confirmAdultAds(it)})
        }
        if(preferences.adultAdsEligible){
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                Text("Разрешить персонализацию",Modifier.weight(1f))
                Switch(preferences.personalizedAds,{if(it)consentDialog=true else preferences.setAdConsent(false)})
            }
        }
        if(consentDialog&&preferences.adultAdsEligible)AlertDialog(
            onDismissRequest={consentDialog=false},
            title={Text("Согласие на обработку данных для персонализации рекламы")},
            text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){
                Text("Я добровольно разрешаю ООО «ЯНДЕКС» обрабатывать доступные технические и рекламные идентификаторы, сведения об устройстве, сети, показах и взаимодействиях с рекламой для подбора объявлений по интересам и оценки их эффективности. Согласие действует до его отзыва. Отозвать согласие можно в разделе «Настройки → Реклама». Это не отключает обычную рекламу. Обработка Яндексом осуществляется по его политике конфиденциальности.")
                TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://yandex.ru/legal/confidential/")))}}){Text("Политика Яндекса")}
            }},
            confirmButton={TextButton(onClick={preferences.setAdConsent(true);consentDialog=false}){Text("Согласен")}},
            dismissButton={TextButton(onClick={consentDialog=false}){Text("Не согласен")}}
        )
    }else Text("Персонализированная реклама не используется при возрастной категории 0+ или 6+.")
}
// The same placement can be recreated on tab changes; don't request more often than RSYA allows.
private object BannerRequestGate {
    private var lastRequest:Long?=null
    suspend fun awaitSlot(){
        while(true){
            val now=SystemClock.elapsedRealtime()
            val wait=lastRequest?.let{(30_000L-(now-it)).coerceAtLeast(0L)}?:0L
            if(wait==0L){lastRequest=now;return}
            delay(wait)
        }
    }
}
@Composable fun OptionalBanner(ready:Boolean){
    if(BuildConfig.BANNER_ID.isBlank())return
    val context=LocalContext.current
    var failed by remember{mutableStateOf(false)}
    var loaded by remember{mutableStateOf(false)}
    LaunchedEffect(ready,failed){if(ready&&failed){delay(60_000L);loaded=false;failed=false}}
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
                        override fun onAdFailedToLoad(adRequestError:AdRequestError){loaded=false;failed=true;visibility=View.GONE}
                        override fun onAdClicked(){}
                        override fun onImpression(impressionData:ImpressionData?){}
                    })
                }}.getOrNull()
            } ?: return@BoxWithConstraints
            LaunchedEffect(view){
                BannerRequestGate.awaitSlot()
                runCatching{view.loadAd(AdRequest.Builder(BuildConfig.BANNER_ID).build())}.onFailure{loaded=false;failed=true}
            }
            DisposableEffect(view){
                onDispose{runCatching{view.setBannerAdEventListener(null);view.destroy()}}
            }
            Column(Modifier.fillMaxWidth().padding(bottom=if(loaded)UiSpacing.Block else 0.dp),verticalArrangement=Arrangement.spacedBy(if(loaded)UiSpacing.Tight else 0.dp)){
                if(loaded)Text(if(BuildConfig.DEBUG)"Тестовая реклама" else "Реклама",style=MaterialTheme.typography.labelSmall)
                key(view){AndroidView(factory={view},modifier=Modifier.fillMaxWidth())}
            }
        }
    }
}

/** Initialize once per consent state, before either advertising format can load. */
@Composable internal fun rememberAdsReady(personalized:Boolean,ageRestricted:Boolean):Boolean{
    val context=LocalContext.current.applicationContext
    val ready=remember(personalized,ageRestricted){mutableStateOf(false)}
    DisposableEffect(context,personalized,ageRestricted){
        var active=true
        if(BuildConfig.BANNER_ID.isNotBlank()||BuildConfig.INTERSTITIAL_ID.isNotBlank()){
            runCatching{
                YandexAds.setUserConsent(personalized)
                YandexAds.setAgeRestricted(ageRestricted)
                YandexAds.setLocationTracking(false)
                YandexAds.initialize(context){if(active)ready.value=true}
            }
        }
        onDispose{active=false}
    }
    return ready.value
}
