package de.arisendrake.patreonrewardavailabilitybot.exceptions

class CampaignForbiddenException(message: String? = null, campaignId: Long? = null, cause: Throwable? = null)
    : CampaignUnavailableException(message, campaignId, UnavailabilityReason.FORBIDDEN, cause)