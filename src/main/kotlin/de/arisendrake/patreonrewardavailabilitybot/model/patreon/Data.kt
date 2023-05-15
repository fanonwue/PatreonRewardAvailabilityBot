package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import kotlinx.serialization.Serializable

@Serializable
data class Data <T> (
    val attributes: T,
    val id: Long,
    val relationships: Relationships
)

typealias RewardData = Data<RewardsAttributes>
typealias CampaignData = Data<CampaignAttributes>