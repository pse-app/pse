package com.pse_app.server.data

import kotlin.annotation.AnnotationRetention.SOURCE

/**
 * Annotated elements allow the use of magic numbers. The annotation should contain a reason.
 */
@Retention(SOURCE)
annotation class Magic(val why: String = "")
