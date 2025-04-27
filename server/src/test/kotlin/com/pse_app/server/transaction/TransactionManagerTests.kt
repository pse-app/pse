package com.pse_app.server.transaction

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.common.util.BigDec
import com.pse_app.server.assertions.assertError
import com.pse_app.server.assertions.assertSuccess
import com.pse_app.server.data.ErrorCategory
import com.pse_app.server.data.Expense
import com.pse_app.server.data.Payment
import com.pse_app.server.data.config.Config
import com.pse_app.server.data.config.ConfigKey
import com.pse_app.server.database.DataBaseTestInitializer
import com.pse_app.server.database.Tables
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.ktorm.database.Database
import org.ktorm.dsl.from
import org.ktorm.dsl.map
import org.ktorm.dsl.select
import java.time.Instant
import java.util.*

@ExtendWith(MockKExtension::class)
class TransactionManagerTests {
    
    @MockK
    private lateinit var config: Config
    private lateinit var db: Database
    private lateinit var tm: TransactionManagement
    
    @Test
    fun balance_is_zero_for_no_transactions() {
        assertSuccess(tm.getBalances(setOf(User1), setOf(GroupA))) {
            assertEquals(BigDec(0), it[User1 to GroupA], "Balance is not zero for empty transaction list.")
        }
    }

    @Test
    fun balances_map_has_entry_iff_user_is_member() {
        assertSuccess(tm.getBalances(setOf(User1, User3), setOf(GroupA, GroupB))) {
            assertTrue(it.containsKey(User1 to GroupA), "Missing balance in getBalances.")
            assertFalse(it.containsKey(User2 to GroupA), "Unrelated user has an entry in the getBalances map.")
            assertTrue(it.containsKey(User3 to GroupA), "Missing balance in getBalances.")
            assertTrue(it.containsKey(User1 to GroupB), "Missing balance in getBalances.")
            assertFalse(it.containsKey(User2 to GroupB), "Unrelated user has an entry in the getBalances map.")
            assertFalse(it.containsKey(User3 to GroupB), "User-Group pair that is no membership has an entry in the getBalances map.")
        }
    }

    @Test
    fun get_balances_yields_correct_balances() {
        assertSuccess(tm.postTransactions(listOf(TransactionA_1to2_7, TransactionB_2to1_1dot5)))
        assertSuccess(tm.getBalances(setOf(User1, User2, User3), setOf(GroupA, GroupB))) {
            assertEquals(BigDec("-7"), it[User1 to GroupA])
            assertEquals(BigDec("7"), it[User2 to GroupA])
            assertEquals(BigDec("0"), it[User3 to GroupA])
            assertEquals(BigDec("1.5"), it[User1 to GroupB])
            assertEquals(BigDec("-1.5"), it[User2 to GroupB])
        }
        assertSuccess(tm.postTransactions(listOf(TransactionA_ExpenseByUser3_15)))
        val emptyMap = mapOf<Pair<UserId, GroupId>, BigDec>()
        assertSuccess(tm.getBalances(setOf(), setOf())) { assertEquals(it, emptyMap) }
        assertSuccess(tm.getBalances(setOf(), setOf(GroupA, GroupB))) { assertEquals(it, emptyMap) }
        assertSuccess(tm.getBalances(setOf(User1, User2), setOf())) { assertEquals(it, emptyMap) }
        assertSuccess(tm.getBalances(setOf(User1, User2, User3), setOf(GroupA, GroupB))) {
            assertEquals(BigDec("-12"), it[User1 to GroupA])
            assertEquals(BigDec("2"), it[User2 to GroupA])
            assertEquals(BigDec("10"), it[User3 to GroupA])
            assertEquals(BigDec("1.5"), it[User1 to GroupB])
            assertEquals(BigDec("-1.5"), it[User2 to GroupB])
        }
    }
    
    @Test
    fun post_transaction_fails_if_there_is_an_unbalanced_transaction() {
        assertError(ErrorCategory.RejectedInput, tm.postTransactions(listOf(TransactionA_1to2_7, TransactionWithNonZeroBalanceSum)))
    }
    
    @Test
    fun post_transaction_writes_correct_payment_distinction_to_db() {
        assertSuccess(tm.postTransactions(listOf(TransactionA_1to2_7)))
        val payments = db.from(Tables.Transactions).select(Tables.Transactions.columns).map {
            assertNull(it[Tables.Transactions.expenseTotal], "Payment has a non-null total in database.")
            Payment(
                it[Tables.Transactions.groupId]!!,
                it[Tables.Transactions.name]!!,
                it[Tables.Transactions.comment],
                it[Tables.Transactions.timestamp]!!,
                mapOf(),
                it[Tables.Transactions.originatingUser]!!,
            )
        }
        assertEquals(listOf(TransactionA_1to2_7_ButWithoutAnyBalanceChanges), payments, "Payment was incorrectly written to database.")
    }
    
    @Test
    fun get_transactions_reads_transactions_correctly() {
        assertSuccess(tm.postTransactions(listOf(TransactionA_1to2_7, TransactionA_ExpenseByUser3_15)))
        assertSuccess(tm.getTransactions(GroupA)) {
            assertEquals(setOf(TransactionA_1to2_7, TransactionA_ExpenseByUser3_15), it.toSet(), "Transactions were re-read incorrectly.")
        }
    }
    
