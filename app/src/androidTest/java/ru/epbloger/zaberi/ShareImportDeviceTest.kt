package ru.epbloger.zaberi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareImportDeviceTest {
    @get:Rule(order=0) val permission=GrantPermissionRule.grant(*(if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.POST_NOTIFICATIONS)else emptyArray<String>()))
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()

    @Before fun prepare(){
        compose.activityRule.scenario.onActivity{
            it.getSharedPreferences("preferences",Context.MODE_PRIVATE).edit()
                .putBoolean("online_product_import_v1",false).commit()
        }
        compose.activityRule.scenario.recreate()
        if(compose.onAllNodesWithTag("tutorial_panel").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithContentDescription("Закрыть").performClick()
    }

    @Test fun structuredProductUsesOnlyExplicitPriceAndPrimaryImage(){
        val page="https://www.ozon.ru/product/item-123/".toHttpUrl()
        val html="""<html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Product","name":"Чайник",
            "image":["https://ir.ozone.ru/s3/first.jpg","https://ir.ozone.ru/s3/second.jpg"],
            "offers":{"@type":"Offer","price":"1299.50","priceCurrency":"RUB"}}
            </script></head></html>"""
        val m=parseProductMetadata(html,page,"Ozon")
        assertEquals("Чайник",m.title)
        assertEquals(129950L,m.price)
        assertEquals("https://ir.ozone.ru/s3/first.jpg",m.image)
        assertNull(parseProductMetadata(html.replace("\"RUB\"","\"USD\""),page,"Ozon").price)
        assertNull(parseProductMetadata(html.replace("\"Offer\"","\"AggregateOffer\""),page,"Ozon").price)
        assertNull(parseProductMetadata(html.replace("ir.ozone.ru","untrusted.example"),page,"Ozon").image)
        val blocked="""<html><head><meta property="og:title" content="Access denied"><meta property="og:image" content="https://ir.ozone.ru/error.jpg"></head></html>"""
        assertEquals(ProductMetadata(null,null,null),parseProductMetadata(blocked,page,"Ozon"))
    }

    private fun share(intent:Intent){
        compose.activityRule.scenario.onActivity{activity->
            activity.startActivity(intent.setClass(activity,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        compose.waitUntil(15000){compose.onAllNodesWithTag("shared_import_panel").fetchSemanticsNodes().isNotEmpty()}
    }

    @Test fun sharePriceAndPhotoPersistAtomicallyWithoutMarkingPayment(){
        val context=compose.activity.applicationContext
        val dao=ZaberiDb.get(context).orders()
        val name="Импорт фото "+System.nanoTime()
        val file=CameraPhoto.create(context)
        try{
            val image=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888)
            try{file.outputStream().use{assertTrue(image.compress(Bitmap.CompressFormat.JPEG,90,it))}}finally{image.recycle()}
            val uri=CameraPhoto.uri(context,file)
            share(Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_TEXT,name+"\n1 299,50 ₽\nhttps://www.ozon.ru/product/photo-"+System.nanoTime()+"/")
                .putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            compose.waitUntil(15000){compose.onAllNodesWithTag("import_photo").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("order_name").performScrollTo().assertTextContains(name)
            compose.onNodeWithTag("order_price").performScrollTo().assertTextContains("1299.50")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("import_photo").performScrollTo().assertExists()
            compose.onNodeWithTag("order_name").performScrollTo().assertTextContains(name)
            compose.onNodeWithTag("save_order").assertIsEnabled().performClick()
            compose.waitUntil(15000){runBlocking{dao.all().any{it.name==name}}}
            val saved=runBlocking{dao.all().single{it.name==name}}
            assertEquals(129950L,saved.price)
            assertEquals(0L,saved.paid)
            assertTrue(runBlocking{dao.allTransactions().none{it.orderId==saved.id}})
            assertEquals(1,runBlocking{dao.photoCount(saved.id)})
            val photo=runBlocking{dao.allPhotos().single{it.orderId==saved.id}}
            assertTrue(photo.jpeg.size in 1..240*1024)
        }finally{
            runBlocking{dao.all().filter{it.name==name}.forEach{dao.delete(it)}}
            file.delete()
        }
    }

    @Test fun lateLookupCannotOverwriteEditedNameOrPrice(){
        val response=CompletableDeferred<ProductLookup>()
        compose.activityRule.scenario.onActivity{activity->
            ViewModelProvider(activity)[Model::class.java].lookupProductDetails={_,_->response.await()}
        }
        try{
            share(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT,"https://www.ozon.ru/product/test-"+System.nanoTime()+"/"))
            compose.onNodeWithTag("import_lookup").performScrollTo().performClick()
            compose.onNodeWithTag("order_name").performScrollTo().performTextInput("Моё название")
            compose.onNodeWithTag("order_price").performScrollTo().performTextReplacement("850")
            response.complete(ProductLookup("Название с сайта",99900L,null))
            compose.waitUntil(15000){compose.onAllNodesWithTag("import_loading").fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithTag("order_name").assertTextContains("Моё название")
            compose.onNodeWithTag("order_price").assertTextContains("850")
        }finally{
            response.cancel()
            compose.activityRule.scenario.onActivity{it.getSharedPreferences("preferences",Context.MODE_PRIVATE).edit().putBoolean("online_product_import_v1",false).commit()}
        }
    }

    @Test fun cancelledImportCannotOverwriteOrPersistPhoto(){
        val response=CompletableDeferred<ProductLookup>()
        compose.activityRule.scenario.onActivity{activity->
            ViewModelProvider(activity)[Model::class.java].lookupProductDetails={_,_->response.await()}
        }
        try{
            share(Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT,"Чайник\n500 ₽\nhttps://www.ozon.ru/product/cancel-"+System.nanoTime()+"/"))
            compose.onNodeWithTag("import_lookup").performScrollTo().performClick()
            compose.onNodeWithTag("save_order").assertIsNotEnabled()
            compose.onNodeWithTag("cancel_import_lookup").performScrollTo().performClick()
            compose.onNodeWithTag("save_order").assertIsEnabled()
            response.complete(ProductLookup("Чужое название",90000L,byteArrayOf(1,2,3)))
            compose.waitForIdle()
            compose.onNodeWithTag("order_name").assertTextContains("Чайник")
            compose.onNodeWithTag("order_price").assertTextContains("500.00")
            compose.onNodeWithTag("import_photo").assertDoesNotExist()
            compose.onNodeWithContentDescription("Закрыть").performClick()
        }finally{
            response.cancel()
            compose.activityRule.scenario.onActivity{it.getSharedPreferences("preferences",Context.MODE_PRIVATE).edit().putBoolean("online_product_import_v1",false).commit()}
        }
    }

    @Test fun tutorialCanBeReopenedAndManualPracticeDoesNotCreateAnOrder(){
        val dao=ZaberiDb.get(compose.activity.applicationContext).orders()
        val before=runBlocking{dao.all().map{it.id}.toSet()}
        compose.onNodeWithText("Настройки",substring=false).performClick()
        compose.onNodeWithText("Обучение",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Добавляйте покупки из магазина").assertExists()
        compose.onNodeWithText("Нажмите «Поделиться»").performScrollTo().assertExists()
        compose.onNodeWithTag("tutorial_next").performClick()
        compose.onNodeWithTag("tutorial_practice").performScrollTo().performClick()
        compose.onNodeWithTag("order_name").assertExists()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithText("Следите за заказами").assertExists()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        assertEquals(before,runBlocking{dao.all().map{it.id}.toSet()})
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("new_order").assertExists()
        compose.onNodeWithTag("tutorial_panel").assertDoesNotExist()
    }
}
