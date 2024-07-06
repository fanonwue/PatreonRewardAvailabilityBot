package de.arisendrake.patreonrewardavailabilitybot.model

import de.arisendrake.patreonrewardavailabilitybot.model.db.RewardEntries
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.RewardId
import de.arisendrake.patreonrewardavailabilitybot.model.patreon.asRewardId
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class RewardEntry(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RewardEntry>(RewardEntries)

    var chat by Chat referencedOn RewardEntries.chat
    var rewardId: RewardId by RewardEntries.rewardId.transform({ it.id }, { it.asRewardId() })
    var availableSince by RewardEntries.availableSince
    var lastNotified by RewardEntries.lastNotified
    var isMissing by RewardEntries.isMissing

}