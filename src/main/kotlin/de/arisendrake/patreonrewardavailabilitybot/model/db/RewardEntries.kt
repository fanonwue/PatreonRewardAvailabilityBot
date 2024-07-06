package de.arisendrake.patreonrewardavailabilitybot.model.db

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp

object RewardEntries : IntIdTable() {
    val chat = reference("chat", Chats.id, ReferenceOption.CASCADE)
    val rewardId = long("reward_id").index("reward_id_index")
    val availableSince = timestamp("available_since").nullable()
    val lastNotified = timestamp("last_notified").nullable()
    val isMissing = bool("is_missing").default(false)

    init {
        uniqueIndex("unique_reward_per_chat", chat, rewardId)
    }
}