package br.vendas.app.ui.common

import br.vendas.app.domain.SHOP_ZONE
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
private val FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM")
private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR)

fun LocalDate.formatFull(): String = format(FULL_DATE)

fun LocalDate.formatShort(): String = format(SHORT_DATE)

/** "quarta-feira, 25 de novembro" */
fun LocalDate.formatLong(): String = format(LONG_DATE).replaceFirstChar { it.uppercase() }

fun OffsetDateTime.formatFull(): String = atZoneSameInstant(SHOP_ZONE).toLocalDate().formatFull()

/** "Nov" */
fun YearMonth.shortName(): String =
    month.getDisplayName(TextStyle.SHORT, PT_BR).removeSuffix(".").replaceFirstChar { it.uppercase() }

/** "Novembro de 2026" */
fun YearMonth.longName(): String =
    month.getDisplayName(TextStyle.FULL, PT_BR).replaceFirstChar { it.uppercase() } + " de $year"

/** "nov/2026" */
fun YearMonth.compactName(): String = month.getDisplayName(TextStyle.SHORT, PT_BR).removeSuffix(".") + "/$year"

/** Material date pickers work with UTC midnight milliseconds. */
fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun localDateFromPickerMillis(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/** "1 compra", "3 compras". */
fun plural(count: Int, one: String, many: String): String = "$count ${if (count == 1) one else many}"

/** "R$ 1,2 mil" for tight spots such as chart labels. */
fun br.vendas.app.domain.Money.formatCompact(): String {
    val reais = cents / 100.0
    return when {
        reais >= 1_000_000 -> "R$ %.1f mi".format(PT_BR, reais / 1_000_000)
        reais >= 1_000 -> "R$ %.1f mil".format(PT_BR, reais / 1_000)
        else -> "R$ %.0f".format(PT_BR, reais)
    }
}
