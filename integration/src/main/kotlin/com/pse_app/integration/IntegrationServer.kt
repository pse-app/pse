package com.pse_app.integration

import com.pse_app.server.bootstrap
import com.pse_app.server.data.Result
import com.pse_app.server.data.config.ConfigKey
import com.pse_app.server.handler.RequestHandler
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.MarkerManager
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.DefaultHandler
import org.eclipse.jetty.util.Callback
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.Properties

class IntegrationServer(private val dev: Adb.Device, private val options: Array<String>, private val controlPort: Int, private val openIdPort: Int) {

    private val logger: Logger = LogManager.getLogger("adb-" + dev.serial)
    private var appServer: RequestHandler? = null
    
    private val openIdServer: Server = runServer(openIdPort, object : DefaultHandler() {
        override fun handle(request: Request, response: Response, callback: Callback): Boolean {
            response.status = HttpStatus.OK_200
            response.write(true, StandardCharsets.UTF_8.encode("""{"jwks_uri":"http://invalid/","issuer":"http://invalid/"}"""), callback)
            return true
        }
    })

    private val controlServer: Server = run {
        val server = runServer(controlPort, object : DefaultHandler() {
            override fun handle(request: Request, response: Response, callback: Callback): Boolean {
                val reqPath = "/" + (Request.getPathInContext(request) ?: "").dropWhile { it == '/' }.dropLastWhile { it == '/' }
                when (reqPath) {
                    "/restart" -> {
                        try {
                            this@run.restartAppServer()
                        } catch (e: Exception) {
                            logger.error("Failed to restart application server.", e)
                        }
                        response.status = HttpStatus.OK_200
                        response.write(true, ByteBuffer.wrap(byteArrayOf()), callback)
                        return true
                    }
                    "/start" -> {
                        logTest("Starting", request)
                        response.status = HttpStatus.OK_200
                        response.write(true, ByteBuffer.wrap(byteArrayOf()), callback)
                        return true
                    }
                    "/stop" -> {
                        logTest("Stopping", request)
                        response.status = HttpStatus.OK_200
                        response.write(true, ByteBuffer.wrap(byteArrayOf()), callback)
                        return true
                    }
                    else -> return super.handle(request, response, callback)
                }
            }
        })
        this.restartAppServer()
        server
    }
    
    private fun runServer(port: Int, handler: Handler): Server {
        val server = Server()

        val connector = ServerConnector(server)
        connector.port = port
        server.connectors = arrayOf(connector)

        server.setHandler(handler)
        server.start()
        return server
    }
    
    private fun logTest(action: String, request: Request) {
        val testName: String? = Request.getParameters(request)?.get("test")?.value
        if (testName != null) logger.info(instrumentedTestLogMarker, "${action} test: {}", testName)
    }
    
    fun stop() {
        this.controlServer.stop()
        this.openIdServer.stop()
    }

    private fun restartAppServer() {
        val appPort = Adb.reverseForward(dev, DEVICE_APP_PORT)
        
        val config = Properties()
        config.setProperty(ConfigKey.Listen.name, appPort.toString())
        config.setProperty(ConfigKey.PublicURI.name, "http://localhost:5000/")
        config.setProperty(ConfigKey.OidcDiscoveryURI.name, "http://localhost:${openIdPort}/")
        config.setProperty(ConfigKey.OidcClientId.name, "SNOWMAN")
        config.setProperty(ConfigKey.DatabaseUser.name, "EmmyNoether")
        config.setProperty(ConfigKey.DatabasePassword.name, "IAmVerySecretIndeed")
        val path = Files.createTempFile("pse-integration-", ".properties")
        Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use {
            config.store(it, null)
        }
        val args = options + arrayOf(path.toAbsolutePath().normalize().toString())
        
        appServer = when (val bootstrap = bootstrap(args) { logger }) {
            is Result.Success -> {
                bootstrap.value.run()
                bootstrap.value
            }
            is Result.Error -> throw IllegalStateException("Failed to start server: " + bootstrap.message)
        }
    }

    companion object {
        val instrumentedTestLogMarker: Marker = MarkerManager.getMarker("PSE_INSTRUMENTED_TEST_LOG")
    }
}
