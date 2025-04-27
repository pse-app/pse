package com.pse_app.client

/**
 * A tag that may be used to identify this object for logging.
 * Imitates the default [Any.toString] implementation.
 */
inline val <reified T: Any> T.loggingTag
    get() = "${this.javaClass.name}@${Integer.toHexString(System.identityHashCode(this))}"

