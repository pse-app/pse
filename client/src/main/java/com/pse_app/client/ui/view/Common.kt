@file:OptIn(ExperimentalMaterial3Api::class)

package com.pse_app.client.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.pse_app.client.R
import com.pse_app.client.ui.theme.blueColor
import com.pse_app.client.ui.view_model.BaseViewModel
import kotlinx.coroutines.launch
import java.text.BreakIterator

/**
 * Test tags for common [Composable]s.
 *
 * @param testTag The test tag.
 */
enum class CommonTestTags(val testTag: String) {
    /**
     * Test tag for back button.
     */
    BackButton("CommonBackButton")
}

/**
 * The back button for navigation.
 */
@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag(CommonTestTags.BackButton.testTag)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Default.ArrowBack,
            contentDescription = stringResource(R.string.back)
        )
    }
}

/**
 * Displays a profile picture.
 */
@Composable
fun ProfilePicture(
    url: String?,
    displayName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = LocalTextStyle.current
) {
    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape
        )
    ) {
        var isShowingImage by rememberSaveable { mutableStateOf(false) }
        if (!isShowingImage) Text(
            text = abbreviateName(displayName),
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxSize()
                .wrapContentSize(),
            style = textStyle,
        )
        if (url != null) AsyncImage(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxSize()
                .clip(CircleShape),
            model = url,
            contentDescription = contentDescription,
            onState = { isShowingImage = it is AsyncImagePainter.State.Success }
        )
    }
}

/** How many characters to use for abbreviated name */
private const val CHARACTERS_FOR_ABBREVIATION = 3

internal fun abbreviateName(fullName: String): String {
    val initials = fullName
        .split(" ")
        .filter { substring -> substring.isNotEmpty() }
        .map { substring -> firstUnicodeCharacter(substring) }
        .take(CHARACTERS_FOR_ABBREVIATION)

    return initials.joinToString(separator = "")
}

private fun firstUnicodeCharacter(string: String): String {
    val boundary = BreakIterator.getCharacterInstance()
    boundary.setText(string)
    val end = boundary.next()
    return string.substring(0, end)
}

/**
 * Text that will overflow with an ellipsis, be underlined and clickable for a tooltip
 * that shows the whole thing.
 */
@Composable
fun TooltippedOverflowingTextBox(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    contentAlignment: Alignment = Alignment.TopStart,
) {
    val tooltipState = rememberTooltipState(
        initialIsVisible = false,
        isPersistent = true,
    )
    val scope = rememberCoroutineScope()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val clickableModifier = remember(layout) {
        // Only return clickable if necessary to capture as few clicks as possible
        if (layout?.hasVisualOverflow == true) {
            Modifier.clickable(interactionSource = null, indication = null) {
                if (tooltipState.isVisible) tooltipState.dismiss()
                else scope.launch { tooltipState.show() }
            }
        } else {
            Modifier
        }
    }

    Box(
        modifier = modifier.then(clickableModifier),
        contentAlignment = contentAlignment
    ) {
        TooltipBox(
            state = tooltipState,
            enableUserInput = layout?.hasVisualOverflow == true,
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { RichTooltip { Text(text = text) } },
        ) {
            Text(
                color = color,
                fontSize = fontSize,
                text = buildAnnotatedString {
                    val decoration =
                        if (layout?.hasVisualOverflow == true) TextDecoration.Underline else null
                    withStyle(SpanStyle(textDecoration = decoration)) {
                        append(text)
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = {
                    layout = it
                    if (!it.hasVisualOverflow && tooltipState.isVisible) tooltipState.dismiss()
                },
            )
        }
    }
}

/**
 * An editable text field.
 */
@Composable
fun EditableTextField(
    value: String,
    textFieldModifier: Modifier = Modifier,
    submitButtonModifier: Modifier = Modifier,
    editButtonModifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onSubmit: suspend () -> Boolean,
    label: (@Composable () -> Unit)? = null,
    keepExpanded: Boolean = false,
    textFieldVisualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isLoading: Boolean = false,
) {
    var editState by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val iconWidth = 40.dp
    if (editState || keepExpanded) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = label,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium,
                visualTransformation = textFieldVisualTransformation,
                trailingIcon = {
                    if (trailingIcon != null) trailingIcon()
                    else if (isLoading) CircularProgressIndicator(Modifier.width(iconWidth))
                    else IconButton(
                        onClick = {
                            scope.launch {
                                if (onSubmit()) {
                                    editState = false
                                }
                            }
                        },
                        modifier = Modifier
                            .width(iconWidth)
                            .wrapContentHeight(align = Alignment.CenterVertically)
                            .then(submitButtonModifier),
                        enabled = !isError
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.confirm)
                        )
                    }
                },
                supportingText = supportingText,
                isError = isError,
                modifier = textFieldModifier,
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.width(iconWidth))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.wrapContentHeight(align = Alignment.CenterVertically)
            )
            if (isLoading) CircularProgressIndicator(Modifier.width(iconWidth))
            else IconButton(
                onClick = {
                    editState = true
                },
                modifier = Modifier
                    .width(iconWidth)
                    .then(editButtonModifier),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit)
                )
            }
        }
    }
}


