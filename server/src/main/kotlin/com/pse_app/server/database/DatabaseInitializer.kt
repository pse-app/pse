package com.pse_app.server.database

import com.pse_app.server.data.*
import com.pse_app.server.data.config.Config
import com.pse_app.server.data.config.ConfigKey
import org.apache.logging.log4j.*
import org.ktorm.database.Database
import org.ktorm.database.use
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.SQLException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Provides functions for connecting to database servers.
 */
object DatabaseInitializer {
    
    private const val SCHEMA_VERSION: Int = 2
    
    private const val DOCKER_CONNECTION_ATTEMPTS_MAIN_DB: Int = 20
    private const val DOCKER_CONNECTION_ATTEMPTS_INSTANCE_DB: Int = 5
    private const val DOCKER_CONNECTION_ATTEMPT_INTERVAL_MILLIS: Long = 1000

    private val logger: Logger = LogManager.getLogger()
    private val dockerLogger: Logger = LogManager.getLogger("docker")
    private val transientDpMarker: Marker = MarkerManager.getMarker("PSE_TRANSIENT_DB")
    
    /**
     * Connects to a database as defined in the provided configuration file.
     */
    fun connectDatabase(config: Config): Result<Database> {
        val dbServer = config[ConfigKey.DatabaseHost]
        val dbPort = config[ConfigKey.DatabasePort]
        val dbName = config[ConfigKey.DatabaseName]
        val dbUser = config[ConfigKey.DatabaseUser]
        val dbPassword = config[ConfigKey.DatabasePassword]
        val db: Result<Database> = Result.tryTo<_, SQLException> {
            logger.info("Connecting to a PostgreSQL database {} at {} on port {} as user {}.", dbName, dbServer, dbPort, dbUser)
            Database.connect("jdbc:postgresql://${dbServer}:${dbPort}/${dbName}", user = dbUser, password = dbPassword)
        }
        return db.mapError { "SQL Connection error: $it" }.void { initialiseDatabase(config[ConfigKey.Currency], it) }
    }

    private var transientPostgres: TransientPostgresInstance? = null

    /**
     * Starts a local transient database that is removed when the process dies. This requires docker to be installed
     * on the machine and the current user must have privileges to run docker commands.
     */
    fun connectTransientDatabase(currency: Currency): Result<Database> = setupTransientPostgresInstance()
        .andThen { it.newDatabase() }
        .void{ initialiseDatabase(currency, it) }

    /**
     * Executes the provided SQL against the provided database.
     */
    fun executeSql(db: Database, sql: String): Result<Unit> = useSql(db) { con -> con.createStatement().use { it.execute(sql) } }

    private fun initializeSchema(currency: Currency, db: Database, schemaSqlTemplate: String): Result<Unit> = useSql(db) { con ->
        val empty = con.metaData.getTables(db.name, "public", null, null).use { !it.next() }
        if (empty) {
            val schemaSql = schemaSqlTemplate.replace("?{currency_fraction_digits}", currency.effectiveFractionDigits.toString())
            con.createStatement().use { it.execute(schemaSql) }
        }
        
        val detectedSchemaVersion = con.createStatement().use { 
            val rs = it.executeQuery("SELECT schema_version();")
            if (rs.next()) rs.getInt(1) else 1
        }
        if (detectedSchemaVersion != SCHEMA_VERSION) {
            throw SQLException("Invalid database schema: ${detectedSchemaVersion} (expected ${SCHEMA_VERSION})")
        }
        
        val detectedFractionDigits = con.createStatement().use { 
            val rs = it.executeQuery("SELECT currency_fraction_digits();")
            if (rs.next()) rs.getInt(1) else -1
        }
        if (detectedFractionDigits != currency.effectiveFractionDigits) {
            throw SQLException(
                "Invalid fraction digits in database: ${detectedFractionDigits} (expected ${currency.effectiveFractionDigits})"
            )
        }
    }
    
    private fun <T> useSql(db: Database, action: (Connection) -> T): Result<T> = try {
        db.useConnection { con ->
            val prevAutoCommit = con.autoCommit
            try {
                con.autoCommit = false
                val value = action(con)
                con.commit()
                Result.Success(value)
            } finally {
                con.autoCommit = prevAutoCommit
            }
        }
    } catch (e: SQLException) {
        Result.Error("SQL error: " + e.message)
    }

    private fun initialiseDatabase(currency: Currency, db: Database): Result<Unit> {
        return loadSchema().andThen { initializeSchema(currency, db, it) }
    }
    
    private fun loadSchema(): Result<String> {
        return DatabaseInitializer::class.java.getResourceAsStream("schema.sql")
            .errIfNull("Database schema source not found.")
            .useMap { String(it.readAllBytes(), StandardCharsets.UTF_8) }
    }

