package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import de.arisendrake.patreonrewardavailabilitybot.Config
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

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

    val formattedAmount get() = formattedAmount()

    fun formattedAmount(locale: Locale = Config.defaultLocale) : String {
        val formatter = NumberFormat.getNumberInstance(locale) as DecimalFormat
        formatter.maximumFractionDigits = 2
        return formatter.format(amount.toDouble() / 100)
    }
}