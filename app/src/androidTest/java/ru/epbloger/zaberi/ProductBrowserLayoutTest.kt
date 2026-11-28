package ru.epbloger.zaberi

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import android.webkit.WebView
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductBrowserLayoutTest{
    @get:Rule val compose=createComposeRule()
    @Test fun browserPrimaryActionIsAboveWebContentAndReadsRenderedPrice()=verifyRenderedPrice(
        SharedProductSeed("","Ozon","https://www.ozon.ru/product/test-123/",null),
        """<h1>Чайник</h1><div data-widget="webPrice"><div>999 ₽ с картой</div><div><span>1 299 ₽</span><span> без Ozon Карты</span><del>2 000 ₽</del></div></div>"""
    )
    @Test fun wildberriesReadsRegularPriceInsteadOfWalletPrice()=verifyRenderedPrice(
        SharedProductSeed("","Wildberries","https://www.wildberries.ru/catalog/123/detail.aspx",null),
        """<h1>Чайник</h1><div class="priceBlock"><span class="priceBlockWalletPrice_x">999 ₽</span><span class="priceBlockFinalPrice_x">1 299 ₽</span><del class="priceBlockOldPrice_x">2 000 ₽</del></div>"""
    )
    @Test fun ozonMobilePriceOutsideMainIgnoresCrossedOutOriginal()=verifyRenderedPrice(
        SharedProductSeed("","Ozon","https://www.ozon.ru/product/2615816959/",null),
        """<main><h1>Чайник</h1></main><aside><div>1 199 ₽</div><div>С банками</div><div><div><span>1&#8201;299&#8201;₽</span><span style="text-decoration:line-through"><span>5 000 ₽</span></span></div><div><span>С другими банками</span></div></div></aside>"""
    )
    private fun verifyRenderedPrice(seed:SharedProductSeed,body:String){
        val result=AtomicReference<ProductLookup?>()
        val web=AtomicReference<WebView>()
        val captureError=AtomicReference<String?>()
        val fixture="""<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>$body</body></html>"""
        compose.setContent{MaterialTheme{
            var open by remember{mutableStateOf(true)}
            if(open)ProductBrowserDialog(seed,false,true,{open=false},loadPage={view,url->
                web.set(view)
                view.loadUrl(url.toString())
            },localResponse={request->
                // Keep the actual HTTPS navigation/history and WebView document URL,
                // unlike loadDataWithBaseURL(..., null), whose history URL is about:blank.
                if(request.isForMainFrame&&request.url.toString()==seed.link)
                    WebResourceResponse("text/html","UTF-8",fixture.byteInputStream(Charsets.UTF_8))
                else WebResourceResponse("text/plain","UTF-8",404,"Not Found",emptyMap(),ByteArrayInputStream(byteArrayOf()))
            },captureDiagnostic={captureError.set(it)}){result.set(it);open=false}
        }}
        compose.waitUntil(20000){compose.onAllNodesWithTag("capture_product").fetchSemanticsNodes().isNotEmpty()}
        val button=compose.onNodeWithTag("capture_product").assertIsDisplayed()
        val browser=compose.onNodeWithTag("product_browser").fetchSemanticsNode()
        val bounds=button.fetchSemanticsNode().boundsInRoot
        assertTrue("Action must be above browser content",bounds.bottom<=browser.boundsInRoot.top+2)
        assertTrue("Action must have a full touch target",bounds.height>=with(compose.density){47.dp.toPx()})
        compose.onNodeWithTag("close_product_browser").assertIsDisplayed()
        var tapX=0f
        var tapY=0f
        compose.runOnIdle{
            val view=requireNotNull(web.get())
            val root=view.rootView
            val webLocation=IntArray(2).also{view.getLocationOnScreen(it)}
            val rootLocation=IntArray(2).also{root.getLocationOnScreen(it)}
            val insets=ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val originX=webLocation[0]-browser.boundsInRoot.left
            val originY=webLocation[1]-browser.boundsInRoot.top
            tapX=originX+bounds.center.x
            tapY=originY+bounds.center.y
            assertTrue("Action must clear status bar",originY+bounds.top>=rootLocation[1]+(insets?.top?:0)-2)
            assertTrue("Action must clear navigation bar",originY+bounds.bottom<=rootLocation[1]+root.height-(insets?.bottom?:0)+2)
            assertTrue("Action must clear left system inset",originX+bounds.left>=rootLocation[0]+(insets?.left?:0)-2)
            assertTrue("Action must clear right system inset",originX+bounds.right<=rootLocation[0]+root.width-(insets?.right?:0)+2)
        }
        compose.waitUntil(20000){runCatching{button.assertIsEnabled();true}.getOrDefault(false)}
        compose.runOnIdle{assertEquals("Fixture must navigate to the real allowed HTTPS document URL",seed.link,web.get().url)}
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val down=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,tapX,tapY,0)
            try{instrumentation.sendPointerSync(event)}finally{event.recycle()}
        }
        try{compose.waitUntil(20000){result.get()!=null||captureError.get()!=null}}
        catch(e:ComposeTimeoutException){
            val diagnostic=compose.onAllNodesWithTag("product_browser_message").fetchSemanticsNodes()
                .joinToString{it.config.toString()}
            throw AssertionError("Rendered capture did not return: "+diagnostic,e)
        }
        assertNull("Capture failed at "+captureError.get(),captureError.get())
        assertEquals(129900L,result.get()?.price)
        assertEquals("Чайник",result.get()?.title)
    }
}