    @Test
    fun get_transactions_sorts_transactions_by_time_for_unsorted_insert() {
        assertSuccess(tm.postTransactions(listOf(TransactionA_ExpenseByUser3_15, TransactionB_2to1_1dot5)))
        assertSuccess(tm.getTransactions(GroupA))
        assertSuccess(tm.postTransactions(listOf(TransactionA_1to2_7)))
        assertSuccess(tm.getTransactions(GroupA)) {
            assertEquals(listOf(TransactionA_1to2_7, TransactionA_ExpenseByUser3_15), it, "Invalid transaction ordering.")
        }
        assertSuccess(tm.getTransactions(GroupB)) {
            assertEquals(listOf(TransactionB_2to1_1dot5), it, "Transactions were re-read incorrectly.")
        }
    }

    @Test
    fun post_transaction_accepts_transactions_without_balance_changes() {
        assertSuccess(tm.postTransactions(listOf(TransactionA_1to2_7_ButWithoutAnyBalanceChanges)), "Transaction without balance changes was rejected.")
        assertSuccess(tm.getTransactions(GroupA)) {
            assertEquals(listOf(TransactionA_1to2_7_ButWithoutAnyBalanceChanges), it, "Transaction without balance changes was incorrectly re-read.")
        }
    }

    @Test
    fun post_transaction_rejects_transactions_with_too_high_scale() {
        assertError(ErrorCategory.RejectedInput, tm.postTransactions(listOf(TransactionA_1to2_WithTooHighScale)), "Transaction with too high scale was not properly rejected.")
    }

    @Test
    fun post_transaction_rejects_transactions_with_too_much_money() {
        assertError(ErrorCategory.RejectedInput, tm.postTransactions(listOf(TransactionA_1to2_WithTooMuchMoney)), "Transaction with too much money was not properly rejected.")
    }

    @Test
    fun post_transaction_rejects_transaction_containing_non_member() {
        assertError(ErrorCategory.UserNotFound, tm.postTransactions(listOf(TransactionA_1to2_7, TransactionB_1to3_NonGroupMember)), "Transaction using non-member was not properly rejected.")
    }
    
    @BeforeEach
    fun setup() {
        every { config[ConfigKey.Currency] } returns Currency.getInstance("USD")
        db = DataBaseTestInitializer.connectTestDatabase()
        db.useTransaction {
            DataBaseTestInitializer.createTestUser(db, User1)
            DataBaseTestInitializer.createTestUser(db, User2)
            DataBaseTestInitializer.createTestUser(db, User3)
            DataBaseTestInitializer.createTestGroup(db, GroupA)
            DataBaseTestInitializer.createTestGroup(db, GroupB)
            DataBaseTestInitializer.createTestMembership(db, User1, GroupA)
            DataBaseTestInitializer.createTestMembership(db, User2, GroupA)
            DataBaseTestInitializer.createTestMembership(db, User3, GroupA)
            DataBaseTestInitializer.createTestMembership(db, User1, GroupB)
            DataBaseTestInitializer.createTestMembership(db, User2, GroupB)
        }
        tm = TransactionManager(config, db)
    }
    
    companion object {
        private val User1: UserId = UserId("user1")
        private val User2: UserId = UserId("user2")
        private val User3: UserId = UserId("user3")
        private val GroupA: GroupId = GroupId(UUID.fromString("98f35d10-fa20-440f-8fb1-7d28d0a0364a"))
        private val GroupB: GroupId = GroupId(UUID.fromString("dfffd274-8d73-4a96-b15b-50725574bdd8"))
        
        private val TransactionA_1to2_7: Payment = Payment(
            GroupA, "A: 1->2: 7€", null, Instant.EPOCH,
            mapOf(User1 to BigDec("-7"), User2 to BigDec("7")),
            User1
        )
        
        private val TransactionA_1to2_7_ButWithoutAnyBalanceChanges: Payment = Payment(
            TransactionA_1to2_7.group, TransactionA_1to2_7.name, TransactionA_1to2_7.comment, TransactionA_1to2_7.timestamp,
            mapOf(), TransactionA_1to2_7.originatingUser
        )

        private val TransactionA_ExpenseByUser3_15: Expense = Expense(
            GroupA, "A: expense of 15€, fair split", "I am a comment!", Instant.EPOCH.plusSeconds(2000),
            mapOf(User1 to BigDec("-5"), User2 to BigDec("-5"), User3 to BigDec("10")),
            User3, BigDec(15)
        )

        private val TransactionB_2to1_1dot5: Payment = Payment(
            GroupB, "B: 2->1: 1.5€", null, Instant.EPOCH.plusSeconds(5000),
            mapOf(User1 to BigDec("1.5"), User2 to BigDec("-1.5")),
            User2
        )
        
        private val TransactionWithNonZeroBalanceSum: Payment = Payment(
            GroupA, "non zero balance sum", null, Instant.EPOCH.plusSeconds(3000),
            mapOf(User1 to BigDec("-12"), User2 to BigDec("6"), User3 to BigDec("5")),
            User2
        )
        
        private val TransactionA_1to2_WithTooHighScale: Payment = Payment(
            GroupA, "A: 1->2: 7€", null, Instant.EPOCH,
            mapOf(User1 to BigDec("-7.001"), User2 to BigDec("7.001")),
            User1
        )
        
        private val TransactionA_1to2_WithTooMuchMoney: Payment = Payment(
            GroupA, "A: 1->2: 7€", null, Instant.EPOCH,
            mapOf(
                User1 to BigDec("-700000000000000000000000000000000000000000000000000"),
                User2 to BigDec("700000000000000000000000000000000000000000000000000")
            ),
            User1
        )
        
        private val TransactionB_1to3_NonGroupMember: Payment = Payment(
            GroupB, "B: 1->3: 2€", null, Instant.EPOCH,
            mapOf(User1 to BigDec("-2"), User3 to BigDec("2")),
            User1
        )
    }
}
