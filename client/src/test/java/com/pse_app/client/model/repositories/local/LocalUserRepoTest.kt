package com.pse_app.client.model.repositories.local

import com.pse_app.client.model.getRandomUid
import com.pse_app.client.model.getRandomUserData
import com.pse_app.client.model.repositories.data.UserData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class LocalUserRepoTest {
    private lateinit var repo: LocalUserRepo
    private lateinit var random: Random

    @Before
    fun setup() {
        repo = LocalUserRepo()
        random = Random(0)
    }

    @Test
    fun setGetDataTest() = runTest(timeout = 1.seconds) {
        val uid = getRandomUid(random)
        val uData = getRandomUserData(uid, random)
        val flow = repo.observeUser(uid)
        assertEquals(null, flow.first())
        repo.setUserData(uData)
        assertEquals(uData, flow.first())
    }

    @Test
    fun setGetActiveUserTest() = runTest(timeout = 1.seconds) {
        val uid1 = getRandomUid(random)
        val flow = repo.observeActiveUserId()
        assertEquals(null, flow.first())
        repo.setActiveUserId(uid1)
        assertEquals(uid1, flow.first())
    }

    @Test
    fun touchUsersTest() = runTest(timeout = 1.seconds) {
        val uid1 = getRandomUid(random)
        val uid2 = getRandomUid(random)
        val udata1 = getRandomUserData(uid1, random)
        val expectedData = UserData(uid2, uid2.id, null)
        repo.setUserData(udata1)
        repo.touchUsers(setOf(uid1, uid2))
        assertEquals(udata1, repo.observeUser(uid1).first())
        assertEquals(expectedData, repo.observeUser(uid2).first())
    }

    @Test
    fun clearTest() = runTest(timeout = 1.seconds) {
        val uid = getRandomUid(random)
        val flow = repo.observeUser(uid)
        assertEquals(null, flow.first())
        val uData = getRandomUserData(uid, random)
        repo.setUserData(uData)
        assertEquals(uData, flow.first())
        repo.clear()
        assertEquals(null, flow.first())
    }
}
