package com.pse_app.client.ui.view_model.create_group

import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.ui.NavigationEvent
import com.pse_app.client.ui.view.CreateGroupView
import com.pse_app.client.ui.view.GroupView
import com.pse_app.client.ui.view.MainMenu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * View Model for [CreateGroupView].
 */
class CreateGroupViewModel(
    private val model: ModelFacade,
) : AbstractCreateGroupViewModel() {
    override fun createGroup(name: String) {
        if (isValidName(name)) {
            launchViewModel {
                try {
                    _isRequestActive.value = true
                    val createdGroup = model.createGroup(name)
                    navigate(
                        NavigationEvent.ViaPop(
                            via = MainMenu,
                            destination = GroupView(createdGroup.id)
                        )
                    )
                } finally {
                    _isRequestActive.value = false
                }
            }
        }
    }

    override fun isValidName(name: String): Boolean {
        return name.isNotBlank()
    }

    private val _isRequestActive = MutableStateFlow(false)
    override val isRequestActive: StateFlow<Boolean> = _isRequestActive.asStateFlow()
}
