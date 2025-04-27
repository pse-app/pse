package com.pse_app.client.model.facade

import com.pse_app.client.model.repositories.Repo
import com.pse_app.client.model.repositories.data.GroupData
import com.pse_app.client.model.repositories.data.GroupId
import com.pse_app.client.model.repositories.data.TransactionData
import com.pse_app.client.model.repositories.data.UserId
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * [Group] implementation backed by Repository.
 */
class RepoGroup(
    private val repo: Repo,
    /** Id of this group */
    val groupId: GroupId
) : Group {
    private inline fun <T> observePart(crossinline transform: suspend (GroupData?) -> T): Flow<T> =
        repo.groupRepo.observeGroup(groupId).map(transform)

    override val id: String = groupId.id

    override val displayName: Flow<String?> by lazy { observePart { it?.displayName } }

    override val members: Flow<List<User>?> by lazy {
        observePart { it?.members?.map { userId -> RepoUser(repo, userId) } }
    }

    override val inviteUrl: Flow<String?> by lazy { observePart { it?.inviteUrl } }

    override val balances: Flow<Map<User, BigDec>> by lazy{ observePart {
        it?.memberBalances?.map { (k, v) -> RepoUser(repo, k) to v }?.toMap() ?: mapOf()
    } }

    override val personalBalance: Flow<BigDec?> by lazy {
        repo.userRepo.observeActiveUserId().combine(balances) { uid, bal ->
            uid?.let { bal[RepoUser(repo, it)] }
        } }


    private fun dataToTransaction(data: TransactionData): Transaction {
        return when (data) {
            is TransactionData.Expense -> {
                Transaction.Expense (
                    data.name,
                    data.comment,
                    data.timestamp,
                    data.balanceChanges.mapKeys { (key, _) -> RepoUser(repo, key) },
                    RepoUser(repo, data.originatingUser),
                    data.expenseAmount
                )
            }

            is TransactionData.Payment -> {
                Transaction.Payment (
                    data.name,
                    data.comment,
                    data.timestamp,
                    data.balanceChanges.mapKeys { (key, _) -> RepoUser(repo, key) },
                    RepoUser(repo, data.originatingUser)
                )
            }
        }
    }

    override val transactions: Flow<List<Transaction>?> by lazy {
        repo.transactionRepo.observeTransactions(groupId)
            .map { it?.map(::dataToTransaction) } }

    override val mostRecentTransaction: Flow<Instant?> by lazy {
        observePart { it?.mostRecentTransaction }
    }


    override suspend fun refresh() = catchInternal {
        repo.transactionRepo.refreshTransactions(groupId)
        repo.groupRepo.refreshGroup(groupId, false)
    }

    override suspend fun refreshPartial() = catchInternal {
        repo.groupRepo.refreshGroup(groupId, true)
    }

    override suspend fun setDisplayName(name: String) = catchInternal {
        repo.groupRepo.setDisplayName(groupId, name)
    }

    override suspend fun kickUser(user: User) = catchInternal {
        repo.groupRepo.kickUser(groupId, UserId(user.id))
    }

    override suspend fun regenerateInviteLink() = catchInternal {
        repo.groupRepo.regenerateInviteLink(groupId)
    }

    override suspend fun addExpense(
        name: String,
        comment: String?,
        split: Map<User, BigDec>,
        originatingUser: User,
        total: BigDec
    ) = catchInternal {
        var balanceChanges = (mapOf(originatingUser to BigDec(0))+split)
            .map { it.key to if (it.key == originatingUser) {total-it.value} else {-it.value}}
            .toMap()
        balanceChanges = stripZeroBalance(balanceChanges)
        check(balanceChangesNotEmpty(balanceChanges))

        val transaction = Transaction.Expense(
            name,
            comment,
            Instant.now(),
            balanceChanges,
            originatingUser,
            total
        )
        repo.transactionRepo.postTransactions(mapOf(groupId to transaction.toData()))
    }

    override suspend fun addPayment(
        name: String,
        comment: String?,
        externalBalanceChange: Map<User, BigDec>,
        payingUser: User,
        originatingUser: User
    ) = catchInternal {
        check(!externalBalanceChange.containsKey(payingUser))
        val sum = externalBalanceChange.toList().fold(BigDec(0)) { acc, e -> acc+e.second }
        val transaction = Transaction.Payment(
            name,
            comment,
            Instant.now(),
            (externalBalanceChange.map { it.key to -it.value } + Pair(payingUser, sum))
                .toMap()
                .let { stripZeroBalance(it) },
            originatingUser
        )
        repo.transactionRepo.postTransactions(mapOf(groupId to transaction.toData()))
    }

    override fun hashCode(): Int {
        return groupId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        return groupId == (other as RepoGroup).groupId
    }
}
