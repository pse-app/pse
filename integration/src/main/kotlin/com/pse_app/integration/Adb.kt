package com.pse_app.integration

import java.io.IOException
import java.net.ServerSocket
import java.nio.charset.StandardCharsets

object Adb {
    
    fun listDevices(): List<Device> = runCommand("adb", "devices").flatMap { 
        val parts = it.split("\t")
        if (parts.size != 2) return@flatMap emptyList<Device>()
        val serial = parts[0]
        val state = parts[1]
        listOf(Device(serial, state == "device"))
    }
    
    fun reverseForward(dev: Device, devicePort: Int): Int {
        if (!dev.authorized) throw IllegalArgumentException("Device not authorized: " + dev.serial)
        val localPort = ServerSocket(0).use { it.localPort }
        runCommand("adb", "-s", dev.serial, "reverse", "tcp:${devicePort}", "tcp:${localPort}")
        return localPort
    }
    
    private fun runCommand(vararg command: String): List<String> {
        val pb: ProcessBuilder = ProcessBuilder(*command)
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
        pb.redirectError(ProcessBuilder.Redirect.INHERIT)
        pb.redirectInput(ProcessBuilder.Redirect.PIPE)
        pb.environment().putAll(System.getenv())
        val proc = pb.start()
        proc.outputStream.close()
        val lines = proc.inputReader(StandardCharsets.UTF_8).lines().toList()
        if (proc.waitFor() != 0) throw IOException("${command.joinToString(" ")} failed: ${proc.exitValue()}")
        return lines
    }
    
    data class Device(val serial: String, val authorized: Boolean)
}
