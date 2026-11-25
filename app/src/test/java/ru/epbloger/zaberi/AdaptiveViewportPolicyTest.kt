package ru.epbloger.zaberi

import org.junit.Assert.*
import org.junit.Test

class AdaptiveViewportPolicyTest {
    @Test fun adaptsToSafeViewportAndFontScale(){
        for(width in listOf(200f,240f,280f,320f,360f,393f,600f,800f,1280f)){
            for(height in listOf(48f,60f,96f,120f,180f,239f,240f,320f,359f,360f,480f,800f)){
                for(scale in listOf(.85f,1f,1.3f,1.5f,2f)){
                    val policy=adaptiveViewport(width,height,scale)
                    assertTrue(policy.horizontalPadding*2 < width)
                    assertTrue(policy.verticalPadding*2+48 <= height)
                    assertEquals(height<240f,policy.inlineClose)
                    assertEquals(height<360f || width<340f || scale>=1.5f,policy.compact)
                }
            }
        }
    }
}
