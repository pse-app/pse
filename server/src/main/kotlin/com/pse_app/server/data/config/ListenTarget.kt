package com.pse_app.server.data.config

import com.pse_app.server.data.Magic
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

sealed interface ListenTarget {
    data class Port(val port: Int) : ListenTarget {
        override fun toString(): String = "tcp:${port}"
    }
    
    data class Socket(
        val socketFile: Path,
        val owner: String?,
        val group: String?,
        val permissions: Set<PosixFilePermission>?
    ) : ListenTarget {
        override fun toString(): String = "unix:${socketFile}"
    }
    
    companion object {

        private val UNIX_PATTERN = Regex("^unix(?:\\[(?<owner>[^:]*):(?<group>[^:]*):(?<permissions>[^:]*)])?:(?<file>.*)$")

        /**
         * Reads the string representation of a listen target and converts it to an actual listen target. 
         */
        @Magic("The numeric representation of posix file permissions is inherently magic numbers. See `man 2 chmod`.")
        fun parse(str: String): ListenTarget? = when (val match = UNIX_PATTERN.matchEntire(str)) {
            null -> str.toIntOrNull()?.let { if (it >= 0) Port(it) else null }
            else -> {
                val file = Path.of(match.groups["file"]!!.value)
                val owner = match.groups["owner"]?.value?.ifBlank { null }
                val group = match.groups["group"]?.value?.ifBlank { null }
                val permissions = match.groups["permissions"]?.value?.ifBlank { null }?.let {
                    val num = it.toIntOrNull(8) ?: return@parse null
                    PosixFilePermission.entries.filter { perm -> (num and (1 shl (8 - perm.ordinal))) != 0 }.toSet()
                }
                Socket(file, owner, group, permissions)
            }
        }
    }
}
