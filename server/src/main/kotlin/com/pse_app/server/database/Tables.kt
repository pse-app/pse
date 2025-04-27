package com.pse_app.server.database

import org.ktorm.schema.Table
import org.ktorm.schema.text
import com.pse_app.server.database.SqlTypes.UuidSqlType
import com.pse_app.server.database.SqlTypes.BigDecSqlType
import com.pse_app.server.database.SqlTypes.InstantSqlTypeAsTimestampTZ
import com.pse_app.server.database.SqlTypes.UriSqlType
import com.pse_app.server.database.SqlTypes.UserIdSqlType
import com.pse_app.server.database.SqlTypes.GroupIdSqlType

/**
 * Models the PSE database tables in ktorm. 
 */
object Tables {

    object Users : Table<Nothing>("users") {
        val id = registerColumn("id", UserIdSqlType).primaryKey() // NOT NULL
        val lastLogin = registerColumn("last_login", InstantSqlTypeAsTimestampTZ) // NOT NULL
    }

    object ActiveUsers : Table<Nothing>("active_users") {
        val id = registerColumn("id", UserIdSqlType).primaryKey() // NOT NULL REFERENCES(users.id)
        val displayName = text("display_name") // NOT NULL
        val profilePictureUrl = registerColumn("profile_picture_url", UriSqlType) // MAY BE NULL
    }

    object Groups : Table<Nothing>("groups") {
        val id = registerColumn("id", GroupIdSqlType).primaryKey() // NOT NULL
        val inviteToken = text("invite_token") // NOT NULL UNIQUE
        val displayName = text("display_name") // NOT NULL
    }

    object Membership : Table<Nothing>("membership") {
        val userId = registerColumn("user_id", UserIdSqlType).primaryKey() // NOT NULL REFERENCES(users.id)
        val groupId = registerColumn("group_id", GroupIdSqlType).primaryKey() // NOT NULL REFERENCES(groups.id)
    }

    object Transactions : Table<Nothing>("transactions") {
        val id = registerColumn("id", UuidSqlType).primaryKey() // NOT NULL
        val groupId = registerColumn("group_id", GroupIdSqlType) // NOT NULL REFERENCES(groups.id)
        val name = text("name") // NOT NULL
        val comment = text("comment") // MAY BE NULL
        val timestamp = registerColumn("timestamp", InstantSqlTypeAsTimestampTZ) // NOT NULL
        val originatingUser = registerColumn("originating_user", UserIdSqlType) // NOT NULL REFERENCES(users.id)
        val expenseTotal = registerColumn("expense_total", BigDecSqlType) // MAY BE NULL
    }

    object BalanceChanges : Table<Nothing>("balance_changes") {
        val transactionId = registerColumn("transaction_id", UuidSqlType).primaryKey() // NOT NULL REFERENCES(transactions.id)
        val userId = registerColumn("user_id", UserIdSqlType).primaryKey() // NOT NULL REFERENCES(users.id)
        val amount = registerColumn("amount", BigDecSqlType) // NOT NULL
    }

    object RecentTransactionTimestamps : Table<Nothing>("recent_transaction_timestamps") {
        val groupId = registerColumn("group_id", GroupIdSqlType) // NOT NULL REFERENCES(groups.id)
        val transactionTimestamp = registerColumn("transaction_timestamp", InstantSqlTypeAsTimestampTZ) // MAY BE NULL
    }

    object RefreshTokens : Table<Nothing>("refresh_tokens") {
        val userId = registerColumn("user_id", UserIdSqlType) // NOT NULL REFERENCES(users.id)
        val token = text("token").primaryKey() // NOT NULL
    }
}
