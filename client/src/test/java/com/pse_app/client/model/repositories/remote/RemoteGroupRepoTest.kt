package com.pse_app.client.model.repositories.remote

import com.pse_app.client.model.data_layer.RemoteAPI
import com.pse_app.client.model.data_layer.Response
import com.pse_app.client.model.getRandomGid
import com.pse_app.client.model.getRandomUid
import com.pse_app.client.model.repositories.data.AllRecommendationData
import com.pse_app.client.model.repositories.data.GroupData
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.SettleRecommendationData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.client.model.repositories.local.LocalGroupRepo
import com.pse_app.common.dto.BalancesRequestPayload
import com.pse_app.common.dto.BalancesResponsePayload
import com.pse_app.common.dto.ChangeDisplayNameRequestPayload
import com.pse_app.common.dto.CreateGroupRequestPayload
import com.pse_app.common.dto.GroupInfoResponsePayload
import com.pse_app.common.dto.RegenerateInviteTokenResponsePayload
import com.pse_app.common.util.BigDec
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.net.URI
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RemoteGroupRepoTest {
    @MockK
    lateinit var repo: RemoteRepo
    @MockK
    lateinit var remoteAPI: RemoteAPI
    private lateinit var localGroupRepo: LocalGroupRepo
    private lateinit var groupRepo: RemoteGroupRepo
    private lateinit var random: Random
    private lateinit var uid: List<UserId>
    private lateinit var cuid: List<com.pse_app.common.dto.UserId>
    private lateinit var gid: List<GroupId>
    private lateinit var cgid: List<com.pse_app.common.dto.GroupId>
    private val inviteUri = URI.create("http://0.0.0.0")

    private lateinit var exampleUsers: List<UserId>
    private lateinit var exampleInfoResponse: GroupInfoResponsePayload
    private lateinit var exampleData: GroupData
    private lateinit var exampleBalances: List<BigDec>

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        localGroupRepo = LocalGroupRepo()
        groupRepo = RemoteGroupRepo(remoteAPI, repo, localGroupRepo)
        random = Random(0)
        uid = (1..10).map { getRandomUid(random) }
        gid = (1..10).map { getRandomGid(random) }
        cuid = uid.map { it.cast() }
        cgid = gid.map { it.cast() }

        exampleUsers = listOf(uid[0], uid[1], uid[2])
        exampleInfoResponse = GroupInfoResponsePayload(
            cgid[0],
            "Example Group",
            inviteUri,
            null,
            setOf(cuid[0], cuid[1], cuid[2])
        )
        exampleBalances = listOf(BigDec("-1"), BigDec("-2.14"), BigDec("3.14"))
        exampleData = GroupData(
            gid[0],
            "Example Group",
            inviteUri.toString(),
            exampleUsers,
            exampleUsers.zip(exampleBalances).toMap(),
            null
        )
    }

    private fun <T> ok(response: T) = Response(HttpStatusCode.OK, response)

    private suspend fun get0Group() = groupRepo.observeGroup(gid[0]).first()

    @Test
    fun refreshAllTest() = runTest(timeout = 1.seconds) {
        coEvery { remoteAPI.getBalances(BalancesRequestPayload(null, null)) } returns
                ok(BalancesResponsePayload(mapOf(
                    Pair(cuid[1], cgid[1]) to BigDec("4"),
                    Pair(cuid[2], cgid[1]) to BigDec("-4")
                )))
        coEvery { remoteAPI.getGroups(setOf(cgid[1])) } returns
                ok(mapOf(
                    cgid[1] to GroupInfoResponsePayload(
                        cgid[1],
                        "ABC",
                        inviteUri,
                        null,
                        setOf(cuid[1], cuid[2], cuid[3])
                    )
                ))
        groupRepo.refreshAllGroups()
        coVerify { remoteAPI.getBalances(BalancesRequestPayload(null, null)) }
        coVerify { remoteAPI.getGroups(setOf(cgid[1])) }

        assertEquals(listOf(gid[1]), groupRepo.observeAllGroupIds().first())
        assertEquals(GroupData(
            gid[1],
            "ABC",
            inviteUri.toString(),
            listOf(uid[1], uid[2], uid[3]),
            mapOf(uid[1] to BigDec("4"), uid[2] to BigDec("-4"), uid[3] to BigDec(0)),
            null
        ), groupRepo.observeGroup(gid[1]).first())
    }

    @Test
    fun refreshGroupTest() = runTest(timeout = 1.seconds) {
        coEvery { remoteAPI.getGroups(setOf(cgid[0])) } returns
                ok(mapOf(cgid[0] to exampleInfoResponse))
        coEvery { repo.userRepo.refreshUsers(exampleUsers) } returns Unit

        // fetchBalances = true
        coEvery { remoteAPI.getBalances(BalancesRequestPayload(null, setOf(cgid[0]))) } returns
                ok(BalancesResponsePayload(listOf(cuid[0] to cgid[0], cuid[1] to cgid[0], cuid[2] to cgid[0]).zip(exampleBalances).toMap()))

        groupRepo.refreshGroup(gid[0], true)
        assertEquals(exampleData, get0Group())

        coVerify { remoteAPI.getBalances(BalancesRequestPayload(null, setOf(cgid[0]))) }
        // fetchBalances = false
        coEvery { repo.transactionRepo.getDerivedBalances(gid[0]) } returns
                exampleUsers.zip(exampleBalances).toMap()
        coEvery { repo.userRepo.touchUsers(exampleUsers.toSet()) } returns Unit

        groupRepo.refreshGroup(gid[0], false)
        assertEquals(exampleData, get0Group())

        coVerify { repo.userRepo.touchUsers(setOf(uid[0], uid[1], uid[2])) }
        coVerify { repo.transactionRepo.getDerivedBalances(gid[0]) }

        //
        coVerify { remoteAPI.getGroups(setOf(cgid[0])) }
        coVerify { repo.userRepo.refreshUsers(exampleUsers) }
    }

    @Test
    fun createGroupTest() = runTest(timeout = 1.seconds) {
        coEvery { remoteAPI.createGroup(CreateGroupRequestPayload(exampleInfoResponse.displayName)) } returns
                ok(exampleInfoResponse.copy(members = setOf(cuid[0])))
        coEvery { repo.userRepo.observeActiveUserId() } returns flowOf(uid[0])
        groupRepo.createGroup(exampleInfoResponse.displayName)
        val expectedData = exampleData.copy(memberBalances = mapOf(uid[0] to BigDec(0)), members = listOf(uid[0]))
        assertEquals(expectedData, get0Group())
        coVerify { remoteAPI.createGroup(CreateGroupRequestPayload(exampleInfoResponse.displayName)) }
    }

    @Test
    fun joinGroupTest() = runTest(timeout = 1.seconds) {
        coEvery { remoteAPI.joinGroup("TOKEN") } returns
                ok(exampleInfoResponse)
        coEvery { remoteAPI.getBalances(BalancesRequestPayload(null, setOf(cgid[0]))) } returns
                ok(BalancesResponsePayload(listOf(cuid[0] to cgid[0], cuid[1] to cgid[0], cuid[2] to cgid[0]).zip(exampleBalances).toMap()))
        assertEquals(gid[0], groupRepo.joinGroup("TOKEN"))
        assertEquals(exampleData, get0Group())
        coVerify { remoteAPI.joinGroup("TOKEN") }
    }

    @Test
    fun regenerateInviteTest() = runTest(timeout = 1.seconds) {
        localGroupRepo.setGroupData(exampleData)
        val newUri = URI.create("http://127.0.0.1")
        coEvery { remoteAPI.regenerateGroupInviteLink(gid[0].id) } returns
                ok(RegenerateInviteTokenResponsePayload(newUri))
        groupRepo.regenerateInviteLink(gid[0])
        assertEquals(exampleData.copy(inviteUrl = newUri.toString()), get0Group())
        coVerify { remoteAPI.regenerateGroupInviteLink(gid[0].id) }
    }

    @Test
    fun kickUserTest() = runTest(timeout = 1.seconds) {
        coEvery { repo.userRepo.observeActiveUserId() } returns flowOf(uid[0])
        localGroupRepo.setGroupData(exampleData)
        coEvery { remoteAPI.kickUser(gid[0].id, uid[2].id) } returns
                ok(Unit)
        coEvery { remoteAPI.kickUser(gid[0].id, uid[0].id) } returns
                ok(Unit)
        groupRepo.kickUser(gid[0], uid[2])
        assertEquals(exampleData.copy(members = listOf(uid[0], uid[1])), get0Group())
        groupRepo.kickUser(gid[0], uid[0])
        assertEquals(null, get0Group())
        coVerify { remoteAPI.kickUser(gid[0].id, uid[2].id) }
        coVerify { remoteAPI.kickUser(gid[0].id, uid[0].id) }
    }

    @Test
    fun setDisplayNameTest() = runTest(timeout = 1.seconds) {
        localGroupRepo.setGroupData(exampleData)
        coEvery { remoteAPI.setGroupDisplayName(gid[0].id, ChangeDisplayNameRequestPayload("New Name")) } returns
                ok(exampleInfoResponse.copy(displayName = "New Name"))
        groupRepo.setDisplayName(gid[0], "New Name")
        assertEquals(exampleData.copy(displayName = "New Name"), get0Group())
        coVerify { remoteAPI.setGroupDisplayName(gid[0].id, ChangeDisplayNameRequestPayload("New Name")) }
    }

    @Test
    fun getAllSettleRecommendationsTest() = runTest(timeout = 1.seconds) {
        localGroupRepo.setGroupData(exampleData)
        coEvery { repo.userRepo.observeActiveUserId() } returns flowOf(uid[0])

        val expected = AllRecommendationData(
            BigDec("-1"),
            listOf(
                SettleRecommendationData(uid[1], listOf(gid[0] to BigDec.ZERO), BigDec.ZERO),
                SettleRecommendationData(uid[2], listOf(gid[0] to BigDec("-1")), BigDec("-1"))
            )
        )
        assertEquals(expected, groupRepo.getAllSettleRecommendations())
        coVerify { repo.userRepo.observeActiveUserId()  }
    }
}
