package com.pse_app.client.model.repositories

import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.TransactionData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.client.model.testing.MockRepo
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

class DerivedBalancesTest {
    private fun makeTransaction(balances: Map<UserId, BigDec>): TransactionData {
        return TransactionData.Payment("", "", Instant.now(), balances, UserId(""))
    }

    @Test
    fun testDerivedBalances() = runTest {
        val repo = MockRepo()
        val gid = GroupId("1")
        val uid1 = UserId("1")
        val uid2 = UserId("2")
        val uid3 = UserId("3")
        repo.transactionRepo.setTransactions(gid, listOf(
            makeTransaction(mapOf(uid1 to BigDec(1), uid2 to BigDec(-2), uid3 to BigDec(1000))),
            makeTransaction(mapOf(uid1 to BigDec("1.25"), uid2 to BigDec(-100), uid3 to BigDec(-1000))),
            makeTransaction(mapOf(uid1 to BigDec("0.1"), uid2 to BigDec("-0.11"), uid3 to BigDec(1000)))
        ))
        assertEquals(
            mapOf(uid1 to BigDec("2.35"), uid2 to BigDec("-102.11"), uid3 to BigDec(1000)),
            repo.transactionRepo.getDerivedBalances(gid)
        )
    }
}
