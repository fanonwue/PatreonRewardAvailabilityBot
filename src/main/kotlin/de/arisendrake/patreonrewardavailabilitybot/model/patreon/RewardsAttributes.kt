package de.arisendrake.patreonrewardavailabilitybot.model.patreon

import de.arisendrake.patreonrewardavailabilitybot.Config
import de.arisendrake.patreonrewardavailabilitybot.model.serializers.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

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
    val fullUrl get(): URI = Config.patreonBaseDomain.resolve(url)

    @SuppressWarnings("WeakerAccess")
    val amountDecimal get() = amountCents.toDouble() / 100

    @SuppressWarnings("WeakerAccess")
    fun formattedAmount(locale: Locale = Config.defaultLocale): String = (NumberFormat.getNumberInstance(locale) as DecimalFormat).let {
        it.maximumFractionDigits = 2
        it.format(amountDecimal)
    }

    fun formattedAmountCurrency(locale: Locale = Config.defaultLocale): String = NumberFormat.getCurrencyInstance(locale).let {
        it.currency = currency
        it.format(amountDecimal)
    }
}