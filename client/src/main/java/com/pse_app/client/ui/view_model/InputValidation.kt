package com.pse_app.client.ui.view_model

private const val MAX_DISPLAY_NAME_LENGTH = 30

/**
 * Validates a display name.
 */
fun validateDisplayName(displayName: String): Boolean {
    return displayName.isNotBlank() && displayName.length <= MAX_DISPLAY_NAME_LENGTH
}
