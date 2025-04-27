package com.pse_app.client.model.facade

import android.net.Uri

/** Settings for OIDC discovery */
data class OIDCSettings (
    /** OIDC client id */
    val clientId: String,
    /** OIDC discovery URI */
    val discoveryUri: Uri,
)
