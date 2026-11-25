package ru.epbloger.zaberi
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Entity(tableName = "orders")
data class Order(
    @PrimaryKey val id: String, val name: String, val store: String, val price: Long?,
    val paid: Long, val status: String, val deadline: String?, val point: String,
    val link: String, val note: String, val created: String, val expectedRefund: Long = 0,
    val receivedRefund: Long = 0, val cancelled: Boolean = false, val returned: Boolean = false
) { @get:Ignore val state get() = Status.valueOf(status) }
@Entity(tableName = "transactions", foreignKeys = [ForeignKey(entity=Order::class, parentColumns=["id"], childColumns=["orderId"], onDelete=ForeignKey.CASCADE)], indices=[Index("orderId")])
data class Transaction(@PrimaryKey val id: String, val orderId: String, val date: String, val amount: Long)
@Entity(tableName = "history", foreignKeys = [ForeignKey(entity=Order::class, parentColumns=["id"], childColumns=["orderId"], onDelete=ForeignKey.CASCADE)], indices=[Index("orderId")])
data class History(@PrimaryKey val id: String, val orderId: String, val date: String, val label: String)
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
}
@Database(entities=[Order::class, Transaction::class, History::class], version=1, exportSchema=true)
abstract class ZaberiDb: RoomDatabase() {
    abstract fun orders(): OrdersDao
    companion object {
        @Volatile private var instance: ZaberiDb? = null
        fun get(context: Context): ZaberiDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ZaberiDb::class.java, "zaberi.db").build().also { instance=it }
        }
    }
}