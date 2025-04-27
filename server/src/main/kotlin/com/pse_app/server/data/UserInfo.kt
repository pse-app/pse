package com.pse_app.server.data

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import java.net.URI

/**
 * Account information for one user in the system.
 * 
 * @param id The users [UserId].
 * @param displayName The users display name. 
 * @param profilePicture A [URI] to the users profile picture if available.
 */
data class UserInfo(
    val id: UserId,
    val displayName: String,
    val profilePicture: URI?)

/**
 * Information about one user in the system and its groups.
 *
 * @param info The [UserInfo] for the associated user.
 * @param groups The ids of all groups the user is a member of.
 */
data class UserMembershipInfo(
    val info: UserInfo,
    val groups: Set<GroupId>)
