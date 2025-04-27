package com.pse_app.server.transaction

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.common.util.BigDec
import com.pse_app.server.data.Result
import com.pse_app.server.data.Transaction

/**
 * The transaction management interface
 */
interface TransactionManagement {

    /**
     * Retrieves all transactions made in the provided group. Fails if the group doesn't exist.
     */
    fun getTransactions(group: GroupId): Result<List<Transaction>>

    /**
     * Posts all provided transactions into the respective groups. This takes the transactions as-is, it especially
     * doesn't check that the transaction timestamp is the current time.
     * <p>
     * If a referenced group or user doesn't exist or a user is not a member of a group for that it is included in
     * a transaction, the method fails.
     * <p>
     * This method guarantees that either all transactions are added or none.
     */
    fun postTransactions(transactions: List<Transaction>): Result<Unit>

    /**
     * Takes the cartesian product of [users] and [groups] and retrieves the user balance in the group for each pair.
     * If a user is not a member of a group, the corresponding key will not be present in the returned map.
     */
    fun getBalances(users: Set<UserId>, groups: Set<GroupId>): Result<Map<Pair<UserId, GroupId>, BigDec>>
}
