package br.vendas.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses amounts as people type them`() {
        assertEquals(Money(6000), Money.parse("60"))
        assertEquals(Money(6050), Money.parse("60,5"))
        assertEquals(Money(5990), Money.parse("59,90"))
        assertEquals(Money(123456), Money.parse("1.234,56"))
        assertEquals(Money(123400), Money.parse("1.234"))
        assertEquals(Money(6050), Money.parse("60.50"))
        assertEquals(Money(6000), Money.parse("R$ 60,00"))
    }

    @Test
    fun `rejects malformed amounts`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-10"))
        assertNull(Money.parse("10,999"))
        assertNull(Money.parse("1,2,3"))
    }

    @Test
    fun `formats in Brazilian reais`() {
        assertEquals("R$ 1.234,56", Money(123456).format())
        assertEquals("R$ 0,05", Money(5).format())
    }

    @Test
    fun `input text round-trips`() {
        assertEquals("59,90", Money(5990).toInputText())
        assertEquals(Money(5990), Money.parse(Money(5990).toInputText()))
    }
}
