package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentMap

data class FetcherCacheStore<T : Data<*>>(
    val cache: ConcurrentMap<Long, Pair<Instant, T>>,
    val maxSize: Int,
    val mutex: Mutex = Mutex()
) {
    suspend fun put(value: T) = put(value.id, value)
    suspend fun put(key: Long, value: T) = mutex.withLock {
        cache[key] = Instant.now() to value
    }

    fun get(key: Long) = cache[key]
}
