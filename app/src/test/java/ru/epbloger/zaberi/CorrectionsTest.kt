package ru.epbloger.zaberi
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class CorrectionsTest {
 @Test fun correctStatuses(){
   assertEquals(Status.READY,nextStatus(Status.RECEIVED,"undo_received"))
   assertEquals(Status.RECEIVED,nextStatus(Status.RETURN_PLANNED,"cancel_return"))
   assertEquals(Status.RECEIVED,nextStatus(Status.DONE,"reopen"))
   assertTrue(runCatching{nextStatus(Status.REFUND,"undo_received")}.isFailure)
 }
 @Test fun refundTarget(){
   assertTrue(refundTargetValid(1000,200,200))
   assertTrue(refundTargetValid(1000,200,1000))
   assertFalse(refundTargetValid(1000,200,199))
   assertFalse(refundTargetValid(1000,200,1001))
 }
 @Test fun paymentDate(){
   val today=LocalDate.of(2026,11,25)
   assertTrue(validOperationDate("2026-11-25",today))
   assertTrue(validOperationDate("2026-11-24",today))
   assertFalse(validOperationDate("2026-11-26",today))
   assertFalse(validOperationDate("2026-02-30",today))
 }
 @Test fun paymentAfterClosing(){
   assertTrue(canMarkPayment(Status.DONE,0,false,false))
   assertFalse(canMarkPayment(Status.DONE,0,true,false))
   assertFalse(canMarkPayment(Status.DONE,100,false,false))
   assertFalse(canMarkPayment(Status.CANCELLED,0,false,true))
 }
}