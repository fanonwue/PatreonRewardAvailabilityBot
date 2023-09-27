package de.arisendrake.patreonrewardavailabilitybot

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.bufferedReader

object Config {
    const val DEFAULT_CONFIG_PATH = "config/config.ini"
    const val DEFAULT_DATA_PATH = "data/main.db"

    val charset = Charsets.UTF_8

    private val configStore by lazy {
        Properties().apply {
            Paths.get(
                System.getenv("CONFIG_PATH")?.takeUnless { it.isBlank() } ?: DEFAULT_CONFIG_PATH
            ).bufferedReader(charset).use {
                load(it)
            }
        }
    }

    val jsonSerializer
        get() = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    val sharedHttpClient by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(jsonSerializer)
            }

            install(HttpTimeout) {
                // 10 seconds request timeout
                requestTimeoutMillis = 10 * 1000
            }
        }
    }

    val defaultLocale: Locale = Locale.ENGLISH


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

    val creatorOnlyAccess: Boolean
            by lazy { getValue("telegram.creatorOnlyAccess", true) }

    val telegramApiKey: String
            by lazy { getValue("telegram.api.key", "") }

    val interval: Duration
            by lazy { getValue("run.interval", 300) }

    val initialDelay: Duration
            by lazy { getValue("run.initialDelay", 0) }

    val baseDomain: String
            by lazy { getValue("baseDomain", "https://www.patreon.com") }

    val databasePath: Path
            by lazy { getValue("run.databasePath", DEFAULT_DATA_PATH) }

    val useFetchCache: Boolean
            by lazy { getValue("run.useFetchCache", true) }

    val cacheValidity: Duration
            by lazy { getValue("run.cacheValidity", 600) }

    val cacheEvictionPeriod: Duration
            by lazy { getValue("run.cacheEvictionPeriod", cacheValidity.seconds / 2) }

    val cacheRewardsMaxSize: Int
            by lazy { getValue("run.cacheRewardsMaxSize", 100) }

    val cacheCampaignsMaxSize: Int
            by lazy { getValue("run.cacheCampaignsMaxSize", cacheRewardsMaxSize) }

    private inline fun <reified T, reified R> getValue(key: String, default: R) = let {
        // Environment variables take precedence over any config file. If the corresponding env variable is not set,
        // this will fall back to reading from the config file. If it doesn't exist there either, the provided
        // default value will be used.
        val value = System.getenv(configKeyToEnvKey(key)) ?: configStore.getProperty(
            key, when (R::class) {
                Duration::class -> (default as Duration).seconds.toString()
                Instant::class -> (default as Instant).toEpochMilli().toString()
                else -> default.toString()
            }
        )

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

    private fun configKeyToEnvKey(key: String) = key
        .replace('.', '_')
        .camelToScreamingSnakeCase()
}
