package ru.epbloger.zaberi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val Brand=Color(0xFF4658D6)
internal val categories=listOf("Одежда","Техника","Дом","Красота","Животные","Авто","Спорт","Хобби","Подарки","Продукты","Работа","Другое")
internal val stores=listOf("Ozon","Wildberries","Яндекс Маркет","Мегамаркет","Авито","AliExpress","Lamoda","DNS")
internal fun money(n:Long?)=n?.let{java.text.NumberFormat.getCurrencyInstance(Locale("ru","RU")).format(java.math.BigDecimal.valueOf(it,2))}?:"Цена не указана"
internal fun number(n:Long?)=n?.let{java.math.BigDecimal.valueOf(it,2).toPlainString()}.orEmpty()
internal fun dateText(s:String?)=s?.let{runCatching{LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMMM yyyy",Locale("ru")))}.getOrDefault(it)}?:"Не указан"
internal fun validDate(s:String)=s.isBlank()||runCatching{LocalDate.parse(s)}.isSuccess

@Composable internal fun Metric(label:String,value:String,modifier:Modifier=Modifier,accent:Boolean=false){
    Card(modifier,shape=RoundedCornerShape(UiSpacing.CardRadius),colors=CardDefaults.cardColors(containerColor=if(accent)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)){
        Column(Modifier.padding(UiSpacing.Card),verticalArrangement=Arrangement.spacedBy(UiSpacing.Related)){Text(label,style=MaterialTheme.typography.labelMedium);Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
    }
}
@Composable internal fun Field(v:String,set:(String)->Unit,label:String,decimal:Boolean=false){
    OutlinedTextField(v,set,modifier=Modifier.fillMaxWidth(),label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=if(decimal)KeyboardType.Decimal else KeyboardType.Text),singleLine=true)
}
@Composable internal fun DateField(v:String,set:(String)->Unit,label:String){
    val ctx=LocalContext.current
    val current=runCatching{LocalDate.parse(v)}.getOrNull()
    val openCalendar:()->Unit={
        val d=current?:LocalDate.now()
        android.app.DatePickerDialog(ctx,{_,y,m,day->set(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()
    }
    OutlinedCard(shape=RoundedCornerShape(UiSpacing.CardRadius),modifier=Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){
        Row(Modifier.fillMaxWidth().padding(horizontal=UiSpacing.Card,vertical=UiSpacing.Tight),verticalAlignment=Alignment.CenterVertically){
            Row(Modifier.weight(1f).heightIn(min=58.dp).clickable(role=Role.Button,onClick=openCalendar),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
                Icon(Icons.Default.CalendarMonth,"Выбрать дату",tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)){Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(if(current==null)"Выбрать дату" else dateText(v),style=MaterialTheme.typography.bodyLarge)}
            }
            if(v.isNotBlank())IconButton(onClick={set("")}){Icon(Icons.Default.Close,"Очистить дату")}
            else IconButton(onClick=openCalendar){Icon(Icons.Default.ChevronRight,"Открыть календарь")}
        }
    }
}
@Composable internal fun Primary(label:String,click:()->Unit){Button(onClick=click,modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch).testTag("primary_action")){Text(label)}}

@Composable internal fun SecondaryAction(label:String,click:()->Unit,modifier:Modifier=Modifier,destructive:Boolean=false){
    val colors=if(destructive)ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonColors()
    OutlinedButton(onClick=click,modifier=modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch),colors=colors,
        border=if(destructive)BorderStroke(1.dp,MaterialTheme.colorScheme.error.copy(alpha=.65f)) else BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){
        if(destructive){Icon(Icons.Default.DeleteOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(UiSpacing.Related))}
        Text(label)
    }
}

// The pair is a group of peer cards: its internal gap must match the gap
// between this group and the surrounding cards in both layout orientations.
@Composable internal fun AdaptiveCardPair(first:@Composable (Modifier)->Unit,second:@Composable (Modifier)->Unit){
    BoxWithConstraints(Modifier.fillMaxWidth()){
        if(maxWidth<340.dp||androidx.compose.ui.platform.LocalDensity.current.fontScale>=1.5f){
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }else{
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
                first(Modifier.weight(1f).fillMaxHeight())
                second(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}
