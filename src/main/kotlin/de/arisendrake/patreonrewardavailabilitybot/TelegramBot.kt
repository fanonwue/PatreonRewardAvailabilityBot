package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.exceptions.CampaignUnavailableException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.RewardUnavailableException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.UnavailabilityReason
import de.arisendrake.patreonrewardavailabilitybot.model.Chat
import de.arisendrake.patreonrewardavailabilitybot.model.RewardAction
import de.arisendrake.patreonrewardavailabilitybot.model.RewardActionType
import de.arisendrake.patreonrewardavailabilitybot.model.RewardEntry
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries.chat
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.*
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.InstantSerializer
import dev.inmo.tgbotapi.abstracts.WithChat
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.sendActionTyping
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.extensions.api.telegramBot
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.CommonMessageFilter
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.utils.fromUserMessageOrNull
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.message.MarkdownParseMode
import dev.inmo.tgbotapi.types.message.abstracts.Message
import dev.inmo.tgbotapi.types.toChatId
import io.ktor.client.*
import kotlinx.coroutines.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext

class TelegramBot(
    apiKey: String,
    private val fetcher: PatreonFetcher,
    httpClient: HttpClient
) {
    companion object {
        private const val lineSeparator = "\n-----------------------------------------\n"
        @JvmStatic
        private val logger = KotlinLogging.logger {  }
        private val defaultLocale = Config.defaultLocale
    }

    init {
        logger.debug { "Using API Key '$apiKey'" }
        logger.debug { "Creator ID is ${Config.telegramCreatorId}" }
    }

    private val bot = telegramBot(
        apiKey,
        client = httpClient
    )
    private val creatorId = Config.telegramCreatorId.toChatId()
    private val messageFilterCreatorOnly = CommonMessageFilter<Any> {
        it.chat.id == creatorId
    }

    // TODO implement correctly
    suspend fun handleRewardActions(actions: Iterable<RewardAction>) = coroutineScope {
        actions.map { async {
            handleRewardAction(it)
        } }.awaitAll()
    }

    suspend fun handleRewardAction(action: RewardAction) = coroutineScope {
        logger.debug { "Handling action for reward ${action.rewardId} of type ${action.actionType}" }
        when(action.actionType) {
            RewardActionType.NOTIFY_AVAILABLE -> {
                AvailabilityChecker.logger.info {
                    "Notification for the availability of reward ${action.rewardId} will be sent to chat ${action.chatId}"
                }
                val now = Instant.now()
                sendAvailabilityNotification(action.chatId, action.rewardData!!, action.campaignData!!)
                newSuspendedTransaction(Config.dbContext) {
                    action.rewardEntry.lastNotified = now
                }
                logger.info {
                    "Notification for the availability of reward ${action.rewardId} sent at ${
                        InstantSerializer.formatter.format(
                            now
                        )
                    }"
                }

            }
            RewardActionType.NOTIFY_MISSING -> sendMissingRewardNotification(
                action.chatId,
                action.rewardId
            )
            RewardActionType.NOTIFY_FORBIDDEN -> sendForbiddenRewardNotification(
                action.chatId,
                action.rewardId
            )
            else -> logger.debug { "Received action type ${action.actionType.name}, will be ignored by the bot" }
        }
    }
    
    suspend fun sendAvailabilityNotification(
        chatId: Long,
        reward: RewardData,
        campaign: CampaignData
    ) {
        bot.sendActionTyping(chatId.toChatId())
        val locale = localeForChat(chatId)
        val ca = campaign.attributes
        val ra = reward.attributes
        val text =
            """
                New Reward available for [${ca.name}](${ca.url})!
                
                Name: 
                *${ra.title}*
                Cost: 
                *${ra.formattedAmountCurrency(locale)}*
                ID:
                ${reward.id}
                
                ([Join now!](${ra.fullUrl}))
            """.trimIndent()
        bot.sendTextMessage(chatId.toChatId(), text, MarkdownParseMode)
    }

    suspend fun sendMissingRewardNotification(entry: RewardEntry) = newSuspendedTransaction {
        sendMissingRewardNotification(entry.chat.id.value, entry.rewardId)
    }

    @SuppressWarnings("WeakerAccess")
    suspend fun sendMissingRewardNotification(chatId: Long, rewardId: Long) = bot.sendTextMessage(
        chatId.toChatId(),
        "WARNING: Reward with ID ${rewardId} could not be found. It may have been removed."
    )

    suspend fun sendForbiddenRewardNotification(entry: RewardEntry) = newSuspendedTransaction {
        sendForbiddenRewardNotification(entry.chat.id.value, entry.rewardId)
    }

    @SuppressWarnings("WeakerAccess")
    suspend fun sendForbiddenRewardNotification(chatId: Long, rewardId: Long) = bot.sendTextMessage(
        chatId.toChatId(),
        "WARNING: Access to reward with ID ${rewardId} is forbidden. It may have been removed."
    )

    fun start(context: CoroutineContext = Dispatchers.IO) = CoroutineScope(context).launch { startInternal(this) }
    
    private suspend fun startInternal(scope: CoroutineScope) = bot.buildBehaviourWithLongPolling(scope, timeoutSeconds = 60) {
        val botCommandList = mutableListOf<BotCommand>()
        val addToCommandList: BotCommand.() -> Unit = { botCommandList.add(this) }

        onCommandWithArgs(BotCommand("add",
            "Adds a reward ID to the list of observed rewards"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) {message, args ->
            sendActionTyping(message.chat.id)
            val rewardIds = parseIdList(args)
            if (rewardIds.isEmpty()) {
                reply(message, "One or multiple Rewards IDs expected as arguments")
                return@onCommandWithArgs
            }
            
            newSuspendedTransaction(Config.dbContext) {
                // Preload current chat with rewardEntries already loaded to avoid N+1 problems
                val currentChat = currentChatWithRewardEntries(message)
                val currentRewardIds = currentChat.rewardEntries.map { it.rewardId }
                val uniqueNewIds = rewardIds.filter { it !in currentRewardIds }

                if (uniqueNewIds.isEmpty()) {
                    reply(message, "All IDs have been added already. No new ID has been added.")
                    return@newSuspendedTransaction
                }

                val addedRewards = uniqueNewIds.associateWith { newId ->
                    RewardEntries.insertAndGetId {
                        it[this.chat] = currentChat.id
                        it[this.rewardId] = newId
                    }
                }

                val rewardsNotAdded = uniqueNewIds.filter { it !in addedRewards }

                reply(message, "Reward IDs [${addedRewards.map { it.key }.joinToString(", ")}] successfully added.".let {
                    val sb = StringBuilder(it)

                    if (rewardIds.size > addedRewards.size) {
                        sb.append("\n")
                        sb.append("Some IDs have been added already and were filtered out.")
                    }

                    if (rewardsNotAdded.isNotEmpty()) {
                        sb.append("\n")
                        sb.append("The following IDs were not added to the database: [${rewardsNotAdded.joinToString(", ")}]")
                    }

                    sb.toString()
                })
            }

        }

        onCommandWithArgs(BotCommand("add_campaign",
            "Retrieve all available rewards for specified reward campaign and allow the User to select a reward"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) { message, args ->
            sendActionTyping(message.chat.id)
            onCampaignAddCommand(message, args)
        }

        onCommandWithArgs(BotCommand("remove",
            "Removes a reward ID from the list of observed rewards"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) {message, args ->
            sendActionTyping(message.chat.id)
            val rewardIds = parseIdList(args)
            if (rewardIds.isEmpty()) {
                reply(message, "One or multiple Rewards IDs expected as arguments")
                return@onCommandWithArgs
            }

            newSuspendedTransaction(Config.dbContext) {
                RewardEntries.deleteWhere {
                    (chat eq message.chat.id.chatId) and (rewardId inList rewardIds)
                }
            }

            reply(message, "Reward IDs [${rewardIds.joinToString(", ")}] successfully removed.")
        }

        onCommandWithArgs(BotCommand("remove_campaign",
            "Removes all rewards associated with the specified campaign"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) {message, args ->
            sendActionTyping(message.chat.id)
            val campaignIds = parseIdList(args)
            if (campaignIds.isEmpty()) {
                reply(message, "One or multiple Campaign IDs expected as arguments")
                return@onCommandWithArgs
            }

            val unavailableCampaigns = mutableMapOf<Long, UnavailabilityReason>()

            val rewardIds = campaignIds.map { campaignId -> async { runCatching {
                fetcher.fetchCampaign(campaignId)
            }.getOrElse { e ->
                if (e is CampaignUnavailableException) unavailableCampaigns[campaignId] = e.unavailabilityReason
                null
            } } }.awaitAll().asSequence().filterNotNull().map {
                it.relationships?.rewards?.data
            }.filterNotNull().flatten().map { it.id }.filter { it > 0 }.toSet()

            val removedRewardIds = newSuspendedTransaction(Config.dbContext) {
                // Preload
                val currentChat = currentChatWithRewardEntries(message)
                val rewardEntriesToRemove = currentChat.rewardEntries.filter {
                    it.rewardId in rewardIds
                }

                // Using delete() on each entity here would create n delete queries, whereas this only creates a single query
                RewardEntries.deleteWhere { id inList rewardEntriesToRemove.map { it.id } }

                rewardEntriesToRemove.map { it.rewardId }
            }

            if (removedRewardIds.isNotEmpty()) {
                reply(message, "Reward IDs [${removedRewardIds.joinToString(", ")}] successfully removed.")
            } else {
                reply(message, "No observed rewards corresponding to any of the specified campaign IDs found, nothing got removed.")
            }

            if (unavailableCampaigns.isNotEmpty()) sendTextMessage(message.chat,
                "The following campaigns are unavailable:\n\n" + unavailableResourcesToString(unavailableCampaigns))
        }
        
        onCommand(BotCommand("reset_notifications",
            "Resets notifications for all rewards, so you'll be notified again about rewards that are still available"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) { message ->
            sendActionTyping(message.chat.id)
            val msg = reply(message, "Resetting last notification timestamps...")
            newSuspendedTransaction(Config.dbContext) {
                RewardEntries.update({chat eq message.chat.id.chatId}, null) {
                    it[lastNotified] = null
                }
            }
            editMessageText(msg, "Timestamps reset!")
        }
        
        onCommand(BotCommand("list",
            "Shows a list of all currently tracked rewards"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) { message ->
            sendActionTyping(message.chat.id)
            onListCommand(message)
        }

        onCommandWithArgs(BotCommand("language",
            "Sets the language that should be used. For now, this only includes number formatting, sorry!"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) {message, args ->
            sendActionTyping(message.chat)

            if (args.isEmpty()) {
                val locale = localeForCurrentChat(message)
                reply(message, "Language is currently set to \"${locale.displayName}\"")
                return@onCommandWithArgs
            }

            if (args.size != 1) {
                reply(message, "Exactly one argument (an IETF BCP 47 language code) is expected")
                return@onCommandWithArgs
            }

            val code = args.first().trim()
            if (code.length != 2) {
                reply(message, "An IETF BCP 47 language tag must be 2 characters long")
                return@onCommandWithArgs
            }

            val locale = Locale.forLanguageTag(code)
            newSuspendedTransaction(Config.dbContext) {
                currentChat(message).locale = locale
            }

            reply(message, "Language has been successfully set to \"${locale.displayName}\"")
        }

        onCommand(BotCommand("start",
            "Start interaction with bot"
        ).apply(addToCommandList)) {
            val chatId = it.chat.id.chatId

            if (Config.creatorOnlyAccess && chatId != creatorId.chatId) {
                reply(it, """
                    Sorry, this bot is currently not available to the public.
                    If you are interested in using it, contact this bot's creator!
                    (See bio for contact info ❤️)
                """.trimIndent())
                return@onCommand
            }

            val newlyCreated = newSuspendedTransaction(Config.dbContext) {
                Chat.findById(chatId)?.let { false } ?: Chat.new(chatId) {  }.let { true }
            }

            if (!newlyCreated) return@onCommand

            reply(it,
                "Welcome to the Patreon Rewards Availability Bot, ${it.fromUserMessageOrNull()?.user?.username?.username}"
            )
            logger.info { "Added new chat $chatId" }
        }

        setMyCommands(botCommandList)

//        onPhoto {
//            val bytes = downloadFile(it.content)
//            runCatchingSafely { setChatPhoto(it.chat.id, bytes.asMultipartFile("chat-photo.png")) }
//                .onSuccess { b -> reply(it, if (b) "Updated chat photo!" else "Didn't update chat photo!")}
//                .onFailure { e ->
//                    logger.error(e) { "Something went wrong while updating chat photo" }
//                    reply(it, "Error occurred. See logs.")
//                }
//        }

    }.join()

    private suspend inline fun BehaviourContext.onListCommand(message: Message) = coroutineScope {
        val rewardErrors = mutableListOf<Long>()
        val campaignErrors = mutableListOf<Long>()
        val unavailableCampaigns = mutableMapOf<Long, UnavailabilityReason>()
        val unavailableRewards = mutableMapOf<Long, UnavailabilityReason>()

        val (rewardsWithoutCampaign, rewardsWithCampaign) = newSuspendedTransaction(Config.dbContext) {
            currentChatWithRewardEntries(message).rewardEntries.map { it.rewardId }
        }.map { rewardId ->
            async { runCatching {
                fetcher.fetchReward(rewardId)
            }.getOrElse { e ->
                when (e) {
                    is RewardUnavailableException -> {
                        e.rewardId?.let { unavailableRewards[it] = e.unavailabilityReason }
                    }
                    else -> {
                        logger.error(e) { "Could not fetch reward: $rewardId" }
                        rewardErrors.add(rewardId)
                    }
                }
                null
            } }
        }.awaitAll().filterNotNull().sortedBy { it.attributes.amountCents }.partition { it.relationships?.campaign?.data?.id == null }

        rewardsWithoutCampaign.forEach { unavailableRewards[it.id] = UnavailabilityReason.NO_CAMPAIGN }

        val fetchedRewardsByCampaign = rewardsWithCampaign.groupBy { it.relationships!!.campaign!!.data.id }

        val fetchedCampaignsById = fetchedRewardsByCampaign.keys.map { async {
            runCatching {
                fetcher.fetchCampaign(it)
            }.getOrElse { e ->
                when (e) {
                    is CampaignUnavailableException -> {
                        e.campaignId?.let { unavailableCampaigns[it] = e.unavailabilityReason }
                    }
                    else -> {
                        logger.error(e) { "Could not fetch campaign: $it" }
                        campaignErrors.add(it)
                    }
                }
                null
            }
        } }.awaitAll().filterNotNull().associateBy { it.id }


        val groupedRewardsByCampaign = fetchedCampaignsById.mapNotNull {
            val rewards = fetchedRewardsByCampaign[it.key] ?: return@mapNotNull null
            it.value to rewards
        }.sortedBy { it.first.attributes.name }

        val locale = localeForCurrentChat(message)


        val messageContent = if (groupedRewardsByCampaign.isEmpty()) {
            "No observed rewards found!"
        } else {
            groupedRewardsByCampaign.joinToString(
                "\n\n",
                "The following rewards are being observed:\n\n"
            ) { formatForList(it.first, it.second, locale) }
        }


        reply(message, messageContent, MarkdownParseMode, true)

        if (unavailableRewards.isNotEmpty()) sendTextMessage(
            message.chat.id,
            "The following rewards are not available anymore:\n\n" + unavailableResourcesToString(unavailableRewards)
        )

        if (unavailableCampaigns.isNotEmpty()) sendTextMessage(
            message.chat.id,
            "The following campaigns are not available anymore:\n\n" + unavailableResourcesToString(unavailableCampaigns)
        )

        if (rewardErrors.isNotEmpty()) sendTextMessage(
            message.chat.id,
            "Error encountered fetching the following rewards:\n\n" + rewardErrors.joinToString(", ")
        )

        if (campaignErrors.isNotEmpty()) sendTextMessage(
            message.chat.id,
            "Error encountered fetching the following campaigns:\n\n" + campaignErrors.joinToString(", ")
        )
    }

    private suspend inline fun BehaviourContext.onCampaignAddCommand(message: Message, args: Array<String>) = coroutineScope {
        if (args.size != 1) {
            reply(message, "Exactly one argument (the campaign's ID) is expected")
            return@coroutineScope
        }

        val campaignId = args.first().toLongOrNull()
        if (campaignId == null) {
            reply(message, "The campaign ID must be a full integer")
            return@coroutineScope
        }

        val campaign = runCatching {
            fetcher.fetchCampaign(campaignId)
        }.getOrElse {
            reply(message, "The specified campaign $campaignId is not available. Please check that this campaign is still accessible.")
            null
        } ?: return@coroutineScope

        val rewardIds = campaign.relationships?.rewards?.data
        if (rewardIds.isNullOrEmpty()) {
            reply(message, "No rewards found for campaign $campaignId")
            return@coroutineScope
        }

        val rewardData = rewardIds.map {
            async { runCatching { fetcher.fetchReward(it.id) }.getOrNull() }
        }.awaitAll().filterNotNull()

        val locale = localeForCurrentChat(message)
        val stringifiedRewardData = rewardData.map {
            val attributes = it.attributes
            """
                *${attributes.title}* for ${attributes.formattedAmountCurrency(locale)}
                ID: *${it.id}*
            """.trimIndent()
        }

        reply(message, "The following rewards have been found:")
        sendTextMessage(message.chat, stringifiedRewardData.joinToString(lineSeparator), MarkdownParseMode)
        sendTextMessage(message.chat, "You can add a reward by using the /add command.")
    }
    
    private fun parseIdList(args: Array<String>)  = args.map { it.split(',') }.flatten()
        .mapNotNull { it.trim().toLongOrNull() }
        .toSet()


    private fun formatForList(
        campaign: CampaignData,
        rewards: Iterable<RewardData>,
        locale: Locale = defaultLocale
    ) : String {
        val ca = campaign.attributes
        val campaignString = "[${ca.name}](${ca.url})\n"
        val rewardLines = rewards.map {
            val ra = it.attributes
            "*${ra.title}* / ${ra.formattedAmountCurrency(locale)}\n(ID ${it.id})"
        }

        val joinedRewardLine = if (rewardLines.isEmpty()) "No rewards found for this campaign (how does this happen???)"
        else rewardLines.joinToString("\n")

        return campaignString + joinedRewardLine
    }

    private fun unavailableResourcesToString(unavailableResources: Map<Long, UnavailabilityReason>) = unavailableResources.mapNotNull {
        """
            ${it.key} (${it.value.displayName})
        """.trimIndent()
    }.joinToString("\n")

    private suspend inline fun localeForCurrentChat(message: WithChat) = localeForChat(message.chat.id.chatId)
    private suspend inline fun localeForChat(chatId: Long) = newSuspendedTransaction { localeForChat(chatId) }
    private fun Transaction.localeForChat(chatId: Long) = Chat.findById(chatId)?.locale ?: defaultLocale
    private fun Transaction.currentChat(message: WithChat) = Chat[message.chat.id.chatId]
    private fun Transaction.currentChatWithRewardEntries(message: WithChat) = currentChat(message).loadRewardEntries()
}

