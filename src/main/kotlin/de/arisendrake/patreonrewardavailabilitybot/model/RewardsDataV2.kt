package de.arisendrake.patreonrewardavailabilitybot.model

import kotlinx.serialization.Serializable

@Serializable
data class RewardsDataV2(
    val dataVersion: Int,
    val data: Collection<RewardEntry>
)