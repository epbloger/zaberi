package ru.epbloger.zaberi

import android.Manifest
import android.os.Build
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.provider.Settings
import androidx.core.view.WindowCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {
    @get:Rule(order=0) val permission=GrantPermissionRule.grant(*(if(Build.VERSION.SDK_INT>=33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray<String>()))
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()

    private fun enterTextWithKeyboard(tag: String, value: String) {
        val field = compose.onNodeWithTag(tag)
        if (tag != "map_search") field.performScrollTo()
        field.performTouchInput { click() }
        field.assertIsFocused()
        field.performTextInput(value)
        field.assertTextContains(value)
        // Semantic text input by itself is not proof that the OS has shown the IME.
        // Ask the window/controller explicitly, with a focused editor and real touch input.
        field.performTouchInput { click() }
        compose.runOnUiThread {
            val activity = compose.activity
            val focused = activity.currentFocus
            if (focused != null) {
                val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(focused, InputMethodManager.SHOW_IMPLICIT)
                WindowCompat.getInsetsController(activity.window, focused).show(WindowInsetsCompat.Type.ime())
            }
        }
        awaitKeyboard(tag)
    }

    private fun awaitKeyboard(tag: String) {
        var diagnostic = ""
        try {
            compose.waitUntil(timeoutMillis = 15000) {
                var visible = false
                compose.runOnUiThread {
                    val activity = compose.activity
                    val decor = activity.window.decorView
                    val root = activity.currentFocus ?: decor
                    val insets = ViewCompat.getRootWindowInsets(root) ?: ViewCompat.getRootWindowInsets(decor)
                    val ime = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                    val bars = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                    visible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true && ime > bars
                    diagnostic = "tag=$tag ime=$ime nav=$bars visible=$visible focus=${activity.currentFocus?.javaClass?.name} windowFocus=${decor.hasWindowFocus()} defaultIme=${Settings.Secure.getString(activity.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)}"
                }
                visible
            }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError("Real software keyboard did not open: $diagnostic", e)
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
        enterTextWithKeyboard("order_name", name)
        assertInsideWindow("save_order")
        enterTextWithKeyboard("order_store", "Тестовый магазин")
        compose.onNodeWithTag("save_order").assertIsEnabled()
        assertInsideWindow("save_order")
        compose.onNodeWithTag("save_order").performTouchInput{click()}
        compose.waitUntil(timeoutMillis=10000){compose.onAllNodesWithTag("new_order").fetchSemanticsNodes().isNotEmpty()}
        val persisted=runBlocking{ZaberiDb.get(compose.activity.applicationContext).orders().all().any{it.name==name&&it.store=="Тестовый магазин"}}
        assertTrue("Order must actually be committed to Room before leaving the form",persisted)
        compose.onNodeWithTag("orders_list").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).assertIsDisplayed().performTouchInput{click()}
        val payment=compose.onNodeWithTag("mark_payment").performScrollTo().assertIsDisplayed()
        assertInsideWindow("mark_payment")
        val paymentY=payment.fetchSemanticsNode().positionInRoot.y
        val primaryBefore=compose.onNodeWithTag("primary_action").fetchSemanticsNode().positionInRoot.y
        assertTrue("Payment must appear before delivery actions",paymentY<primaryBefore)
        payment.performClick()
        compose.onNodeWithText("Оплата",substring=false).assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        val primary=compose.onNodeWithTag("primary_action").performScrollTo().assertIsDisplayed().fetchSemanticsNode()
        val delete=compose.onNodeWithTag("delete_order").performScrollTo()
        assertInsideWindow("delete_order")
        assertTrue("Delete button must match primary action width", kotlin.math.abs(primary.size.width-delete.fetchSemanticsNode().size.width)<=2)
        delete.performTouchInput{click()}
        compose.onNodeWithText("Удалить заказ?").assertIsDisplayed()
        compose.onNodeWithText("Отмена").performClick()
        assertTrue("Cancelling deletion must preserve order",runBlocking{ZaberiDb.get(compose.activity.applicationContext).orders().all().any{it.name==name}})
        delete.performScrollTo().performTouchInput{click()}
        compose.onNodeWithTag("confirm_delete_order").performClick()
        compose.waitUntil(timeoutMillis=10000){compose.onAllNodesWithTag("new_order").fetchSemanticsNodes().isNotEmpty()}
        assertTrue("Confirmed deletion must reach Room",runBlocking{ZaberiDb.get(compose.activity.applicationContext).orders().all().none{it.name==name}})
    }
    @Test fun mapActionRemainsVisibleAndReturnsToDraft(){
        compose.onNodeWithTag("new_order").performTouchInput{click()}
        enterTextWithKeyboard("order_name", "Черновик")
        compose.onNodeWithTag("open_pickup_map").performScrollTo().performTouchInput{click()}
        compose.onNodeWithTag("confirm_online_map").performClick()
        assertInsideWindow("save_pickup")
        enterTextWithKeyboard("map_search", "Москва")
        assertInsideWindow("save_pickup")
        compose.onNodeWithContentDescription("Закрыть").performTouchInput{click()}
        compose.onNodeWithTag("order_name").performScrollTo().assertTextContains("Черновик")
        assertInsideWindow("save_order")
    }
}
