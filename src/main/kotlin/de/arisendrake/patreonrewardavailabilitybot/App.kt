package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.exceptions.CampaignNotFoundException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.RewardForbiddenException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.RewardNotFoundException
import de.arisendrake.patreonrewardavailabilitybot.model.RewardEntry
import de.arisendrake.patreonrewardavailabilitybot.model.db.DbHelper
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardsAttributes
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.InstantSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransaction
import java.time.Instant

object App {

    @JvmStatic
    private val logger = KotlinLogging.logger {  }

    private val fetcher = PatreonFetcher()

    private val scope by lazy {
        CoroutineScope(Dispatchers.Default)
    }

    private val context get() = scope.coroutineContext

    private val notificationTelegramBot by lazy {
        TelegramBot(
            Config.telegramApiKey,
            fetcher
        )
    }

    fun run() {
        DbHelper.db

        logger.info { "Starting Telegram Bot" }
        scope.launch { notificationTelegramBot.start() }

        logger.info {"""
            Starting coroutine scheduler with an interval of ${Config.interval.toSeconds()}s and with an initial
            delay of ${Config.initialDelay.toSeconds()}s
        """.trimIndent().withoutNewlines()
        }

        runBlocking(context) {
            delay(Config.initialDelay)
            while (isActive) {
                logger.info { "Checking reward availability" }


                val availableRewards = newSuspendedTransaction {
                    RewardEntry.all().map {
                        async { doAvailabilityCheck(it) }
                    }.awaitAll().filterNotNull()
                }

                logger.info { "${availableRewards.size} available rewards found" }
                delay(Config.interval)
            }
        }
    }

    private suspend fun Transaction.doAvailabilityCheck(entry: RewardEntry) : RewardEntry? = suspendedTransaction {
        logger.debug { "Checking reward availability for reward $entry" }
        try {
            val result = fetcher.checkAvailability(entry.rewardId)
            // Reward was found, so it's not missing
            entry.isMissing = false
            result.first?.let { remainingCount ->
                if (remainingCount > 0) {
                    logger.debug { "$remainingCount slots for available for reward $entry" }
                    onRewardAvailability(result.second)
                    entry
                } else {
                    entry.lastNotified = null
                    entry.availableSince = null
                }
            }
        } catch (e: RewardNotFoundException) {
            logger.warn { e.message ?: "Reward ${entry.id} not found" }
            if (Config.removeMissingRewards) {
                logger.info { "Removing missing reward ${entry.id} from the rewards list" }
                entry.delete()
            } else if (Config.notifyOnMissingRewards && !entry.isMissing) {
                logger.info { "Notifying user of missing reward ${entry.id}" }
                notificationTelegramBot.sendMissingRewardNotification(entry)
            }
            entry.isMissing = true
        } catch (e: RewardForbiddenException) {
            logger.warn { e.message ?: "Access to reward ${entry.id} is forbidden" }
            if (Config.notifyOnForbiddenRewards && !entry.isMissing) {
                logger.info { "Notifying user of reward ${entry.id} with forbidden access" }
                notificationTelegramBot.sendForbiddenRewardNotification(entry)
            }
            entry.isMissing = true
        } catch (t: Throwable) {
            logger.error(t) { "An Error occured!" }
        }
        null
    }

    private suspend fun Transaction.onRewardAvailability(reward: Data<RewardsAttributes>) = suspendedTransaction {
        RewardEntry.find { RewardEntries.rewardId eq reward.id }.firstOrNull()?.let { entry ->
            if (entry.availableSince == null) entry.availableSince = Instant.now()
            try {
                if (entry.lastNotified == null || entry.availableSince!!.isAfter(entry.lastNotified)) {
                    val message =
                        notificationTelegramBot.sendAvailabilityNotification(entry.chat.id.value, reward, fetcher.fetchCampaign(reward))
                    message?.also { entry.lastNotified = Instant.now() }
                    logger.info {
                        "Notification for the availability of reward ${entry.id} sent at ${
                            InstantSerializer.formatter.format(
                                entry.lastNotified
                            )
                        }"
                    }
                } else {
                    logger.info { "Notification for the availability of reward ${entry.id} has been sent already. Skipping." }
                }

            } catch (e: CampaignNotFoundException) {
                logger.warn { e.message ?: "Campaign for reward ${entry.id} not found" }
            } catch (t: Throwable) {
                logger.error(t) { "An Error occured!" }
            }
        } ?: logger.warn { "No RewardEntry found for rewardId ${reward.id}" }
    }
}