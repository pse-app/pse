package com.pse_app.server.database

import com.pse_app.common.util.BigDec
import org.ktorm.dsl.cast
import org.ktorm.expression.ArgumentExpression
import org.ktorm.expression.FunctionExpression
import org.ktorm.expression.ScalarExpression
import org.ktorm.schema.ColumnDeclaring
import org.ktorm.schema.DecimalSqlType
import org.ktorm.schema.SqlType
import org.ktorm.schema.TextSqlType

/**
 * Postgres specific SQL functions.
 */
object PostgreSQL {

    fun bigDecExpression(value: BigDec): ScalarExpression<BigDec> {
        return ArgumentExpression(value.value.stripTrailingZeros(), DecimalSqlType).cast(SqlTypes.BigDecSqlType)
    }
    
    fun <T : Any> coalesce(type: SqlType<T>, args: List<ScalarExpression<out T>>): FunctionExpression<T> {
        return FunctionExpression("COALESCE", args, type)
    }
    
    fun aggregateHStore(key: ColumnDeclaring<String>, value: ColumnDeclaring<String>): FunctionExpression<Map<String, String>> {
        return FunctionExpression("hstore", listOf(
            unsafeNonNullArray(unsafeAggregateArray(key), TextSqlType),
            unsafeNonNullArray(unsafeAggregateArray(value), TextSqlType),
        ), SqlTypes.HStoreSqlType)
    }
    
    private fun <T : Any>unsafeAggregateArray(arg: ColumnDeclaring<T>): FunctionExpression<java.sql.Array> {
        return FunctionExpression("array_agg", listOf(
            arg.asExpression()
        ), SqlTypes.UnsafeArraySqlType)
    }
    
    private fun unsafeNonNullArray(arg: ColumnDeclaring<java.sql.Array>, elemType: SqlType<*>): FunctionExpression<java.sql.Array> {
        return FunctionExpression("array_remove", listOf(
            arg.asExpression(),
            ArgumentExpression(null, elemType)
        ), SqlTypes.UnsafeArraySqlType)
    }
}
