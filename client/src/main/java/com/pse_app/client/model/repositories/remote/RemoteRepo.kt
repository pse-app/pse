package com.pse_app.client.model.repositories.remote

import com.pse_app.client.model.data_layer.RemoteAPI
import com.pse_app.client.model.repositories.Repo

/**
 * Bundles and initializes [RemoteUserRepo], [RemoteGroupRepo] and [RemoteTransactionRepo].
 */
class RemoteRepo(remoteAPI: RemoteAPI) : Repo() {
    override val userRepo = RemoteUserRepo(remoteAPI, this)
    override val groupRepo = RemoteGroupRepo(remoteAPI, this)
    override val transactionRepo = RemoteTransactionRepo(remoteAPI, this)
}
