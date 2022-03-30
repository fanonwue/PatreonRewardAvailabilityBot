package de.arisendrake.patreonrewardavailabilitybot.model

import de.arisendrake.patreonrewardavailabilitybot.model.serializers.InstantSerializer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

import java.time.Instant

@Serializable
data class RewardEntry(
    val id: Long,
    @Serializable(with = InstantSerializer::class)
    var availableSince: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    var lastNotified: Instant? = null
) {
    @Transient
    private val mutex = Mutex()

    suspend fun <T> withLock(owner: Any? = null, block: () -> T) = mutex.withLock(owner, block)
}