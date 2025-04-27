package com.pse_app.client.model.repositories.local

import com.pse_app.client.model.repositories.UserRepoObservables
import com.pse_app.client.model.repositories.data.UserData
import com.pse_app.client.model.repositories.data.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Expect around 10 users ~> 10 listeners
private const val BUFFER_CAPACITY = 10

/**
 * Local user repository to be used for caching and testing.
 */
open class LocalUserRepo : UserRepoObservables {
    private val userData = mutableMapOf<UserId, UserData?>()
    private val userDataChanged = MutableSharedFlow<UserId?>(extraBufferCapacity = BUFFER_CAPACITY)
    private val activeUser = MutableStateFlow<UserId?>(null)

    /**
     * Sets id of active user.
     */
    suspend fun setActiveUserId(uid: UserId) {
        activeUser.emit(uid)
    }

    /**
     * Sets data for user. Validity not checked!
     */
    suspend fun setUserData(userData: UserData) {
        this.userData[userData.userId] = userData
        userDataChanged.emit(userData.userId)
    }

    override fun observeActiveUserId(): Flow<UserId?> {
        return activeUser.asStateFlow()
    }

    override fun observeUser(userId: UserId): Flow<UserData?> {
        return userDataChanged.toObservable(userId) { userData[userId] }
    }

    override suspend fun touchUsers(users: Set<UserId>) {
        users.filter { !userData.containsKey(it) }
            .forEach { setUserData(UserData(
                it,
                it.id,
                null
            ) ) }
    }

    override suspend fun clear() {
        activeUser.emit(null)
        userData.clear()
        // null updates all observers
        userDataChanged.emit(null)
    }
}
