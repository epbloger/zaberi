package ru.epbloger.zaberi
import org.junit.Test
import org.junit.Assert.*
class BackupCryptoTest {
    @Test fun roundTripAndRandomSalt(){
        val data="Проверка резервной копии".toByteArray()
        val a=encryptBackup(data,"test-only-password")
        val b=encryptBackup(data,"test-only-password")
        assertArrayEquals(data,decryptBackup(a,"test-only-password"))
        assertFalse(a.contentEquals(b))
    }
    @Test fun wrongPasswordAndTampering(){
        val a=encryptBackup("sample".toByteArray(),"test-only-password")
        assertTrue(runCatching{decryptBackup(a,"wrong-test-password")}.isFailure)
        a[a.lastIndex]=(a.last().toInt() xor 1).toByte()
        assertTrue(runCatching{decryptBackup(a,"test-only-password")}.isFailure)
    }
    @Test fun rejectsShortAndInvalidFiles(){
        assertTrue(runCatching{encryptBackup(byteArrayOf(1),"short")}.isFailure)
        assertTrue(runCatching{decryptBackup(ByteArray(128),"test-only-password")}.isFailure)
    }
}