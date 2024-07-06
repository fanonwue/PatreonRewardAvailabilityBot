package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

data class FetcherCacheStore<T : Data<*>>(
    val maxSize: Int,
    val ttl: Duration = Duration.ofMinutes(5),
    private val cache: MutableMap<Long, Pair<Instant, T>> = mutableMapOf(),
    private val lock: ReadWriteLock = ReentrantReadWriteLock()
) {
    fun put(value: T) = put(value.rawId, value)
    fun put(key: Long, value: T) = lock.writeLock().withLock {
        cache[key] = Instant.now() to value
    }

    fun getValueIfValid(key: Long) = this[key]?.takeIf { isCacheValid(it.first) }?.second

    operator fun get(key: Long) = lock.readLock().withLock { cache[key] }
    operator fun set(key: Long, value: T) = put(key, value)

    private fun isCacheValid(cacheTime: Instant) = (Instant.now().epochSecond - cacheTime.epochSecond) < ttl.seconds

    fun removeInvalidCacheEntries() = lock.writeLock().withLock {
        cache.removeAll(
            cache.mapNotNull { (key, value) ->
                if (isCacheValid(value.first)) null else key
            }
        )

        if (cache.size > maxSize) {
            // Evict oldest entries
            val cacheElements = cache.toList().sortedByDescending { it.second.first }
            cache.removeAll(
                cacheElements.subList(maxSize, cacheElements.size).map { it.first }
            )
        }
    }
}
