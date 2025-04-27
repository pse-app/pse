package com.pse_app.client.model.data_layer

import com.auth0.jwt.JWT
import com.pse_app.client.annotations.KoverIgnore
import com.pse_app.client.model.exceptions.LoginRejectedException
import com.pse_app.client.model.exceptions.NetworkException
import com.pse_app.client.model.exceptions.NotFoundException
import com.pse_app.client.model.exceptions.ServerErrorException
import com.pse_app.client.model.exceptions.SessionMissingException
import com.pse_app.client.model.exceptions.SessionRejectedException
import com.pse_app.client.model.exceptions.TimeoutException
import com.pse_app.client.model.exceptions.UnexpectedServerException
import com.pse_app.common.dto.BalancesRequestPayload
import com.pse_app.common.dto.BalancesResponsePayload
import com.pse_app.common.dto.ChangeDisplayNameRequestPayload
import com.pse_app.common.dto.CreateGroupRequestPayload
import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.GroupInfoResponsePayload
import com.pse_app.common.dto.GroupInviteInfoResponsePayload
import com.pse_app.common.dto.LoginResponsePayload
import com.pse_app.common.dto.RegenerateInviteTokenResponsePayload
import com.pse_app.common.dto.SettingsResponsePayload
import com.pse_app.common.dto.TransactionRequestPayload
import com.pse_app.common.dto.TransactionResponsePayload
import com.pse_app.common.dto.UserId
import com.pse_app.common.dto.UserInfoResponsePayload
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod.Companion.Delete
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpMethod.Companion.Patch
import io.ktor.http.HttpMethod.Companion.Post
import io.ktor.http.HttpMethod.Companion.Put
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import java.time.Instant

/** API version */
const val VERSION = "v1"
/** Connection keep alive */
const val KEEP_ALIVE = 2000L

/**
 * Constructs a [HttpClient] usable by [RemoteClient]
 */
@Suppress("MagicNumber") // Meaning of numbers clear from context
fun getHTTPClient(timeoutMillis: Long, debug: Boolean=false): HttpClient {
    return HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = timeoutMillis
            // Don't restrict connection more than request as a whole
            connectTimeoutMillis = timeoutMillis
        }
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = if (debug) { Logger.DEFAULT } else { Logger.SIMPLE }
            level = if (debug) {LogLevel.ALL} else { LogLevel.HEADERS }
            if (!debug) sanitizeHeader { it == HttpHeaders.Authorization }
        }
        engine {
            maxConnectionsCount = 4
            endpoint {
                maxConnectionsPerRoute = 4
                pipelineMaxSize = 20
                keepAliveTime = KEEP_ALIVE
                connectAttempts = 1
                connectTimeout = timeoutMillis/2
            }
        }
        followRedirects = true
    }
}

// How often to attempt refresh on receiving Unauthorized with a valid access token
private const val REFRESH_ATTEMPTS = 1

private const val ACCESS_FRESHNESS_SPACING = 10000L

/**
 * Implements [RemoteAPI] using a ktor [HttpClient].
 */
