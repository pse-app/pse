package com.pse_app.client.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pse_app.client.R
import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.Transaction
import com.pse_app.client.model.facade.User
import com.pse_app.client.model.facade.stubs.PreviewGroup
import com.pse_app.client.model.facade.stubs.PreviewUser
import com.pse_app.client.ui.NavigationRoute
import com.pse_app.client.ui.theme.pseTopAppBarColors
import com.pse_app.client.ui.view_model.payment.AbstractPaymentViewModel
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import java.util.Currency

@Serializable
data class PaymentView(val groupId: String) : NavigationRoute

/**
 * Test tags for [PaymentView].
 *
 * @param testTag The test tag.
 */
enum class PaymentViewTestTags(val testTag: String) {
    /**
     * Test tag for amount text field.
     */
    AmountTextField("PaymentViewAmountTextField"),

    /**
     * Test tag for comment text field.
     */
    CommentTextField("PaymentViewCommentTextField"),

    /**
     * Test tag for submit button.
     */
    SubmitButton("PaymentViewSubmitButton")
}

/**
 * View for creating a payment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentView(viewModel: AbstractPaymentViewModel) {
    val groupName by viewModel.selectedGroup.displayName.collectAsState(null)
    val userBalances by viewModel.userBalances.collectAsState(mapOf())
    val currentUser by viewModel.currentUser.collectAsState()

    BaseScaffold(errorFlow = viewModel.errors, topBar = {
        Column {
            CenterAlignedTopAppBar(
                title = { Text(groupName ?: "", maxLines = 1) },
                colors = pseTopAppBarColors(),
                navigationIcon = { viewModel.BackButton() }
            )
            TabRow(
                selectedTabIndex = 1,
                containerColor = pseTopAppBarColors().containerColor,
                contentColor = pseTopAppBarColors().titleContentColor
            ) {
                Tab(selected = false, onClick = { viewModel.navigateToExpense() }, text = {
                    Text(stringResource(R.string.new_group_expense))
                })

                // We're already on this screen so we don't need to do anything on click
                Tab(selected = true, onClick = { }, text = {
                    Text(stringResource(R.string.new_payment))
                })
            }
        }
    }) { padding ->
        if (userBalances.isEmpty()) LoadingScreen(
            modifier = Modifier.padding(padding).fillMaxSize(),
            onRetry = { viewModel.onEntry() },
            showRetryOnError = viewModel,
            loadingText = stringResource(R.string.loading_members),
            errorText = stringResource(R.string.failed_to_load_members),
        )
        else if (userBalances.keys == setOf(currentUser)) NothingToShowScreen(
            modifier = Modifier.padding(padding).fillMaxSize(),
            header = stringResource(R.string.nothing_to_see_here),
            subtext = stringResource(R.string.invite_other_members_to_add_transactions),
            goBack = viewModel::goBack,
        )
        else PaymentMainView(padding, viewModel)
    }
}

@Composable
private fun PaymentMainView(padding: PaddingValues, viewModel: AbstractPaymentViewModel) {
    val comment by viewModel.comment.collectAsState()
    val userBalances by viewModel.userBalances.collectAsState(mapOf())
    val currentUser by viewModel.currentUser.collectAsState()
    val inputsAreValid by viewModel.inputsAreValid.collectAsState()
    val payingUser by viewModel.payingUser.collectAsState()

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        PayingUserDropDown(
            currentUser,
            users = userBalances.keys.toList(),
            selectedUser = payingUser,
            selectUser = { user -> viewModel.setPayingUser(user) })

        Text(stringResource(R.string.paid_users), modifier = Modifier.padding(10.dp))

        for ((user, balance) in userBalances) {
            if (user != payingUser) {
                val displayName by user.displayName.collectAsState(null)
                val profilePicture by user.profilePicture.collectAsState(null)

                ElevatedCard(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        ProfilePicture(
                            url = profilePicture,
                            displayName = displayName ?: "",
                            contentDescription = null,
                            modifier = Modifier.height(40.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            DisplayNameText(
                                displayName ?: "",
                                isCurrentUser = user == currentUser,
                            )
                            Row {
                                Text(
                                    stringResource(R.string.balance_colon),
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize
                                )
                                BalanceText(
                                    balance,
                                    absolute = false,
                                    currency = viewModel.currency,
                                    contentAlignment = Alignment.CenterEnd,
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(7.dp))
                        MonetaryOutlinedTextField(
                            modifier = Modifier
                                .width(100.dp)
                                .testTag(PaymentViewTestTags.AmountTextField.testTag),
                            onValueChange = { newValue ->
                                viewModel.setAmountForUser(
                                    user,
                                    newValue,
                                )
                            },
                            nonNegative = true,
                            currency = viewModel.currency,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = comment,
            onValueChange = { newValue -> viewModel.changeComment(newValue) },
            modifier = Modifier.testTag(PaymentViewTestTags.CommentTextField.testTag),
            placeholder = { Text(stringResource(R.string.payment_comment)) }
        )

        Spacer(modifier = Modifier.height(5.dp))

        val isDispatching by viewModel.isDispatching.collectAsState()
        if (isDispatching) CircularProgressIndicator(Modifier.height(5.dp))
        else Button(
            onClick = { viewModel.confirm() },
            modifier = Modifier.testTag(PaymentViewTestTags.SubmitButton.testTag),
            enabled = inputsAreValid
        ) {
            Text(stringResource(R.string.done))
        }
    }
}

@Composable
fun PayingUserDropDown(
    currentUser: User,
    users: List<User>,
    selectedUser: User,
    selectUser: (User) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val dropDownWidth = 200.dp

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.paying_user))
        Box {
            DropdownMenuItem(
                modifier = Modifier.width(dropDownWidth),
                onClick = { expanded = !expanded },
                text = {
                    UserSelectionCard(
                        user = selectedUser,
                        isCurrentUser = selectedUser == currentUser
                    )
                })

            DropdownMenu(
                modifier = Modifier.width(dropDownWidth),
                expanded = expanded,
                onDismissRequest = { expanded = false }) {
                for (user in users) {
                    if (user != selectedUser) {
                        DropdownMenuItem(text = {
                            UserSelectionCard(
                                user = user,
                                isCurrentUser = user == currentUser
                            )
                        }, onClick = {
                            selectUser(user)
                            expanded = false
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun UserSelectionCard(user: User, isCurrentUser: Boolean) {
    val displayName by user.displayName.collectAsState(null)
    val profilePicture by user.profilePicture.collectAsState(null)

    UserCard(
        displayName = displayName,
        profilePicture = profilePicture,
        isCurrentUser = isCurrentUser,
        displayNameWidth = 150.dp
    ) { }
}


@Preview
@Composable
fun PreviewPaymentView() {
    fun makeUser(name: String): User = PreviewUser(name, name, null)

    PaymentView(object : AbstractPaymentViewModel() {
        override val selectedGroup: Group = object : PreviewGroup("WG Ausgaben", BigDec("1.23")) {
            override val id: String = "A"
            override val members: Flow<List<User>?> = flowOf(
                listOf(
                    makeUser("Darth Vader"),
                    makeUser("Luke Skywalker"),
                    makeUser("Han Solo")
                )
            )
            override val transactions: Flow<List<Transaction>> = flowOf()
            override val balances: Flow<Map<User, BigDec>> = flowOf(
                mapOf(
                    makeUser("Darth Vader") to BigDec("1"),
                    makeUser("Luke Skywalker") to BigDec("2")
                )
            )
        }
        override val currency = Currency.getInstance("EUR")
        override val userBalances: Flow<Map<User, BigDec>> = flowOf(
            mapOf(
                makeUser("Darth Vader") to BigDec("12.34"),
                makeUser("Luke Skywalkers reicher Sohn mit langem Namen") to BigDec("123456789.00")
            ),
        )
        override val currentUser: StateFlow<User> = MutableStateFlow(makeUser("Luke Skywalker"))
        override val inputsAreValid: StateFlow<Boolean> = MutableStateFlow(true)
        override val amountByUser: StateFlow<Map<User, BigDec>> = MutableStateFlow(mapOf())
        override val comment: StateFlow<String> = MutableStateFlow("")
        override val payingUser: StateFlow<User>
            get() = MutableStateFlow(makeUser("Luke Skywalker"))

        override fun navigateToExpense() {
            // Stubbed out for preview purposes
        }

        override fun setAmountForUser(
            user: User,
            amount: BigDec
        ) {
            // Stubbed out for preview purposes
        }

        override fun changeComment(newComment: String) {
            // Stubbed out for preview purposes
        }

        override fun setPayingUser(user: User) {
            // Stubbed out for preview purposes
        }

        override fun confirm() {
            // Stubbed out for preview purposes
        }

        override val isDispatching = MutableStateFlow(false)
    })
}
