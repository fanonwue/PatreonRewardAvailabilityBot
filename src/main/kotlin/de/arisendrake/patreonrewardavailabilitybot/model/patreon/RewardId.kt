package de.arisendrake.patreonrewardavailabilitybot.model.patreon

@JvmInline
value class RewardId(
    override val id: Long
): PatreonId {
    override fun toString() = rawId.toString()
}

fun Long.asRewardId() = RewardId(this)
fun PatreonId.asRewardId() = RewardId(rawId)