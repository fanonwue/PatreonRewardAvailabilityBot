package de.arisendrake.patreonrewardavailabilitybot.model.patreon

@JvmInline
value class CampaignId(
    override val id: Long
): PatreonId {
    override fun toString() = id.toString()
}

fun Long.asCampaignId() = CampaignId(this)
fun PatreonId.asCampaignId() = CampaignId(rawId)