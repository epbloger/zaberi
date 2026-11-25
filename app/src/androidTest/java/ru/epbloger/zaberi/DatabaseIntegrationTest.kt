package ru.epbloger.zaberi

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseIntegrationTest {
    private lateinit var db:ZaberiDb
    @Before fun setup(){
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),ZaberiDb::class.java).build()
    }
    @After fun close(){db.close()}
    private fun order(id:String)=Order(id,"Тестовая покупка","Тестовый магазин",10000,10000,Status.READY.name,"2026-11-25","Тестовый ПВЗ","","","2026-11-20",category="Тест")
    @Test fun atomicWritesAndCascade()=runBlocking{
        val dao=db.orders();val o=order("order-test")
        db.withTransaction{
            dao.insert(o)
            dao.transaction(Transaction("payment-test",o.id,"2026-11-20",10000))
            dao.history(History("history-test",o.id,"2026-11-20T12:00:00","Добавлен"))
            dao.photo(Attachment("photo-test",o.id,byteArrayOf(1,2,3)))
        }
        assertEquals(1,dao.all().size)
        assertEquals(10000L,dao.allTransactions().sumOf{it.amount})
        assertEquals(3L,dao.photoBytes())
        dao.delete(o)
        assertTrue(dao.allTransactions().isEmpty())
        assertTrue(dao.allHistory().isEmpty())
        assertTrue(dao.allPhotos().isEmpty())
    }
    @Test fun rollbackKeepsOrders()=runBlocking{
        val dao=db.orders()
        dao.insert(order("one"))
        val failed=runCatching{db.withTransaction{dao.clearOrders();dao.insert(order("two"));error("rollback test")}}
        assertTrue(failed.isFailure)
        assertEquals(listOf("one"),dao.all().map{it.id})
    }
    @Test fun catalogDeduplicates()=runBlocking{
        val dao=db.orders()
        dao.remember(CatalogEntry("one","store","","Тест"))
        dao.remember(CatalogEntry("two","store","","Тест"))
        assertEquals(1,dao.allCatalog().size)
        dao.clearCatalog()
        assertTrue(dao.allCatalog().isEmpty())
    }
    @Test fun pickupCoordinatesAreSaved()=runBlocking{
        val dao=db.orders();val o=order("map-test").copy(pointLat=56.84,pointLon=60.61)
        dao.insert(o)
        dao.remember(CatalogEntry("pickup","point",o.store,o.point,56.84,60.61))
        assertEquals(56.84,dao.get(o.id)!!.pointLat!!,0.000001)
        assertEquals(60.61,dao.allCatalog().single().pointLon!!,0.000001)
        dao.updatePoint(o.store,o.point,55.75,37.62)
        assertEquals(37.62,dao.allCatalog().single().pointLon!!,0.000001)
        assertEquals(60.61,dao.get(o.id)!!.pointLon!!,0.000001)
    }
    @Test fun partialRefundLedger()=runBlocking{
        val dao=db.orders();val o=order("refund-test").copy(status=Status.REFUND.name,expectedRefund=8000,receivedRefund=3000,returned=true)
        db.withTransaction{dao.insert(o);dao.transaction(Transaction("paid",o.id,"2026-11-20",10000));dao.transaction(Transaction("refund1",o.id,"2026-11-21",-3000))}
        val stored=dao.get(o.id)!!
        assertEquals(5000L,remaining(stored.expectedRefund,stored.receivedRefund))
        assertEquals(7000L,dao.allTransactions().sumOf{it.amount})
    }
}