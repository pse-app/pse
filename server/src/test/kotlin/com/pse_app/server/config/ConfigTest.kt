package com.pse_app.server.config

import com.pse_app.server.assertions.assertError
import com.pse_app.server.assertions.assertSuccess
import com.pse_app.server.data.ErrorCategory
import com.pse_app.server.data.config.Config
import com.pse_app.server.data.config.ConfigKey
import com.pse_app.server.data.config.ListenTarget
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import kotlin.test.assertEquals

class ConfigTest {

    @Test
    fun load_loads_the_config_file_and_correctly_parses_all_values() {
        assertSuccess(Config.load(Path.of("src/test/resources/com/pse_app/server/config/test_config.properties").toAbsolutePath())) {
            assertEquals(ListenTarget.Socket(Path.of("unix.sock"), "root", "root", setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)), it[ConfigKey.Listen])
            assertEquals(URI.create("https://pse_app.com/hello"), it[ConfigKey.PublicURI])
            assertEquals("/show", it[ConfigKey.PublicRedirect])
            assertEquals(URI.create("https://pse_app.com/oidc"), it[ConfigKey.OidcDiscoveryURI])
            assertEquals("here_is_a_client_id", it[ConfigKey.OidcClientId])
            assertEquals("127.0.0.1", it[ConfigKey.DatabaseHost])
            assertEquals(5433, it[ConfigKey.DatabasePort])
            assertEquals("pse", it[ConfigKey.DatabaseName])
            assertEquals("root", it[ConfigKey.DatabaseUser])
            assertEquals("hunter2", it[ConfigKey.DatabasePassword])
            assertEquals(Currency.getInstance("EUR"), it[ConfigKey.Currency])
        }
    }
    
    @Test
    fun load_loads_the_correct_default_values() {
        assertSuccess(Config.load(Path.of("src/test/resources/com/pse_app/server/config/defaulted_config.properties").toAbsolutePath())) {
            assertEquals(ListenTarget.Port(80), it[ConfigKey.Listen])
            assertEquals("", it[ConfigKey.PublicRedirect])
            assertEquals("localhost", it[ConfigKey.DatabaseHost])
            assertEquals(5432, it[ConfigKey.DatabasePort])
            assertEquals("postgres", it[ConfigKey.DatabaseName])
            assertEquals(Currency.getInstance("USD"), it[ConfigKey.Currency])
        }
    }

    @Test
    fun load_fails_on_invalid_config_file() {
        assertError(ErrorCategory.Generic, Config.load(Path.of("server/src/test/resources/com/pse_app/server/config/invalid_config.properties")))
    }

    @Test
    fun load_fails_on_incomplete_config_file() {
        assertError(ErrorCategory.Generic, Config.load(Path.of("server/src/test/resources/com/pse_app/server/config/incomplete_config.properties")))
    }
    
    @Test
    fun listen_target_parse_parses_correct_listen_targets() {
        assertNull(ListenTarget.parse(""))
        assertNull(ListenTarget.parse("-2"))
        assertEquals(ListenTarget.Port(443), ListenTarget.parse("443"))
        assertEquals(ListenTarget.Socket(Path.of("sock"), null, null, null), ListenTarget.parse("unix:sock"))
        assertEquals(ListenTarget.Socket(Path.of("/run/sock"), null, null, null), ListenTarget.parse("unix:/run/sock"))
        assertEquals(ListenTarget.Socket(Path.of("/run/sock"), null, null, null), ListenTarget.parse("unix[::]:/run/sock"))
        assertEquals(ListenTarget.Socket(Path.of("/run/sock"), "root", null, null), ListenTarget.parse("unix[root::]:/run/sock"))
        assertEquals(ListenTarget.Socket(Path.of("/run/sock"), null, "adm", null), ListenTarget.parse("unix[:adm:]:/run/sock"))
        assertEquals(ListenTarget.Socket(Path.of("/run/sock"), null, null, setOf()), ListenTarget.parse("unix[::0]:/run/sock"))
        assertEquals(ListenTarget.Socket(Path.of("/run/sock"), null, null, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)), ListenTarget.parse("unix[::600]:/run/sock"))
        assertEquals(ListenTarget.Socket(Path.of("/run/sock"), "root", "adm", setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OTHERS_EXECUTE)), ListenTarget.parse("unix[root:adm:0401]:/run/sock"))
    }
}
