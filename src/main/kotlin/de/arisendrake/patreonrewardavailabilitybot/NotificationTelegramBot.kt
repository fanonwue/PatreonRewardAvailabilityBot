package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.model.RewardObservationList
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.CampaignAttributes
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.Data
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardsAttributes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.objects.Ability
import org.telegram.abilitybots.api.objects.Locality
import org.telegram.abilitybots.api.objects.Privacy
import org.telegram.abilitybots.api.toggle.BareboneToggle
import org.telegram.telegrambots.meta.api.methods.ActionType
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault


class NotificationTelegramBot(
    apiKey: String,
    username: String,
    val fetcher: PatreonFetcher,
    val coroutineScope: CoroutineScope
) : AbilityBot(apiKey, username) {

    companion object {
        private val bareboneToggle = BareboneToggle()
    }

    override fun onRegister() {
        super<AbilityBot>.onRegister()
        val setMyCommands = SetMyCommands.builder()
            .commands(abilities().values.map {
                BotCommand.builder()
                    .command(it.name())
                    .description(it.info() ?: "default")
                    .build()
            })
            .scope(BotCommandScopeDefault())
            .build()

        execute(setMyCommands)
    }

    override fun creatorId() = Config.telegramCreatorId

    fun addReward() : Ability {
        return Ability.builder()
            .name("add")
            .info("Adds a reward ID to the list of observed rewards")
            .locality(Locality.ALL)
            .privacy(Privacy.CREATOR)
            .action { coroutineScope.launch {
                if (it.arguments().isEmpty()) {
                    silent.send("Reward ID expected as first argument", it.chatId())
                } else {
                    val idList = getIdsFromArguments(it.arguments())
                    RewardObservationList.add(idList)
                    silent.send("Reward IDs [${idList.joinToString(", ")}] successfully added", it.chatId())
                }
            } }
            .post {  }
            .build()
    }

    fun removeReward() : Ability {
        return Ability.builder()
            .name("remove")
            .info("Removes a reward ID from the list of observed rewards")
            .locality(Locality.ALL)
            .privacy(Privacy.CREATOR)
            .action { coroutineScope.launch {
                if (it.arguments().isEmpty()) {
                    silent.send("Reward ID expected as first argument", it.chatId())
                } else {
                    val idList = getIdsFromArguments(it.arguments())
                    RewardObservationList.remove(idList)
                    silent.send("Reward IDs [${idList.joinToString(", ")}] successfully removed", it.chatId())
                }
            } }
            .post {  }
            .build()
    }

    fun listTrackedRewards() : Ability {
        return Ability.builder()
            .name("list")
            .info("Shows a list of all currently tracked rewards")
            .locality(Locality.ALL)
            .privacy(Privacy.CREATOR)
            .action { context ->  coroutineScope.launch {

                val message = context.bot().execute(SendMessage().also {
                    it.chatId = context.chatId().toString()
                    it.text = "Fetching data..."
                })

                context.bot().execute(SendChatAction().also {
                    it.setAction(ActionType.TYPING)
                    it.chatId = context.chatId().toString()
                })

                var messageContent = RewardObservationList.rewardSet.map {
                    async {
                        val reward = fetcher.fetchReward(it)
                        val campaign = fetcher.fetchCampaign(reward.relationships.campaign?.data!!.id)
                        // Create an artificial combined key for sorting
                        (campaign.attributes.name to reward.attributes.amount) to formatForList(reward, campaign)
                    }
                }.awaitAll().sortedWith(compareBy( { it.first.first }, {it.first.second} )  ).joinToString(
                    separator = "\n-----------------------------------------\n"
                ) { it.second }


                if (messageContent.isEmpty()) messageContent = "No observed rewards found!"

                context.bot().execute(EditMessageText().also {
                    it.chatId = context.chatId().toString()
                    it.messageId = message.messageId
                    it.text = messageContent
                    it.enableMarkdown(true)
                    it.disableWebPagePreview()
                })

            } }
            .post {  }
            .build()
    }

    suspend fun sendAvailabilityNotification(reward: Data<RewardsAttributes>, campaign: Data<CampaignAttributes>) {
        val ca = campaign.attributes
        val ra = reward.attributes
        silent.sendMd(
            """
                New Reward available for [${ca.name}](${ca.url})!
                
                Name: 
                *${ra.title}*
                Cost: 
                *${ra.formattedAmount} ${ra.currency.currencyCode}*
                
                ([Reward ${reward.id}](${ra.fullUrl}))
            """.trimIndent(),
            creatorId()
        )
    }

    private fun formatForList(reward: Data<RewardsAttributes>, campaign: Data<CampaignAttributes>) = let {
        val ca = campaign.attributes
        val ra = reward.attributes
        """
            [${ca.name}](${ca.url}) - *${ra.title}* for ${ra.formattedAmount} ${ra.currency.currencyCode}
            (ID *${reward.id}*)
        """.trimIndent()
    }

    private fun getIdsFromArguments(arguments: Array<String>) = arguments.map {
        try {
            it.toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }.filterNotNull()
}