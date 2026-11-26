package ru.epbloger.zaberi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardSpacingTest {
    @get:Rule val compose=createComposeRule()

    private fun node(tag:String)=compose.onNodeWithTag(tag,useUnmergedTree=true).fetchSemanticsNode()
    private fun expectedGap()=with(compose.density){12.dp.roundToPx()}.toFloat()
    private fun assertStack(vararg tags:String){
        compose.waitForIdle()
        val nodes=tags.map(::node)
        for(i in 0 until nodes.lastIndex){
            val before=nodes[i]
            val after=nodes[i+1]
            assertEquals("Gap ${tags[i]} -> ${tags[i+1]}",expectedGap(),after.positionInRoot.y-before.positionInRoot.y-before.size.height,1f)
            assertEquals("Card width",before.size.width,after.size.width)
            assertEquals("Card left edge",before.positionInRoot.x,after.positionInRoot.x,1f)
        }
    }

    @Test fun narrowStatisticsCardsHaveIdenticalVerticalGaps(){
        compose.setContent{
            MaterialTheme{
                Column(Modifier.width(260.dp).verticalScroll(rememberScrollState())){
                    StatisticsSummary(123456,456,789)
                }
            }
        }
        assertStack("stats_expenses","stats_paid","stats_refunded","stats_pending")
    }
    @Test fun narrowOverviewCardsHaveIdenticalVerticalGaps(){
        compose.setContent{
            MaterialTheme{
                Column(Modifier.width(260.dp).verticalScroll(rememberScrollState())){
                    OrderOverview(emptyList(),0,{}, {})
                }
            }
        }
        assertStack("overview_summary","overview_ready","overview_refund")
    }
    @Test fun largeFontKeepsTheSameGaps(){
        compose.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)){
                MaterialTheme{
                    Column(Modifier.requiredWidth(360.dp).verticalScroll(rememberScrollState())){
                        StatisticsSummary(123456,456,789)
                    }
                }
            }
        }
        assertStack("stats_expenses","stats_paid","stats_refunded","stats_pending")
    }
    @Test fun wideStatisticsCardsAlignAndHaveEqualHeight(){
        compose.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1f)){
                MaterialTheme{
                    Box(Modifier.requiredWidth(360.dp)){
                        StatisticsSummary(123456,456,789)
                    }
                }
            }
        }
        compose.waitForIdle()
        val first=node("stats_paid")
        val second=node("stats_refunded")
        val top=node("stats_expenses")
        val bottom=node("stats_pending")
        assertEquals("Horizontal gap",expectedGap(),second.positionInRoot.x-first.positionInRoot.x-first.size.width,1f)
        assertEquals("Equal row height",first.size.height,second.size.height)
        assertEquals("Equal top",first.positionInRoot.y,second.positionInRoot.y,1f)
        assertEquals("Gap above",expectedGap(),first.positionInRoot.y-top.positionInRoot.y-top.size.height,1f)
        assertEquals("Gap below",expectedGap(),bottom.positionInRoot.y-first.positionInRoot.y-first.size.height,1f)
    }
}
