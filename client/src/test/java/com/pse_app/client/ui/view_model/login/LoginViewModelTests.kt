@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model.login

import android.net.Uri
import android.text.TextUtils
import android.util.Base64
import com.pse_app.client.model.exceptions.InternalException
import com.pse_app.client.model.facade.OIDCSettings
import com.pse_app.client.ui.view_model.ViewModelTests
import com.pse_app.client.ui.view_model.runViewModelTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationServiceDiscovery
import net.openid.appauth.TokenResponse
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
@OptIn(ExperimentalCoroutinesApi::class, LoginViewModelVisibleForTesting::class)
class LoginViewModelTests: ViewModelTests<LoginViewModel>() {
    override fun makeViewModel() = LoginViewModel(facade)

    @Before
    fun initialize() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(any()) } answers {
            firstArg<CharSequence?>().isNullOrEmpty()
        }
        every { TextUtils.join(any(), any<Iterable<*>>()) } answers {
            secondArg<Iterable<*>>().joinToString(separator = firstArg<String>())
        }

        mockkConstructor(AuthorizationRequest::class)

        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "a".repeat(100)
    }

    @Test
    fun `no redirect if not authenticated`() = runViewModelTest {
        coEvery { facade.isAuthenticated() } returns false
        viewModel.onEntry()

        advanceUntilIdle()
    }

    @Test
    fun `no redirect if auth check throws`() = runViewModelTest {
        val ex = InternalException(Exception())
        coEvery { facade.isAuthenticated() } throws ex

        viewModel.onEntry()

        advanceUntilIdle()
        assertThat(errors).containsExactly(ex)
        assertAndClearErrors()
    }

    @Test
    fun `automatic redirect if already logged in`() = runViewModelTest {
        coEvery { facade.isAuthenticated() } returns true

        viewModel.onEntry()

        advanceUntilIdle()

        assertAndClearSingleNavigation()
    }

    @Test
    fun `startAuthorization sets state`() = runViewModelTest {
        val settings = OIDCSettings(
            clientId = "test-id",
            discoveryUri = Uri.parse("https://accounts.google.com/.well-known/openid-configuration"),
        )
        coEvery { facade.isAuthenticated() } returns false
        coEvery { facade.getOIDCSettings() } returns settings

        // supply the prefetched config when it is requested
        mockkStatic(AuthorizationServiceConfiguration::class)
        every {
            AuthorizationServiceConfiguration.fetchFromUrl(settings.discoveryUri, any())
        } answers {
            OIDC_CONFIG
                .let(::JSONObject)
                .let(::AuthorizationServiceDiscovery)
                .let(::AuthorizationServiceConfiguration)
                .let {
                    secondArg<AuthorizationServiceConfiguration.RetrieveConfigurationCallback>()
                        .onFetchConfigurationCompleted(it, null)
                }
        }

        viewModel.startAuthorization()

        advanceUntilIdle()

        assertThat(viewModel.authRequest.value?.clientId).isEqualTo(settings.clientId)
    }

    @Test
    fun `token auth exceptions get thrown`() = runViewModelTest {
        val ex = mockk<AuthorizationException>(relaxed = true)

        viewModel.onTokenResult(null, ex)

        advanceUntilIdle()

        assertThat(errors).containsExactly(ex)
        assertAndClearErrors()
    }

    @Test
    fun `throws on auth cancelled`() = runViewModelTest {
        viewModel.onTokenResult(null, null)

        advanceUntilIdle()

        assertAndClearErrors()
    }

    @Test
    fun `tokens get used`() = runViewModelTest {
        val response = mockk<TokenResponse>()
        val idToken = "asdf"
        every { viewModel.getIdTokenFromTokenResponse(response) } returns idToken
        coEvery { facade.login(idToken) } just runs
        coEvery { facade.isAuthenticated() } returns true

        viewModel.onTokenResult(response, null)
        advanceUntilIdle()

        coVerify(exactly = 1) { facade.login(idToken) }
        assertAndClearSingleNavigation()
    }

    @Test
    fun `throws if id token is missing`() = runViewModelTest {
        viewModel.viewModelScope.launch {
            throw ArithmeticException()
        }
        val response = mockk<TokenResponse>()
        every { viewModel.getIdTokenFromTokenResponse(response) } returns null

        viewModel.onTokenResult(response, null)
        advanceUntilIdle()

        assertAndClearErrors()
    }

    @Test
    fun `navigationes navigarent`() = runViewModelTest {
        viewModel.goBack()
        advanceUntilIdle()
        assertAndClearSingleNavigation()

        viewModel.raiseLogin()
        advanceUntilIdle()
        assertNoNavigations()
    }
}

// Taken from https://accounts.google.com/.well-known/openid-configuration
private const val OIDC_CONFIG = """{
  "issuer": "https://accounts.google.com",
  "authorization_endpoint": "https://accounts.google.com/o/oauth2/v2/auth",
  "device_authorization_endpoint": "https://oauth2.googleapis.com/device/code",
  "token_endpoint": "https://oauth2.googleapis.com/token",
  "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo",
  "revocation_endpoint": "https://oauth2.googleapis.com/revoke",
  "jwks_uri": "https://www.googleapis.com/oauth2/v3/certs",
  "response_types_supported": ["code", "token", "id_token", "code token", "code id_token", "token id_token", "code token id_token", "none"],
  "subject_types_supported": ["public"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "scopes_supported": ["openid", "email", "profile"],
  "token_endpoint_auth_methods_supported": ["client_secret_post", "client_secret_basic"],
  "claims_supported": ["aud", "email", "email_verified", "exp", "family_name", "given_name", "iat", "iss", "name", "picture", "sub"],
  "code_challenge_methods_supported": ["plain", "S256"],
  "grant_types_supported": ["authorization_code", "refresh_token"]
}"""
