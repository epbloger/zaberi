package ru.epbloger.zaberi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class GuideStep(val title:String,val description:String,val icon:ImageVector,val tab:Int){
    SHARE("Добавляйте покупки из магазина","Не нужно переписывать название и ссылку вручную. Поделитесь карточкой товара прямо из приложения магазина.",Icons.Default.Share,0),
    MANUAL("Добавляйте заказ вручную","Если магазин не умеет делиться карточкой, нажмите «Добавить заказ». Укажите название и магазин, затем цену, статус и срок, если они известны.",Icons.Default.AddShoppingCart,0),
    ORDERS("Следите за заказами","В разделе «Заказы» видны покупки в пути, готовые к выдаче и возвраты. Откройте заказ, чтобы отметить получение, оплату или возврат.",Icons.Default.Inventory2,0),
    CALENDAR("Не пропускайте сроки","В календаре видны даты доставки, получения, возврата и ожидаемых денег. Укажите срок в заказе и разрешите уведомления, чтобы получать напоминания.",Icons.Default.CalendarMonth,1),
    STATS("Контролируйте расходы","В статистике учитываются отмеченные оплаты и поступившие возвраты. Цена из карточки не означает, что покупка уже оплачена: оплату отмечаете вы.",Icons.Default.BarChart,2),
    SETTINGS("Настройте приложение под себя","В настройках можно изменить уведомления и оформление, сохранить пункты выдачи, создать защищённую копию и восстановить данные. Обучение всегда доступно здесь.",Icons.Default.Settings,3)
}
@Composable internal fun GuidePanel(step:GuideStep,next:()->Unit,back:()->Unit,skip:()->Unit,practice:()->Unit){
    AdaptiveActionScreen(title="Как пользоваться ЗАБЕРИ.",close=skip,action={
        Button(onClick=next,modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch).testTag("tutorial_next")){
            Text(if(step==GuideStep.SETTINGS)"Готово" else "Далее")
        }
    }){
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(LocalActionPagePadding.current).testTag("tutorial_panel"),
            verticalArrangement=Arrangement.spacedBy(UiSpacing.Block)){
            Text("Шаг "+(step.ordinal+1)+" из "+GuideStep.entries.size,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress={ (step.ordinal+1).toFloat()/GuideStep.entries.size },modifier=Modifier.fillMaxWidth())
            Surface(shape=RoundedCornerShape(UiSpacing.CardRadius),color=MaterialTheme.colorScheme.primaryContainer){
                Icon(step.icon,null,Modifier.padding(20.dp).size(36.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(step.title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            Text(step.description,style=MaterialTheme.typography.bodyLarge)
            when(step){
                GuideStep.SHARE->{
                    GuideInstruction("1","Откройте товар","В Ozon, Wildberries или другом магазине откройте карточку нужного товара.")
                    GuideInstruction("2","Нажмите «Поделиться»","Обычно кнопка со значком стрелки находится рядом с названием или в меню карточки.")
                    GuideInstruction("3","Выберите «ЗАБЕРИ.»","В системном меню Android найдите приложение. Если его не видно, раскройте список приложений.")
                    GuideInstruction("4","Проверьте и сохраните","Название, магазин и ссылка подставятся из полученных сведений. Цена и главная фотография тоже заполнятся, если магазин их передал или предоставил открытые данные карточки. Для загрузки из интернета включите автозаполнение при первом импорте.")
                    Text("Цена в магазине может отличаться от фактически оплаченной. Если данных не хватает, исправьте поля вручную. Интернет для ручного учёта не нужен.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GuideStep.MANUAL->OutlinedButton(onClick=practice,modifier=Modifier.fillMaxWidth().heightIn(min=UiSpacing.Touch).testTag("tutorial_practice")){Text("Попробовать добавить заказ")}
                GuideStep.ORDERS->GuideInstruction("✓","Отметьте нужное действие","Статус и сумма возврата изменяются только по вашим действиям. Заказ не удаляется при получении.")
                GuideStep.CALENDAR->GuideInstruction("!","Укажите срок хранения","Напоминание зависит от выбранной даты и настроек уведомлений Android.")
                GuideStep.STATS->GuideInstruction("₽","Оплаты и возвраты","Проверьте сумму и отметьте оплату, когда деньги действительно списаны.")
                GuideStep.SETTINGS->GuideInstruction("✓","Ваши данные остаются у вас","Сохраняйте резервные копии с паролем перед сменой телефона. Регистрация не нужна.")
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Related)){
                if(step.ordinal>0)TextButton(onClick=back,modifier=Modifier.weight(1f).testTag("tutorial_back")){Text("Назад")}
                TextButton(onClick=skip,modifier=Modifier.weight(1f).testTag("tutorial_skip")){Text("Пропустить")}
            }
        }
    }
}
@Composable private fun GuideInstruction(number:String,title:String,body:String){
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(UiSpacing.CardRadius)){
        Row(Modifier.padding(UiSpacing.Card),horizontalArrangement=Arrangement.spacedBy(UiSpacing.Related),verticalAlignment=Alignment.Top){
            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(12.dp)){
                Box(Modifier.size(36.dp),contentAlignment=Alignment.Center){Text(number,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimaryContainer)}
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(UiSpacing.Tight)){
                Text(title,fontWeight=FontWeight.SemiBold)
                Text(body,style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
