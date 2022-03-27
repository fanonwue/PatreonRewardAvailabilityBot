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
        val logger = LoggerFactory.getLogger(App::class.java)
    }

    val updateJob : Job? = null
    private var supervisorJob = SupervisorJob()
    private val fetcher = PatreonFetcher()
    private val botsApi = TelegramBotsApi(DefaultBotSession::class.java)

    private val coroutineScope by lazy {
        val executor = Executors.newScheduledThreadPool(2)
        val context = executor.asCoroutineDispatcher() + supervisorJob
        CoroutineScope(context)
    }

    private val notificationTelegramBot by lazy {
        NotificationTelegramBot(
            Config.telegramApiKey,
            "@patreon_rewards_bot",
            fetcher,
            coroutineScope
        )
    }

    fun setup() {
        logger.info("""
            Starting coroutine scheduler with an interval of ${Config.interval.toSeconds()}s and with an initial
            delay of ${Config.initialDelay.toSeconds()}s
        """.trimIndent().withoutNewlines())
        coroutineScope.launch {
            while (isActive) {
                logger.info("Checking reward availability")
                var availabilityCounter = 0
                RewardObservationList.rewardSet.map { id ->
                    logger.debug("Checking reward availability for reward $id")
                    launch {
                        try {
                            val result = fetcher.checkAvailability(id)
                            result.first?.let { remainingCount ->
                                if (remainingCount > 0) {
                                    logger.debug("$remainingCount slots for available for reward $id")
                                    onRewardAvailability(result.second)
                                    availabilityCounter++
                                }
                            }
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }
                }.joinAll()
                logger.info("$availabilityCounter available rewards found")
                delay(Config.interval.toMillis())
            }
        }
    }

    fun start() {
        botsApi.registerBot(notificationTelegramBot)
        supervisorJob.start()
    }

    fun onRewardAvailability(reward: Data<RewardsAttributes>) {
        coroutineScope.launch {
            notificationTelegramBot.sendAvailabilityNotification(reward, fetcher.fetchCampaign(reward))
        }
    }
}