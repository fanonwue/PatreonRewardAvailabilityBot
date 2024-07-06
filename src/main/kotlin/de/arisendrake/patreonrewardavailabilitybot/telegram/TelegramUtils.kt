package de.arisendrake.patreonrewardavailabilitybot.telegram

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
fun String.escapeHtmlTg() = this.escapeHtml(telegramHtmlEntities)

fun String.escapeHtml(replacementMap: Map<Char, String> = telegramHtmlEntities): String {
    val text = this@escapeHtml
    if (text.isEmpty()) return text

    return buildString(length) {
        for (element in text) {
            replacementMap[element]?.also { append(it) } ?: append(element)
        }
    }
}