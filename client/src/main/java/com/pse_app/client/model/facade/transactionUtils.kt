package com.pse_app.client.model.facade

import com.pse_app.common.util.BigDec

/**
 * Returns map with same entries as [balanceChanges] but with zero entries removed
 */
internal fun <T> stripZeroBalance(balanceChanges: Map<T, BigDec>): Map<T, BigDec> {
    return balanceChanges.filterValues { it != BigDec(0) }
}

/**
 * Checks balance changes contain at least one non-zero value
 */
internal fun <T> balanceChangesNotEmpty(balanceChanges: Map<T, BigDec>): Boolean {
    return balanceChanges.map { if (it.value != BigDec.ZERO) {1} else {0} }.fold(0, Int::plus) > 0
}
