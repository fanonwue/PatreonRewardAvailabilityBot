package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.exceptions.*
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.time.delay
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

class PatreonFetcher(
    private val httpClient: HttpClient
) {
    companion object {
        const val PATREON_REQUEST_TIMEOUT = 30L * 1000L
        @JvmStatic
        val logger = KotlinLogging.logger {}
    }

    private val patreonHttptimeout: HttpTimeout.HttpTimeoutCapabilityConfiguration.() -> Unit = {
        requestTimeoutMillis = PATREON_REQUEST_TIMEOUT
    }

    private val cacheValidity = Config.cacheValidity
    private val cacheEvictionPeriod = Config.cacheEvictionPeriod
    private val rewardsCacheStore = FetcherCacheStore(
        ConcurrentHashMap<Long, Pair<Instant, RewardData>>(),
        Config.cacheRewardsMaxSize
    )
    private val campaignsCacheStore = FetcherCacheStore(
        ConcurrentHashMap<Long, Pair<Instant, CampaignData>>(),
        Config.cacheCampaignsMaxSize
    )
    private val allowCache = Config.useFetchCache

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
        fetchReward(rewardId, false)
    }

    @Throws(RewardUnavailableException::class, RuntimeException::class)
    suspend fun fetchReward(rewardId: Long, useCache: Boolean = true) : RewardData {
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
    private suspend fun fetchRewardInternal(rewardId: Long) : RewardData {
        val result = httpClient.get("$baseUri/rewards/$rewardId") {
            timeout(patreonHttptimeout)
        }

        if (result.status == HttpStatusCode.NotFound) throw RewardNotFoundException("Reward $rewardId gave 404 Not Found", rewardId)
        if (result.status == HttpStatusCode.Forbidden) throw RewardForbiddenException("Access to reward $rewardId is forbidden", rewardId)
        if (result.status != HttpStatusCode.OK) throw RuntimeException("Received error while fetching reward $rewardId, status ${result.status}")

        return result.body<Response<RewardsAttributes>>().data
    }

    @Throws(CampaignUnavailableException::class, RuntimeException::class)
    suspend fun fetchCampaign(campaignId: Long, useCache: Boolean = true) : CampaignData {
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
    private suspend fun fetchCampaignInternal(campaignId: Long) : CampaignData {
        val result = httpClient.get("$baseUri/campaigns/$campaignId") {
            timeout(patreonHttptimeout)
        }

        if (result.status == HttpStatusCode.NotFound) throw CampaignNotFoundException("Campaign $campaignId gave 404 Not Found", campaignId)
        if (result.status == HttpStatusCode.Forbidden) throw CampaignForbiddenException("Access to campaign $campaignId is forbidden", campaignId)
        if (result.status != HttpStatusCode.OK) throw RuntimeException("Received error while fetching campaing $campaignId, status ${result.status}")

        return result.body<Response<CampaignAttributes>>().data
    }

    @Throws(CampaignUnavailableException::class, RuntimeException::class)
    suspend fun fetchCampaign(rewardsData: Data<RewardsAttributes>) = let {
        val campaignId = rewardsData.relationships?.campaign?.data?.id
            ?: throw CampaignNotFoundException("Reward ${rewardsData.id} does not contain a relationship to a campaign")

        fetchCampaign(campaignId)
    }
}