package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import de.arisendrake.patreonrewardavailabilitybot.model.serializers.CampaignIdAsLong
import kotlinx.serialization.Serializable

@Serializable
data class CampaignRelationshipData(
    val id: CampaignIdAsLong
)