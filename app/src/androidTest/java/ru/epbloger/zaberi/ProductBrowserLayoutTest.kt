package ru.epbloger.zaberi

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import android.webkit.WebView
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
    @Test fun browserPrimaryActionIsAboveWebContentAndReadsRenderedPrice(){
        val result=AtomicReference<ProductLookup?>()
        val web=AtomicReference<WebView>()
        val seed=SharedProductSeed("","Ozon","https://www.ozon.ru/product/test-123/",null)
        val fixture="""<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>
          <h1>Чайник</h1><div data-widget="webPrice"><div>999 ₽ с картой</div><div><span>1 299 ₽</span><span> без Ozon Карты</span><del>2 000 ₽</del></div></div>
          </body></html>"""
        compose.setContent{MaterialTheme{
            var open by remember{mutableStateOf(true)}
            if(open)ProductBrowserDialog(seed,false,true,{open=false},loadPage={view,url->
                web.set(view)
                view.loadDataWithBaseURL(url.toString(),fixture,"text/html","UTF-8",null)
            }){result.set(it);open=false}
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
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val down=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            val event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,tapX,tapY,0)
            try{instrumentation.sendPointerSync(event)}finally{event.recycle()}
        }
        try{compose.waitUntil(20000){result.get()!=null}}
        catch(e:ComposeTimeoutException){
            val diagnostic=compose.onAllNodesWithTag("product_browser_message").fetchSemanticsNodes()
                .joinToString{it.config.toString()}
            throw AssertionError("Rendered capture did not return: "+diagnostic,e)
        }
        assertEquals(129900L,result.get()?.price)
        assertEquals("Чайник",result.get()?.title)
    }
}
