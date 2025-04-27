package com.pse_app.client.model.repositories.local

import com.pse_app.client.model.getRandomData
import com.pse_app.client.model.getRandomString
import com.pse_app.client.model.repositories.data.GroupId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LocalGroupRepoTest {
    private lateinit var repo: LocalGroupRepo
    private lateinit var random: Random

    @Before
    fun setup() {
        repo = LocalGroupRepo()
        random = Random(0)
    }

    @Test
    fun setGetDataTest() = runTest(timeout = 1.seconds) {
        val gid =  GroupId(getRandomString(32, random))
        val data = getRandomData(gid, random)
        val flow = repo.observeGroup(gid).stateIn(backgroundScope)
        assertEquals(null, flow.first())
        repo.setGroupData(data)
        delay(1)
        assertEquals(data, flow.first())
        val gid2 =  GroupId(getRandomString(32, random))
        val data2 = getRandomData(gid2, random)
        repo.setAllGroupData(listOf(data2))
        delay(1)
        assertEquals(null, flow.first())
        assertEquals(listOf(gid2), repo.observeAllGroupIds().first())
    }

    @Test
    fun deleteDataTest() = runTest(timeout = 1.seconds) {
        val gid =  GroupId(getRandomString(32, random))
        val data = getRandomData(gid, random)
        val flow = repo.observeGroup(gid).stateIn(backgroundScope)
        assertEquals(null, flow.first())
        repo.setGroupData(data)
        delay(1)
        assertEquals(data, flow.first())
        repo.deleteGroupData(gid)
        delay(1)
        assertEquals(null, flow.first())
    }

    @Test
    fun clearTest() = runTest(timeout = 1.seconds) {
        val gid =  GroupId(getRandomString(32, random))
        val data = getRandomData(gid, random)
        val flow = repo.observeAllGroupIds().stateIn(backgroundScope)
        repo.setGroupData(data)
        repo.clear()
        delay(1)
        assertEquals(null, flow.first())
    }
}
