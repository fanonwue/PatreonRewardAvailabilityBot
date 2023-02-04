package de.arisendrake.patreonrewardavailabilitybot.exceptions

open class CampaignUnavailableException(
    message: String? = null,
    val campaignId: Long? = null,
    val unavailabilityReason: UnavailabilityReason = UnavailabilityReason.NOT_FOUND,
    cause: Throwable? = null
) : RuntimeException(message, cause)