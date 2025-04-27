package com.pse_app.client.model.facade

/**
 * Info about invite to join a group
 */
data class GroupInviteInfo(
    /** Group name */
    val displayName: String,
    /** Whether active user is member */
    val alreadyMember: Boolean,
    /** Group id */
    val id: String,
)
