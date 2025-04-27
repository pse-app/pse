package com.pse_app.client.model.repositories

import com.pse_app.client.model.repositories.data.SettleRecommendationData
import com.pse_app.client.model.repositories.data.TransactionData

/**
 * Repository for transaction related actions and data.
 * Single source of truth for [TransactionData] and [SettleRecommendationData].
 */
interface TransactionRepo : TransactionRepoObservables, TransactionRepoRemoteCalls
