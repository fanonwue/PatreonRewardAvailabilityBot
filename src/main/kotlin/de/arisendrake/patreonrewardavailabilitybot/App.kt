package de.arisendrake.patreonrewardavailabilitybot

import de.arisendrake.patreonrewardavailabilitybot.model.db.DbHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import io.github.oshai.kotlinlogging.KotlinLogging

object App {

    @JvmStatic
    private val logger = KotlinLogging.logger {  }

    private val fetcher = PatreonFetcher(Config.sharedHttpClient)

    private val scope by lazy {
        CoroutineScope(Dispatchers.Default)
    }

    private val context get() = scope.coroutineContext

    private val notificationTelegramBot = TelegramBot(
            Config.telegramApiKey,
            fetcher,
            Config.sharedHttpClient
    )


    private val availabilityChecker = AvailabilityChecker(
        fetcher,
        notificationTelegramBot
    )

    fun run() {
        DbHelper.db

        logger.info { "Starting Telegram Bot" }
        notificationTelegramBot.start()

        if (Config.useFetchCache) {
            logger.info { "Starting cache eviction background job, cache validity is at ${Config.cacheValidity.seconds}s" }
            fetcher.startCacheEviction()
        } else {
            logger.info { "Fetch cache disabled globally, skipping eviction job scheduling" }
        }

        logger.info {"""
            Starting coroutine scheduler with an interval of ${Config.interval.seconds}s and with an initial
            delay of ${Config.initialDelay.seconds}s
        """.trimIndent().withoutNewlines()
        }

        // Wait for the configured initial delay
        Thread.sleep(Config.initialDelay.toMillis())

        while (true) {
            availabilityChecker.check()
            Thread.sleep(Config.interval.toMillis())
        }
    }
}