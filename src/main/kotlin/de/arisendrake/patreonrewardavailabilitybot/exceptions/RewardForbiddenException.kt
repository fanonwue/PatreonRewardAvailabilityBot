package de.arisendrake.patreonrewardavailabilitybot.exceptions

class RewardForbiddenException(message: String? = null, rewardId: Long? = null, cause: Throwable? = null)
    : RewardUnavailableException(message, rewardId, UnavailabilityReason.FORBIDDEN, cause)