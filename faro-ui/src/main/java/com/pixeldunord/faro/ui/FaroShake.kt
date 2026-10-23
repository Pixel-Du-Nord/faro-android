package com.pixeldunord.faro.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.pixeldunord.faro.Faro
import kotlin.math.sqrt

/**
 * Opens the help screen when the person shakes the phone while [activity] is on screen, with the
 * screenshot of what they were looking at. It follows the activity's lifecycle (listens only while
 * resumed, stops when destroyed); call it once in `onCreate`.
 *
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     Faro.enableShakeToReport(this)
 * }
 * ```
 *
 * @return A handle to turn it off earlier with [FaroShake.disable].
 */
public fun Faro.enableShakeToReport(
    activity: ComponentActivity,
    options: FaroHelpOptions = FaroHelpOptions(),
    theme: FaroTheme = FaroTheme(),
): FaroShake = FaroShake(activity, options, theme).also { it.attach() }

/** Shake-to-report on one activity; see [enableShakeToReport]. */
public class FaroShake internal constructor(
    private val activity: ComponentActivity,
    private val options: FaroHelpOptions,
    private val theme: FaroTheme,
) : SensorEventListener, LifecycleEventObserver {
    private val sensors = activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var lastPeak = 0L
    private var peaks = 0
    private var lastOpen = 0L
    private var listening = false

    internal fun attach() {
        activity.lifecycle.addObserver(this)
    }

    /** Stops listening for shakes. */
    public fun disable() {
        activity.lifecycle.removeObserver(this)
        stop()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> start()
            Lifecycle.Event.ON_PAUSE -> stop()
            Lifecycle.Event.ON_DESTROY -> disable()
            else -> Unit
        }
    }

    private fun start() {
        if (listening || accelerometer == null) return
        listening = sensors?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI) == true
    }

    private fun stop() {
        if (!listening) return
        sensors?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (g < THRESHOLD_G) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPeak > WINDOW_MS) peaks = 0
        if (now - lastPeak < MIN_GAP_MS) return
        lastPeak = now
        peaks++
        // Two strong jolts within a second: a deliberate shake, not a bump.
        if (peaks >= 2 && now - lastOpen > COOLDOWN_MS && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            peaks = 0
            lastOpen = now
            Faro.breadcrumb("Shake to report")
            FaroHelpActivity.start(activity, options, theme)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int): Unit = Unit

    private companion object {
        const val THRESHOLD_G = 2.6f
        const val WINDOW_MS = 1_000L
        const val MIN_GAP_MS = 120L
        const val COOLDOWN_MS = 3_000L
    }
}
