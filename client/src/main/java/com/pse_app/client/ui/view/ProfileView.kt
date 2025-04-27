package com.pse_app.client.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pse_app.client.R
import com.pse_app.client.ui.NavigationRoute
import com.pse_app.client.ui.theme.pseTopAppBarColors
import com.pse_app.client.ui.theme.redButtonColors
import com.pse_app.client.ui.view_model.profile.AbstractProfileViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable

@Serializable
object Profile : NavigationRoute

/**
 * The test tags for [ProfileView].
 *
 * @param testTag The test tag.
 */
enum class ProfileViewTestTags(val testTag: String) {
    /**
     * Test tag for logout button.
     */
    LogoutButton("ProfileViewLogoutButton"),

    /**
     * Test tag for deactivate account button.
     */
    DeactivateAccountButton("ProfileViewDeactivateAccountButton"),

    /**
     * Test tag for edit user display name button.
     */
    EditDisplayNameButton("ProfileViewEditDisplayNameButton"),

    /**
     * Test tag for submit user display name button.
     */
    SubmitDisplayNameButton("ProfileViewSubmitDisplayNameButton"),

    /**
     * Test tag for display name text field.
     */
    DisplayNameTextField("ProfileViewDisplayNameTextField")
}

/**
 * View displaying a user profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileView(viewModel: AbstractProfileViewModel) {
    val profilePicture by viewModel.profilePicture.collectAsState(null)
    val displayName by viewModel.displayName.collectAsState(null)
    val displayNameValid by viewModel.displayNameIsValid.collectAsState(false)

    BaseScaffold(errorFlow = viewModel.errors,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.profile)) },
                navigationIcon = { viewModel.BackButton() },
                colors = pseTopAppBarColors()
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            item {} // Empty item for SpaceBetween padding
            item {
                Column(
                    Modifier.padding(
                        top = padding.calculateTopPadding() + 5.dp,
                        start = padding.calculateStartPadding(LocalLayoutDirection.current),
                        end = padding.calculateEndPadding(LocalLayoutDirection.current),
                        bottom = 10.dp,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val baseTextStyle = MaterialTheme.typography.titleLarge
                    ProfilePicture(
                        url = profilePicture ?: "",
                        displayName = displayName ?: "",
                        contentDescription = null,
                        modifier = Modifier.height(160.dp),
                        textStyle = baseTextStyle.copy(fontSize = baseTextStyle.fontSize * 2)
                    )
                    val isError = !displayNameValid
                    val isDispatching by viewModel.isDispatchingNameChange.collectAsState()
                    EditableTextField(
                        value = displayName ?: "",
                        label = { Text(stringResource(R.string.display_name)) },
                        onValueChange = { newName -> viewModel.enterDisplayName(newName) },
                        onSubmit = { viewModel.confirmChangeDisplayNameRequest() },
                        isError = isError,
                        trailingIcon = conditionalErrorIcon(isError),
                        supportingText = {
                            TextFieldSupportingText(
                                isError,
                                stringResource(R.string.invalid_display_name),
                            )
                        },
                        textFieldModifier = Modifier
                            .width(300.dp)
                            .testTag(ProfileViewTestTags.DisplayNameTextField.testTag),
                        editButtonModifier = Modifier.testTag(ProfileViewTestTags.EditDisplayNameButton.testTag),
                        submitButtonModifier = Modifier.testTag(ProfileViewTestTags.SubmitDisplayNameButton.testTag),
                        isLoading = isDispatching,
                    )
                }
            }
            item {
                Box(
                    Modifier.padding(
                        start = padding.calculateStartPadding(LocalLayoutDirection.current),
                        end = padding.calculateEndPadding(LocalLayoutDirection.current),
                        bottom = 10.dp,
                    ),
                ) {
                    WithConfirmationPrompt(
                        onConfirm = viewModel::logOut,
                        confirmationButtonText = { Text(stringResource(R.string.log_out_button_text)) },
                        promptMessage = { Text(stringResource(R.string.log_out_prompt)) },
                    ) { askToConfirm ->
                        Button(
                            onClick = askToConfirm,
                            modifier = Modifier.testTag(ProfileViewTestTags.LogoutButton.testTag),
                            colors = redButtonColors(),
                        ) {
                            Text(stringResource(R.string.log_out))
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
                Box(
                    Modifier.padding(
                        bottom = padding.calculateBottomPadding(),
                        start = padding.calculateStartPadding(LocalLayoutDirection.current),
                        end = padding.calculateEndPadding(LocalLayoutDirection.current),
                    )
                ) {
                    WithConfirmationPrompt(
                        onConfirm = viewModel::deactivateAccount,
                        confirmationButtonText = { Text(stringResource(R.string.deactivate_account)) },
                        promptMessage = { Text(stringResource(R.string.deactivate_account_prompt)) }
                    ) { askToConfirm ->
                        Button(
                            onClick = askToConfirm,
                            modifier = Modifier
                                .padding(10.dp)
                                .testTag(ProfileViewTestTags.DeactivateAccountButton.testTag),
                            colors = redButtonColors(),
                        ) {
                            Text(stringResource(R.string.deactivate_account))
                        }
                    }
                }
            }
        }
    }
}


@Preview
@Composable
fun PreviewProfileView() {
    ProfileView(object : AbstractProfileViewModel() {
        override fun enterDisplayName(newPartialName: String) {
            /* Do nothing in preview */
        }

        override suspend fun confirmChangeDisplayNameRequest(): Boolean {
            return true
        }

        override fun logOut() {
            /* Do nothing in preview */
        }

        override fun deactivateAccount() {
            /* Do nothing in preview */
        }

        override val profilePicture: StateFlow<String?>
            get() = MutableStateFlow("https://upload.wikimedia.org/wikipedia/en/f/f7/Darth_Vader.png")
        override val displayName: StateFlow<String>
            get() = MutableStateFlow("Luke Skywalker")
        override val displayNameIsValid: Flow<Boolean>
            get() = flowOf(true)
        override val isDispatchingNameChange: StateFlow<Boolean> = MutableStateFlow(false)
    })
}
