package com.pse_app.client.model.repositories.remote

import com.pse_app.client.annotations.SpecifiedInDesign
import com.pse_app.client.model.data_layer.RemoteAPI
import com.pse_app.client.model.repositories.TransactionRepo
import com.pse_app.client.model.repositories.TransactionRepoObservables
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.SettleRecommendationData
import com.pse_app.client.model.repositories.data.TransactionData
import com.pse_app.client.model.repositories.local.LocalTransactionRepo

/**
 * Repository for transaction related actions and data.
 * Single source of truth for [TransactionData] and [SettleRecommendationData].
 *
 * Retrieves data from [RemoteAPI]
 */
class RemoteTransactionRepo(
    private val remoteAPI: RemoteAPI,
    @SpecifiedInDesign
    private val repo: RemoteRepo,
    private val localTransactionRepo: LocalTransactionRepo = LocalTransactionRepo()
) : TransactionRepo, TransactionRepoObservables by localTransactionRepo {
    override suspend fun refreshTransactions(groupId: GroupId) {
        val transactions = remoteAPI
            .getTransactions(setOf(groupId.cast()))
            .unpack().getValue(groupId.cast())
        localTransactionRepo.setTransactions(
            groupId,
            transactions.map { fromDTO(it) }
        )
    }

    override suspend fun postTransactions(transactions: Map<GroupId, TransactionData>) {
        remoteAPI.postTransactions(
            transactions.map { it.key.cast() to toDTO(it.value) }.toMap()
        ).unpack()
    }
}
