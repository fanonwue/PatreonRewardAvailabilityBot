package de.arisendrake.patreonrewardavailabilitybot.model.serializers

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.CampaignId
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.asCampaignId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object CampaignIdValueSerializer : KSerializer<CampaignId> {
    override fun serialize(encoder: Encoder, value: CampaignId) {
        encoder.encodeLong(value.rawId)
    }

    override fun deserialize(decoder: Decoder): CampaignId {
        return decoder.decodeLong().asCampaignId()
    }

    override val descriptor = PrimitiveSerialDescriptor("CampaignIdAsLong", PrimitiveKind.LONG)
}

typealias CampaignIdAsLong = @Serializable(with = CampaignIdValueSerializer::class) CampaignId