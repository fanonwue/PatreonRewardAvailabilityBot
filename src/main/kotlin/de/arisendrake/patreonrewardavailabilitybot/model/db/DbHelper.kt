package de.arisendrake.patreonrewardavailabilitybot.model.db

import de.arisendrake.patreonrewardavailabilitybot.Config
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

object DbHelper {
    val db by lazy {
        val dbConfig = DatabaseConfig {
            //useNestedTransactions = true
            defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED
        }
        Database.connect(
            "jdbc:sqlite:${Config.databasePath}",
            "org.sqlite.JDBC",
            databaseConfig = dbConfig
        ).also {
            transaction {
                SchemaUtils.createMissingTablesAndColumns(Chats, RewardEntries)
            }
        }
    }
}