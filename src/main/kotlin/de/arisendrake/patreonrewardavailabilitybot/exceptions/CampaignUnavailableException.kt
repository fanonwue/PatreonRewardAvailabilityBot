package de.arisendrake.patreonrewardavailabilitybot.exceptions

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.CampaignId

open class CampaignUnavailableException(
    message: String? = null,
    val campaignId: CampaignId? = null,
    val unavailabilityReason: UnavailabilityReason = UnavailabilityReason.NOT_FOUND,
    cause: Throwable? = null
) : RuntimeException(message, cause)