package com.pse_app.client.model.repositories

import com.pse_app.client.model.exceptions.ModelException
import com.pse_app.client.model.exceptions.NoActiveUserException
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.TransactionData

/**
 * TransactionRepo actions assuming existence of backend.
 */
interface TransactionRepoRemoteCalls {
    /**
     * Refresh list of [TransactionData] for a group with [GroupId].
     *
     * @throws ModelException
     */
    suspend fun refreshTransactions(groupId: GroupId)

    /**
     * Post transactions into groups. One transaction per group.
     *
     * @throws ModelException
     * @throws NoActiveUserException
     */
    suspend fun postTransactions(transactions: Map<GroupId, TransactionData>)
}
