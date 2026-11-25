package br.vendas.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** An amount in Brazilian reais, kept as whole cents so sums never drift. */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(cents + other.cents)

    operator fun minus(other: Money) = Money(cents - other.cents)

    operator fun times(quantity: Int) = Money(cents * quantity)

    override fun compareTo(other: Money) = cents.compareTo(other.cents)

    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(cents, 2)

    /** Formats as "R$ 1.234,56". */
    fun format(): String = currencyFormat().format(toBigDecimal()).replace(' ', ' ')

    /** Formats without the currency symbol, as typed in a field: "1234,56". */
    fun toInputText(): String = toBigDecimal().toPlainString().replace('.', ',')

    companion object {
        val ZERO = Money(0)

        private val THOUSANDS_ONLY = Regex("""^\d{1,3}(\.\d{3})+$""")
        private val PLAIN_NUMBER = Regex("""^\d+(\.\d+)?$""")

        fun fromBigDecimal(value: BigDecimal): Money =
            Money(value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact())

        /**
         * Reads an amount typed by a person: "60", "60,5", "1.234,56", "R$ 60,00", "60.50".
         * Returns null for empty, negative or malformed input, or more than 2 decimals.
         */
        fun parse(text: String): Money? {
            val cleaned = text
                .replace("R$", "")
                .replace(" ", "")
                .replace(" ", "")
                .trim()
            if (cleaned.isEmpty()) return null

            val normalized = when {
                cleaned.contains(',') -> cleaned.replace(".", "").replace(',', '.')
                THOUSANDS_ONLY.matches(cleaned) -> cleaned.replace(".", "")
                else -> cleaned
            }
            if (!PLAIN_NUMBER.matches(normalized)) return null

            val value = normalized.toBigDecimal()
            if (value.scale() > 2) return null
            return fromBigDecimal(value)
        }

        private fun currencyFormat(): NumberFormat =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    }
}

fun Iterable<Money>.sum(): Money = Money(sumOf { it.cents })
