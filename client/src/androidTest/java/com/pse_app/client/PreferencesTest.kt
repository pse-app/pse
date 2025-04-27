package com.pse_app.client

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pse_app.client.model.data_layer.Session
import com.pse_app.client.model.data_layer.SessionPreferenceStore
import com.pse_app.client.persistence.Preferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@Suppress("LocalVariableName")
@RunWith(AndroidJUnit4::class)
class PreferencesTest {
    @Test
    fun testBasic() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        val prefs = Preferences(appContext)
        val key1 = "key1"
        val key2 = "key2"


        runBlocking {
            withTimeout(1000) {
                prefs.set(key1, "test-value")
                assertEquals("test-value", prefs.get(key1))

                prefs.set(key1, "Alice")
                prefs.set(key2, "Bob")
                assertEquals("Alice", prefs.get(key1))
                assertEquals("Bob", prefs.get(key2))
            }
        }
    }

    @Test
    fun testSessionStore() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        val prefs = Preferences(appContext)
        val sessionStore = SessionPreferenceStore(prefs)
        val random = Random(0)

        val atoken = "ABC"
        val rtoken = "RBC"
        val longRToken = getRandomString(256, random)

        runBlocking {
            withTimeout(1000) {
                sessionStore.setSession(null)
                assertEquals(null, sessionStore.getSession())

                sessionStore.setSession(Session(atoken, rtoken))
                assertEquals(sessionStore.getSession(), Session(atoken, rtoken))

                sessionStore.setSession(Session(atoken, longRToken))
                assertEquals(sessionStore.getSession(), Session(atoken, longRToken))
                assertEquals(sessionStore.getSession(), Session(atoken, longRToken))

                sessionStore.setSession(null)
                assertEquals(null, sessionStore.getSession())
            }
        }
    }
}
