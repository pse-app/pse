package com.pse_app.client.ui.view_model.group_settings

import com.pse_app.client.model.facade.User
import com.pse_app.client.ui.view.GroupSettings
import com.pse_app.common.util.BigDec

/**
 * Represents the data a user in a group should show as displayed in [GroupSettings].
 * @param user Information about the user.
 * @param balance The balance of the user in the group.
 * @param isActiveUser Whether the user is the active user.
 * @param allowSettleUp Whether to enable settle up button.
 */
data class GroupUserData(
    val user: User,
    val balance: BigDec,
    val isActiveUser: Boolean,
    val allowSettleUp: Boolean
)
