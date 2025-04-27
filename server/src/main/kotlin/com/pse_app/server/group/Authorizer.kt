package com.pse_app.server.group

import com.pse_app.common.dto.GroupId
import com.pse_app.server.data.Result
import com.pse_app.common.dto.UserId

/**
 * The authorizer interface
 */
interface Authorizer {

    /**
     * Checks that the provided user is active and all the provided users are either the acting user itself or share
     * a common group with them. If this is the case, returns a [Result.Success], otherwise returns an [Result.Error].
     */
    fun allUsersKnown(actingUser: UserId, users: Set<UserId>): Result<Unit>

    /**
     * Checks that the provided user is active and is a member of all the provided groups. If this is the case,
     * returns a [Result.Success], otherwise returns an [Result.Error].
     */
    fun allGroupsKnown(actingUser: UserId, groups: Set<GroupId>): Result<Unit>

    /**
     * Checks that the provided user is active and is a member of the provided group. If this is the case, returns a
     * [Result.Success], otherwise returns an [Result.Error].
     */
    fun groupKnown(actingUser: UserId, group: GroupId): Result<Unit>
}
