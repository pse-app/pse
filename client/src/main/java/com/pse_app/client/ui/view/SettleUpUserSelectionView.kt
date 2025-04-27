package com.pse_app.client.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.AbsoluteAlignment
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
import com.pse_app.client.ui.view_model.settle_up_user_selection.AbstractSettleUpUserSelectionViewModel
import com.pse_app.client.ui.view_model.settle_up_user_selection.SettleUpUserUIState
import com.pse_app.client.ui.view_model.settle_up_user_selection.SettleUserRow
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.util.Currency

/**
 * View for selecting users to settle up with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpUserSelectionView(viewModel: AbstractSettleUpUserSelectionViewModel) {
    BaseScaffold(errorFlow = viewModel.errors,
        topBar = {}
    ) { padding ->
        SidebarNavigationDrawer(
            padding = padding,
            onMainMenuClick = { viewModel.navigateToMainMenu() },
            onSettleUpClick = {},
            settleUpButtonEnabled = false
        )
        { setDrawerState ->
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.settle_up)) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                setDrawerState(DrawerValue.Open)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.menu)
                            )
                        }
                    },
                    colors = pseTopAppBarColors()
                )
                val uiState = viewModel.uiState.collectAsState().value
                if (uiState == null) LoadingScreen(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    onRetry = { viewModel.onEntry() },
                    loadingText = stringResource(R.string.loading_others),
                    errorText = stringResource(R.string.failed_to_load_others),
                    showRetryOnError = viewModel,
                )
                else if (uiState.rows.isEmpty()) NothingToShowScreen(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    header = stringResource(R.string.nothing_to_see_here),
                    subtext = stringResource(R.string.nothing_to_settle_subtext),
                    goBack = viewModel::goBack,
                )
                else SettleUpUserSelectionMainView(viewModel, uiState)
            }
        }
    }
}

@Serializable
object SettleUpUserSelection : NavigationRoute

@Composable
private fun ColumnScope.SettleUpUserSelectionMainView(
    viewModel: AbstractSettleUpUserSelectionViewModel,
    uiState: SettleUpUserUIState,
) {
    // Total amount
    Row(
        modifier = Modifier
            .padding(start = 10.dp, top = 12.dp)
            .align(Alignment.Start)
    ) {
        Text(text = "${stringForTotal(uiState.total)}: ", fontSize = 20.sp)
        BalanceText(
            uiState.total,
            currency = viewModel.currency,
            fontSize = 20.sp,
            absolute = true
        )

    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        stringResource(R.string.settle_up_explainer),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp),
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))
    Text(stringResource(R.string.proposed_to_settle), fontSize = 16.sp)
    Spacer(modifier = Modifier.height(4.dp))

    LazyColumn {
        // List of users
        items(uiState.rows) { user ->
            val proposedTotal = user.amount
            val displayName = user.displayName
            val profilePicture = user.profilePicture
            ElevatedCard(modifier = Modifier
                .padding(8.dp)
                .width(350.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfilePicture(
                        url = profilePicture,
                        displayName = displayName,
                        contentDescription = null,
                        modifier = Modifier.height(40.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    DisplayNameText(
                        displayName,
                        isCurrentUser = false,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Proposed total
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = AbsoluteAlignment.Right,
                    ) {
                        if (proposedTotal != BigDec(0)) {
                            Text(
                                text = stringForProposed(proposedTotal),
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                                textAlign = TextAlign.Right,
                            )
                        }
                        BalanceText(
                            proposedTotal,
                            currency = viewModel.currency,
                            absolute = true,
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Select button
                    FilledIconButton(
                        onClick = { viewModel.selectUser(user.id) },
                        modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.select),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

}


@Preview
@Composable
fun PreviewSettleUpUserSelectionView() {
    SettleUpUserSelectionView(object : AbstractSettleUpUserSelectionViewModel() {
        override val currency = Currency.getInstance("EUR")

        override val uiState: StateFlow<SettleUpUserUIState>
            get() = MutableStateFlow(
                SettleUpUserUIState(
                    BigDec("45.60"),
                    listOf(
                        SettleUserRow("", "Han Solo", null, BigDec("50.00")),
                        SettleUserRow("", "Chewbacca", null, BigDec("0.00")),
                        SettleUserRow(
                            "",
                            "Der Vater von Darth Vaders Sohn mit langem Namen und wenig Geld",
                            "https://upload.wikimedia.org/wikipedia/en/f/f7/Darth_Vader.png",
                            BigDec("-243993085345729583725.00")
                        ),
                    )
                )
            )

        override fun navigateToMainMenu() {
            // Stubbed out for preview purposes
        }

        override fun selectUser(userId: String) {
            // Stubbed out for preview purposes
        }
    })
}
