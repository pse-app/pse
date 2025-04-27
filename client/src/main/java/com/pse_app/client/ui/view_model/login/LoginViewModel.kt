package com.pse_app.client.ui.view_model.login

import android.net.Uri
import android.util.Log
import com.pse_app.client.BuildConfig
import com.pse_app.client.LocalizedStrings
import com.pse_app.client.model.exceptions.ModelException
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.view.LoginView
import com.pse_app.client.ui.view_model.SimpleMessageException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * View Model for [LoginView].
 */
@OptIn(LoginViewModelVisibleForTesting::class)
class LoginViewModel(
    private val facade: ModelFacade,
) : AbstractLoginViewModel() {
    private val _authRequest = MutableStateFlow<AuthorizationRequest?>(null)
    override val authRequest = _authRequest.asStateFlow()

    private val _loadingState = MutableStateFlow<LoginLoadingState?>(LoginLoadingState.REFRESH)

    override fun onEntry() {
        launchViewModel {
            val autoRedirected = redirectIfLoggedIn()
            
            if (!autoRedirected) _loadingState.value = null
        }
    }

    override fun startAuthorization() = launchViewModel {
        _loadingState.value = LoginLoadingState.SERVER_SETTINGS

        val tryRedirectTask = async {
            redirectIfLoggedIn()
        }

        val regularLoginTask = async {
            val oidc = facade.getOIDCSettings()

            // Convert the callback into a coroutine call
            val config = suspendCancellableCoroutine { continuation ->
                AuthorizationServiceConfiguration.fetchFromUrl(oidc.discoveryUri) { res, ex ->
                    res?.let(continuation::resume)
                    ex?.let(continuation::resumeWithException)
                }
            }
            Pair(oidc, config)
        }

        if (tryRedirectTask.await()) {
            regularLoginTask.cancel()
            return@launchViewModel
        }
        val (oidc, config) = regularLoginTask.await()

        _loadingState.value = LoginLoadingState.OIDC

        _authRequest.value = AuthorizationRequest.Builder(
            config,
            oidc.clientId,
            ResponseTypeValues.CODE,
            Uri.parse("${BuildConfig.REDIRECT_SCHEME}:/oauth2redirect")
        ).apply {
            setScopes("openid", "profile")
            setClaims(JSONObject(
                """
                {
                    "id_token": {
                        "preferred_username": null,
                        "nickname": null,
                        "given_name": null,
                        "email": null
                    }
                }
                """.trimIndent()
            ))
        }.build()
    }

    override fun onTokenResult(
        resp: TokenResponse?,
        ex: AuthorizationException?,
    ) = launchViewModel {
        ex?.let { throw it }
        if (resp == null) {
            throw SimpleMessageException(LocalizedStrings.authorization_cancelled)
        }
        getIdTokenFromTokenResponse(resp)?.let {
            _loadingState.value = LoginLoadingState.SERVER_LOGIN
            facade.login(it)

            if (!redirectIfLoggedIn()) {
                Log.wtf(
                    LoginViewModel::class.simpleName,
                    "Login was successful but user is not logged in?!"
                )
                throw SimpleMessageException(LocalizedStrings.internal_client_error)
            }
        } ?: throw SimpleMessageException(LocalizedStrings.missing_id_token)
    }

    /**
     * Testing API necessary for mocking because [TokenResponse] uses a public java attribute
     * to expose its properties.
     */
    @LoginViewModelVisibleForTesting
    internal fun getIdTokenFromTokenResponse(tokenResponse: TokenResponse) = tokenResponse.idToken

    override val loadingState = _loadingState.asStateFlow()

    private suspend fun redirectIfLoggedIn(): Boolean = try {
        facade.isAuthenticated()
    } catch (e: ModelException) {
        handleException(e)
        false
    }.also {
        if (it) navigate(NavigationEvent.DefaultBack)
    }

    override fun goBack() = navigate(NavigationEvent.CloseApp)

    override fun raiseLogin() { /* Do nothing */ }
}

/**
 * Opt-In marker to expose internal logic of [LoginViewModel] such that testing doesn't
 * have to mock as much of AppAuth-Android
 */
@RequiresOptIn
@Retention(AnnotationRetention.BINARY)
internal annotation class LoginViewModelVisibleForTesting
