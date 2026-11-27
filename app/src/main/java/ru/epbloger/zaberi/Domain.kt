package ru.epbloger.zaberi
import java.math.BigDecimal
import java.time.LocalDate
enum class Status(val label: String, val deadlineLabel: String?) {
    TRANSIT("В пути", "Ожидается доставка"), READY("Можно забрать", "Забрать до"),
    RECEIVED("Получен", null), RETURN_PLANNED("Запланирован возврат", "Вернуть до"),
    REFUND("Ожидаются деньги", "Ожидаемый возврат"), DONE("Завершён", null), CANCELLED("Отменён", null);
    val archived get() = this == DONE || this == CANCELLED
}
fun parseMoney(value: String): Long? = try {
    val normalized = value.trim().replace(" ", "").replace(',', '.')
    if (normalized.isEmpty()) null else BigDecimal(normalized).movePointRight(2).longValueExact().also { require(it in 0..999999999999L) }
} catch (_: Exception) { null }
fun remaining(expected: Long, received: Long): Long { require(expected >= 0 && received in 0..expected); return expected - received }
fun isOverdue(status: Status, date: LocalDate?, today: LocalDate): Boolean = status.deadlineLabel != null && date != null && date.isBefore(today)
fun nextStatus(current: Status, event: String, paid: Long = 0L): Status = when (event) {
    "arrived" -> { require(current == Status.TRANSIT); Status.READY }
    "received" -> { require(current == Status.READY || current == Status.TRANSIT); Status.RECEIVED }
    "keep" -> { require(current == Status.RECEIVED); Status.DONE }
    "undo_received" -> { require(current == Status.RECEIVED); Status.READY }
    "cancel_return" -> { require(current == Status.RETURN_PLANNED); Status.RECEIVED }
    "reopen" -> { require(current == Status.DONE); Status.RECEIVED }
    "return" -> { require(current == Status.RECEIVED || current == Status.DONE); Status.RETURN_PLANNED }
    "returned" -> { require(current == Status.RETURN_PLANNED); if (paid > 0) Status.REFUND else Status.DONE }
    "cancel" -> { require(current == Status.TRANSIT || current == Status.READY); if (paid > 0) Status.REFUND else Status.CANCELLED }
    else -> error("Unknown action")
}
fun detectStore(text: String): String? {
    val url = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(text)?.value ?: return null
    val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return null
    fun matches(domain: String) = host == domain || host.endsWith(".$domain")
    return when { matches("ozon.ru") || matches("ozon.com") -> "Ozon"; matches("wildberries.ru") || matches("wb.ru") -> "Wildberries"; matches("market.yandex.ru") -> "Яндекс Маркет"; matches("avito.ru") -> "Авито"; matches("megamarket.ru") || matches("sbermegamarket.ru") -> "Мегамаркет"; matches("aliexpress.ru") || matches("aliexpress.com") -> "AliExpress"; matches("lamoda.ru") -> "Lamoda"; matches("dns-shop.ru") -> "DNS"; else -> null }
}
fun refundTargetValid(paid:Long,received:Long,target:Long):Boolean = paid>=0 && received in 0..paid && target in received..paid
fun validOperationDate(value:String,today:LocalDate):Boolean = runCatching { val date=LocalDate.parse(value); !date.isAfter(today) }.getOrDefault(false)
fun canMarkPayment(status:Status,paid:Long,returned:Boolean,cancelled:Boolean):Boolean = paid==0L&&!returned&&!cancelled&&status in listOf(Status.TRANSIT,Status.READY,Status.RECEIVED,Status.DONE,Status.RETURN_PLANNED)

fun orderPriority(status:Status,deadline:LocalDate?,today:LocalDate):Int = when {
 status.archived -> 4
 deadline!=null && !deadline.isAfter(today.plusDays(1)) && status in listOf(Status.READY,Status.RETURN_PLANNED,Status.REFUND) -> 0
 status==Status.READY -> 1
 status==Status.REFUND||status==Status.RETURN_PLANNED -> 2
 else -> 3
}

fun reminderDue(status:Status,days:Long,lead:Int,overdue:Boolean):Boolean=when(status){
 Status.READY,Status.RETURN_PLANNED -> days in 0L..lead.coerceIn(0,7).toLong() || (overdue&&days<0L&&(days== -1L||days%3L==0L))
 Status.TRANSIT -> days==0L||(overdue&&days== -1L)
 Status.REFUND -> days==0L||(overdue&&days<0L&&(days== -1L||days%3L==0L))
 else -> false
}

fun validPoint(lat:Double?,lon:Double?):Boolean = if(lat==null||lon==null)lat==null&&lon==null else lat.isFinite()&&lon.isFinite()&&lat in -85.05112878..85.05112878&&lon in -180.0..180.0
