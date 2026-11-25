package ru.epbloger.zaberi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private const val MAX_FILE=32*1024*1024
private const val MAX_PHOTO=240*1024
private val MAGIC="ZABERI02".toByteArray(Charsets.US_ASCII)
private fun InputStream.bounded(limit:Int):ByteArray {
    val out=ByteArrayOutputStream()
    val b=ByteArray(8192)
    while(true){val n=read(b);if(n<0)break;require(out.size()+n<=limit){"Файл слишком большой"};out.write(b,0,n)}
    return out.toByteArray()
}
private fun key(password:String,salt:ByteArray):SecretKeySpec {
    require(password.length>=8){"Пароль должен содержать не менее 8 символов"}
    val spec=PBEKeySpec(password.toCharArray(),salt,310000,256)
    return try{SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")}finally{spec.clearPassword()}
}
internal fun encryptBackup(data:ByteArray,password:String):ByteArray {
    require(data.size<=MAX_FILE-64)
    val random=SecureRandom();val salt=ByteArray(16).also{random.nextBytes(it)};val iv=ByteArray(12).also{random.nextBytes(it)}
    val cipher=Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),GCMParameterSpec(128,iv));cipher.updateAAD(MAGIC)
    return MAGIC+salt+iv+cipher.doFinal(data)
}
internal fun decryptBackup(data:ByteArray,password:String):ByteArray {
    require(data.size in 52..MAX_FILE && data.copyOfRange(0,8).contentEquals(MAGIC)){"Неверный формат копии"}
    val cipher=Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE,key(password,data.copyOfRange(8,24)),GCMParameterSpec(128,data.copyOfRange(24,36)));cipher.updateAAD(MAGIC)
    return cipher.doFinal(data.copyOfRange(36,data.size))
}
private fun JSONObject.nullString(k:String)=if(isNull(k))null else getString(k)
private fun JSONArray.objects(max:Int):List<JSONObject>{require(length()<=max);return (0 until length()).map{getJSONObject(it)}}
private fun array(items:List<JSONObject>)=JSONArray().also{a->items.forEach{a.put(it)}}
private fun orderJson(o:Order)=JSONObject().put("id",o.id).put("name",o.name).put("store",o.store).put("price",o.price?:JSONObject.NULL)
    .put("paid",o.paid).put("status",o.status).put("deadline",o.deadline?:JSONObject.NULL).put("point",o.point).put("link",o.link)
    .put("note",o.note).put("created",o.created).put("expected",o.expectedRefund).put("received",o.receivedRefund).put("cancelled",o.cancelled).put("returned",o.returned).put("category",o.category).put("pointLat",o.pointLat?:JSONObject.NULL).put("pointLon",o.pointLon?:JSONObject.NULL)
