package com.pse_app.server

import com.pse_app.server.data.Result
import com.pse_app.server.data.andThen
import com.pse_app.server.data.config.Config
import com.pse_app.server.data.config.ConfigKey
import com.pse_app.server.data.map
import com.pse_app.server.data.void
import com.pse_app.server.database.DatabaseInitializer
import com.pse_app.server.executor.RequestDispatcher
import com.pse_app.server.group.GroupManager
import com.pse_app.server.handler.RequestHandler
import com.pse_app.server.handler.RequestRouter
import com.pse_app.server.transaction.TransactionManager
import com.pse_app.server.user.UserManager
import joptsimple.OptionParser
import joptsimple.util.PathConverter
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.io.IoBuilder
import sun.misc.Signal
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.jar.Manifest
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val logger = LogManager.getLogger("Main")

    val bootstrap: Result<RequestHandler> = bootstrap(args) {
        System.setOut(IoBuilder.forLogger(LogManager.getLogger("system.out")).setLevel(Level.INFO).buildPrintStream())
        System.setErr(IoBuilder.forLogger(LogManager.getLogger("system.err")).setLevel(Level.ERROR).buildPrintStream())
        return@bootstrap logger
    }

    when (bootstrap) {
        is Result.Success -> {
            val handler = bootstrap.value
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info("Received shutdown, stopping server")
                handler.stop()
                logger.info("Server stopped.")
            })
            try {
                logger.info("Bootstrap complete, launching server on {}", bootstrap.value.listen)
                handler.run()
                try {
                    Signal.handle(Signal("INT")) { handler.stop() }
                    Signal.handle(Signal("TERM")) { handler.stop() }
                } catch (_: Exception) {
                    // Non-portable code. This is required, so we can exit with exit code 0 when gracefully stopped
                    // after bootstrap.
                }
                handler.join()
                exitProcess(0) // Terminate auxiliary threads and run shutdown hooks.
            } catch (e: Exception) {
                logger.fatal("A critical error has been detected while running the server.", e)
                exitProcess(1) // If the server fails to start, its thread keeps running unnoticed otherwise.
            }
        }
        is Result.Error -> {
            logger.fatal("There was an error bootstrapping the server: {}", bootstrap.message)
            exitProcess(1)
        }
    }
}

fun bootstrap(args: Array<String>, setupLogger: () -> Logger): Result<RequestHandler> {
    val version = Config::class.java.getResourceAsStream("/META-INF/MANIFEST.MF").use { when (it) {
        null -> "<unknown>"
        else -> Manifest(it).mainAttributes?.getValue("Implementation-Version") ?: "<unknown>"
    }}
    
    val options = OptionParser(false)
    val specHelp = options.accepts("help", "Show this help.").forHelp()
    val specVersion = options.accepts("version", "Show version and exit.").forHelp()
    val specTransientDb = options.accepts("transient", "Use docker to start up a transient database.")
    val specDbInit = options.accepts("database-init", "Run the provided initialisation file against the database.")
        .withRequiredArg().withValuesConvertedBy(PathConverter())
    val specConfig = options.nonOptions("The config file to load.").withValuesConvertedBy(PathConverter())
    val set = options.parse(*args)
    if (set.has(specVersion)) {
        println(version)
        exitProcess(0)
    } else if (set.has(specHelp) || set.valuesOf(specConfig).isEmpty()) {
        options.printHelpOn(System.err)
        exitProcess(if (set.has(specHelp)) 0 else 1)
    }
    
    val logger = setupLogger()
    logger.info("Starting PSE server version {}", version)

    val configPath = set.valueOf(specConfig)
    val dbInitSql = when (set.has(specDbInit)) {
        false -> null
        true -> {
            val dbInitPath = set.valueOf(specDbInit)
            if (!Files.exists(dbInitPath)) {
                logger.fatal("Database initialisation file not found: {}", dbInitPath)
                exitProcess(1)
            }
            Files.readString(dbInitPath, StandardCharsets.UTF_8)
        }
    }

    logger.info("Bootstrapping server from {}", configPath)
    return Config.load(configPath).andThen { config ->
        when (set.has(specTransientDb)) {
            true  -> DatabaseInitializer.connectTransientDatabase(config[ConfigKey.Currency])
            false -> DatabaseInitializer.connectDatabase(config)
        }.void { database ->
            when (dbInitSql) {
                null -> Result.Success(Unit)
                else -> {
                    logger.info("Running provided database initialisation.")
                    DatabaseInitializer.executeSql(database, dbInitSql)
                }
            }
        }.map { database ->
            logger.debug("Database connection established, instantiating managers.")
            val userManager = UserManager(database)
            val groupManager = GroupManager(database)
            val transactionManager = TransactionManager(config, database)
            val requestExecutor = RequestDispatcher(userManager, groupManager, transactionManager)
            RequestRouter(config, requestExecutor, userManager.authenticator, groupManager.authorizer)
        }
    }
}
