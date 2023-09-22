package de.arisendrake.patreonrewardavailabilitybot.model

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.CampaignData
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardData

data class RewardAction(
    val chatId: Long,
    val rewardEntry: RewardEntry,
    val actionType: RewardActionType,
    val rewardData: RewardData? = null,
    val campaignData: CampaignData? = null,
) {
    val rewardId get() = rewardEntry.rewardId
}