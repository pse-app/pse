package com.pse_app.server.data

/**
 * Specifies the category of a [Result.Error].
 */
sealed interface ErrorCategory {

    /**
     * A generic error that does not fit any other category.
     */
    data object Generic : ErrorCategory

    /**
     * An invalid or expired access, refresh or ID Token has been provided.
     */
    data object InvalidToken : ErrorCategory

    /**
     * An invalid or expired invite-Token has been provided.
     */
    data object InvalidInvite : ErrorCategory

    /**
     * A user could not be found in the database or is not known to the requester and its existence should
     * therefore not be revealed to them.
     */
    data object UserNotFound : ErrorCategory
    
    /**
     * A group could not be found in the database or is not known to the requester and its existence should
     * therefore not be revealed to them.
     */
    data object GroupNotFound : ErrorCategory

    /**
     * Some user input retrieved is malformed and has been rejected.
     */
    data object RejectedInput : ErrorCategory

    /**
     * Some operation expected the system to be in a particular state which it wasn't in. This is different from
     * [RejectedInput] in that the requester sent a valid request that could not be fulfilled due to the current
     * state of the system.
     */
    data object PreconditionFailed : ErrorCategory
}
