package ru.epbloger.zaberi
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
@Entity(tableName = "orders")
data class Order(
    @PrimaryKey val id: String, val name: String, val store: String, val price: Long?,
    val paid: Long, val status: String, val deadline: String?, val point: String,
    val link: String, val note: String, val created: String, val expectedRefund: Long = 0,
    val receivedRefund: Long = 0, val cancelled: Boolean = false, val returned: Boolean = false,
    @ColumnInfo(defaultValue="''") val category:String = "",
    val pointLat:Double?=null, val pointLon:Double?=null
) { @get:Ignore val state get() = Status.valueOf(status) }
@Entity(tableName = "transactions", foreignKeys = [ForeignKey(entity=Order::class, parentColumns=["id"], childColumns=["orderId"], onDelete=ForeignKey.CASCADE)], indices=[Index("orderId")])
data class Transaction(@PrimaryKey val id: String, val orderId: String, val date: String, val amount: Long)
@Entity(tableName = "history", foreignKeys = [ForeignKey(entity=Order::class, parentColumns=["id"], childColumns=["orderId"], onDelete=ForeignKey.CASCADE)], indices=[Index("orderId")])
data class History(@PrimaryKey val id: String, val orderId: String, val date: String, val label: String)
@Entity(tableName="attachments",foreignKeys=[ForeignKey(entity=Order::class,parentColumns=["id"],childColumns=["orderId"],onDelete=ForeignKey.CASCADE)],indices=[Index("orderId")])
data class Attachment(@PrimaryKey val id:String,val orderId:String,val jpeg:ByteArray)
@Entity(tableName="catalog",indices=[Index(value=["kind","parent","value"],unique=true)])
data class CatalogEntry(@PrimaryKey val id:String,val kind:String,val parent:String,val value:String,val pointLat:Double?=null,val pointLon:Double?=null)
@Dao interface OrdersDao {
    @Query("SELECT * FROM orders ORDER BY created DESC, name") fun observe(): Flow<List<Order>>
    @Query("SELECT * FROM orders") suspend fun all(): List<Order>
    @Query("SELECT * FROM orders WHERE id = :id") suspend fun get(id: String): Order?
    @Query("SELECT * FROM transactions") fun transactions(): Flow<List<Transaction>>
    @Query("SELECT * FROM history ORDER BY date DESC") fun history(): Flow<List<History>>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(order: Order)
    @Update suspend fun update(order: Order)
    @Insert suspend fun transaction(transaction: Transaction)
    @Insert suspend fun history(history: History)
    @Delete suspend fun delete(order: Order)
    @Query("SELECT * FROM transactions") suspend fun allTransactions():List<Transaction>
    @Query("SELECT * FROM history") suspend fun allHistory():List<History>
    @Query("SELECT * FROM attachments WHERE orderId=:id") fun photos(id:String):Flow<List<Attachment>>
    @Query("SELECT * FROM attachments") suspend fun allPhotos():List<Attachment>
    @Query("SELECT COALESCE(SUM(length(jpeg)),0) FROM attachments") suspend fun photoBytes():Long
    @Query("SELECT COUNT(*) FROM attachments WHERE orderId=:id") suspend fun photoCount(id:String):Int
    @Insert suspend fun photo(photo:Attachment)
    @Query("DELETE FROM attachments WHERE id=:id") suspend fun removePhoto(id:String)
    @Query("DELETE FROM orders") suspend fun clearOrders()
    @Query("SELECT * FROM catalog ORDER BY value COLLATE NOCASE") fun catalog():Flow<List<CatalogEntry>>
    @Query("SELECT * FROM catalog") suspend fun allCatalog():List<CatalogEntry>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun remember(entry:CatalogEntry)
    @Query("UPDATE catalog SET pointLat=:lat,pointLon=:lon WHERE kind='point' AND parent=:store AND value=:value") suspend fun updatePoint(store:String,value:String,lat:Double,lon:Double)
    @Query("DELETE FROM catalog WHERE id=:id") suspend fun forget(id:String)
    @Query("DELETE FROM catalog") suspend fun clearCatalog()
}
@Database(entities=[Order::class, Transaction::class, History::class, Attachment::class, CatalogEntry::class], version=4, exportSchema=true)
abstract class ZaberiDb: RoomDatabase() {
    abstract fun orders(): OrdersDao
    companion object {
        private val MIGRATION_1_2=object:Migration(1,2){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("CREATE TABLE IF NOT EXISTS attachments (id TEXT NOT NULL PRIMARY KEY, orderId TEXT NOT NULL, jpeg BLOB NOT NULL, FOREIGN KEY(orderId) REFERENCES orders(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_orderId ON attachments(orderId)")
            }
        }
        private val MIGRATION_2_3=object:Migration(2,3){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("ALTER TABLE orders ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS catalog (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, parent TEXT NOT NULL, value TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_catalog_kind_parent_value ON catalog(kind,parent,value)")
                db.execSQL("INSERT OR IGNORE INTO catalog SELECT lower(hex(randomblob(16))), 'store', '', store FROM orders WHERE trim(store) <> '' GROUP BY store")
                db.execSQL("INSERT OR IGNORE INTO catalog SELECT lower(hex(randomblob(16))), 'point', store, point FROM orders WHERE trim(point) <> '' GROUP BY store,point")
            }
        }
        internal val MIGRATION_3_4=object:Migration(3,4){
            override fun migrate(db:SupportSQLiteDatabase){
                db.execSQL("ALTER TABLE orders ADD COLUMN pointLat REAL")
                db.execSQL("ALTER TABLE orders ADD COLUMN pointLon REAL")
                db.execSQL("ALTER TABLE catalog ADD COLUMN pointLat REAL")
                db.execSQL("ALTER TABLE catalog ADD COLUMN pointLon REAL")
            }
        }
        @Volatile private var instance: ZaberiDb? = null
        fun get(context: Context): ZaberiDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ZaberiDb::class.java, "zaberi.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build().also { instance=it }
        }
    }
}