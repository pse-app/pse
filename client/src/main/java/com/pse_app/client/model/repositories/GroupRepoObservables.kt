package com.pse_app.client.model.repositories

import com.pse_app.client.model.repositories.data.GroupData
import com.pse_app.client.model.repositories.data.GroupId
import kotlinx.coroutines.flow.Flow

/**
 * UserRepo actions independent of backend.
 */
interface GroupRepoObservables {
    /**
     * Get a Flow on the ids of all groups in which active user is a member.
     * Flow will emit a first value and then values when the data changes.
     * Emits null if no group ids set.
     */
    fun observeAllGroupIds(): Flow<List<GroupId>?>

    /**
     * Get a Flow on the full data of one groups.
     * Flow will emit a first value and then values when the data changes.
     * Emits null if group doesn't exists.
     */
    fun observeGroup(groupId: GroupId): Flow<GroupData?>

    /**
     * Delete locally saved state. Use for logout.
     */
    suspend fun clear()
}
