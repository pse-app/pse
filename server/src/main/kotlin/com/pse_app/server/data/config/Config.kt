package com.pse_app.server.data.config

import com.pse_app.server.data.Result
import com.pse_app.server.data.lift
import com.pse_app.server.data.map
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/**
 * A config file that maps each [ConfigKey] to a value.
 */
class Config private constructor (private val values: Map<ConfigKey<Any>, Any>) {

    /**
     * Retrieves a value from the config file.
     */
    operator fun <T : Any> get(key: ConfigKey<T>): T = key.type.javaObjectType.cast(this.values[key])
    
    companion object {

        /**
         * Attempts to load a [Config] from the provided path.
         */
        fun load(path: Path): Result<Config> {
            val properties = Properties()
            try {
                Files.newBufferedReader(path, StandardCharsets.UTF_8).use { properties.load(it) }
            } catch (e: IOException) {
                Result.Error("Failed to read config: " + e.message)
            } catch (e: IllegalArgumentException) {
                Result.Error("Failed to read config: " + e.message)
            }
            val valueMap: Result<Map<ConfigKey<Any>, Any>> = ConfigKey.list().map { key ->
                when (val stringValue = properties.getProperty(key.name)) {
                    null -> key.default()
                    else -> key.read(stringValue.trim())
                }.map { key to it }
            }.lift().map { it.toMap() }
            return valueMap.map { Config(it) }
        }
    }
}
