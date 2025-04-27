package com.pse_app.client.ui.view

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.pse_app.client.ui.view_model.expense.AbstractExpenseViewModel
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import java.util.Currency

@Serializable
data class ExpenseView(val groupId: String) : NavigationRoute

/**
 * Test tags for [ExpenseView].
 *
 * @param testTag The test tag.
 */
enum class ExpenseViewTestTags(val testTag: String) {
    /**
     * Test tag for name text field.
     */
    NameTextField("ExpenseViewNameTextField"),

    /**
     * Test tag for total amount text field.
     */
    TotalAmountTextField("ExpenseViewTotalAmountTextField"),

    /**
     * Test tag for split evenly checkbox.
     */
    SplitEvenlyCheckbox("ExpenseViewSplitEvenlyCheckbox"),

    /**
     * Test tag for submit button.
     */
    SubmitButton("ExpenseViewSubmitButton"),

    /**
     * Test tag for payment tab.
     */
    PaymentTab("ExpenseViewPaymentTab")
}

/**
 * View for creating a new expense.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseView(viewModel: AbstractExpenseViewModel) {
    val groupName by viewModel.selectedGroup.displayName.collectAsState(null)
    val currentUser by viewModel.currentUser.collectAsState()

    BaseScaffold(errorFlow = viewModel.errors, topBar = {
        Column {
            CenterAlignedTopAppBar(
                title = { Text(groupName ?: "", maxLines = 1) },
                colors = pseTopAppBarColors(),
                navigationIcon = { viewModel.BackButton() }
            )
            TabRow(
                selectedTabIndex = 0,
                containerColor = pseTopAppBarColors().containerColor,
                contentColor = pseTopAppBarColors().titleContentColor
            ) {
                // We're already on this screen so we don't need to do anything on click
                Tab(selected = true, onClick = {}, text = {
                    Text(stringResource(R.string.new_group_expense))
                })

                Tab(
                    selected = false,
                    onClick = { viewModel.navigateToPayment() },
                    modifier = Modifier.testTag(ExpenseViewTestTags.PaymentTab.testTag),
                    text = {
                        Text(stringResource(R.string.new_payment))
                    })
            }
        }
    }) { padding ->
        val users = viewModel.users.collectAsState(null).value

        if (users.isNullOrEmpty()) LoadingScreen(
            modifier = Modifier.padding(padding).fillMaxSize(),
            onRetry = { viewModel.onEntry() },
            loadingText = stringResource(R.string.loading_members),
            errorText = stringResource(R.string.failed_to_load_members),
            showRetryOnError = viewModel,
        )
        else if (users == listOf(currentUser)) NothingToShowScreen(
            modifier = Modifier.padding(padding).fillMaxSize(),
            header = stringResource(R.string.nothing_to_see_here),
            subtext = stringResource(R.string.invite_other_members_to_add_transactions),
            goBack = viewModel::goBack,
        )
        else ExpenseMainView(padding, viewModel, users)
    }
}

@Composable
private fun ExpenseMainView(
    padding: PaddingValues,
    viewModel: AbstractExpenseViewModel,
    users: List<User>
) {
    var expenseName by rememberSaveable { mutableStateOf("") }
    var isValidExpenseName by rememberSaveable { mutableStateOf(false) }
    var allowInvalidExpenseName by rememberSaveable { mutableStateOf(true) }
    fun showInvalidExpenseName() = !isValidExpenseName && !allowInvalidExpenseName

    val total by viewModel.total.collectAsState(BigDec.ZERO)
    val recordedBalances by viewModel.recordedBalances.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val inputsAreValid by viewModel.inputsAreValid.collectAsState()
    val inSplitEvenlyMode by viewModel.inSplitEvenlyMode.collectAsState()

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = expenseName,
            onValueChange = {
                expenseName = it
                isValidExpenseName = viewModel.isValidExpenseName(expenseName)
                allowInvalidExpenseName = false
            },
            singleLine = true,
            label = { Text(stringResource(R.string.name)) },
            modifier = Modifier
                .width(300.dp)
                .testTag(ExpenseViewTestTags.NameTextField.testTag),
            isError = showInvalidExpenseName(),
            supportingText = {
                TextFieldSupportingText(
                    show = showInvalidExpenseName(),
                    text = stringResource(R.string.expense_name_must_not_be_blank),
                )
            },
            trailingIcon = conditionalErrorIcon(showInvalidExpenseName()),
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            CenteredText(stringResource(R.string.split_evenly))
            Checkbox(
                checked = inSplitEvenlyMode,
                onCheckedChange = viewModel::setSplitEvenlyMode,
                modifier = Modifier.testTag(ExpenseViewTestTags.SplitEvenlyCheckbox.testTag)
            )
        }

        if (inSplitEvenlyMode) {
            MonetaryOutlinedTextField(
                label = { Text(stringResource(R.string.you_paid)) },
                initialValue = if (total == BigDec.ZERO) null else total,
                nonNegative = true,
                onValueChange = { newValue -> viewModel.splitEvenlyBasedOnTotal(newValue) },
                currency = viewModel.currency,
                modifier = Modifier.testTag(ExpenseViewTestTags.TotalAmountTextField.testTag)
            )
        } else {
            Text(stringResource(R.string.total), style = MaterialTheme.typography.titleLarge)

            BalanceText(
                balance = total,
                currency = viewModel.currency,
                colored = false,
                absolute = true,
                fontSize = MaterialTheme.typography.titleLarge.fontSize
            )
        }
        Text(stringResource(R.string.members_owe), modifier = Modifier.padding(top=10.dp, bottom = 5.dp))
        for (user in users) {
            val displayName by user.displayName.collectAsState(null)
            val profilePicture by user.profilePicture.collectAsState(null)

            UserCard(
                modifier = Modifier.padding(10.dp),
                displayName,
                profilePicture,
                isCurrentUser = user == currentUser,
                displayNameWidth = 150.dp,
                addWeightedSpacer = false,
            ) {
                if (inSplitEvenlyMode) {
                    BalanceText(
                        modifier = Modifier.weight(1f),
                        balance = recordedBalances[user] ?: BigDec.ZERO,
                        absolute = true,
                        colored = false,
                        fontSize = MaterialTheme.typography.titleLarge.fontSize,
                        currency = viewModel.currency,
                    )
                    val selectedUsersForSplit by viewModel.selectedUsersForSplit.collectAsState()

                    Checkbox(
                        checked = selectedUsersForSplit.contains(user),
                        onCheckedChange = { checked ->
                            if (checked) {
                                viewModel.changeUserSelection(user, true)
                            } else {
                                viewModel.changeUserSelection(user, false)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    MonetaryOutlinedTextField(
                        initialValue = if (recordedBalances[user] != BigDec.ZERO) recordedBalances[user] else null,
                        modifier = Modifier.width(100.dp),
                        nonNegative = true,
                        onValueChange = { newValue ->
                            viewModel.changeAmountForUser(
                                user,
                                newValue
                            )
                        },
                        currency = viewModel.currency,
                    )
                }
            }

        }

        Spacer(modifier = Modifier.height(10.dp))

        val isDispatching by viewModel.isDispatching.collectAsState()
        if (isDispatching) CircularProgressIndicator(modifier = Modifier.height(5.dp))
        else Button(
            onClick = {
                if (isValidExpenseName) viewModel.confirm(expenseName)
                else allowInvalidExpenseName = false
            },
            enabled = inputsAreValid && !showInvalidExpenseName(),
            modifier = Modifier.testTag(ExpenseViewTestTags.SubmitButton.testTag)
        ) {
            Text(stringResource(R.string.done))
        }
    }
}

@Preview
@Composable
fun PreviewExpenseView() {
    fun makeUser(name: String): User {
        return PreviewUser(name, name, null)
    }

    ExpenseView(object : AbstractExpenseViewModel() {
        private val luke = makeUser("Luke Skywalker")
        override val total: StateFlow<BigDec> = MutableStateFlow(BigDec("50"))
        override val inSplitEvenlyMode: StateFlow<Boolean>
            get() = MutableStateFlow(true)
        override val selectedUsersForSplit: StateFlow<Set<User>>
            get() = MutableStateFlow(setOf())
        override val recordedBalances: StateFlow<Map<User, BigDec>> = MutableStateFlow(mapOf(
            luke to BigDec("9999999999999999999999")
        ))
        override val selectedGroup: Group = object: PreviewGroup("WG Ausgaben", BigDec("9.99")) {
            override val inviteUrl: Flow<String?> = flowOf("https://example.com")
            override val balances: Flow<Map<User, BigDec>> = flowOf(mapOf(luke to BigDec("9.99")))
            override val members: Flow<List<User>?> = flowOf(listOf())
            override val transactions: Flow<List<Transaction>> = flowOf(listOf())
        }

        override val currency: Currency = Currency.getInstance("EUR")
        override val users: StateFlow<List<User>?> = MutableStateFlow(
            listOf(
                luke,
                makeUser("Chewbaccaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                makeUser("Han Solo")
            )
        )
        override val currentUser: StateFlow<User> = MutableStateFlow(luke)
        override val inputsAreValid: StateFlow<Boolean> = MutableStateFlow(true)

        override fun navigateToPayment() {
            // Stubbed out for preview purposes
        }

        override fun setNumberInputValidity(user: User, isValid: Boolean) {
            // Stubbed out for preview purposes
        }

        override fun changeAmountForUser(
            user: User,
            newAmount: BigDec
        ) {
            // Stubbed out for preview purposes
        }

        override fun setSplitEvenlyMode(inSplitEvenlyMode: Boolean) {
            // Stubbed out for preview purposes
        }

        override fun changeUserSelection(user: User, newSelectionState: Boolean) {
            // Stubbed out for preview purposes
        }

        override fun splitEvenlyBasedOnTotal(newTotal: BigDec) {
            // Stubbed out for preview purposes
        }

        override fun isValidExpenseName(name: String) = false

        override fun confirm(expenseName: String) {
            // Stubbed out for preview purposes
        }

        override val isDispatching = MutableStateFlow(false)
    })
}
