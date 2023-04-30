package de.arisendrake.patreonrewardavailabilitybot.model

import de.arisendrake.patreonrewardavailabilitybot.model.db.Chats
import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.load
import java.util.Locale

class Chat(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Chat>(Chats) {
        fun insertIfNotExists(chatId: Long) = let {
            Chat.findById(chatId) ?: Chat.new(chatId) {}
        }
    }

    val createdAt by Chats.createdAt

    /**
     * 3-letter locale code
     */
    private var language by Chats.language

    var locale: Locale
        get() = Locale.forLanguageTag(language)
        set(value) {
            language = value.isO3Language
        }

    val rewardEntries by RewardEntry referrersOn RewardEntries.chat

    fun loadRewardEntries() = this.load(Chat::rewardEntries)
}