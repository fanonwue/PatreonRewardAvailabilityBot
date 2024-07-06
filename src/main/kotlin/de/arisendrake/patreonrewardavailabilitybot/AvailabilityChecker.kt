package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.exceptions.*
import de.arisendrake.patreonrewardavailabilitybot.model.RewardAction
import de.arisendrake.patreonrewardavailabilitybot.model.RewardActionType
import de.arisendrake.patreonrewardavailabilitybot.model.RewardCheckResult
import de.arisendrake.patreonrewardavailabilitybot.model.RewardEntry
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries
import de.arisendrake.patreonrewardavailabilitybot.telegram.TelegramBot
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.withSuspendTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

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

        val rewardCheckResults = channelFlow {
            val processing = newSuspendedTransaction(Config.dbContext) {
                val idCol = RewardEntries.rewardId
                RewardEntries.select(idCol).withDistinct().map {
                    it[idCol]
                }
            }.map { rewardId ->
                delay(50)
                logger.debug { "Starting availability check for reward $rewardId" }
                async { checkReward(rewardId).also {
                    logger.debug { "Availability check resolved for reward $rewardId" }
                    send(it)
                } }
            }

            // Wait for all deferred objects to resolve before closing the channel
            processing.awaitAll()
            close()
        }.filterNotNull()
        .toList()

        val rewardActions = newSuspendedTransaction(Config.dbContext) { rewardCheckResults.mapNotNull {
            handleResult(it)
        } }.flatten()

        bot.handleRewardActions(rewardActions)

        val availableRewards = rewardActions.filter { it.actionType == RewardActionType.NOTIFY_AVAILABLE }
        logger.info { "${availableRewards.size} available rewards found" }
    }

    private suspend fun checkReward(rewardId: Long) : RewardCheckResult {
        logger.debug { "Checking reward availability for reward $rewardId" }
        return try {
            val fetchResult = fetcher.checkAvailability(rewardId)
            RewardCheckResult(
                rewardId,
                fetchResult
            )
        } catch (e: RuntimeException) {
            RewardCheckResult(
                rewardId, null, e
            )
        }
    }

    private suspend fun Transaction.handleResult(result: RewardCheckResult) = withSuspendTransaction {
        val error = result.error

        // Skip handling when nothing is available and no errors occurred
        // Reset flags to default though
        // Configurable via Config, defaults to false
        if (Config.skipRewardEntryCheckIfEmptyAndNoError && result.available == 0 && error == null) {
            RewardEntries.update({ RewardEntries.rewardId eq result.rewardId }) {
                it[isMissing] = false
                it[availableSince] = null
                it[lastNotified] = null
            }
            return@withSuspendTransaction null
        }

        RewardEntry.find { RewardEntries.rewardId eq result.rewardId }.mapNotNull { entry ->
            var action: RewardAction? = null
            // Handle errors
            if (error != null) {
                when(error) {
                    is RewardNotFoundException -> {
                        logger.warn { error.message ?: "Reward ${entry.id} not found" }
                        if (Config.removeMissingRewards) {
                            logger.info { "Removing missing reward ${entry.id} from the rewards list" }
                            entry.delete()
                        } else if (Config.notifyOnMissingRewards && !entry.isMissing) {
                            logger.info { "Notifying user ${entry.chat.id.value} of missing reward ${entry.id}" }
                            action = RewardAction(entry.chat.id.value, entry, RewardActionType.NOTIFY_MISSING)
                        }
                        entry.isMissing = true
                    }
                    is RewardForbiddenException -> {
                        logger.warn { error.message ?: "Access to reward ${entry.id} is forbidden" }
                        if (Config.notifyOnForbiddenRewards && !entry.isMissing) {
                            logger.info { "Notifying user ${entry.chat.id.value} of reward ${entry.id} with forbidden access" }
                            action = RewardAction(entry.chat.id.value, entry, RewardActionType.NOTIFY_FORBIDDEN)
                        }
                        entry.isMissing = true
                    }
                }

                return@mapNotNull action
            }

            // It's not missing
            entry.isMissing = false

            if (result.available > 0) {
                logger.debug { "${result.available} slots for available for reward $entry" }
                handleAvailableReward(result, entry)
            } else {
                entry.lastNotified = null
                entry.availableSince = null
                null
            }
        }
    }

    private suspend inline fun Transaction.handleAvailableReward(result: RewardCheckResult, entry: RewardEntry) = withSuspendTransaction {
        val rewardData = result.rewardData
        if (rewardData == null) {
            logger.warn { "Reward data empty in handleAvailableReward()" }
            return@withSuspendTransaction null
        }

        if (entry.availableSince == null) entry.availableSince = Instant.now()

        try {
            if (entry.lastNotified == null || entry.availableSince!!.isAfter(entry.lastNotified)) {
                return@withSuspendTransaction RewardAction(
                    entry.chat.id.value,
                    entry,
                    RewardActionType.NOTIFY_AVAILABLE,
                    rewardData,
                    fetcher.fetchCampaign(rewardData)
                )
            } else {
                logger.info { "Notification for the availability of reward ${entry.id} has been sent already. Skipping." }
            }

        } catch (e: CampaignUnavailableException) {
            logger.warn { e.message ?: "Campaign for reward ${entry.id} not found" }
        } catch (t: Throwable) {
            logger.error(t) { "An Error occured!" }
        }

        null
    }

}