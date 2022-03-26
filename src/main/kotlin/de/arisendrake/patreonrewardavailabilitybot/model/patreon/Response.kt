package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import kotlinx.serialization.Serializable

@Serializable
data class Response <T> (
    val data: Data<T>
)