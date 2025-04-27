package com.pse_app.client.ui.view_model.create_group

import com.pse_app.client.ui.view_model.BaseViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract View Model for [CreateGroupViewModel].
 */
abstract class AbstractCreateGroupViewModel : BaseViewModel() {
    /**
     * Creates a group.
     */
    abstract fun createGroup(name: String)

    /**
     * Checks group name for validity
     */
    abstract fun isValidName(name: String): Boolean

    /**
     * Indicates whether a group creation request is currently ongoing.
     */
    abstract val isRequestActive: StateFlow<Boolean>
}
