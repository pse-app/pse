package com.pse_app.client.model.data_layer

import com.pse_app.client.model.exceptions.LoginRejectedException
import com.pse_app.common.dto.BalancesRequestPayload
import com.pse_app.common.dto.BalancesResponsePayload
import com.pse_app.common.dto.ChangeDisplayNameRequestPayload
import com.pse_app.common.dto.CreateGroupRequestPayload
import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.GroupInfoResponsePayload
import com.pse_app.common.dto.GroupInviteInfoResponsePayload
import com.pse_app.common.dto.RegenerateInviteTokenResponsePayload
import com.pse_app.common.dto.SettingsResponsePayload
import com.pse_app.common.dto.TransactionRequestPayload
import com.pse_app.common.dto.TransactionResponsePayload
import com.pse_app.common.dto.UserId
import com.pse_app.common.dto.UserInfoResponsePayload

/**
 * Abstracts interaction with backend REST API.
 *
 * Unless otherwise specified the methods correspond to REST endpoints.
 *
 * Sessions for auth are managed through a [SessionStore]
 */
interface RemoteAPI {
    /**
     * Attempts to initiate new session with given id token.
     *
     * @throws LoginRejectedException
     */
    suspend fun createSession(idToken: String)

    /**
     * Checks whether a session currently exists and refreshes it if necessary.
     *
     * @return True if fresh session exists after call completed
     * @throws LoginRejectedException
     */
    suspend fun refreshSession(): Boolean

    /**
     * Deletes current session
     */
    suspend fun logout()

    /**
     * Callback is called when session changes state.
     * Boolean is true if session exists, false if not.
     */
    fun registerOnSessionDestroyed(callback: suspend () -> Unit)

    /** Calls /settings */
    suspend fun getSettings(): Response<SettingsResponsePayload>
    /** Calls /v1/groups */
    suspend fun getGroups(groups: Set<GroupId>): Response<Map<GroupId, GroupInfoResponsePayload>>
    /** Calls /v1/groups */
    suspend fun createGroup(request: CreateGroupRequestPayload): Response<GroupInfoResponsePayload>
    /** Calls /v1/groups/<id>/displayName */
    suspend fun setGroupDisplayName(groupId: String, request: ChangeDisplayNameRequestPayload): Response<GroupInfoResponsePayload>
    /** Calls /v1/groups/<id>/regenerateInviteLink */
    suspend fun regenerateGroupInviteLink(groupId: String): Response<RegenerateInviteTokenResponsePayload>
    /** Calls /v1/groups/<id>/kick/<id> */
    suspend fun kickUser(groupId: String, userId: String): Response<Unit>
    /** Calls /v1/join/<token> */
    suspend fun joinGroup(inviteToken: String): Response<GroupInfoResponsePayload>
    /** Calls /v1/join/<token>/info */
    suspend fun getGroupInviteInfo(inviteToken: String): Response<GroupInviteInfoResponsePayload>

    /** Calls /v1/transactions */
    suspend fun getTransactions(groups: Set<GroupId>): Response<Map<GroupId, List<TransactionResponsePayload>>>
    /** Calls /v1/transactions */
    suspend fun postTransactions(transactions: Map<GroupId, TransactionRequestPayload>): Response<Unit>
    /** Calls /v1/balances */
    suspend fun getBalances(request: BalancesRequestPayload): Response<BalancesResponsePayload>

    /** Calls /v1/me */
    suspend fun getActiveUser(): Response<UserInfoResponsePayload>
    /** Calls /v1/me */
    suspend fun deactivateActiveUser(): Response<Unit>
    /** Calls /v1/me/displayName */
    suspend fun setUserDisplayName(request: ChangeDisplayNameRequestPayload): Response<UserInfoResponsePayload>
    /** Calls /v1/knownUsers */
    suspend fun getKnownUsers(): Response<List<UserInfoResponsePayload>>
    /** Calls /v1/users */
    suspend fun getUsers(users: Set<UserId>): Response<Map<UserId, UserInfoResponsePayload>>
}
