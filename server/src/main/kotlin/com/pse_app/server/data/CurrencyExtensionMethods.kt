package com.pse_app.server.data

import java.util.Currency

/**
 * Returns the default fraction digits ised for a currency. For currencies where
 * [Currency.defaultFractionDigits] is {@code -1}, this defaults to {@code 2}.
 */
val Currency.effectiveFractionDigits
    get() = when (val digits = this.defaultFractionDigits) {
        -1 -> 2
        else -> digits
    }
