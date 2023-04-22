package de.arisendrake.patreonrewardavailabilitybot.model.db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object Chats : LongIdTable(columnName = "chat_id") {
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
}