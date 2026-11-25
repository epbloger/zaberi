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

@Composable internal fun CalendarPage(orders:List<Order>,open:(Order)->Unit){
    var monthString by rememberSaveable{mutableStateOf(YearMonth.now().toString())}
    var dayString by rememberSaveable{mutableStateOf(today())}
    var upcoming by rememberSaveable{mutableStateOf(false)}
    val month=YearMonth.parse(monthString)
    val now=LocalDate.now()
    val active=orders.filter{!it.state.archived&&it.deadline!=null}.sortedBy{it.deadline}
    val byDay=active.groupBy{it.deadline}
    val offset=month.atDay(1).dayOfWeek.value-1
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(UiSpacing.Page),verticalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Related)){FilterChip(!upcoming,{upcoming=false},{Text("Календарь")});FilterChip(upcoming,{upcoming=true},{Text("Все сроки")})}}
        if(!upcoming){
            item{
                OutlinedCard(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(UiSpacing.CardRadius),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){
                    Column(Modifier.padding(UiSpacing.Card),verticalArrangement=Arrangement.spacedBy(UiSpacing.Related)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            IconButton(onClick={monthString=month.minusMonths(1).toString();dayString=month.minusMonths(1).atDay(1).toString()}){Icon(Icons.Default.ChevronLeft,"Предыдущий месяц")}
                            Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy",Locale("ru"))).replaceFirstChar{it.titlecase(Locale("ru"))},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick={monthString=month.plusMonths(1).toString();dayString=month.plusMonths(1).atDay(1).toString()}){Icon(Icons.Default.ChevronRight,"Следующий месяц")}
                        }
                        Row{listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach{Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text(it,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
                        repeat((offset+month.lengthOfMonth()+6)/7){r->Row{repeat(7){c->
                            val n=r*7+c-offset+1
                            if(n in 1..month.lengthOfMonth()){
                                val date=month.atDay(n);val ds=date.toString();val count=byDay[ds]?.size?:0
                                val selected=dayString==ds
                                Surface(modifier=Modifier.weight(1f).heightIn(min=UiSpacing.Touch).padding(2.dp).clickable{dayString=ds},shape=RoundedCornerShape(12.dp),color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,border=if(date==now)BorderStroke(1.dp,MaterialTheme.colorScheme.primary)else null){
                                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                        Text(n.toString(),fontWeight=if(selected||date==now)FontWeight.Bold else FontWeight.Normal,color=if(selected)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                        if(count>0)Text(if(count>9)"9+" else count.toString(),style=MaterialTheme.typography.labelSmall,color=if(date.isBefore(now))MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }else Spacer(Modifier.weight(1f).heightIn(min=UiSpacing.Touch))
                        }}}
                        TextButton(onClick={monthString=YearMonth.now().toString();dayString=today()},modifier=Modifier.align(Alignment.End)){Text("Сегодня")}
                    }
                }
            }
            item{Text(dateText(dayString),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
            val selected=byDay[dayString].orEmpty()
            if(selected.isEmpty())item{Text("На этот день нет сроков.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(selected,key={it.id}){OrderCard(it){open(it)}}
        }else{
            if(active.isEmpty())item{Text("Нет заказов с указанным сроком.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
            active.groupBy{it.deadline}.forEach{(date,group)->
                item(key="day:"+date){Text(dateText(date),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,color=if(date!=null&&date<today())MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)}
                items(group,key={it.id}){OrderCard(it){open(it)}}
            }
        }
    }
}
