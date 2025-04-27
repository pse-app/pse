package com.pse_app.client.model.exceptions

/**
 * Represents a possible Server error when a users balance has to be zero for an operation to
 * succeed but is not.
 *
 * @param isKick whether the event that caused this exception was a kick or a leave group action
 */
class BalanceNotZeroException(val isKick: Boolean):
    ModelException("User balance must be zero for operation to succeed")
