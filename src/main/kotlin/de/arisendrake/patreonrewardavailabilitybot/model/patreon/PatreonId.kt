package de.arisendrake.patreonrewardavailabilitybot.model.patreon

sealed interface PatreonId {
    val rawId: Long
}