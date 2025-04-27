package com.pse_app.server.handler

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Verification
import com.pse_app.common.dto.*
import com.pse_app.server.data.*
import com.pse_app.server.data.Result.Error
import com.pse_app.server.data.Result.Success
import com.pse_app.server.data.config.Config
import com.pse_app.server.data.config.ConfigKey
import com.pse_app.server.data.config.ListenTarget
import com.pse_app.server.executor.RequestExecutor
import com.pse_app.server.group.Authorizer
import com.pse_app.server.handler.RequestRouter.HttpMethod.*
import com.pse_app.server.user.Authenticator
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.slf4j.LoggerFactory
import java.net.URISyntaxException
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import io.ktor.server.routing.delete as ktorDelete
import io.ktor.server.routing.get as ktorGet
import io.ktor.server.routing.patch as ktorPatch
import io.ktor.server.routing.post as ktorPost
import io.ktor.server.routing.put as ktorPut

private typealias URI = @Serializable(with = URISerializer::class) java.net.URI
private typealias Instant = @Serializable(with = InstantSerializer::class) java.time.Instant

/**
 * The Request Router implements [RequestHandler] and handles HTTP requests.
 */
class RequestRouter(
    config: Config,
    requestExecutor: RequestExecutor,
    authenticator: Authenticator,
    authorizer: Authorizer
) : RequestHandler(config, requestExecutor, authenticator, authorizer) {
    private val tokens = TokenAuthenticator(authenticator, config)

    private val server: Server = run {
        val module: Application.() -> Unit = {
            auth()
            routes()
        }
        when (val listen = config[ConfigKey.Listen]) {
            is ListenTarget.Port -> JettyEmbeddedServer.createJettyServer(module) { server -> 
                val connector = ServerConnector(server)
                connector.port = listen.port
                connector
            }
            is ListenTarget.Socket -> JettyEmbeddedServer.createJettyServer(module) { server ->
                val connector = UnixListenServerConnector(server)
                connector.sockeSpec = listen
                connector
            }
        }
    }

    override fun run() {
        tokens.startIdTokenAuth()
        server.start()
    }

    override fun stop() {
        server.stop()
        tokens.stopIdTokenAuth()
    }

    override fun join() {
        server.join()
    }

    private fun Application.auth() {
        // According to our specification, the server responds 401 Unauthorized whenever the auth
        // header is invalid. Ktor has another reasonable default of using 400 Bad Request.
        // This code translates between the two, assuming that the authHeader is overridden.

        // We need a custom exception class for this because authHeader is a parser function that
        // is not meant to perform the response by itself and is thus not suspend.
        class InvalidAuthHeaderException(cause: BadRequestException) : Exception(
            cause.message,
            cause,
        )
        install(StatusPages) {
            // The custom exception class needs a handler so that we dont respond with
            // Internal Server Error.
            exception<InvalidAuthHeaderException> { call, ex ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ex.message ?: "Invalid Authentication Header"
                )
            }
            exception<BadRequestException> { call, _ ->
                call.respond(HttpStatusCode.BadRequest, "Bad Request")
            }
        }
        // This is our replacement for the authHeader callback. It uses the documented default
        // handler and translates 400 to 401, as well as adding a message.
        fun parseAuthHeader(call: ApplicationCall): HttpAuthHeader? = try {
            call.request.parseAuthorizationHeader()
        } catch (ex: BadRequestException) {
            throw InvalidAuthHeaderException(ex)
        }
        // NOTE: The authentication handlers are implemented to perform the authentication
        // during the validation, since that is how the Authenticator interface is designed.
        install(Authentication) {
            jwt("login-auth-oidc") {
                authHeader(::parseAuthHeader)

                // This uses the OIDC provider's jwk endpoint to lookup the public key for ID Tokens
                // The public key is only cached for a limited time, such that revoked keys stop
                // being accepted.
                verifier(tokens.oidcJwkProvider, tokens.configureVerification)

                // Does transformation from JWTCredential to Result<AuthenticationResult>, as
                // well as creating the new session in the database.
                // There are only few scenarios (internal errors) where this can return failure,
                // since the id token is already verified once this gets called.
                validate(tokens.idTokenValidator)

                // Explicitly ensures that unhandled validation errors cause a 401.
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Unauthorized, "Token malformed, missing or expired")
                }
            }
            jwt("local-access-token") {
                authHeader(::parseAuthHeader)

                // Verifies the JWT using the access token secret
                verifier(tokens.accessTokenVerifier)

                // Does validation such as the user actually existing, as well
                validate(tokens.accessTokenValidator)

                // Explicitly ensures that unhandled validation errors cause a 401.
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Unauthorized, "Token malformed, missing or expired")
                }
            }
            bearer("local-refresh-token") {
                authHeader(::parseAuthHeader)
                
                // Since this is an opaque token whose format is entirely decided on by the
                // Authenticator, it just gets passed there directly
                authenticate { tokenCred ->
                    authenticator.refreshSession(tokenCred.token)
                }
            }
        }

        routing {
            // since the authentication is already performed during the validation, we
            // only need to transform the resulting principal into a server response
            fun RoutingContext.convertAuthResult() =
                call.principal<Result<AuthenticationResult>>()
                    .errIfNull(ErrorCategory.InvalidToken, "Unknown Authentication Failure")
                    .flatten()
                    .map {
                        LoginResponsePayload(
                            accessToken = tokens.createAccessToken(it.user.id),
                            refreshToken = it.refreshToken
                        )
                    }
                    .map(::ok)

            authenticate("login-auth-oidc") {
                route<LoginResponsePayload>(Post, "/v1/login") {
                    convertAuthResult()
                }
            }

            authenticate("local-refresh-token") {
                route<LoginResponsePayload>(Post, "/v1/refresh") {
                    convertAuthResult()
                }
            }
        }
    }
    
    fun Application.routes() {
        install(ContentNegotiation) {
            json()
        }

        routing {
            route<SettingsResponsePayload>(Get, "/v1/settings") {
                SettingsResponsePayload(
                    discoveryUri = config[ConfigKey.OidcDiscoveryURI],
                    clientId = config[ConfigKey.OidcClientId],
                    currency = config[ConfigKey.Currency],
                ).let(::Success)
                    .map(::ok)
            }

            routeWithAuth<GroupInfoResponsePayload>(Put, "/v1/groups") { user ->
                call.tryReceive<CreateGroupRequestPayload>()
                    .andThen { payload -> requestExecutor.createGroup(user, payload.displayName) }
                    .map(::groupMembershipInfoToGroupInfoResponsePayload)
                    .map { Pair(HttpStatusCode.Created, it) }
            }

            routeWithAuth<Map<GroupId, GroupInfoResponsePayload>>(Get, "/v1/groups") { user ->
                call.tryReceive<Set<GroupId>>()
                    .void { authorizer.allGroupsKnown(user, it) }
                    .andThen(requestExecutor::retrieveGroupMembershipInfo)
                    .map { it.mapValues { (_, value) -> groupMembershipInfoToGroupInfoResponsePayload(value) } }
                    .map(::ok)
            }

            routeWithAuth<GroupInfoResponsePayload>(Patch, "/v1/groups/{groupId}/displayName") { user ->
                Result.ap(
                    call.parsePathParameter("groupId", ::parseUuid)
                        .map(::GroupId)
                        .void { authorizer.groupKnown(user, it) },
                    call.tryReceive<ChangeDisplayNameRequestPayload>(),
                    ::Pair
                )
                    .andThen { (group, payload) ->
                        requestExecutor.updateGroupDisplayName(
                            group,
                            payload.displayName
                        )
                    }
                    .map(::groupMembershipInfoToGroupInfoResponsePayload)
                    .map(::ok)
            }

            routeWithAuth<RegenerateInviteTokenResponsePayload>(Post, "/v1/groups/{groupId}/regenerateInviteLink") { user ->
                call.parsePathParameter("groupId", ::parseUuid)
                    .map(::GroupId)
                    .void { authorizer.groupKnown(user, it) }
                    .andThen(requestExecutor::regenerateInviteToken)
                    .map(::inviteTokenToUri)
                    .map(::RegenerateInviteTokenResponsePayload)
                    .map(::ok)
            }

            routeWithAuth<Unit>(Post, "/v1/groups/{groupId}/kick/{userId}") { user ->
                Result.ap(
                    call.parsePathParameter("groupId", ::parseUuid)
                        .map(::GroupId)
                        .void { authorizer.groupKnown(user, it) },
                    call.parsePathParameter("userId")
                        .map(::UserId),
                    ::Pair
                )
                    .andThen { (group, targetUser) -> requestExecutor.kickUser(group, targetUser) }
                    .map(::ok)
            }

            routeWithAuth<Unit>(Put, "/v1/transactions") { user ->
                call.tryReceive<Map<GroupId, TransactionRequestPayload>>()
                    .void { authorizer.allGroupsKnown(user, it.keys) }
                    .map {
                        it.map { (group, transaction) ->
                            when (transaction) {
                                is ExpenseRequestPayload -> Expense(
                                    group = group,
                                    name = transaction.name,
                                    comment = transaction.comment,
                                    timestamp = Instant.now(),
                                    balanceChanges = transaction.balanceChanges,
                                    originatingUser = user,
                                    expenseAmount = transaction.expenseAmount
                                )

                                is PaymentRequestPayload -> Payment(
                                    group = group,
                                    name = transaction.name,
                                    comment = transaction.comment,
                                    timestamp = Instant.now(),
                                    balanceChanges = transaction.balanceChanges,
                                    originatingUser = user,
                                )
                            }
                        }
                    }
                    .andThen(requestExecutor::postTransactions)
                    .map { Pair(HttpStatusCode.NoContent, Unit) }
            }

            routeWithAuth<Map<GroupId, List<TransactionResponsePayload>>>(Get, "/v1/transactions") { user ->
                call.tryReceive<Set<GroupId>>()
                    .void { authorizer.allGroupsKnown(user, it) }
                    .andThen(requestExecutor::getTransactions)
                    .map {
                        it.mapValues { (group, transactions) ->
                            transactions.map { transaction ->
                                when (transaction) {
                                    is Expense -> ExpenseResponsePayload(
                                        group = group,
                                        originatingUser = transaction.originatingUser,
                                        name = transaction.name,
                                        comment = transaction.comment,
                                        timestamp = transaction.timestamp,
                                        balanceChanges = transaction.balanceChanges,
                                        expenseAmount = transaction.expenseAmount
                                    )

                                    is Payment -> PaymentResponsePayload(
                                        group = group,
                                        originatingUser = transaction.originatingUser,
                                        name = transaction.name,
                                        comment = transaction.comment,
                                        timestamp = transaction.timestamp,
                                        balanceChanges = transaction.balanceChanges,
                                    )
                                }
                            }
                        }
                    }.map(::ok)
            }

            routeWithAuth<BalancesResponsePayload>(Get, "/v1/balances") { user ->
                call.tryReceive<BalancesRequestPayload>()
                    .andThen { (users, groups) ->
                        Result.ap(
                            users
                                ?.let(::Success)
                                ?.void { authorizer.allUsersKnown(user, it) }
                                ?: requestExecutor.listKnownUsers(user),
                            groups
                                ?.let(::Success)
                                ?.void { authorizer.allGroupsKnown(user, it) }
                                ?: requestExecutor.listKnownGroups(user),
                            ::Pair
                        )
                    }
                    .andThen { (users, groups) -> requestExecutor.calculateBalances(users, groups) }
                    .map(::BalancesResponsePayload)
                    .map(::ok)
            }

            routeWithAuth<UserInfoResponsePayload>(Get, "/v1/me") { user ->
                requestExecutor
                    .retrieveUserMembershipInfo(user, user)
                    .map(::userMembershipInfoToUserInfoResponsePayload)
                    .map(::ok)
            }

            routeWithAuth<Unit>(Delete, "/v1/me") { user ->
                requestExecutor
                    .deactivateUser(user)
                    .map { Pair(HttpStatusCode.NoContent, Unit) }
            }

            routeWithAuth<UserInfoResponsePayload>(Patch, "/v1/me/displayName") { user ->
                call.tryReceive<ChangeDisplayNameRequestPayload>()
                    .andThen { requestExecutor.updateUserDisplayName(user, it.displayName) }
                    .map(::userMembershipInfoToUserInfoResponsePayload)
                    .map(::ok)
            }

            routeWithAuth<List<UserInfoResponsePayload>>(Get, "/v1/knownUsers") { user ->
                requestExecutor
                    .listKnownUsers(user)
                    .andThen { requestExecutor.retrieveUserMembershipInfo(user, it) }
                    .map {
                        it.map { (_, userMembershipInfo) ->
                            userMembershipInfoToUserInfoResponsePayload(userMembershipInfo)
                        }
                    }
                    .map(::ok)
            }

            routeWithAuth<Map<UserId, UserInfoResponsePayload>>(Get, "/v1/users") { user ->
                call.tryReceive<Set<UserId>>()
                    .void { authorizer.allUsersKnown(user, it) }
                    .andThen { requestExecutor.retrieveUserMembershipInfo(user, it) }
                    .map {
                        it.mapValues { (_, userMembershipInfo) ->
                            userMembershipInfoToUserInfoResponsePayload(userMembershipInfo)
                        }
                    }
                    .map(::ok)
            }

            routeWithAuth<GroupInfoResponsePayload>(Post, "/v1/join/{inviteToken}") { user ->
                call.parsePathParameter("inviteToken")
                    .andThen { requestExecutor.joinGroup(user, it) }
                    .map(::groupMembershipInfoToGroupInfoResponsePayload)
                    .map(::ok)
            }

            routeWithAuth<GroupInviteInfoResponsePayload>(Get, "/v1/join/{inviteToken}/info") { user ->
                call.parsePathParameter("inviteToken")
                    .andThen(requestExecutor::getInviteInfo)
                    .map {
                        GroupInviteInfoResponsePayload(
                            displayName = it.info.displayName,
                            alreadyMember = it.members.contains(user),
                            id = it.info.id
                        )
                    }
                    .map(::ok)
            }

            val publicRedirect: suspend RoutingContext.() -> Unit = {
                val redirect = config[ConfigKey.PublicRedirect]
                if (redirect.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound, Unit)
                } else if (redirect.contains("://")) {
                    call.respondRedirect(Url(redirect), false)
                } else {
                    call.respondRedirect(Url(resolveWithPublicUri(redirect)), false)
                }
            }
            
            ktorGet("/", publicRedirect)
            ktorGet("/v1/join/{inviteToken}", publicRedirect)
        }
    }

    private fun groupMembershipInfoToGroupInfoResponsePayload(groupMembershipInfo: GroupMembershipInfo): GroupInfoResponsePayload =
        GroupInfoResponsePayload(
            id = groupMembershipInfo.info.id,
            displayName = groupMembershipInfo.info.displayName,
            inviteUri = inviteTokenToUri(groupMembershipInfo.info.inviteToken),
            mostRecentTransaction = groupMembershipInfo.info.mostRecentTransaction,
            members = groupMembershipInfo.members
        )

    private fun userMembershipInfoToUserInfoResponsePayload(userMembershipInfo: UserMembershipInfo): UserInfoResponsePayload =
        UserInfoResponsePayload(
            id = userMembershipInfo.info.id,
            displayName = userMembershipInfo.info.displayName,
            profilePicture = userMembershipInfo.info.profilePicture,
            groups = userMembershipInfo.groups
        )

    private fun inviteTokenToUri(inviteToken: String): URI =
        resolveWithPublicUri("/v1/join/$inviteToken")

    internal fun resolveWithPublicUri(path: String): URI =
        URLBuilder(config[ConfigKey.PublicURI].toString()).apply {
            appendPathSegments(path)
        }.build().toURI()

    private suspend inline fun <reified T : Any> RoutingCall.respondResult(
        result: Result<Pair<HttpStatusCode, T>>,
    ) = when (result) {
        is Success -> {
            val (status, response) = result.value
            if (T::class == Unit::class)
                this.response.status(status)
            else
                respond(status, response)
        }

        is Error -> respondText(
            status = when (result.category) {
                ErrorCategory.Generic -> HttpStatusCode.InternalServerError
                ErrorCategory.InvalidToken -> HttpStatusCode.Unauthorized
                ErrorCategory.InvalidInvite, ErrorCategory.RejectedInput -> HttpStatusCode.BadRequest
                ErrorCategory.UserNotFound, ErrorCategory.GroupNotFound -> HttpStatusCode.NotFound
                ErrorCategory.PreconditionFailed -> HttpStatusCode.PreconditionFailed
            },
            text = result.message,
            contentType = ContentType.Text.Plain
        )
    }

    private fun <T> ok(value: T) = Pair(HttpStatusCode.OK, value)

    private suspend inline fun <reified T> RoutingCall.tryReceive(): Result<T> =
        Result.tryTo<T, CannotTransformContentToTypeException> { receive() }
            .mapErrorCategory { _, _ -> ErrorCategory.RejectedInput }

    private fun parseUuid(uuid: String): Result<UUID> = try {
        Success(UUID.fromString(uuid))
    } catch (e: IllegalArgumentException) {
        Error(ErrorCategory.RejectedInput, "Invalid UUID")
    }

    private inline fun <reified T> RoutingCall.parsePathParameter(
        pathParameter: String,
        crossinline parser: (String) -> Result<T>,
    ): Result<T> = pathParameters[pathParameter]
        .errIfNull(ErrorCategory.RejectedInput, "Missing path parameter '$pathParameter'")
        .andThen { parser(it) }

    private fun RoutingCall.parsePathParameter(
        pathParameter: String,
    ): Result<String> = parsePathParameter(pathParameter, ::Success)


    private inline fun <reified R : Any> Route.route(
        method: HttpMethod,
        path: String,
        crossinline handler: suspend RoutingContext.() -> Result<Pair<HttpStatusCode, R>>,
    ): Route = ktorRoute(method, path) {
        call.respondResult(logOnError(method, path) { handler() })
    }

    private inline fun <reified R : Any> Route.routeWithAuth(
        method: HttpMethod,
        path: String,
        crossinline handler: suspend RoutingContext.(UserId) -> Result<Pair<HttpStatusCode, R>>,
    ) = authenticate("local-access-token") {
        ktorRoute(method, path) {
            call.respondResult(
                call.principal<Result<UserId>>()
                    .errIfNull(
                        ErrorCategory.InvalidToken,
                        "Unknown Authentication Failure",
                    )
                    .flatten()
                    .andThen { logOnError(method, path) { handler(it) } }
            )
        }
    }

    private suspend inline fun <reified R : Any> logOnError(
        method: HttpMethod, path: String,
        crossinline function: suspend () -> Result<Pair<HttpStatusCode, R>>
    ): Result<Pair<HttpStatusCode, R>> {
        try {
            return function()
        } catch (e: Exception) {
            val methodString = method.toString().uppercase(Locale.ROOT)
            logger.error("An exception was thrown while handling a request to {} {}", methodString, path, e)
            return Error(ErrorCategory.Generic, "Internal Server Error")
        }
    }

    private sealed interface HttpMethod {
        data object Get : HttpMethod
        data object Put : HttpMethod
        data object Patch : HttpMethod
        data object Post : HttpMethod
        data object Delete : HttpMethod
    }

    private fun Route.ktorRoute(
        method: HttpMethod,
        path: String,
        body: suspend RoutingContext.() -> Unit
    ): Route = when (method) {
        is Get -> ktorGet(path, body)
        is Put -> ktorPut(path, body)
        is Patch -> ktorPatch(path, body)
        is Post -> ktorPost(path, body)
        is Delete -> ktorDelete(path, body)
    }
    
    companion object {
        private val logger: Logger = LogManager.getLogger()
    }
}

