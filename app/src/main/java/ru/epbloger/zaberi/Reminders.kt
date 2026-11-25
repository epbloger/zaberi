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
class Reminders(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val c=applicationContext
        if (Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return Result.success()
        if (LocalTime.now().hour !in 9..21) return Result.success()
        val manager=c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("deadlines","Сроки заказов",NotificationManager.IMPORTANCE_DEFAULT))
        val prefs=c.getSharedPreferences("reminders",Context.MODE_PRIVATE)
        val today=LocalDate.now()
        val orders=ZaberiDb.get(c).orders().all()
        for(o in orders) {
            val date=runCatching { LocalDate.parse(o.deadline) }.getOrNull() ?: continue
            val days=ChronoUnit.DAYS.between(today,date)
            val due=when(o.state) {
                Status.READY -> days in listOf(3L,1L,0L,-1L)
                Status.RETURN_PLANNED -> days in listOf(3L,0L,-1L)
                Status.TRANSIT -> days==0L
                Status.REFUND -> days==0L || days == -3L
                else -> false
            }
            val key="${o.id}:${o.status}:${o.deadline}:$today"
            if(!due || prefs.getBoolean(key,false)) continue
            val intent=Intent(c,MainActivity::class.java).putExtra("orderId",o.id)
            val pending=PendingIntent.getActivity(c,o.id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(o.id.hashCode(),Notification.Builder(c,"deadlines").setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(o.state.deadlineLabel ?: "Проверь заказ")
                .setContentText("${o.name} · ${o.store} · $date").setContentIntent(pending).setAutoCancel(true).build())
            prefs.edit().putBoolean(key,true).apply()
        }
        prefs.edit().also { editor -> prefs.all.keys.filterNot { it.endsWith(":$today") }.forEach { editor.remove(it) } }.apply()
        return Result.success()
    }
    companion object {
        fun enqueue(c: Context) {
            val work=WorkManager.getInstance(c)
            work.enqueueUniquePeriodicWork("deadlines-periodic",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<Reminders>(1,TimeUnit.HOURS).build())
            work.enqueueUniqueWork("deadlines-now",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<Reminders>().build())
        }
    }
}