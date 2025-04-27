package com.pse_app.server.group

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.common.util.BigDec
import com.pse_app.server.assertions.assertError
import com.pse_app.server.assertions.assertSuccess
import com.pse_app.server.data.ErrorCategory
import com.pse_app.server.database.DataBaseTestInitializer
import com.pse_app.server.database.Tables
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.ktorm.database.Database
import org.ktorm.dsl.*
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class GroupManagerTests {

    private lateinit var db: Database
    private lateinit var gm: GroupManagement
    private lateinit var ga: Authorizer

    @Test
    fun create_group_fails_on_deactivated_or_nonexistent_user() {
        assertError(
            ErrorCategory.UserNotFound,
            gm.createGroup(NonexistentUser1, "Test Group"),
            "createGroup should fail given a nonexistent initial member."
        )
        assertError(
            ErrorCategory.UserNotFound,
            gm.createGroup(DeactivatedUser1, "Test Group"),
            "createGroup should fail given a deactivated initial member."
        )
    }

    @Test
    fun create_group_creates_group_and_membership_correctly() {
        assertSuccess(gm.createGroup(User1, "Test Group")) {
            val displayNames = db.from(Tables.Groups)
                .select(Tables.Groups.displayName)
                .where(Tables.Groups.id eq it)
                .map { row -> row[Tables.Groups.displayName] }
                .toList()
            assertEquals(
                listOf("Test Group"),
                displayNames,
                "createGroup inserted the group display name in a wrong way."
            )
            val members = db.from(Tables.Membership)
                .select(Tables.Membership.userId)
                .where(Tables.Membership.groupId eq it)
                .map { row -> row[Tables.Membership.userId] }
                .toList()
            assertEquals(listOf(User1), members, "createGroup inserted the group membership in a wrong way.")
        }
    }

    @Test
    fun get_group_info_returns_correct_data() {
        assertSuccess(gm.createGroup(User1, "Test Group")) { group ->
            assertSuccess(gm.addMember(group, User2))
            assertSuccess(gm.regenerateInviteToken(group)) { token ->
                assertSuccess(gm.getGroupInfo(group)) {
                    assertEquals("Test Group", it.displayName, "Invalid display name in getGroupInfo.")
                    assertEquals(token, it.inviteToken, "Invalid invite token in getGroupInfo.")
                    assertNull(it.mostRecentTransaction, "Expected a null timestamp for te most recent transaction in a group without transactions.")
                }
            }
        }
    }
    
    @Test
    fun get_group_info_returns_most_recent_transaction() {
        assertSuccess(gm.createGroup(User1, "Test Group")) { group ->
            assertSuccess(gm.addMember(group, User2))
            DataBaseTestInitializer.createSimpleTestTransaction(db, UUID.fromString("37f79533-e39d-4974-99b2-e405ffb6eabd"), group, User1, User2, timestamp = Instant.parse("1882-03-23T00:00:00Z"))
            DataBaseTestInitializer.createSimpleTestTransaction(db, UUID.fromString("0ebfed75-1448-48c5-ab8f-5680c5c57826"), group, User2, User1, timestamp = Instant.parse("1912-06-23T00:00:00Z"))
            DataBaseTestInitializer.createSimpleTestTransaction(db, UUID.fromString("e9f3c296-d235-43c0-990a-144941c0edf9"), group, User2, User1, timestamp = Instant.parse("1871-07-27T00:00:00Z"))
            assertSuccess(gm.getGroupInfo(group)) {
                assertEquals(Instant.parse("1912-06-23T00:00:00Z"), it.mostRecentTransaction, "Wrong value for most recent transaction.")
            }
        }
    }

    @Test
    fun get_group_info_fails_for_nonexistent_group() {
        assertError(ErrorCategory.GroupNotFound, gm.getGroupInfo(NonexistentGroup))
    }

    @Test
    fun set_display_name_sets_the_display_name_correctly() {
        assertSuccess(gm.createGroup(User1, "Initial Display Name")) { group ->
            assertSuccess(gm.setDisplayName(group, "New Display Name"))
            val displayNames = db.from(Tables.Groups)
                .select(Tables.Groups.displayName)
                .where(Tables.Groups.id eq group)
                .map { row -> row[Tables.Groups.displayName] }
                .toList()
            assertEquals(listOf("New Display Name"), displayNames, "setDisplayName stored the wrong display name.")
        }
    }

    @Test
    fun get_user_groups_fails_on_nonexistent_user() {
        assertError(ErrorCategory.UserNotFound, gm.getUserGroups(NonexistentUser1))
    }

    @Test
    fun get_user_groups_works_on_active_user() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group1 ->
            assertSuccess(gm.addMember(group1, User2))
            assertSuccess(gm.createGroup(User1, "Group2")) { group2 ->
                assertSuccess(gm.getUserGroups(User1)) {
                    assertEquals(setOf(group1, group2), it.toSet())
                }
                assertSuccess(gm.getUserGroups(User2)) {
                    assertEquals(setOf(group1), it.toSet())
                }
            }
        }
    }

    @Test
    fun get_user_groups_works_on_deactivated_user() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            db.insert(Tables.Membership) {
                set(Tables.Membership.userId, DeactivatedUser1)
                set(Tables.Membership.groupId, group)
            }
            assertSuccess(gm.getUserGroups(User1)) {
                assertEquals(setOf(group), it.toSet())
            }
        }
    }

    @Test
    fun get_group_members_fails_on_nonexistent_group() {
        assertError(ErrorCategory.GroupNotFound, gm.getGroupMembers(NonexistentGroup))
    }

    @Test
    fun get_group_members_yields_all_members() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            assertSuccess(gm.addMember(group, User3))
            db.insert(Tables.Membership) {
                set(Tables.Membership.userId, DeactivatedUser1)
                set(Tables.Membership.groupId, group)
            }
            assertSuccess(gm.getGroupMembers(group)) {
                assertEquals(setOf(User1, User3, DeactivatedUser1), it.toSet())
            }
        }
    }

    @Test
    fun resolve_invite_token_fails_for_invalid_token() {
        assertError(ErrorCategory.InvalidInvite, gm.resolveInviteToken("INVALID"), "Invalid invite token was accepted.")
    }

    @Test
    fun resolve_invite_token_fails_for_revoked_token() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            assertSuccess(gm.regenerateInviteToken(group)) { oldToken ->
                assertSuccess(gm.regenerateInviteToken(group))
                assertError(
                    ErrorCategory.InvalidInvite,
                    gm.resolveInviteToken(oldToken),
                    "Revoked invite token was accepted."
                )
            }
        }
    }

    @Test
    fun resolve_invite_token_works_for_correct_invite_token() {
        assertSuccess(gm.createGroup(User1, "Group1"))
        assertSuccess(gm.createGroup(User1, "Group2")) { group ->
            assertSuccess(gm.regenerateInviteToken(group)) { token ->
                assertSuccess(gm.resolveInviteToken(token)) {
                    assertEquals(group, it, "resolveInviteToken resolved the wrong group.")
                }
            }
        }
    }

    @Test
    fun regenerate_invite_token_generates_a_new_token() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            db.update(Tables.Groups) {
                set(Tables.Groups.inviteToken, "OLD_TOKEN")
                where { Tables.Groups.id eq group }
            }
            assertSuccess(gm.regenerateInviteToken(group)) { newToken ->
                assertNotEquals("OLD_TOKEN", newToken, "regenerateInviteToken didn't generate a new token.")
            }
        }
    }

    @Test
    fun add_member_correctly_adds_an_active_member() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            assertSuccess(gm.addMember(group, User3))
            val members = db.from(Tables.Membership)
                .select(Tables.Membership.userId)
                .where(Tables.Membership.groupId eq group)
                .map { row -> row[Tables.Membership.userId] }
                .toSet()
            assertEquals(setOf(User1, User3), members, "addMember doesn't add an active user.")
        }
    }

    @Test
    fun add_member_correctly_adds_a_deactivated_member() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            assertSuccess(gm.addMember(group, DeactivatedUser2))
            val members = db.from(Tables.Membership)
                .select(Tables.Membership.userId)
                .where(Tables.Membership.groupId eq group)
                .map { row -> row[Tables.Membership.userId] }
                .toSet()
            assertEquals(setOf(User1, DeactivatedUser2), members, "addMember doesn't add a deactivated user.")
        }
    }

    @Test
    fun add_member_is_idempotent_on_user_who_is_already_a_member() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            assertSuccess(gm.addMember(group, User1))
            val members = db.from(Tables.Membership)
                .select(Tables.Membership.userId)
                .where(Tables.Membership.groupId eq group)
                .map { row -> row[Tables.Membership.userId] }
                .toSet()
            assertEquals(setOf(User1), members, "addMember must be idempotent if user is already a member.")
        }
    }

    @Test
    fun remove_member_correctly_removes_an_active_member() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            assertSuccess(gm.addMember(group, User3))
            assertSuccess(gm.removeMember(group, User1))
            val members = db.from(Tables.Membership)
                .select(Tables.Membership.userId)
                .where(Tables.Membership.groupId eq group)
                .map { row -> row[Tables.Membership.userId] }
                .toSet()
            assertEquals(setOf(User3), members, "removeMember doesn't remove an active user.")
        }
    }
    
    @Test
    fun remove_member_does_not_remove_a_member_with_nonzero_balance() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            assertSuccess(gm.addMember(group, User3))
            DataBaseTestInitializer.createSimpleTestTransaction(db, UUID.fromString("4f4ce5b1-4cde-48a2-b0ce-0d34ea5895dc"), group, User3, User1, amount = BigDec(5))
            assertError(ErrorCategory.PreconditionFailed, gm.removeMember(group, User1), "User with nonzero balance was removed.")
            DataBaseTestInitializer.createSimpleTestTransaction(db, UUID.fromString("20789b1d-1360-4d49-8592-2f754fc65f41"), group, User1, User3, amount = BigDec(3))
            DataBaseTestInitializer.createSimpleTestTransaction(db, UUID.fromString("d5956943-03e2-4529-b190-81fc3aebdd27"), group, User1, User3, amount = BigDec(2))
            assertSuccess(gm.removeMember(group, User1), "User with zero balance was not removed.")
        }
    }

    @Test
    fun group_gets_deleted_after_removing_last_active_member() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            assertSuccess(gm.addMember(group, DeactivatedUser1))
            assertSuccess(gm.removeMember(group, User1))
            assertError(ErrorCategory.GroupNotFound, gm.getGroupInfo(group))
        }
    }

    @Test
    fun group_does_NOT_get_deleted_after_removing_member_when_there_is_still_an_active_member_left() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            assertSuccess(gm.addMember(group, DeactivatedUser1))
            assertSuccess(gm.addMember(group, User2))
            assertSuccess(gm.removeMember(group, User1))
            assertSuccess(gm.getGroupInfo(group))
        }
    }

    @Test
    fun remove_member_correctly_removes_a_deactivated_member() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            assertSuccess(gm.addMember(group, DeactivatedUser2))
            assertSuccess(gm.removeMember(group, DeactivatedUser2))
            val members = db.from(Tables.Membership)
                .select(Tables.Membership.userId)
                .where(Tables.Membership.groupId eq group)
                .map { row -> row[Tables.Membership.userId] }
                .toSet()
            assertEquals(setOf(User1), members, "addMember doesn't remove a deactivated user.")
        }
    }

    @Test
    fun remove_member_is_idempotent_on_user_who_is_not_a_member() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group ->
            assertSuccess(gm.removeMember(group, User2))
            assertSuccess(gm.removeMember(group, DeactivatedUser1))
            val members = db.from(Tables.Membership)
                .select(Tables.Membership.userId)
                .where(Tables.Membership.groupId eq group)
                .map { row -> row[Tables.Membership.userId] }
                .toSet()
            assertEquals(setOf(User1), members, "removeMember must be idempotent if user is not a member.")
        }
    }

    @Test
    fun all_users_known_succeeds_on_empty() {
        assertSuccess(ga.allUsersKnown(User1, setOf()))
    }

    @Test
    fun all_users_known_succeeds_on_self() {
        assertSuccess(ga.allUsersKnown(User1, setOf(User1)))
    }

    @Test
    fun all_users_known_lists_all_known_users_for_active_user() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group1 ->
            db.insert(Tables.Membership) {
                set(Tables.Membership.userId, DeactivatedUser2)
                set(Tables.Membership.groupId, group1)
            }
            assertSuccess(gm.createGroup(User1, "Group2")) { group2 ->
                assertSuccess(gm.addMember(group2, User2))
                assertSuccess(ga.allUsersKnown(User1, setOf(User1, User2, DeactivatedUser2)))
                assertSuccess(ga.allUsersKnown(User1, setOf(User1)))
                assertSuccess(ga.allUsersKnown(User1, setOf(User2)))
                assertSuccess(ga.allUsersKnown(User1, setOf(DeactivatedUser2)))
                assertError(ErrorCategory.UserNotFound, ga.allUsersKnown(User1, setOf(User1, User3)))
                assertError(ErrorCategory.UserNotFound, ga.allUsersKnown(User1, setOf(User2, DeactivatedUser1)))
                assertError(ErrorCategory.UserNotFound, ga.allUsersKnown(User1, setOf(User3)))
                assertError(ErrorCategory.UserNotFound, ga.allUsersKnown(User1, setOf(DeactivatedUser1)))
            }
        }
    }

    @Test
    fun all_users_known_fails_for_deactivated_acting_user() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group1 ->
            db.insert(Tables.Membership) {
                set(Tables.Membership.userId, DeactivatedUser2)
                set(Tables.Membership.groupId, group1)
            }
            assertError(ErrorCategory.UserNotFound, ga.allUsersKnown(DeactivatedUser2, setOf()))
            assertError(ErrorCategory.UserNotFound, ga.allUsersKnown(DeactivatedUser2, setOf(DeactivatedUser2)))
            assertError(ErrorCategory.UserNotFound, ga.allUsersKnown(DeactivatedUser2, setOf(User2)))
        }
    }

    @Test
    fun all_groups_known_succeeds_on_empty() {
        assertSuccess(ga.allGroupsKnown(User1, setOf()))
    }

    @Test
    fun all_groups_known_lists_all_known_groups_for_active_user() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group1 ->
            assertSuccess(gm.createGroup(User1, "Group2")) { group2 ->
                assertSuccess(ga.allGroupsKnown(User1, setOf(group1, group2)))
                assertSuccess(ga.allGroupsKnown(User1, setOf(group1)))
                assertSuccess(ga.allGroupsKnown(User1, setOf(group2)))
                assertError(ErrorCategory.GroupNotFound, ga.allGroupsKnown(User1, setOf(NonexistentGroup)))
                assertError(ErrorCategory.GroupNotFound, ga.allGroupsKnown(User1, setOf(group1, NonexistentGroup)))
                assertSuccess(ga.groupKnown(User1, group1))
                assertSuccess(ga.groupKnown(User1, group2))
                assertError(ErrorCategory.GroupNotFound, ga.groupKnown(User1, NonexistentGroup))
            }
        }
    }

    @Test
    fun all_groups_known_fails_for_deactivated_acting_user() {
        assertSuccess(gm.createGroup(User1, "Group1")) { group1 ->
            db.insert(Tables.Membership) {
                set(Tables.Membership.userId, DeactivatedUser2)
                set(Tables.Membership.groupId, group1)
            }
            assertError(ErrorCategory.GroupNotFound, ga.allGroupsKnown(DeactivatedUser2, setOf()))
            assertError(ErrorCategory.GroupNotFound, ga.allGroupsKnown(DeactivatedUser2, setOf(group1)))
            assertError(ErrorCategory.GroupNotFound, ga.allGroupsKnown(DeactivatedUser2, setOf(NonexistentGroup)))
            assertError(ErrorCategory.GroupNotFound, ga.groupKnown(DeactivatedUser2, group1))
            assertError(ErrorCategory.GroupNotFound, ga.groupKnown(DeactivatedUser2, NonexistentGroup))
        }
    }

    @BeforeEach
    fun setup() {
        db = DataBaseTestInitializer.connectTestDatabase()
        db.useTransaction {
            DataBaseTestInitializer.createTestUser(db, User1)
            DataBaseTestInitializer.createTestUser(db, User2)
            DataBaseTestInitializer.createTestUser(db, User3)
            DataBaseTestInitializer.createDeactivatedTestUser(db, DeactivatedUser1)
            DataBaseTestInitializer.createDeactivatedTestUser(db, DeactivatedUser2)
        }
        val manager = GroupManager(db)
        gm = manager
        ga = manager.authorizer
    }

    companion object {
        private val User1: UserId = UserId("user1")
        private val User2: UserId = UserId("user2")
        private val User3: UserId = UserId("user3")
        private val DeactivatedUser1: UserId = UserId("disabled_user1")
        private val DeactivatedUser2: UserId = UserId("disabled_user2")
        private val NonexistentUser1: UserId = UserId("nonexistent_user1")
        private val NonexistentGroup: GroupId = GroupId(UUID.fromString("b64f67fe-432f-4789-8799-3a03b10119f6"))
    }
}
