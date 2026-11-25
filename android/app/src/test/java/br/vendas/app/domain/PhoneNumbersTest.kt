package br.vendas.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `accepts the common ways of typing a mobile number`() {
        listOf(
            "11 99999-8888",
            "(11) 99999-8888",
            "11999998888",
            "+55 11 99999-8888",
            "5511999998888",
            "011 99999-8888",
        ).forEach { assertEquals(it, "+5511999998888", PhoneNumbers.normalize(it)) }
    }

    @Test
    fun `adds the leading 9 to mobile numbers written with 8 digits`() {
        assertEquals("+5511999998888", PhoneNumbers.normalize("11 9999-8888"))
        assertEquals("+5511999998888", PhoneNumbers.normalize("551199998888"))
    }

    @Test
    fun `keeps 8-digit landlines`() {
        assertEquals("+551133334444", PhoneNumbers.normalize("(11) 3333-4444"))
    }

    @Test
    fun `area code 55 is not confused with the country code`() {
        assertEquals("+5555999998888", PhoneNumbers.normalize("55 99999-8888"))
        assertEquals("+5555999998888", PhoneNumbers.normalize("+55 55 99999-8888"))
    }

    @Test
    fun `rejects numbers without a valid area code`() {
        assertNull(PhoneNumbers.normalize("99999-8888"))
        assertNull(PhoneNumbers.normalize(""))
        assertNull(PhoneNumbers.normalize("10 99999-8888"))
        assertNull(PhoneNumbers.normalize("11 89999-8888"))
        assertNull(PhoneNumbers.normalize("abc"))
    }

    @Test
    fun `formats for display`() {
        assertEquals("(11) 99999-8888", PhoneNumbers.format("+5511999998888"))
        assertEquals("(11) 3333-4444", PhoneNumbers.format("+551133334444"))
        assertEquals("+12025550123", PhoneNumbers.format("+12025550123"))
    }

    @Test
    fun `builds wa_me digits`() {
        assertEquals("5511999998888", PhoneNumbers.waMeDigits("+5511999998888"))
    }
}
