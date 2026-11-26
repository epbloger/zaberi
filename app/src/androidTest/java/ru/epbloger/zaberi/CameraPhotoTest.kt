package ru.epbloger.zaberi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPhotoTest {
    @Test fun providerAndPhotoImport()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=ZaberiDb.get(context)
        val dao=db.orders()
        val id=uid()
        val order=Order(id,"Снимок","Тестовый магазин",null,0,Status.TRANSIT.name,null,"","","","2026-11-25")
        val file=CameraPhoto.create(context)
        try{
            dao.insert(order)
            assertEquals(file,CameraPhoto.restore(context,file.name))
            assertNull(CameraPhoto.restore(context,"../outside.jpg"))
            val uri=CameraPhoto.uri(context,file)
            assertEquals("content",uri.scheme)
            val bitmap=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888)
            try{
                context.contentResolver.openOutputStream(uri)!!.use{assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it))}
            }finally{bitmap.recycle()}
            ExifInterface(file.absolutePath).apply{
                setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()
            }
            PrivateFiles.addPhoto(context,id,uri)
            val stored=dao.allPhotos().single{it.orderId==id}
            assertTrue(stored.jpeg.size<=240*1024)
            val photo=BitmapFactory.decodeByteArray(stored.jpeg,0,stored.jpeg.size)
            try{assertEquals(40,photo.width);assertEquals(80,photo.height)}finally{photo.recycle()}
        }finally{
            file.delete()
            dao.get(id)?.let{dao.delete(it)}
        }
        assertNull(CameraPhoto.restore(context,file.name))
    }
}
