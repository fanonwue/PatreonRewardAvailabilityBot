package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.model.RewardObservationList
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardsAttributes
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.util.concurrent.Executors

class App {

    companion object {
        @JvmStatic
        val logger = LoggerFactory.getLogger(this::class.java)
    }

    val updateJob : Job? = null
    private var supervisorJob = SupervisorJob()
    private val fetcher = PatreonFetcher()
    val botsApi = TelegramBotsApi(DefaultBotSession::class.java)

    val coroutineScope by lazy {
        val executor = Executors.newScheduledThreadPool(2)
        val context = executor.asCoroutineDispatcher() + supervisorJob
        CoroutineScope(context)
    }

    val notificationTelegramBot by lazy {
        NotificationTelegramBot(
            Config.telegramApiKey,
            "@patreon_rewards_bot",
            fetcher,
            coroutineScope
        )
    }

    fun setup() {
        logger.info("Starting Coroutine Scheduler")
        coroutineScope.launch {
            while (isActive) {
                RewardObservationList.rewardSet.map { id ->
                    launch {
                        try {
                            val result = fetcher.checkAvailability(id)
                            result.first?.let { remainingCount ->
                                if (remainingCount > 0) onRewardAvailability(result.second)
                            }
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }
                }.joinAll()
                delay(Config.interval.toMillis())
            }
        }
    }

    fun start() {
        botsApi.registerBot(notificationTelegramBot)
        supervisorJob?.start()
    }

    fun onRewardAvailability(reward: Data<RewardsAttributes>) {
        coroutineScope.launch {
            notificationTelegramBot.sendAvailabilityNotification(reward, fetcher.fetchCampaign(reward))
        }
    }
}