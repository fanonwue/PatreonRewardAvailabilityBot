package de.arisendrake.patreonrewardavailabilitybot.telegram

import de.arisendrake.patreonrewardavailabilitybot.Config
import de.arisendrake.patreonrewardavailabilitybot.PatreonFetcher
import de.arisendrake.patreonrewardavailabilitybot.exceptions.CampaignUnavailableException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.RewardUnavailableException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.UnavailabilityReason
import de.arisendrake.patreonrewardavailabilitybot.model.Chat
import de.arisendrake.patreonrewardavailabilitybot.model.RewardAction
import de.arisendrake.patreonrewardavailabilitybot.model.RewardActionType
import de.arisendrake.patreonrewardavailabilitybot.model.RewardEntry
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries.chat
import de.arisendrake.patreonrewardavailabilitybot.model.db.newSuspendedTransactionSingleThreaded
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.*
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.InstantSerializer
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.deleteMessage
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.sendActionTyping
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.extensions.api.telegramBot
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.CommonMessageFilter
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.utils.fromUserMessageOrNull
import dev.inmo.tgbotapi.requests.send.SendTextMessage
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.LinkPreviewOptions
import dev.inmo.tgbotapi.types.ReplyParameters
import dev.inmo.tgbotapi.types.buttons.ReplyKeyboardRemove
import dev.inmo.tgbotapi.types.message.HTML
import dev.inmo.tgbotapi.types.message.abstracts.AccessibleMessage
import dev.inmo.tgbotapi.types.message.abstracts.Message
import dev.inmo.tgbotapi.types.toChatId
import io.ktor.client.*
import kotlinx.coroutines.*
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.first
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.sequences.Sequence

