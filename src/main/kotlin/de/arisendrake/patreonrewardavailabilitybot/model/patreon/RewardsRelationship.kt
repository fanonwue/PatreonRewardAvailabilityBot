package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RewardsRelationship(
    val data: Collection<RewardId>
)