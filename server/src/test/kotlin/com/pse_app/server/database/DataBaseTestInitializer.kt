package com.pse_app.server.database

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.common.util.BigDec
import com.pse_app.server.data.Result
import org.ktorm.database.Database
import org.ktorm.dsl.insert
import java.io.IOException
import java.io.UncheckedIOException
import java.time.Instant
import java.util.*

object DataBaseTestInitializer {
    
    fun connectTestDatabase(): Database {
        return when (val db = DatabaseInitializer.connectTransientDatabase(Currency.getInstance("USD"))) {
            is Result.Success -> db.value
            is Result.Error -> {
                val ex = IOException("Test DB connection error: ${db.message}")
                ex.fillInStackTrace()
                throw UncheckedIOException(ex)
            }
        }
    }
    
    fun createTestUser(db: Database, id: UserId, name: String? = null, lastLogin: Instant? = null): Unit = db.useTransaction {
        db.insert(Tables.Users) {
            set(it.id, id)
            set(it.lastLogin, lastLogin ?: Instant.now())
        }
        db.insert(Tables.ActiveUsers) {
            set(it.id, id)
            set(it.displayName, name ?: id.id)
        }
    }
    
    fun createDeactivatedTestUser(db: Database, id: UserId, lastLogin: Instant? = null): Unit = db.useTransaction {
        db.insert(Tables.Users) {
            set(it.id, id)
            set(it.lastLogin, lastLogin ?: Instant.now())
        }
    }
    
    fun createTestGroup(db: Database, id: GroupId, name: String? = null, inviteToken: String? = null): Unit = db.useTransaction {
        db.insert(Tables.Groups) {
            set(it.id, id)
            set(it.displayName, name ?: id.id.toString())
            set(it.inviteToken, inviteToken ?: id.id.toString())
        }
    }
    
    fun createTestMembership(db: Database, user: UserId, group: GroupId): Unit = db.useTransaction {
        db.insert(Tables.Membership) {
            set(it.userId, user)
            set(it.groupId, group)
        }
    }
    
    fun createSimpleTestTransaction(
        db: Database, id: UUID,
        group: GroupId,
        user1: UserId,
        user2: UserId,
        amount: BigDec = BigDec(1),
        name: String = "Test transaction",
        comment: String? = null,
        timestamp: Instant = Instant.EPOCH
    ): Unit = db.useTransaction {
        db.insert(Tables.Transactions) {
            set(it.id, id)
            set(it.groupId, group)
            set(it.name, name)
            set(it.comment, comment)
            set(it.timestamp, timestamp)
            set(it.originatingUser, user1)
            set(it.expenseTotal, null)
        }
        db.insert(Tables.BalanceChanges) {
            set(it.userId, user1)
            set(it.transactionId, id)
            set(it.amount, -amount)
        }
        db.insert(Tables.BalanceChanges) {
            set(it.userId, user2)
            set(it.transactionId, id)
            set(it.amount, amount)
        }
    }
}
