package com.pse_app.server.database

import com.pse_app.common.util.BigDec
import com.pse_app.server.assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.IntSqlType
import org.ktorm.schema.Table
import java.sql.ResultSet
import java.util.*

object DatabaseTest {

    @Test
    fun transient_database_connection_creates_and_initializes_a_database_correctly() {
        val db: Database = assertSuccess(DatabaseInitializer.connectTransientDatabase(Currency.getInstance("USD")), "Transient database connection failed")
        val results = assertDoesNotThrow<ResultSet>({ db.useConnection {
            val stmt = it.createStatement()
            stmt.execute("SELECT * FROM users;")
            stmt.resultSet
        } }, "Could not query users. The database seems to be improperly initialised.")
        assertFalse(results.next(), "Transient database is not empty.")
    }

    @Test
    fun big_dec_sql_type_handles_large_numbers_correctly() {
        val db: Database = assertSuccess(DatabaseInitializer.connectTransientDatabase(Currency.getInstance("USD")), "Transient database connection failed")
        assertDoesNotThrow({db.useConnection { 
            val stmt = it.createStatement()
            stmt.execute("CREATE TABLE money_test (id INTEGER, money DECIMAL NOT NULL);")
        } }, "Could not create test table.")
        val testTable = object : Table<Nothing>("money_test") {
            val id = registerColumn("id", IntSqlType).primaryKey() // NOT NULL
            val money = registerColumn("money", SqlTypes.BigDecSqlType) // NOT NULL
        }
        
        val aLotOfMoney = BigDec("5772156649015328606065120900824024310421593359399235988057672348848677267776646709369470632917467495")
        assertDoesNotThrow({ db.insert(testTable) {
            set(testTable.id, 0)
            set(testTable.money, aLotOfMoney)
        } }, "Failed to insert large amount of money.")
        val retrieved = assertDoesNotThrow<List<BigDec>>({ db.from(testTable)
            .select(testTable.money)
            .where(testTable.id eq 0)
            .map { it[testTable.money]!! }
            .toList()
        }, "Failed to re-read the inserted money.")
        assertEquals(listOf(aLotOfMoney), retrieved, "Database precision loss.")
    }
}
