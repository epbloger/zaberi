package ru.epbloger.zaberi
import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// All order mutations, restore operations and reminder scans share one gate.
// A deleted order cannot be posted from an outdated worker snapshot.
internal val reminderMutationMutex=Mutex()
internal val reminderNotificationMonitor=Any()

class Reminders(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = reminderMutationMutex.withLock {
        val c=applicationContext
        if (Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return@withLock Result.success()
        val settings=c.getSharedPreferences("preferences",Context.MODE_PRIVATE)
        if(!settings.getBoolean("enabled",true))return@withLock Result.success()
        if(LocalTime.now().hour !in settings.getInt("hour",9).coerceIn(7,21)..21)return@withLock Result.success()
         val manager=c.getSystemService(NotificationManager::class.java)?:return@withLock Result.success()
        if(!manager.areNotificationsEnabled())return@withLock Result.success()
        manager.createNotificationChannel(NotificationChannel("deadlines","Сроки заказов",NotificationManager.IMPORTANCE_DEFAULT))
        val prefs=c.getSharedPreferences("reminders",Context.MODE_PRIVATE)
        val today=LocalDate.now()
        val orders=ZaberiDb.get(c).orders().all()
        for(o in orders) {
            val date=runCatching { LocalDate.parse(o.deadline) }.getOrNull() ?: continue
            val days=ChronoUnit.DAYS.between(today,date)
            val due=reminderDue(o.state,days,settings.getInt("leadDays",3),settings.getBoolean("overdue",true))
            val key="${o.id}:${o.status}:${o.deadline}:$today"
            if(!due || prefs.getBoolean(key,false)) continue
            val intent=Intent(c,MainActivity::class.java).setData(android.net.Uri.parse("zaberi://order/"+o.id)).putExtra("orderId",o.id)
            val pending=PendingIntent.getActivity(c,o.id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try { synchronized(reminderNotificationMonitor){
                if(!settings.getBoolean("enabled",true)||!manager.areNotificationsEnabled()||
                    LocalTime.now().hour !in settings.getInt("hour",9).coerceIn(7,21)..21||
                    !reminderDue(o.state,days,settings.getInt("leadDays",3),settings.getBoolean("overdue",true)))return@synchronized
                val details=settings.getBoolean("details",false)
                manager.notify(o.id,0,Notification.Builder(c,"deadlines").setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(Notification.Builder(c,"deadlines").setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Забери").setContentText("Проверьте сроки заказов").build())
                .setContentTitle(o.state.deadlineLabel ?: "Проверь заказ")
                .setContentText(if(details) "${o.name} · ${o.store} · $date" else "Откройте приложение, чтобы проверить заказ").setContentIntent(pending).setAutoCancel(true).build())
                prefs.edit().putBoolean(key,true).apply()
            }} catch (_:SecurityException) { return@withLock Result.success() }
        }
        prefs.edit().also { editor -> prefs.all.keys.filterNot { it.endsWith(":$today") }.forEach { editor.remove(it) } }.apply()
        Result.success()
    }
    companion object {
        fun enqueue(c: Context) {
            val work=WorkManager.getInstance(c)
            work.enqueueUniquePeriodicWork("deadlines-periodic",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<Reminders>(1,TimeUnit.HOURS).build())
            work.enqueueUniqueWork("deadlines-now",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<Reminders>().build())
        }
    }
}