package de.arisendrake.patreonrewardavailabilitybot.model

import de.arisendrake.patreonrewardavailabilitybot.Config
import de.arisendrake.patreonrewardavailabilitybot.removeAll
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import mu.KotlinLogging
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.exists

object RewardObservationList {

    @JvmStatic
    private val logger = KotlinLogging.logger {  }

    val rewardMap get() = synchronized(rewardMapInternal) { rewardMapInternal.toMap() }
    val rewards get() = rewardMap.values
    private const val currentDataVersion = 2
    private val file = Config.rewardsListFile
    private val rewardMapInternal = mutableMapOf<Long, RewardEntry>()
    private val serializer = Config.jsonSerializer
    private val charset = Config.charset

    init {
        synchronized(rewardMapInternal) {
            if (!file.exists()) saveToFile()
            readFromFile()
        }
    }

    private fun saveToFile() {
        val rewardsData = RewardsDataV2(currentDataVersion, rewardMapInternal.values.toList())
        file.bufferedWriter(charset).use {
            it.write(serializer.encodeToString(rewardsData))
        }
        logger.debug { "Saved ${rewardsData.data.size} Reward IDs to disk" }
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
        logger.info { "Removed reward entries: $rewardList" }
    }


    private fun readFromFile() {
        val text = file.bufferedReader(charset).use { it.readText() }
        rewardMapInternal.clear()
        if (text.isNotBlank()) {
            val rewardsDataVersion = serializer.decodeFromString<RewardsDataVersion>(text)
            val rewardsData = when (rewardsDataVersion.dataVersion) {
                2 -> parseRewardsDataV2(text)
                else -> migrateDataV1toV2(text)
            }
            rewardMapInternal.putAll(rewardsData.data.associateBy { it.id })
        }
        logger.info { "Read ${rewardMap.size} Reward IDs from disk" }
    }

    private fun migrateDataV1toV2(rewardString: String) = let {
        logger.info { "Found V1 data format, migrating to V2" }
        val dataV1 = parseRewardsDataV1(rewardString)
        RewardsDataV2(2, dataV1.data.map { RewardEntry(it) })
    }

    private fun parseRewardsDataV1(rewardString: String) = parseRewardsData<RewardsDataV1>(rewardString)

    private fun parseRewardsDataV2(rewardString: String) = parseRewardsData<RewardsDataV2>(rewardString)

    private inline fun <reified T> parseRewardsData(rewardString: String) = serializer.decodeFromString<T>(rewardString)
}