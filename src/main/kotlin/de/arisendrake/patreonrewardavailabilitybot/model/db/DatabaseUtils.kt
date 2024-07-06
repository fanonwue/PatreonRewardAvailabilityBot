package de.arisendrake.patreonrewardavailabilitybot.model.db

import de.arisendrake.patreonrewardavailabilitybot.Config
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.withSuspendTransaction

suspend fun <T> newSuspendedTransactionSingleThreaded(
    db: Database? = null,
    transactionIsolation: Int? = null,
    statement: suspend Transaction.() -> T
) = newSuspendedTransaction(Config.dbContext, db, transactionIsolation, statement)

suspend fun <T> Transaction.withSuspendTransactionSingleThreaded(
    statement: suspend Transaction.() -> T
): T = withSuspendTransaction(Config.dbContext, statement)