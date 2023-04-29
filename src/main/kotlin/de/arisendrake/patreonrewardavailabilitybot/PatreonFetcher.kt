package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.exceptions.*
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.CampaignAttributes
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Response
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardsAttributes
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.time.delay
import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class PatreonFetcher(
    private val httpClient: HttpClient
) {
    private val cacheValidity = Config.cacheValidity
    private val cacheEvictionPeriod = Config.cacheEvictionPeriod
    private val rewardsCacheStore = FetcherCacheStore(
        ConcurrentHashMap<Long, Pair<Instant, Data<RewardsAttributes>>>(),
        Config.cacheRewardsMaxSize
    )
    private val campaignsCacheStore = FetcherCacheStore(
        ConcurrentHashMap<Long, Pair<Instant, Data<CampaignAttributes>>>(),
        Config.cacheCampaignsMaxSize
    )
    private val allowCache = Config.useFetchCache

    companion object {
        @JvmStatic
        val logger = KotlinLogging.logger {}
    }

    fun startCacheEviction(context: CoroutineContext = Dispatchers.Default) = CoroutineScope(context).launch {
        delay(cacheEvictionPeriod)
        while (isActive) {
            evictCache()
            delay(cacheEvictionPeriod)
        }
    }

    private suspend fun removeInvalidCacheEntries(cacheStore: FetcherCacheStore<*>) = cacheStore.mutex.withLock {
        val cache = cacheStore.cache
        cache.removeAll(
            cache.mapNotNull { (key, value) ->
                if (isCacheValid(value.first)) null else key
            }
        )

        if (cache.size > cacheStore.maxSize) {
            // Evict oldest entries
            val cacheElements = cache.toList().sortedByDescending { it.second.first }
            cache.removeAll(
                cacheElements.subList(cacheStore.maxSize, cacheElements.size).map { it.first }
            )
        }
    }

    private suspend fun evictCache() {
        logger.debug { "Evicting rewards cache" }
        removeInvalidCacheEntries(rewardsCacheStore)
        logger.debug { "Evicting campaigns cache" }
        removeInvalidCacheEntries(campaignsCacheStore)
    }

    private val baseUri = "${Config.baseDomain}/api"

    private fun isCacheValid(cacheTime: Instant) = (Instant.now().epochSecond - cacheTime.epochSecond) < cacheValidity.seconds

    private fun rewardFromCache(rewardId: Long) = rewardsCacheStore.get(rewardId)?.takeIf { isCacheValid(it.first) }?.second

    private fun campaignFromCache(campaignId: Long) = campaignsCacheStore.get(campaignId)?.takeIf { isCacheValid(it.first) }?.second

    @Throws(RewardNotFoundException::class, RuntimeException::class)
    suspend fun checkAvailability(rewardId: Long) = let {
        logger.debug { "Checking availability for reward $rewardId" }
        // Since we are checking the CURRENT availability, we obviously have to skip the cache
        // The result will still be cached though, if the cache is enabled
        val result = fetchReward(rewardId, false)
        result.attributes.remaining to result
    }

    @Throws(RewardUnavailableException::class, RuntimeException::class)
    suspend fun fetchReward(rewardId: Long, useCache: Boolean = true) : Data<RewardsAttributes> {
        logger.debug { "Fetching reward $rewardId" }
        if (!this.allowCache) return fetchRewardInternal(rewardId).also {
            logger.trace { "Cache disabled globally, skipping" }
        }

        val cacheResponse = if (useCache) rewardFromCache(rewardId) else null
        logger.trace {
            if (useCache) {
                if (cacheResponse != null) "Cache hit for reward $rewardId" else "Cache miss for reward $rewardId"
            } else {
                "Cache disabled for current fetch for reward $rewardId"
            }
        }
        return cacheResponse ?: fetchRewardInternal(rewardId).apply { rewardsCacheStore.put(this) }
    }

    @Throws(RewardUnavailableException::class, RuntimeException::class)
    private suspend fun fetchRewardInternal(rewardId: Long) : Data<RewardsAttributes> {
        val result = httpClient.get("$baseUri/rewards/$rewardId")

        if (result.status == HttpStatusCode.NotFound) throw RewardNotFoundException("Reward $rewardId gave 404 Not Found", rewardId)
        if (result.status == HttpStatusCode.Forbidden) throw RewardForbiddenException("Access to reward $rewardId is forbidden", rewardId)
        if (result.status != HttpStatusCode.OK) throw RuntimeException("Received error while fetching reward $rewardId, status ${result.status}")

        return result.body<Response<RewardsAttributes>>().data
    }

    @Throws(CampaignUnavailableException::class, RuntimeException::class)
    suspend fun fetchCampaign(campaignId: Long, useCache: Boolean = true) : Data<CampaignAttributes> {
        logger.debug { "Fetching campaign $campaignId" }
        if (!this.allowCache) return fetchCampaignInternal(campaignId).also {
            logger.trace { "Cache disabled globally, skipping" }
        }

        val cacheResponse = if (useCache) campaignFromCache(campaignId) else null
        logger.trace {
            if (useCache) {
                if (cacheResponse != null) "Cache hit for campaign $campaignId" else "Cache miss for campaign $campaignId"
            } else {
                "Cache disabled for current fetch for campaign $campaignId"
            }
        }
        return cacheResponse ?: fetchCampaignInternal(campaignId).apply { campaignsCacheStore.put(this) }
    }

    @Throws(CampaignUnavailableException::class, RuntimeException::class)
    private suspend fun fetchCampaignInternal(campaignId: Long) : Data<CampaignAttributes> {
        val result = httpClient.get("$baseUri/campaigns/$campaignId")

        if (result.status == HttpStatusCode.NotFound) throw CampaignNotFoundException("Campaign $campaignId gave 404 Not Found", campaignId)
        if (result.status == HttpStatusCode.Forbidden) throw CampaignForbiddenException("Access to campaign $campaignId is forbidden", campaignId)
        if (result.status != HttpStatusCode.OK) throw RuntimeException("Received error while fetching campaing $campaignId, status ${result.status}")

        return result.body<Response<CampaignAttributes>>().data
    }

    @Throws(CampaignNotFoundException::class, RuntimeException::class)
    suspend fun fetchCampaign(rewardsData: Data<RewardsAttributes>)
        = fetchCampaign(rewardsData.relationships.campaign!!.data.id)
}