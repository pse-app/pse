package com.pse_app.client.model.repositories.local

import com.pse_app.client.model.repositories.GroupRepoObservables
import com.pse_app.client.model.repositories.data.GroupData
import com.pse_app.client.model.repositories.data.GroupId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Expect around 10 groups
private const val BUFFER_CAPACITY = 10

/**
 * Local group repository to be used for caching and testing.
 */
open class LocalGroupRepo : GroupRepoObservables {
    private val groupIds = MutableStateFlow<List<GroupId>?>(null)
    private var groupData = mutableMapOf<GroupId, GroupData>()
    private val groupDataChanged = MutableSharedFlow<GroupId?>(extraBufferCapacity = BUFFER_CAPACITY)

    /**
     * Sets data for all groups. Validity not checked!
     */
    suspend fun setAllGroupData(groups: List<GroupData>) {
        groupData.clear()
        groupData = groups.associateBy { it.groupId }.toMutableMap()
        groupIds.emit(groups.map(GroupData::groupId))
        groupDataChanged.emit(null)
    }

    /**
     * Deletes group from groups of which the active user is a member
     */
    suspend fun deleteGroupData(groupId: GroupId) {
        groupData.remove(groupId)
        val ids = groupIds.value
        groupIds.value = if (ids != null) {ids - groupId} else {null}
        groupDataChanged.emit(groupId)
    }

    private fun insertId(groupId: GroupId) {
        if (groupIds.value?.contains(groupId) != true) {
            groupIds.value = (listOf(groupId) + (groupIds.value ?: listOf()))
        }
    }

    /**
     * Sets data for group. Validity not checked!
     */
    suspend fun setGroupData(data: GroupData) {
        groupData[data.groupId] = data
        groupDataChanged.emit(data.groupId)
        insertId(data.groupId)
    }

    override fun observeAllGroupIds(): Flow<List<GroupId>?> {
        return groupIds.asStateFlow()
    }

    override fun observeGroup(groupId: GroupId): Flow<GroupData?> {
        return groupDataChanged.toObservable(groupId) { groupData[groupId] }
    }

    override suspend fun clear() {
        groupData.clear()
        groupIds.emit(null)
        // null updates all observers
        groupDataChanged.emit(null)
    }
}
