package de.arisendrake.patreonrewardavailabilitybot

val camelRegex = "(?<=[a-zA-Z])[A-Z]".toRegex()
val snakeRegex = "_[a-zA-Z]".toRegex()

fun String.camelToSnakeCase() = camelRegex.replace(this) {
        "_${it.value}"
}.lowercase()

fun String.camelToScreamingSnakeCase() = camelToSnakeCase().uppercase()

fun String.snakeToLowerCamelCase() = snakeRegex.replace(this) {
        it.value.replace("_","")
            .uppercase()
}

fun String.snakeToUpperCamelCase() = this.snakeToLowerCamelCase()
        .replaceFirstChar { it.uppercase() }

fun String.withoutNewlines() = this
    .replace("\\r\\n|\\n".toRegex(), " ")

fun <T, K> MutableMap<T, K>.removeAll(iterable: Iterable<T>) = removeAll(iterable.iterator())

fun <T, K> MutableMap<T, K>.removeAll(sequence: Sequence<T>) = removeAll(sequence.iterator())

fun <T, K> MutableMap<T, K>.removeAll(iterator: Iterator<T>) = iterator.forEach {
    this.remove(it)
}