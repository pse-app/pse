@file:OptIn(ExperimentalFoundationApi::class)

package com.pse_app.client.ui.view

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pse_app.client.BuildConfig
import com.pse_app.client.R
import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.stubs.PreviewGroup
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.NavigationRoute
import com.pse_app.client.ui.theme.pseTopAppBarColors
import com.pse_app.client.ui.view_model.main_menu.AbstractMainMenuViewModel
import com.pse_app.client.ui.view_model.main_menu.MainMenuUIState
import com.pse_app.common.util.BigDec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.util.Currency
import kotlin.random.Random

@Serializable
object MainMenu : NavigationRoute

/**
 * View for the main menu.
 */
@Composable
fun MainMenuView(viewModel: AbstractMainMenuViewModel) {
    BaseScaffold(
        errorFlow = viewModel.errors,
        // We need to implement the top bar manually so it plays nicely with our navigation drawer.
        // If we used this topBar property, the top bar would overlay the navigation drawer.
        topBar = {},
        contentWindowInsets = WindowInsets(0),
        snackbarInsets = WindowInsets.navigationBars,
    ) { padding ->
        SidebarNavigationDrawer(
            padding = padding,
            onMainMenuClick = {},
            mainMenuButtonEnabled = false,
            onSettleUpClick = { viewModel.navigateToSettleUp() },
        ) { setDrawerState ->
            MainMenuMainView(
                viewModel,
                setDrawerState
            )
        }
    }
}

/**
 * Test tags for [MainMenuView].
 *
 * @param testTag The test tag.
 */
enum class MainMenuViewTestTags(val testTag: String) {
    /**
     * Test tag for profile button.
     */
    ProfileButton("MainMenuViewProfileButton"),

    /**
     * Test tag for use group invite button.
     */
    UseGroupInviteButton("MainMenuViewGroupInviteButton"),

    /**
     * Test tag for create group button.
     */
    CreateGroupButton("MainMenuViewCreateGroupButton")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainMenuMainView(
    viewModel: AbstractMainMenuViewModel,
    setDrawerState: (DrawerValue) -> Unit,
) {
    val showJoinDialogState = rememberSaveable { mutableStateOf(false) }
    var showJoinDialog by showJoinDialogState

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
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
            actions = {
                IconButton(
                    onClick = { viewModel.navigateToProfile() },
                    modifier = Modifier.testTag(MainMenuViewTestTags.ProfileButton.testTag)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = stringResource(R.string.profile),
                    )
                }
            },
            colors = pseTopAppBarColors()
        )


        val uiState = viewModel.uiState.collectAsState().value

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            if (uiState == null) LoadingScreen(
                modifier = Modifier.fillMaxSize(),
                onRetry = { viewModel.onEntry() },
                loadingText = stringResource(R.string.loading_groups),
                errorText = stringResource(R.string.failed_to_load_groups),
                showRetryOnError = viewModel,
            )
            else {
                if (uiState.groups.isEmpty()) NothingToShowScreen(
                    modifier = Modifier.fillMaxSize(),
                    header = stringResource(R.string.user_not_a_member_of_any_group),
                    subtext = stringResource(R.string.create_your_own_group_or_ask_someone_to_invite_you),
                ) else {
                    GroupsColumn(viewModel, uiState.groups)
                }
            }
            ButtonColumn(viewModel, showJoinDialogState)
        }
        if (showJoinDialog) {
            ModalBottomSheet(
                onDismissRequest = { showJoinDialog = false },
            ) {
                JoinDialog(
                    viewModel = viewModel,
                    closeDialog = { showJoinDialog = false },
                )
            }
        }
    }
}

