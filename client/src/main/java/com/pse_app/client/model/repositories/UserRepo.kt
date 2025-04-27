package com.pse_app.client.model.repositories

import com.pse_app.client.model.repositories.data.UserData

/**
 * Repository for user related actions and data.
 * Single source of truth for [UserData]
 */
interface UserRepo: UserRepoObservables, UserRepoRemoteCalls
