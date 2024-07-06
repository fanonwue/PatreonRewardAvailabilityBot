package de.arisendrake.patreonrewardavailabilitybot.model.serializers

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardId
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardIdObject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object RewardIdObjectSerializer : KSerializer<RewardId> {
    override fun serialize(encoder: Encoder, value: RewardId) {
        encoder.encodeSerializableValue(RewardIdObject.serializer(), RewardIdObject(value))
    }

    override fun deserialize(decoder: Decoder): RewardId {
        return decoder.decodeSerializableValue(RewardIdObject.serializer()).id
    }

    override val descriptor = RewardIdObject.serializer().descriptor
}

typealias RewardIdAsObject = @Serializable(with = RewardIdObjectSerializer::class) RewardId