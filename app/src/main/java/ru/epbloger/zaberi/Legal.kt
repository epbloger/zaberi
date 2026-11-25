
package ru.epbloger.zaberi
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

const val SUPPORT_EMAIL="helpmoorkis@yandex.ru"
fun openSupportEmail(context:Context){
    val intent=Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+SUPPORT_EMAIL)).putExtra(Intent.EXTRA_SUBJECT,"Забери — обращение в поддержку")
    if(runCatching{context.startActivity(intent)}.isFailure)Toast.makeText(context,"Напишите нам: "+SUPPORT_EMAIL,Toast.LENGTH_LONG).show()
}

@Composable fun LegalMenu(open:(String)->Unit){
    Text("Документы доступны без интернета.",color=MaterialTheme.colorScheme.onSurfaceVariant)
    SettingsLink(Icons.Default.Shield,"Конфиденциальность","Какие данные используются и где хранятся"){open("Конфиденциальность")}
    SettingsLink(Icons.Default.Description,"Условия использования","Назначение приложения и правила"){open("Условия использования")}
    SettingsLink(Icons.Default.Campaign,"Реклама и сторонние сервисы","Баннеры, данные и внешние сервисы"){open("Реклама и сторонние сервисы")}
    SettingsLink(Icons.Default.ContactSupport,"Разработчик и контакты","Обратная связь и сведения о распространителе"){open("Разработчик и контакты")}
}
@Composable fun LegalDocument(page:String){
    val ctx=LocalContext.current
    val file=when(page){"Конфиденциальность"->"privacy";"Условия использования"->"terms";"Реклама и сторонние сервисы"->"ads";else->"developer"}
    val document=remember(file){runCatching{ctx.assets.open("legal/"+file+".md").bufferedReader(Charsets.UTF_8).use{it.readText()}}.getOrDefault("Документ недоступен. Обновите приложение.")}
    document.lineSequence().filter{it.isNotBlank()}.forEach{line->
        when{
            line.startsWith("## ")->Text(line.removePrefix("## "),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            line.startsWith("# ")->Text(line.removePrefix("# "),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            else->Text(line,style=MaterialTheme.typography.bodyMedium)
        }
    }
    if(file=="developer"||file=="privacy"||file=="terms")OutlinedButton(onClick={openSupportEmail(ctx)},modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch)){Text("Написать в поддержку")}
}
