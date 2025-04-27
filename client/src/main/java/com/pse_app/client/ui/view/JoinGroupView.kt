package com.pse_app.client.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pse_app.client.R
import com.pse_app.client.StringResource
import com.pse_app.client.getValue
import com.pse_app.client.model.facade.GroupInviteInfo
import com.pse_app.client.ui.NavigationRoute
import com.pse_app.client.ui.theme.pseTopAppBarColors
import com.pse_app.client.ui.theme.redButtonColors
import com.pse_app.client.ui.view_model.join_group.AbstractJoinGroupViewModel
import com.pse_app.client.ui.view_model.join_group.JoinGroupUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class JoinGroup(val inviteToken: String?) : NavigationRoute

/**
 * Test tags for [JoinGroupView].
 *
 * @param testTag The test tag.
 */
enum class JoinGroupViewTestTags(val testTag: String) {
    /**
     * Test tag for accept button.
     */
    AcceptButton("JoinGroupViewAcceptButton"),

    /**
     * Test tag for decline button.
     */
    DeclineButton("JoinGroupViewDeclineButton")
}

/**
 * View for joining a group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupView(viewModel: AbstractJoinGroupViewModel) {
    val stateState = viewModel.state.collectAsState()


    BaseScaffold(errorFlow = viewModel.errors, topBar = {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.join_group)) },
            navigationIcon = { viewModel.BackButton() },
            actions = { },
            colors = pseTopAppBarColors()
        )
    }) { padding ->
        when (val state = stateState.value) {
            is JoinGroupUiState.Loading -> LoadingScreen(
                modifier = Modifier.padding(padding).fillMaxSize(),
                onRetry = { if (state.isLoadingData) viewModel.onEntry() else viewModel.accept() },
                loadingText = stringResource(
                    if (state.isLoadingData) R.string.loading_group_info else R.string.accepting
                ),
                errorText = stringResource(
                    if (state.isLoadingData) R.string.failed_to_load_group_info else R.string.accepting_failed
                ),
                showRetryOnError = viewModel,
            )
            is JoinGroupUiState.Info -> GroupInvite(padding, state.info, viewModel)
            is JoinGroupUiState.Failed -> JoinFailed(padding, state.message, viewModel)
        }
    }
}


@Composable
private fun JoinFailed(
    padding: PaddingValues,
    message: StringResource,
    viewModel: AbstractJoinGroupViewModel
) = NothingToShowScreen(
    modifier = Modifier.padding(padding).fillMaxSize(),
    header = stringResource(R.string.group_join_failed),
    subtext = message.getValue(LocalContext.current),
    goBack = viewModel::decline,
)

@Composable
private fun GroupInvite(
    padding: PaddingValues,
    groupInfo: GroupInviteInfo,
    viewModel: AbstractJoinGroupViewModel
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.group_join_message),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfilePicture(
                url = null,
                displayName = groupInfo.displayName,
                contentDescription = null,
                modifier = Modifier.height(50.dp),
                textStyle = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                groupInfo.displayName,
                maxLines = 1,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.wrapContentHeight(Alignment.CenterVertically)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { viewModel.decline() },
                modifier = Modifier.testTag(JoinGroupViewTestTags.DeclineButton.testTag),
                colors = redButtonColors()
            ) {
                Text(stringResource(R.string.decline))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = { viewModel.accept() },
                modifier = Modifier.testTag(JoinGroupViewTestTags.AcceptButton.testTag)
            ) {
                Text(stringResource(R.string.accept))
            }

        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Preview
@Composable
fun PreviewJoinGroupView() {
    JoinGroupView(object : AbstractJoinGroupViewModel() {
        override val state: StateFlow<JoinGroupUiState>
            get() = MutableStateFlow(JoinGroupUiState.Failed(StringResource("Network Error: Connection Refused")))
        //    get() = MutableStateFlow(
        //        JoinGroupUiState.Info(
        //            GroupInviteInfo(
        //                displayName = "WG Ausgaben",
        //                id = "group",
        //                alreadyMember = false
        //            )
        //        )
        //    )

        override fun accept() {
            // Stubbed out for preview purposes
        }

        override fun decline() {
            // Stubbed out for preview purposes
        }
    })
}

