package com.pse_app.server.handler

import com.pse_app.server.data.config.Config
import com.pse_app.server.data.config.ConfigKey
import com.pse_app.server.data.config.ListenTarget
import com.pse_app.server.executor.RequestExecutor
import com.pse_app.server.group.Authorizer
import com.pse_app.server.user.Authenticator

/**
 * The Request Handler interface.
 */
abstract class RequestHandler(
    val config: Config,
    val requestExecutor: RequestExecutor,
    val authenticator: Authenticator,
    val authorizer: Authorizer
) {

    val listen: ListenTarget get() = config[ConfigKey.Listen]
    
    /**
     * Starts an HTTP server that handles requests.
     */
    abstract fun run()

    /**
     * Stops the HTTP server handling the requests.
     */
    abstract fun stop()

    /**
     * Blocks the current thread until the HTTP server is stopped.
     */
    abstract fun join()
}
