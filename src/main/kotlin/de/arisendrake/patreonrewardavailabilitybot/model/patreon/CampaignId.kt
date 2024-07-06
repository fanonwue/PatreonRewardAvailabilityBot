package de.arisendrake.patreonrewardavailabilitybot.model.patreon

@JvmInline
value class CampaignId(
    private val id: Long
): PatreonId {
    override val rawId get() = id
    override fun toString() = id.toString()
}

fun Long.asCampaignId() = CampaignId(this)
fun PatreonId.asCampaignId() = CampaignId(rawId)