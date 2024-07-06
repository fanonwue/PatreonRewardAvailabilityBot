package de.arisendrake.patreonrewardavailabilitybot.exceptions

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardId

class RewardForbiddenException(message: String? = null, rewardId: RewardId? = null, cause: Throwable? = null)
    : RewardUnavailableException(message, rewardId, UnavailabilityReason.FORBIDDEN, cause)