/**
 * Displays centered text.
 */
@Composable
fun CenteredText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current
) {
    Text(
        text,
        modifier = modifier.wrapContentHeight(align = Alignment.CenterVertically),
        color = color,
        style = style,
        maxLines = 1,
    )
}

/**
 * Default back button.
 */
@Composable
fun BaseViewModel.BackButton() {
    val coroutineScope = rememberCoroutineScope()
    BackButton(onClick = { coroutineScope.launch { goBack() } })
}

internal fun defaultValuePlaceHolderTransformation(
    placeholder: String,
    color: Color,
) = VisualTransformation { text ->
    if (text.isNotEmpty()) TransformedText(text, OffsetMapping.Identity)
    else {
        val emptyOffsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = 0
            override fun transformedToOriginal(offset: Int) = 0
        }
        buildAnnotatedString {
            withStyle(SpanStyle().copy(color = color)) {
                append(placeholder)
            }
        }.let { TransformedText(it, emptyOffsetMapping) }
    }
}


/**
 * Button with a confirmation prompt.
 */
@Composable
fun WithConfirmationPrompt(
    onConfirm: () -> Unit,
    confirmationButtonText: @Composable () -> Unit,
    cancelButtonText: @Composable () -> Unit = { Text(stringResource(R.string.cancel)) },
    promptMessage: @Composable () -> Unit,
    content: @Composable (askToConfirm: () -> Unit) -> Unit
) {
    var confirmationPromptOpen by rememberSaveable { mutableStateOf(false) }

    if (confirmationPromptOpen) ConfirmationDialog(
        onDismissRequest = { confirmationPromptOpen = false },
        onConfirm = onConfirm,
        confirmationButtonText = confirmationButtonText,
        cancelButtonText = cancelButtonText,
        promptMessage = promptMessage,
    )

    content { confirmationPromptOpen = true }
}

/**
 * The test tags of [ConfirmationDialog].
 *
 * @param testTag The test tag.
 */
enum class ConfirmationDialogTestTags(
    val testTag: String
) {
    /**
     * Test tag of the confirmation button.
     */
    ConfirmButton("ConfirmationDialogConfirmButton"),

    /**
     * Test tag of the cancel button.
     */
    CancelButton("ConfirmationDialogCancelButton")
}

/**
 * A simple confirmation dialog.
 */
@Composable
fun ConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    confirmationButtonText: @Composable () -> Unit,
    cancelButtonText: @Composable () -> Unit = { Text(stringResource(R.string.cancel)) },
    promptMessage: @Composable () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismissRequest,
    confirmButton = {
        Button(
            onClick = {
                onDismissRequest()
                onConfirm()
            },
            Modifier.testTag(ConfirmationDialogTestTags.ConfirmButton.testTag),
        ) {
            confirmationButtonText()
        }
    }, dismissButton = {
        Button(onClick = onDismissRequest) {
            cancelButtonText()
        }
    }, text = promptMessage
)

/**
 * Displays a user's display name.
 */
@Composable
fun DisplayNameText(
    displayName: String,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current
) {
    val displayColor =
        if (isCurrentUser)
            blueColor()
        else
            Color.Unspecified

    val adjustedDisplayName = if (isCurrentUser)
        "$displayName (${stringResource(R.string.you)})"
    else
        displayName


    Text(
        text = adjustedDisplayName,
        modifier = modifier.wrapContentHeight(align = Alignment.CenterVertically),
        overflow = TextOverflow.Ellipsis,
        color = displayColor,
        style = style,
        maxLines = 1
    )
}


/**
 * Card showing a user and some [content].
 */
@Composable
fun UserCard(
    modifier: Modifier = Modifier,
    displayName: String?,
    profilePicture: String?,
    isCurrentUser: Boolean,
    displayNameWidth: Dp,
    addWeightedSpacer: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    ElevatedCard(modifier = modifier) {
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
            DisplayNameText(
                displayName ?: "",
                isCurrentUser,
                modifier = Modifier.width(displayNameWidth)
            )
            Spacer(modifier = if (addWeightedSpacer) Modifier.weight(1f) else Modifier.width(5.dp))
            content()
        }
    }
}

/**
 * The navigation drawer.
 */
