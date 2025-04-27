package com.pse_app.server.data

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import java.time.Instant

/**
 * Information about one group in the system.
 * 
 * @param id The groups [GroupId].
 * @param displayName The groups display name.
 * @param inviteToken The current invite token of the group.
 * @param mostRecentTransaction The timestamp of the most recent transaction made in the group
 *                              or `null` if the group has no transactions.
 */
data class GroupInfo(
    val id: GroupId,
    val displayName: String,
    val inviteToken: String,
    val mostRecentTransaction: Instant?)

/**
 * Information about one group in the system and its members.
 * 
 * @param info The [GroupInfo] for the associated group.
 * @param members The ids of all group members.
 */
data class GroupMembershipInfo(
    val info: GroupInfo,
    val members: Set<UserId>)
