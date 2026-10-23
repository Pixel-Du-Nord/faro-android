package com.pixeldunord.faro.internal

import android.content.Context
import android.os.Build
import java.util.Locale
import java.util.TimeZone

/** What the SDK tells Faro about the device, collected once (it does not change while running). */
internal object DeviceFacts {
    /** Faro keeps up to 12 device entries, keys of 40 characters and values of 120. */
    const val MAX_ENTRIES = 12
    const val KEY_MAX = 40
    const val VALUE_MAX = 120

    fun collect(context: Context): Map<String, String> {
        val pkg = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val build = pkg?.let {
            if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toString() else @Suppress("DEPRECATION") it.versionCode.toString()
        }
        val metrics = context.resources.displayMetrics
        val screen = "${minOf(metrics.widthPixels, metrics.heightPixels)}x${maxOf(metrics.widthPixels, metrics.heightPixels)}" +
            " ${metrics.densityDpi}dpi"
        return linkedMapOf(
            "appVersion" to pkg?.versionName.orEmpty(),
            "build" to build.orEmpty(),
            "os" to "Android",
            "osVersion" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "model" to Build.MODEL.orEmpty(),
            "manufacturer" to Build.MANUFACTURER.orEmpty().replaceFirstChar { it.titlecase(Locale.ROOT) },
            "locale" to Locale.getDefault().toLanguageTag(),
            "timezone" to TimeZone.getDefault().id,
            "screen" to screen,
        ).filterValues { it.isNotBlank() }
    }

    /**
     * The `device` object of a request: the SDK's facts first, then the breadcrumb [trail] and the
     * unsigned [userId], then the app's [extra] ones (which may override a fact with the same key),
     * clipped to Faro's limits. `app` is reserved by Faro and dropped.
     */
    fun compose(
        base: Map<String, String>,
        trail: String?,
        userId: String?,
        extra: Map<String, String>,
    ): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        fun put(key: String, value: String?) {
            val k = key.trim().take(KEY_MAX)
            val v = value?.replace(Regex("\\s+"), " ")?.trim()?.take(VALUE_MAX).orEmpty()
            if (k.isEmpty() || k == "app" || v.isEmpty()) return
            if (k !in out && out.size >= MAX_ENTRIES) return
            out[k] = v
        }
        base.forEach { (k, v) -> put(k, v) }
        put("trail", trail)
        put("userId", userId)
        extra.forEach { (k, v) -> put(k, v) }
        return out
    }
}
