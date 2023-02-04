package de.arisendrake.patreonrewardavailabilitybot.exceptions

class RewardNotFoundException(message: String? = null, rewardId: Long? = null, cause: Throwable? = null)
    : RewardUnavailableException(message, rewardId, UnavailabilityReason.NOT_FOUND, cause)