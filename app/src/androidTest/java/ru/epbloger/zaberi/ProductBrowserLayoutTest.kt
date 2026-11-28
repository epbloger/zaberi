package ru.epbloger.zaberi

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
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
        var result:ProductLookup?=null
        val seed=SharedProductSeed("","Ozon","https://www.ozon.ru/product/test-123/",null)
        val fixture="""<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>
          <h1>Чайник</h1><div data-widget="webPrice"><div>999 ₽ с картой</div><div><span>1 299 ₽</span><span> без Ozon Карты</span><del>2 000 ₽</del></div></div>
          </body></html>"""
        compose.setContent{MaterialTheme{
            var open by remember{mutableStateOf(true)}
            if(open)ProductBrowserDialog(seed,false,true,{open=false},loadPage={view,url->
                view.loadDataWithBaseURL(url.toString(),fixture,"text/html","UTF-8",null)
            }){result=it;open=false}
        }}
        compose.waitUntil(20000){compose.onAllNodesWithTag("capture_product").fetchSemanticsNodes().isNotEmpty()}
        val button=compose.onNodeWithTag("capture_product").assertIsDisplayed()
        val browser=compose.onNodeWithTag("product_browser").fetchSemanticsNode()
        val bounds=button.fetchSemanticsNode().boundsInRoot
        assertTrue("Action must be above browser content",bounds.bottom<=browser.boundsInRoot.top+2)
        assertTrue("Action must have a full touch target",bounds.height>=with(compose.density){47.dp.toPx()})
        compose.onNodeWithTag("close_product_browser").assertIsDisplayed()
        compose.waitUntil(20000){runCatching{button.assertIsEnabled();true}.getOrDefault(false)}
        button.performTouchInput{click()}
        compose.waitUntil(20000){result!=null}
        assertEquals(129900L,result?.price)
        assertEquals("Чайник",result?.title)
    }
}
