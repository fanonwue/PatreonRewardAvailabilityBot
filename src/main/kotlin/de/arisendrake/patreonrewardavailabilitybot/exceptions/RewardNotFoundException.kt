package de.arisendrake.patreonrewardavailabilitybot.exceptions

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardId

class RewardNotFoundException(message: String? = null, rewardId: RewardId? = null, cause: Throwable? = null)
    : RewardUnavailableException(message, rewardId, UnavailabilityReason.NOT_FOUND, cause)