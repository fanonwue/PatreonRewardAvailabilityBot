package de.arisendrake.patreonrewardavailabilitybot.model

import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class RewardEntry(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<RewardEntry>(RewardEntries)

    var chat by Chat referencedOn RewardEntries.chat
    var rewardId by RewardEntries.rewardId
    var availableSince by RewardEntries.availableSince
    var lastNotified by RewardEntries.lastNotified
    var isMissing by RewardEntries.isMissing

}