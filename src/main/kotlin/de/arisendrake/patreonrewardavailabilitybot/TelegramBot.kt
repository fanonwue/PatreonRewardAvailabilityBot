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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
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
        val locale = getLocaleForChat(chatId)
        val ca = campaign.attributes
        val ra = reward.attributes
        val text =
            """
                New Reward available for [${ca.name}](${ca.url})!
                
                Name: 
                *${ra.title}*
                Cost: 
                *${ra.formattedAmount(locale)} ${ra.currency.currencyCode}*
                
                ([Reward ${reward.id}](${ra.fullUrl}))
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
        val addToCommandList: (BotCommand) -> Unit = { botCommandList.add(it) }

        onCommandWithArgs(BotCommand("add",
            "Adds a reward ID to the list of observed rewards"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) {message, args ->
            sendActionTyping(message.chat.id)
            val rewardIds = parseRewardIdList(args)
            if (rewardIds.isEmpty()) {
                reply(message, "One or multiple Rewards IDs expected as arguments")
                return@onCommandWithArgs
            }
            
            val uniqueNewIds = newSuspendedTransaction(Config.dbContext) {
                val currentRewardIds = RewardEntries
                    .slice(rewardId)
                    .select { chat eq message.chat.id.chatId }.map {
                        it[rewardId]
                    }

                rewardIds.filterNot { currentRewardIds.contains(it) }
            }
            if (uniqueNewIds.isEmpty()) {
                reply(message, "All IDs have been added already. No new ID has been added.")
                return@onCommandWithArgs
            }

            newSuspendedTransaction(Config.dbContext) { uniqueNewIds.forEach {it ->
                RewardEntry.new {
                    chat = Chat[message.chat.id.chatId]
                    rewardId = it
                }
            } }

            reply(message, "Reward IDs [${uniqueNewIds.joinToString(", ")}] successfully added.".let { it ->
                if (rewardIds.size > uniqueNewIds.size)
                    "$it\nSome IDs have been added already and were filtered out."
                else
                    it
            })
        }

        onCommandWithArgs(BotCommand("add_campaign",
            "Retrieve all available rewards for specified reward campaign and allow the User to select a reward"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) { message, args ->
            sendActionTyping(message.chat.id)
            onCampaignAddCommand(message, args)
        }
        
        onCommandWithArgs(BotCommand("remove",
            "Removes a reward ID from the list of observed rewards"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) {message, args ->
            sendActionTyping(message.chat.id)
            val rewardIds = parseRewardIdList(args)
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
        
        onCommand(BotCommand("reset_notifications",
            "Resets notifications for all rewards, so you'll be notified again about rewards that are still available"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) { message ->
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
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) { message ->
            sendActionTyping(message.chat.id)
            onListCommand(message)
        }

        onCommand(BotCommand("start",
            "Start interaction with bot"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) {

            val newlyCreated = newSuspendedTransaction(Config.dbContext) {
                val chatId = it.chat.id.chatId
                Chat.findById(chatId)?.let { false } ?: Chat.new(chatId) {  }.let { true }
            }

            if (newlyCreated) reply(it,
                "Welcome to the Patreon Rewards Availability Bot, ${it.fromUserMessageOrNull()?.user?.username?.username}"
            )
        }

        onCommandWithArgs(BotCommand("language",
            "Sets the language that should be used. For now, this only includes number formatting, sorry!"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) {message, args ->
            sendActionTyping(message.chat)

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
                Chat[message.chat.id.chatId].locale = locale
            }

            reply(message, "Language has been successfully set to \"${locale.displayName}\"")
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
        val responseMessage = reply(message, "Fetching data...")

        val unavailableCampaigns = mutableMapOf<Long, UnavailabilityReason>()
        val unavailableRewards = mutableMapOf<Long, UnavailabilityReason>()
        val locale = getLocaleForChat(message.chat.id.chatId)
        var messageContent = newSuspendedTransaction(Config.dbContext) { RewardEntry.find { chat eq message.chat.id.chatId }.map { entry ->
            async {
                try {
                    val reward = fetcher.fetchReward(entry.rewardId)
                    val campaign = fetcher.fetchCampaign(reward.relationships.campaign?.data!!.id)
                    // Create an artificial combined key for sorting
                    (campaign.attributes.name to reward.attributes.amount) to formatForList(reward, campaign, locale)
                } catch (e: RewardUnavailableException) {
                    e.rewardId?.let { unavailableRewards[it] = e.unavailabilityReason }
                    null
                } catch (e: CampaignUnavailableException) {
                    e.campaignId?.let { unavailableCampaigns[it] = e.unavailabilityReason}
                    null
                }
            }
        }.awaitAll().filterNotNull().sortedWith(compareBy( { it.first.first }, {it.first.second} )  ).joinToString(
            lineSeparator
        ) { it.second } }

        if (messageContent.isBlank()) messageContent = "No observed rewards found!"

        editMessageText(responseMessage, messageContent, MarkdownParseMode, true)

        if (unavailableRewards.isNotEmpty()) sendTextMessage(
            message.chat.id,
            "The following rewards are not available anymore\n\n" + unavailableResourcesToString(unavailableRewards)
        )

        if (unavailableCampaigns.isNotEmpty()) sendTextMessage(
            message.chat.id,
            "The following campaigns are not available anymore\n\n" + unavailableResourcesToString(unavailableCampaigns)
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

        val locale = getLocaleForChat(message.chat.id.chatId)
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
    
    private fun parseRewardIdList(args: Array<String>)  = args.map { it.split(',') }.flatten()
        .filterNot { it.isBlank() }
        .mapNotNull { it.trim().toLongOrNull() }
        .toSet()


    private fun formatForList(
        reward: Data<RewardsAttributes>, campaign: Data<CampaignAttributes>,
        locale: Locale = defaultLocale
    ) = let {
        val ca = campaign.attributes
        val ra = reward.attributes
        """
            [${ca.name}](${ca.url}) - *${ra.title}* for ${ra.formattedAmount(locale)} ${ra.currency.currencyCode}
            (ID: *${reward.id}*)
        """.trimIndent()
    }

    private fun getIdsFromArguments(arguments: Array<String>) = arguments.map {
        it.toLongOrNull()
    }.filterNotNull()

    private fun unavailableResourcesToString(unavailableResources: Map<Long, UnavailabilityReason>) = unavailableResources.mapNotNull {
        """
            ${it.key} (${it.value.displayName})
        """.trimIndent()
    }.joinToString("\n")

    private suspend fun getLocaleForChat(chatId: Long) = newSuspendedTransaction(Config.dbContext) {
        Chat.findById(chatId)?.locale ?: defaultLocale
    }
}

