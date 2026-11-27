package ru.epbloger.zaberi

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    @Test fun encryptedBackupRestoresOrdersRefundsAndPhotosAndRejectsWrongPassword()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ZaberiDb::class.java).build()
        val file=File.createTempFile("round-trip-",".zaberi",ctx.cacheDir)
        val password="backup-test-123"
        try{
            val dao=db.orders()
            val a=Order("paid","Чайник","Ozon",129950L,129950L,Status.REFUND.name,"2026-12-01","ПВЗ","https://www.ozon.ru/product/example/","Тест","2026-11-20",expectedRefund=100000L,receivedRefund=35000L,returned=true,category="Дом",pointLat=56.84,pointLon=60.61)
            val b=Order("unpaid","Другой товар","Wildberries",50000L,0,Status.TRANSIT.name,null,"","","","2026-11-20")
            val bitmap=Bitmap.createBitmap(40,60,Bitmap.Config.ARGB_8888)
            val jpeg=try{ByteArrayOutputStream().use{out->assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,80,out));out.toByteArray()}}finally{bitmap.recycle()}
            val payment=Transaction("p",a.id,"2026-11-20",129950L)
            val refund=Transaction("r",a.id,"2026-11-21",-35000L)
            val history=History("h",a.id,"2026-11-20T12:00:00","Добавлен")
            val catalog=CatalogEntry("c","point","Ozon","ПВЗ",56.84,60.61)
            db.withTransaction{
                dao.insert(a);dao.insert(b);dao.transaction(payment);dao.transaction(refund)
                dao.history(history);dao.remember(catalog);PrivateFiles.attachPreparedPhoto(dao,a.id,jpeg)
            }
            val savedPhoto=dao.allPhotos().single()
            val uri=Uri.fromFile(file)
            PrivateFiles.exportFrom(ctx,uri,password,db)
            assertTrue(file.length()>52)
            assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("Чайник"))
            db.withTransaction{dao.clearOrders();dao.clearCatalog();dao.insert(b.copy(name="Новая запись"))}
            assertTrue(runCatching{PrivateFiles.restoreInto(ctx,uri,"wrong-password",db)}.isFailure)
            assertEquals(listOf(b.copy(name="Новая запись")),dao.all())
            PrivateFiles.restoreInto(ctx,uri,password,db)
            assertEquals(setOf(a,b),dao.all().toSet())
            assertEquals(setOf(payment,refund),dao.allTransactions().toSet())
            assertEquals(listOf(history),dao.allHistory())
            assertEquals(listOf(catalog),dao.allCatalog())
            val restored=dao.allPhotos().single()
            assertEquals(savedPhoto.id,restored.id)
            assertEquals(a.id,restored.orderId)
            assertArrayEquals(jpeg,restored.jpeg)
            assertEquals(0L,dao.get(b.id)!!.paid)
            dao.delete(a)
            assertEquals(listOf(b),dao.all())
            assertTrue(dao.allTransactions().isEmpty())
            assertTrue(dao.allPhotos().isEmpty())
            PrivateFiles.restoreInto(ctx,uri,password,db)
            assertEquals(setOf(a,b),dao.all().toSet())
        }finally{db.close();file.delete()}
    }
}
