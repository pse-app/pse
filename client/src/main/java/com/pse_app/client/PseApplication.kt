@file:KoverIgnore("Android Code")
package com.pse_app.client

import android.app.Application
import com.pse_app.client.annotations.KoverIgnore
import com.pse_app.client.model.data_layer.RemoteClient
import com.pse_app.client.model.data_layer.SessionPreferenceStore
import com.pse_app.client.model.data_layer.getHTTPClient
import com.pse_app.client.model.facade.Model
import com.pse_app.client.model.facade.ModelFacade
import com.pse_app.client.model.repositories.remote.RemoteRepo
import com.pse_app.client.persistence.Preferences

private const val REQUEST_TIMEOUT = 8000L

/**
 * The main application class of the program.
 *
 * The instantiation of this class is managed by Android and declared in
 * the AndroidManifest.xml file.
 */
@KoverIgnore("Android code")
class PseApplication: Application() {

    /**
     * The centralized facade for accessing the Model.
     *
     * Note that accessing this property before [onCreate] runs will result in an
     * [UninitializedPropertyAccessException].
     */
    lateinit var model: ModelFacade

    override fun onCreate() {
        super.onCreate()
        
        val isRunningInstrumentedTest = try {
            Class.forName("androidx.test.runner.AndroidJUnitRunner")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        
        val prefs = Preferences(applicationContext)
        val client = RemoteClient(
            if (isRunningInstrumentedTest) INSTRUMENTED_TEST_SERVER else BuildConfig.PUBLIC_API,
            REQUEST_TIMEOUT,
            SessionPreferenceStore(prefs),
            getHTTPClient(REQUEST_TIMEOUT, BuildConfig.DEBUG)
        )
        val repo = RemoteRepo(client)
        model = Model(repo, client, prefs)
    }
    
    companion object {
        const val INSTRUMENTED_TEST_SERVER = "http://localhost:5000/"
    }
}
