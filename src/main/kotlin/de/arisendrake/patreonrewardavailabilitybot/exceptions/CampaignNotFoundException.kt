package de.arisendrake.patreonrewardavailabilitybot.exceptions

class CampaignNotFoundException(message: String? = null, campaignId: Long? = null, cause: Throwable? = null)
    : CampaignUnavailableException(message, campaignId, UnavailabilityReason.NOT_FOUND, cause)