@Composable
fun SidebarNavigationDrawer(
    padding: PaddingValues,
    onMainMenuClick: () -> Unit,
    onSettleUpClick: () -> Unit,
    mainMenuButtonEnabled: Boolean = true,
    settleUpButtonEnabled: Boolean = true,
    content: @Composable (setDrawerState: (DrawerValue) -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val scope = rememberCoroutineScope()
    fun setDrawerState(value: DrawerValue) {
        scope.launch {
            if (value == DrawerValue.Closed) {
                drawerState.close()
            } else {
                drawerState.open()
            }
        }
    }
    ModalNavigationDrawer(
        modifier = Modifier.padding(padding),
        drawerContent = {
            NavigationRail {
                IconButton(onClick = { setDrawerState(DrawerValue.Closed) }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.navigation_menu)
                    )
                }

                NavBarIconButton(
                    onClick = onMainMenuClick,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            // The contentDescription is null since we already have a description in the
                            // text component below it
                            contentDescription = null
                        )
                    },
                    enabled = mainMenuButtonEnabled,
                    name = stringResource(R.string.groups)
                )
                NavBarIconButton(
                    onClick = onSettleUpClick,
                    icon = { Text("=", fontSize = 8.em) },
                    enabled = settleUpButtonEnabled,
                    name = stringResource(R.string.settle_up)
                )
            }
        },
        gesturesEnabled = true,
        drawerState = drawerState,
        content = { content(::setDrawerState) }
    )
}

@Composable
private fun NavBarIconButton(
    icon: @Composable () -> Unit,
    name: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            icon()
        }
        Text(modifier = Modifier.offset(y = (-10).dp), text = name, maxLines = 1)
    }
}

/**
 * Displays an error icon iff [isError].
 */
fun conditionalErrorIcon(isError: Boolean): @Composable (() -> Unit)? {
    return conditionalComposable(isError) {
        Icon(
            Icons.Filled.Error,
            stringResource(R.string.error_icon_desc),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Supporting text for a text field.
 */
@Composable
fun TextFieldSupportingText(show: Boolean, text: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = if (show) text else "",
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * A [Composable] with conditional rendering.
 */
fun conditionalComposable(
    show: Boolean,
    composable: @Composable () -> Unit
): @Composable (() -> Unit)? = if (show) composable else null


@Composable
fun AsyncPullToRefreshBox(
    modifier: Modifier = Modifier,
    onRefresh: suspend () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            coroutineScope.launch {
                onRefresh()
                isRefreshing = false
            }
        },
        state = pullToRefreshState,
        content = content
    )
}

data class DialogOption(val option: String, val action: () -> Unit)

@Composable
fun OptionListDialog(
    options: List<DialogOption>,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = AlertDialogDefaults.shape
        ) {
            Column {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        "Options",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                }
                LazyColumn {
                    items(options) { (option, action) ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            onClick = {
                                onDismissRequest()
                                action()
                            },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 20.dp)
                            ) {
                                Text(
                                    option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserOptionsDialog(
    settleNav: () -> Unit,
    kick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    OptionListDialog(
        onDismissRequest = onDismissRequest,
        options = listOf(
            DialogOption(stringResource(R.string.settle_up_option)) {
                settleNav()
            },
            DialogOption(stringResource(R.string.kick_user_option)) {
                kick()
            },
        )
    )
}

/**
 * A field displaying an invite link.
 *
 * @param inviteLink The link to display
 * @param allowRegenerate Allow regenerating the link
 * @param onRegenerate When the regenerate button is clicked
 */
@Composable
fun InviteLinkField(
    inviteLink: String,
    allowRegenerate: Boolean,
    onRegenerate: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = inviteLink,
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(stringResource(R.string.invite_link)) },
        trailingIcon = {
            Row {
                IconButton(
                    onClick = {
                        val clipBoardContentBuilder = AnnotatedString.Builder()
                        clipBoardContentBuilder.append(inviteLink)
                        clipboardManager.setText(clipBoardContentBuilder.toAnnotatedString())
                    },
                    modifier = Modifier
                        .wrapContentHeight(align = Alignment.CenterVertically)
                        .wrapContentWidth(align = Alignment.CenterHorizontally)
                        .width(35.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.content_copy_24px),
                        contentDescription = stringResource(R.string.copy_to_clipboard)
                    )
                }
                if (allowRegenerate) {
                    IconButton(
                        onClick = { onRegenerate() },
                        modifier = Modifier
                            .wrapContentHeight(align = Alignment.CenterVertically)
                            .wrapContentWidth(align = Alignment.CenterHorizontally)
                            .width(35.dp)
                            .testTag(GroupSettingsViewTestTags.RegenerateInviteLinkButton.testTag)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.regenerate)
                        )
                    }
                }
                Spacer(Modifier.width(5.dp))
            }
        },
        visualTransformation = defaultValuePlaceHolderTransformation(
            " ",
            Color.Transparent,
        ),
        supportingText = { Text("") }, // Padding (identical to name field)
    )
}
