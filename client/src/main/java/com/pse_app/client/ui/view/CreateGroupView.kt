package com.pse_app.client.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
import com.pse_app.client.ui.NavigationRoute
import com.pse_app.client.ui.theme.pseTopAppBarColors
import com.pse_app.client.ui.view_model.create_group.AbstractCreateGroupViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Serializable
object CreateGroup : NavigationRoute

/**
 * Test tags for [CreateGroupView].
 *
 * @param testTag The test tag.
 */
enum class CreateGroupViewTestTags(val testTag: String) {
    /**
     * Test tag for group name text field.
     */
    GroupNameTextField("CreateGroupViewGroupNameTextField"),

    /**
     * Test tag for create group confirm button.
     */
    CreateGroupConfirmButton("CreateGroupViewCreateButtonConfirmButton")
}

/**
 * View for creating a new group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupView(viewModel: AbstractCreateGroupViewModel) {
    var groupName by rememberSaveable { mutableStateOf("") }
    var enableErrors by rememberSaveable { mutableStateOf(false) }
    val isRequestActive by viewModel.isRequestActive.collectAsState()

    fun showError() = !viewModel.isValidName(groupName) && enableErrors

    BaseScaffold(errorFlow = viewModel.errors, topBar = {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.create_group)) },
            navigationIcon = { viewModel.BackButton() },
            actions = { },
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
            OutlinedTextField(
                modifier = Modifier
                    .width(300.dp)
                    .testTag(CreateGroupViewTestTags.GroupNameTextField.testTag),
                value = groupName,
                onValueChange = { newName ->
                    enableErrors = true
                    groupName = newName
                },
                label = { Text(stringResource(R.string.group_name)) },
                singleLine = true,
                isError = showError(),
                trailingIcon = conditionalErrorIcon(showError()),
                supportingText = {
                    TextFieldSupportingText(
                        showError(),
                        stringResource(R.string.invalid_group_name),
                    )
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (isRequestActive) CircularProgressIndicator(
                modifier = Modifier.height(5.dp)
            ) else Button(
                onClick = {
                    if (viewModel.isValidName(groupName)) viewModel.createGroup(groupName)
                    else enableErrors = true
                },
                enabled = !showError(),
                modifier = Modifier.testTag(CreateGroupViewTestTags.CreateGroupConfirmButton.testTag)
            ) {
                Text(stringResource(R.string.create))
            }
        }
    }
}

@Preview
@Composable
fun PreviewCreateGroupView() {
    CreateGroupView(object : AbstractCreateGroupViewModel() {
        override fun createGroup(name: String) {
            // Stubbed out for preview purposes
        }

        override val isRequestActive = MutableStateFlow(false)
        override fun isValidName(name: String) = true
    })
}
