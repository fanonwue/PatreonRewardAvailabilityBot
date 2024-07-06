package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KProperty

@Serializable
@SerialName("reward")
data class RewardId(
    override val id: Long
): PatreonId {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = id
    override fun toString() = id.toString()
}

fun Long.asRewardId() = RewardId(this)
fun PatreonId.asRewardId() = RewardId(id)