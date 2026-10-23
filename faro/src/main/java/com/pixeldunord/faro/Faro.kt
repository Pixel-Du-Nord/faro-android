package com.pixeldunord.faro

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.pixeldunord.faro.internal.Breadcrumbs
import com.pixeldunord.faro.internal.DeviceFacts
import com.pixeldunord.faro.internal.FileFormStore
import com.pixeldunord.faro.internal.FormStore
import com.pixeldunord.faro.internal.UrlConnectionTransport
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Faro, Pixel du Nord's support desk, in your Android app.
 *
 * Configure it once (in `Application.onCreate` or before the first use):
 *
 * ```kotlin
 * Faro.configure(this, FaroConfig(key = "wk_…", app = "my-app"))
 * ```
 *
 * Then either open the ready-made screen from `faro-ui`, or build your own with [client].
 */
public object Faro {
    /** The SDK version. */
    public val VERSION: String = BuildConfig.FARO_VERSION

    @Volatile
    private var current: FaroClient? = null

    @Volatile
    private var identified: FaroUser? = null

    private val trail = Breadcrumbs()

    /**
     * Sets up the SDK. Call it once, early (in `Application.onCreate`).
     *
     * Calling it again (for example with another key in a debug menu) updates the SAME client: every
     * [FaroClient] you already hold, such as one kept by a `ViewModel`, keeps working with the new
     * configuration. A proof of work made for another key or API is dropped. The language does not
     * need a new configuration: pass it with each call ([FaroClient.form], [FaroRequest.locale]).
     *
     * @return The [client] (always the same instance).
     */
    @JvmStatic
    public fun configure(context: Context, config: FaroConfig): FaroClient {
        val app = context.applicationContext
        val store = FileFormStore(File(app.noBackupFilesDir, "faro"), config.key, config.app)
        return install(config, store) {
            val pkg = runCatching {
                @Suppress("DEPRECATION")
                app.packageManager.getPackageInfo(app.packageName, 0)
            }.getOrNull()
            val userAgent = "FaroAndroid/$VERSION (Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
                "${app.packageName}/${pkg?.versionName ?: "0"}"
            FaroClient(
                config = config,
                transport = UrlConnectionTransport(userAgent),
                store = store,
                collectDevice = { DeviceFacts.collect(app) },
                breadcrumbs = trail,
                user = { identified },
                clock = SystemClock::elapsedRealtime,
                wallClock = System::currentTimeMillis,
                io = Dispatchers.IO,
                cpu = Dispatchers.Default,
            )
        }
    }

    /** The first call builds the client; later ones update it in place, so nobody holds a stale one. */
    internal fun install(config: FaroConfig, store: FormStore, build: () -> FaroClient): FaroClient =
        synchronized(this) {
            current?.also { it.update(config, store) } ?: build().also { current = it }
        }

    /** Forgets the client (tests only). */
    internal fun reset() {
        synchronized(this) { current = null }
    }

    /** True once [configure] was called. */
    @JvmStatic
    public val isConfigured: Boolean get() = current != null

    /**
     * The client for forms and requests.
     *
     * @throws IllegalStateException if [configure] was not called.
     */
    @JvmStatic
    public val client: FaroClient
        get() = current ?: throw IllegalStateException(
            "Faro is not configured: call Faro.configure(context, FaroConfig(key = \"wk_…\")) first.",
        )

    /**
     * Tells Faro who is using the app (see [FaroUser]); `null` forgets them (on sign-out). Kept in
     * memory only: call it again on every launch, after sign-in.
     */
    @JvmStatic
    public fun identify(user: FaroUser?) {
        identified = user
    }

    /** The person set with [identify], or `null`. */
    @JvmStatic
    public val user: FaroUser? get() = identified

    /**
     * Records an action of the app ("Opened settings", "Tapped Pay"). The last 20, cut to 60
     * characters each, travel with the next request so the team sees what led to it. Keep them
     * short and free of personal data: screen names and button names, not what people typed.
     */
    @JvmStatic
    public fun breadcrumb(text: String) {
        trail.add(text)
    }

    /** Forgets the recorded actions (for example on sign-out). */
    @JvmStatic
    public fun clearBreadcrumbs() {
        trail.clear()
    }

    /** The recorded actions, oldest first. */
    @JvmStatic
    public val breadcrumbs: List<String> get() = trail.snapshot().map { it.text }
}
