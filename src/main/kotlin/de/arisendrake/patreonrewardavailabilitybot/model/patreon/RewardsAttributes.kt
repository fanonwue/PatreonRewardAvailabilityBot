package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import de.arisendrake.patreonrewardavailabilitybot.Config
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.time.Instant
import java.util.Currency

@Serializable
data class RewardsAttributes(
    val amount: Int,
    @SerialName("amount_cents") val amountCents: Int,
    val title: String,
    val remaining: Int?,
    val url: UriAsString,
    val currency: CurrencyAsString,
    @SerialName("created_at") val createdAt: InstantAsIsoString,
    @SerialName("edited_at") val editedAt: InstantAsIsoString
) {
    val fullUrl get() = URI.create(Config.baseDomain + url)
    val formattedAmount get() = let {
        val cents = (amount % 100)
        val full = amount / 100
        if (cents > 0) "$full.${cents.toString().padStart(2, '0')}" else full.toString()
    }
}