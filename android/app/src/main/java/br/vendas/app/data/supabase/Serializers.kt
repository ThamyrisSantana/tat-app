package br.vendas.app.data.supabase

import br.vendas.app.domain.Money
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/** Postgres numeric(10,2) <-> Money, reading the JSON number text so no cent is lost. */
object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Money", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Money {
        val text = if (decoder is JsonDecoder) {
            decoder.decodeJsonElement().jsonPrimitive.content
        } else {
            decoder.decodeString()
        }
        return Money.fromBigDecimal(BigDecimal(text))
    }

    override fun serialize(encoder: Encoder, value: Money) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonPrimitive(value.toBigDecimal()))
        } else {
            encoder.encodeString(value.toBigDecimal().toPlainString())
        }
    }
}

/** Postgres date ("2026-11-25"). */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
}

/** Postgres timestamptz ("2026-11-25T13:45:00.123456+00:00"). */
object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: OffsetDateTime) = encoder.encodeString(value.toString())
}
