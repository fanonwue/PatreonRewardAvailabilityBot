package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import kotlinx.serialization.Serializable

@Serializable
sealed interface Data <T> {
    val rawId: Long
    val attributes: T
    val relationships: Relationships?
}