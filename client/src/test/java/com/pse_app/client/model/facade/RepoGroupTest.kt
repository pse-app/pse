package com.pse_app.client.model.facade

import com.pse_app.client.model.getRandomGid
import com.pse_app.client.model.getRandomUid
import com.pse_app.client.model.repositories.Repo
import com.pse_app.client.model.repositories.data.GroupData
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.TransactionData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.client.model.testing.MockRepo
import com.pse_app.common.util.BigDec
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class RepoGroupTest {
    private lateinit var repo: MockRepo
    @MockK
    lateinit var mockkRepo: Repo
    private lateinit var random: Random
    private var gid: GroupId = GroupId("")
    private lateinit var data: GroupData
    private lateinit var members: List<UserId>
    private lateinit var repoGroup: RepoGroup
    private lateinit var exampleBalances: List<BigDec>

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        repo = MockRepo()
        random = Random(0)
        gid = getRandomGid(random)
        members = (1..3).map { getRandomUid(random) }
        exampleBalances = listOf(BigDec("-1"), BigDec("-2.14"), BigDec("3.14"))
        data = GroupData(
            gid,
            "Group",
            "INVITE",
            members,
            members.zip(exampleBalances).toMap(),
            null
        )
        repoGroup = RepoGroup(repo, gid)
    }

    @Test
    fun testGroupEquality() = runTest(timeout = 1.seconds) {
        val group1 = RepoGroup(repo, GroupId("0"))
        val group2 = RepoGroup(repo, GroupId("0"))
        val group3 = RepoGroup(repo, GroupId(""))

        assertEquals(group1, group2)
        assertEquals(group1.hashCode(), group2.hashCode())
        assertNotEquals(group1, group3)
    }

    @Test
    fun testDisplayName() = runTest(timeout = 1.seconds) {
        val nameFlow = repoGroup.displayName.stateIn(backgroundScope)
        assertEquals(null, nameFlow.first())
        repo.groupRepo.setGroupData(data)
        delay(1)
        assertEquals(data.displayName, nameFlow.first())
    }

    @Test
    fun testInviteUrl() = runTest(timeout = 1.seconds) {
        assertEquals(null, repoGroup.inviteUrl.first())
        repo.groupRepo.setGroupData(data)
        assertEquals(data.inviteUrl, repoGroup.inviteUrl.first())
    }

    @Test
    fun testMembers() = runTest(timeout = 1.seconds) {
        assertEquals(null, repoGroup.members.first())
        repo.groupRepo.setGroupData(data)
        assertEquals(data.members, repoGroup.members.first()!!.map { UserId(it.id) })
    }

    @Test
    fun testTransactions() = runTest(timeout = 1.seconds) {
        assertEquals(null, repoGroup.transactions.first())
        repo.transactionRepo.setTransactions(gid, listOf(
            TransactionData.Payment("Payment", null, Instant.EPOCH,
                mapOf(members[0] to BigDec(1), members[1] to BigDec(-1)), members[0]),
            TransactionData.Expense("Expense", null, Instant.EPOCH,
                mapOf(members[0] to BigDec(1), members[1] to BigDec(-1)), members[0], BigDec(1))
        ))
        repo.groupRepo.setGroupData(data)
        val transactions = repoGroup.transactions.first()
        assertEquals("Payment", transactions!![0].name)
        assertEquals("Expense", transactions[1].name)
    }

    @Test
    fun testAddExpense() = runTest {
        val group = RepoGroup(mockkRepo, gid)
        val user1 = RepoUser(mockkRepo, UserId("1"))
        val user2 = RepoUser(mockkRepo, UserId("2"))

        val slot = slot<Map<GroupId, TransactionData>>()
        coEvery { mockkRepo.transactionRepo.postTransactions(capture(slot)) } returns Unit

        group.addExpense(
            "Market",
            "Bought fruits",
            mapOf(
                user1 to BigDec(1),
                user2 to BigDec(2),
            ),
            user1,
            BigDec(3)
        )

        val expected = mapOf(gid to TransactionData.Expense(
            "Market",
            "Bought fruits",
            slot.captured[gid]!!.timestamp,
            mapOf(
                UserId("1") to BigDec(2),
                UserId("2") to BigDec(-2)
            ),
            UserId("1"),
            BigDec(3)
        ))

        assertEquals(expected, slot.captured)
    }

    @Test
    fun testAddPayment() = runTest {
        val group = RepoGroup(mockkRepo, gid)
        val user1 = RepoUser(mockkRepo, UserId("1"))
        val user2 = RepoUser(mockkRepo, UserId("2"))

        val slot = slot<Map<GroupId, TransactionData>>()
        coEvery { mockkRepo.transactionRepo.postTransactions(capture(slot)) } returns Unit

        group.addPayment(
            "",
            "Payed fruits",
            mapOf(
                user1 to BigDec(2)
            ),
            user2,
            user1
        )

        val expected = mapOf(gid to TransactionData.Payment(
            "",
            "Payed fruits",
            slot.captured[gid]!!.timestamp,
            mapOf(
                UserId("1") to BigDec(-2),
                UserId("2") to BigDec(2)
            ),
            UserId("1")
        ))

        assertEquals(expected, slot.captured)
    }
}
