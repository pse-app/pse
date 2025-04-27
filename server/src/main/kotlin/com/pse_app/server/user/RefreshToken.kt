package com.pse_app.server.user

import com.pse_app.common.dto.UserId
import com.pse_app.server.data.AuthenticationResult
import com.pse_app.server.data.ErrorCategory
import com.pse_app.server.data.Result
import com.pse_app.server.data.UserInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * A deserialized refresh Token.
 *
 * Refresh Tokens are represented as Json Strings containing a user id and a secret.
 * The user id is not validated against the token and must be treated as untrusted.
 * This means that for the token to be validated, the corresponding user id must always be
 * stored with it.
 *
 * Instead, the user id serves as a hint to for which user this token belongs to
 * (when issued by the server), or as a claim for which user one is trying to authenticate as
 * (when received by the server) using the actual token; this is so that it can be sent as
 * a standalone Bearer Token, as well as to allow tokens to be namespaced by their corresponding
 * user, removing the (miniscule) chance of collisions of tokens belonging to different users.
 */
@Serializable
data class RefreshToken(val userId: UserId, val secret: String) {
    companion object {
        private val encoder = Base64.getUrlEncoder()
        private val decoder = Base64.getUrlDecoder()
        private const val ENTROPY = 256

        /**
         * Generates a new token for the specified user.
         */
        fun generate(userId: UserId, randomness: SecureRandom) = RefreshToken(
            userId,
            // Generates a random byte array with ENTROPY bits and encodes it to a string.
            // Since the encoding is injective, this gives us a string with ENTROPY bits of entropy.
            BigInteger(ENTROPY, randomness)
                .toByteArray()
                .let(encoder::encodeToString)
        )

        /**
         * Attempts to decode a [String] into a [RefreshToken].
         */
        fun decode(encoded: String) = try {
            Json.decodeFromString<RefreshToken>(decoder.decode(encoded).decodeToString())
                .let { Result.Success(it) }
        } catch (ex: IllegalArgumentException) {
            Result.Error(
                ErrorCategory.InvalidToken,
                "Invalid refresh token format"
            )
        }
    }


    /**
     * Converts the token to an AuthenticationResult to be sent by the server.
     */
    fun toAuthResult(info: UserInfo): AuthenticationResult {
        assert(info.id == userId)
        return AuthenticationResult(
            user = info,
            refreshToken = encode()
        )
    }

    /**
     * Encodes the token in a URL safe manner.
     */
    internal fun encode() =
        encoder.encodeToString(Json.encodeToString(this).encodeToByteArray())!!

    /**
     * Used to get a hash of the token for storage and later validation.
     * The token is accepted if and only if it matches a known token hash for the specified user.
     *
     * ### Security
     * Since we control the entropy of the token, we do not need to treat it like a password.
     * Instead, it is treated like a shared secret.
     *
     * Validating secrets based on their SHA-256 signature is standard in algorithms such as HMAC-256,
     * which is one of the two main algorithms used to sign Json Web Tokens.
     * This allows us to create a shared secret without having to store the original secret.
     * Whenever the token is used, it is sent to the server unhashed, to be validated against the
     * known hash.
     *
     * For similar reason, adding a random salt to the token is pointless, since it is already a random
     * string; adding a salt is equivalent to increasing the entropy of the token.
     */
    fun generateHash(): String = MessageDigest
        .getInstance("SHA-256")
        .digest(secret.encodeToByteArray())
        .let(encoder::encodeToString)
        .let { "digest$$$it"}
}
