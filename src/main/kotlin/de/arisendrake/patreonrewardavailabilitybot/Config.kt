package de.arisendrake.patreonrewardavailabilitybot

import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties

object Config {

    val configStore by lazy {
        val props = Properties()

        val configPath = let {
            var path = System.getenv("CONFIG_PATH")
            if (path == null || path.isEmpty()) path = "config/config.ini"
            Paths.get(path)
        }

        configPath?.let {
            FileInputStream(it.toFile()).use {
                props.load(it)
            }
        }

        props
    }

    val telegramCreatorId get() = getLong("telegram.creatorId", 0)

    val telegramApiKey get() = getString("telegram.api.key", "")

    val interval: Duration get() = getDuration("run.interval", 300)

    val initialDelay: Duration get() = getDuration("run.initialDelay", 5)

    val baseDomain get() = getString("baseDomain", "https://www.patreon.com")

    val rewardsListFile get() = getPath("run.rewardsListFile", "data/rewards.json")

    val jsonSerializer get() = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }


    private fun getString(key: String, default: String = "")
        = configStore.getProperty(key, default).toString()

    private fun getLong(key: String, default: Long = 0)
        = configStore.getProperty(key, default.toString()).toLong()

    private fun getInt(key: String, default: Int = 0)
        = configStore.getProperty(key, default.toString()).toInt()

    private fun getBoolean(key: String, default: Boolean = false)
        = configStore.getProperty(key, default.toString()).toBoolean()

    private fun getDuration(key: String, default: Long = 0)
        = Duration.ofSeconds(getLong(key, default))

    private fun getDuration(key: String, default: Duration = Duration.ZERO)
        = getDuration(key, default.seconds)

    private fun getPath(key: String, default: String = "")
            = Paths.get(configStore.getProperty(key, default))
}
