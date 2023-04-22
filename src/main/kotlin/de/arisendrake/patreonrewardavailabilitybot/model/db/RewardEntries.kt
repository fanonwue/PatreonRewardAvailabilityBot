package de.arisendrake.patreonrewardavailabilitybot.model.db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object RewardEntries : LongIdTable() {
    val chat = reference("chat", Chats.id)
    val rewardId = long("reward_id")
    val availableSince = timestamp("available_since").nullable()
    val lastNotified = timestamp("last_notified").nullable()
    val isMissing = bool("is_missing").default(false)

    init {
        uniqueIndex("unique_reward_per_chat", chat, rewardId)
    }
}