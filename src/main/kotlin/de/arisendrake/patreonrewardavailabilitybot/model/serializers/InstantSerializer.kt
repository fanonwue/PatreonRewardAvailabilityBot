package de.arisendrake.patreonrewardavailabilitybot.model.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object InstantSerializer : KSerializer<Instant> {
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("UTC"))

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(formatter.format(value))
    }

    override fun deserialize(decoder: Decoder): Instant {
        return Instant.from(formatter.parse(decoder.decodeString()))
    }

    override val descriptor = PrimitiveSerialDescriptor("DateTime", PrimitiveKind.STRING)
}

typealias InstantAsIsoString = @Serializable(with = InstantSerializer::class) Instant