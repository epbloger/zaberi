package ru.epbloger.zaberi
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate
class PriorityTest {
 @Test fun urgentBeforeOtherOrders(){
   val t=LocalDate.of(2026,11,25)
   assertEquals(0,orderPriority(Status.READY,t.minusDays(1),t))
   assertEquals(0,orderPriority(Status.RETURN_PLANNED,t.plusDays(1),t))
   assertEquals(1,orderPriority(Status.READY,t.plusDays(5),t))
   assertEquals(2,orderPriority(Status.REFUND,null,t))
   assertEquals(3,orderPriority(Status.TRANSIT,t,t))
   assertEquals(4,orderPriority(Status.DONE,t,t))
 }
}