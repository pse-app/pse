package com.pse_app.server.transaction

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.common.util.BigDec
import com.pse_app.server.data.*
import com.pse_app.server.data.config.Config
import com.pse_app.server.data.config.ConfigKey
import com.pse_app.server.database.PostgreSQL
import com.pse_app.server.database.SqlTypes
import com.pse_app.server.database.Tables
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.Column
import org.ktorm.schema.DecimalSqlType
import org.ktorm.schema.TextSqlType
import java.math.BigInteger
import java.math.RoundingMode
import java.util.*

private typealias UserBalances = Map<Pair<UserId, GroupId>, BigDec>

/**
 * The Transaction Manager implements [TransactionManagement].
 */
class TransactionManager(private val config: Config, private val db: Database) : TransactionManagement {

    override fun getTransactions(group: GroupId): Result<List<Transaction>> = db.useTransaction {
        logger.trace("getTransactions(group={})", group)
        if (!allExist(Tables.Groups.id, setOf(group))) return Result.Error(ErrorCategory.GroupNotFound, "group not found")

        val balanceChanges = PostgreSQL.aggregateHStore(
            Tables.BalanceChanges.userId.cast(TextSqlType),
            Tables.BalanceChanges.amount.cast(DecimalSqlType).cast(TextSqlType)
        ).aliased("balance_change_map")
        
        fun loadTransaction(row: QueryRowSet): Transaction {
            val balanceChangeMap = row[balanceChanges]!!.map { UserId(it.key) to BigDec(it.value) }.toMap()
            return when (val expenseTotal = row[Tables.Transactions.expenseTotal]) {
                null -> Payment(
                    row[Tables.Transactions.groupId]!!,
                    row[Tables.Transactions.name]!!,
                    row[Tables.Transactions.comment],
                    row[Tables.Transactions.timestamp]!!,
                    balanceChangeMap,
                    row[Tables.Transactions.originatingUser]!!
                )
                else -> Expense(
                    row[Tables.Transactions.groupId]!!,
                    row[Tables.Transactions.name]!!,
                    row[Tables.Transactions.comment],
                    row[Tables.Transactions.timestamp]!!,
                    balanceChangeMap,
                    row[Tables.Transactions.originatingUser]!!,
                    expenseTotal
                )
            }
        }
        
        Result.Success(db.from(Tables.Transactions)
            .leftJoin(Tables.BalanceChanges, on = Tables.Transactions.id eq Tables.BalanceChanges.transactionId)
            .select(Tables.Transactions.columns.plus(balanceChanges))
            .where(Tables.Transactions.groupId eq group)
            .groupBy(Tables.Transactions.id)
            .orderBy(Tables.Transactions.timestamp.asc())
            .map { row -> loadTransaction(row) })
    }
    
    override fun postTransactions(transactions: List<Transaction>): Result<Unit> = db.useTransaction {
        logger.trace("postTransactions(transactions={})", transactions)
        if (transactions.isEmpty()) return Result.Success(Unit)
        return checkTransactions(transactions).andThen { insertTransactionsUnchecked(transactions) }
    }
    
    private fun checkTransactions(transactions: List<Transaction>): Result<Unit> = db.useTransaction {
        for (transaction in transactions) {
            if (transaction.balanceChanges.values.any { !isValidBalance(it) }) {
                return Result.Error(ErrorCategory.RejectedInput, "numeric value of balance change rejected")
            }
            if (transaction is Expense && !isValidBalance(transaction.expenseAmount)) {
                return Result.Error(ErrorCategory.RejectedInput, "numeric value of expense total rejected")
            }
            if (BigDec(transaction.balanceChanges.values.sumOf { it.value }) != BigDec(0)) {
                return Result.Error(ErrorCategory.RejectedInput, "unbalanced transaction")
            }
        }

        val referencedUsers: Set<UserId> = transactions.flatMap { it.balanceChanges.keys.plus(it.originatingUser) }.toSet()
        val referencedGroups: Set<GroupId> = transactions.map { it.group }.toSet()

        if (!allExist(Tables.Users.id, referencedUsers)) {
            return Result.Error(ErrorCategory.UserNotFound, "invalid user reference")
        }

        if (!allExist(Tables.Groups.id, referencedGroups)) {
            return Result.Error(ErrorCategory.UserNotFound, "invalid group reference")
        }

        val memberships: Set<Pair<UserId, GroupId>> = when (referencedUsers.isEmpty() || referencedGroups.isEmpty()) {
            true -> setOf()
            false -> db.from(Tables.Membership)
                .select(Tables.Membership.userId, Tables.Membership.groupId)
                .where { (Tables.Membership.userId inList referencedUsers) and (Tables.Membership.groupId inList referencedGroups) }
                .map { it[Tables.Membership.userId]!! to it[Tables.Membership.groupId]!! }
                .toSet()
        }

        for (transaction in transactions) {
            if (!memberships.contains(transaction.originatingUser to transaction.group)) {
                return Result.Error(ErrorCategory.UserNotFound, "invalid user reference")
            }
            if (!transaction.balanceChanges.keys.all { memberships.contains(it to transaction.group) }) {
                return Result.Error(ErrorCategory.UserNotFound, "invalid user reference")
            }
        }
        
        return Result.Success(Unit)
    }
    
