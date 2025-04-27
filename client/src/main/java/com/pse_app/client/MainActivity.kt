@file:KoverIgnore("Android/UI Code")
package com.pse_app.client

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import androidx.navigation.compose.rememberNavController
import com.pse_app.client.annotations.KoverIgnore
import com.pse_app.client.ui.NavigationRoute
import com.pse_app.client.ui.theme.PseTheme
import com.pse_app.client.ui.view.JoinGroup
import java.net.URI

/**
 * The main and only activity of the application.
 * Different screens are implemented using navigation-compose.
 *
 * The instantiation of this activity is managed by Android and declared in
 * the AndroidManifest.xml file.
 */
@KoverIgnore("Android Code")
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val model = (application as PseApplication).model

        setContent {
            PseTheme {
                val navController = rememberNavController()
                DisposableEffect(navController) {
                    val consumer = Consumer<Intent> { intent ->
                        intent.data?.let { deepLink ->
                            navController.navigate(interpretDeepLink(deepLink.path))
                        }
                    }
                    this@MainActivity.addOnNewIntentListener(consumer)
                    onDispose {
                        this@MainActivity.removeOnNewIntentListener(consumer)
                    }
                }
                MainActivityContent(model, navController)
            }
        }
    }
}
private fun interpretDeepLink(deepLinkPath: String?): NavigationRoute {
    // Handle all deep links of unknown format as invalid
    // invite links, since that is the only format we currently
    // accept.
    deepLinkPath ?: return JoinGroup(null)
    val path = deepLinkPath.removePrefix("/").removeSuffix("/")
    return JoinGroup(findInviteToken(path))
}

private fun findInviteToken(path: String): String? {
    for (prefix in joinApiPathPrefixes) {
        if (!path.startsWith(prefix))
            continue

        val invite = path.removePrefix(prefix)

        if (invite.matches(inviteTokenPattern))
            return invite
    }
    return null
}

// Matches a sequence of alphanumeric characters
private val inviteTokenPattern = Regex("^[a-zA-Z0-9]+$")

private val joinApiPathPrefixes = BuildConfig
    .JOIN_API_URIS
    .asSequence()
    .mapNotNull { uri ->
        URI(uri).path
            ?.removePrefix("/")
            ?.let { if (it.endsWith("/")) it else "$it/" }
    }
    .distinct()
    .toList()
