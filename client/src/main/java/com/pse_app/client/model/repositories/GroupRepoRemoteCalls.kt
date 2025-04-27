package com.pse_app.client.model.repositories

import com.pse_app.client.model.exceptions.ModelException
import com.pse_app.client.model.exceptions.NoActiveUserException
import com.pse_app.client.model.repositories.data.AllRecommendationData
import com.pse_app.client.model.repositories.data.GroupData
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.SettleRecommendationData
import com.pse_app.client.model.repositories.data.UserId

/**
 * GroupRepo actions assuming existence of backend.
 */
interface GroupRepoRemoteCalls {
    /**
     * Refresh [GroupData] of all groups in which active user is a member.
     *
     * @throws ModelException
     * @throws NoActiveUserException
     */
    suspend fun refreshAllGroups()

    /**
     * Refresh [GroupData] for given [GroupId].
     *
     * @throws ModelException
     */
    suspend fun refreshGroup(groupId: GroupId, fetchBalances: Boolean)

    /**
     * Create a new group with the given name.
     *
     * @throws ModelException
     * @throws NoActiveUserException
     */
    suspend fun createGroup(name: String): GroupId

    /**
     * Add active user to group with invite token.
     *
     * @throws ModelException
     * @throws NoActiveUserException
     */
    suspend fun joinGroup(inviteToken: String): GroupId

    /**
     * Regenerate invite link for given [GroupId].
     *
     * @throws ModelException
     */
    suspend fun regenerateInviteLink(groupId: GroupId)

    /**
     * Remove user with [UserId] from group with [GroupId].
     *
     * @throws ModelException
     */
    suspend fun kickUser(groupId: GroupId, userId: UserId)

    /**
     * Change the display name of group with [GroupId] to given name.
     *
     * @throws ModelException
     */
    suspend fun setDisplayName(groupId: GroupId, name: String)

    /**
     * Get a [SettleRecommendationData] for each known user.
     *
     * @throws ModelException
     * @throws NoActiveUserException
     */
    suspend fun getAllSettleRecommendations(): AllRecommendationData
}
