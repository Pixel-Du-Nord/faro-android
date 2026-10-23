package com.pixeldunord.faro.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * A picture of the screen the person was on, offered on the help screen so they can cover private
 * details and attach it.
 *
 * Take it right BEFORE opening the help screen (once it is open, the old screen is gone).
 * [FaroHelpActivity.start] and shake-to-report already do this; call it yourself when you show
 * [FaroHelpScreen] in your own navigation:
 *
 * ```kotlin
 * FaroScreenshot.capture(activity) { navController.navigate("help") }
 * ```
 *
 * Privacy: nothing is attached unless the person chooses to, and a window marked
 * `FLAG_SECURE` (banking, passwords…) is never captured.
 */
public object FaroScreenshot {
    @Volatile
    private var pending: Bitmap? = null

    /**
     * Captures [activity]'s window and keeps it for the next help screen, then calls [then] on the
     * main thread (with the bitmap, or `null` if it could not be taken).
     */
    @JvmStatic
    @JvmOverloads
    public fun capture(activity: Activity, then: ((Bitmap?) -> Unit)? = null) {
        val window = activity.window
        val view = window?.decorView
        if (window == null || view == null || view.width <= 0 || view.height <= 0 ||
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        ) {
            pending = null
            then?.invoke(null)
            return
        }
        val bitmap = runCatching { Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888) }.getOrNull()
        if (bitmap == null) {
            then?.invoke(null)
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            // PixelCopy reads what is really on screen, hardware layers and video included.
            try {
                PixelCopy.request(window, bitmap, { result ->
                    val shot = if (result == PixelCopy.SUCCESS) bitmap else drawn(activity)
                    pending = shot
                    then?.invoke(shot)
                }, Handler(Looper.getMainLooper()))
            } catch (e: IllegalArgumentException) {
                val shot = drawn(activity)
                pending = shot
                then?.invoke(shot)
            }
        } else {
            val shot = drawn(activity)
            pending = shot
            then?.invoke(shot)
        }
    }

    /** [capture] as a suspend function: the bitmap, or `null`. */
    public suspend fun captureNow(activity: Activity): Bitmap? = suspendCancellableCoroutine { cont ->
        capture(activity) { cont.resume(it) }
    }

    /** Offers your own picture instead (or `null` for none) on the next help screen. */
    @JvmStatic
    public fun set(bitmap: Bitmap?) {
        pending = bitmap
    }

    /** Forgets the captured picture. */
    @JvmStatic
    public fun clear() {
        pending = null
    }

    /** Hands the picture to the help screen (once). */
    internal fun take(): Bitmap? = pending.also { pending = null }

    /** Fallback: the view hierarchy drawn into a bitmap (misses hardware-only content). */
    private fun drawn(activity: Activity): Bitmap? = runCatching {
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap
    }.getOrNull()
}
