@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model

import android.net.Uri
import android.util.Log
import com.pse_app.client.MainCoroutineRule
import com.pse_app.client.model.facade.Group
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.model.facade.User
import com.pse_app.client.model.facade.stubs.PreviewUser
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.common.util.BigDec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.math.BigDecimal
import java.time.Instant
import java.util.Currency
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@Suppress("LeakingThis")
abstract class RuleTests {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    @get:Rule
    val mockKRule = MockKRule(this)
}

@Suppress("TestFunctionName")
abstract class ViewModelTests<VM: BaseViewModel>: RuleTests() {

    val facade: ModelFacade = mockk {
        coEvery { getCurrency() } returns Currency.getInstance("EUR")
        coEvery { refreshActiveUser() } just runs
    }
    val me = testingUser("me", facade, isMe = true)
    init {
        every { facade.observeActiveUser() } returns MutableStateFlow(me)
    }

    lateinit var viewModel: VM
    val errors = mutableListOf<Throwable>()
    val navigations = mutableListOf<NavigationEvent>()

    @Before
    fun __initialize() {
        mockAndroid()
    }

    @After
    fun __cleanup() {
        unmockkAll()
    }

    fun __assertUninitialized() = assert(!::viewModel.isInitialized)

    abstract fun makeViewModel(): VM

    fun assertNoNavigations() = assertEquals(listOf(), navigations)
    fun assertAndClearSingleNavigation() {
        assertNotEquals(listOf(), navigations, "Should have at least one navigation")
        navigations.removeLast()
        assertEquals(listOf(), navigations, "Unexpected extra navigations")
    }
    fun assertNoErrors() {
        for (e in errors) {
            e.printStackTrace()
        }
        assertEquals(listOf(), errors)
    }
    fun assertAndClearErrors() {
        assertNotEquals(listOf(), errors)
        errors.clear()
    }

    fun clearRecorededCalls(mock: Any) {
        clearMocks(
            mock,
            answers = false,
            recordedCalls = true,
            childMocks = false,
            verificationMarks = false,
            exclusionRules = false,
        )
    }
}

inline fun <reified VM: BaseViewModel> ViewModelTests<VM>.runViewModelTest(
    crossinline test: suspend TestScope.() -> Unit,
) = runTest {
    __assertUninitialized()
    viewModel = spyk(makeViewModel()) {
        every { handleException(any()) } answers {
            val ex = firstArg<Throwable>()
            if (ex is CancellationException || ex is Error) throw ex
            this@runViewModelTest.errors.add(ex)
        }

        every { navigate(capture(this@runViewModelTest.navigations)) } just runs

        every { unguardedViewModelScope } returns this@runTest
        every { viewModelScope } returns CoroutineScope(
            coroutineContext
                .plus(NonCancellable)
                .plus(CoroutineExceptionHandler { _, ex ->
                    if (ex is Error) throw ex
                    this@runViewModelTest.errors.add(ex)
                })
        )
    }

    test()
    assertNoErrors()
    assertNoNavigations()
}

fun testingGroup(facade: ModelFacade, me: User, id: String) = run {
    val group = mockk<Group>(relaxUnitFun = true) {
        every { this@mockk.id } returns id
        every { displayName } returns flowOf("TestGroup")
        every { inviteUrl } returns flowOf("https://pse-app.com/join/xyxysjhyhjydh")
        every { personalBalance } returns flowOf(BigDec(BigDecimal("4.20")))
        every { members } returns flowOf(
            listOf(
                testingUser("GroupMember1", facade),
                testingUser("GroupMember2", facade),
                testingUser("GroupMember3", facade),
                me,
            )
        )
        every { transactions } returns flowOf(listOf(mockk()))
        every { balances } returns flowOf(mapOf())
        every { mostRecentTransaction } returns flowOf(Instant.EPOCH)
        coEvery { refreshPartial() } just runs
        coEvery { refresh() } just runs
    }
    every { facade.getGroupById(id) } returns group
    group
}

fun testingUser(name: String, facade: ModelFacade, isMe: Boolean = false): User = run {
    val user = PreviewUser(name, name, null)
    val id = user.id
    every { facade.getUserById(id) } returns user
    if (isMe) every { facade.observeActiveUser() } returns flowOf(user)
    user
}

fun mockAndroid() {
    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0
    every { Log.i(any(), any()) } returns 0
    every { Log.i(any(), any(), any()) } returns 0

    mockkStatic(Uri::class)
    every { Uri.parse(any()) } answers {
        val mocked = mockk<Uri>()
        every { mocked.toString() } returns firstArg<String>()
        mocked
    }
}
