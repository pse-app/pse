package com.pse_app.server.handler

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.pse_app.common.dto.UserId
import com.pse_app.server.assertions.assertSuccess
import com.pse_app.server.data.AuthenticationResult
import com.pse_app.server.data.Result
import com.pse_app.server.data.UserInfo
import com.pse_app.server.data.config.Config
import com.pse_app.server.data.config.ConfigKey
import com.pse_app.server.user.Authenticator
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.ktorm.database.use
import java.net.ServerSocket
import java.net.URI
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import javax.crypto.KeyGenerator
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
class TokenAuthenticatorTests {
    @MockK
    lateinit var authenticator: Authenticator
    @MockK
    lateinit var config: Config

    private lateinit var tokens: TokenAuthenticator

    @BeforeEach
    fun initTokenAuth() {
        tokens = TokenAuthenticator(
            authenticator = authenticator,
            config = config,
        )
    }

    @Test
    fun accessTokenJwtMakesSense() {
        val id = UserId("Peter")
        every { authenticator.authenticate(id) } returns Result.Success(Unit)

        val now = Instant.now()
        val token = tokens.createAccessToken(id)
        val decoded = tokens.accessTokenVerifier.verify(token)
        val credential = JWTCredential(decoded)

        assertEquals(decoded.algorithm, "HS256")
        assert(decoded.expiresAtAsInstant!! >= now.plus(tokens.accessTokenExpiryTime).minusSeconds(10))
        assertEquals(credential.issuer, tokens.accessTokenIssuer)
        assertEquals(credential.audience, listOf(tokens.accessTokenIssuer))
        assertEquals(credential.subject, id.id)
        assertSuccess(tokens.validateAccessToken(credential))
    }

    @Test
    fun accessTokenWorksOnKtorServer() = testApplication {
        install(Authentication) {
            jwt("access-tokens") {
                verifier(tokens.accessTokenVerifier)
                validate(tokens.accessTokenValidator)
            }
        }
        routing {
            authenticate("access-tokens") {
                get("/endpoint") {
                    call.principal<Result<UserId>>()
                        .let(::assertNotNull)
                        .let(::assertSuccess)
                }
            }
        }
        val id = UserId("Peter")
        every { authenticator.authenticate(id) } returns Result.Success(Unit)
        client.get("/endpoint") {
            bearerAuth(tokens.createAccessToken(id))
        }
    }

    @Test
    fun invalidAccessTokenSecret() {
        val secret = KeyGenerator
            .getInstance("HmacSHA256")
            .generateKey()
            .toString()

        val token = tokens
            .createUnsignedAccessToken(UserId("Johannes"))
            .sign(Algorithm.HMAC256(secret))

        assertThrows<JWTVerificationException> { tokens.accessTokenVerifier.verify(token) }
    }

    @Test
    fun expiredAccessToken() {
        val token = tokens
            .createUnsignedAccessToken(UserId("Carlos Magnussen"))
            .withExpiresAt(Instant.now().minus(Duration.ofMinutes(10)))
            .let(tokens::sign)

        assertThrows<JWTVerificationException> { tokens.accessTokenVerifier.verify(token) }
    }

    @Test
    fun invalidIssuer() {
        val token = tokens
            .createUnsignedAccessToken(UserId("Karl"))
            .withIssuer("_xx_Hacker_1337_xx_")
            .let(tokens::sign)

        assertThrows<JWTVerificationException> { tokens.accessTokenVerifier.verify(token) }
    }

    @Test
    fun invalidAudience() {
        val token = tokens
            .createUnsignedAccessToken(UserId("Karl"))
            .withClaim("aud", "_xx_Hacker_1337_xx_")
            .let(tokens::sign)

        assertThrows<JWTVerificationException> { tokens.accessTokenVerifier.verify(token) }
    }
}

@ExtendWith(MockKExtension::class)
class IdTokenTests {
    @MockK
    lateinit var authenticator: Authenticator
    @MockK
    lateinit var config: Config
    @MockK
    lateinit var schedule: ScheduledExecutorService

    private lateinit var scheduledRefresh: Runnable

    private lateinit var tokens: TokenAuthenticator

