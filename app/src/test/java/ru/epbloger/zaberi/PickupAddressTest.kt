package ru.epbloger.zaberi
import org.junit.Assert.*
import org.junit.Test

class PickupAddressTest {
    @Test fun labelIsOnlyForItsOwnPoint(){
        assertEquals("Ленина, 10",matchingPickupLabel(" Ленина, 10 ",0.5,0.4,0.5,0.4))
        assertNull(matchingPickupLabel("Ленина, 10",0.5,0.4,0.51,0.4))
        assertNull(matchingPickupLabel("Ленина, 10",0.5,0.4,0.5,0.41))
        assertNull(matchingPickupLabel("Ленина, 10",null,null,0.5,0.4))
    }
    @Test fun invalidAddressRejected(){
        assertNull(cleanPickupAddress(null))
        assertNull(cleanPickupAddress("   "))
        assertNull(cleanPickupAddress("а".repeat(201)))
        assertEquals("а".repeat(200),cleanPickupAddress("а".repeat(200)))
    }
}
