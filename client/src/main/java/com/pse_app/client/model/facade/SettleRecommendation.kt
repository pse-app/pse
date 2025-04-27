package com.pse_app.client.model.facade

import com.pse_app.common.util.BigDec

/**
 * Represents a recommendation for settling all outstanding amounts with one user
 */
data class SettleRecommendation (
    /** The user which whom to settle */
    val user: User,
    /** Settlement amounts by group */
    val amountsByGroup: Map<Group, BigDec>,
    /** Sum of amounts */
    val totalAmount: BigDec
)
