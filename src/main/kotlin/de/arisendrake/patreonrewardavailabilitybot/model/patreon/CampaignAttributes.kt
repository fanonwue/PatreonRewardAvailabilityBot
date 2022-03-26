package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import de.arisendrake.patreonrewardavailabilitybot.Config
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.InstantSerializer
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.UriSerializer
import kotlinx.serialization.Serializable
import java.net.URI
import java.time.Instant

@Serializable
data class CampaignAttributes(
    val name: String,
    @Serializable(with = UriSerializer::class)
    val url: URI,
    @Serializable(with = InstantSerializer::class)
    val created_at: Instant,
    @Serializable(with = InstantSerializer::class)
    val published_at: Instant
) {
    val fullUrl get() = url
}