package com.pse_app.client.model.exceptions

/**
 * Resource not found on server. Could be due to outdated references.
 */
class NotFoundException(endpoint: String):
    ModelException("Not found when requesting endpoint $endpoint")
