package de.arisendrake.patreonrewardavailabilitybot.exceptions

open class RewardUnavailableException(
    message: String? = null,
    val rewardId: Long? = null,
    val unavailabilityReason: UnavailabilityReason = UnavailabilityReason.NOT_FOUND,
    cause: Throwable? = null
) : RuntimeException(message, cause)