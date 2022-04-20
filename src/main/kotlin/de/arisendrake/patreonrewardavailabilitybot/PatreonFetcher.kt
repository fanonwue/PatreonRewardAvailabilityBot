package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.CampaignAttributes
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Response
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardsAttributes
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*

class PatreonFetcher {

    private val BASE_URI = "${Config.baseDomain}/api"
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Config.jsonSerializer)
        }
    }

    suspend fun checkAvailability(rewardId: Long) : Pair<Int?, Data<RewardsAttributes>> {
        val result = fetchReward(rewardId)
        return result.attributes.remaining to result
    }

    suspend fun fetchReward(rewardId: Long) = client.get("$BASE_URI/rewards/$rewardId").body<Response<RewardsAttributes>>().data

    suspend fun fetchCampaign(campaignId: Long)
        = client.get("$BASE_URI/campaigns/$campaignId").body<Response<CampaignAttributes>>().data

    suspend fun fetchCampaign(rewardsData: Data<RewardsAttributes>)
        = fetchCampaign(rewardsData.relationships.campaign?.data!!.id)
}