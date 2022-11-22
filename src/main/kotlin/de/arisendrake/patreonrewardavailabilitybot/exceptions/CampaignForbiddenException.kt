package de.arisendrake.patreonrewardavailabilitybot.exceptions

class CampaignForbiddenException(message: String?, cause: Throwable?) : ResourceForbiddenException(message, cause) {
    constructor() : this(null, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(null, cause)
}