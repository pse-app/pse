package com.pse_app.client.ui.view_model.settle_up_user_selection

import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.view.MainMenu
import com.pse_app.client.ui.view.SettleUpGroupSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.Currency

/**
 * Standard implementation of [AbstractSettleUpUserSelectionViewModel]
 */
class SettleUpUserSelectionViewModel(
    private val model: ModelFacade,
) : AbstractSettleUpUserSelectionViewModel() {
    override var currency: Currency? = null
        private set

    private val _uiState = MutableStateFlow<SettleUpUserUIState?>(null)
    override val uiState = _uiState.asStateFlow()

    override fun navigateToMainMenu() {
        navigate(NavigationEvent.SimpleNav(MainMenu))
    }

    override fun onEntry() {
        super.onEntry()
        launchViewModel {
            currency = model.getCurrency()
        }
        launchViewModel {
            val recommendation = model.getAllSettleRecommendations()
            // After getAllSettleRecommendations()
            // all relevant users are fresh and can be queried with first()
            val total = recommendation.total
            _uiState.value = SettleUpUserUIState(
                total,
                recommendation.byUser.map { SettleUserRow(
                    it.user.id,
                    it.user.displayName.first()!!,
                    it.user.profilePicture.first(),
                    it.totalAmount)
                }
            )
        }
    }

    override fun selectUser(userId: String) =
        navigate(NavigationEvent.SimpleNav(SettleUpGroupSelection(userId)))
}
