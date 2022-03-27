package de.arisendrake.patreonrewardavailabilitybot

fun String.withoutNewlines() = this
    .replace("\\r\\n|\\n".toRegex(), " ")