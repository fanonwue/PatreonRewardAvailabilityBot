package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.exceptions.CampaignNotFoundException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.RewardForbiddenException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.RewardNotFoundException
import de.arisendrake.patreonrewardavailabilitybot.model.RewardEntry
import de.arisendrake.patreonrewardavailabilitybot.model.db.DbHelper
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardData
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardsAttributes
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.InstantSerializer
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import io.github.oshai.KotlinLogging
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransaction
import java.time.Instant

object App {

    @JvmStatic
    private val logger = KotlinLogging.logger {  }

    private val fetcher = PatreonFetcher(Config.sharedHttpClient)

    private val scope by lazy {
        CoroutineScope(Dispatchers.Default)
    }

    private val context get() = scope.coroutineContext

    private val notificationTelegramBot by lazy {
        TelegramBot(
            Config.telegramApiKey,
            fetcher,
            Config.sharedHttpClient
        )
    }

    fun run() {
        DbHelper.db

        logger.info { "Starting Telegram Bot" }
        notificationTelegramBot.start()

        if (Config.useFetchCache) {
            logger.info { "Starting cache eviction background job, cache validity is at ${Config.cacheValidity.seconds}s" }
            fetcher.startCacheEviction()
        } else {
            logger.info { "Fetch cache disabled globally, skipping eviction job scheduling" }
        }

        logger.info {"""
            Starting coroutine scheduler with an interval of ${Config.interval.seconds}s and with an initial
            delay of ${Config.initialDelay.seconds}s
        """.trimIndent().withoutNewlines()
        }

        runBlocking {
            delay(Config.initialDelay)
            while (isActive) {
                logger.info { "Checking reward availability..." }

                val availableRewards = newSuspendedTransaction(Config.dbContext) {
                    RewardEntry.all().map {
                        async(context) { doAvailabilityCheck(it) }
                    }.awaitAll().filterNotNull()
                }

                logger.debug { "${availableRewards.size} available rewards found" }
                delay(Config.interval)
            }
        }
    }

    private suspend inline fun Transaction.doAvailabilityCheck(entry: RewardEntry) = suspendedTransaction {
        logger.debug { "Checking reward availability for reward ${entry.id.value} (internal ID)" }
        runCatching {
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
                    null
                }
            }
        }.getOrElse {
            when (it) {
                is RewardNotFoundException -> {
                    logger.warn { it.message ?: "Reward ${entry.id} not found" }
                    if (Config.removeMissingRewards) {
                        logger.info { "Removing missing reward ${entry.id} from the rewards list" }
                        entry.delete()
                    } else if (Config.notifyOnMissingRewards && !entry.isMissing) {
                        logger.info { "Notifying user ${entry.chat.id.value} of missing reward ${entry.id}" }
                        notificationTelegramBot.sendMissingRewardNotification(entry)
                    }
                    entry.isMissing = true
                }
                is RewardForbiddenException -> {
                    logger.warn { it.message ?: "Access to reward ${entry.id} is forbidden" }
                    if (Config.notifyOnForbiddenRewards && !entry.isMissing) {
                        logger.info { "Notifying user ${entry.chat.id.value} of reward ${entry.id} with forbidden access" }
                        notificationTelegramBot.sendForbiddenRewardNotification(entry)
                    }
                    entry.isMissing = true
                }
                else -> logger.error(it) { "An Error occured!" }
            }
            null
        }
    }

    private suspend inline fun Transaction.onRewardAvailability(reward: RewardData) = suspendedTransaction {
        RewardEntry.find { RewardEntries.rewardId eq reward.id }.firstOrNull()?.let { entry ->
            if (entry.availableSince == null) entry.availableSince = Instant.now()
            try {
                if (entry.lastNotified == null || entry.availableSince!!.isAfter(entry.lastNotified)) {
                    notificationTelegramBot.sendAvailabilityNotification(entry.chat.id.value, reward, fetcher.fetchCampaign(reward))
                    entry.lastNotified = Instant.now()
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