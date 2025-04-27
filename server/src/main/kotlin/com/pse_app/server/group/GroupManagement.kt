package com.pse_app.server.group

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.server.data.*

/**
 * The group management interface.
 */
interface GroupManagement {

    /**
     * Creates a new group using the provided data. [initialMember] is made single member for the group. Returns the
     * [GroupId] of the newly created group.
     */
    fun createGroup(initialMember: UserId, displayName: String): Result<GroupId>

    /**
     * Retrieves the [GroupInfo] associated with the provided group. Fails if the group doesn't exist.
     */
    fun getGroupInfo(group: GroupId): Result<GroupInfo>

    /**
     * Sets the display name of the provided group. Fails if the group doesn't exist.
     */
    fun setDisplayName(group: GroupId, displayName: String): Result<Unit>

    /**
     * Gets all groups the provided user is a member of. This method succeeds for deactivated users and fails for
     * non-existent users.
     */
    fun getUserGroups(user: UserId): Result<Set<GroupId>>

    /**
     * Gets all users that are a member of the provided group. Fails if the group doesn't exist.
     */
    fun getGroupMembers(group: GroupId): Result<Set<UserId>>

    /**
     * Resolves an invite token to the group it belongs to. Fails if the invite token is invalid or has been revoked.
     */
    fun resolveInviteToken(inviteToken: String): Result<GroupId>

    /**
     * Adds a member to a group. If the user is already a member, this does nothing. This method succeeds on deactivated
     * users and fails for non-existent users or groups.
     */
    fun addMember(group: GroupId, user: UserId): Result<Unit>

    /**
     * Removes a member from a group. If the user is not a member, this does nothing. This method succeeds on
     * deactivated users and fails for non-existent users or groups. If the balance of the member to remove is
     * not zero, the method fails as well.
     * 
     * If the group has only deactivated members after the provided user was removed, the group is deleted.
     */
    fun removeMember(group: GroupId, user: UserId): Result<Unit>

    /**
     * Generates a new invite token for the provided group. Fails if the group doesn't exist.
     */
    fun regenerateInviteToken(group: GroupId): Result<String>
}
