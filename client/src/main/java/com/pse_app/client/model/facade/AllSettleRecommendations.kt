package com.pse_app.client.model.facade

import com.pse_app.common.util.BigDec

/**
 * Summary of settlement recommendations
 */
data class AllSettleRecommendations (
    /**
     * The total owed to all groups the active user is a member in.
     */
    val total: BigDec,
    /**
     * A settlement recommendation for each known user
     */
    val byUser: List<SettleRecommendation>
)
