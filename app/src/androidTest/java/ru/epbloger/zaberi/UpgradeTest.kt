package ru.epbloger.zaberi

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpgradeTest {
    @Test fun migrateVersion3PreservesOrdersAndMoney()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val name="migration-test-v3.db"
        ctx.deleteDatabase(name)
        val path=ctx.getDatabasePath(name)
        path.parentFile?.mkdirs()
        try{
            SQLiteDatabase.openOrCreateDatabase(path,null).use{old->
                old.execSQL("CREATE TABLE orders (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, store TEXT NOT NULL, price INTEGER, paid INTEGER NOT NULL, status TEXT NOT NULL, deadline TEXT, point TEXT NOT NULL, link TEXT NOT NULL, note TEXT NOT NULL, created TEXT NOT NULL, expectedRefund INTEGER NOT NULL, receivedRefund INTEGER NOT NULL, cancelled INTEGER NOT NULL, returned INTEGER NOT NULL, category TEXT NOT NULL DEFAULT '')")
                old.execSQL("CREATE TABLE transactions (id TEXT NOT NULL PRIMARY KEY, orderId TEXT NOT NULL, date TEXT NOT NULL, amount INTEGER NOT NULL, FOREIGN KEY(orderId) REFERENCES orders(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                old.execSQL("CREATE INDEX index_transactions_orderId ON transactions(orderId)")
                old.execSQL("CREATE TABLE history (id TEXT NOT NULL PRIMARY KEY, orderId TEXT NOT NULL, date TEXT NOT NULL, label TEXT NOT NULL, FOREIGN KEY(orderId) REFERENCES orders(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                old.execSQL("CREATE INDEX index_history_orderId ON history(orderId)")
                old.execSQL("CREATE TABLE attachments (id TEXT NOT NULL PRIMARY KEY, orderId TEXT NOT NULL, jpeg BLOB NOT NULL, FOREIGN KEY(orderId) REFERENCES orders(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                old.execSQL("CREATE INDEX index_attachments_orderId ON attachments(orderId)")
                old.execSQL("CREATE TABLE catalog (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, parent TEXT NOT NULL, value TEXT NOT NULL)")
                old.execSQL("CREATE UNIQUE INDEX index_catalog_kind_parent_value ON catalog(kind,parent,value)")
                old.execSQL("INSERT INTO orders VALUES ('old','Test order','Test store',10000,10000,'READY','2026-11-25','Test pickup','','','2026-11-20',0,0,0,0,'Test category')")
                old.execSQL("INSERT INTO transactions VALUES ('tx','old','2026-11-20',10000)")
                old.execSQL("INSERT INTO catalog VALUES ('point','point','Test store','Test pickup')")
                old.version=3
            }
            val upgraded=Room.databaseBuilder(ctx,ZaberiDb::class.java,name).addMigrations(ZaberiDb.MIGRATION_3_4).build()
            try{
                val dao=upgraded.orders()
                val order=dao.get("old")!!
                assertEquals("Test pickup",order.point)
                assertNull(order.pointLat)
                assertNull(order.pointLon)
                assertEquals(10000L,dao.allTransactions().single().amount)
                assertEquals("Test category",order.category)
                assertNull(dao.allCatalog().single().pointLat)
                dao.update(order.copy(pointLat=56.84,pointLon=60.61))
                assertEquals(56.84,dao.get("old")!!.pointLat!!,0.000001)
            }finally{upgraded.close()}
        }finally{ctx.deleteDatabase(name)}
    }
}
