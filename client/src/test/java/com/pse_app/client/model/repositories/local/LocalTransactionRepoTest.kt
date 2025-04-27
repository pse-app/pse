package com.pse_app.client.model.repositories.local

import com.pse_app.client.model.getRandomString
import com.pse_app.client.model.getRandomUid
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.TransactionData
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class LocalTransactionRepoTest {
    private lateinit var repo: LocalTransactionRepo
    private lateinit var random: Random

    @Before
    fun setup() {
        repo = LocalTransactionRepo()
        random = Random(0)
    }

    @Test
    fun setGetDataTest() = runTest(timeout = 1.seconds) {
        val uid = getRandomUid(random)
        val guid = GroupId(getRandomString(32, random))
        val flow = repo.observeTransactions(guid)
        // Check value always emitted
        assertEquals(null, flow.first())
        val transaction1 = TransactionData.Payment("Payment",null, Instant.now(),
            mapOf(uid to BigDec(1)),
            uid
        )
        // Check setTransactions
        repo.setTransactions(guid, listOf(transaction1))
        assertEquals(listOf(transaction1), flow.first())
        // Check clear
        repo.clear()
        assertEquals(null, flow.first())
    }
}
