package com.pse_app.client.ui.view

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.GroupRemove
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pse_app.client.R
import com.pse_app.client.model.facade.User
import com.pse_app.client.model.facade.stubs.PreviewUser
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.NavigationRoute
import com.pse_app.client.ui.theme.pseTopAppBarColors
import com.pse_app.client.ui.theme.redButtonColors
import com.pse_app.client.ui.theme.redFilledIconButtonColors
import com.pse_app.client.ui.view_model.group_settings.AbstractGroupSettingsViewModel
import com.pse_app.client.ui.view_model.group_settings.GroupSettingsUIState
import com.pse_app.client.ui.view_model.group_settings.GroupUserData
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import java.util.Currency

@Serializable
data class GroupSettings(val groupId: String) : NavigationRoute

/**
 * Test tags for [GroupSettingsView].
 *
 * @param testTag The test tag.
 */
enum class GroupSettingsViewTestTags(val testTag: String) {
    /**
     * Test tag for group name text field.
     */
    GroupNameTextField("GroupSettingsViewGroupNameTextField"),

    /**
     * Test tag for group name submit button.
     */
    GroupNameSubmitButton("GroupSettingsViewGroupNameSubmitButton"),

    /**
     * Test tag for regenerate invite link button.
     */
    RegenerateInviteLinkButton("GroupSettingsViewRegenerateInviteLink"),

    /**
     * Test tag for leave group button.
     */
    LeaveGroupButton("GroupSettingsViewLeaveGroupButton"),

    /**
     * Test tag for kick button.
     */
    KickButton("GroupSettingsViewKickButton")
}

