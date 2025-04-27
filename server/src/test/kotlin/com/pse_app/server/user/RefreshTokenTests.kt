package com.pse_app.server.user

import com.pse_app.common.dto.LoginResponsePayload
import com.pse_app.common.dto.UserId
import com.pse_app.server.assertions.assertSuccess
import com.pse_app.server.data.UserInfo
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.test.assertEquals

class RefreshTokenTests {
    private val randomness = SecureRandom(byteArrayOf(1))
    private val user = UserId("test-user")
    private val info = UserInfo(user, "name", null)
    private val token = RefreshToken.generate(user, randomness)

    @Test
    fun `hash always gives the same result`() {
        assertEquals(token.generateHash(), token.generateHash())
    }

    @Test
    fun `decode is left inverse of encode`() {
        val recoded = assertSuccess(RefreshToken.decode(token.encode()))
        assertEquals(token, recoded)
        assertEquals(token.generateHash(), recoded.generateHash())
    }

    @Test
    fun `authentication results uses encoded tokens`() {
        val auth = token.toAuthResult(info)
        assertEquals(token.encode(), auth.refreshToken)
    }

    @Test
    fun `refresh token can be sent as bearer token`() = testApplication {
        install(ContentNegotiation) {
            json()
        }
        install(Authentication) {
            bearer("refresh-tokens") {
                authenticate { credential ->
                    assertEquals(credential.token, token.encode())
                }
            }
        }
        routing {
            get("/") {
                call.respond(LoginResponsePayload(
                    accessToken = "",
                    refreshToken = token.encode()
                ))
            }
            authenticate("refresh-tokens") {
                post("/") {}
            }
        }
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }
        val resp = client.get("/")
        val login = resp.body<LoginResponsePayload>()
        client.post("/") {
            bearerAuth(login.refreshToken)
        }
    }
}