    private fun setupTransientPostgresInstance(): Result<TransientPostgresInstance> = when (val instance = transientPostgres) {
        null -> hasDockerImage()
            .andThen { if (!it) pullDockerImage() else Result.Success(Unit) }
            .andThen { findAvailablePort() }
            .tap { port -> logger.info("Starting ephemeral PostgreSQL Docker container on Port {}.", port) }
            .void { port -> startDockerContainer(port) }
            .andThen { port ->
                waitForDockerConnection(port, "main_db", DOCKER_CONNECTION_ATTEMPTS_MAIN_DB)
                    .map { con -> TransientPostgresInstance(port, con) }
            }
            .tap { logger.info("Connected to ephemeral PostgreSQL Docker container on 127.0.0.1:{}/main_db", it.port) }
            .map { transientPostgres = it; it }
        else -> Result.Success(instance)
    }
    
    private fun hasDockerImage(): Result<Boolean> = Result.tryTo<_, Exception> {
        logger.debug("Checking for the PostgreSQL Docker image.")
        val pb = ProcessBuilder("docker", "image", "inspect", "postgres:15")
        pb.redirectInput(ProcessBuilder.Redirect.PIPE)
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
        pb.redirectError(ProcessBuilder.Redirect.PIPE)
        val process: Process = pb.start()
        process.outputStream.close()
        copyProcessOutputToDockerLog("image inspect", process, outLevel = Level.TRACE, errLevel = Level.TRACE)
        when (process.waitFor()) {
            0 -> true
            else -> false
        }
    }
    
    private fun pullDockerImage(): Result<Unit> = Result.tryTo<_, Exception> {
        logger.warn("PostgreSQL Docker image not present, pulling postgres:15")
        val pb = ProcessBuilder("docker", "pull", "postgres:15")
        pb.redirectInput(ProcessBuilder.Redirect.PIPE)
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
        pb.redirectError(ProcessBuilder.Redirect.PIPE)
        val process: Process = pb.start()
        process.outputStream.close()
        copyProcessOutputToDockerLog("pull", process, outLevel = Level.DEBUG, errLevel = Level.WARN)
        when (val code = process.waitFor()) {
            0 -> Unit
            else -> error("docker pull exited with exit code $code")
        }
    }.mapError { "Failed to pull docker container: $it" }

    private fun startDockerContainer(port: Int): Result<Unit> = Result.tryTo<_, Exception> {
        val pb = ProcessBuilder(
           "docker", "run", "--rm", "-i", "-e", "POSTGRES_USER=test_user", "-e", "POSTGRES_PASSWORD=hunter2",
           "-e", "POSTGRES_DB=main_db", "-p", "127.0.0.1:${port}:5432", "postgres:15"
        )
        pb.redirectInput(ProcessBuilder.Redirect.PIPE)
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
        pb.redirectError(ProcessBuilder.Redirect.PIPE)
        val process: Process = pb.start()
        process.outputStream.close()
        val isShuttingDown = copyProcessOutputToDockerLog("run", process, outLevel = Level.DEBUG, errLevel = Level.WARN)
        Runtime.getRuntime().addShutdownHook(Thread { isShuttingDown.set(true); process.destroy() })
    }.mapError { "Failed to start docker container: $it" }

    private fun waitForDockerConnection(port: Int, databaseName: String, attempts: Int): Result<Database> {
        return try {
            Result.Success(Database.connect(
                "jdbc:postgresql://127.0.0.1:${port}/${databaseName}",
                user = "test_user",
                password = "hunter2"
            ))
        } catch (e: SQLException) {
            if (attempts <= 1) {
                Result.Error("Docker SQL connection error: " + e.message)
            } else {
                Thread.sleep(DOCKER_CONNECTION_ATTEMPT_INTERVAL_MILLIS)
                waitForDockerConnection(port, databaseName, attempts - 1)
            }
        }
    }

    private fun findAvailablePort(): Result<Int> = Result.tryTo<_, IOException> {
        ServerSocket(0).use { it.localPort }
    }.mapError { "Failed to find available port: $it" }
    
    private fun copyProcessOutputToDockerLog(name: String, process: Process, outLevel: Level, errLevel: Level): AtomicBoolean {
        val isShuttingDown = AtomicBoolean(false)
        
        fun copyStream(stream: InputStream, level: Level): Unit = Thread {
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use {
                try {
                    while (process.isAlive) when (val line = it.readLine()) {
                        null -> return@Thread
                        else -> dockerLogger.log(level, "{}: {}", name, line)
                    }
                } catch (e: IOException) {
                    if (!isShuttingDown.get()) throw e
                }
            }
        }.start()
        
        copyStream(process.inputStream, outLevel)
        copyStream(process.errorStream, errLevel)
        
        return isShuttingDown
    }
    
    private class TransientPostgresInstance(val port: Int, val db: Database) {
        
        fun newDatabase(): Result<Database> {
            val id: String = "db_" + UUID.randomUUID().toString().replace("-", "")
            logger.info(transientDpMarker, "Connecting to transient PostgreSQL database at 127.0.0.1:{}/{}", this.port, id)
            return Result.tryTo<Unit, Exception> {
                db.useConnection { con ->
                    val stmt = con.createStatement()
                    stmt.execute("CREATE DATABASE ${id};")
                }
            }.mapError { "Failed to create database in transient PostgreSQL instance: $it" }
                .andThen { waitForDockerConnection(port, id, DOCKER_CONNECTION_ATTEMPTS_INSTANCE_DB) }
        }
    }
}
