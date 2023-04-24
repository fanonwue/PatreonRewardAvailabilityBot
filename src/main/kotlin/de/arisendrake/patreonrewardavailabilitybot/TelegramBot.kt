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
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

class TelegramBot(
    apiKey: String,
    private val fetcher: PatreonFetcher,
    httpClient: HttpClient
) {

    companion object {
        @JvmStatic
        private val logger = KotlinLogging.logger {  }
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
        val ca = campaign.attributes
        val ra = reward.attributes
        val text = 
            """
                New Reward available for [${ca.name}](${ca.url})!
                
                Name: 
                *${ra.title}*
                Cost: 
                *${ra.formattedAmount} ${ra.currency.currencyCode}*
                
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

        onCommandWithArgs(BotCommand("add",
            "Adds a reward ID to the list of observed rewards"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) {context, args ->
            val rewardIds = parseRewardIdList(args)
            if (rewardIds.isEmpty()) {
                reply(context, "One or multiple Rewards IDs expected as arguments")
                return@onCommandWithArgs
            }
            
            val uniqueNewIds = newSuspendedTransaction(Config.dbContext) {
                val currentRewardIds = RewardEntries
                    .slice(rewardId)
                    .select { chat eq context.chat.id.chatId }.map {
                        it[rewardId]
                    }

                rewardIds.filterNot { currentRewardIds.contains(it) }
            }
            if (uniqueNewIds.isEmpty()) {
                reply(context, "All IDs have been added already. No new ID has been added.")
                return@onCommandWithArgs
            }

            newSuspendedTransaction(Config.dbContext) { uniqueNewIds.forEach {it ->
                RewardEntry.new {
                    chat = Chat[context.chat.id.chatId]
                    rewardId = it
                }
            } }

            reply(context, "Reward IDs [${uniqueNewIds.joinToString(", ")}] successfully added.".let { it ->
                if (rewardIds.size > uniqueNewIds.size)
                    "$it\nSome IDs have been added already and were filtered out."
                else
                    it
            })
        }
        
        onCommandWithArgs(BotCommand("remove",
            "Removes a reward ID from the list of observed rewards"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) {context, args ->

            val rewardIds = parseRewardIdList(args)
            if (rewardIds.isEmpty()) {
                reply(context, "One or multiple Rewards IDs expected as arguments")
                return@onCommandWithArgs
            }

            newSuspendedTransaction(Config.dbContext) {
                RewardEntries.deleteWhere {
                    (chat eq context.chat.id.chatId) and (rewardId inList rewardIds)
                }
            }

            reply(context, "Reward IDs [${rewardIds.joinToString(", ")}] successfully removed.")
            
        }
        
        onCommand(BotCommand("reset_notifications",
            "Resets notifications for all rewards, so you'll be notified again about rewards that are still available"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) { context ->
            val msg = reply(context, "Resetting last notification timestamps...")
            newSuspendedTransaction(Config.dbContext) {
                RewardEntries.update({chat eq context.chat.id.chatId}, null) {
                    it[lastNotified] = null
                }
            }
            editMessageText(msg, "Timestamps reset!")
        }
        
        onCommand(BotCommand("list",
            "Shows a list of all currently tracked rewards"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) { onListCommand(this, it) }


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

    private suspend fun onListCommand(context: BehaviourContext, message: CommonMessage<TextContent>) = coroutineScope {
        context.sendActionTyping(message.chat.id)
        val responseMessage = context.reply(message, "Fetching data...")

        val unavailableCampaigns = mutableMapOf<Long, UnavailabilityReason>()
        val unavailableRewards = mutableMapOf<Long, UnavailabilityReason>()

        var messageContent = newSuspendedTransaction(Config.dbContext) { RewardEntry.find { chat eq message.chat.id.chatId }.map { entry ->
            async {
                try {
                    val reward = fetcher.fetchReward(entry.rewardId)
                    val campaign = fetcher.fetchCampaign(reward.relationships.campaign?.data!!.id)
                    // Create an artificial combined key for sorting
                    (campaign.attributes.name to reward.attributes.amount) to formatForList(reward, campaign)
                } catch (e: RewardUnavailableException) {
                    e.rewardId?.let { unavailableRewards[it] = e.unavailabilityReason }
                    null
                } catch (e: CampaignUnavailableException) {
                    e.campaignId?.let { unavailableCampaigns[it] = e.unavailabilityReason}
                    null
                }
            }
        }.awaitAll().filterNotNull().sortedWith(compareBy( { it.first.first }, {it.first.second} )  ).joinToString(
            separator = "\n-----------------------------------------\n"
        ) { it.second } }

        if (messageContent.isBlank()) messageContent = "No observed rewards found!"

        context.editMessageText(responseMessage, messageContent, MarkdownParseMode, true)

        if (unavailableRewards.isNotEmpty()) context.sendTextMessage(
            message.chat.id,
            "The following rewards are not available anymore\n\n" + unavailableResourcesToString(unavailableRewards)
        )

        if (unavailableCampaigns.isNotEmpty()) context.sendTextMessage(
            message.chat.id,
            "The following campaigns are not available anymore\n\n" + unavailableResourcesToString(unavailableCampaigns)
        )
    }
    
    private fun parseRewardIdList(args: Array<String>)  = args.map { it.split(',') }.flatten()
        .filterNot { it.isBlank() }
        .mapNotNull { it.trim().toLongOrNull() }
        .toSet()


    private fun formatForList(reward: Data<RewardsAttributes>, campaign: Data<CampaignAttributes>) = let {
        val ca = campaign.attributes
        val ra = reward.attributes
        """
            [${ca.name}](${ca.url}) - *${ra.title}* for ${ra.formattedAmount} ${ra.currency.currencyCode}
            (ID *${reward.id}*)
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
}