    @BeforeEach
    fun initTokenAuth() {
        every { config[ConfigKey.OidcDiscoveryURI] } returns testingOidc.discoveryURI
        every { config[ConfigKey.OidcClientId] } returns testingOidc.clientId
        tokens = TokenAuthenticator(
            authenticator = authenticator,
            config = config,
            schedule = schedule,
        )

        run {
            val userIdSlot = slot<UserId>()
            val userNameSlot = slot<String>()
            val userPictureSlot = slot<URI?>()
            every {
                authenticator.createSession(
                    capture(userIdSlot),
                    capture(userNameSlot),
                    captureNullable(userPictureSlot)
                )
            } answers {
                Result.Success(AuthenticationResult(
                    UserInfo(userIdSlot.captured, userNameSlot.captured, userPictureSlot.captured),
                    refreshToken = ""
                ))
            }
        }

        val refreshSlot = slot<Runnable>()
        every {
            schedule.scheduleAtFixedRate(
                capture(refreshSlot),
                any(),
                any(),
                any()
            )
        } returns mockk<ScheduledFuture<*>>()
        every { schedule.shutdown() } just runs
        tokens.startIdTokenAuth()
        scheduledRefresh = refreshSlot.captured
    }
    @AfterEach
    fun stopIdTokens() {
        tokens.stopIdTokenAuth()
    }

    private class IdTokenTestContext(private val client: HttpClient) {
        suspend fun checkToken(token: String, valid: Boolean) {
            val action: suspend () -> Unit = {
                client.get("/token-test") {
                    bearerAuth(token)
                }.body<String>()
            }
            if (valid) assertDoesNotThrow { action() }
            else {
                val ex = assertThrows<ClientRequestException> { action() }
                assertEquals(ex.response.status, HttpStatusCode.Unauthorized)
            }
        }
        suspend fun assertUnacceptedToken(token: String) = checkToken(token, false)
        suspend fun assertAcceptedToken(token: String) = checkToken(token, true)
    }

    private fun testIdTokens(
        block: suspend IdTokenTestContext.() -> Unit
    ) = testApplication {
        install(Authentication) {
            jwt {
                verifier(tokens.oidcJwkProvider, tokens.configureVerification)
                validate(tokens.idTokenValidator)
            }
        }
        routing {
            authenticate {
                get("token-test") {
                    when (val auth = call.principal<Result<AuthenticationResult>>()) {
                        null -> call.respond(HttpStatusCode.Unauthorized, "Unknown Authentication Failure")
                        is Result.Error -> call.respond(HttpStatusCode.Unauthorized, auth.message)
                        is Result.Success -> call.respond(HttpStatusCode.OK, auth.toString())
                    }
                }
            }
        }

        val client = createClient {
            expectSuccess = true
        }
        IdTokenTestContext(client).block()
    }

    @Test
    fun validToken() = testIdTokens {
        for ((id, name, picture) in testUsers) {
            testingOidc.createUnsignedIdToken(id, name, picture)
                .let(testingOidc::signIdToken)
                .let { this.assertAcceptedToken(it) }
            verify(exactly = 1) { authenticator.createSession(UserId(id), name ?: id, picture) }
        }
    }

    @Test
    fun expiredToken() = testIdTokens {
        for ((id, name, picture) in testUsers) {
            testingOidc.createUnsignedIdToken(id, name, picture)
                .withExpiresAt(Instant.now() - Duration.ofMinutes(10))
                .let { testingOidc.signIdToken(it) }
                .let { assertUnacceptedToken(it) }
            verify(exactly = 0) {
                authenticator.createSession(
                    any(), any(), any()
                )
            }
        }
    }

    @Test
    fun wrongIssuer() = testIdTokens {
        for ((id, name, picture) in testUsers) {
            testingOidc.createUnsignedIdToken(id, name, picture)
                .withIssuer("Hackerman")
                .let { testingOidc.signIdToken(it) }
                .let { assertUnacceptedToken(it) }
            verify(exactly = 0) {
                authenticator.createSession(
                    any(), any(), any()
                )
            }
        }
    }

    @Test
    fun wrongAudience() = testIdTokens {
        for ((id, name, picture) in testUsers) {
            testingOidc.createUnsignedIdToken(id, name, picture)
                .withClaim("aud", "")
                .let { testingOidc.signIdToken(it) }
                .let { assertUnacceptedToken(it) }
            verify(exactly = 0) {
                authenticator.createSession(
                    any(), any(), any()
                )
            }
        }
    }

