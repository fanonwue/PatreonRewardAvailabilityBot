package de.arisendrake.patreonrewardavailabilitybot.model.serializers

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardId
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.asRewardId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object RewardIdValueSerializer : KSerializer<RewardId> {
    override fun serialize(encoder: Encoder, value: RewardId) {
        encoder.encodeLong(value.rawId)
    }

    override fun deserialize(decoder: Decoder): RewardId {
        return decoder.decodeLong().asRewardId()
    }

    override val descriptor = PrimitiveSerialDescriptor("RewardIdAsLong", PrimitiveKind.LONG)
}

typealias RewardIdAsLong = @Serializable(with = RewardIdValueSerializer::class) RewardId