package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("reward")
data class RewardId(
    val id: Long
)
