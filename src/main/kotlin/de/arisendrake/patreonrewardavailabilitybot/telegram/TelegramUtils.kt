package de.arisendrake.patreonrewardavailabilitybot.telegram

import de.arisendrake.patreonrewardavailabilitybot.model.patreon.PatreonId
import dev.inmo.tgbotapi.extensions.utils.types.buttons.flatReplyKeyboard
import dev.inmo.tgbotapi.extensions.utils.types.buttons.simpleButton

val cancelMarkup = flatReplyKeyboard(resizeKeyboard = true) {
    simpleButton("Cancel")
}

val telegramHtmlEntities = hashMapOf(
    '&' to "&amp;",
    '<' to "&lt;",
    '>' to "&gt;",
)

/**
 * Escapes the string (using HTML entities) to make it compatible with Telegram's HTML format.
 *
 * The API only supports the following entities: `&lt;`, `&gt;` and `&amp;`, therefore only the characters corresponding
 * to those will be escaped
 */
fun String.tgHtmlEscape() = this.escapeHtml(telegramHtmlEntities)

fun String.escapeHtml(replacementMap: Map<Char, String> = telegramHtmlEntities): String {
    val text = this@escapeHtml
    if (text.isEmpty()) return text

    return buildString(length) {
        for (element in text) {
            replacementMap[element]?.also { append(it) } ?: append(element)
        }
    }
}

fun String.tgHtmlCode() = "<code>$this</code>"
fun PatreonId.tgHtmlCode() = toString().tgHtmlCode()

fun <T : PatreonId> Iterable<T>.tgStringify(separator: String = ", ", useCodeFormatting: Boolean = true) = joinToString(separator) {
    if (useCodeFormatting) return@joinToString it.tgHtmlCode()
    it.toString()
}