package com.pse_app.server.executor

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.server.assertions.assertError
import com.pse_app.server.assertions.assertSuccess
import com.pse_app.server.data.*
import com.pse_app.server.group.GroupManagement
import com.pse_app.server.transaction.TransactionManagement
import com.pse_app.server.user.UserManagement
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class RequestDispatcherTests {
    @MockK(relaxed = true)
    lateinit var userManagement: UserManagement

    @MockK(relaxed = true)
    lateinit var groupManagement: GroupManagement

    @MockK(relaxed = true)
    lateinit var transactionManagement: TransactionManagement

    private lateinit var requestExecutor: RequestExecutor

    @BeforeEach
    fun initRequestExecutor() {
        requestExecutor = RequestDispatcher(userManagement, groupManagement, transactionManagement)
    }

    @Test
    fun deactivateUser() {
        val user = UserId("user")
        requestExecutor.deactivateUser(user)
        verify(exactly = 1) { userManagement.deactivateUser(user) }
    }

    @Test
    fun retrieveUserMembershipInfo() {
        val user1 = UserId("id")
        val user2 = UserId("di")
        val info1 = UserInfo(
            id = user1,
            displayName = "Herbert",
            profilePicture = null
        )
        val info2 = UserInfo(
            id = user2,
            displayName = "Norbert",
            profilePicture = null
        )
        val commonGroup = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val groups1 = setOf(
            commonGroup,
            GroupId(UUID.nameUUIDFromBytes("group2".toByteArray()))
        )
        val groups2 = setOf(
            commonGroup,
            GroupId(UUID.nameUUIDFromBytes("group3".toByteArray()))
        )
        every { userManagement.getUserInfo(user1) } returns Result.Success(info1)
        every { userManagement.getUserInfo(user2) } returns Result.Success(info2)
        every { groupManagement.getUserGroups(user1) } returns Result.Success(groups1)
        every { groupManagement.getUserGroups(user2) } returns Result.Success(groups2)
        assertSuccess(requestExecutor.retrieveUserMembershipInfo(user1, user2)) {
            assertEquals(it, UserMembershipInfo(info2, setOf(commonGroup))) // don't return a group the acting user doesn't know!
        }
        assertSuccess(requestExecutor.retrieveUserMembershipInfo(user1, user1)) {
            assertEquals(it, UserMembershipInfo(info1, groups1))
        }
        assertSuccess(requestExecutor.retrieveUserMembershipInfo(user1, setOf(user1, user2))) {
            assertEquals(
                it,
                mapOf(
                    user1 to UserMembershipInfo(info1, groups1),
                    user2 to UserMembershipInfo(info2, setOf(commonGroup))
                )
            )
        }
    }

    @Test
    fun updateUserDisplayName() {
        val user = UserId("blabla")
        val displayName = "albalb"
        requestExecutor.updateUserDisplayName(user, displayName)
        verify(exactly = 1) { userManagement.setDisplayName(user, displayName) }
    }

    @Test
    fun listKnownUsers() {
        val user = UserId("Mr. X")
        val group1 = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val group2 = GroupId(UUID.nameUUIDFromBytes("group2".toByteArray()))
        val members1 = setOf(user, UserId("1"), UserId("2"))
        val members2 = setOf(user, UserId("a"), UserId("b"))
        every { groupManagement.getUserGroups(user) } returns Result.Success(setOf(group1, group2))
        every { groupManagement.getGroupMembers(group1) } returns Result.Success(members1)
        every { groupManagement.getGroupMembers(group2) } returns Result.Success(members2)
        assertSuccess(requestExecutor.listKnownUsers(user)) {
            assertEquals(it, members1.union(members2).plus(user))
        }
    }

    @Test
    fun listKnownGroups() {
        val user = UserId("Mal wieder Herbert")
        requestExecutor.listKnownGroups(user)
        verify(exactly = 1) { groupManagement.getUserGroups(user) }
    }

    @Test
    fun createGroup() {
        val user = UserId("Herbert")
        val displayName = "[kreativen Gruppennamen einfügen]"
        requestExecutor.createGroup(user, displayName)
        verify(exactly = 1) { groupManagement.createGroup(user, displayName) }
    }

    @Test
    fun retrieveGroupMembershipInfo() {
        val group1 = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val group2 = GroupId(UUID.nameUUIDFromBytes("group2".toByteArray()))
        val members1 = setOf(UserId("1"), UserId("2"))
        val members2 = setOf(UserId("a"), UserId("b"))
        val info1 = GroupInfo(group1, "Komm in die Gruppe", "werde auch du Millionär", null)
        val info2 = GroupInfo(group2, "Lambo in drei Jahren", "100% Erfolgsquote", null)
        every { groupManagement.getGroupInfo(group1) } returns Result.Success(info1)
        every { groupManagement.getGroupInfo(group2) } returns Result.Success(info2)
        every { groupManagement.getGroupMembers(group1) } returns Result.Success(members1)
        every { groupManagement.getGroupMembers(group2) } returns Result.Success(members2)
        assertSuccess(requestExecutor.retrieveGroupMembershipInfo(group1)) {
            assertEquals(it, GroupMembershipInfo(info1, members1))
        }
        assertSuccess(requestExecutor.retrieveGroupMembershipInfo(setOf(group1, group2))) {
            assertEquals(
                it,
                mapOf(
                    group1 to GroupMembershipInfo(info1, members1),
                    group2 to GroupMembershipInfo(info2, members2)
                )
            )
        }
    }

    @Test
    fun updateGroupDisplayName() {
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val displayName = "dn"
        requestExecutor.updateGroupDisplayName(group, displayName)
        verify(exactly = 1) { groupManagement.setDisplayName(group, displayName) }
    }

    @Test
    fun regenerateInviteToken() {
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val inviteToken = "noice"
        every { groupManagement.regenerateInviteToken(group) } returns Result.Success(inviteToken)
        assertSuccess(requestExecutor.regenerateInviteToken(group)) {
            assertEquals(it, inviteToken)
        }
        verify(exactly = 1) { groupManagement.regenerateInviteToken(group) }
    }

    @Test
    fun getTransactionsSingleError() {
        val transactions = listOf(mockk<Transaction>(), mockk<Transaction>(), mockk<Transaction>())
        val group1 = GroupId(UUID.nameUUIDFromBytes("group42".toByteArray()))
        val group2 = GroupId(UUID.nameUUIDFromBytes("group2".toByteArray()))
        every { transactionManagement.getTransactions(group1) } returns Result.Success(transactions)
        every { transactionManagement.getTransactions(group2) } returns Result.Error("nope")
        assertError(requestExecutor.getTransactions(setOf(group1, group2)))
    }

    @Test
    fun joinGroupWithValidInviteToken() {
        val user = UserId("user")
        val inviteToken = "invite_token"
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        every { groupManagement.resolveInviteToken(inviteToken) } returns Result.Success(group)
        requestExecutor.joinGroup(user, inviteToken)
        verify(exactly = 1) { groupManagement.addMember(group, user) }
    }

    @Test
    fun joinGroupWithInvalidInviteToken() {
        val user = UserId("user")
        every { groupManagement.resolveInviteToken(any()) } returns Result.Error("No, thou shalt not!")
        assertError(requestExecutor.joinGroup(user, "May I, with thy grace, join thy group??"))
        verify(exactly = 0) { groupManagement.addMember(any(), any()) }
    }

    @Test
    fun groupInfoFromValidInvite() {
        val inviteToken = "invite_token"
        val group = GroupId(UUID.nameUUIDFromBytes("Carglass repariert".toByteArray()))
        val info = GroupMembershipInfo(
            GroupInfo(group, "Carglass tauscht aus", inviteToken, null),
            setOf(UserId("Hans"), UserId("Peter"))
        )

        every { groupManagement.resolveInviteToken(inviteToken) } returns Result.Success(group)
        every { groupManagement.getGroupInfo(group) } returns Result.Success(info.info)
        every { groupManagement.getGroupMembers(group) } returns Result.Success(info.members)

        assertSuccess(requestExecutor.getInviteInfo(inviteToken)) {
            assertEquals(info, it)
        }
        verify(exactly = 1) { groupManagement.resolveInviteToken(inviteToken) }
        verify(exactly = 1) { groupManagement.getGroupInfo(group) }
    }

    @Test
    fun groupInfoFromInvalidInvite() {
        val inviteToken = "invite_token"
        every { groupManagement.resolveInviteToken(any()) } returns Result.Error("Not Real")
        assertError(requestExecutor.getInviteInfo(inviteToken))
        verify(exactly = 0) { groupManagement.getGroupInfo(any()) }
    }

    @Test
    fun kickUser() {
        val user = UserId("user")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        every { groupManagement.removeMember(group, user) } returns Result.Success(Unit)
        assertSuccess(requestExecutor.kickUser(group, user))
        verify(exactly = 1) { groupManagement.removeMember(group, user) }
    }

    @Test
    fun getTransactions() {
        val transactions1 = listOf(mockk<Transaction>(), mockk<Transaction>(), mockk<Transaction>())
        val transactions2 = listOf(mockk<Transaction>(), mockk<Transaction>())
        val group1 = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val group2 = GroupId(UUID.nameUUIDFromBytes("group2".toByteArray()))
        every { transactionManagement.getTransactions(group1) } returns Result.Success(transactions1)
        every { transactionManagement.getTransactions(group2) } returns Result.Success(transactions2)
        assertSuccess(requestExecutor.getTransactions(setOf(group1, group2))) {
            assertEquals(
                it,
                mapOf(
                    group1 to transactions1,
                    group2 to transactions2
                )
            )
        }
    }

    @Test
    fun postTransactions() {
        val transactions = listOf(mockk<Transaction>(), mockk<Transaction>(), mockk<Transaction>())
        requestExecutor.postTransactions(transactions)
        verify(exactly = 1) { transactionManagement.postTransactions(transactions) }
    }

    @Test
    fun calculateBalances() {
        val users = setOf(UserId("1"), UserId("2"), UserId("3"))
        val groups = setOf(
            GroupId(UUID.nameUUIDFromBytes("group1".toByteArray())),
            GroupId(UUID.nameUUIDFromBytes("group2".toByteArray()))
        )
        requestExecutor.calculateBalances(users, groups)
        verify(exactly = 1) { transactionManagement.getBalances(users, groups) }
    }

    @Test
    fun everythingErrors() {
        val transactions = listOf(mockk<Transaction>(), mockk<Transaction>(), mockk<Transaction>())
        val group1 = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val group2 = GroupId(UUID.nameUUIDFromBytes("group2".toByteArray()))
        val user1 = UserId("1")
        val user2 = UserId("2")
        val user3 = UserId("3")
        val users = setOf(user1, user2, user3)
        val groups = setOf(group1, group2)
        every { userManagement.getUserInfo(any()) } returns Result.Error("")
        every { userManagement.deactivateUser(any()) } returns Result.Error("")
        every { userManagement.setDisplayName(any(), any()) } returns Result.Error("")
        every { groupManagement.createGroup(any(), any()) } returns Result.Error("")
        every { groupManagement.getGroupInfo(any()) } returns Result.Error("")
        every { groupManagement.setDisplayName(any(), any()) } returns Result.Error("")
        every { groupManagement.getUserGroups(any()) } returns Result.Error("")
        every { groupManagement.getGroupMembers(any()) } returns Result.Error("")
        every { groupManagement.resolveInviteToken(any()) } returns Result.Error("")
        every { groupManagement.addMember(any(), any()) } returns Result.Error("")
        every { groupManagement.removeMember(any(), any()) } returns Result.Error("")
        every { groupManagement.regenerateInviteToken(any()) } returns Result.Error("")
        every { transactionManagement.getBalances(any(), any()) } returns Result.Error("")
        every { transactionManagement.postTransactions(any()) } returns Result.Error("")
        assertError(requestExecutor.calculateBalances(users, groups))
        assertError(requestExecutor.postTransactions(transactions))
        assertError(requestExecutor.kickUser(group1, user1))
        assertError(requestExecutor.joinGroup(user1, "invite token"))
        assertError(requestExecutor.regenerateInviteToken(group2))
        assertError(requestExecutor.updateGroupDisplayName(group2, "display name"))
        assertError(requestExecutor.retrieveGroupMembershipInfo(groups))
        assertError(requestExecutor.retrieveGroupMembershipInfo(group2))
        assertError(requestExecutor.createGroup(user1, "display name"))
        assertError(requestExecutor.listKnownGroups(user1))
        assertError(requestExecutor.listKnownUsers(user1))
        assertError(requestExecutor.updateUserDisplayName(user1, "display name"))
        assertError(requestExecutor.retrieveUserMembershipInfo(user1, users))
        assertError(requestExecutor.retrieveUserMembershipInfo(user1, user1))
        assertError(requestExecutor.deactivateUser(user1))
    }
}
