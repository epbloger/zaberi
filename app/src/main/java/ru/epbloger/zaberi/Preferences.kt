package ru.epbloger.zaberi
import android.content.Context
import android.app.NotificationManager
import androidx.compose.runtime.*
class Preferences(private val context:Context) {
    private val prefs=context.getSharedPreferences("preferences",Context.MODE_PRIVATE)
    var theme by mutableStateOf(prefs.getString("theme","system") ?: "system")
        private set
    var enabled by mutableStateOf(prefs.getBoolean("enabled",true))
        private set
    var hour by mutableIntStateOf(prefs.getInt("hour",9).coerceIn(7,21))
        private set
    var showDetails by mutableStateOf(prefs.getBoolean("details",false))
        private set
    fun theme(value:String){require(value in listOf("system","light","dark"));theme=value;prefs.edit().putString("theme",value).apply()}
    fun enabled(value:Boolean){enabled=value;prefs.edit().putBoolean("enabled",value).apply();if(!value)clear() else Reminders.enqueue(context)}
    fun hour(value:Int){hour=value.coerceIn(7,21);prefs.edit().putInt("hour",hour).apply();Reminders.enqueue(context)}
    fun details(value:Boolean){showDetails=value;prefs.edit().putBoolean("details",value).apply();clear();Reminders.enqueue(context)}
    var leadDays by mutableIntStateOf(prefs.getInt("leadDays",3).coerceIn(0,7))
        private set
    var overdue by mutableStateOf(prefs.getBoolean("overdue",true))
        private set
    val notificationAsked get()=prefs.getBoolean("notificationAsked",false)
    fun markNotificationAsked(){prefs.edit().putBoolean("notificationAsked",true).apply()}
    var personalizedAds by mutableStateOf(prefs.getBoolean("personalizedAds",false))
        private set
    fun leadDays(value:Int){leadDays=value.coerceIn(0,7);prefs.edit().putInt("leadDays",leadDays).apply();Reminders.enqueue(context)}
    fun overdue(value:Boolean){overdue=value;prefs.edit().putBoolean("overdue",value).apply();Reminders.enqueue(context)}
    fun personalizedAds(value:Boolean){personalizedAds=value;prefs.edit().putBoolean("personalizedAds",value).remove("adsEnabled").apply();runCatching{com.yandex.mobile.ads.common.YandexAds.setUserConsent(value)}}
    private fun clear(){context.getSystemService(NotificationManager::class.java).cancelAll()}
}