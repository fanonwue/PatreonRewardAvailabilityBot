package de.arisendrake.patreonrewardavailabilitybot

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.Properties
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.bufferedReader

object Config {
    const val DEFAULT_CONFIG_PATH = "config/config.ini"
    const val DEFAULT_DATA_PATH = "data/rewards.json"

    val charset = Charsets.UTF_8

    val configStore by lazy {
        Properties().also { props ->
            Paths.get(
                System.getenv("CONFIG_PATH").let {
                    if (it.isNullOrBlank()) DEFAULT_CONFIG_PATH else it
                }
            ).bufferedReader(charset).use {
                props.load(it)
            }
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    val dbContext: CoroutineContext = Dispatchers.IO.limitedParallelism(1)

    val removeMissingRewards: Boolean
    by lazy { getValue("run.removeMissingRewards", false) }

    val notifyOnMissingRewards: Boolean
    by lazy { getValue("run.notifyOnMissingRewards", true) }

    val notifyOnForbiddenRewards: Boolean
    by lazy { getValue("run.notifyOnForbiddenRewards", true) }

    val telegramCreatorId: Long
    by lazy { getValue("telegram.creatorId", 0L) }

    val telegramApiKey: String
    by lazy { getValue("telegram.api.key", "") }

    val interval: Duration
    by lazy { getValue("run.interval", 300) }

    val initialDelay: Duration
    by lazy { getValue("run.initialDelay", 5) }

    val baseDomain: String
    by lazy { getValue("baseDomain", "https://www.patreon.com") }

    val rewardsListFile: Path
    by lazy { getValue("run.rewardsListFile", DEFAULT_DATA_PATH) }

    val jsonSerializer get() = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private inline fun <reified T, reified R> getValue(key: String, default: R) = let {
        val value = configStore.getProperty(key, when (R::class) {
            Duration::class -> (default as Duration).seconds.toString()
            Instant::class -> (default as Instant).toEpochMilli().toString()
            else -> default.toString()
        })

        when (T::class) {
            String::class -> value
            Int::class -> value.toInt()
            Long::class -> value.toLong()
            Float::class -> value.toFloat()
            Double::class -> value.toDouble()
            Boolean::class -> value.toBoolean()

            Path::class -> Paths.get(value)
            Duration::class -> Duration.ofSeconds(value.toLong())
            Instant::class -> Instant.ofEpochMilli(value.toLong())

            else -> throw IllegalArgumentException("Type ${T::class} is not supported")
        } as T
    }
}