@Composable
fun ButtonColumn(
    viewModel: AbstractMainMenuViewModel,
    showJoinDialogState: MutableState<Boolean>,
) = Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    var showJoinDialog by showJoinDialogState
    Spacer(modifier = Modifier.weight(1f))
    ElevatedButton(
        onClick = { showJoinDialog = true },
        modifier = Modifier
            .width(300.dp)
            .testTag(MainMenuViewTestTags.UseGroupInviteButton.testTag),
        colors = ButtonDefaults.elevatedButtonColors()
            .copy(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
    ) {
        Text(stringResource(R.string.use_group_invite))
    }
    ElevatedButton(
        onClick = { viewModel.navigateToCreateGroup() },
        modifier = Modifier
            .width(300.dp)
            .testTag(MainMenuViewTestTags.CreateGroupButton.testTag),
        colors = ButtonDefaults.elevatedButtonColors()
            .copy(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
    ) {
        Text(stringResource(R.string.new_group))
    }
    Spacer(modifier = Modifier.height(10.dp))
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupsColumn(
    viewModel: AbstractMainMenuViewModel,
    groups: List<Group>,
) {
    var showOptionsDialog by rememberSaveable { mutableStateOf<String?>(null) }

    showOptionsDialog?.let { groupId ->
        OptionListDialog(
            onDismissRequest = { showOptionsDialog = null },
            options = listOf(
                DialogOption(stringResource(R.string.add_expense_option)) {
                    NavigationEvent.SimpleNav(
                        ExpenseView(groupId)
                    ).let(viewModel::navigate)
                },
                DialogOption(stringResource(R.string.add_payment_option)) {
                    NavigationEvent.SimpleNav(
                        PaymentView(groupId)
                    ).let(viewModel::navigate)
                },
                DialogOption(stringResource(R.string.view_settings_option)) {
                    NavigationEvent.SimpleNav(
                        GroupSettings(groupId)
                    ).let(viewModel::navigate)
                },
            )
        )
    }

    AsyncPullToRefreshBox(
        onRefresh = viewModel::refresh,
    ) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
            }
            items(groups) { group ->
                val displayName by group.displayName.collectAsState(null)
                val personalBalance by group.personalBalance.collectAsState(null)
                ElevatedCard(
                    modifier = Modifier
                        .width(300.dp)
                        .height(50.dp)
                        // clipping the shape to itself here will clip the on-click ripple effect
                        .clip(CardDefaults.elevatedShape)
                        .combinedClickable(
                            onClick = { viewModel.selectGroup(group) },
                            onLongClick = { showOptionsDialog = group.id }
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape
                                )
                                .aspectRatio(1f)
                                .wrapContentHeight(align = Alignment.CenterVertically),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            text = abbreviateName(displayName ?: ""),
                            maxLines = 1,
                        )

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            displayName ?: "",
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BalanceText(
                            personalBalance ?: BigDec.ZERO,
                            currency = viewModel.currency,
                            absolute = false,
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

private val inviteLinkPlaceholder = buildString {
    append(BuildConfig.JOIN_API_URIS
        .minBy { it.length }
        .removeSuffix("/"))
    append("/")
    @Suppress("MagicNumber") // Generates a random number
    append(BigInteger(ByteArray(0xFE) + Random.nextBytes(10)).toString(36))
}


/**
 * Test tags for [JoinDialog].
 *
 * @param testTag The test tag.
 */
enum class JoinDialogTestTags(val testTag: String) {
    /**
     * Test tag for invite link text field.
     */
    InviteLinkTextField("JoinDialogInviteLinkTextField"),

    /**
     * Test tag for ok button.
     */
    OkButton("JoinDialogOkButton")
}


@Composable
private fun JoinDialog(viewModel: AbstractMainMenuViewModel, closeDialog: () -> Unit) {
    var invite by rememberSaveable { mutableStateOf("") }
    var isInvalid by rememberSaveable { mutableStateOf(true) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    fun updateInvite(newInvite: String) {
        showErrors = true
        invite = newInvite
        isInvalid = parseInvite(invite) == null
    }

    fun isError() = isInvalid && showErrors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.join_a_group),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            stringResource(R.string.enter_an_invite_below),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(.85f),
        )
        Spacer(Modifier.height(30.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(350.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .width(300.dp)
                    .testTag(JoinDialogTestTags.InviteLinkTextField.testTag),
                value = invite,
                onValueChange = ::updateInvite,
                readOnly = false,
                singleLine = true,
                label = { Text(stringResource(R.string.invite_link)) },
                visualTransformation = defaultValuePlaceHolderTransformation(
                    inviteLinkPlaceholder,
                    TextFieldDefaults.colors().disabledPlaceholderColor,
                ),
                isError = isError(),
                supportingText = {
                    TextFieldSupportingText(
                        isError(),
                        stringResource(R.string.invalid_invite_link_format),
                    )
                },
                trailingIcon = conditionalErrorIcon(isError())
            )
            val clipboardManager = LocalClipboardManager.current
            IconButton(
                onClick = { clipboardManager.getText()?.text?.let(::updateInvite) },
                modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.content_paste_24px),
                    contentDescription = stringResource(R.string.set_from_clipboard)
                )
            }
        }
        Spacer(Modifier.height(15.dp))
        Button(
            onClick = {
                if (isInvalid) showErrors = true
                else {
                    closeDialog()
                    viewModel.joinGroupConfirm(parseInvite(invite))
                }
            },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag(JoinDialogTestTags.OkButton.testTag),
        ) {
            Text(stringResource(R.string.ok))
        }
    }
}

private fun parseInvite(invite: String): String? {
    // We allow any Uri format. This includes simple relative
    // paths, e.g. only pasting an invite token will work.
    return Uri.parse(invite).lastPathSegment
}


@Preview
@Composable
fun PreviewMainMenuView() {
    fun makePreviewGroup(name: String, balance: BigDec): Group {
        return PreviewGroup(name, balance)
    }

    val previewState = MainMenuUIState(
        groups = listOf(
            makePreviewGroup(
                "WG Ausgaben (Help I am in crippling debt)",
                BigDec("-984396347896739486947397634896783973486.00")
            ),
            makePreviewGroup("Restaurant", BigDec("54.60")),
            makePreviewGroup("Neue Gruppe 1", BigDec(0)),
            makePreviewGroup("WIE FUNKTIONIERT DAS", BigDec(0))
        )
    )

    MainMenuView(object : AbstractMainMenuViewModel() {
        override val uiState: StateFlow<MainMenuUIState>
            get() = MutableStateFlow(previewState)
        override val currency: Currency = Currency.getInstance("EUR")

        override suspend fun refresh() {
            // Stubbed out for preview purposes
        }

        override fun selectGroup(group: Group) {
            // Stubbed out for preview purposes
        }

        override fun navigateToCreateGroup() {
            // Stubbed out for preview purposes
        }

        override fun navigateToProfile() {
            // Stubbed out for preview purposes
        }

        override fun navigateToSettleUp() {
            // Stubbed out for preview purposes
        }

        override fun joinGroupConfirm(invite: String?) = Unit
    })
}
