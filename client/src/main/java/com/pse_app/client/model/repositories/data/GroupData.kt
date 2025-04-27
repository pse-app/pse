package com.pse_app.client.model.repositories.data

import com.pse_app.common.util.BigDec
import java.time.Instant

/**
 * Represents data associated with a group
 * @param groupId The id of the group.
 * @param displayName The display name of the group.
 * @param inviteUrl The invite URL of the group.
 * @param members The group members.
 * @param memberBalances The balances of the members in the group.
 * @param mostRecentTransaction Timestamp of most recent transaction in group
 */
data class GroupData (
    val groupId: GroupId,
    val displayName: String,
    val inviteUrl: String,
    val members: List<UserId>,
    val memberBalances: Map<UserId, BigDec>,
    val mostRecentTransaction: Instant?
)
