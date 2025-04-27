package com.pse_app.client.ui.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pse_app.client.R
import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.Transaction
import com.pse_app.client.model.facade.User
import com.pse_app.client.model.facade.stubs.PreviewGroup
import com.pse_app.client.model.facade.stubs.PreviewUser
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.NavigationRoute
import com.pse_app.client.ui.theme.pseTopAppBarColors
import com.pse_app.client.ui.view_model.group.AbstractGroupViewModel
import com.pse_app.client.ui.view_model.group.GroupViewUIState
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency


@Serializable
data class GroupView(val groupId: String) : NavigationRoute

/**
 * Test tags for [GroupView].
 *
 * @param testTag The test tag.
 */
enum class GroupViewTestTags(val testTag: String) {
    /**
     * The test tag for settings button.
     */
    SettingsButton("GroupViewSettingsButton"),

    /**
     * The test tag for create transaction button.
     */
    CreateTransactionButton("GroupViewCreateTransactionButton")
}

/**
 * View for displaying a group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupView(viewModel: AbstractGroupViewModel) {
    val uiState by viewModel.uiState.collectAsState(GroupViewUIState(listOf()))
    val currentUser by viewModel.currentUser.collectAsState()
    BaseScaffold(
        errorFlow = viewModel.errors,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val displayName by viewModel.activeGroup.displayName.collectAsState(null)
                    val personalBalance by viewModel.activeGroup.personalBalance.collectAsState(null)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(displayName ?: "", overflow = TextOverflow.Ellipsis, maxLines = 1)
                        BalanceText(
                            personalBalance ?: BigDec.ZERO,
                            currency = viewModel.currency,
                            fontSize = MaterialTheme.typography.titleMedium.fontSize,
                            absolute = false
                        )
                    }
                },
                navigationIcon = { viewModel.BackButton() },
                actions = {
                    IconButton(
                        onClick = { viewModel.navigateToGroupSettings() },
                        modifier = Modifier.testTag(GroupViewTestTags.SettingsButton.testTag)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.group_settings)
                        )
                    }
                },
                colors = pseTopAppBarColors(),
                modifier = Modifier.clickable {
                    viewModel.navigateToGroupSettings()
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val transactions = uiState?.transactions
            val inviteUrl = viewModel.activeGroup.inviteUrl.collectAsState("")
            if (transactions == null) LoadingScreen(
                modifier = Modifier.fillMaxSize(),
                loadingText = stringResource(R.string.loading_transactions),
                errorText = stringResource(R.string.error_loading_transactions),
                onRetry = { viewModel.onEntry() },
                showRetryOnError = viewModel,
            ) else if (transactions.isEmpty()) {
                NothingToShowScreen(
                    modifier = Modifier.fillMaxSize(),
                    header = stringResource(R.string.no_transactions_header),
                    subtext = stringResource(R.string.invite_friends),
                    subContent = {
                        Row(
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Column {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                                InviteLinkField(
                                    inviteUrl.value ?: "",
                                    false
                                ) { }
                            }
                        }
                    }
                )
            } else AsyncPullToRefreshBox(
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                val userDialogState = rememberSaveable { mutableStateOf<String?>(null) }
                userDialogState.value?.let { userId ->
                    UserOptionsDialog(
                        onDismissRequest = { userDialogState.value = null },
                        kick = { viewModel.navigateToGroupSettings() },
                        settleNav = {
                            NavigationEvent.SimpleNav(
                                SettleUpGroupSelection(userId)
                            ).let(viewModel::navigate)
                        }
                    )
                }

                // We reverse the layout of the LazyColumn to make it start out on the bottom.
                // This does mean that we also need to reverse the transaction list first though.
                LazyColumn(
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // This will actually end up at the *bottom* of the page
                    // since we set reverseLayout to true.
                    item(null) {
                        Spacer(modifier = Modifier.height(50.dp))
                    }
                    items(transactions.reversed()) { transaction ->
                        when (transaction) {
                            is Transaction.Expense -> Expense(
                                currentUser = currentUser,
                                currency = viewModel.currency,
                                expense = transaction,
                                userDialogState,
                            )

                            is Transaction.Payment -> Payment(
                                currentUser = currentUser,
                                currency = viewModel.currency,
                                payment = transaction,
                                userDialogState,
                            )
                        }
                    }
                }
            }
            AddTransactionButton(onClick = { viewModel.navigateToTransaction() })
        }
    }
}

@Composable
private fun AddTransactionButton(onClick: () -> Unit) {
    Column {
        // This is drawn over the entire screen so we need to align it with the bottom
        Spacer(Modifier.weight(1f))
        Row {
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onClick,
                modifier = Modifier
                    .width(300.dp)
                    .testTag(GroupViewTestTags.CreateTransactionButton.testTag),
                colors = ButtonDefaults.elevatedButtonColors()
                    .copy(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
            ) {
                Text(stringResource(R.string.new_transaction))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Expense(
    currentUser: User,
    currency: Currency?,
    expense: Transaction.Expense,
    userDialogState: MutableState<String?>,
) {
    val displayName by expense.originatingUser.displayName.collectAsState(null)
    val profilePictureUrl by expense.originatingUser.profilePicture.collectAsState(null)

    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val isFromCurrentUser = expense.originatingUser == currentUser


    Row(Modifier.padding(10.dp)) {
        if (isFromCurrentUser) {
            Spacer(modifier = Modifier.weight(1f))
        }

        Column {
            ElevatedCard(
                Modifier
                    .width(300.dp)
                    .clickable(onClick = { isExpanded = !isExpanded })
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfilePicture(
                            modifier = Modifier
                                .height(50.dp)
                                .clickable {
                                    if (!isFromCurrentUser)
                                        userDialogState.value = expense.originatingUser.id
                                },
                            url = profilePictureUrl,
                            displayName = displayName ?: "",
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            DisplayNameText(
                                modifier = Modifier.clickable {
                                    if (!isFromCurrentUser)
                                        userDialogState.value = expense.originatingUser.id
                                },
                                displayName = displayName ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                isCurrentUser = isFromCurrentUser,
                            )
                            Text(expense.name, maxLines = 1)
                        }

                        Spacer(modifier = Modifier.width(5.dp))

                        Column(Modifier.weight(1f)) {
                            val balanceChangeForCurrentUser =
                                expense.balanceChanges.getOrDefault(currentUser, BigDec.ZERO)


                            BalanceText(
                                expense.expenseAmount,
                                currency,
                                absolute = true,
                                colored = false,
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterEnd,
                            )
                            BalanceText(
                                balanceChangeForCurrentUser,
                                currency,
                                colored = true,
                                absolute = false,
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterEnd,
                            )
                        }

                        Icon(
                            modifier = if (isExpanded) Modifier.rotate(180f) else Modifier,
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.expand)
                        )
                    }

                    if (isExpanded) {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        for ((user, amount) in expense.balanceChanges) {
                            Row(
                                modifier = Modifier.padding(
                                    end = 10.dp,
                                    bottom = 10.dp,
                                ), verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.width(40.dp))
                                UserDisplay(
                                    user = user,
                                    isCurrentUser = user == currentUser,
                                    userDialogState,
                                )
                                BalanceText(
                                    amount,
                                    currency,
                                    absolute = false,
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterEnd,
                                )
                            }
                        }
                    }
                }
            }
            TransactionBottomRow(expense, !isFromCurrentUser)
        }
        if (!isFromCurrentUser) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Payment(
    currentUser: User,
    currency: Currency?,
    payment: Transaction.Payment,
    userDialogState: MutableState<String?>,
) {
    val payingUser = payment.balanceChanges.firstNotNullOf { if (it.value > BigDec(0)) {it.key} else {null} }
    val isFromCurrentUser = currentUser == payment.originatingUser
    Row(Modifier.padding(10.dp)) {
        if (isFromCurrentUser) {
            Spacer(modifier = Modifier.weight(1f))
        }
        Column {
            ElevatedCard(
                Modifier
                    .width(300.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    val comment = payment.comment?.let { " ($it)" } ?: ""
                    Text(
                        text = stringResource(R.string.payment) + comment,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(5.dp))
                    UserDisplay(
                        user = payingUser,
                        isCurrentUser = payingUser == currentUser,
                        userDialogState
                    ) {
                        Spacer(modifier = Modifier.width(5.dp))
                        BalanceText(
                            payment.balanceChanges.getOrDefault(
                                payingUser,
                                BigDec.ZERO
                            ),
                            currency = currency,
                            absolute = false,
                            modifier = Modifier.weight(1f),
                        )
                    }


                    for ((user, amount) in payment.balanceChanges) {
                        // We skip the balance change for the originating user since
                        // we only want to display how much they paid the others
                        if (user == payingUser) {
                            continue
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(5.dp)
                        ) {
                            BalanceText(
                                amount,
                                currency = currency,
                                modifier = Modifier.width(100.dp),
                                contentAlignment = Alignment.CenterEnd,
                                absolute = false,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            UserDisplay(
                                user = user,
                                isCurrentUser = user == currentUser,
                                userDialogState,
                            )
                        }
                    }
                }
            }
            TransactionBottomRow(payment, !isFromCurrentUser)
        }
    }
}

@Composable
private fun TransactionBottomRow(transaction: Transaction, showName: Boolean) {
    Row(
        modifier = Modifier.width(300.dp)
    ) {
        Timestamp(transaction.timestamp)
        val originatorName = transaction.originatingUser.displayName.collectAsState(null)
        Spacer(Modifier.weight(1f))
        if (showName) {
            Text(
                originatorName.value ?: "",
                modifier = Modifier.widthIn(0.dp, 150.dp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Timestamp(timestamp: Instant) {
    val formattedTimestamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .format(ZonedDateTime.ofInstant(timestamp, ZoneId.systemDefault()))
    Text(formattedTimestamp, maxLines = 1)
}

@Composable
private fun UserDisplay(
    user: User,
    isCurrentUser: Boolean,
    userDialogState: MutableState<String?>,
    content: (@Composable () -> Unit) = {},
) {
    val profilePictureUrl by user.profilePicture.collectAsState(null)
    val displayName by user.displayName.collectAsState(null)

    Row(verticalAlignment = Alignment.CenterVertically) {
        ProfilePicture(
            url = profilePictureUrl,
            displayName = displayName ?: "",
            contentDescription = null,
            modifier = Modifier
                .height(30.dp)
                .clickable {
                    if (!isCurrentUser) userDialogState.value = user.id
                }
        )
        Spacer(modifier = Modifier.width(5.dp))

        DisplayNameText(
            displayName ?: "", isCurrentUser,
            modifier = Modifier
                .width(150.dp)
                .clickable {
                    if (!isCurrentUser) userDialogState.value = user.id
                }
        )

        content()
    }
}


@Composable
@Preview
fun PreviewGroupView() {
    fun makeUser(displayName: String, profilePicture: String?): User = PreviewUser(
        displayName, displayName, profilePicture
    )

    val darthVader =
        makeUser("Darth Vader", "https://upload.wikimedia.org/wikipedia/en/f/f7/Darth_Vader.png")
    val hanSolo = makeUser("Han Solo", null)
    val chewbacca = makeUser("Chewbaccaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", null)
    val lukeSkywalker = makeUser("Luke Skywalker", null)

    val uiState = GroupViewUIState(
        transactions = listOf(
            Transaction.Expense(
                name = "Einkauf",
                originatingUser = darthVader,
                balanceChanges = mapOf(
                    Pair(hanSolo, BigDec("12.5")),
                    Pair(darthVader, BigDec("-10")),
                    Pair(chewbacca, BigDec("0")),
                    Pair(lukeSkywalker, BigDec("-2.50"))
                ),
                comment = null,
                timestamp = Instant.now(),
                expenseAmount = BigDec("54.2")
            ), Transaction.Expense(
                name = "Bowling",
                originatingUser = lukeSkywalker,
                balanceChanges = mapOf(
                    Pair(hanSolo, BigDec("12.5")),
                    Pair(lukeSkywalker, BigDec("-2.50")),
                    Pair(darthVader, BigDec("-10")),
                    Pair(chewbacca, BigDec("0")),
                ),
                comment = null,
                timestamp = Instant.now(),
                expenseAmount = BigDec("54.2")
            ), Transaction.Payment(
                name = "",
                comment = "Bowling Rückzahlung",
                originatingUser = lukeSkywalker,
                balanceChanges = mapOf(
                    Pair(lukeSkywalker, BigDec("-2.50")),
                    Pair(hanSolo, BigDec("2.50"))
                ),

                timestamp = Instant.now()
            ),
            Transaction.Payment(
                name = "",
                comment = null,
                originatingUser = darthVader,
                balanceChanges = mapOf(
                    Pair(darthVader, BigDec("14.55")),
                    Pair(chewbacca, BigDec("-13.55")),
                    Pair(hanSolo, BigDec("-1.00"))
                ),
                timestamp = Instant.now()
            ),
            Transaction.Expense(
                name = "Filler",
                expenseAmount = BigDec("6"),
                comment = null,
                originatingUser = darthVader,
                balanceChanges = mapOf(
                    Pair(darthVader, BigDec("1")),
                    Pair(chewbacca, BigDec("1")),
                    Pair(hanSolo, BigDec("1")),
                    Pair(lukeSkywalker, BigDec("-346464645646546456446546456465465464"))
                ),
                timestamp = Instant.now()
            ),
            Transaction.Payment(
                name = "",
                comment = "Filler",
                originatingUser = lukeSkywalker,
                balanceChanges = mapOf(
                    Pair(darthVader, BigDec("1")),
                    Pair(chewbacca, BigDec("1")),
                    Pair(hanSolo, BigDec("198725957359829357295723583325798732325")),
                    Pair(lukeSkywalker, BigDec("-3989834697934867496738347698376396"))
                ),
                timestamp = Instant.now()
            )
        )
    )

    GroupView(object : AbstractGroupViewModel() {
        override val uiState: StateFlow<GroupViewUIState>
            get() = MutableStateFlow(uiState)

        override val activeGroup: Group
            get() = object: PreviewGroup("WG Ausgaben", BigDec(-9)) {
                override val inviteUrl = flowOf("Invite")
            }
        override val currentUser: StateFlow<User>
            get() = MutableStateFlow(lukeSkywalker)
        override val currency = Currency.getInstance("EUR")

        override suspend fun refresh() {
            // Stubbed out for preview purposes
        }

        override fun navigateToTransaction() {
            // Stubbed out for preview purposes
        }

        override fun navigateToGroupSettings() {
            // Stubbed out for preview purposes
        }
    })
}

