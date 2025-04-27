package com.pse_app.client.integration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pse_app.client.model.data_layer.RemoteClient
import com.pse_app.client.model.data_layer.Session
import com.pse_app.client.model.data_layer.SessionMemoryStore
import com.pse_app.client.model.data_layer.SessionPreferenceStore
import com.pse_app.client.model.facade.Model
import com.pse_app.client.model.repositories.remote.RemoteRepo
import com.pse_app.client.persistence.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * This class contains the system tests.
 * Each method tests a specific function of the App.
 */
@ExperimentalTestApi
@RunWith(AndroidJUnit4::class)
class ModelTests {
    lateinit var model: Model
    lateinit var model2: Model
    lateinit var user2Model: Model

    @get:Rule(order = 0)
    val notifyServerAboutTest = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            URL("http://localhost:5001/start?test=" + URLEncoder.encode(description.methodName, StandardCharsets.UTF_8.name())).openStream().close()
            try {
                base.evaluate()
            } finally {
                URL("http://localhost:5001/stop?test=" + URLEncoder.encode(description.methodName, StandardCharsets.UTF_8.name())).openStream().close()
            }
        }
    } }

    @get:Rule(order = 1)
    val setup = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            URL("http://localhost:5001/restart").openStream().close() // Restart the application server
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext
            val sessionStore = SessionPreferenceStore(Preferences(appContext))
            val altSessionStore = SessionMemoryStore()
            runBlocking {
                sessionStore.setSession(Session(expiredAccessToken, "eyJ1c2VySWQiOiJ1c2VyMSIsInNlY3JldCI6InNlY3JldDEifQ=="))
                altSessionStore.setSession(Session(expiredAccessToken, "eyJ1c2VySWQiOiJ1c2VyMiIsInNlY3JldCI6InNlY3JldDIifQ=="))
            }
            val client = RemoteClient("http://localhost:5000", 1000, sessionStore)
            val prefs = Preferences(appContext)
            model = Model(RemoteRepo(client), client, prefs)
            model2 = Model(RemoteRepo(client), client, prefs)

            val altClient = RemoteClient("http://localhost:5000", 1000, altSessionStore)
            user2Model = Model(RemoteRepo(altClient), altClient, prefs)
            runBlocking {
                model.refreshActiveUser()
                model2.refreshActiveUser()
                user2Model.refreshActiveUser()
            }
            base.evaluate()
        }
    } }

    private val expiredAccessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJjb20ucHNlX2FwcC5zZXJ2ZXIiLCJpc3MiOiJjb20ucHNlX2FwcC5zZXJ2ZXIiLCJzdWIiOiI2ZWYxMmMzYy0xNTg3LTQ0MDQtOGZmZC1mNTMyN2M5OTVjMTEiLCJleHAiOjE3MzkzNTk5NjR9.1RYd5uaouCrEaLnJS9BRDE2b6IlbTlHMDmPo8p-Yeqo"

    @Test
    fun isAuthenticatedTest() = runTest(timeout = 1.seconds) {
        assertEquals(model.isAuthenticated(), true)
    }

    @Test
    fun logoutTest() = runTest(timeout = 1.seconds) {
        model.logout()
        assertEquals(model.isAuthenticated(), false)
    }

    @Test
    fun setNameTest() = runTest(timeout = 1.seconds) {
        val user = model.observeActiveUser().first()!!
        assertEquals("User1", user.displayName.first())
        model.setUserDisplayName("NAME")
        assertEquals("NAME", user.displayName.first())
    }

    @Test
    fun refreshTest() = runTest(timeout = 1.seconds) {
        val user = model.observeActiveUser().first()!!
        model2.setUserDisplayName("NAME")

        model.refreshActiveUser()
        assertEquals("NAME", user.displayName.first())
    }

    @Test
    fun refreshUsersTest() = runTest(timeout = 1.seconds) {
        val user = model.observeActiveUser().first()!!
        val user2 = user2Model.observeActiveUser().first()!!

        model.refreshUsers()
        assertEquals(
            user2.displayName.first()!!,
            model.getUserById(user2.id).displayName.first()!!
        )
        user2Model.setUserDisplayName("NAME")
        model.refreshUsers()
        assertEquals(
            user2.displayName.first()!!,
            model.getUserById(user2.id).displayName.first()!!
        )
    }

    @Test
    fun currencyTest() = runTest(timeout = 1.seconds) {
        val currency = model.getCurrency()
        assertEquals("USD", currency.currencyCode)
    }
}
