package br.vendas.app.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val SHORT_DATE = DateTimeFormatter.ofPattern("dd/MM")

fun LocalDate.formatFull(): String = format(FULL_DATE)

fun LocalDate.formatShort(): String = format(SHORT_DATE)

fun OffsetDateTime.formatFull(): String = atZoneSameInstant(br.vendas.app.domain.SHOP_ZONE).toLocalDate().formatFull()

/** Material date pickers work with UTC midnight milliseconds. */
fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun localDateFromPickerMillis(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
