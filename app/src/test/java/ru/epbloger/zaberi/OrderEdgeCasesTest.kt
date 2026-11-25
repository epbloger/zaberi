package ru.epbloger.zaberi
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class OrderEdgeCasesTest {
    @Test fun moneyDoesNotRoundOrOverflow(){
        assertEquals(1L,parseMoney("0,01"))
        assertEquals(999999999999L,parseMoney("9999999999,99"))
        assertNull(parseMoney("10000000000"))
        assertNull(parseMoney("1.001"))
        assertNull(parseMoney("NaN"))
    }
    @Test fun paidCancellationTracksRefund(){
        assertEquals(Status.REFUND,nextStatus(Status.TRANSIT,"cancel",199900L))
        assertEquals(Status.CANCELLED,nextStatus(Status.TRANSIT,"cancel",0))
        assertEquals(1L,remaining(199900,199899))
        assertEquals(0L,remaining(199900,199900))
    }
    @Test fun invalidTransitionsAreRejected(){
        listOf(Status.CANCELLED,Status.REFUND,Status.RETURN_PLANNED).forEach{
            assertTrue(runCatching{nextStatus(it,"received")}.isFailure)
        }
    }
    @Test fun deadlineTodayIsNotOverdue(){
        val date=LocalDate.of(2026,1,15)
        assertFalse(isOverdue(Status.READY,date,date))
        assertTrue(isOverdue(Status.READY,date,date.plusDays(1)))
        assertFalse(isOverdue(Status.READY,null,date))
    }
    @Test fun unsafeHostsNotRecognized(){
        assertNull(detectStore("https://ozon.ru.example.com/product"))
        assertNull(detectStore("https://ozon.ru@other.example/product"))
        assertNull(detectStore("https://example.com/?store=ozon.ru"))
    }
}
