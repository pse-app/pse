@file:OptIn(ExperimentalCoroutinesApi::class)

package com.pse_app.client.ui.view_model.settle_up_user_selection

import com.pse_app.client.model.facade.AllSettleRecommendations
import com.pse_app.client.model.facade.SettleRecommendation
import com.pse_app.client.ui.view_model.ViewModelTests
import com.pse_app.client.ui.view_model.runViewModelTest
import com.pse_app.client.ui.view_model.testingGroup
import com.pse_app.common.util.BigDec
import io.mockk.coEvery
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SettleUpUserSelectionViewModelTests: ViewModelTests<SettleUpUserSelectionViewModel>() {
    override fun makeViewModel() = SettleUpUserSelectionViewModel(facade)

    private val group = testingGroup(facade, me, "group")
    private val otherUser = group.members.run { runBlocking { first() } }!!.first { it != me }

    @Before
    fun init() {
        coEvery { facade.getAllSettleRecommendations() } coAnswers {
            val recs = group.members.first()!!.mapIndexed { i, user ->
                SettleRecommendation(
                    user = user,
                    amountsByGroup = mapOf(group to BigDec(i.toLong() + 1)),
                    totalAmount = BigDec(i.toLong() + 1),
                )
            }
            AllSettleRecommendations(
                byUser = recs,
                total = recs.fold(BigDec(0)) { total, rec ->
                    total + rec.totalAmount
                }
            )
        }
    }

    @Test
    fun `onEntry creates sane state`() = runViewModelTest {
        viewModel.onEntry()
        advanceUntilIdle()
        val state = assertNotNull(viewModel.uiState.value)
        val facadeValues = facade.getAllSettleRecommendations()
        assertEquals(facadeValues.total, state.total)
        assertThat(state.rows).allSatisfy { row ->
            assertThat(facadeValues.byUser).filteredOn {
                it.user.id == row.id
            }.singleElement().satisfies({ expect ->
                assertEquals(expect.totalAmount, row.amount)
            })
        }
        assertEquals(facade.getCurrency(), viewModel.currency)
    }

    @Test
    fun `navigations navigate`() = runViewModelTest {
        viewModel.selectUser(otherUser.id)
        assertAndClearSingleNavigation()
        viewModel.navigateToMainMenu()
        assertAndClearSingleNavigation()
    }
}