class TelegramBot(
    apiKey: String,
    private val fetcher: PatreonFetcher,
    httpClient: HttpClient
) {
    companion object {
        private const val LINE_SEPARATOR = "\n-----------------------------------------\n"
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
                logger.info {
                    "Notification for the availability of reward ${action.rewardId} will be sent to chat ${action.chatId}"
                }
                val now = Instant.now()
                val rewardData = action.rewardData ?: fetcher.fetchReward(action.rewardId)
                val campaignData = action.campaignData ?: fetcher.fetchCampaign(rewardData.relationships?.campaign?.data!!.id)
                sendAvailabilityNotification(action.chatId, rewardData, campaignData)
                newSuspendedTransactionSingleThreaded {
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
                New Reward available for <a href="${ca.fullUrl}">${ca.name.tgHtmlEscape()}</a>
                
                <a href="${ra.fullUrl}"><b>${ra.title.tgHtmlEscape()}</b></a>
                for <b>${ra.formattedAmountCurrency(locale)}</b>
                
                ID: ${reward.id.tgHtmlCode()}
            """.trimIndent()



        bot.sendTextMessage(chatId.toChatId(), text, HTML)
    }

    suspend fun sendMissingRewardNotification(entry: RewardEntry) = newSuspendedTransactionSingleThreaded {
        sendMissingRewardNotification(entry.chat.id.value, entry.rewardId)
    }

    @SuppressWarnings("WeakerAccess")
    suspend fun sendMissingRewardNotification(chatId: Long, rewardId: RewardId) = bot.sendTextMessage(
        chatId.toChatId(),
        "WARNING: Reward with ID ${rewardId.tgHtmlCode()} could not be found. It may have been removed.",
        parseMode = HTML
    )

    suspend fun sendForbiddenRewardNotification(entry: RewardEntry) = newSuspendedTransactionSingleThreaded {
        sendForbiddenRewardNotification(entry.chat.id.value, entry.rewardId)
    }

    @SuppressWarnings("WeakerAccess")
    suspend fun sendForbiddenRewardNotification(chatId: Long, rewardId: RewardId) = bot.sendTextMessage(
        chatId.toChatId(),
        "WARNING: Access to reward with ID ${rewardId.tgHtmlCode()} is forbidden. It may have been removed.",
        parseMode = HTML
    )

    fun start(context: CoroutineContext = Dispatchers.IO) = CoroutineScope(context).launch { startInternal(this) }
    
    private suspend fun startInternal(scope: CoroutineScope) = bot.buildBehaviourWithLongPolling(scope, timeoutSeconds = 60) {
        val botCommandList = mutableListOf<BotCommand>()
        val addToCommandList: BotCommand.() -> Unit = { botCommandList.add(this) }

        onCommandWithArgs(BotCommand("add",
            "Adds one or more reward ID(s) to the list of observed rewards"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) {message, args ->
            sendActionTyping(message.chat)
            onRewardAddCommand(message, args)
        }

        onCommandWithArgs(BotCommand("add_campaign",
            "Retrieve all available rewards for specified reward campaign and allow the User to select a reward"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) { message, args ->
            sendActionTyping(message.chat)
            onCampaignAddCommand(message, args)
        }

        onCommandWithArgs(BotCommand("remove",
            "Removes a reward ID from the list of observed rewards"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) {message, args ->
            sendActionTyping(message.chat)
            onRewardRemoveCommand(message, args)
        }

        onCommandWithArgs(BotCommand("remove_campaign",
            "Removes all rewards associated with the specified campaign"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) {message, args ->
            sendActionTyping(message.chat)
            onCampaignRemoveCommand(message, args)
        }
        
        onCommand(BotCommand("reset_notifications",
            "Resets notifications for all rewards, so you'll be notified again about rewards that are still available"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) { message ->
            sendActionTyping(message.chat)
            val msg = reply(message, "Resetting last notification timestamps...")
            newSuspendedTransactionSingleThreaded {
                RewardEntries.update({chat eq message.chat.id.chatId.long}, null) {
                    it[lastNotified] = null
                }
            }
            editMessageText(msg, "Timestamps reset!")
        }
        
        onCommand(BotCommand("list",
            "Shows a list of all currently tracked rewards"
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) { message ->
            sendActionTyping(message.chat)
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
            newSuspendedTransactionSingleThreaded {
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

            val newlyCreated = newSuspendedTransactionSingleThreaded {
                Chat.findById(chatId.long)?.let { false } ?: Chat.new(chatId.long) {  }.let { true }
            }

            val username = it.fromUserMessageOrNull()?.user?.username

            if (!newlyCreated) {
                reply(it, "I know you already $username ;)")
                return@onCommand
            }

            reply(it,
                "Welcome to the Patreon Rewards Availability Bot, $username"
            )
            logger.info { "Added new chat $chatId" }
        }

        onCommand(BotCommand("privacy",
            "Privacy policy"
        ).apply(addToCommandList)) {
            reply(it, """
                This bot saves the following user information:
                
                - Your Chat ID (to match your watched Patreon Rewards to your Telegram account). 
                This is also used to instruct the Telegram API which Chat to send a message to.
                - Your langauge setting that you provided via the /language command
                - Your watched Patreon Rewrads, associated with your Chat ID 
                (to be able to check them regularly and inform you about available rewards)
            """.trimIndent(), parseMode = HTML)
        }

        setMyCommands(botCommandList)
    }.join()

    private suspend inline fun BehaviourContext.onListCommand(message: AccessibleMessage) = coroutineScope {
        val rewardErrors = mutableListOf<RewardId>()
        val campaignErrors = mutableListOf<CampaignId>()
        val unavailableCampaigns = mutableMapOf<CampaignId, UnavailabilityReason>()
        val unavailableRewards = mutableMapOf<RewardId, UnavailabilityReason>()

        val (rewardsWithoutCampaign, rewardsWithCampaign) = newSuspendedTransactionSingleThreaded {
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
                "\n<b>$LINE_SEPARATOR</b>\n",
                "The following rewards are being observed:\n\n"
            ) { formatForList(it.first, it.second, locale) }
        }


        reply(message, messageContent, parseMode = HTML, linkPreviewOptions = LinkPreviewOptions.Disabled)

        if (unavailableRewards.isNotEmpty()) sendTextMessage(
            message.chat.id,
            "The following rewards are not available anymore:\n\n" + unavailableResourcesToString(unavailableRewards),
            parseMode = HTML
        )

        if (unavailableCampaigns.isNotEmpty()) sendTextMessage(
            message.chat.id,
            "The following campaigns are not available anymore:\n\n" + unavailableResourcesToString(unavailableCampaigns),
            parseMode = HTML
        )

        if (rewardErrors.isNotEmpty()) sendTextMessage(
            message.chat.id,
            "Error encountered fetching the following rewards:\n\n" + rewardErrors.tgStringify(),
            parseMode = HTML
        )

        if (campaignErrors.isNotEmpty()) sendTextMessage(
            message.chat.id,
            "Error encountered fetching the following campaigns:\n\n" + campaignErrors.tgStringify(),
            parseMode = HTML
        )
    }

    private suspend inline fun BehaviourContext.onRewardAddCommand(message: AccessibleMessage, args: Array<String>) = coroutineScope {
        var replyTarget = message
        var rewardIds = parseIdList(args) { it.asRewardId() }
        while (rewardIds.isEmpty()) {
            val response = waitTextMessage(SendTextMessage(
                message.chat.id,
                "Please enter a list of reward IDs that you'd like to add",
                replyMarkup = cancelMarkup,
                replyParameters = ReplyParameters(message)
            )).first()

            val textContent = response.content.text

            if (textContent.lowercase() == "cancel") {
                sendCommandCancelMessage(message)
                return@coroutineScope
            }

            replyTarget = response
            rewardIds = parseIdList(textContent) { it.asRewardId() }
        }

        val placeholderMessage = reply(replyTarget, "Adding reward IDs...", replyMarkup = ReplyKeyboardRemove(), disableNotification = true)

        // Preload current chat with rewardEntries already loaded to avoid N+1 problems
        val currentChat = newSuspendedTransactionSingleThreaded { currentChat(message) }
        val currentRewardIds = newSuspendedTransactionSingleThreaded {
            currentChat.loadRewardEntries().rewardEntries.map { it.rewardId }
        }
        val uniqueNewIds = rewardIds.filter { it !in currentRewardIds }

        if (uniqueNewIds.isEmpty()) {
            deleteMessage(placeholderMessage)
            reply(replyTarget, "All IDs have been added already. No new ID has been added.")
            return@coroutineScope
        }

        val addedRewards = newSuspendedTransactionSingleThreaded {
            uniqueNewIds.associateWith { newId ->
                RewardEntries.insertAndGetId {
                    it[this.chat] = currentChat.id
                    it[this.rewardId] = newId.rawId
                }.value
            }
        }

        val rewardsNotAdded = uniqueNewIds.filter { it !in addedRewards }

        deleteMessage(placeholderMessage)
        reply(replyTarget, buildString {
            append("Reward IDs [${addedRewards.keys.tgStringify()}] successfully added.")

            if (rewardIds.size > addedRewards.size) {
                append("\n")
                append("Some IDs have been added already and were filtered out.")
            }

            if (rewardsNotAdded.isNotEmpty()) {
                append("\n")
                append("The following IDs were not added to the database: [${rewardsNotAdded.tgStringify()}]")
            }
        }, parseMode = HTML)
    }

    private suspend inline fun BehaviourContext.onRewardRemoveCommand(message: AccessibleMessage, args: Array<String>) = coroutineScope {
        var replyTarget = message
        var rewardIds = parseIdList(args) { it }
        while (rewardIds.isEmpty()) {
            val response = waitTextMessage(SendTextMessage(
                message.chat.id,
                "Please enter a list of reward IDs that you'd like to remove",
                replyMarkup = cancelMarkup,
                replyParameters = ReplyParameters(message)
            )).first()

            val textContent = response.content.text

            if (textContent.lowercase() == "cancel") {
                sendCommandCancelMessage(message)
                return@coroutineScope
            }

            replyTarget = response
            rewardIds = parseIdList(textContent) { it }
        }

        newSuspendedTransactionSingleThreaded {
            RewardEntries.deleteWhere {
                (chat eq message.chat.id.chatId.long) and (rewardId inList rewardIds)
            }
        }

        reply(
            replyTarget,
            "Reward IDs [${rewardIds.map { it.asRewardId() }.tgStringify()}] successfully removed.",
            parseMode = HTML,
            replyMarkup = ReplyKeyboardRemove()
        )
    }

    private suspend inline fun BehaviourContext.onCampaignAddCommand(message: AccessibleMessage, args: Array<String>) = coroutineScope {
        var replyTarget = message
        var rawCampaignId = args.firstOrNull()?.toLongOrNull()
        while (rawCampaignId == null) {
            val response = waitTextMessage(SendTextMessage(
                message.chat.id,
                "Please enter a campaign ID",
                replyMarkup = cancelMarkup,
                replyParameters = ReplyParameters(message)
            )).first()

            val textContent = response.content.text

            if (textContent.lowercase() == "cancel") {
                sendCommandCancelMessage(message)
                return@coroutineScope
            }

            replyTarget = response
            rawCampaignId = textContent.toLongOrNull()
        }

        val placeholderMessage = reply(replyTarget, "Searching rewards...", replyMarkup = ReplyKeyboardRemove(), disableNotification = true)

        val campaignId = rawCampaignId.asCampaignId()

        val campaign = try {
            fetcher.fetchCampaign(campaignId)
        } catch (e: Exception) {
            deleteMessage(placeholderMessage)
            reply(replyTarget, "The specified campaign $campaignId is not available. Please check that this campaign is still accessible.")
            return@coroutineScope
        }

        val rewardIds = campaign.relationships?.rewards?.data
        if (rewardIds.isNullOrEmpty()) {
            deleteMessage(placeholderMessage)
            reply(replyTarget, "No rewards found for campaign $campaignId")
            return@coroutineScope
        }

        val rewardData = rewardIds.map {
            async { runCatching { fetcher.fetchReward(it) }.getOrNull() }
        }.awaitAll().filterNotNull()

        val locale = localeForCurrentChat(message)
        val stringifiedRewardData = rewardData.map {
            val attributes = it.attributes
            """
                <b>${attributes.title.tgHtmlEscape()}</b> for ${attributes.formattedAmountCurrency(locale)}
                ID: ${it.id.tgHtmlCode()}
            """.trimIndent()
        }

        deleteMessage(placeholderMessage)
        reply(replyTarget, "The following rewards have been found:")
        sendTextMessage(message.chat, stringifiedRewardData.joinToString(LINE_SEPARATOR), parseMode = HTML, disableNotification = true)
        sendTextMessage(message.chat, "You can add a reward by using the /add command.", disableNotification = true)

    }

    private suspend inline fun BehaviourContext.onCampaignRemoveCommand(message: AccessibleMessage, args: Array<String>) = coroutineScope {
        val campaignIds = parseIdList(args) { it.asCampaignId() }
        if (campaignIds.isEmpty()) {
            reply(message, "One or multiple Campaign IDs expected as arguments")
            return@coroutineScope
        }

        val unavailableCampaigns = mutableMapOf<CampaignId, UnavailabilityReason>()

        val rewardIds = campaignIds.map { campaignId -> async { runCatching {
            fetcher.fetchCampaign(campaignId)
        }.getOrElse { e ->
            if (e is CampaignUnavailableException) unavailableCampaigns[campaignId] = e.unavailabilityReason
            null
        } } }.awaitAll().asSequence().filterNotNull().map {
            it.relationships?.rewards?.data
        }.filterNotNull().flatten().filter { it.rawId > 0 }.toSet()

        val removedRewardIds = newSuspendedTransactionSingleThreaded {
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
            reply(message, "Reward IDs [${removedRewardIds.tgStringify()}] successfully removed.", parseMode = HTML)
        } else {
            reply(message, "No observed rewards corresponding to any of the specified campaign IDs found, nothing got removed.")
        }

        if (unavailableCampaigns.isNotEmpty()) sendTextMessage(message.chat,
            "The following campaigns are unavailable:\n\n" + unavailableResourcesToString(unavailableCampaigns))
    }

    private fun <T> parseIdList(text: String, transform: (Long) -> T) = parseIdList(text.splitToSequence(',', ' '), transform)

    private fun <T> parseIdList(args: Array<String>, transform: (Long) -> T) = parseIdList(args.asSequence(), transform)

    private fun <T> parseIdList(args: Sequence<String>, transform: (Long) -> T) = args
        .map { it.split(',') }.flatten()
        .mapNotNull { it.trim().toLongOrNull() }
        .map(transform)
        .toSet()



    private fun formatForList(
        campaign: CampaignData,
        rewards: Iterable<RewardData>,
        locale: Locale = defaultLocale
    ) : String {
        val ca = campaign.attributes
        val campaignString = "<a href=\"${ca.fullUrl}\"><b>${ca.name.tgHtmlEscape()}</b></a>"
        val rewardLines = rewards.map {
            val ra = it.attributes
            """
                <b>${ra.title.tgHtmlEscape()}</b> for ${ra.formattedAmountCurrency(locale)}
                (ID ${it.id.tgHtmlCode()})
            """.trimIndent()
        }

        val joinedRewardLine = if (rewardLines.isEmpty()) "No rewards found for this campaign (how does this happen???)"
        else rewardLines.joinToString("\n\n")

        return campaignString + "\n\n" + joinedRewardLine
    }

    private fun <T : PatreonId> unavailableResourcesToString(unavailableResources: Map<T, UnavailabilityReason>) = unavailableResources.mapNotNull {
        """
            ${it.key.tgHtmlCode()} -- ${it.value.displayName}
        """.trimIndent()
    }.joinToString("\n")

    private suspend inline fun localeForCurrentChat(message: Message) = localeForChat(message.chat.id.chatId.long)
    private suspend inline fun localeForChat(chatId: Long) = newSuspendedTransactionSingleThreaded { localeForChat(chatId) }
    private fun Transaction.localeForChat(chatId: Long) = Chat.findById(chatId)?.locale ?: defaultLocale
    private fun Transaction.currentChat(message: Message) = Chat[message.chat.id.chatId.long]
    private fun Transaction.currentChatWithRewardEntries(message: Message) = currentChat(message).loadRewardEntries()
}

