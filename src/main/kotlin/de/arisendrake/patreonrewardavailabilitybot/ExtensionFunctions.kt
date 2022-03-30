package de.arisendrake.patreonrewardavailabilitybot

fun String.withoutNewlines() = this
    .replace("\\r\\n|\\n".toRegex(), " ")

fun <T, K> MutableMap<T, K>.removeAll(iterable: Iterable<T>) = removeAll(iterable.iterator())

fun <T, K> MutableMap<T, K>.removeAll(sequence: Sequence<T>) = removeAll(sequence.iterator())

fun <T, K> MutableMap<T, K>.removeAll(iterator: Iterator<T>) = iterator.forEach {
    this.remove(it)
}
