package com.pse_app.client.model

import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.UserId
import org.junit.Assert.assertEquals
import org.junit.Test

class IdTest {
    @Test
    fun testUserId() {
        val id1 = UserId("ABC")
        val id2 = UserId("ABC")

        assertEquals(id1.hashCode(), id2.hashCode())
        assertEquals(id1, id2)
    }

    @Test
    fun testGroupId() {
        val id1 = GroupId("ABC")
        val id2 = GroupId("ABC")

        assertEquals(id1.hashCode(), id2.hashCode())
        assertEquals(id1, id2)
    }
}
