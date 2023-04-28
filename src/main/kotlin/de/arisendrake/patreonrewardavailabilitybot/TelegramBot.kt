package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.exceptions.CampaignUnavailableException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.RewardUnavailableException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.UnavailabilityReason
import de.arisendrake.patreonrewardavailabilitybot.model.Chat
import de.arisendrake.patreonrewardavailabilitybot.model.RewardEntry
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries.chat
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries.rewardId
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.CampaignAttributes
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardsAttributes
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
import dev.inmo.tgbotapi.types.message.abstracts.CommonMessage
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.types.toChatId
import io.ktor.client.*
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

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

    private val bot = telegramBot(
        apiKey,
        client = httpClient
    )
    private val creatorId = Config.telegramCreatorId.toChatId()
    private val messageFilterCreatorOnly = CommonMessageFilter<Any> {
        it.chat.id == creatorId
    }
    
    suspend fun sendAvailabilityNotification(
        chatId: Long,
        reward: Data<RewardsAttributes>,
        campaign: Data<CampaignAttributes>
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
                *${ra.formattedAmount(locale)} ${ra.currency.currencyCode}*
                ID:
                ${reward.id}
                
                ([Join now!](${ra.fullUrl}))
            """.trimIndent()
        bot.sendTextMessage(chatId.toChatId(), text, MarkdownParseMode)
    }

    suspend fun sendMissingRewardNotification(entry: RewardEntry) = bot.sendTextMessage(
        creatorId,
        "WARNING: Reward with ID ${entry.id} could not be found. It may have been removed."
    )
    

    suspend fun sendForbiddenRewardNotification(entry: RewardEntry) = bot.sendTextMessage(
        creatorId,
        "WARNING: Access to reward with ID ${entry.id} is forbidden. It may have been removed."
    )
    
    suspend fun start() = bot.buildBehaviourWithLongPolling(timeoutSeconds = 60) {
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
                // Preload current chat with rewardEntries already loaded to avoid N+1 problem
                val currentChat = currentChat(message).load(Chat::rewardEntries)
                val currentRewardIds = currentChat.rewardEntries.map { it.rewardId }
                val uniqueNewIds = rewardIds.filter { it !in currentRewardIds }

                if (uniqueNewIds.isEmpty()) {
                    reply(message, "All IDs have been added already. No new ID has been added.")
                    return@newSuspendedTransaction
                }

                uniqueNewIds.forEach {
                    RewardEntry.new {
                        chat = currentChat
                        rewardId = it
                    }
                }

                reply(message, "Reward IDs [${uniqueNewIds.joinToString(", ")}] successfully added.".let {
                    if (rewardIds.size > uniqueNewIds.size)
                        "$it\nSome IDs have been added already and were filtered out."
                    else
                        it
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
                it.relationships.rewards?.data
            }.filterNotNull().flatten().map { it.id }.filter { it > 0 }.toSet()

            val removedRewardIds = newSuspendedTransaction(Config.dbContext) {
                // Preload
                val currentChat = currentChat(message).load(Chat::rewardEntries)
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
                reply(message, "Exactly one argument (an ISO 639 language code) is expected")
                return@onCommandWithArgs
            }

            val code = args.first().trim()
            if (code.length < 2 || code.length > 3) {
                reply(message, "An ISO 639 language code must be 2 or 3 characters long")
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
        ).apply(addToCommandList), initialFilter = messageFilterCreatorOnly) {

            val newlyCreated = newSuspendedTransaction(Config.dbContext) {
                val chatId = it.chat.id.chatId
                Chat.findById(chatId)?.let { false } ?: Chat.new(chatId) {  }.let { true }
            }

            if (newlyCreated) reply(it,
                "Welcome to the Patreon Rewards Availability Bot, ${it.fromUserMessageOrNull()?.user?.username?.username}"
            )
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

    private suspend fun BehaviourContext.onListCommand(message: CommonMessage<TextContent>) = coroutineScope {
        val unavailableCampaigns = mutableMapOf<Long, UnavailabilityReason>()
        val unavailableRewards = mutableMapOf<Long, UnavailabilityReason>()

        val fetchedRewardsByCampaign = newSuspendedTransaction(Config.dbContext) {
            RewardEntries.slice(rewardId).select { chat eq message.chat.id.chatId }.map { it[rewardId] }
        }.map { rewardId ->
            async { runCatching {
                fetcher.fetchReward(rewardId)
            }.getOrElse { e ->
                if (e is RewardUnavailableException) e.rewardId?.let { unavailableRewards[it] = e.unavailabilityReason }
                null
            } }
        }.awaitAll().filterNotNull().sortedBy { it.attributes.amountCents }.groupBy { it.relationships.campaign!!.data.id }

        val fetchedCampaignsById = fetchedRewardsByCampaign.keys.map { async {
            runCatching {
                fetcher.fetchCampaign(it)
            }.getOrElse { e ->
                if (e is CampaignUnavailableException) e.campaignId?.let { unavailableCampaigns[it] = e.unavailabilityReason }
                null
            }
        } }.awaitAll().filterNotNull().associateBy { it.id }


        val groupedRewardsByCampaign = fetchedCampaignsById.map {
            val rewards = fetchedRewardsByCampaign[it.key] ?: return@map null
            it.value to rewards
        }.filterNotNull().sortedBy { it.first.attributes.name }

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
    }

    private suspend fun BehaviourContext.onCampaignAddCommand(message: CommonMessage<TextContent>, args: Array<String>) = coroutineScope {
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

        val rewardIds = campaign.relationships.rewards?.data
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
                *${attributes.title}* for ${attributes.formattedAmount(locale)} ${attributes.currency.currencyCode}
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
        campaign: Data<CampaignAttributes>,
        rewards: Iterable<Data<RewardsAttributes>>,
        locale: Locale = defaultLocale
    ) : String {
        val ca = campaign.attributes
        val campaignString = "[${ca.name}](${ca.url})\n"
        val rewardLines = rewards.map {
            val ra = it.attributes
            "*${ra.title}* / ${ra.formattedAmount(locale)} ${ra.currency.currencyCode}\n(ID ${it.id})"
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

    private suspend fun localeForCurrentChat(message: CommonMessage<*>) = localeForChat(message.chat.id.chatId)
    private suspend fun localeForChat(chatId: Long) = newSuspendedTransaction(Config.dbContext) { localeForChat(chatId) }
    private fun Transaction.localeForChat(chatId: Long) = Chat.findById(chatId)?.locale ?: defaultLocale
    private fun Transaction.currentChat(message: CommonMessage<*>) = Chat[message.chat.id.chatId]
}

