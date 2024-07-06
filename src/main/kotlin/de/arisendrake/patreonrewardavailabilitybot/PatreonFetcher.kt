package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.exceptions.*
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.*
import kotlin.coroutines.CoroutineContext

class PatreonFetcher(
    private val httpClient: HttpClient
) {
    companion object {
        const val PATREON_REQUEST_TIMEOUT = 30L * 1000L
        @JvmStatic
        val logger = KotlinLogging.logger {}
    }

    private val patreonHttpTimeout: HttpTimeout.HttpTimeoutCapabilityConfiguration.() -> Unit = {
        requestTimeoutMillis = PATREON_REQUEST_TIMEOUT
    }

    private val cacheEvictionPeriod = Config.cacheEvictionPeriod
    private val rewardsCacheStore = FetcherCacheStore<RewardData>(
        Config.cacheRewardsMaxSize,
        Config.cacheValidity
    )
    private val campaignsCacheStore = FetcherCacheStore<CampaignData>(
        Config.cacheCampaignsMaxSize,
        Config.cacheValidity
    )
    private val allowCache = Config.useFetchCache

    fun startCacheEviction(context: CoroutineContext = Dispatchers.Default) = CoroutineScope(context).launch {
        delay(cacheEvictionPeriod)
        while (isActive) {
            evictCache()
            delay(cacheEvictionPeriod)
        }
    }

    private fun evictCache() {
        logger.debug { "Evicting rewards cache" }
        rewardsCacheStore.removeInvalidCacheEntries()
        logger.debug { "Evicting campaigns cache" }
        campaignsCacheStore.removeInvalidCacheEntries()
    }

    private val baseUri = Config.patreonBaseDomain.resolve("/api/")

    private fun rewardFromCache(rewardId: RewardId) = rewardsCacheStore.getValueIfValid(rewardId.id)

    private fun campaignFromCache(campaignId: CampaignId) = campaignsCacheStore.getValueIfValid(campaignId.id)

    @Throws(RewardNotFoundException::class, RuntimeException::class)
    suspend fun checkAvailability(rewardId: Long) = let {
        logger.debug { "Checking availability for reward $rewardId" }
        // Since we are checking the CURRENT availability, we obviously have to skip the cache
        // The result will still be cached though, if the cache is enabled
        fetchReward(rewardId, false)
    }

    @Throws(RewardUnavailableException::class, RuntimeException::class)
    suspend inline fun fetchReward(rewardId: Long, useCache: Boolean = true) = fetchReward(rewardId.asRewardId(), useCache)

    @Throws(RewardUnavailableException::class, RuntimeException::class)
    suspend fun fetchReward(rewardId: RewardId, useCache: Boolean = true) : RewardData {
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
    private suspend fun fetchRewardInternal(rewardId: RewardId) : RewardData {
        val result = httpClient.get(rewardId.apiUrl()) {
            timeout(patreonHttpTimeout)
        }

        if (result.status == HttpStatusCode.NotFound) throw RewardNotFoundException("Reward $rewardId gave 404 Not Found", rewardId)
        if (result.status == HttpStatusCode.Forbidden) throw RewardForbiddenException("Access to reward $rewardId is forbidden", rewardId)
        if (result.status != HttpStatusCode.OK) throw RuntimeException("Received error while fetching reward $rewardId, status ${result.status}")

        return result.body<Response<RewardsAttributes>>().data as RewardData
    }

    @Throws(RewardUnavailableException::class, RuntimeException::class)
    suspend inline fun fetchCampaign(campaignId: Long, useCache: Boolean = true) = fetchCampaign(campaignId.asCampaignId(), useCache)

    @Throws(CampaignUnavailableException::class, RuntimeException::class)
    suspend fun fetchCampaign(campaignId: CampaignId, useCache: Boolean = true) : CampaignData {
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
    private suspend fun fetchCampaignInternal(campaignId: CampaignId) : CampaignData {
        val result = httpClient.get(campaignId.apiUrl()) {
            timeout(patreonHttpTimeout)
        }

        if (result.status == HttpStatusCode.NotFound) throw CampaignNotFoundException("Campaign $campaignId gave 404 Not Found", campaignId)
        if (result.status == HttpStatusCode.Forbidden) throw CampaignForbiddenException("Access to campaign $campaignId is forbidden", campaignId)
        if (result.status != HttpStatusCode.OK) throw RuntimeException("Received error while fetching campaign $campaignId, status ${result.status}")

        return result.body<Response<CampaignAttributes>>().data as CampaignData
    }

    @Throws(CampaignUnavailableException::class, RuntimeException::class)
    suspend fun fetchCampaign(rewardsData: RewardData) = let {
        val campaignId = rewardsData.relationships?.campaign?.data?.id
            ?: throw CampaignNotFoundException("Reward ${rewardsData.id} does not contain a relationship to a campaign")

        fetchCampaign(campaignId)
    }

    private fun RewardId.apiUrl() = baseUri.resolve("rewards/${this.id}").toURL()
    private fun CampaignId.apiUrl() = baseUri.resolve("campaigns/${this.id}").toURL()
}