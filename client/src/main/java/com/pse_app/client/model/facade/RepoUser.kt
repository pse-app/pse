package com.pse_app.client.model.facade

import com.pse_app.client.model.repositories.Repo
import com.pse_app.client.model.repositories.data.UserData
import com.pse_app.client.model.repositories.data.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [User] implementation backed by Repository.
 */
class RepoUser(
    private val repo: Repo,
    /** Id of this user */
    val userId: UserId
) : User {
    private fun <R> transformFlow(transform: suspend (data: UserData?) -> R): Flow<R> =
        repo.userRepo.observeUser(userId).map(transform)

    override val id: String = userId.id

    override val displayName: Flow<String?> by lazy { transformFlow { value ->
        value?.displayName
    } }
    override val profilePicture: Flow<String?> by lazy{ transformFlow { value ->
        value?.profilePicture
    } }

    override fun hashCode(): Int {
        return userId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        return userId == (other as RepoUser).userId
    }
}
