package de.arisendrake.patreonrewardavailabilitybot.model.patreon

sealed interface PatreonId {
    val id: Long
    val rawId get() = id
}