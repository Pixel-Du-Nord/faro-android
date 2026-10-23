package com.pixeldunord.faro.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * The ready-made help screen as its own activity: one line from anywhere, no navigation to wire.
 *
 * ```kotlin
 * FaroHelpActivity.start(this)                       // from an Activity: offers its screenshot
 * FaroHelpActivity.start(this, FaroHelpOptions(initialKind = FaroKind.QUESTION), FaroTheme(accent = brand))
 * ```
 *
 * When it closes after a request got in, the result is [Activity.RESULT_OK] with the number in
 * [EXTRA_TICKET_NUMBER] (if you started it for a result).
 */
public class FaroHelpActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Options and theme are not parcelable (colors, fonts): they wait in memory. After the
        // process was killed and restored, the screen comes back with the defaults.
        val launch = pending ?: Launch(FaroHelpOptions(), FaroTheme())
        // System bar icons that read on the screen, also when the theme forces light or dark.
        when (launch.theme.darkMode) {
            FaroDarkMode.SYSTEM -> enableEdgeToEdge()
            FaroDarkMode.LIGHT -> enableEdgeToEdge(
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            )
            FaroDarkMode.DARK -> enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        }
        super.onCreate(savedInstanceState)
        setContent {
            FaroHelpScreen(
                onClose = ::finish,
                options = launch.options,
                theme = launch.theme,
                onSent = { ticket ->
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_TICKET_NUMBER, ticket.number))
                },
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) pending = null
    }

    private class Launch(val options: FaroHelpOptions, val theme: FaroTheme)

    public companion object {
        /** The request number, as an `Int`, in the result of a finished activity. */
        public const val EXTRA_TICKET_NUMBER: String = "com.pixeldunord.faro.TICKET_NUMBER"

        @Volatile
        private var pending: Launch? = null

        /**
         * Opens the help screen. If [context] is an activity and [FaroHelpOptions.offerScreenshot]
         * is on, its screen is captured first and offered as a picture (never attached on its own).
         */
        @JvmStatic
        @JvmOverloads
        public fun start(
            context: Context,
            options: FaroHelpOptions = FaroHelpOptions(),
            theme: FaroTheme = FaroTheme(),
        ) {
            pending = Launch(options, theme)
            val intent = intent(context)
            if (context is Activity && options.offerScreenshot && options.allowAttachments) {
                FaroScreenshot.capture(context) { context.startActivity(intent) }
            } else {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }

        /**
         * The intent that opens the screen with [options] and [theme], for
         * `registerForActivityResult(StartActivityForResult())`. Capture the screenshot yourself
         * with [FaroScreenshot.capture] before launching it.
         */
        @JvmStatic
        @JvmOverloads
        public fun intent(
            context: Context,
            options: FaroHelpOptions? = null,
            theme: FaroTheme? = null,
        ): Intent {
            if (options != null || theme != null) {
                pending = Launch(options ?: FaroHelpOptions(), theme ?: FaroTheme())
            }
            return Intent(context, FaroHelpActivity::class.java)
        }
    }
}
