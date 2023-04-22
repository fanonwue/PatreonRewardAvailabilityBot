package de.arisendrake.patreonrewardavailabilitybot.model

import de.arisendrake.patreonrewardavailabilitybot.model.db.Chats
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Chat(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Chat>(Chats) {
        fun insertIfNotExists(chatId: Long) = let {
            Chat.findById(chatId) ?: Chat.new(chatId) {}
        }
    }

    val createdAt  by Chats.createdAt
}