package ru.epbloger.zaberi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Invoked explicitly before and after adb install -r, never against ordinary test data. */
@RunWith(AndroidJUnit4::class)
class PublishedApkUpgradeTest {
    private val first=Order("upgrade-paid","Чайник","Ozon",129950L,129950L,Status.REFUND.name,"2026-12-01","ПВЗ","https://www.ozon.ru/product/upgrade/","Заметка","2026-11-20",expectedRefund=100000L,receivedRefund=35000L,returned=true,category="Дом",pointLat=56.84,pointLon=60.61)
    private val second=Order("upgrade-unpaid","Книга","Wildberries",50000L,0L,Status.TRANSIT.name,null,"","","","2026-11-20")
    private val payment=Transaction("upgrade-payment",first.id,"2026-11-20",129950L)
    private val refund=Transaction("upgrade-refund",first.id,"2026-11-21",-35000L)
    private val event=History("upgrade-history",first.id,"2026-11-20T12:00:00","Добавлен")
    private val point=CatalogEntry("upgrade-point","point","Ozon","ПВЗ",56.84,60.61)
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
    @Suppress("DEPRECATION")
    @Test fun preservePublishedDataAcrossInstallUpdate()=runBlocking{
        val phase=InstrumentationRegistry.getArguments().getString("upgradePhase")
        assumeTrue(phase=="seed"||phase=="verify")
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val info=ctx.packageManager.getPackageInfo(ctx.packageName,0)
        val dao=ZaberiDb.get(ctx).orders()
        val prefs=ctx.getSharedPreferences("preferences",Context.MODE_PRIVATE)
        if(phase=="seed"){
            assertEquals("1.0.3",info.versionName)
            assertEquals(111,info.versionCode)
            assertTrue("Upgrade fixture requires a fresh baseline",dao.all().isEmpty())
            val bitmap=Bitmap.createBitmap(40,60,Bitmap.Config.ARGB_8888)
            val jpeg=try{ByteArrayOutputStream().use{out->assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,80,out));out.toByteArray()}}finally{bitmap.recycle()}
            dao.insert(first);dao.insert(second)
            dao.transaction(payment);dao.transaction(refund);dao.history(event);dao.remember(point)
            dao.photo(Attachment("upgrade-photo",first.id,jpeg))
            assertTrue(prefs.edit().putString("theme","dark").putBoolean("enabled",false)
                .putInt("hour",17).putInt("leadDays",5).putBoolean("overdue",false)
                .putBoolean("details",true).putString("upgrade_fixture_digest",digest(jpeg)).commit())
        }else{
            assertEquals("1.1.0",info.versionName)
            assertEquals(112,info.versionCode)
            assertEquals(setOf(first,second),dao.all().toSet())
            assertEquals(setOf(payment,refund),dao.allTransactions().toSet())
            assertEquals(listOf(event),dao.allHistory())
            assertEquals(listOf(point),dao.allCatalog())
            val photo=dao.allPhotos().single()
            assertEquals("upgrade-photo",photo.id)
            assertEquals(first.id,photo.orderId)
            assertEquals(prefs.getString("upgrade_fixture_digest",null),digest(photo.jpeg))
            val bitmap=BitmapFactory.decodeByteArray(photo.jpeg,0,photo.jpeg.size)
            assertNotNull(bitmap)
            try{assertEquals(40,bitmap.width);assertEquals(60,bitmap.height)}finally{bitmap.recycle()}
            val settings=Preferences(ctx)
            assertEquals("dark",settings.theme)
            assertFalse(settings.enabled)
            assertEquals(17,settings.hour)
            assertEquals(5,settings.leadDays)
            assertFalse(settings.overdue)
            assertTrue(settings.showDetails)
            assertFalse(settings.onlineProductImport)
            assertFalse(settings.tutorialSeen)
        }
    }
}
