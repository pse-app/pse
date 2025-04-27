package com.pse_app.client.model.facade

import kotlinx.coroutines.flow.Flow

/**
 * Represents a specific user provided by the model facade.
 * Observe user properties by collecting appropriate flows.
 * Refresh the user via [ModelFacade].
 */
interface User {
    /**
     * Uniquely identifies user
     */
    val id: String
    /**
     * A flow on the user display name.
     * Emits a first value immediately on collection.
     * Emits null if user no longer valid.
     */
    val displayName: Flow<String?>
    /**
     * A flow on the user profile picture URI.
     * Emits a first value immediately on collection.
     * Emits null if user no longer valid.
     */
    val profilePicture: Flow<String?>
}
