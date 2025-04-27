package com.pse_app.client.model.repositories

/**
 * Bundles and initializes [UserRepo], [GroupRepo] and [TransactionRepo].
 */
abstract class Repo {
    abstract val userRepo: UserRepo
    abstract val groupRepo: GroupRepo
    abstract val transactionRepo: TransactionRepo

    /**
     * Delete locally saved state in all repos. Use for logout.
     */
    suspend fun clear() {
        userRepo.clear()
        groupRepo.clear()
        transactionRepo.clear()
    }
}
