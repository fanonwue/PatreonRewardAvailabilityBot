package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import de.arisendrake.patreonrewardavailabilitybot.model.serializers.CampaignIdAsLong
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.RewardIdAsLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("campaign")
data class CampaignIdObject(
    val id: CampaignIdAsLong
)
