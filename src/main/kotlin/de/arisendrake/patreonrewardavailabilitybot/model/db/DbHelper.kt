package de.arisendrake.patreonrewardavailabilitybot.model.db

import de.arisendrake.patreonrewardavailabilitybot.Config
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
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

    private val dbConfig = DatabaseConfig {
        //useNestedTransactions = true
        defaultIsolationLevel = Connection.TRANSACTION_READ_UNCOMMITTED
    }

    val db by lazy {
        Database.connect(
            dataSource,
            databaseConfig = dbConfig
        ).apply {
            TransactionManager.defaultDatabase = this
            transaction {
                SchemaUtils.createMissingTablesAndColumns(Chats, RewardEntries)
            }
        }
    }
}