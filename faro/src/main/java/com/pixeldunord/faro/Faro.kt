package com.pixeldunord.faro

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.pixeldunord.faro.internal.Breadcrumbs
import com.pixeldunord.faro.internal.DeviceFacts
import com.pixeldunord.faro.internal.FileFormStore
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
     * Sets up the SDK. Calling it again replaces the configuration (for example when the person
     * switches the app's language).
     *
     * @return The new [client].
     */
    @JvmStatic
    public fun configure(context: Context, config: FaroConfig): FaroClient {
        val app = context.applicationContext
        val pkg = runCatching {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(app.packageName, 0)
        }.getOrNull()
        val userAgent = "FaroAndroid/$VERSION (Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
            "${app.packageName}/${pkg?.versionName ?: "0"}"
        val directory = File(app.noBackupFilesDir, "faro")
        val client = FaroClient(
            config = config,
            transport = UrlConnectionTransport(userAgent),
            store = FileFormStore(directory, config.key, config.app),
            collectDevice = { DeviceFacts.collect(app) },
            breadcrumbs = trail,
            user = { identified },
            clock = SystemClock::elapsedRealtime,
            wallClock = System::currentTimeMillis,
            io = Dispatchers.IO,
            cpu = Dispatchers.Default,
        )
        synchronized(this) {
            current?.close()
            current = client
        }
        return client
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
