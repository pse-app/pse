package com.pse_app.client.ui.view

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pse_app.client.StringResource
import com.pse_app.client.getValue
import kotlinx.coroutines.flow.Flow

/**
 * The Scaffold used in every View.
 */
@Composable
fun BaseScaffold(
    floatingActionButton: @Composable (() -> Unit) = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    errorFlow: Flow<StringResource>,
    topBar: @Composable () -> Unit,
    contentWindowInsets: WindowInsets = WindowInsets.navigationBars,
    snackbarInsets: WindowInsets = WindowInsets(0),
    content: @Composable (PaddingValues) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                Modifier.windowInsetsPadding(snackbarInsets),
            )
        },
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        topBar = topBar,
        contentWindowInsets = contentWindowInsets,
    ) { padding ->
        val context = LocalContext.current
        Spacer(Modifier)
        LaunchedEffect(errorFlow, snackbarHostState) {
            errorFlow.collect { message ->
                snackbarHostState.showSnackbar(message.getValue(context), withDismissAction = true)
            }
        }
        content(padding)
    }
}
