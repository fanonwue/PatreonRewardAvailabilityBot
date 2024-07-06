package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import de.arisendrake.patreonrewardavailabilitybot.model.serializers.CampaignIdAsLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("campaign")
class CampaignData(
    override val attributes: CampaignAttributes,
    val id: CampaignIdAsLong,
    override val relationships: Relationships? = null
) : Data<CampaignAttributes>  {
    override val rawId get() = id.rawId
}