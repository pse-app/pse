package com.pse_app.client.annotations

import kotlin.annotation.AnnotationRetention.SOURCE

/**
 * Annotated elements allow unused properties, since they where specified in the design document
 */
@Retention(SOURCE)
annotation class SpecifiedInDesign
