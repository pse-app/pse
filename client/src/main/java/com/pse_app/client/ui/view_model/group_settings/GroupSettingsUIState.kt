package com.pse_app.client.ui.view_model.group_settings

import com.pse_app.client.ui.view.GroupSettingsView

/**
 * Represents the UI state of the [GroupSettingsView].
 * @param inviteLink The invite link of the group.
 * @param groupName The display name of the group.
 */
data class GroupSettingsUIState(val inviteLink: String, val groupName: String)
