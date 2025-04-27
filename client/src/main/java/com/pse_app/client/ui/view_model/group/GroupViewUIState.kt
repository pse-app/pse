package com.pse_app.client.ui.view_model.group

import com.pse_app.client.model.facade.Transaction
import com.pse_app.client.ui.view.GroupView

/**
 * Represents the UI state of [GroupView].
 * @param transactions The list of transactions in the group.
 */
data class GroupViewUIState(val transactions: List<Transaction>)
