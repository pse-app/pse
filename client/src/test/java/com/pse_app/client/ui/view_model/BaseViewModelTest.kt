@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model

import com.pse_app.client.ui.NavigationEvent
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.test.assertEquals

class BaseViewModelTest: RuleTests() {
    val viewModel by lazy {
        object: BaseViewModel() {}
    }

    private suspend inline fun <T> TestScope.runCollecting(
        flow: Flow<T>,
        f: () -> Unit,
    ): List<T> {
        val items = mutableListOf<T>()
        val collector = backgroundScope.launch {
            flow.toList(items)
        }
        f()
        runCurrent()
        advanceUntilIdle()
        collector.cancelAndJoin()
        return items
    }

    @Test
    fun `test navigation channel`() = runTest {
        mockAndroid()
        val navigationEvent = mockk<NavigationEvent>()
        assertEquals(listOf(navigationEvent), runCollecting(viewModel.navigations) {
            viewModel.navigate(navigationEvent)
        })
        assertNotEquals(listOf<Any>(), runCollecting(viewModel.navigations) {
            viewModel.goBack()
        })
        assertNotEquals(listOf<Any>(), runCollecting(viewModel.navigations) {
            viewModel.raiseLogin()
        })
    }


    @Test
    fun `test error handler`() = runTest {
        mockAndroid()
        assertNotEquals(listOf<Any>(), runCollecting(viewModel.errors) {
            viewModel.handleException(Exception())
        })
        assertNotEquals(listOf<Any>(), runCollecting(viewModel.errors) {
            viewModel.viewModelScope.launch {
                throw ArithmeticException()
            }.join()
        })
    }
}
