package com.pse_app.client.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pse_app.client.R
import com.pse_app.client.ui.NavigationRoute
import com.pse_app.client.ui.theme.pseTopAppBarColors
import com.pse_app.client.ui.view_model.settle_up_group_selection.AbstractSettleUpGroupSelectionViewModel
import com.pse_app.client.ui.view_model.settle_up_group_selection.SettleUpGroupSelectionUIState
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.util.Currency

/**
 * View for selection groups to settle up with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpGroupSelectionView(viewModel: AbstractSettleUpGroupSelectionViewModel) {
    val uiState = viewModel.uiState.collectAsState()
    val inputsAreValid by viewModel.inputsAreValid.collectAsState()
    val isDispatching by viewModel.isDispatching.collectAsState()

    BaseScaffold(errorFlow = viewModel.errors,
        topBar = {
            // Top bar
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settle_up)) },
                navigationIcon = { viewModel.BackButton() },
                actions = {},
                colors = pseTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(16.dp))

            // Circle with initials
            val baseTextStyle = MaterialTheme.typography.titleLarge
            ProfilePicture(
                url = uiState.value.userDesc.profilePic,
                displayName = uiState.value.userDesc.displayName,
                contentDescription = null,
                modifier = Modifier.height(128.dp),
                textStyle = baseTextStyle.copy(fontSize = baseTextStyle.fontSize * 2)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Username
            DisplayNameText(
                displayName = uiState.value.userDesc.displayName,
                // It is the current user, but theres no value in pointing it out
                isCurrentUser = false,
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                modifier = Modifier.padding(8.dp),
                textAlign = TextAlign.Center,
                text = stringResource(R.string.settle_user_explainer)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                // List of groups
                items(uiState.value.groups) { group ->
                    val isChecked = rememberSaveable { mutableStateOf(true) }
                    val groupName = group.displayName

                    ElevatedCard(
                        modifier = Modifier
                            .padding(8.dp)
                            .width(350.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circle with group name initials
                            ProfilePicture(
                                url = null,
                                displayName = groupName,
                                contentDescription = null,
                                modifier = Modifier.height(40.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = groupName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(200.dp)
                                )
                                Row {
                                    Text(
                                        text = "${stringResource(R.string.proposed)}: ",
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                    )
                                    BalanceText(
                                        group.proposedAmount,
                                        currency = viewModel.currency,
                                        absolute = false,
                                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Text box
                            Row(
                                modifier = Modifier.width(100.dp)
                            ) {
                                MonetaryOutlinedTextField(
                                    initialValue = group.selectedAmount,
                                    onValueChange = { newAmount ->
                                        viewModel.changeAmountForGroup(
                                            group.id,
                                            newAmount
                                        )
                                    },
                                    currency = viewModel.currency,
                                )
                            }

                            // Checkbox
                            Checkbox(
                                checked = isChecked.value,
                                onCheckedChange = { checked ->
                                    isChecked.value = checked
                                    viewModel.changeGroupSelection(group.id, checked)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Total
            Row(
                modifier = Modifier
                    .padding(bottom = 10.dp)
            ) {
                Text(text = "${stringForProposed(uiState.value.total)}: ", fontSize = 20.sp)
                BalanceText(
                    uiState.value.total,
                    currency = viewModel.currency,
                    fontSize = 20.sp,
                    absolute = true
                )
            }

            // Select button
            if (isDispatching) CircularProgressIndicator(Modifier.height(5.dp))
            else Button(onClick = { viewModel.settle() }, enabled = inputsAreValid) {
                Text(stringResource(R.string.settle))
            }
        }
    }
}

@Serializable
data class SettleUpGroupSelection(val selectedUserId: String) : NavigationRoute

@Preview
@Composable
fun PreviewSettleUpGroupSelectionView() {
    SettleUpGroupSelectionView(object : AbstractSettleUpGroupSelectionViewModel() {
        override val isDispatching = MutableStateFlow(false)
        override val uiState: StateFlow<SettleUpGroupSelectionUIState> =
            MutableStateFlow(
                SettleUpGroupSelectionUIState(
                    SettleUpGroupSelectionUIState.UserDescription(
                        "Luke Skywalkers armer Cousin mit langem Namen",
                        null
                    ),
                    BigDec("-5.00"),
                    listOf(
                        SettleUpGroupSelectionUIState.GroupDescription(
                            "a",
                            "WG Ausgaben (tiefe Schulden deswegen ist unser Name ganz lang)",
                            BigDec("-900000000000000.00"),
                            BigDec("-9.00"),
                        ),
                        SettleUpGroupSelectionUIState.GroupDescription(
                            "a",
                            "Restaurant",
                            BigDec("54.60"),
                            BigDec("54.60")
                        )
                    )

                )
            )
        override val currency = Currency.getInstance("EUR")
        override val inputsAreValid: StateFlow<Boolean>
            get() = MutableStateFlow(true)

        override fun changeAmountForGroup(group: String, newAmount: BigDec) {
            //
        }

        override fun changeGroupSelection(group: String, newSelectionState: Boolean) {
            //
        }

        override fun settle(): Boolean {
            return true
        }
    })
}
