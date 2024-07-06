package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KProperty

@Serializable
@SerialName("campaign")
data class CampaignId(
    override val id: Long
): PatreonId {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = id
    override fun toString() = id.toString()
}

fun Long.asCampaignId() = CampaignId(this)
fun PatreonId.asCampaignId() = CampaignId(id)