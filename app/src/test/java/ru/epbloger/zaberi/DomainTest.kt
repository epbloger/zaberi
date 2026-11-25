package ru.epbloger.zaberi
import org.junit.Test
import java.time.LocalDate
class DomainTest { @Test fun domainChecks(){
    check(parseMoney("1 990,50")==199050L)
    check(parseMoney("0.001")==null)
    check(parseMoney("-1")==null)
    check(parseMoney("99999999999999999999999")==null)
    check(parseMoney("")==null)
    check(remaining(459000,300000)==159000L)
    check(runCatching{remaining(100,101)}.isFailure)
    check(nextStatus(Status.TRANSIT,"arrived")==Status.READY)
    check(nextStatus(Status.READY,"received")==Status.RECEIVED)
    check(nextStatus(Status.RECEIVED,"return")==Status.RETURN_PLANNED)
    check(nextStatus(Status.RETURN_PLANNED,"returned",500)==Status.REFUND)
    check(nextStatus(Status.READY,"cancel",500)==Status.REFUND)
    check(nextStatus(Status.READY,"cancel")==Status.CANCELLED)
    check(runCatching{nextStatus(Status.DONE,"arrived")}.isFailure)
    check(isOverdue(Status.READY,LocalDate.parse("2026-11-24"),LocalDate.parse("2026-11-25")))
    check(!isOverdue(Status.DONE,LocalDate.parse("2026-11-24"),LocalDate.parse("2026-11-25")))
    check(detectStore("https://www.ozon.ru/product/123")=="Ozon")
    check(detectStore("https://ozon.ru.evil.example/")==null)
    check(detectStore("https://market.yandex.ru/product/123")=="Яндекс Маркет")
} }