private fun JSONObject.optionalDouble(key:String):Double?=if(!has(key)||isNull(key))null else getDouble(key)
private fun readOrder(j:JSONObject):Order {
    val o=Order(j.getString("id"),j.getString("name"),j.getString("store"),if(j.isNull("price"))null else j.getLong("price"),j.getLong("paid"),j.getString("status"),j.nullString("deadline"),j.getString("point"),j.getString("link"),j.getString("note"),j.getString("created"),j.getLong("expected"),j.getLong("received"),j.getBoolean("cancelled"),j.getBoolean("returned"),j.optString("category",""),j.optionalDouble("pointLat"),j.optionalDouble("pointLon"))
    require(validPoint(o.pointLat,o.pointLon)&&(o.pointLat==null||o.point.isNotBlank()))
    require(o.id.isNotBlank()&&o.id.length<=128&&o.name.isNotBlank()&&o.name.length<=200&&o.store.isNotBlank()&&o.store.length<=200&&o.point.length<=200&&o.category.length<=200&&o.link.length<=8192&&o.note.length<=10000&&o.paid in 0..999999999999L)
    require(o.price==null||o.price in o.paid..999999999999L)
    require(o.expectedRefund in 0..o.paid&&o.receivedRefund in 0..o.expectedRefund)
    require(o.state!=Status.REFUND||o.receivedRefund<o.expectedRefund)
    LocalDate.parse(o.created);o.deadline?.let{LocalDate.parse(it)}
    return o
}
object PrivateFiles{
    suspend fun addPhoto(context:Context,id:String,uri:Uri)=withContext(Dispatchers.IO){
        val raw=context.contentResolver.openInputStream(uri)?.use{it.bounded(20*1024*1024)}?:error("Не удалось открыть изображение")
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(raw,0,raw.size,bounds)
        require(bounds.outWidth>0&&bounds.outHeight>0){"Неподдерживаемое изображение"}
        var sample=1
        while((maxOf(bounds.outWidth,bounds.outHeight).toLong()+sample-1)/sample>1200)sample*=2
        val bitmap=BitmapFactory.decodeByteArray(raw,0,raw.size,BitmapFactory.Options().apply{inSampleSize=sample})?:error("Не удалось открыть изображение")
        val bytes=try{
            var result=ByteArray(0)
            for(quality in listOf(82,65,45,25)){
                val out=ByteArrayOutputStream();check(bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out));result=out.toByteArray()
                if(result.size<=MAX_PHOTO)break
            }
            require(result.size<=MAX_PHOTO){"Изображение слишком большое"};result
        }finally{bitmap.recycle()}
        val db=ZaberiDb.get(context)
        db.withTransaction{val dao=db.orders();require(dao.get(id)!=null);require(dao.photoCount(id)<5){"Не более пяти фото на заказ"};require(dao.photoBytes()+bytes.size<=20L*1024*1024){"Достигнут общий лимит фотографий (20 МБ)"};dao.photo(Attachment(uid(),id,bytes))}
    }
    suspend fun export(context:Context,uri:Uri,password:String)=withContext(Dispatchers.IO){
        val db=ZaberiDb.get(context);val d=db.orders()
        val snapshot=db.withTransaction{
            require(d.photoBytes()<=20L*1024*1024){"Слишком много фотографий для одной резервной копии"}
            JSONObject().put("version",4).put("orders",array(d.all().map(::orderJson)))
                .put("transactions",array(d.allTransactions().map{JSONObject().put("id",it.id).put("order",it.orderId).put("date",it.date).put("amount",it.amount)}))
                .put("history",array(d.allHistory().map{JSONObject().put("id",it.id).put("order",it.orderId).put("date",it.date).put("label",it.label)}))
                .put("catalog",array(d.allCatalog().map{JSONObject().put("id",it.id).put("kind",it.kind).put("parent",it.parent).put("value",it.value).put("pointLat",it.pointLat?:JSONObject.NULL).put("pointLon",it.pointLon?:JSONObject.NULL)}))
                .put("photos",array(d.allPhotos().map{JSONObject().put("id",it.id).put("order",it.orderId).put("jpeg",Base64.encodeToString(it.jpeg,Base64.NO_WRAP))}))
        }
        val bytes=encryptBackup(snapshot.toString().toByteArray(Charsets.UTF_8),password)
        context.contentResolver.openOutputStream(uri,"wt")?.use{it.write(bytes)}?:error("Не удалось записать файл")
    }
    suspend fun restore(context:Context,uri:Uri,password:String)=withContext(Dispatchers.IO){
        val data=context.contentResolver.openInputStream(uri)?.use{it.bounded(MAX_FILE)}?:error("Не удалось открыть файл")
        val json=JSONObject(decryptBackup(data,password).toString(Charsets.UTF_8));require(json.getInt("version") in 2..4)
        val orders=json.getJSONArray("orders").objects(10000).map(::readOrder)
        val ids=orders.map{it.id}.toSet();require(ids.size==orders.size)
        val transactions=json.getJSONArray("transactions").objects(50000).map{Transaction(it.getString("id"),it.getString("order"),it.getString("date"),it.getLong("amount")).also{t->require(t.orderId in ids&&t.amount in -999999999999L..999999999999L&&t.amount!=0L);LocalDate.parse(t.date)}}
        val histories=json.getJSONArray("history").objects(100000).map{History(it.getString("id"),it.getString("order"),it.getString("date"),it.getString("label")).also{h->require(h.orderId in ids);LocalDateTime.parse(h.date)}}
        val photos=json.getJSONArray("photos").objects(50000).map{Attachment(it.getString("id"),it.getString("order"),Base64.decode(it.getString("jpeg"),Base64.NO_WRAP)).also{a->require(a.orderId in ids&&a.jpeg.size in 1..MAX_PHOTO);val b=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(a.jpeg,0,a.jpeg.size,b);require(b.outWidth in 1..1200&&b.outHeight in 1..1200)}}
        require(photos.groupingBy{it.orderId}.eachCount().values.all{it<=5}&&photos.sumOf{it.jpeg.size.toLong()}<=20L*1024*1024)
        val catalog=(json.optJSONArray("catalog")?:JSONArray()).objects(30000).map{j->
            CatalogEntry(j.getString("id"),j.getString("kind"),j.getString("parent"),j.getString("value"),j.optionalDouble("pointLat"),j.optionalDouble("pointLon")).also{c->
                require(validPoint(c.pointLat,c.pointLon)&&(c.kind=="point"||c.pointLat==null))
                require(c.id.isNotBlank()&&c.kind in listOf("store","point","category")&&c.value.isNotBlank()&&c.value.length<=200&&(c.kind!="point"||c.parent.isNotBlank()))
            }
        }
        require(transactions.map{it.id}.toSet().size==transactions.size)
        require(histories.map{it.id}.toSet().size==histories.size)
        require(photos.map{it.id}.toSet().size==photos.size)
        require(catalog.map{it.id}.toSet().size==catalog.size)
        require(catalog.map{Triple(it.kind,it.parent,it.value)}.toSet().size==catalog.size)
        val groups=transactions.groupBy{it.orderId}
        orders.forEach{o->val t=groups[o.id].orEmpty();require(t.filter{it.amount>0}.sumOf{it.amount}==o.paid&&-t.filter{it.amount<0}.sumOf{it.amount}==o.receivedRefund)}
        val db=ZaberiDb.get(context);val d=db.orders()
        db.withTransaction{d.clearOrders();d.clearCatalog();orders.forEach{d.insert(it)};transactions.forEach{d.transaction(it)};histories.forEach{d.history(it)};photos.forEach{d.photo(it)};catalog.forEach{d.remember(it)}
            if(json.optInt("version")==2)orders.forEach{o->d.remember(CatalogEntry(uid(),"store","",o.store));if(o.point.isNotBlank())d.remember(CatalogEntry(uid(),"point",o.store,o.point));if(o.category.isNotBlank())d.remember(CatalogEntry(uid(),"category","",o.category))}
        }
        context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        context.getSharedPreferences("reminders",Context.MODE_PRIVATE).edit().clear().apply()
        Reminders.enqueue(context)
    }
    suspend fun csv(context:Context,uri:Uri)=withContext(Dispatchers.IO){
        val db=ZaberiDb.get(context);val d=db.orders()
        val (orders,tx)=db.withTransaction{d.all().associateBy{it.id} to d.allTransactions()}
        fun cell(s:String):String{val safe=if(s.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r'))"'"+s else s;return "\""+safe.replace("\"","\"\"")+"\""}
        context.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use{w->
            w.write("\uFEFFДата;Магазин;Товар;Сумма (руб.)\r\n")
            tx.sortedBy{it.date}.forEach{t->val o=orders[t.orderId];w.write(listOf(cell(t.date),cell(o?.store.orEmpty()),cell(o?.name.orEmpty()),java.math.BigDecimal.valueOf(t.amount,2).toPlainString().replace('.',',')).joinToString(";")+"\r\n")}
        }?:error("Не удалось записать файл")
    }
}