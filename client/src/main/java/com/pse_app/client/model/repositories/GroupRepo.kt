package com.pse_app.client.model.repositories

import com.pse_app.client.model.repositories.data.GroupData

/**
 * Repository for group related actions and data, excluding transactions.
 * Single source of truth for [GroupData]
 */
interface GroupRepo : GroupRepoObservables, GroupRepoRemoteCalls
