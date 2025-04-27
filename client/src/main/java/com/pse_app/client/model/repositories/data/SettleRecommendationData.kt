package com.pse_app.client.model.repositories.data

import com.pse_app.common.util.BigDec

/**
 * Represents data for a settlement recommendation with one user.
 * @param user The user id.
 * @param amountsByGroup The amount per group.
 * @param totalAmount The total amount of the settlement.
 */
data class SettleRecommendationData (
    val user: UserId,
    val amountsByGroup: List<Pair<GroupId, BigDec>>,
    val totalAmount: BigDec,
)
