package com.pse_app.client.model.exceptions

import com.pse_app.client.model.facade.ModelFacade

/**
 * Tried to execute an operation requiring an active user, but active user nonexistent.
 * Possible cause is a missing call to [ModelFacade.refreshActiveUser]
 */
class NoActiveUserException: ModelException("Action requires fresh active user")
