package com.pixeldunord.faro.sample

import android.app.Application
import android.content.Context
import com.pixeldunord.faro.Faro
import com.pixeldunord.faro.FaroConfig
import com.pixeldunord.faro.FaroUser

/**
 * Configures Faro once, at start. The key and the app come from `gradle.properties`
 * (`faro.sample.key`, `faro.sample.app`) and can be changed in the app's Settings.
 */
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SampleSettings.load(this).apply(this)
        Faro.breadcrumb("App started")
    }
}

/** What the sample lets you change, kept in SharedPreferences. */
data class SampleSettings(
    val key: String,
    val app: String,
    val locale: String,
    val identify: Boolean,
    val email: String,
    val name: String,
) {
    /** (Re)configures Faro with these settings. */
    fun apply(context: Context) {
        Faro.configure(
            context,
            FaroConfig(
                key = key.trim(),
                app = app.trim().ifEmpty { null },
                locale = locale.trim().ifEmpty { null },
            ),
        )
        Faro.identify(
            if (identify) FaroUser(id = "sample-user-1", email = email.ifBlank { null }, name = name.ifBlank { null }) else null,
        )
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("key", key).putString("app", app).putString("locale", locale)
            .putBoolean("identify", identify).putString("email", email).putString("name", name)
            .apply()
    }

    companion object {
        private const val PREFS = "sample"

        fun load(context: Context): SampleSettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return SampleSettings(
                key = p.getString("key", null) ?: BuildConfig.FARO_KEY,
                app = p.getString("app", null) ?: BuildConfig.FARO_APP,
                locale = p.getString("locale", null).orEmpty(),
                identify = p.getBoolean("identify", false),
                email = p.getString("email", null).orEmpty(),
                name = p.getString("name", null).orEmpty(),
            )
        }
    }
}
