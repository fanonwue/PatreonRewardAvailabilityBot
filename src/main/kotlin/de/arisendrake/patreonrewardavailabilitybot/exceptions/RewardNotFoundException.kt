package de.arisendrake.patreonrewardavailabilitybot.exceptions

class RewardNotFoundException(message: String?, cause: Throwable?) : ResourceNotFoundException(message, cause) {
    constructor() : this(null, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(null, cause)
}