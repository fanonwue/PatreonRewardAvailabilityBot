package de.arisendrake.patreonrewardavailabilitybot.model

import kotlinx.serialization.Serializable

@Serializable
data class RewardsDataV1(
    val dataVersion: Int,
    val data: Set<Long>
)