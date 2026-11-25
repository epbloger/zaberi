package ru.epbloger.zaberi

import android.Manifest
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StoreMediaTest {
    @get:Rule(order=0) val permission=GrantPermissionRule.grant(*(if(Build.VERSION.SDK_INT>=33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray<String>()))
    @get:Rule(order=1) val compose=createAndroidComposeRule<MainActivity>()

    private fun capture(name:String) {
        compose.waitForIdle()
        Thread.sleep(900)
        val inst=InstrumentationRegistry.getInstrumentation()
        val folder=File(requireNotNull(inst.targetContext.getExternalFilesDir(null)),"store-media")
        check(folder.exists()||folder.mkdirs())
        val bitmap=requireNotNull(inst.uiAutomation.takeScreenshot())
        File(folder,name+".png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        bitmap.recycle()
    }

    @Test fun captureStoreScreens() {
        val ctx=compose.activity.applicationContext
        val today=LocalDate.now()
        val db=ZaberiDb.get(ctx)
        val examples=listOf(
            Order("media-1","Беспроводные наушники","Магазин техники",349000,349000,Status.READY.name,today.plusDays(2).toString(),"Пункт выдачи № 1","","Забрать после работы",today.minusDays(2).toString(),category="Электроника"),
            Order("media-2","Книга и блокнот","Книжный магазин",129000,129000,Status.TRANSIT.name,today.plusDays(5).toString(),"Пункт выдачи № 2","","",today.minusDays(1).toString(),category="Книги"),
            Order("media-3","Настольная лампа","Товары для дома",189000,189000,Status.REFUND.name,today.plusDays(4).toString(),"","","",today.minusDays(4).toString(),expectedRefund=189000,receivedRefund=50000,returned=true,category="Дом"),
            Order("media-4","Термокружка","Товары для дома",99000,99000,Status.DONE.name,null,"","","",today.minusDays(5).toString(),category="Дом")
        )
        runBlocking {
            db.withTransaction {
                val dao=db.orders()
                dao.clearOrders()
                dao.clearCatalog()
                examples.forEach { o ->
                    dao.insert(o)
                    dao.transaction(Transaction(o.id+"-paid",o.id,today.toString(),o.paid))
                }
                dao.transaction(Transaction("media-refund","media-3",today.toString(),-50000))
            }
        }
        compose.waitForIdle()
        Thread.sleep(1200)
        compose.onNodeWithTag("orders_list").assertIsDisplayed()
        capture("01-orders")
        compose.onNodeWithTag("orders_list").performScrollToNode(hasText("Беспроводные наушники"))
        compose.onNodeWithText("Беспроводные наушники").performClick()
        capture("02-order-details")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitUntil(timeoutMillis=10000) { compose.onAllNodesWithText("Календарь",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Календарь",useUnmergedTree=true).onFirst().performTouchInput { click() }
        capture("03-calendar")
        compose.waitUntil(timeoutMillis=10000) { compose.onAllNodesWithText("Статистика",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Статистика",useUnmergedTree=true).onFirst().performTouchInput { click() }
        compose.onNodeWithTag("stats_expenses").assertIsDisplayed()
        capture("04-statistics")
        compose.waitUntil(timeoutMillis=10000) { compose.onAllNodesWithText("Настройки",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Настройки",useUnmergedTree=true).onFirst().performTouchInput { click() }
        capture("05-settings")
    }
}
