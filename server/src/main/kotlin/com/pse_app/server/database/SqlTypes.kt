package com.pse_app.server.database

import com.pse_app.common.dto.GroupId
import com.pse_app.common.dto.UserId
import com.pse_app.common.util.BigDec
import org.ktorm.schema.SqlType
import java.net.URI
import java.net.URISyntaxException
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.*

private typealias HStore = Map<String, String>

/**
 * Provides additional [SqlType]s for our data types.
 */
object SqlTypes {

    object UuidSqlType : SqlType<UUID>(Types.OTHER, "UUID") {
        override fun doSetParameter(ps: PreparedStatement, index: Int, parameter: UUID) = ps.setObject(index, parameter)
        override fun doGetResult(rs: ResultSet, index: Int): UUID? = rs.getObject(index, UUID::class.java)
    }

    object BigDecSqlType : SqlType<BigDec>(Types.OTHER, "DECIMAL") {
        override fun doSetParameter(ps: PreparedStatement, index: Int, parameter: BigDec) = ps.setBigDecimal(index, parameter.value)
        override fun doGetResult(rs: ResultSet, index: Int): BigDec? = when (val bigDecimal = rs.getBigDecimal(index)) {
            null -> null
            else -> BigDec(bigDecimal)
        }
    }

    // ktorm has its own Instant SqlType but it uses TIMESTAMP instead of TIMESTAMPTZ
    object InstantSqlTypeAsTimestampTZ : SqlType<Instant>(Types.TIMESTAMP_WITH_TIMEZONE, "TIMESTAMPTZ") {
        override fun doSetParameter(ps: PreparedStatement, index: Int, parameter: Instant) =
            ps.setObject(index, OffsetDateTime.ofInstant(parameter, ZoneId.systemDefault()), Types.TIMESTAMP_WITH_TIMEZONE)
        override fun doGetResult(rs: ResultSet, index: Int): Instant? = rs.getTimestamp(index)?.toInstant()
    }

    object UriSqlType : SqlType<URI>(Types.VARCHAR, "TEXT") {
        override fun doSetParameter(ps: PreparedStatement, index: Int, parameter: URI) = ps.setString(index, parameter.toString())
        override fun doGetResult(rs: ResultSet, index: Int): URI? = try {
            when (val str = rs.getString(index)) {
                null -> null
                else -> URI(str)
            }
        } catch (e: URISyntaxException) {
            null
        }
    }

    object UserIdSqlType : SqlType<UserId>(Types.VARCHAR, "TEXT") {
        override fun doSetParameter(ps: PreparedStatement, index: Int, parameter: UserId) = ps.setString(index, parameter.id)
        override fun doGetResult(rs: ResultSet, index: Int): UserId? = when(val string = rs.getString(index)) {
            null, "" -> null
            else -> UserId(string)
        }
    }

    object GroupIdSqlType : SqlType<GroupId>(Types.OTHER, "UUID") {
        override fun doSetParameter(ps: PreparedStatement, index: Int, parameter: GroupId) = ps.setObject(index, parameter.id)
        override fun doGetResult(rs: ResultSet, index: Int): GroupId? = when (val uuid = rs.getObject(index, UUID::class.java)) {
            null -> null
            else -> GroupId(uuid)
        }
    }

    object UnsafeArraySqlType : SqlType<java.sql.Array>(Types.ARRAY, "ARRAY") {
        override fun doSetParameter(ps: PreparedStatement, index: Int, parameter: java.sql.Array) = ps.setArray(index, parameter)
        override fun doGetResult(rs: ResultSet, index: Int): java.sql.Array? = rs.getArray(index)
    }

    object HStoreSqlType : SqlType<HStore>(Types.OTHER, "HSTORE") {
        @Suppress("UNCHECKED_CAST")
        override fun doGetResult(rs: ResultSet, index: Int): HStore? = rs.getObject(index, Map::class.java) as HStore?
        override fun doSetParameter(ps: PreparedStatement, index: Int, parameter: HStore) = ps.setObject(index, parameter)
    }
}
