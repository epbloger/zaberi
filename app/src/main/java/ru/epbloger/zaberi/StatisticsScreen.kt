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

@Composable internal fun Stats(orders:List<Order>,tx:List<Transaction>){
    var period by rememberSaveable{mutableStateOf("Месяц")}
    var offset by rememberSaveable{mutableIntStateOf(0)}
    var grouping by rememberSaveable{mutableStateOf("Магазины")}
    var showOperations by rememberSaveable{mutableStateOf(false)}
    val now=LocalDate.now()
    val month=YearMonth.from(now).plusMonths(offset.toLong())
    val year=now.year+offset
    val subset=tx.filter{when(period){"Месяц"->it.date.startsWith(month.toString());"Год"->it.date.startsWith(year.toString());else->true}}
    val spent=subset.filter{it.amount>0}.sumOf{it.amount}
    val refund=-subset.filter{it.amount<0}.sumOf{it.amount}
    val pending=orders.filter{it.state==Status.REFUND}.sumOf{remaining(it.expectedRefund,it.receivedRefund)}
    val index=orders.associateBy{it.id}
    val groups=subset.groupBy{t->if(grouping=="Магазины")index[t.orderId]?.store?:"Другой" else index[t.orderId]?.category?.ifBlank{"Без категории"}?:"Без категории"}.mapValues{(_,v)->v.sumOf{it.amount}}.toList().sortedByDescending{it.second}
    val largest=groups.maxOfOrNull{kotlin.math.abs(it.second)}?.coerceAtLeast(1L)?:1L
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Месяц","Год","Всё время").forEach{FilterChip(period==it,{period=it;offset=0},{Text(it)})}}}
        if(period!="Всё время")item{Row(verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={if(offset> -1200)offset--}){Icon(Icons.Default.ChevronLeft,"Предыдущий период")}
            Text(if(period=="Месяц")month.format(DateTimeFormatter.ofPattern("LLLL yyyy",Locale("ru"))).replaceFirstChar{it.titlecase(Locale("ru"))}else year.toString(),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick={offset++},enabled=offset<0){Icon(Icons.Default.ChevronRight,"Следующий период")}
        }}
        item{Card(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Фактические расходы",style=MaterialTheme.typography.labelLarge)
                Text(money(spent-refund),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text("Оплата минус полученные возвраты за период",style=MaterialTheme.typography.bodySmall)
            }
        }}
        item{BoxWithConstraints(Modifier.fillMaxWidth()){if(maxWidth<340.dp||androidx.compose.ui.platform.LocalDensity.current.fontScale>=1.5f)Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Metric("Оплачено",money(spent),Modifier.fillMaxWidth());Metric("Возвращено",money(refund),Modifier.fillMaxWidth())}else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("Оплачено",money(spent),Modifier.weight(1f));Metric("Возвращено",money(refund),Modifier.weight(1f))}}}
        item{Metric("Ожидаются возвраты · все заказы",money(pending),Modifier.fillMaxWidth())}
        item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Магазины","Категории").forEach{FilterChip(grouping==it,{grouping=it},{Text(it)})}}}
        if(groups.isEmpty())item{Text("Нет операций за выбранный период.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
        items(groups){(label,total)->
            OutlinedCard(shape=RoundedCornerShape(18.dp),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)){
                Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontWeight=FontWeight.Medium);Text(money(total),fontWeight=FontWeight.Bold)}
                    LinearProgressIndicator(progress={kotlin.math.abs(total).toFloat()/largest.toFloat()},modifier=Modifier.fillMaxWidth().height(5.dp),color=if(total<0)MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                }
            }
        }
        item{TextButton(onClick={showOperations=!showOperations}){Text(if(showOperations)"Скрыть операции" else "Показать операции ("+subset.size+")")}}
        if(showOperations)items(subset.sortedByDescending{it.date},key={it.id}){t->
            Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(index[t.orderId]?.name?:"Заказ",maxLines=2,overflow=TextOverflow.Ellipsis);Text(dateText(t.date),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Text((if(t.amount<0)"−" else "+")+money(kotlin.math.abs(t.amount)),fontWeight=FontWeight.SemiBold)
            }
        }
    }
}
