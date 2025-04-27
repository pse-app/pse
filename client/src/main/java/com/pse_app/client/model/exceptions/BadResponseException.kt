package com.pse_app.client.model.exceptions

/**
 * Server response can't be processed to due bad format, faulty data.
 */
class BadResponseException(message: String?=null): ModelException(message)
