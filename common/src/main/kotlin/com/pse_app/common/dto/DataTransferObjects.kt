package com.pse_app.common.dto

import com.pse_app.common.util.BigDec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

private typealias URI = @Serializable(with = URISerializer::class) java.net.URI
private typealias Instant = @Serializable(with = InstantSerializer::class) java.time.Instant
private typealias Currency = @Serializable(with = CurrencySerializer::class) java.util.Currency

/**
 * A [KSerializer] for [URI].
 */
object URISerializer : KSerializer<URI> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("URI", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): URI {
        return URI(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: URI) {
        encoder.encodeString(value.toString())
    }
}

/**
 * A [KSerializer] for [Instant].
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
}

/**
 * A [KSerializer] for [Currency].
 */
object CurrencySerializer : KSerializer<Currency> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Currency {
        return Currency.getInstance(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Currency) {
        encoder.encodeString(value.toString())
    }
}

/**
 * The DTO for a response containing the server settings.
 * @param clientId The OIDC client id.
 * @param discoveryUri The OIDC discovery [URI].
 * @param currency The currency as an ISO 4217 currency code
 */
@Serializable
data class SettingsResponsePayload(
    val clientId: String,
    val discoveryUri: URI,
    val currency: Currency,
)

/**
 * The DTO for a response containing information about a group.
 * @param id The id of the group.
 * @param displayName The display name of the group.
 * @param inviteUri The invite [URI] of the group.
 * @param mostRecentTransaction The timestamp of the most recent transaction in the group or {@code null} if there are none.
 * @param members The members of the group.
 */
@Serializable
data class GroupInfoResponsePayload (
    val id: GroupId, val displayName: String, val inviteUri: URI, val mostRecentTransaction: Instant?, val members: Set<UserId>
)

/**
 * The DTO for a response containing information about a user.
 * @param id The id of the user.
 * @param displayName The display name of the user.
 * @param profilePicture A [URI] to the profile picture of the user if available.
 * @param groups The groups the user is a member of.
 */
@Serializable
data class UserInfoResponsePayload(
    val id: UserId, val displayName: String, val profilePicture: URI?, val groups: Set<GroupId>
)

/**
 * The DTO for a response when retrieving information about a group using its invite token.
 * @param displayName The display name of the group associated with the invite token.
 * @param id The id of the group associated with the invite token.
 * @param alreadyMember Whether the user requesting the information is already a member of the group
 *                      associated with the invite token.
 */
@Serializable
data class GroupInviteInfoResponsePayload(
    val displayName: String,
    val id: GroupId,
    val alreadyMember: Boolean,
)

/**
 * The base DTO for a response containing information about a posted transaction.
 */
@Serializable
sealed class TransactionResponsePayload {
    /**
     * The group the transaction was posted to.
     */
    abstract val group: GroupId
    /**
     * The user the transaction originated from.
     */
    abstract val originatingUser: UserId
    /**
     * The name of the posted transaction.
     */
    abstract val name: String
    /**
     * The optional comment of the posted transaction.
     */
    abstract val comment: String?
    /**
     * The time of the server at which the posted transaction was made effective.
     */
    abstract val timestamp: Instant
    /**
     * The balance change of the posted transaction.
     */
    abstract val balanceChanges: Map<UserId, BigDec>
}

/**
 * The DTO for a response containing information about a posted payment.
 */
@Serializable
data class PaymentResponsePayload(
    override val group: GroupId,
    override val originatingUser: UserId,
    override val name: String,
    override val comment: String?,
    override val timestamp: Instant,
    override val balanceChanges: Map<UserId, BigDec>
) : TransactionResponsePayload()

/**
 * The DTO for a response containing information about a posted expense.
 * @param expenseAmount The total amount of the posted expense.
 */
@Serializable
data class ExpenseResponsePayload(
    override val group: GroupId,
    override val originatingUser: UserId,
    override val name: String,
    override val comment: String?,
    override val timestamp: Instant,
    override val balanceChanges: Map<UserId, BigDec>,
    val expenseAmount: BigDec
) : TransactionResponsePayload()

/**
 * The DTO for a request to create a new group.
 * @param displayName The display name of the new group to create.
 */
@Serializable
data class CreateGroupRequestPayload(val displayName: String)

/**
 * The DTO for a request to change a display name.
 * @param displayName The new display name.
 */
@Serializable
data class ChangeDisplayNameRequestPayload(val displayName: String)

/**
 * The DTO for a response containing information about a successful login.
 * @param accessToken The access token to be used by the client for authentication.
 * @param refreshToken The refresh token to be used by the client for refreshing its access token.
 */
@Serializable
class LoginResponsePayload(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * The base DTO for a request to post a transaction.
 */
@Serializable
sealed class TransactionRequestPayload {
    /**
     * The name of the transaction.
     */
    abstract val name: String
    /**
     * The optional comment of the transaction.
     */
    abstract val comment: String?
    /**
     * The balance changes of this transaction.
     */
    abstract val balanceChanges: Map<UserId, BigDec>
}

/**
 * The DTO for a request to post a payment.
 */
@Serializable
data class PaymentRequestPayload(
    override val name: String,
    override val comment: String?,
    override val balanceChanges: Map<UserId, BigDec>,
) : TransactionRequestPayload()

/**
 * The DTO for a request to post an expense.
 * @param expenseAmount The total amount of the expense.
 */
@Serializable
data class ExpenseRequestPayload(
    override val name: String,
    override val comment: String?,
    override val balanceChanges: Map<UserId, BigDec>,
    val expenseAmount: BigDec
) : TransactionRequestPayload()

/**
 * The DTO for a request to get balances of some [users] with respect to some [groups].
 * @param users The users to retrieve the balances of.
 * @param groups The groups to retrieve the [users] balances of.
 */
@Serializable
data class BalancesRequestPayload(
    val users: Set<UserId>?,
    val groups: Set<GroupId>?
)

/**
 * The DTO for a response containing the balances of the requested users with respect to the
 * requested groups.
 * @param balances The requested balances of the users with respect to the requested groups.
 */
@Serializable
data class BalancesResponsePayload(
    val balances: Map<Pair<UserId, GroupId>, BigDec>
)

/**
 * The DTO for a response containing the new invite [URI] of a group after regenerating its invite
 * token.
 * @param newInviteUri The new invite [URI] of the group.
 */
@Serializable
data class RegenerateInviteTokenResponsePayload(val newInviteUri: URI)
