package com.pse_app.server.user

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.server.assertions.assertError
import com.pse_app.server.assertions.assertSuccess
import com.pse_app.server.data.ErrorCategory
import com.pse_app.server.data.UserInfo
import com.pse_app.server.database.DataBaseTestInitializer
import com.pse_app.server.database.Tables
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.ktorm.database.Database
import org.ktorm.dsl.*
import java.net.URI
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UserManagerTests {
    private lateinit var db: Database
    private lateinit var users: UserManagement
    private lateinit var auth: Authenticator

    @Test
    fun testUserInfo() {
        val info = users.getUserInfo(user1)
            .let(::assertSuccess)
        assertEquals(info, UserInfo(id = user1, displayName = user1Name, profilePicture = null))
        val info2 = users.getUserInfo(user1)
            .let(::assertSuccess)
        assertEquals(info, info2)
        val info3 = users.getUserInfo(deactivatedUser)
            .let(::assertSuccess)
        assertEquals(info3, UserInfo(id = deactivatedUser, displayName = deactivatedUser.id, profilePicture = null))
    }

    @Test
    fun testLogin() {
        val result = auth.createSession(user1, "AAAAAAAA", "path".uri)
            .let(::assertSuccess)
        assertEquals(
            UserInfo(id = user1, displayName = user1Name, profilePicture = "path".uri),
            result.user,
        )
        auth.authenticate(user1).let(::assertSuccess)
        val result2 = auth.createSession(user1, "BBBBBBBBB", "path2".uri)
            .let(::assertSuccess)
        assertEquals(
            UserInfo(id = user1, displayName = user1Name, profilePicture = "path2".uri),
            result2.user,
        )
        assertNotEquals(result.refreshToken, result2.refreshToken)

        val result3 = auth.createSession("new user".id, "new name", "path3".uri)
            .let(::assertSuccess)
        assertEquals(
            UserInfo(id = "new user".id, displayName = "new name", profilePicture = "path3".uri),
            result3.user,
        )
    }

    @Test
    fun testRefresh() {
        val token = auth.createSession(user1, "", null)
            .let(::assertSuccess)
            .refreshToken
        val newToken = auth.refreshSession(token)
            .let(::assertSuccess)
            .refreshToken
        assertError(ErrorCategory.InvalidToken, auth.refreshSession(token))
        auth.refreshSession(newToken)
            .let(::assertSuccess)
    }

    @Test
    fun testSetDisplayName() {
        users.setDisplayName(user1, "AAAA")
            .let(::assertSuccess)
        users.setDisplayName(user1, "AAAA")
            .let(::assertSuccess)
        assertError(ErrorCategory.UserNotFound, users.setDisplayName("doesntExist".id, "AAAA"))
    }

    @Test
    @Suppress("RedundantUnitExpression")
    fun testGroupDoes_NOT_GetDeletedWhenItShouldntBe() {
        assertSuccess(users.deactivateUser(user1))
        assertTrue(
            db.from(Tables.Groups)
                .select(Tables.Groups.id)
                .where(Tables.Groups.id eq group2)
                .map { Unit }
                .isNotEmpty(),
            "Group should not have been deleted")
    }

    @Test
    @Suppress("RedundantUnitExpression")
    fun testGroupGetsDeletedWhenItShouldBe() {
        assertSuccess(users.deactivateUser(user1))
        assertTrue(
            db.from(Tables.Groups)
                .select(Tables.Groups.id)
                .where(Tables.Groups.id eq group1)
                .map { Unit }
                .isEmpty(),
            "Group should have been deleted")
    }

    @Test
    fun testDisableUser() {
        val token = auth.createSession(user1, "", null)
            .let(::assertSuccess)
            .refreshToken
        users.deactivateUser(user1)

        assertError(ErrorCategory.InvalidToken, auth.refreshSession(token))
        assertSuccess(users.getUserInfo(user1)) {
            assertEquals(user1.id, it.displayName)
        }
        assertError(ErrorCategory.UserNotFound, auth.authenticate(user1))
        assertError(ErrorCategory.UserNotFound, users.deactivateUser(user1))
        assertError(ErrorCategory.UserNotFound, users.setDisplayName(user1, "AAAAA"))
        val result = auth.createSession(user1, "newName", "test".uri)
            .let(::assertSuccess)
        assertEquals(
            UserInfo(id = user1, displayName = "newName", profilePicture = "test".uri),
            result.user
        )
        assertError(ErrorCategory.InvalidToken, auth.refreshSession(token))
        auth.refreshSession(result.refreshToken)
            .let(::assertSuccess)
    }

    @BeforeEach
    fun setup() {
        db = DataBaseTestInitializer.connectTestDatabase()
        users = UserManager(db).also {
            auth = it.authenticator
        }
        DataBaseTestInitializer.apply {
            createTestUser(db, user1, name = user1Name)
            createTestUser(db, user2, name = user2Name)
            createTestUser(db, user3)
            createTestUser(db, user4)
            createTestUser(db, user5, name = user5Name)
            createTestGroup(db, group1)
            createTestGroup(db, group2)
            createDeactivatedTestUser(db, deactivatedUser)
            createTestMembership(db, user1, group1)
            createTestMembership(db, deactivatedUser, group1)
            createTestMembership(db, user1, group2)
            createTestMembership(db, user2, group2)
            createTestMembership(db, deactivatedUser, group2)
        }
    }

    private val group1 = GroupId(UUID.nameUUIDFromBytes("group name".toByteArray()))
    private val group2 = GroupId(UUID.nameUUIDFromBytes("another group name".toByteArray()))
    private val user1 = UserId("user1")
    private val user1Name = "Peter"
    private val user2 = UserId("user2")
    private val user2Name = "Johannes"
    private val user3 = UserId("user3")
    private val user4 = UserId("user4")
    private val user5 = UserId("user5")
    private val deactivatedUser = UserId("dead")
    private val user5Name = "Jonas"

    private val String.uri
        get() = URI(this)
    private val String.id
        get() = UserId(this)
}
