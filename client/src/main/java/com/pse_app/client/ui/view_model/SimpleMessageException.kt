package com.pse_app.client.ui.view_model

import com.pse_app.client.StringId
import com.pse_app.client.StringResource

/**
 * Custom Exception for a simple error message.
 * @param messageResource The message as a [StringResource].
 */
class SimpleMessageException(
    val messageResource: StringResource,
    cause: Throwable? = null,
) : Exception((messageResource as? StringResource.RawString)?.value, cause) {
    constructor(message: StringId, cause: Throwable? = null) : this(StringResource(message), cause)
}
