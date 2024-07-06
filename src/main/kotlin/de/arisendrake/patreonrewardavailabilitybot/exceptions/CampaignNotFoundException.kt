package de.arisendrake.patreonrewardavailabilitybot.exceptions

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.CampaignId

class CampaignNotFoundException(message: String? = null, campaignId: CampaignId? = null, cause: Throwable? = null)
    : CampaignUnavailableException(message, campaignId, UnavailabilityReason.NOT_FOUND, cause)