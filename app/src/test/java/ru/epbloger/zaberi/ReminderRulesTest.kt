package ru.epbloger.zaberi
import org.junit.Test
import org.junit.Assert.*
class ReminderRulesTest {
 @Test fun leadTime(){
  assertTrue(reminderDue(Status.READY,3,3,true))
  assertFalse(reminderDue(Status.READY,4,3,true))
  assertTrue(reminderDue(Status.RETURN_PLANNED,0,0,false))
  assertFalse(reminderDue(Status.RETURN_PLANNED,1,0,false))
 }
 @Test fun overdueRules(){
  assertTrue(reminderDue(Status.REFUND,-1,3,true))
  assertTrue(reminderDue(Status.REFUND,-3,3,true))
  assertFalse(reminderDue(Status.REFUND,-3,3,false))
  assertFalse(reminderDue(Status.DONE,0,7,true))
 }
}