package de.arisendrake.patreonrewardavailabilitybot.exceptions

class CampaignNotFoundException(message: String?, cause: Throwable?) : ResourceNotFoundException(message, cause) {
    constructor() : this(null, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(null, cause)
}