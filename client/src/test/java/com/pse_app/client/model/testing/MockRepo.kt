package com.pse_app.client.model.testing

import com.pse_app.client.model.repositories.Repo

class MockRepo : Repo() {
    override val userRepo = MockUserRepo()
    override val groupRepo = MockGroupRepo(userRepo)
    override val transactionRepo = MockTransactionRepo()
}
