package com.pse_app.client.model.exceptions

/**
 * Object referred to by id not found
 */
class ReferenceInvalidException(message: String? = null):
    ModelException(message ?: "Object referred to by id not found. Reference could be outdated.")
