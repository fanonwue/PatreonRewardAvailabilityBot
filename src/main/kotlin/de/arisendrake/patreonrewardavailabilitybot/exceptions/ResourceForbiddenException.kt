package de.arisendrake.patreonrewardavailabilitybot.exceptions

open class ResourceForbiddenException(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    constructor() : this(null, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(null, cause)
}