    @Test
    fun wrongSignature() = testIdTokens {
        val key = KeyPairGenerator.getInstance("RSA")
            .apply {
                initialize(2048)
            }
            .genKeyPair()
            .private as RSAPrivateKey

        for ((id, name, picture) in testUsers) {
            testingOidc.createUnsignedIdToken(id, name, picture)
                .sign(Algorithm.RSA256(key))
                .let { assertUnacceptedToken(it) }
            verify(exactly = 0) {
                authenticator.createSession(
                    any(), any(), any()
                )
            }
        }
    }

    @Test
    fun noSignature() = testIdTokens {
        for ((id, name, picture) in testUsers) {
            testingOidc.createUnsignedIdToken(id, name, picture)
                .sign(Algorithm.none())
                .let { assertUnacceptedToken(it) }
            verify(exactly = 0) {
                authenticator.createSession(
                    any(), any(), any()
                )
            }
        }
    }

    @Test
    fun wrongSignatureAlgorithm() = testIdTokens {
        val secret = KeyGenerator.getInstance("HmacSHA256")
            .generateKey()
            .toString()

        for ((id, name, picture) in testUsers) {
            testingOidc.createUnsignedIdToken(id, name, picture)
                .sign(Algorithm.HMAC256(secret))
                .let { assertUnacceptedToken(it) }
            verify(exactly = 0) {
                authenticator.createSession(
                    any(), any(), any()
                )
            }
        }
    }

    companion object {
        private lateinit var testingOidc: FakeIdp

        private val testUsers = listOf(
            Triple("user1", "Peter", URI("asdf")),
            Triple("user2", "Gustav", null),
            Triple("user3", null, URI("test")),
            Triple("user4", null, null),
        )

        @JvmStatic
        @BeforeAll
        fun initAll() {
            val freePort = ServerSocket(0).use { it.localPort }
            testingOidc = FakeIdp(freePort)
            testingOidc.start()
        }

        @JvmStatic
        @AfterAll
        fun disposeAll() {
            testingOidc.stop()
        }
    }
}

class FakeIdp(val discoveryURI: URI, val clientId: String) {
    constructor(port: Int) : this(
        URI("http://localhost:$port/oidc-configuration"),
        "pse-testing-client",
    )
    private val baseUrl: Url
    private val discoveryEndpoint: String
    init {
        val url = Url(discoveryURI)

        var discoveryEndpoint = url.fullPath
        if (!discoveryEndpoint.startsWith("/")) discoveryEndpoint = "/$discoveryEndpoint"
        this.discoveryEndpoint = discoveryEndpoint

        baseUrl =
            Url("${url.protocol.name}://${url.hostWithPortIfSpecified}")

        assert(baseUrl.host == "localhost")

        assert(getEndpoint(discoveryEndpoint).toURI() == discoveryURI)
    }

    private val issuer = baseUrl.toString()

    private val publicKey: RSAPublicKey
    private val privateKey: RSAPrivateKey

    init {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.genKeyPair()
        publicKey = keyPair.public as RSAPublicKey
        privateKey = keyPair.private as RSAPrivateKey
    }

    private val server = embeddedServer(
        Netty,
        port = baseUrl.port
    ) {
        install(ContentNegotiation) {
            json()
        }
        val (modulus, exponent) = listOf(
            publicKey.modulus,
            publicKey.publicExponent
        ).map {
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(it.toByteArray())
        }

        install(Authentication)

        routing {
            get(discoveryEndpoint) {
                call.respond(
                    HttpStatusCode.OK,
                    OidcConfigData(
                        jwksUri = getEndpoint("certs").toURI(),
                        issuer = issuer,
                    ),
                )
            }
            get("/certs") {
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "keys" to listOf(
                            mapOf(
                                "kty" to "RSA",
                                "kid" to "test-key-id",
                                "use" to "sig",
                                "n" to modulus,
                                "e" to exponent
                            )
                        )
                    )
                )
            }
        }
    }

    fun createUnsignedIdToken(user: String, name: String?, picture: URI?) =
        JWT.create().apply {
            withAudience(clientId)
            withIssuer(issuer)
            withSubject(user)
            withExpiresAt(Instant.now() + Duration.ofHours(1))!!
            name?.let { withClaim("preferred_username", it) }
            picture?.let { withClaim("picture", it.toString()) }
        }!!

    fun signIdToken(token: JWTCreator.Builder) =
        token.sign(Algorithm.RSA256(privateKey))!!

    fun start() {
        server.start()
    }
    fun stop() {
        server.stop()
    }


    private fun getEndpoint(path: String): Url = URLBuilder(
        baseUrl
    ).apply {
        path(path)
    }.build()
}
