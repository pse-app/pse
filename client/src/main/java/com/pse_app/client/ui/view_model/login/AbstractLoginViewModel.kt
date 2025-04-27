package com.pse_app.client.ui.view_model.login

import com.pse_app.client.ui.view_model.BaseViewModel
import kotlinx.coroutines.flow.StateFlow
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.TokenResponse

/**
 * Abstract View Model for [LoginViewModel].
 */
abstract class AbstractLoginViewModel : BaseViewModel() {

    /**
     * [StateFlow] on the [AuthorizationRequest].
     */
    abstract val authRequest: StateFlow<AuthorizationRequest?>

    /**
     * Starts authorization.
     */
    abstract fun startAuthorization()

    /**
     * Handles the authorization result from the Identity Provider.
     * On success, it attempts a login with the server.
     */
    abstract fun onTokenResult(resp: TokenResponse?, ex: AuthorizationException?)

    /**
     * [StateFlow] on the loading status.
     */
    abstract val loadingState : StateFlow<LoginLoadingState?>
}

enum class LoginLoadingState {
    REFRESH,
    SERVER_SETTINGS,
    OIDC,
    SERVER_LOGIN;
}
