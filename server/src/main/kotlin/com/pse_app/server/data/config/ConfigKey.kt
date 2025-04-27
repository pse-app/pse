package com.pse_app.server.data.config

import com.pse_app.server.data.Magic
import com.pse_app.server.data.Result
import java.net.URI
import kotlin.reflect.KClass

private typealias JCurrency = java.util.Currency

/**
 * A key for a value in the config file.
 *
 * @param type The type of the property.
 * @param name The name of the property in the config file.
 * @param defaultValue An optional default value.
 */
@Magic("The default values in config keys are sufficiently described by the config key that contains them.")
sealed class ConfigKey<out T : Any>(
    val type: KClass<out T>,
    val name: String,
    private val defaultValue: T?,
    private val readFunc: (String) -> T?
) {

    /**
     * Attempts to read the provided string into a value appropriate for this [ConfigKey].
     */
    fun read(stringValue: String): Result<T> = when (val value = this.readFunc(stringValue)) {
        null -> Result.Error("Invalid value in config: " + this.name)
        else -> Result.Success(value)
    }

    /**
     * Retrieves the default value of this [ConfigKey] if available, otherwise returns an error stating the absence
     * of this config key.
     */
    fun default(): Result<T> = when (this.defaultValue) {
        null -> Result.Error("Missing value in config: " + this.name)
        else -> Result.Success(this.defaultValue)
    }

    data object Listen : ConfigKey<ListenTarget>(ListenTarget::class, "listen", ListenTarget.Port(80), ListenTarget::parse)
    data object PublicURI : ConfigKey<URI>(URI::class, "public_uri", null, URI::create)
    data object PublicRedirect : ConfigKey<String>(String::class, "public_redirect", "", { it })
    data object OidcDiscoveryURI : ConfigKey<URI>(URI::class, "oidc_discovery_uri", null, URI::create)
    data object OidcClientId : ConfigKey<String>(String::class, "oidc_client_id", null, {  it })
    data object DatabaseHost : ConfigKey<String>(String::class, "database_host", "localhost", {  it })
    data object DatabasePort : ConfigKey<Int>(Int::class, "database_port", 5432, String::toIntOrNull)
    data object DatabaseName : ConfigKey<String>(String::class, "database_name", "postgres", { it })
    data object DatabaseUser : ConfigKey<String>(String::class, "database_user", null, { it })
    data object DatabasePassword : ConfigKey<String>(String::class, "database_password", null, { it })
    data object Currency : ConfigKey<JCurrency>(JCurrency::class, "currency", JCurrency.getInstance("USD"), { readCurrency(it) })
    
    companion object {

        /**
         * Lists all known config keys.
         */
        fun list(): List<ConfigKey<Any>> = listOf(
            Listen,
            PublicURI,
            PublicRedirect,
            OidcDiscoveryURI,
            OidcClientId,
            DatabaseHost,
            DatabasePort,
            DatabaseName,
            DatabaseUser,
            DatabasePassword,
            Currency
        )
        
        private fun readCurrency(iso4217: String): java.util.Currency? = try {
            java.util.Currency.getInstance(iso4217)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