/**
 * The Token Authenticator handles the authentication with tokens.
 */
class TokenAuthenticator(
    private val authenticator: Authenticator,
    private val config: Config,
    client: HttpClientEngine? = null,
    private val schedule: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
) {
    private val client = client?.let(::HttpClient) ?: HttpClient(CIO)

    private val logger = LoggerFactory.getLogger(this::class.java)
    private lateinit var oidcConfig: OidcConfigData
    private lateinit var currentOidcJwkProvider: JwkProvider

    private val accessTokenSecret =
        KeyGenerator
            .getInstance("HmacSHA256")
            .generateKey()
            .toString()
    val accessTokenIssuer = "com.pse_app.server"
    val accessTokenExpiryTime = Duration.ofHours(1)!!

    // Username candidates
    // https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims
    private val nameClaimOrder = listOf(
        "preferred_username",
        "nickname",
        "given_name",
        "email",
        "sub", // User ID as Fallback; always present for accepted tokens
    )

    private val jsonIgnoreUnknown = Json { ignoreUnknownKeys = true }

    private suspend fun updateOidcConfig() {
        val discoConfig: OidcConfigData =
            config[ConfigKey.OidcDiscoveryURI]
                .toURL()
                .let { client.get(it) }
                .body<String>()
                .let(jsonIgnoreUnknown::decodeFromString)

        val refreshJwks = !::oidcConfig.isInitialized || oidcConfig.jwksUri != discoConfig.jwksUri
        oidcConfig = discoConfig
        if (refreshJwks) updateJwkProvider()
    }

    private fun updateJwkProvider() {
            currentOidcJwkProvider = JwkProviderBuilder(oidcConfig.jwksUri.toURL())
                .cached(OIDC_JWK_CACHE_SIZE, 1, TimeUnit.DAYS)
                .rateLimited(OIDC_JWK_BUCKET_SIZE_PER_MINUTE, 1, TimeUnit.MINUTES)
                .build()
    }

    val accessTokenVerifier =
        JWT.require(Algorithm.HMAC256(accessTokenSecret))
            .withAudience(accessTokenIssuer)
            .withIssuer(accessTokenIssuer)
            .acceptLeeway(Duration.ofMinutes(MAXIMUM_LEEWAY_MINUTES).toSeconds())
            .build()!!

    internal fun createAccessToken(id: UserId) = createUnsignedAccessToken(id).let(::sign)

    internal fun createUnsignedAccessToken(id: UserId) =
        JWT.create()
            .withAudience(accessTokenIssuer)
            .withIssuer(accessTokenIssuer)
            .withSubject(id.id)
            .withExpiresAt(Instant.now().plus(accessTokenExpiryTime))

    internal fun sign(builder: JWTCreator.Builder) =
        builder.sign(Algorithm.HMAC256(accessTokenSecret))!!

    private fun validateIdToken(credential: JWTCredential): Result<AuthenticationResult> {
        return credential.subject
            .errIfNull(ErrorCategory.InvalidToken, "Missing 'sub' claim from ID Token")
            .map(::UserId)
            .andThen { user ->
                authenticator.createSession(
                    user,
                    nameClaimOrder.firstNotNullOf(credential::get),
                    credential["picture"]?.let {
                        Result.tryTo<_, URISyntaxException> { URI(it) }
                            .let { it as? Success }
                            ?.value
                    },
                )
            }
    }

    fun validateAccessToken(credential: JWTCredential): Result<UserId> =
        credential.subject
            .errIfNull(ErrorCategory.InvalidToken, "Missing 'sub' claim from Access Token")
            .map(::UserId)
            .void(authenticator::authenticate)

    val idTokenValidator: Validator<AuthenticationResult> = { validateIdToken(it) }
    val accessTokenValidator: Validator<UserId> = { validateAccessToken(it) }

    fun startIdTokenAuth() {
        runBlocking { updateOidcConfig() }
        schedule.scheduleAtFixedRate({
            try {
                runBlocking { updateOidcConfig() }
            } catch (e: Exception) {
                logger.error("Failed to fetch OIDC configuration", e)
            }
        }, 1, 1, TimeUnit.DAYS)
    }

    fun stopIdTokenAuth() {
        schedule.shutdown()
        client.close()
    }

    val configureVerification: Verification.() -> Unit = {
        acceptLeeway(Duration.ofMinutes(MAXIMUM_LEEWAY_MINUTES).toSeconds())
        withIssuer(oidcConfig.issuer)
        withAudience(config[ConfigKey.OidcClientId])
    }

    // NOTE: This needs to be a lambda so that the oidc jwk gets updated
    val oidcJwkProvider = JwkProvider { currentOidcJwkProvider[it] }
    
    companion object {
        private const val OIDC_JWK_CACHE_SIZE: Long = 10
        private const val OIDC_JWK_BUCKET_SIZE_PER_MINUTE: Long = 10
        private const val MAXIMUM_LEEWAY_MINUTES: Long = 5
    }
}

private typealias Validator<T> = ApplicationCall.(JWTCredential) -> Result<T>

/**
 * Represents OIDC configuration data.
 */
@Serializable
data class OidcConfigData(
    @SerialName("jwks_uri") val jwksUri: URI,
    val issuer: String,
)
