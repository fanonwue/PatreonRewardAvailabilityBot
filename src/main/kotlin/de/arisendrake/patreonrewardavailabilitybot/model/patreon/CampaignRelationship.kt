package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import kotlinx.serialization.Serializable

@Serializable
data class CampaignRelationship(
    val data: CampaignRelationshipData
)