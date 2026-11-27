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

    @Test fun shareTargetSupportsHtmlAndMultipleImagesAndClipboardPayload(){
        val context=compose.activity
        val url="https://www.wildberries.ru/catalog/330535596/detail.aspx"
        val html=Intent(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_HTML_TEXT,"<a href='$url'>Товар WB</a>")
        val multi=Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg")
        multi.clipData=android.content.ClipData.newPlainText("product",url)
        for(intent in listOf(html,multi)){
            val resolved=context.packageManager.queryIntentActivities(intent.setPackage(context.packageName),0)
            assertTrue(resolved.any{it.activityInfo.name==MainActivity::class.java.name})
            val seed=sharedProductSeed(requireNotNull(sharedProductRequest(intent)).text)
            assertEquals("Wildberries",seed.store)
            assertEquals(url,seed.link)
        }
    }
    @Test fun currentStorefrontFormatsKeepExactPriceAndPrimaryImage(){
        val json="""{"products":[{"id":330535596,"name":"Чайник","totalQuantity":5,"sizes":[{"price":{"product":129950,"basic":200000},"stocks":[{"qty":5}]}]}]}"""
        assertEquals(129950L,parseWildberriesDetails(json,330535596L).price)
        assertNull(parseWildberriesDetails(json,999L).price)
        val routes="""{"origin":{"mediabasket_route_map":[{"method":"range","hosts":[{"vol_range_from":3260,"vol_range_to":3400,"host":"basket-20.wbbasket.ru"}]}]}}"""
        assertEquals("basket-20.wbbasket.ru",wildberriesBasketHost(routes,330535596L))
        assertNull(wildberriesBasketHost(routes.replace("basket-20.wbbasket.ru","evil.example"),330535596L))
        val ozon="""<div id="state-webPrice-1" data-state='{"price":"1 299 ₽","cardPrice":"999 ₽","originalPrice":"2 000 ₽"}'></div><div data-widget="webProductHeading"><h1>Чайник</h1></div><div data-widget="webGallery"><img src="https://ir.ozone.ru/s3/first.jpg"><img src="https://ir.ozone.ru/s3/second.jpg"></div>"""
        val parsed=parseProductMetadata(ozon,"https://www.ozon.ru/product/item-123/".toHttpUrl(),"Ozon")
        assertEquals(129900L,parsed.price)
        assertEquals("https://ir.ozone.ru/s3/first.jpg",parsed.image)
    }

    private fun share(intent:Intent){
        compose.activityRule.scenario.onActivity{activity->
            // ActivityScenario matches lifecycle events against its original launch Intent.
            // MainActivity.onNewIntent intentionally calls setIntent; restore only the
            // scenario's launch identity after exercising the real handler on the UI thread.
            val launchIntent=activity.intent
            try{
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .callActivityOnNewIntent(activity,intent.setClass(activity,MainActivity::class.java))
                assertSame(intent,activity.intent)
            }finally{
                activity.intent=launchIntent
            }
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
        compose.onNodeWithTag("order_name").performScrollTo().performTextInput("Учебный заказ")
        compose.onNodeWithText("Ozon",substring=false).performClick()
        compose.onNodeWithTag("save_order").assertIsEnabled().performClick()
        compose.onNodeWithText("Следите за заказами").assertExists()
        assertEquals(before,runBlocking{dao.all().map{it.id}.toSet()})
        compose.onNodeWithTag("tutorial_back").performScrollTo().performClick()
        compose.onNodeWithTag("tutorial_practice").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithText("Следите за заказами").assertExists()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        assertEquals(before,runBlocking{dao.all().map{it.id}.toSet()})
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("new_order").assertExists()
        compose.onNodeWithTag("tutorial_panel").assertDoesNotExist()
    }
}
