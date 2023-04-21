package de.arisendrake.patreonrewardavailabilitybot.model

import de.arisendrake.patreonrewardavailabilitybot.model.serializers.InstantAsIsoString
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

import java.time.Instant

@Serializable
data class RewardEntry(
    val id: Long,
    var availableSince: InstantAsIsoString? = null,
    var lastNotified: InstantAsIsoString? = null,
    var isMissing: Boolean = false
) {
    @Transient
    private val mutex = Mutex()

    suspend fun <T> withLock(owner: Any? = null, block: () -> T) = mutex.withLock(owner, block)
}