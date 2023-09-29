package de.arisendrake.patreonrewardavailabilitybot.model

import de.arisendrake.patreonrewardavailabilitybot.exceptions.RewardUnavailableException
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardData

data class RewardCheckResult(
    val rewardId: Long,
    val rewardData: RewardData?,
    val error: RewardUnavailableException? = null
) {
    val available get() = rewardData?.attributes?.remaining ?: 0
}
