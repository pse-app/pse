package com.pse_app.client.model.facade

import com.pse_app.client.model.getRandomString
import com.pse_app.client.model.repositories.data.UserData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.client.model.testing.MockRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class RepoUserTest {
    private lateinit var repo: MockRepo
    private lateinit var random: Random

    @Before
    fun setup() {
        repo = MockRepo()
        random = Random(0)
    }

    @Test
    fun testUserEquality() = runTest(timeout = 1.seconds) {
        val user1 = RepoUser(repo, UserId("0"))
        val user2 = RepoUser(repo, UserId("0"))
        val user3 = RepoUser(repo, UserId(""))

        assertEquals(user1, user2)
        assertEquals(user1.hashCode(), user2.hashCode())
        assertNotEquals(user1, user3)
    }

    @Test
    fun testUserNameRandom() = runTest(timeout = 1.seconds) {
        val uCount = 10
        val repCount = 10

        val uids = mutableListOf<UserId>()
        val users = mutableListOf<User>()
        val lastNames = mutableListOf<String?>()
        for (i in 0..<uCount) {
            uids.add(UserId(getRandomString(10, random)))
            users.add(RepoUser(repo, uids[i]))
            lastNames.add(null)
        }

        for (i in 0..<repCount) {
            // This could fail when multithreaded
            launch {
                for (j in (0..<uCount).shuffled(random)) {
                    lastNames[j] = getRandomString(10, random)
                    repo.userRepo.setUserData(
                        UserData(
                            uids[j], lastNames[j]!!, ""
                        )
                    )
                }
            }
            launch {
                for (j in (0..<uCount).shuffled(random)) {
                    assertEquals(lastNames[j], users[j].displayName.first())
                }
            }
        }
    }

    @Test
    fun testProfilePictureRandom() = runTest(timeout = 1.seconds) {
        val repo = MockRepo()
        val random = Random(0)
        val uCount = 10
        val repCount = 10

        val uids = mutableListOf<UserId>()
        val users = mutableListOf<User>()
        val lastProfilePicture = mutableListOf<String?>()
        for (i in 0..<uCount) {
            uids.add(UserId(getRandomString(10, random)))
            users.add(RepoUser(repo, uids[i]))
            lastProfilePicture.add(null)
        }

        for (i in 0..<repCount) {
            // This could fail when multithreaded
            launch {
                for (j in (0..<uCount).shuffled(random)) {
                    lastProfilePicture[j] = getRandomString(10, random)
                    repo.userRepo.setUserData(
                        UserData(
                            uids[j], "", lastProfilePicture[j]!!
                        )
                    )
                }
            }
            launch {
                for (j in (0..<uCount).shuffled(random)) {
                    assertEquals(lastProfilePicture[j], users[j].profilePicture.first())
                }
            }
        }
    }
}
