package de.arisendrake.patreonrewardavailabilitybot.model.db

import de.arisendrake.patreonrewardavailabilitybot.Config
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
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
        ).apply {
            transaction {
                SchemaUtils.createMissingTablesAndColumns(Chats, RewardEntries)
            }
        }
    }

}