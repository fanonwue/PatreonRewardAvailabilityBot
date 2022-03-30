package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.CampaignAttributes
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Response
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardsAttributes
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*

class PatreonFetcher {

    private val BASE_URI = "${Config.baseDomain}/api"
    val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Config.jsonSerializer)
        }
    }

    suspend fun checkAvailability(rewardId: Long) : Pair<Int?, Data<RewardsAttributes>> {
        val result = fetchReward(rewardId)
        return result.attributes.remaining to result
    }

    suspend fun fetchReward(rewardId: Long) = client.get<Response<RewardsAttributes>>("$BASE_URI/rewards/$rewardId").data

    suspend fun fetchCampaign(campaignId: Long)
        = client.get<Response<CampaignAttributes>>("$BASE_URI/campaigns/$campaignId").data

    suspend fun fetchCampaign(rewardsData: Data<RewardsAttributes>)
        = fetchCampaign(rewardsData.relationships.campaign?.data!!.id)
}