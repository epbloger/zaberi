package ru.epbloger.zaberi

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Single Activity-owned viewport. No full-screen dialogs or separate dialog window insets.
@Composable
fun SafeAppViewport(content:@Composable ()->Unit){
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
        Box(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .testTag("safe_viewport")){content()}
    }
}

internal data class PickupMapRequest(val address:String,val lat:Double?,val lon:Double?,val save:(String,Double?,Double?)->Unit)
internal val LocalPickupMap=staticCompositionLocalOf<(PickupMapRequest)->Unit>{{error("Map host missing")}}

@Composable
internal fun MapOverlayHost(content:@Composable ()->Unit){
    var request by remember{mutableStateOf<PickupMapRequest?>(null)}
    CompositionLocalProvider(LocalPickupMap provides {r->request=r}){
        Box(Modifier.fillMaxSize()){
            // Keep the form mounted so the draft survives opening/closing the map.
            Box(if(request!=null)Modifier.fillMaxSize().clearAndSetSemantics{} else Modifier.fillMaxSize()){content()}
            request?.let{r->
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
                    MapPicker(r.address,r.lat,r.lon,{request=null}){address,lat,lon->r.save(address,lat,lon);request=null}
                }
            }
        }
    }
}

// The primary action is measured before the weighted content. Even on short screens,
// scrolling content cannot push the button beyond the safe viewport.
@Composable
internal fun AdaptiveActionScreen(title:String,close:()->Unit,action:@Composable ()->Unit,content:@Composable ()->Unit){
    BoxWithConstraints(Modifier.fillMaxSize()){
        val short=maxHeight<360.dp
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){
            Row(Modifier.fillMaxWidth().heightIn(min=if(short)48.dp else 56.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick=close){Icon(Icons.Default.Close,"Закрыть")}
                Text(title,Modifier.weight(1f).padding(end=12.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            }
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.TopCenter){
                Box(Modifier.widthIn(max=720.dp).fillMaxWidth().fillMaxHeight()){content()}
            }
            Surface(Modifier.fillMaxWidth(),shadowElevation=2.dp,color=MaterialTheme.colorScheme.surface){
                Box(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=if(short)4.dp else 8.dp),contentAlignment=Alignment.Center){
                    Box(Modifier.widthIn(max=720.dp).fillMaxWidth()){action()}
                }
            }
        }
    }
}
