package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import de.arisendrake.patreonrewardavailabilitybot.model.serializers.InstantAsIsoString
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.UriAsString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CampaignAttributes(
    val name: String,
    val url: UriAsString,
    @SerialName("created_at")
    val createdAt: InstantAsIsoString,
    @SerialName("published_at")
    val publishedAt: InstantAsIsoString
) {
    val fullUrl get() = url
}