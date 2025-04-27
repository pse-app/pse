package com.pse_app.client.model.repositories.data

/**
 * Represents data directly associated with a user.
 * @param userId The user id.
 * @param displayName The display name.
 * @param profilePicture The profile picture if available.
 */
data class UserData (
    val userId: UserId,
    val displayName: String,
    val profilePicture: String?
)
