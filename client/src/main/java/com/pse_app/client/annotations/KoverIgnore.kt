package com.pse_app.client.annotations

/**
 * Annotated object should be excluded from coverage for [reason]
 */
@Retention(AnnotationRetention.BINARY) // Retention needed for kover
@Target(AnnotationTarget.FILE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class KoverIgnore(@Suppress("unused") val reason: String)
