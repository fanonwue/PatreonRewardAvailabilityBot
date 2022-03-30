package de.arisendrake.patreonrewardavailabilitybot.model

import de.arisendrake.patreonrewardavailabilitybot.Config
import de.arisendrake.patreonrewardavailabilitybot.removeAll
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

object RewardObservationList {
    val rewardMap get() = synchronized(rewardMapInternal) { rewardMapInternal.toMap() }
    private val file = Config.rewardsListFile
    private val rewardMapInternal = mutableMapOf<Long, RewardEntry>().withDefault { RewardEntry(it, null) }
    private val serializer = Config.jsonSerializer
    private val charset = Charsets.UTF_8
    private val currentDataVersion = 2

    @JvmStatic
    private val logger: Logger = LoggerFactory.getLogger(RewardObservationList.javaClass)

    init {
        synchronized(rewardMapInternal) {
            if (!file.exists()) saveToFile()
            readFromFile()
        }
    }

    private fun saveToFile() {
        val rewardsData = RewardsDataV2(currentDataVersion, rewardMapInternal.values.toList())
        serializer.encodeToString(rewardsData).let {
            file.outputStream().use { stream ->
                stream.bufferedWriter(charset).use { writer ->
                    writer.write(it)
                }
            }
        }
        logger.info("Saved ${rewardMap.size} Reward IDs to disk")
    }

    fun add(rewardList: Iterable<RewardEntry>) = synchronized(rewardMapInternal) {
        rewardMapInternal.putAll(rewardList.associateBy { it.id })
        saveToFile()
    }


    @JvmName("addIds")
    fun add(rewardList: Iterable<Long>) = add(rewardList.map { RewardEntry(it, null) })

    fun add(rewardEntry: RewardEntry) = add(listOf(rewardEntry))

    fun add(rewardId: Long) = add(RewardEntry(rewardId))

    fun update(rewardList: Iterable<RewardEntry>) = add(rewardList)

    fun update(rewardEntry: RewardEntry) = add(rewardEntry)

    fun remove(rewardList: Iterable<RewardEntry>) = remove(rewardList.map { it.id })

    fun remove(rewardEntry: RewardEntry) = remove(listOf(rewardEntry))

    fun remove(rewardId: Long) = remove(RewardEntry(rewardId))

    @JvmName("removeIds")
    fun remove(rewardList: Iterable<Long>) = synchronized(rewardMapInternal) {
        rewardMapInternal.removeAll(rewardList)
        saveToFile()
    }


    private fun readFromFile() {
        file.inputStream().use { stream ->
            val reader = stream.bufferedReader(charset)
            val text = reader.readText()
            reader.close()
            rewardMapInternal.clear()
            if (text.isNotBlank()) {
                val rewardsDataVersion = serializer.decodeFromString<RewardsDataVersion>(text)
                val rewardsData = when (rewardsDataVersion.dataVersion) {
                    2 -> parseRewardsDataV2(text)
                    else -> migrateDataV1toV2(text)
                }
                rewardMapInternal.putAll(rewardsData.data.associateBy { it.id })
            }
        }
        logger.info("Read ${rewardMap.size} Reward IDs from disk")
    }

    private fun migrateDataV1toV2(rewardString: String) = let {
        logger.info("Found V1 data format, migrating to V2")
        val dataV1 = parseRewardsDataV1(rewardString)
        RewardsDataV2(2, dataV1.data.map { RewardEntry(it) })
    }

    private fun parseRewardsDataV1(rewardString: String) = parseRewardsData<RewardsDataV1>(rewardString)

    private fun parseRewardsDataV2(rewardString: String) = parseRewardsData<RewardsDataV2>(rewardString)

    private inline fun <reified T> parseRewardsData(rewardString: String) = serializer.decodeFromString<T>(rewardString)
}