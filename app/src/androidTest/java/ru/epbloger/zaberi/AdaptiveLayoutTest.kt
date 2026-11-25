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
    @get:Rule(order=0) val permission=GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()

    private fun assertInsideWindow(tag:String){
        val node=compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
        val bounds=node.boundsInWindow
        val unclipped=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
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
        assertTrue("$tag is outside safe left edge: $bounds",bounds.left>=left-2)
        assertTrue("$tag is outside safe top edge: $bounds",bounds.top>=top-2)
        assertTrue("$tag is outside safe right edge: $bounds",bounds.right<=right+2)
        assertTrue("$tag is outside safe bottom edge: $bounds, safe bottom=$bottom",bounds.bottom<=bottom+2)
        assertTrue("$tag clipped height: $bounds",bounds.height>=unclipped.height.value*density-3)
        assertTrue("$tag touch target is too small: $bounds",bounds.height>=47*density)
    }
    @Test fun orderActionRemainsVisibleWithKeyboard(){
        compose.onNodeWithTag("new_order").performClick()
        assertInsideWindow("save_order")
        compose.onNodeWithTag("order_name").performClick().performTextInput("Проверка интерфейса")
        compose.waitForIdle()
        assertInsideWindow("save_order")
        compose.onNodeWithText("Ozon").performScrollTo().performClick()
        compose.onNodeWithTag("save_order").assertIsEnabled()
        assertInsideWindow("save_order")
        compose.onNodeWithTag("save_order").performClick()
        compose.onNodeWithTag("new_order").assertIsDisplayed()
    }
    @Test fun mapActionRemainsVisibleAndReturnsToDraft(){
        compose.onNodeWithTag("new_order").performClick()
        compose.onNodeWithTag("order_name").performClick().performTextInput("Черновик")
        compose.onNodeWithTag("open_pickup_map").performScrollTo().performClick()
        compose.onNodeWithTag("save_pickup").assertExists()
        assertInsideWindow("save_pickup")
        compose.onNodeWithTag("map_search").performClick().performTextInput("Москва")
        compose.waitForIdle()
        assertInsideWindow("save_pickup")
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithTag("order_name").assertTextContains("Черновик")
        assertInsideWindow("save_order")
    }
}
