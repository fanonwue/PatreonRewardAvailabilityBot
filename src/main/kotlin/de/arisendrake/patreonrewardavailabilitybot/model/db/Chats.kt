package de.arisendrake.patreonrewardavailabilitybot.model.db

import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

object Chats : IdTable<Long>() {
    override val id = long("chat_id").entityId()
    override val primaryKey = PrimaryKey(id)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val language = char("locale", 3).default(Locale.ENGLISH.isO3Language)
}