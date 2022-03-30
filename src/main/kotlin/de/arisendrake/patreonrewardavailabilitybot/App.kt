package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.model.RewardObservationList
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardsAttributes
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.InstantSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import java.time.Instant
import java.util.concurrent.Executors

class App {

    companion object {
        @JvmStatic
        val logger: Logger = LoggerFactory.getLogger(App::class.java)
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
                RewardObservationList.rewardMap.values.map { entry ->
                    logger.debug("Checking reward availability for reward $entry")
                    launch {
                        try {
                            val result = fetcher.checkAvailability(entry.id)
                            result.first?.let { remainingCount ->
                                if (remainingCount > 0) {
                                    logger.debug("$remainingCount slots for available for reward $entry")
                                    onRewardAvailability(result.second)
                                    availabilityCounter++
                                } else {
                                    entry.withLock { entry.availableSince = null }
                                    RewardObservationList.update(entry)
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

    fun onRewardAvailability(reward: Data<RewardsAttributes>) = runBlocking {
        RewardObservationList.rewardMap[reward.id]?.also { entry ->
            entry.withLock { if (entry.availableSince == null) entry.availableSince = Instant.now() }
            try {
                if (entry.lastNotified == null || entry.availableSince!!.isAfter(entry.lastNotified)) {
                    val message = notificationTelegramBot.sendAvailabilityNotification(reward, fetcher.fetchCampaign(reward))
                    message?.also { entry.lastNotified = Instant.now() }
                    logger.info("Notification for the availability of reward ${entry.id} sent at ${InstantSerializer.formatter.format(entry.lastNotified)}")
                } else {
                    logger.info("Notification for the availability of reward ${entry.id} has been sent already. Skipping.")
                }

            } finally {
                RewardObservationList.update(entry)
            }
        } ?: logger.warn("No RewardEntry found for rewardId ${reward.id}")
    }
}