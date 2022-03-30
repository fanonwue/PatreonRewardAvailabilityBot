package de.arisendrake.patreonrewardavailabilitybot.model

import de.arisendrake.patreonrewardavailabilitybot.Config
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

object RewardObservationList {
    val rewardSet get() = synchronized(rewardSetInternal) { rewardSetInternal.toSet() }
    private val file = Config.rewardsListFile
    private val rewardSetInternal = mutableSetOf<Long>()
    private val serializer = Config.jsonSerializer
    private val charset = Charsets.UTF_8

    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(RewardObservationList.javaClass)

    init {
        synchronized(rewardSetInternal) {
            if (!file.exists()) saveToFile()
            readFromFile()
        }
    }

    private fun saveToFile() {
        serializer.encodeToString(rewardSetInternal).let {
            file.outputStream().use { stream ->
                stream.bufferedWriter(charset).use { writer ->
                    writer.write(it)
                }
            }
        }
        logger.info("Saved ${rewardSet.size} Reward IDs to disk")
    }

    fun add(rewardList: Collection<Long>) = synchronized(rewardSetInternal) {
        rewardSetInternal.addAll(rewardList)
        saveToFile()
    }

    fun add(rewardId: Long) = add(listOf(rewardId))

    fun remove(rewardList: Collection<Long>) = synchronized(rewardSetInternal) {
        rewardSetInternal.removeAll(rewardList.toSet())
        saveToFile()
    }

    fun remove(rewardId: Long) = remove(listOf(rewardId))


    private fun readFromFile() {
        file.inputStream().use { stream ->
            val reader = stream.bufferedReader(charset)
            rewardSetInternal.clear()
            rewardSetInternal.addAll(serializer.decodeFromString<Set<Long>>(
                reader.readText()
            ))
            reader.close()
        }
        logger.info("Read ${rewardSet.size} Reward IDs from disk")
    }
}