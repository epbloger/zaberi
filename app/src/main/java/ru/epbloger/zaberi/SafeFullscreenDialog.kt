package ru.epbloger.zaberi

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// One Activity owns all insets. Descendant screens must not add them again.
@Composable
fun SafeAppViewport(content:@Composable ()->Unit){
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
        Box(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .clipToBounds().testTag("safe_viewport")){content()}
    }
}

internal data class PickupMapRequest(val address:String,val lat:Double?,val lon:Double?,val save:(String,Double?,Double?)->Unit)
internal val LocalPickupMap=staticCompositionLocalOf<(PickupMapRequest)->Unit>{{error("Map host missing")}}

@Composable
internal fun MapOverlayHost(content:@Composable ()->Unit){
    var request by remember{mutableStateOf<PickupMapRequest?>(null)}
    CompositionLocalProvider(LocalPickupMap provides {r->request=r}){
        // Keep the form composed at its normal size. Only place the foreground screen.
        Layout(modifier=Modifier.fillMaxSize(),content={
            Box(if(request!=null)Modifier.clearAndSetSemantics{} else Modifier){content()}
            request?.let{r->
                Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
                    MapPicker(r.address,r.lat,r.lon,{request=null}){address,lat,lon->r.save(address,lat,lon);request=null}
                }
            }
        }){measurables,constraints->
            val pages=measurables.map{it.measure(constraints)}
            layout(constraints.maxWidth,constraints.maxHeight){pages.lastOrNull()?.placeRelative(0,0)}
        }
    }
}

@Composable
internal fun AdaptiveActionScreen(title:String,close:()->Unit,action:@Composable ()->Unit,content:@Composable ()->Unit){
    BoxWithConstraints(Modifier.fillMaxSize()){
        val policy=adaptiveViewport(maxWidth.value,maxHeight.value,LocalDensity.current.fontScale)
        Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.TopCenter){
                Column(Modifier.widthIn(max=policy.maxContentWidth.dp).fillMaxWidth().fillMaxHeight()){
                    if(!policy.inlineClose){
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                            IconButton(onClick=close){Icon(Icons.Default.Close,"Закрыть")}
                            Text(title,Modifier.weight(1f).padding(end=8.dp),style=if(policy.compact)MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()){
                        CompositionLocalProvider(LocalActionPagePadding provides policy.horizontalPadding.dp){content()}
                    }
                }
            }
            // Non-weighted primary action is measured before scrollable content.
            Surface(Modifier.fillMaxWidth(),shadowElevation=2.dp,color=MaterialTheme.colorScheme.surface){
                Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
                    Row(Modifier.widthIn(max=policy.maxContentWidth.dp).fillMaxWidth().padding(horizontal=policy.horizontalPadding.dp,vertical=policy.verticalPadding.dp),verticalAlignment=Alignment.CenterVertically){
                        if(policy.inlineClose)IconButton(onClick=close){Icon(Icons.Default.Close,"Закрыть")}
                        Box(Modifier.weight(1f)){action()}
                    }
                }
            }
        }
    }
}
