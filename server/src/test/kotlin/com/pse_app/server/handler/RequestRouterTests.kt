package com.pse_app.server.handler

import com.pse_app.common.dto.*
import com.pse_app.common.util.BigDec
import com.pse_app.server.data.*
import com.pse_app.server.data.Result.Error
import com.pse_app.server.data.Result.Success
import com.pse_app.server.data.config.Config
import com.pse_app.server.data.config.ConfigKey
import com.pse_app.server.data.config.ListenTarget
import com.pse_app.server.executor.RequestExecutor
import com.pse_app.server.group.Authorizer
import com.pse_app.server.user.Authenticator
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.time.Instant
import java.util.*
import kotlin.test.assertContains
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class RequestRouterTests {
    @MockK
    private lateinit var config: Config

    @MockK
    private lateinit var requestExecutor: RequestExecutor

    @MockK
    private lateinit var authenticator: Authenticator

    @MockK
    private lateinit var authorizer: Authorizer

    private lateinit var requestRouter: RequestRouter
    private val publicUri = URI("https://example.org/sub")

    @BeforeEach
    fun initRequestRouter() {
        every { config[ConfigKey.Listen] } returns ListenTarget.Port(0)
        requestRouter = RequestRouter(config, requestExecutor, authenticator, authorizer)
        every { config[ConfigKey.PublicURI] } returns publicUri
    }

    private fun withTestApplication(testBlock: suspend HttpClient.() -> Unit) = testApplication {
        install(Authentication) {
            bearer("local-access-token") {
                /*
                In Testing, a user id is used as the access token.
                Authentication should always succeed if a token was provided.
                 */
                authenticate { Success(UserId(it.token)) }
            }
        }
        application {
            requestRouter.run { routes() }
        }

        createClient {
            install(ContentNegotiation) {
                json()
            }
        }.apply { testBlock() }
    }

    @Test
    fun getSettings() = withTestApplication {
        val discoveryUri = URI("https://macrohard.com/oidc-configuration")
        val clientId = "client-id"
        val currency = Currency.getInstance("USD")
        every { config[ConfigKey.OidcDiscoveryURI] } returns discoveryUri
        every { config[ConfigKey.OidcClientId] } returns clientId
        every { config[ConfigKey.Currency] } returns currency
        val response = get("/v1/settings")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            SettingsResponsePayload(clientId, discoveryUri, currency),
            response.body<SettingsResponsePayload>()
        )
    }

    @Test
    fun createGroupFailsInternally() = withTestApplication {
        val user = UserId("Peter")
        val displayName = "group name"
        val error = Error("idk why it fails")
        every { requestExecutor.createGroup(user, displayName) } returns error
        val response = put("/v1/groups") {
            headers {
                bearerAuth(user.id)
                contentType(ContentType.Application.Json)
            }
            setBody(CreateGroupRequestPayload(displayName))
        }
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(error.message, response.bodyAsText())
    }

    @Test
    fun createGroupNeedsAuthentication() = withTestApplication {
        val displayName = "group name"
        val response = put("/v1/groups") {
            headers {
                contentType(ContentType.Application.Json)
            }
            setBody(CreateGroupRequestPayload(displayName))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun createGroup() = withTestApplication {
        val user = UserId("Peter")
        val displayName = "group name"
        val group = GroupId(UUID.nameUUIDFromBytes("group name".toByteArray()))
        val members = setOf(user)
        val inviteToken = "invite-token"
        val inviteUri = resolveInviteToken(inviteToken)
        val mostRecentTransaction = Instant.parse("1882-03-23T00:00:00Z")
        every { requestExecutor.createGroup(user, displayName) } returns Success(
            GroupMembershipInfo(GroupInfo(group, displayName, inviteToken, mostRecentTransaction), members)
        )
        val response = put("/v1/groups") {
            headers {
                bearerAuth(user.id)
                contentType(ContentType.Application.Json)
            }
            setBody(CreateGroupRequestPayload(displayName))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(
            GroupInfoResponsePayload(group, displayName, inviteUri, mostRecentTransaction, members),
            response.body<GroupInfoResponsePayload>()
        )
        verify(exactly = 1) { requestExecutor.createGroup(user, displayName) }
    }

    @Test
    fun getGroupsNeedsAuthentication() = withTestApplication {
        val group1 = GroupId(UUID.nameUUIDFromBytes("a group".toByteArray()))
        val group2 = GroupId(UUID.nameUUIDFromBytes("another group".toByteArray()))
        val groups = setOf(group1, group2)
        val response = get("/v1/groups") {
            headers {
                contentType(ContentType.Application.Json)
            }
            setBody(groups)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun getGroupsNeedsAuthorization() = withTestApplication {
        val user = UserId("Peter")
        val group1 = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val group2 = GroupId(UUID.nameUUIDFromBytes("another group".toByteArray()))
        val groups = setOf(group1, group2)
        val error = Error(ErrorCategory.GroupNotFound, "group should not be known")
        every { authorizer.allGroupsKnown(user, groups) } returns error
        val response = get("/v1/groups") {
            headers {
                bearerAuth(user.id)
                contentType(ContentType.Application.Json)
            }
            setBody(groups)
        }

        assertEquals(error.message, response.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun patchGroupDisplayName() = withTestApplication {
        val user = UserId("Peter")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val displayName = "new display name"
        val inviteToken = "invite-token"
        val inviteUri = resolveInviteToken(inviteToken)
        val mostRecentTransaction = Instant.parse("1882-03-23T00:00:00Z")
        val members = setOf(user, UserId("other user"))
        every { authorizer.groupKnown(user, group) } returns Success(Unit)
        every { requestExecutor.updateGroupDisplayName(group, displayName) } returns Success(
            GroupMembershipInfo(GroupInfo(group, displayName, inviteToken, mostRecentTransaction), members)
        )
        val response = patch("/v1/groups/$group/displayName") {
            headers {
                bearerAuth(user.id)
                contentType(ContentType.Application.Json)
            }
            setBody(ChangeDisplayNameRequestPayload(displayName))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            GroupInfoResponsePayload(group, displayName, inviteUri, mostRecentTransaction, members),
            response.body<GroupInfoResponsePayload>(),
        )
        verify(exactly = 1) { requestExecutor.updateGroupDisplayName(group, displayName) }
    }

    @Test
    fun patchGroupDisplayNeedsAuthorization() = withTestApplication {
        val user = UserId("Peter")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val displayName = "new display name"
        val error = Error(ErrorCategory.GroupNotFound, "group not known")
        every { authorizer.groupKnown(user, group) } returns error
        val response = patch("/v1/groups/$group/displayName") {
            headers {
                bearerAuth(user.id)
                contentType(ContentType.Application.Json)
            }
            setBody(ChangeDisplayNameRequestPayload(displayName))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(error.message, response.bodyAsText())
    }

    @Test
    fun patchGroupDisplayNeedsAuthentication() = withTestApplication {
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val displayName = "new display name"
        val response = patch("/v1/groups/$group/displayName") {
            headers {
                contentType(ContentType.Application.Json)
            }
            setBody(ChangeDisplayNameRequestPayload(displayName))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun postRegenerateInviteLink() = withTestApplication {
        val user = UserId("Peter")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val newInviteToken = "new-invite-token"
        val newInviteUri = resolveInviteToken(newInviteToken)
        every { config[ConfigKey.PublicURI] } returns publicUri
        every { authorizer.groupKnown(user, group) } returns Success(Unit)
        every { requestExecutor.regenerateInviteToken(group) } returns Success(newInviteToken)
        val response = post("/v1/groups/$group/regenerateInviteLink") {
            headers {
                bearerAuth(user.id)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            RegenerateInviteTokenResponsePayload(newInviteUri),
            response.body<RegenerateInviteTokenResponsePayload>(),
        )
        verify(exactly = 1) { requestExecutor.regenerateInviteToken(group) }
    }

    @Test
    fun postRegenerateInviteLinkNeedsAuthorization() = withTestApplication {
        val user = UserId("Peter")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val error = Error(ErrorCategory.GroupNotFound, "group not known")
        every { authorizer.groupKnown(user, group) } returns error
        val response = post("/v1/groups/$group/regenerateInviteLink") {
            headers {
                bearerAuth(user.id)
            }
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(error.message, response.bodyAsText())
    }

    @Test
    fun postRegenerateInviteLinkNeedsAuthentication() = withTestApplication {
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val response = post("/v1/groups/$group/regenerateInviteLink")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun postKick() = withTestApplication {
        val user = UserId("Peter")
        val target = UserId("Gustav")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        every { authorizer.groupKnown(user, group) } returns Success(Unit)
        every { requestExecutor.kickUser(group, target) } returns Success(Unit)
        val response = post("/v1/groups/$group/kick/$target") {
            headers {
                bearerAuth(user.id)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) { requestExecutor.kickUser(group, target) }
    }

    @Test
    fun postKickNeedsAuthorization() = withTestApplication {
        val user = UserId("Peter")
        val target = UserId("Gustav")
        val group = GroupId(UUID.nameUUIDFromBytes("Gruppierung".toByteArray()))
        val error = Error(ErrorCategory.UserNotFound, "target not in group")
        every { authorizer.groupKnown(user, group) } returns error
        val response = post("/v1/groups/$group/kick/$target") {
            headers {
                bearerAuth(user.id)
            }
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(
            error.message,
            response.bodyAsText()
        )
    }

    @Test
    fun postKickNeedsAuthentication() = withTestApplication {
        val target = UserId("Gustav")
        val group = GroupId(UUID.nameUUIDFromBytes("Gruppierung".toByteArray()))
        val response = post("/v1/groups/$group/kick/$target")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun putTransactions() = withTestApplication {
        val user = UserId("Peter")
        val target = UserId("Gustav")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val groups = setOf(group)
        val paymentName = "important payment"
        val balanceChanges = mapOf(user to BigDec(-100), target to BigDec(100))
        every { authorizer.allGroupsKnown(user, groups) } returns Success(Unit)
        every { requestExecutor.postTransactions(any()) } returns Success(Unit)
        val requestBody: Map<GroupId, TransactionRequestPayload> = mapOf(
            group to PaymentRequestPayload(
                name = paymentName,
                comment = null,
                balanceChanges = balanceChanges,
            )
        )
        val response = put("/v1/transactions") {
            headers {
                bearerAuth(user.id)
                contentType(ContentType.Application.Json)
            }
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        verify(exactly = 1) {
            requestExecutor.postTransactions(withArg { transactions ->
                assertEquals(1, transactions.size)
                val transaction = transactions.first()
                assertEquals(group, transaction.group)
                assertEquals(paymentName, transaction.name)
                assertEquals(null, transaction.comment)
                assertEquals(balanceChanges, transaction.balanceChanges)
                assertEquals(user, transaction.originatingUser)
            })
        }
    }

    @Test
    fun putTransactionsNeedsAuthorization() = withTestApplication {
        val user = UserId("Peter")
        val target = UserId("Gustav")
        val group = GroupId(UUID.nameUUIDFromBytes("group name".toByteArray()))
        val groups = setOf(group)
        val paymentName = "important payment"
        val balanceChanges = mapOf(user to BigDec(-100), target to BigDec(100))
        val error = Error(ErrorCategory.GroupNotFound, "user not in group")
        every { authorizer.allGroupsKnown(user, groups) } returns error
        val requestBody: Map<GroupId, TransactionRequestPayload> = mapOf(
            group to PaymentRequestPayload(
                name = paymentName,
                comment = null,
                balanceChanges = balanceChanges,
            )
        )
        val response = put("/v1/transactions") {
            headers {
                bearerAuth(user.id)
                contentType(ContentType.Application.Json)
            }
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(error.message, response.bodyAsText())
    }

    @Test
    fun putTransactionsNeedsAuthentication() = withTestApplication {
        val user = UserId("Peter")
        val target = UserId("Gustav")
        val group = GroupId(UUID.nameUUIDFromBytes("group name".toByteArray()))
        val paymentName = "important payment"
        val balanceChanges = mapOf(user to BigDec(-100), target to BigDec(100))
        val requestBody: Map<GroupId, TransactionRequestPayload> = mapOf(
            group to PaymentRequestPayload(
                name = paymentName,
                comment = null,
                balanceChanges = balanceChanges,
            )
        )
        val response = put("/v1/transactions") {
            headers {
                contentType(ContentType.Application.Json)
            }
            setBody(requestBody)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun getTransactions() = withTestApplication {
        val user1 = UserId("Peter")
        val user2 = UserId("Gustav")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val groups = setOf(group)
        val paymentName = "important payment"
        val expenseName = "even more important expense"
        val paymentTimestamp = Instant.now()
        val expenseTimestamp = Instant.now()
        val expenseBalanceChanges = mapOf(user1 to BigDec(-100), user2 to BigDec(100))
        val paymentBalanceChanges = mapOf(user1 to BigDec(100), user2 to BigDec(-100))
        val expenseAmount = BigDec(100)
        every { authorizer.allGroupsKnown(user1, groups) } returns Success(Unit)
        every { requestExecutor.getTransactions(groups) } returns Success(
            mapOf(
                group to listOf(
                    Payment(
                        group = group,
                        name = paymentName,
                        comment = null,
                        timestamp = paymentTimestamp,
                        balanceChanges = paymentBalanceChanges,
                        originatingUser = user1
                    ),
                    Expense(
                        group = group,
                        name = expenseName,
                        comment = null,
                        timestamp = expenseTimestamp,
                        balanceChanges = expenseBalanceChanges,
                        originatingUser = user2,
                        expenseAmount = expenseAmount
                    )
                )
            )
        )
        val response = get("/v1/transactions") {
            headers {
                bearerAuth(user1.id)
                contentType(ContentType.Application.Json)
            }
            setBody(groups)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            mapOf(
                group to listOf(
                    PaymentResponsePayload(
                        group = group,
                        originatingUser = user1,
                        name = paymentName,
                        comment = null,
                        timestamp = paymentTimestamp,
                        balanceChanges = paymentBalanceChanges
                    ),
                    ExpenseResponsePayload(
                        group = group,
                        originatingUser = user2,
                        name = expenseName,
                        comment = null,
                        timestamp = expenseTimestamp,
                        balanceChanges = expenseBalanceChanges,
                        expenseAmount = expenseAmount
                    )
                )
            ),
            response.body<Map<GroupId, List<TransactionResponsePayload>>>()
        )
    }

    @Test
    fun getTransactionsNeedsAuthorization() = withTestApplication {
        val user = UserId("Peter")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val groups = setOf(group)
        val error = Error(
            ErrorCategory.GroupNotFound,
            "Die von ihnen gesuchte Gruppierung konnte nicht gefunden werden"
        )
        every { authorizer.allGroupsKnown(user, groups) } returns error
        val response = get("/v1/transactions") {
            headers {
                bearerAuth(user.id)
                contentType(ContentType.Application.Json)
            }
            setBody(groups)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(error.message, response.bodyAsText())
    }

    @Test
    fun getTransactionsNeedsAuthentication() = withTestApplication {
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val groups = setOf(group)
        val response = get("/v1/transactions") {
            headers {
                contentType(ContentType.Application.Json)
            }
            setBody(groups)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun getBalancesOfSomeUsersAndSomeGroups() = withTestApplication {
        val user1 = UserId("Peter")
        val user2 = UserId("Peters Bruder")
        val user3 = UserId("Peters Schwester")
        val users = setOf(user1, user2, user3)
        val allUsers = setOf(user1, user2, user3)
        val group1 = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val group2 = GroupId(UUID.nameUUIDFromBytes("group2".toByteArray()))
        val groups = setOf(group1, group2)
        val allGroups = setOf(group1, group2)
        every { authorizer.allUsersKnown(user1, any()) } returns Success(Unit)
        every { authorizer.allGroupsKnown(user1, any()) } returns Success(Unit)
        every { requestExecutor.listKnownUsers(user1) } returns Success(allUsers)
        every { requestExecutor.listKnownGroups(user1) } returns Success(allGroups)
        every { requestExecutor.calculateBalances(any(), any()) } returns Success(mapOf())
        val response = get("/v1/balances") {
            headers {
                bearerAuth(user1.id)
                contentType(ContentType.Application.Json)
            }
            setBody(BalancesRequestPayload(users, groups))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) { requestExecutor.calculateBalances(users, groups) }
    }

    @Test
    fun getBalancesOfSomeUsersAndNoGroups() = withTestApplication {
        val user1 = UserId("Peter")
        val user2 = UserId("Peters Bruder")
        val user3 = UserId("Peters Schwester")
        val users = setOf(user1, user2, user3)
        val allUsers = setOf(user1, user2, user3)
        val group1 = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val group2 = GroupId(UUID.nameUUIDFromBytes("group2".toByteArray()))
        val groups = null
        val allGroups = setOf(group1, group2)
        every { authorizer.allUsersKnown(user1, any()) } returns Success(Unit)
        every { authorizer.allGroupsKnown(user1, any()) } returns Success(Unit)
        every { requestExecutor.listKnownUsers(user1) } returns Success(allUsers)
        every { requestExecutor.listKnownGroups(user1) } returns Success(allGroups)
        every { requestExecutor.calculateBalances(any(), any()) } returns Success(mapOf())
        val response = get("/v1/balances") {
            headers {
                bearerAuth(user1.id)
                contentType(ContentType.Application.Json)
            }
            setBody(BalancesRequestPayload(users, groups))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) { requestExecutor.calculateBalances(users, allGroups) }
    }

    @Test
    fun getBalancesOfNoUsersAndSomeGroups() = withTestApplication {
        val user1 = UserId("Peter")
        val user2 = UserId("Peters Bruder")
        val user3 = UserId("Peters Schwester")
        val users = null
        val allUsers = setOf(user1, user2, user3)
        val group1 = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val group2 = GroupId(UUID.nameUUIDFromBytes("group2".toByteArray()))
        val groups = setOf(group1)
        val allGroups = setOf(group1, group2)
        every { authorizer.allUsersKnown(user1, any()) } returns Success(Unit)
        every { authorizer.allGroupsKnown(user1, any()) } returns Success(Unit)
        every { requestExecutor.listKnownUsers(user1) } returns Success(allUsers)
        every { requestExecutor.listKnownGroups(user1) } returns Success(allGroups)
        every { requestExecutor.calculateBalances(any(), any()) } returns Success(mapOf())
        val response = get("/v1/balances") {
            headers {
                bearerAuth(user1.id)
                contentType(ContentType.Application.Json)
            }
            setBody(BalancesRequestPayload(users, groups))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) { requestExecutor.calculateBalances(allUsers, groups) }
    }

    @Test
    fun getBalancesOfNoUsersAndNoGroups() = withTestApplication {
        val user1 = UserId("Peter")
        val user2 = UserId("Peters Bruder")
        val user3 = UserId("Peters Schwester")
        val users = null
        val allUsers = setOf(user1, user2, user3)
        val group1 = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val group2 = GroupId(UUID.nameUUIDFromBytes("group2".toByteArray()))
        val groups = null
        val allGroups = setOf(group1, group2)
        every { authorizer.allUsersKnown(user1, any()) } returns Success(Unit)
        every { authorizer.allGroupsKnown(user1, any()) } returns Success(Unit)
        every { requestExecutor.listKnownUsers(user1) } returns Success(allUsers)
        every { requestExecutor.listKnownGroups(user1) } returns Success(allGroups)
        every { requestExecutor.calculateBalances(any(), any()) } returns Success(mapOf())
        val response = get("/v1/balances") {
            headers {
                bearerAuth(user1.id)
                contentType(ContentType.Application.Json)
            }
            setBody(BalancesRequestPayload(users, groups))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) { requestExecutor.calculateBalances(allUsers, allGroups) }
    }

    @Test
    fun getBalancesNeedsAuthorization() = withTestApplication {
        val user1 = UserId("Peter")
        val user2 = UserId("Peters Bruder")
        val users = setOf(user2)
        val group1 = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val groups = setOf(group1)
        val userError = Error(ErrorCategory.UserNotFound, "user not found")
        val groupError = Error(ErrorCategory.GroupNotFound, "group not found")
        every { authorizer.allUsersKnown(user1, users) } returns userError
        every { authorizer.allGroupsKnown(user1, groups) } returns groupError
        val response = get("/v1/balances") {
            headers {
                bearerAuth(user1.id)
                contentType(ContentType.Application.Json)
            }
            setBody(BalancesRequestPayload(users, groups))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertContains(listOf(userError.message, groupError.message), response.bodyAsText())
    }

    @Test
    fun getBalancesNeedsAuthentication() = withTestApplication {
        val user1 = UserId("Peter")
        val user2 = UserId("Peters Bruder")
        val users = setOf(user1, user2)
        val group1 = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val groups = setOf(group1)
        val response = get("/v1/balances") {
            headers {
                contentType(ContentType.Application.Json)
            }
            setBody(BalancesRequestPayload(users, groups))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun getMe() = withTestApplication {
        val user = UserId("Peter")
        val group = GroupId(UUID.nameUUIDFromBytes("group1".toByteArray()))
        val groups = setOf(group)
        val displayName = "display name"
        every { requestExecutor.retrieveUserMembershipInfo(user, user) } returns Success(
            UserMembershipInfo(
                info = UserInfo(
                    id = user,
                    displayName = displayName,
                    profilePicture = null
                ),
                groups = groups
            )
        )
        val response = get("/v1/me") {
            headers {
                bearerAuth(user.id)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            UserInfoResponsePayload(
                id = user,
                displayName = displayName,
                profilePicture = null,
                groups = groups
            ),
            response.body<UserInfoResponsePayload>()
        )
    }

    @Test
    fun getMeNeedsAuthentication() = withTestApplication {
        val response = get("/v1/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun deleteMe() = withTestApplication {
        val user = UserId("Peter")
        every { requestExecutor.deactivateUser(user) } returns Success(Unit)
        val response = delete("/v1/me") {
            headers {
                bearerAuth(user.id)
            }
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        verify(exactly = 1) { requestExecutor.deactivateUser(user) }
    }

    @Test
    fun deleteMeNeedsAuthentication() = withTestApplication {
        val response = delete("/v1/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun patchMeDisplayName() = withTestApplication {
        val user = UserId("Peter")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val groups = setOf(group)
        val newDisplayName = "new display name"
        every { requestExecutor.updateUserDisplayName(user, newDisplayName) } returns Success(
            UserMembershipInfo(
                info = UserInfo(
                    id = user,
                    displayName = newDisplayName,
                    profilePicture = null
                ),
                groups = groups
            )
        )
        val response = patch("/v1/me/displayName") {
            headers {
                bearerAuth(user.id)
                contentType(ContentType.Application.Json)
            }
            setBody(ChangeDisplayNameRequestPayload(newDisplayName))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            UserInfoResponsePayload(
                id = user,
                displayName = newDisplayName,
                profilePicture = null,
                groups = groups
            ),
            response.body<UserInfoResponsePayload>()
        )
        verify(exactly = 1) { requestExecutor.updateUserDisplayName(user, newDisplayName) }
    }

    @Test
    fun patchMeDisplayNameNeedsAuthentication() = withTestApplication {
        val newDisplayName = "new display name"
        val response = patch("/v1/me/displayName") {
            headers {
                contentType(ContentType.Application.Json)
            }
            setBody(ChangeDisplayNameRequestPayload(newDisplayName))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun getKnownUsers() = withTestApplication {
        val user = UserId("Peter")
        val user2 = UserId("Gunter")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val knownUsers = setOf(user, user2)
        val groups = setOf(group)
        val displayName = "username"
        val displayName2 = "username2"
        every { requestExecutor.listKnownUsers(user) } returns Success(knownUsers)
        every { requestExecutor.retrieveUserMembershipInfo(user, knownUsers) } returns Success(
            mapOf(
                user to
                        UserMembershipInfo(
                            info = UserInfo(
                                id = user,
                                displayName = displayName,
                                profilePicture = null
                            ),
                            groups = groups
                        ),
                user2 to
                        UserMembershipInfo(
                            info = UserInfo(
                                id = user2,
                                displayName = displayName2,
                                profilePicture = null
                            ),
                            groups = groups
                        ),
            )
        )
        val response = get("/v1/knownUsers") {
            headers {
                bearerAuth(user.id)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val userInfos = response.body<List<UserInfoResponsePayload>>()
        assertContains(
            userInfos,
            UserInfoResponsePayload(
                id = user,
                displayName = displayName,
                profilePicture = null,
                groups = groups
            ),
        )
        assertContains(
            userInfos,
            UserInfoResponsePayload(
                id = user2,
                displayName = displayName2,
                profilePicture = null,
                groups = groups
            ),
        )
    }

    @Test
    fun getKnownUsersNeedsAuthentication() = withTestApplication {
        val response = get("/v1/knownUsers")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun getUsers() = withTestApplication {
        val user = UserId("Peter")
        val user2 = UserId("Gunter")
        val commonGroup = GroupId(UUID.nameUUIDFromBytes("common group".toByteArray()))
        val otherGroup = GroupId(UUID.nameUUIDFromBytes("other group".toByteArray()))
        val displayName = "username"
        val displayName2 = "username2"
        val users = setOf(user, user2)
        every { authorizer.allUsersKnown(user, users) } returns Success(Unit)
        every { requestExecutor.retrieveUserMembershipInfo(user, users) } returns Success(
            mapOf(
                user to
                        UserMembershipInfo(
                            info = UserInfo(
                                id = user,
                                displayName = displayName,
                                profilePicture = null
                            ),
                            groups = setOf(otherGroup, commonGroup)
                        ),
                user2 to
                        UserMembershipInfo(
                            info = UserInfo(
                                id = user2,
                                displayName = displayName2,
                                profilePicture = null
                            ),
                            groups = setOf(commonGroup)
                        ),
            )
        )
        val response = get("/v1/users") {
            headers {
                bearerAuth(user.id)
                contentType(ContentType.Application.Json)
            }
            setBody(users)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            mapOf(
                user to UserInfoResponsePayload(
                    id = user,
                    displayName = displayName,
                    profilePicture = null,
                    groups = setOf(otherGroup, commonGroup)
                ),
                user2 to UserInfoResponsePayload(
                    id = user2,
                    displayName = displayName2,
                    profilePicture = null,
                    groups = setOf(commonGroup)
                ),
            ),
            response.body<Map<UserId, UserInfoResponsePayload>>()
        )
    }

    @Test
    fun getUsersNeedsAuthorization() = withTestApplication {
        val user = UserId("Peter")
        val user2 = UserId("Gunter")
        val users = setOf(user, user2)
        val error = Error(ErrorCategory.UserNotFound, "user not found")
        every { authorizer.allUsersKnown(user, users) } returns error
        val response = get("/v1/users") {
            headers {
                bearerAuth(user.id)
                contentType(ContentType.Application.Json)
            }
            setBody(users)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(error.message, response.bodyAsText())
    }

    @Test
    fun getUsersNeedsAuthentication() = withTestApplication {
        val user = UserId("Peter")
        val user2 = UserId("Gunter")
        val users = setOf(user, user2)
        val response = get("/v1/users") {
            headers {
                contentType(ContentType.Application.Json)
            }
            setBody(users)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun postJoin() = withTestApplication {
        val user = UserId("Peter")
        val user2 = UserId("Albrecht")
        val inviteToken = "let-me-iiiiin"
        val mostRecentTransaction = Instant.parse("1882-03-23T00:00:00Z")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val displayName = "group name"
        val newMembers = setOf(user, user2)
        every { requestExecutor.joinGroup(user, inviteToken) } returns Success(
            GroupMembershipInfo(
                info = GroupInfo(
                    id = group,
                    displayName = displayName,
                    inviteToken = inviteToken,
                    mostRecentTransaction = mostRecentTransaction
                ),
                members = newMembers,
            )
        )
        val response = post("/v1/join/$inviteToken") {
            headers {
                bearerAuth(user.id)
            }
        }
        assertEquals(HttpStatusCode.OK, response.status)
        verify(exactly = 1) { requestExecutor.joinGroup(user, inviteToken) }
    }

    @Test
    fun getJoinInfo() = withTestApplication {
        val user = UserId("Peter")
        val user2 = UserId("Albrecht")
        val inviteToken = "let-me-iiiiin"
        val mostRecentTransaction = Instant.parse("1882-03-23T00:00:00Z")
        val group = GroupId(UUID.nameUUIDFromBytes("group".toByteArray()))
        val displayName = "group name"
        for (members in listOf(setOf(user), setOf(user, user2))) {
            every { requestExecutor.getInviteInfo(inviteToken) } returns Success(
                GroupMembershipInfo(
                    info = GroupInfo(
                        id = group,
                        displayName = displayName,
                        inviteToken = inviteToken,
                        mostRecentTransaction = mostRecentTransaction
                    ),
                    members = members,
                )
            )
            val response = get("/v1/join/$inviteToken/info") {
                headers {
                    bearerAuth(user.id)
                }
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                GroupInviteInfoResponsePayload(
                    displayName = displayName,
                    alreadyMember = members.contains(user),
                    id = group,
                ),
                response.body<GroupInviteInfoResponsePayload>()
            )
        }
    }

    @Test
    fun getJoinInfoNeedsAuthentication() = withTestApplication {
        val inviteToken = "let-me-iiiiin"
        val response = get("/v1/join/$inviteToken/info")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun postJoinFailsInternally() = withTestApplication {
        val user = UserId("Peter")
        val inviteToken = "let-me-iiiiin"
        val error = Error(ErrorCategory.InvalidToken, "invalid invite token")
        every { requestExecutor.joinGroup(user, inviteToken) } returns error
        val response = post("/v1/join/$inviteToken") {
            headers {
                bearerAuth(user.id)
            }
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(error.message, response.bodyAsText())
    }

    @Test
    fun postJoinNeedsAuthentication() = withTestApplication {
        val inviteToken = "let-me-iiiiin"
        val response = post("/v1/join/$inviteToken")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun nonExistingRoute() = withTestApplication {
        val response = post("/does/not/exist")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun wrongMethod() = withTestApplication {
        val response = post("/v1/me")
        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
    }

    @Test
    fun testUriResolve() {
        val testUris = listOf(
            "htcpcp://test/aaaa/",
            "https://test.kit.edu/aaa",
            "http://test.kit.edu/",
            "smtp://test.kit.edu",
        ).map(::URI)
        val testPaths = listOf(
            "v1/test",
            "/v1/test",
            "/v1/test/",
            "v1/test/",
            "test",
            "/test",
            "test/",
        )
        for (base in testUris) {
            every { config[ConfigKey.PublicURI] } returns base

            for (path in testPaths) {
                val expected = "$base/$path"
                    .replace("//", "/")
                    .replace("//", "/")
                    .replace(":/", "://")
                val actual = requestRouter.resolveWithPublicUri(path).toString()

                assertEquals(expected, actual)
            }
        }
    }

    private fun resolveInviteToken(inviteToken: String): URI =
        requestRouter.resolveWithPublicUri("/v1/join/$inviteToken")
}