/**
 * View for group settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsView(viewModel: AbstractGroupSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState(GroupSettingsUIState("", ""))
    val currentGroupName by viewModel.currentGroupName.collectAsState()
    val currentGroupNameIsValid by viewModel.currentGroupNameIsValid.collectAsState()
    val groupMembers by viewModel.groupMembers.collectAsState(listOf())
    val isDispatchingNameChange by viewModel.isDispatchingNameChange.collectAsState()

    BaseScaffold(errorFlow = viewModel.errors,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(uiState.groupName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { viewModel.BackButton() },
                colors = pseTopAppBarColors()
            )
        }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)

            Row(modifier = Modifier.width(350.dp)) {
                val isError = !currentGroupNameIsValid
                EditableTextField(
                    value = currentGroupName,
                    label = { Text(stringResource(R.string.group_name)) },
                    onValueChange = { newName -> viewModel.changeGroupName(newName) },
                    onSubmit = { viewModel.confirmChangeGroupNameRequest() },
                    keepExpanded = true,
                    textFieldVisualTransformation = defaultValuePlaceHolderTransformation(
                        " ",
                        Color.Transparent
                    ),
                    isError = isError,
                    trailingIcon = conditionalErrorIcon(isError),
                    supportingText = {
                        TextFieldSupportingText(
                            isError,
                            stringResource(R.string.invalid_group_name)
                        )
                    },
                    textFieldModifier = Modifier
                        .fillMaxWidth()
                        .testTag(GroupSettingsViewTestTags.GroupNameTextField.testTag),
                    submitButtonModifier = Modifier.testTag(GroupSettingsViewTestTags.GroupNameSubmitButton.testTag),
                    isLoading = isDispatchingNameChange,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(350.dp)
            ) {
                InviteLinkField(
                    uiState.inviteLink,
                    true
                ) {
                    viewModel.regenerateInviteLink()
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(horizontalAlignment = Alignment.CenterHorizontally) {
                items(groupMembers) { userData ->
                    val displayName by userData.user.displayName.collectAsState(null)
                    val profilePicture by userData.user.profilePicture.collectAsState(null)
                    ElevatedCard(modifier = Modifier.width(380.dp)) {
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
                            Spacer(modifier = Modifier.width(10.dp))
                            DisplayNameText(
                                displayName ?: "",
                                modifier = Modifier.weight(1f),
                                isCurrentUser = userData.isActiveUser
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            BalanceText(
                                userData.balance,
                                currency = viewModel.currency,
                                absolute = false,
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterEnd,
                            )
                            Spacer(modifier = Modifier.width(5.dp))

                            var showConfirm by rememberSaveable { mutableStateOf(false) }
                            if (showConfirm) ConfirmationDialog(
                                onDismissRequest = { showConfirm = false },
                                onConfirm = { viewModel.kickUser(userData.user) },
                                promptMessage = {
                                    Text(
                                        stringResource(
                                            R.string.kick_user_confirmation_prompt,
                                            displayName ?: ""
                                        )
                                    )
                                },
                                confirmationButtonText = {
                                    Text(stringResource(R.string.kick_user_button_text))
                                },
                            )
                            FilledIconButton(
                                onClick = {
                                    NavigationEvent.SimpleNav(
                                        SettleUpGroupSelection(userData.user.id)
                                    ).let(viewModel::navigate)
                                },
                                modifier = Modifier
                                    .wrapContentHeight(align = Alignment.CenterVertically),
                                enabled = userData.allowSettleUp && !userData.isActiveUser,
                            ) {
                                Text("=", fontSize = 8.em)
                            }

                            FilledIconButton(
                                onClick = { showConfirm = true },
                                modifier = Modifier
                                    .wrapContentHeight(align = Alignment.CenterVertically)
                                    .testTag(GroupSettingsViewTestTags.KickButton.testTag),
                                enabled = !userData.isActiveUser,
                                colors = redFilledIconButtonColors(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GroupRemove,
                                    contentDescription = stringResource(R.string.kick_user_button_text),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            WithConfirmationPrompt(
                onConfirm = { viewModel.leaveGroup() },
                confirmationButtonText = { Text(stringResource(R.string.leave_group_button_text)) },
                promptMessage = {
                    Text(
                        stringResource(
                            R.string.leave_group_confirmation_prompt,
                            currentGroupName
                        )
                    )
                }
            ) { askToConfirm ->
                Button(
                    onClick = askToConfirm,
                    colors = redButtonColors(),
                    modifier = Modifier.testTag(GroupSettingsViewTestTags.LeaveGroupButton.testTag)
                ) {
                    Text(stringResource(R.string.leave_group))
                }
            }
        }
    }
}

@Composable
@Preview
fun PreviewGroupSettingsView() {
    fun makeUser(name: String): User {
        return PreviewUser(name, name, null)
    }

    val lukeSkywalker = makeUser("Luke Skywalker")

    GroupSettingsView(object : AbstractGroupSettingsViewModel() {
        override val isDispatchingNameChange = MutableStateFlow(false)

        override val uiState: Flow<GroupSettingsUIState>
            get() = flowOf(GroupSettingsUIState("https://example.com", "WG Ausgaben"))
        override val groupMembers: Flow<List<GroupUserData>>
            get() = flowOf(
                listOf(
                    GroupUserData(makeUser("Darth Vader"), BigDec("6969696969696969"), false,  allowSettleUp = false),
                    GroupUserData(lukeSkywalker, BigDec("5.3"), true, allowSettleUp = false),
                    GroupUserData(makeUser("Chewbacca"), BigDec("-1.34"), false, allowSettleUp =true)
                )
            )

        override val currency: Currency = Currency.getInstance("EUR")

        override val currentGroupName: StateFlow<String>
            get() = MutableStateFlow("WG Ausgaben")
        override val currentGroupNameIsValid: StateFlow<Boolean>
            get() = MutableStateFlow(true)

        override fun kickUser(user: User) {
            // Stubbed out for preview purposes
        }

        override fun changeGroupName(newName: String) {
            // Stubbed out for preview purposes
        }

        override suspend fun confirmChangeGroupNameRequest(): Boolean {
            return true
        }

        override fun regenerateInviteLink() {
            // Stubbed out for preview purposes
        }

        override fun leaveGroup() {
            // Stubbed out for preview purposes
        }
    })
}