    private fun insertTransactionsUnchecked(transactions: List<Transaction>): Result<Unit> = db.useTransaction {
        val transactionsWithIds: List<Pair<UUID, Transaction>> = transactions.map { UUID.randomUUID() to it }

        fun insertTransaction(builder: BatchInsertStatementBuilder<Tables.Transactions>, value: Pair<UUID, Transaction>) {
            val (transactionId, transaction) = value
            builder.item {
                set(Tables.Transactions.id, transactionId)
                set(Tables.Transactions.groupId, transaction.group)
                set(Tables.Transactions.name, transaction.name)
                set(Tables.Transactions.comment, transaction.comment)
                set(Tables.Transactions.timestamp, transaction.timestamp)
                set(Tables.Transactions.originatingUser, transaction.originatingUser)
                when (transaction) {
                    is Expense -> set(Tables.Transactions.expenseTotal, transaction.expenseAmount)
                    else -> set(Tables.Transactions.expenseTotal, null)
                }
            }
        }

        fun insertBalanceChanges(builder: BatchInsertStatementBuilder<Tables.BalanceChanges>, value: Pair<UUID, Transaction>) {
            val (transactionId, transaction) = value
            for ((user, amount) in transaction.balanceChanges) {
                builder.item {
                    set(Tables.BalanceChanges.transactionId, transactionId)
                    set(Tables.BalanceChanges.userId, user)
                    set(Tables.BalanceChanges.amount, amount)
                }
            }
        }

        if (transactions.isNotEmpty()) {
            db.batchInsert(Tables.Transactions) {
                for (value in transactionsWithIds) insertTransaction(this, value)
            }
        }

        if (transactions.any { it.balanceChanges.isNotEmpty() }) {
            db.batchInsert(Tables.BalanceChanges) {
                for (value in transactionsWithIds) insertBalanceChanges(this, value)
            }
        }
        
        return Result.Success(Unit)
    }

    override fun getBalances(users: Set<UserId>, groups: Set<GroupId>): Result<UserBalances> = db.useTransaction {
        logger.trace("getBalances(users={}, groups={})", users, groups)
        // needed because ktorm generates a sql syntax error when using inList on an empty collection
        if (users.isEmpty() || groups.isEmpty()) return Result.Success(mapOf())
        if (!allExist(Tables.Users.id, users)) return Result.Error(ErrorCategory.UserNotFound, "user not found")
        if (!allExist(Tables.Groups.id, groups)) return Result.Error(ErrorCategory.GroupNotFound, "group not found")
        
        val balanceChangeSum = PostgreSQL.coalesce(SqlTypes.BigDecSqlType, listOf(
            sum(Tables.BalanceChanges.amount),
            PostgreSQL.bigDecExpression(BigDec(0))
        )).aliased("balance_change_sum")
        
        return Result.Success(db.from(Tables.Membership)
            .leftJoin(
                Tables.Transactions,
                on = Tables.Membership.groupId eq Tables.Transactions.groupId
            )
            .leftJoin(
                Tables.BalanceChanges,
                on = (Tables.Transactions.id eq Tables.BalanceChanges.transactionId)
                        and (Tables.Membership.userId eq Tables.BalanceChanges.userId)
            )
            .select(Tables.Membership.groupId, Tables.Membership.userId, balanceChangeSum)
            .where((Tables.Membership.userId inList users) and (Tables.Membership.groupId inList groups))
            .groupBy(Tables.Membership.groupId, Tables.Membership.userId)
            .map { row -> (row[Tables.Membership.userId]!! to row[Tables.Membership.groupId]!!) to row[balanceChangeSum]!! }
            .toMap())
    }

    private fun <C : Any> allExist(column: Column<C>, values: Set<C>): Boolean {
        // needed because ktorm generates a sql syntax error when using inList on an empty collection
        if (values.isEmpty()) return true
        val existing = db.from(column.table)
            .select(column)
            .where(column inList values)
            .map { row -> row[column] }
            .toSet()
        return values.minus(existing).isEmpty()
    }

    private val maxUnscaled = BigInteger("100000000000000000000000000000000000000000000000000")
    private fun isValidBalance(value: BigDec): Boolean {
        val scaled = BigDec(value.value.setScale(config[ConfigKey.Currency].effectiveFractionDigits, RoundingMode.DOWN))
        if (value != scaled) return false
        return scaled.value.unscaledValue() <= maxUnscaled
    }
    
    companion object {
        private val logger: Logger = LogManager.getLogger()
    }
}
