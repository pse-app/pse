package com.pse_app.client.model.repositories.remote

import com.pse_app.client.model.data_layer.RemoteAPI
import com.pse_app.client.model.data_layer.Response
import com.pse_app.client.model.repositories.data.UserData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.common.dto.ChangeDisplayNameRequestPayload
import com.pse_app.common.dto.UserInfoResponsePayload
import io.ktor.http.HttpStatusCode
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class RemoteUserRepoTest {
    @MockK
    lateinit var repo: RemoteRepo
    @MockK
    lateinit var remoteAPI: RemoteAPI
    private lateinit var userRepo: RemoteUserRepo

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        userRepo = RemoteUserRepo(remoteAPI, repo)
    }

    @Test
    fun refreshActiveUserTest() = runTest {
        val user = flow {
            userRepo.observeActiveUserId().collect {
                if (it != null) {
                    emitAll(userRepo.observeUser(it))
                } else {
                    emit(null)
                }
            }
        }.stateIn(backgroundScope)
        delay(1)
        assertEquals(null, user.first())
        val rid = UserId("ABC")
        coEvery { remoteAPI.getActiveUser() } returns Response(HttpStatusCode.OK, UserInfoResponsePayload(
            rid.cast(),
            "",
            null,
            setOf()
        ))
        userRepo.refreshActiveUser()
        delay(1)
        assertEquals(UserData(rid, "", null), user.value)
    }

    private fun getInfoDataPair(uid: UserId): Pair<UserInfoResponsePayload, UserData> {
        val info = UserInfoResponsePayload(
            uid.cast(),
            "name",
            null,
            setOf()
        )
        val data = UserData(
            uid,
            "name",
            null
        )
        return Pair(info, data)
    }

    @Test
    fun refreshUsersTest() = runTest {
        val uid = UserId("ABC")
        val user = userRepo.observeUser(uid).stateIn(backgroundScope)
        delay(1)
        assertEquals(null, user.first())
        val uid2 = UserId("ABC2")
        val (info, data) = getInfoDataPair(uid)
        val (info2, data2) = getInfoDataPair(uid)
        coEvery { remoteAPI.getUsers(setOf(uid.cast(), uid2.cast())) } returns Response(HttpStatusCode.OK, mapOf(
            uid.cast() to info,
            uid2.cast() to info2
        ))
        userRepo.refreshUsers(listOf(uid, uid2))
        delay(1)
        assertEquals(data, user.value)
    }

    @Test
    fun setDisplayNameTest() = runTest {
        val (info, data) = getInfoDataPair(UserId("ABC"))
        coEvery {
            remoteAPI.setUserDisplayName(ChangeDisplayNameRequestPayload("name"))
        } returns Response(HttpStatusCode.OK, info)
        userRepo.setDisplayName("name")
        coVerify { remoteAPI.setUserDisplayName(ChangeDisplayNameRequestPayload("name")) }
        confirmVerified(remoteAPI)
        assertEquals(data, userRepo.observeUser(UserId("ABC")).first())
    }
}
