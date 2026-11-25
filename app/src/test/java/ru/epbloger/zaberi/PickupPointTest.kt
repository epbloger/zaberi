package ru.epbloger.zaberi
import org.junit.Assert.*
import org.junit.Test
class PickupPointTest {
 @Test fun coordinates(){
  assertTrue(validPoint(null,null))
  assertTrue(validPoint(56.84,60.61))
  assertFalse(validPoint(56.84,null))
  assertFalse(validPoint(90.0,0.0))
  assertFalse(validPoint(0.0,181.0))
  assertFalse(validPoint(Double.NaN,0.0))
 }
 @Test fun projectionRoundTrip(){
  assertEquals(56.84,yToLat(latToY(56.84)),0.000001)
  assertEquals(60.61,xToLon(lonToX(60.61)),0.000001)
  assertEquals(0.5,latToY(0.0),0.000001)
 }
}
