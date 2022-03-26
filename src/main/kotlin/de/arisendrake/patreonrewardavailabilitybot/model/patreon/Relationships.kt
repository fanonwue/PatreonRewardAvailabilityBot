package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import kotlinx.serialization.Serializable

@Serializable
data class Relationships(
    val creator: CreatorRelationship? = null,
    val goals: GoalsRelationship? = null,
    val rewards: RewardsRelationship? = null,
    val campaign: CampaignRelationship? = null,
)