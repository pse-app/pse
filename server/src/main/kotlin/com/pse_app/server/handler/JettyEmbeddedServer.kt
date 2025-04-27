package com.pse_app.server.handler

import com.pse_app.server.data.config.ListenTarget
import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.servlet.jakarta.*
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.SessionHandler
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.unixdomain.server.UnixDomainServerConnector
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission

object JettyEmbeddedServer {
    
    fun createJettyServer(module: Application.() -> Unit, connector: (Server) -> Connector): Server {
        val embeddedServer = embeddedServer(NoopEngine, configure = {}, module = module)
        val applicationGetter: () -> Application = { embeddedServer.application }
        embeddedServer.start()
        
        val server = Server()
        server.connectors = arrayOf(connector(server))

        val context = ServletContextHandler()
        context.contextPath = "/"
        context.sessionHandler = SessionHandler()
        context.setAttribute(ServletApplicationEngine.EnvironmentAttributeKey, embeddedServer.environment)
        context.setAttribute(ServletApplicationEngine.ApplicationAttributeKey, applicationGetter)
        context.addServlet(ServletApplicationEngine::class.java, "/*")
        
        server.setHandler(context)
        return server
    }
}

class NoopEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
) : BaseApplicationEngine(environment, monitor, developmentMode) {
    
    override fun start(wait: Boolean): ApplicationEngine = this
    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) = Unit
    
    companion object : ApplicationEngineFactory<NoopEngine, Configuration> {
        
        override fun configuration(configure: Configuration.() -> Unit): Configuration = Configuration().apply(configure)

        override fun create(
            environment: ApplicationEnvironment,
            monitor: Events,
            developmentMode: Boolean,
            configuration: Configuration,
            applicationProvider: () -> Application
        ): NoopEngine = NoopEngine(environment, monitor, developmentMode)
    }
}

class UnixListenServerConnector(server: Server) : UnixDomainServerConnector(server) {

    var sockeSpec: ListenTarget.Socket? = null
        set(value) {
            this.unixDomainPath = value?.socketFile
            field = value
        }

    override fun doStart() {
        super.doStart()
        when (val path = this.unixDomainPath) {
            null -> {}
            else -> setPosixPermissions(path, this.sockeSpec?.owner, this.sockeSpec?.group, this.sockeSpec?.permissions)
        }
    }

    private fun setPosixPermissions(path: Path, owner: String?, group: String?, permissions: Set<PosixFilePermission>?) {
        if (owner == null && group == null && permissions == null) return
        try {
            val attr: PosixFileAttributeView? = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
            val lookup = path.fileSystem.userPrincipalLookupService
            if (attr == null || lookup == null) throw IOException("Filesystem does not support permissions")
            if (owner != null) {
                val principal = lookup.lookupPrincipalByName(owner) // throws UserPrincipalNotFoundException
                attr.owner = principal
            }
            if (group != null) {
                val principal = lookup.lookupPrincipalByGroupName(group) // throws UserPrincipalNotFoundException
                attr.setGroup(principal)
            }
            if (permissions != null) {
                attr.setPermissions(permissions)
            }
        } catch (e: IOException) {
            throw IOException("Failed to set socket file permissions", e)
        }
    }
}
