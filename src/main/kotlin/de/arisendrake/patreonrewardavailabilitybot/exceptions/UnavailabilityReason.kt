package de.arisendrake.patreonrewardavailabilitybot.exceptions

enum class UnavailabilityReason(val displayName: String) {
    FORBIDDEN("Forbidden"),
    NOT_FOUND("Not Found"),
    NO_CAMPAIGN("No campaign")
}