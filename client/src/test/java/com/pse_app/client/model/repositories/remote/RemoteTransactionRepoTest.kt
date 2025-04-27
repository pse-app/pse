package com.pse_app.client.model.repositories.remote

import com.pse_app.client.model.data_layer.RemoteAPI
import com.pse_app.client.model.data_layer.Response
import com.pse_app.client.model.getRandomGid
import com.pse_app.client.model.repositories.data.TransactionData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.common.dto.ExpenseResponsePayload
import com.pse_app.common.dto.PaymentResponsePayload
import com.pse_app.common.util.BigDec
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RemoteTransactionRepoTest {
    @MockK
    lateinit var repo: RemoteRepo
    @MockK
    lateinit var remoteAPI: RemoteAPI
    private lateinit var transactionRepo: RemoteTransactionRepo
    private lateinit var random: Random

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        transactionRepo = RemoteTransactionRepo(remoteAPI, repo)
        random = Random(0)
    }

    @Test
    fun refreshTest() = runTest(timeout = 1.seconds) {
        val guid = getRandomGid(random)
        val transactions = transactionRepo.observeTransactions(guid)
        val instant = Instant.now()
        val transaction1 = PaymentResponsePayload(guid.cast(), UserId("User").cast(), "Payment", "A", instant, mapOf())
        val transaction2 = ExpenseResponsePayload(guid.cast(), UserId("User").cast(), "Expense", "B", instant, mapOf(), BigDec(0))

        coEvery { remoteAPI.getTransactions(setOf(guid.cast())) } returns Response(HttpStatusCode.OK,
            mapOf(guid.cast() to listOf(transaction1, transaction2)))

        transactionRepo.refreshTransactions(guid)

        val expect1 = TransactionData.Payment("Payment", "A", instant, mapOf(), UserId("User"))
        val expect2 = TransactionData.Expense("Expense", "B", instant, mapOf(), UserId("User"), BigDec(0))

        assertEquals(listOf(expect1, expect2), transactions.first())
    }

    @Test
    fun postTransactionsTest() = runTest(timeout = 1.seconds) {
        val gid1 = getRandomGid(random)
        val gid2 = getRandomGid(random)
        val instant = Instant.now()
        val send1 = TransactionData.Payment("Payment", "A", instant, mapOf(), UserId("User"))
        val send2 = TransactionData.Expense("Expense", "B", instant, mapOf(), UserId("User"), BigDec(0))

        coEvery { remoteAPI.postTransactions(mapOf(gid1.cast() to toDTO(send1), gid2.cast() to toDTO(send2))) } returns
                Response(HttpStatusCode.OK, Unit)
        transactionRepo.postTransactions(mapOf(gid1 to send1, gid2 to send2))
        coVerify { remoteAPI.postTransactions(mapOf(gid1.cast() to toDTO(send1), gid2.cast() to toDTO(send2))) }
    }
}
