package com.pse_app.client.model.exceptions

class TimeoutException(cause: Throwable): ModelException("HTTP request timed out", cause)
