package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import de.arisendrake.patreonrewardavailabilitybot.model.serializers.RewardIdAsObject
import kotlinx.serialization.Serializable

@Serializable
data class RewardsRelationship(
    val data: Collection<RewardIdAsObject>
)