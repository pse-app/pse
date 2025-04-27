package com.pse_app.client.model.data_layer

import com.pse_app.client.persistence.Preferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class SessionPreferencesStoreTest {
    lateinit var prefs: Preferences
    lateinit var store: SessionPreferenceStore

    @Before
    fun setup() {
        prefs = mockk<Preferences>()
        store = SessionPreferenceStore(prefs)
    }

    @Test
    fun getSessionTest() = runTest {
        coEvery { prefs.get(ACCESS_TOKEN_KEY) } returns "ACCESS"
        coEvery { prefs.get(REFRESH_TOKEN_KEY) } returns "REFRESH"
        assertEquals(Session("ACCESS", "REFRESH"), store.getSession())
    }

    @Test
    fun setSessionTest() = runTest {
        coEvery { prefs.set(ACCESS_TOKEN_KEY, "ACCESS") } returns Unit
        coEvery { prefs.set(REFRESH_TOKEN_KEY, "REFRESH") } returns Unit
        store.setSession(Session("ACCESS", "REFRESH"))
        coVerify { prefs.set(ACCESS_TOKEN_KEY, "ACCESS") }
        coVerify { prefs.set(REFRESH_TOKEN_KEY, "REFRESH") }
    }
}
