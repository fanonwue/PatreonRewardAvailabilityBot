package de.arisendrake.patreonrewardavailabilitybot.exceptions

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardId

open class RewardUnavailableException(
    message: String? = null,
    val rewardId: RewardId? = null,
    val unavailabilityReason: UnavailabilityReason = UnavailabilityReason.NOT_FOUND,
    cause: Throwable? = null
) : RuntimeException(message, cause)