package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import de.arisendrake.patreonrewardavailabilitybot.model.serializers.RewardIdAsLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("reward")
class RewardData(
    override val attributes: RewardsAttributes,
    val id: RewardIdAsLong,
    override val relationships: Relationships? = null
) : Data<RewardsAttributes>  {
    override val rawId: Long by id
}