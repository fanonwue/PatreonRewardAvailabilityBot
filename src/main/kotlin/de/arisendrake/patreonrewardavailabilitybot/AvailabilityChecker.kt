package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.exceptions.CampaignNotFoundException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.RewardForbiddenException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.RewardNotFoundException
import de.arisendrake.patreonrewardavailabilitybot.model.RewardAction
import de.arisendrake.patreonrewardavailabilitybot.model.RewardActionType
import de.arisendrake.patreonrewardavailabilitybot.model.RewardEntry
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardData
import io.github.oshai.KotlinLogging
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.withSuspendTransaction
import java.time.Instant
import kotlin.coroutines.CoroutineContext

class AvailabilityChecker(
    private val fetcher: PatreonFetcher,
    private val bot: TelegramBot
) {
    
    companion object {
        @JvmStatic
        val logger = KotlinLogging.logger {  }
    }
    
    fun check() = runBlocking {
        logger.info { "Checking reward availability..." }

        val availableRewards = newSuspendedTransaction(Config.dbContext) {
            RewardEntry.all().map {
                async { doAvailabilityCheck(it) }
            }.awaitAll().filterNotNull()
        }

        logger.debug { "${availableRewards.size} available rewards found" }
    }

    private suspend inline fun Transaction.doAvailabilityCheck(entry: RewardEntry) = withSuspendTransaction {
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
                        bot.sendMissingRewardNotification(entry)
                    }
                    entry.isMissing = true
                }
                is RewardForbiddenException -> {
                    logger.warn { it.message ?: "Access to reward ${entry.id} is forbidden" }
                    if (Config.notifyOnForbiddenRewards && !entry.isMissing) {
                        logger.info { "Notifying user ${entry.chat.id.value} of reward ${entry.id} with forbidden access" }
                        RewardAction(entry.chat.id.value, entry, RewardActionType.NOTIFY_FORBIDDEN)
                    }
                    entry.isMissing = true
                }
                else -> logger.error(it) { "An Error occured!" }
            }
            null
        }
    }

    private suspend inline fun Transaction.onRewardAvailability(reward: RewardData) = withSuspendTransaction {
        RewardEntry.find { RewardEntries.rewardId eq reward.id }.firstOrNull()?.let { entry ->
            if (entry.availableSince == null) entry.availableSince = Instant.now()
            try {
                if (entry.lastNotified == null || entry.availableSince!!.isAfter(entry.lastNotified)) {
                    logger.info {
                        "Notification for the availability of reward ${entry.id} will be sent to chat ${entry.chat.id.value}"
                    }
                    return@withSuspendTransaction RewardAction(
                        entry.chat.id.value,
                        entry,
                        RewardActionType.NOTIFY_AVAILABLE,
                        reward,
                        fetcher.fetchCampaign(reward)
                    )
                } else {
                    logger.info { "Notification for the availability of reward ${entry.id} has been sent already. Skipping." }
                }

            } catch (e: CampaignNotFoundException) {
                logger.warn { e.message ?: "Campaign for reward ${entry.id} not found" }
            } catch (t: Throwable) {
                logger.error(t) { "An Error occured!" }
            }
        } ?: logger.warn { "No RewardEntry found for rewardId ${reward.id}" }
        null
    }

}