package com.pse_app.client.ui.view_model.main_menu

import com.pse_app.client.model.facade.Group
import com.pse_app.client.ui.view.MainMenuView

/**
 * Represents the UI state of [MainMenuView].
 * @param groups The groups to display.
 */
data class MainMenuUIState(
    val groups: List<Group>
)
