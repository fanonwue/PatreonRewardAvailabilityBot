package de.arisendrake.patreonrewardavailabilitybot.model.serializers

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object CampaignIdObjectSerializer : KSerializer<CampaignId> {
    override fun serialize(encoder: Encoder, value: CampaignId) {
        encoder.encodeSerializableValue(CampaignIdObject.serializer(), CampaignIdObject(value))
    }

    override fun deserialize(decoder: Decoder): CampaignId {
        return decoder.decodeSerializableValue(CampaignIdObject.serializer()).id
    }

    override val descriptor = CampaignIdObject.serializer().descriptor
}

typealias CampaignIdAsObject = @Serializable(with = CampaignIdObjectSerializer::class) CampaignId