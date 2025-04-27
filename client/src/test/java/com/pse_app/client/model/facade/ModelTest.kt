package com.pse_app.client.model.facade

import android.net.Uri
import android.webkit.URLUtil
import com.pse_app.client.model.data_layer.RemoteAPI
import com.pse_app.client.model.data_layer.Response
import com.pse_app.client.model.repositories.Repo
import com.pse_app.client.model.repositories.data.AllRecommendationData
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.SettleRecommendationData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.client.model.repositories.remote.cast
import com.pse_app.client.persistence.Preferences
import com.pse_app.common.dto.GroupInviteInfoResponsePayload
import com.pse_app.common.dto.SettingsResponsePayload
import com.pse_app.common.util.BigDec
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.net.URI
import java.util.Currency
import java.util.UUID

class ModelTest {
    @MockK
    private lateinit var repo: Repo
    @MockK
    private lateinit var api: RemoteAPI
    private lateinit var model: Model
    @MockK
    private lateinit var prefs: Preferences
    private var groupId: GroupId = GroupId(UUID.randomUUID().toString())

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { api.registerOnSessionDestroyed(any()) } returns Unit
        model = Model(repo, api, prefs)
    }

    @After
    fun tearDown() {
        verify { api.registerOnSessionDestroyed(any()) }
        confirmVerified(repo, api, prefs)
    }

    @Test
    fun isAuthenticated() = runTest {
        every { api.registerOnSessionDestroyed(any()) } returns Unit
        coEvery { api.refreshSession() } returns true
        assertEquals(true, model.isAuthenticated())
        coVerify { api.refreshSession() }
    }

    @Test
    fun login() = runTest {
        coEvery { api.createSession("TOKEN") } returns Unit
        assertEquals(Unit, model.login("TOKEN"))
        coVerify { api.createSession("TOKEN") }
    }

    @Test
    fun logout() = runTest {
        coEvery { api.logout() } returns Unit
        assertEquals(Unit, model.logout())
        coVerify { api.logout() }
    }

    @Test
    fun setUserDisplayName() = runTest {
        coEvery { repo.userRepo.setDisplayName("NAME") } returns Unit
        assertEquals(Unit, model.setUserDisplayName("NAME"))
        coVerify { repo.userRepo.setDisplayName("NAME") }
    }

    @Test
    fun deactivateActiveUser() = runTest {
        coEvery { api.deactivateActiveUser() } returns Response(HttpStatusCode.OK, Unit)
        coEvery { api.logout() } returns Unit
        model.deactivateActiveUser()
        coVerify(ordering = Ordering.ORDERED) { api.deactivateActiveUser() }
        coVerify(ordering = Ordering.ORDERED) { api.logout() }
    }

    @Test
    fun observeActiveUser() = runTest {
        every { repo.userRepo.observeActiveUserId() } returns flowOf(UserId("ID"))
        assertEquals("ID", model.observeActiveUser().first()?.id)
        verify { repo.userRepo.observeActiveUserId() }
    }

    @Test
    fun refreshActiveUser() = runTest {
        coEvery { repo.userRepo.refreshActiveUser() } returns Unit
        coEvery { repo.groupRepo.refreshAllGroups() } returns Unit
        model.refreshActiveUser()
        coVerify { repo.userRepo.refreshActiveUser() }
        coVerify { repo.groupRepo.refreshAllGroups() }
    }

    @Test
    fun refreshUsers() = runTest {
        coEvery { repo.userRepo.refreshUsers(listOf(UserId("ID"))) } returns Unit
        model.refreshUsers(listOf(RepoUser(repo, UserId("ID"))))
        coVerify { repo.userRepo.refreshUsers(listOf(UserId("ID"))) }
    }

    @Test
    fun observeGroups() = runTest {
        every { repo.groupRepo.observeAllGroupIds() } returns flowOf(listOf(GroupId("IDENTITY")))
        assertEquals("IDENTITY", model.observeGroups().first()!![0].id)
        verify { repo.groupRepo.observeAllGroupIds() }
    }

    @Test
    fun getGroupById() = runTest {
        val id = GroupId("ID")
        assertEquals(id.id, model.getGroupById(id.id).id)
    }

    @Test
    fun getUserById() = runTest {
        val id = UserId("ID")
        assertEquals(id.id, model.getUserById(id.id).id)
    }

    @Test
    fun createGroup() = runTest {
        coEvery { repo.groupRepo.createGroup("NAME") } returns groupId
        assertEquals(groupId.id, model.createGroup("NAME").id)
        coVerify { repo.groupRepo.createGroup("NAME") }
    }

    @Test
    fun joinGroup() = runTest {
        coEvery { repo.groupRepo.joinGroup("TOKEN") } returns groupId
        assertEquals(groupId.id, model.joinGroup("TOKEN").id)
        coVerify { repo.groupRepo.joinGroup("TOKEN") }
    }

    @Test
    fun getGroupInviteInfo() = runTest {
        coEvery { api.getGroupInviteInfo("TOKEN") } returns
                Response(HttpStatusCode.OK, GroupInviteInfoResponsePayload(
                    "NAME",
                    groupId.cast(),
                    true
                ))
        assertEquals(GroupInviteInfo("NAME", true, groupId.id), model.getGroupInviteInfo("TOKEN"))
        coVerify { api.getGroupInviteInfo("TOKEN") }
    }

     @Test
     fun getAllSettleRecommendations() = runTest {
         coEvery { repo.userRepo.refreshUsers() } returns Unit
         coEvery { repo.groupRepo.refreshAllGroups() } returns Unit
         coEvery { repo.groupRepo.getAllSettleRecommendations() } returns AllRecommendationData(
             BigDec.ZERO,
             listOf(
                 SettleRecommendationData(
                     UserId("ID"),
                     listOf(Pair(groupId, BigDec(42))),
                     BigDec(1)
                 )
         )

         )
         val recommendation = model.getAllSettleRecommendations().byUser[0]
         assertEquals("ID", recommendation.user.id)
         assertEquals(BigDec(1), recommendation.totalAmount)
         assertEquals(BigDec(42), recommendation.amountsByGroup[RepoGroup(repo, groupId)])
         coVerify { repo.userRepo.refreshUsers() }
         coVerify { repo.groupRepo.refreshAllGroups() }
         coVerify { repo.groupRepo.getAllSettleRecommendations() }
    }

    @Test
    fun postTransactions() = runTest {

    }

    @Test
    fun getOIDCSettings() = runTest {
        coEvery { api.getSettings() } returns Response(HttpStatusCode.OK, SettingsResponsePayload(
            "CLIENT",
            URI.create("http://0.0.0.0"),
            Currency.getInstance("EUR")
        ))
        mockkStatic(Uri::class)
        mockkStatic(URLUtil::class)
        every { Uri.parse("http://0.0.0.0") } returns mockk<Uri>()
        every { URLUtil.isNetworkUrl("http://0.0.0.0") } returns true
        assertEquals("CLIENT", model.getOIDCSettings().clientId)
        coVerify { api.getSettings() }
    }

    @Test
    fun splitEvenlyTest() {
        val total = BigDec(BigInteger.valueOf(42), 0)
        val users = (0..<5).map { RepoUser(repo, UserId(it.toString())) }
        val res = model.splitEvenly(total, users)
        for (i in 0..<2) {
            assertEquals(BigDec(9), res[RepoUser(repo, UserId(i.toString()))])
        }
        for (i in 2..<5) {
            assertEquals(BigDec(8), res[RepoUser(repo, UserId(i.toString()))])
        }
    }
}
