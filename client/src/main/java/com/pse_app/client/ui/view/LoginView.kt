package com.pse_app.client.ui.view

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pse_app.client.R
import com.pse_app.client.ui.NavigationRoute
import com.pse_app.client.ui.theme.pseTopAppBarColors
import com.pse_app.client.ui.view_model.login.AbstractLoginViewModel
import com.pse_app.client.ui.view_model.login.LoginLoadingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenResponse

/**
 * View for login.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginView(viewModel: AbstractLoginViewModel) {
    val context = LocalContext.current
    val authService = remember { AuthorizationService(context) }
    val loading by viewModel.loadingState.collectAsState()

    DisposableEffect(authService) {
        onDispose {
            authService.dispose()
        }
    }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.onTokenResult(null, null)
        } else {
            val data = result.data!!
            AuthorizationException.fromIntent(data)
                ?.let { viewModel.onTokenResult(null, it) }

            AuthorizationResponse.fromIntent(data)
                ?.let {
                    authService.performTokenRequest(
                        it.createTokenExchangeRequest(),
                        viewModel::onTokenResult,
                    )
                }
        }
    }

    val authRequestState by viewModel.authRequest.collectAsState()
    LaunchedEffect(authRequestState) {
        authRequestState
            ?.let(authService::getAuthorizationRequestIntent)
            ?.let(authLauncher::launch)
    }

    BaseScaffold(errorFlow = viewModel.errors,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                // We don't use the navigation bar on this screen
                navigationIcon = { },
                actions = { },
                colors = pseTopAppBarColors()
            )
        }) { padding ->
        val loadingState = loading
        if (loadingState == null) Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(stringResource(R.string.login), style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.weight(1f))

            val textHeight = with(LocalDensity.current) {
                MaterialTheme.typography.bodyLarge.lineHeight.toDp()
            }

            val border = CardDefaults.outlinedCardBorder()
            Surface(
                onClick = viewModel::startAuthorization,
                shape = CircleShape,
                border = border,
                color = CardDefaults.outlinedCardColors().containerColor,
                contentColor = CardDefaults.outlinedCardColors().contentColor,
                modifier = Modifier.height((textHeight + border.width) * 2),
            ) {
                Row(
                    Modifier.padding(horizontal = textHeight / 2 + border.width),
                    horizontalArrangement = Arrangement.spacedBy(textHeight / 4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.idp_logo),
                        contentDescription = "",
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.app_sign_in),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily(Font(R.font.idp_font))
                    )
                }
            }
            Spacer(modifier = Modifier.weight(2f))
        } else {
            val (loadingMessage, errorMessage) = when (loadingState) {
                LoginLoadingState.REFRESH -> Pair("", "")
                LoginLoadingState.SERVER_SETTINGS -> Pair(
                    stringResource(R.string.fetching_server_settings),
                    stringResource(R.string.failed_to_fetch_server_settings),
                )
                LoginLoadingState.OIDC -> Pair(
                    stringResource(R.string.authenticating),
                    stringResource(R.string.authentication_failed),
                )
                LoginLoadingState.SERVER_LOGIN -> Pair(
                    stringResource(R.string.logging_in),
                    stringResource(R.string.login_failed),
                )
            }
            LoadingScreen(
                modifier = Modifier.padding(padding).fillMaxSize(),
                loadingText = loadingMessage,
                errorText = errorMessage,
                onRetry = { viewModel.startAuthorization() },
                showRetryOnError = viewModel,
            )
        }
    }
}

@Serializable
object Login: NavigationRoute


@Preview
@Composable
fun PreviewLoginView() {
    LoginView(object : AbstractLoginViewModel() {

        override val authRequest: StateFlow<AuthorizationRequest?> = MutableStateFlow(null)

        override fun startAuthorization() { /* Do nothing */ }

        override fun onTokenResult(resp: TokenResponse?, ex: AuthorizationException?) { /* Do nothing */ }
        override val loadingState = MutableStateFlow(null)
    })
}
