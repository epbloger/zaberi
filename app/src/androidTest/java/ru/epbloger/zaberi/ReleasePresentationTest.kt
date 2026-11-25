package ru.epbloger.zaberi

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReleasePresentationTest {
    @get:Rule val compose=createComposeRule()

    @Test fun actionColorsRemainLegibleInBothThemes(){
        val ratios=mutableListOf<Float>()
        val unique=mutableListOf<Int>()
        compose.setContent{
            Column{
                for(dark in listOf(false,true)){
                    MaterialTheme(colorScheme=if(dark)darkColorScheme() else lightColorScheme()){
                        val tones=listOf(ActionTone.PAYMENT,ActionTone.DELIVERY,ActionTone.RETURN,ActionTone.DANGER)
                        val filled=tones.map{actionColor(it)}
                        val surface=MaterialTheme.colorScheme.surface
                        val outlined=tones.map{actionColor(it,false)}
                        androidx.compose.runtime.SideEffect{
                            unique.add(filled.toSet().size)
                            filled.forEach{ratios.add(contrast(Color.White,it))}
                            outlined.forEach{ratios.add(contrast(it,surface))}
                        }
                    }
                }
            }
        }
        compose.runOnIdle{
            assertTrue("Both themes checked",unique.size>=2)
            assertTrue("Semantic tones must be distinct",unique.all{it==4})
            assertTrue("Action labels need at least 4.5:1 contrast",ratios.all{it>=4.5f})
        }
    }

    @Test fun advertisingSettingsCannotDisableAdsOrEnablePersonalization(){
        val app=ApplicationProvider.getApplicationContext<Context>()
        val storage=app.getSharedPreferences("advertising_settings_test",Context.MODE_PRIVATE)
        storage.edit().clear().putBoolean("personalizedAds",true).putBoolean("adsEnabled",false).commit()
        val isolated=object:ContextWrapper(app){
            override fun getSharedPreferences(name:String?,mode:Int):SharedPreferences=storage
        }
        val preferences=Preferences(isolated)
        try{
            assertFalse(preferences.personalizedAds)
            compose.setContent{MaterialTheme{Column{AdsSettings(preferences)}}}
            compose.onNodeWithText("Отключить рекламу в настройках нельзя.",substring=true).assertExists()
            if(BuildConfig.STORE_AGE<12){
                compose.onNodeWithText("Персонализированная реклама не используется",substring=true).assertExists()
                compose.onAllNodes(isToggleable()).assertCountEquals(0)
            }else{
                compose.onNode(isToggleable()).assertIsOff().performClick()
                compose.runOnIdle{assertFalse(preferences.personalizedAds)}
                compose.onNodeWithText("Разрешить персонализацию").assertExists()
                compose.runOnIdle{preferences.setAdConsent(true);assertTrue(preferences.personalizedAds);preferences.confirmAdultAds(false);assertFalse(preferences.personalizedAds)}
            }
        }finally{
            storage.edit().clear().commit()
        }
    }

    private fun contrast(a:Color,b:Color):Float{
        val x=a.luminance()
        val y=b.luminance()
        return (maxOf(x,y)+0.05f)/(minOf(x,y)+0.05f)
    }

    @Test fun documentsContainOnlyPublicBrandAndNoDebugSection(){
        val context=ApplicationProvider.getApplicationContext<Context>()
        for(name in listOf("privacy","terms","ads","developer")){
            val doc=context.assets.open("legal/$name.md").bufferedReader(Charsets.UTF_8).use{it.readText()}
            assertFalse("Internal build notes in $name",doc.contains("Тестовая сборка",ignoreCase=true)||doc.contains("debug-сборк",ignoreCase=true)||doc.contains("кандидата на релиз",ignoreCase=true))
            assertFalse("Personal name in $name",doc.contains("Матвеев",ignoreCase=true))
        }
        val developer=context.assets.open("legal/developer.md").bufferedReader(Charsets.UTF_8).use{it.readText()}
        assertTrue(developer.contains("Moorkis Development"))
        assertTrue(developer.contains("helpmoorkis@yandex.ru"))
    }
}
