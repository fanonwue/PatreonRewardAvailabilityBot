package de.arisendrake.patreonrewardavailabilitybot.model.patreon

@JvmInline
value class RewardId(
    private val id: Long
): PatreonId {
    override val rawId get() = id
    override fun toString() = id.toString()
}

fun Long.asRewardId() = RewardId(this)
fun PatreonId.asRewardId() = RewardId(rawId)