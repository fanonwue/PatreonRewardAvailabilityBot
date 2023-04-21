package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.exceptions.*
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.CampaignAttributes
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Response
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardsAttributes
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import mu.KotlinLogging

class PatreonFetcher {

    companion object {
        @JvmStatic
        val logger = KotlinLogging.logger {}
    }

    private val BASE_URI = "${Config.baseDomain}/api"
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Config.jsonSerializer)
        }
    }

    @Throws(RewardNotFoundException::class, RuntimeException::class)
    suspend fun checkAvailability(rewardId: Long) = let {
        logger.debug { "Checking availability for reward $rewardId" }
        val result = fetchReward(rewardId)
        result.attributes.remaining to result
    }



    @Throws(RewardUnavailableException::class, RuntimeException::class)
    suspend fun fetchReward(rewardId: Long) = let {
        logger.debug { "Fetching reward $rewardId" }
        val result = client.get("$BASE_URI/rewards/$rewardId")

        if (result.status == HttpStatusCode.NotFound) throw RewardNotFoundException("Reward $rewardId gave 404 Not Found", rewardId)
        if (result.status == HttpStatusCode.Forbidden) throw RewardForbiddenException("Access to reward $rewardId is forbidden", rewardId)
        if (result.status != HttpStatusCode.OK) throw RuntimeException("Received error while fetching reward $rewardId, status ${result.status}")

        result.body<Response<RewardsAttributes>>().data
    }

    @Throws(CampaignUnavailableException::class, RuntimeException::class)
    suspend fun fetchCampaign(campaignId: Long) = let {
        logger.debug { "Fetching campaign $campaignId" }
        val result = client.get("$BASE_URI/campaigns/$campaignId")

        if (result.status == HttpStatusCode.NotFound) throw CampaignNotFoundException("Campaign $campaignId gave 404 Not Found", campaignId)
        if (result.status == HttpStatusCode.Forbidden) throw CampaignForbiddenException("Access to campaign $campaignId is forbidden", campaignId)
        if (result.status != HttpStatusCode.OK) throw RuntimeException("Received error while fetching campaing $campaignId, status ${result.status}")

        result.body<Response<CampaignAttributes>>().data
    }

    @Throws(CampaignNotFoundException::class, RuntimeException::class)
    suspend fun fetchCampaign(rewardsData: Data<RewardsAttributes>)
        = fetchCampaign(rewardsData.relationships.campaign?.data!!.id)
}