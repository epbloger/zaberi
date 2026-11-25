package ru.epbloger.zaberi

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {
    @get:Rule(order=0) val permission=GrantPermissionRule.grant(*(if(Build.VERSION.SDK_INT>=33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray<String>()))
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()

    private fun awaitKeyboard(){
        compose.waitUntil(timeoutMillis=10000){
            var visible=false
            compose.runOnUiThread{
                visible=ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime())==true
            }
            visible
        }
        compose.waitForIdle()
    }
    private fun assertInsideWindow(tag:String){
        val node=compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        val bounds=node.boundsInWindow
        val pos=node.positionInWindow
        val fullWidth=node.size.width
        val fullHeight=node.size.height
        var right=0
        var bottom=0
        var left=0
        var top=0
        var density=1f
        compose.runOnIdle{
            val decor=compose.activity.window.decorView
            val insets=ViewCompat.getRootWindowInsets(decor)?.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            left=insets?.left?:0
            top=insets?.top?:0
            right=decor.width-(insets?.right?:0)
            bottom=decor.height-(insets?.bottom?:0)
            density=decor.resources.displayMetrics.density
        }
        assertTrue("$tag outside safe left edge",pos.x>=left-2)
        assertTrue("$tag outside safe top edge",pos.y>=top-2)
        assertTrue("$tag outside safe right edge",pos.x+fullWidth<=right+2)
        assertTrue("$tag outside safe bottom edge: ${pos.y+fullHeight}, bottom=$bottom",pos.y+fullHeight<=bottom+2)
        assertTrue("$tag clipped width",bounds.width>=fullWidth-3)
        assertTrue("$tag clipped height",bounds.height>=fullHeight-3)
        assertTrue("$tag touch target too small",fullHeight>=47*density)
    }
    @Test fun orderActionRemainsVisibleWithKeyboard(){
        assertInsideWindow("new_order")
        compose.onNodeWithTag("new_order").performTouchInput{click()}
        assertInsideWindow("save_order")
        val name="Проверка интерфейса "+System.nanoTime()
        compose.onNodeWithTag("order_name").performScrollTo().performClick().performTextInput(name)
        awaitKeyboard()
        assertInsideWindow("save_order")
        compose.onNodeWithTag("order_store").performScrollTo().performClick().performTextInput("Тестовый магазин")
        awaitKeyboard()
        compose.onNodeWithTag("save_order").assertIsEnabled()
        assertInsideWindow("save_order")
        compose.onNodeWithTag("save_order").performTouchInput{click()}
        compose.onNodeWithTag("new_order").assertIsDisplayed()
        compose.waitUntil(timeoutMillis=5000){compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun mapActionRemainsVisibleAndReturnsToDraft(){
        compose.onNodeWithTag("new_order").performTouchInput{click()}
        compose.onNodeWithTag("order_name").performScrollTo().performClick().performTextInput("Черновик")
        awaitKeyboard()
        compose.onNodeWithTag("open_pickup_map").performScrollTo().performTouchInput{click()}
        assertInsideWindow("save_pickup")
        compose.onNodeWithTag("map_search").performClick().performTextInput("Москва")
        awaitKeyboard()
        assertInsideWindow("save_pickup")
        compose.onNodeWithContentDescription("Закрыть").performTouchInput{click()}
        compose.onNodeWithTag("order_name").performScrollTo().assertTextContains("Черновик")
        assertInsideWindow("save_order")
    }
}
