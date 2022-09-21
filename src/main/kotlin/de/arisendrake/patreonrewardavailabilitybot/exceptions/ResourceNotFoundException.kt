package de.arisendrake.patreonrewardavailabilitybot.exceptions

open class ResourceNotFoundException(message: String?, cause: Throwable?) : NoSuchElementException(message, cause) {
    constructor() : this(null, null)
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(null, cause)
}