@KoverIgnore("Unit tests would be testing ktor library")
class RemoteClient(
    private val serverUrl: String,
    private val timeoutMillis: Long,
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient = getHTTPClient(timeoutMillis)
) : RemoteAPI {
    private val sessionCallbacks = mutableListOf<suspend () -> Unit>()
    // Ensure session state isn't changed concurrently
    private val sessionLock = Mutex()
    private val invalidatedAccessTokens: MutableSet<String> = mutableSetOf()

    // Use for generic requests.
    private suspend inline fun <reified T> doRequest(
        requestBody: T,
        path: String,
        block: HttpRequestBuilder.() -> Unit
    ): HttpResponse {
        val serverEndpoint = URLBuilder(serverUrl).apply {
            path(VERSION, path)
        }.build()

        return try {
            val response = httpClient.request(serverEndpoint) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
                block()
            }
            when (response.status) {
                HttpStatusCode.InternalServerError -> throw ServerErrorException(response.bodyAsText())
                HttpStatusCode.MethodNotAllowed -> throw UnexpectedServerException("Method not allowed")
                HttpStatusCode.BadRequest -> throw UnexpectedServerException("Bad request: ${response.bodyAsText()}")
                HttpStatusCode.NotFound -> throw NotFoundException(serverEndpoint.toString())
                else -> response
            }
        } catch (ex: ConnectTimeoutException) {
            throw TimeoutException(ex)
        } catch (ex: HttpRequestTimeoutException) {
            throw TimeoutException(ex)
        } catch (ex: SocketTimeoutException) {
            throw TimeoutException(ex)
        } catch (ex: IOException) {
            throw NetworkException(message = ex.message, cause = ex)
        }
    }

    // Use for endpoints that require access token.
    private suspend inline fun <reified T> doProtectedRequest(
        requestBody: T,
        path: String,
        block: HttpRequestBuilder.() -> Unit
    ): HttpResponse {
        repeat(REFRESH_ATTEMPTS+1) {
            val accessToken = getOrRefreshAccess()
            val response = doRequest(requestBody, path) {
                bearerAuth(accessToken)
                block()
            }
            if (response.status == HttpStatusCode.Unauthorized) {
                invalidatedAccessTokens.add(accessToken)
                return@repeat
            }
            return response
        }
        invalidateSession()
        throw SessionRejectedException()
    }

    private suspend inline fun <reified T> extractBody(response: HttpResponse): Response<T> {
        val status = response.status
        var body: T? = null
        try {
            body = response.body<T>()
        } catch (e: NoTransformationFoundException) {
            // Assume Success means existence of body
            if (status.isSuccess()) { throw e }
        } catch (e: io.ktor.serialization.JsonConvertException) {
            if (status.isSuccess()) { throw e }
        }
        return Response(status, body)
    }

    private suspend fun invalidateSession() {
        sessionStore.setSession(null)
        for (sessionCallback in sessionCallbacks) {
            sessionCallback()
        }
    }

    /**
     * Returns a fresh access token by either refreshing the session or returning
     * a fresh one from session store.
     * May refresh inactive session.
     * Requires either valid refresh token or valid access token to be in store.
     *
     * @throws SessionMissingException In case no session, fresh or old, exists
     * @throws LoginRejectedException In case session refresh fails
     */
    private suspend fun getOrRefreshAccess(): String = sessionLock.withLock {
        val session: Session = sessionStore.getSession()
            ?: throw SessionMissingException()
        if (isAccessFresh(session.accessToken) && !invalidatedAccessTokens.contains(session.accessToken)) {
            return session.accessToken
        }
        // Must refresh session
        val response = doRequest(Unit, "refresh") {
            method = Post
            bearerAuth(session.refreshToken)
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            // Invalidate session
            invalidateSession()
            throw SessionRejectedException()
        }
        val loginResponse = extractBody<LoginResponsePayload>(response).unpack()
        val newSession = Session(loginResponse.accessToken, loginResponse.refreshToken)
        sessionStore.setSession(newSession)
        return newSession.accessToken
    }

    override suspend fun createSession(idToken: String) {
        invalidateSession()
        val response = doRequest(Unit, "login") {
            method = Post
            bearerAuth(idToken)
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            throw LoginRejectedException()
        }
        val newSession = extractBody<LoginResponsePayload>(response).unpack()
        sessionStore.setSession(Session(newSession.accessToken, newSession.refreshToken))
    }
    
    @Suppress("SwallowedException")
    override suspend fun refreshSession(): Boolean {
        return try {
            getOrRefreshAccess()
            true
        } catch (e: SessionMissingException) {
            false
        }
    }

    private fun isAccessFresh(accessToken: String): Boolean {
        val expiry = JWT.decode(accessToken).expiresAtAsInstant
        return expiry.minusMillis(ACCESS_FRESHNESS_SPACING).isAfter(Instant.now())
    }

    override suspend fun logout() {
        invalidateSession()
    }

    override fun registerOnSessionDestroyed(callback: suspend () -> Unit) {
        sessionCallbacks.add(callback)
    }

    override suspend fun getSettings(): Response<SettingsResponsePayload> {
        val response = doRequest(Unit, "settings") {
            method = Get
        }
        return extractBody(response)
    }

    override suspend fun getGroups(groups: Set<GroupId>): Response<Map<GroupId, GroupInfoResponsePayload>> {
        val response = doProtectedRequest(groups, "groups") {
            method = Get
        }
        return extractBody(response)
    }

    override suspend fun createGroup(request: CreateGroupRequestPayload): Response<GroupInfoResponsePayload> {
        val response = doProtectedRequest(request, "groups") {
            method = Put
        }
        return extractBody(response)
    }

    override suspend fun setGroupDisplayName(
        groupId: String,
        request: ChangeDisplayNameRequestPayload
    ): Response<GroupInfoResponsePayload> {
        val response = doProtectedRequest(request, "groups/${groupId}/displayName") {
            method = Patch
        }
        return extractBody(response)
    }

    override suspend fun regenerateGroupInviteLink(groupId: String): Response<RegenerateInviteTokenResponsePayload> {
        val response = doProtectedRequest(Unit, "groups/${groupId}/regenerateInviteLink") {
            method = Post
        }
        return extractBody(response)
    }

    override suspend fun kickUser(
        groupId: String,
        userId: String
    ): Response<Unit> {
        val response = doProtectedRequest(Unit, "groups/${groupId}/kick/${userId}") {
            method = Post
        }
        return extractBody(response)
    }

    override suspend fun joinGroup(inviteToken: String): Response<GroupInfoResponsePayload> {
        val response = doProtectedRequest(Unit, "join/${inviteToken}") {
            method = Post
        }
        return extractBody(response)
    }

    override suspend fun getGroupInviteInfo(inviteToken: String): Response<GroupInviteInfoResponsePayload> {
        val response = doProtectedRequest(Unit, "join/${inviteToken}/info") {
            method = Get
        }
        return extractBody(response)
    }

    override suspend fun getTransactions(groups: Set<GroupId>): Response<Map<GroupId, List<TransactionResponsePayload>>> {
        val response = doProtectedRequest(groups, "transactions") {
            method = Get
        }
        return extractBody(response)
    }

    override suspend fun postTransactions(transactions: Map<GroupId, TransactionRequestPayload>): Response<Unit> {
        val response = doProtectedRequest(transactions, "transactions") {
            method = Put
        }
        return extractBody(response)
    }

    override suspend fun getBalances(request: BalancesRequestPayload): Response<BalancesResponsePayload> {
        val response = doProtectedRequest(request, "balances") {
            method = Get
        }
        return extractBody(response)
    }

    override suspend fun getActiveUser(): Response<UserInfoResponsePayload> {
        val response = doProtectedRequest(Unit, "me") {
            method = Get
        }
        return extractBody(response)
    }

    override suspend fun deactivateActiveUser(): Response<Unit> {
        val response = doProtectedRequest(Unit, "me") {
            method = Delete
        }
        return extractBody(response)
    }

    override suspend fun setUserDisplayName(request: ChangeDisplayNameRequestPayload): Response<UserInfoResponsePayload> {
        val response = doProtectedRequest(request, "me/displayName") {
            method = Patch
        }
        return extractBody(response)
    }

    override suspend fun getKnownUsers(): Response<List<UserInfoResponsePayload>> {
        val response = doProtectedRequest(Unit, "knownUsers") {
            method = Get
        }
        return extractBody(response)
    }

    override suspend fun getUsers(users: Set<UserId>): Response<Map<UserId, UserInfoResponsePayload>> {
        val response = doProtectedRequest(users, "users") {
            method = Get
        }
        return extractBody(response)
    }
}
