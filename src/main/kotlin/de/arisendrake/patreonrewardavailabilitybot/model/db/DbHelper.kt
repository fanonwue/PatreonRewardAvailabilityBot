package de.arisendrake.patreonrewardavailabilitybot.model.db

import de.arisendrake.patreonrewardavailabilitybot.Config
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import javax.sql.DataSource

object DbHelper {
    val dataSource: DataSource by lazy {
        SQLiteDataSource().apply {
            url = "jdbc:sqlite:${Config.databasePath}"
        }
    }
    val db by lazy {
        val dbConfig = DatabaseConfig {
            //useNestedTransactions = true
            defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED
        }
        Database.connect(
            dataSource,
            databaseConfig = dbConfig
        ).apply {
            transaction {
                SchemaUtils.createMissingTablesAndColumns(Chats, RewardEntries)
            }
        }
    }

}