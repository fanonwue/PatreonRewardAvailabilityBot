package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.exceptions.CampaignUnavailableException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.RewardUnavailableException
import de.arisendrake.patreonrewardavailabilitybot.exceptions.UnavailabilityReason
import de.arisendrake.patreonrewardavailabilitybot.model.RewardEntry
import de.arisendrake.patreonrewardavailabilitybot.model.RewardObservationList
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
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.message.MarkdownParseMode
import dev.inmo.tgbotapi.types.message.MarkdownV2ParseMode
import dev.inmo.tgbotapi.types.message.abstracts.CommonMessage
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.types.toChatId
import kotlinx.coroutines.*
import mu.KotlinLogging

class TelegramBot(
    apiKey: String,
    private val fetcher: PatreonFetcher
) {

    companion object {
        @JvmStatic
        private val logger = KotlinLogging.logger {  }
    }

    private val bot = telegramBot(apiKey)
    private val creatorId = Config.telegramCreatorId.toChatId()
    private val messageFilterCreatorOnly = CommonMessageFilter<Any> {
        it.chat.id == creatorId
    }
    
    suspend fun sendAvailabilityNotification(
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
        bot.sendTextMessage(creatorId, text, MarkdownV2ParseMode)
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
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) {it, args ->
            val rewardIds = parseRewardIdList(args)
            if (rewardIds.isEmpty()) {
                reply(it, "One or multiple Rewards IDs expected as arguments")
                return@onCommandWithArgs
            }
            
            val uniqueNewIds = rewardIds.filterNot { RewardObservationList.rewardMap.containsKey(it) }
            if (uniqueNewIds.isEmpty()) {
                reply(it, "All IDs have been added already. No new ID has been added.")
                return@onCommandWithArgs
            }
            
            RewardObservationList.add(uniqueNewIds)
            reply(it, "Reward IDs [${uniqueNewIds.joinToString(", ")}] successfully added.".let { text ->
                if (rewardIds.size > uniqueNewIds.size)
                    "$text\nSome IDs have been added already and were filtered out."
                else
                    text
            })
        }
        
        onCommandWithArgs(BotCommand("remove",
            "Removes a reward ID from the list of observed rewards"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) {it, args ->

            val rewardIds = parseRewardIdList(args)
            if (rewardIds.isEmpty()) {
                reply(it, "One or multiple Rewards IDs expected as arguments")
                return@onCommandWithArgs
            }

            RewardObservationList.remove(rewardIds)
            reply(it, "Reward IDs [${rewardIds.joinToString(", ")}] successfully removed.")
            
        }
        
        onCommand(BotCommand("reset_notifications",
            "Resets notifications for all rewards, so you'll be notified again about rewards that are still available"
        ).also(addToCommandList), initialFilter = messageFilterCreatorOnly) {
            val msg = reply(it, "Resetting last notification timestamps...")
            RewardObservationList.updateAll { it.lastNotified = null }
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

        var messageContent = RewardObservationList.rewardMap.values.map { entry ->
            async {
                try {
                    val reward = fetcher.fetchReward(entry.id)
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
        ) { it.second }

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

