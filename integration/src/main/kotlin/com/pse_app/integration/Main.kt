package com.pse_app.integration

import org.apache.logging.log4j.LogManager
import sun.misc.Signal
import java.net.ServerSocket
import kotlin.system.exitProcess

const val DEVICE_APP_PORT = 5000
const val DEVICE_CONTROL_PORT = 5001

fun main(args: Array<String>) {
    val logger = LogManager.getLogger("integration")
    Thread.currentThread().setUncaughtExceptionHandler { _, ex ->
        ex.printStackTrace()
        exitProcess(1)
    }

    try {
        Signal.handle(Signal("INT")) { exitProcess(0) }
        Signal.handle(Signal("TERM")) { exitProcess(0) }
        Signal.handle(Signal("HUP")) { exitProcess(0) }
    } catch (_: Exception) {
        // Gracefully exit on SIGHUP, so we die when the gradle (daemon) process dies. This also runs
        // shutdown hooks and deletes the docker container.
    }
    
    for (dev in Adb.listDevices()) {
        if (!dev.authorized) {
            logger.error("Skipping unauthorized device: " + dev.serial)
            continue
        }
        val controlPort = Adb.reverseForward(dev, DEVICE_CONTROL_PORT)
        val openIdPort = ServerSocket(0).use { it.localPort }
        logger.info("Launching integration server for {}, local ports: control={} openid={}", dev.serial, controlPort, openIdPort)
        val server = IntegrationServer(dev, args, controlPort, openIdPort)
        Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
    }
}
