package com.pixeldunord.faro.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/*
 * Line icons from Tabler Icons (MIT License, © Paweł Kuna, https://tabler.io/icons), built from
 * their path data so the library needs neither drawables nor material-icons-extended.
 */
internal object FaroIcons {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = "Faro.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (data in paths) {
            builder.addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    val Bug by lazy {
        icon(
            "Bug",
            "M9 9v-1a3 3 0 0 1 6 0v1",
            "M8 9h8a6 6 0 0 1 1 3v3a5 5 0 0 1 -10 0v-3a6 6 0 0 1 1 -3",
            "M3 13l4 0", "M17 13l4 0", "M12 20l0 -6", "M4 19l3.35 -2", "M20 19l-3.35 -2",
            "M4 7l3.75 2.4", "M20 7l-3.75 2.4",
        )
    }
    val Bulb by lazy {
        icon(
            "Bulb",
            "M3 12h1m8 -9v1m8 8h1m-15.4 -6.4l.7 .7m12.1 -.7l-.7 .7",
            "M9 16a5 5 0 1 1 6 0a3.5 3.5 0 0 0 -1 3a2 2 0 0 1 -4 0a3.5 3.5 0 0 0 -1 -3",
            "M9.7 17l4.6 0",
        )
    }
    val Question by lazy {
        icon(
            "Question",
            "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0",
            "M12 16v.01",
            "M12 13a2 2 0 0 0 .914 -3.782a1.98 1.98 0 0 0 -2.414 .483",
        )
    }
    val PhotoPlus by lazy {
        icon(
            "PhotoPlus",
            "M15 8h.01",
            "M12.5 21h-6.5a3 3 0 0 1 -3 -3v-12a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v6.5",
            "M3 16l5 -5c.928 -.893 2.072 -.893 3 0l4 4",
            "M14 14l1 -1c.67 -.644 1.45 -.824 2.182 -.54",
            "M16 19h6", "M19 16v6",
        )
    }
    val Close by lazy { icon("Close", "M18 6l-12 12", "M6 6l12 12") }
    val Back by lazy { icon("Back", "M5 12l14 0", "M5 12l6 6", "M5 12l6 -6") }
    val Send by lazy {
        icon("Send", "M10 14l11 -11", "M21 3l-6.5 18a.55 .55 0 0 1 -1 0l-3.5 -7l-7 -3.5a.55 .55 0 0 1 0 -1l18 -6.5")
    }
    val Refresh by lazy {
        icon("Refresh", "M20 11a8.1 8.1 0 0 0 -15.5 -2m-.5 -4v4h4", "M4 13a8.1 8.1 0 0 0 15.5 2m.5 4v-4h-4")
    }
    val CircleCheck by lazy { icon("CircleCheck", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M9 12l2 2l4 -4") }
    val Check by lazy { icon("Check", "M5 12l5 5l10 -10") }
    val WifiOff by lazy {
        icon(
            "WifiOff",
            "M12 18l.01 0",
            "M9.172 15.172a4 4 0 0 1 5.656 0",
            "M6.343 12.343a7.963 7.963 0 0 1 3.864 -2.14m4.163 .155a7.965 7.965 0 0 1 3.287 2",
            "M3.515 9.515a12 12 0 0 1 3.544 -2.455m3.101 -.92a12 12 0 0 1 10.325 3.374",
            "M3 3l18 18",
        )
    }
    val AlertCircle by lazy { icon("AlertCircle", "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M12 8v4", "M12 16h.01") }
    val Clock by lazy { icon("Clock", "M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0", "M12 7v5l3 3") }
    val Pencil by lazy { icon("Pencil", "M4 20h4l10.5 -10.5a2.828 2.828 0 1 0 -4 -4l-10.5 10.5v4", "M13.5 6.5l4 4") }
    val Undo by lazy { icon("Undo", "M9 14l-4 -4l4 -4", "M5 10h11a4 4 0 1 1 0 8h-1") }
    val Trash by lazy {
        icon(
            "Trash",
            "M4 7l16 0", "M10 11l0 6", "M14 11l0 6",
            "M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2 -2l1 -12",
            "M9 7v-3a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v3",
        )
    }
    val Square by lazy { icon("Square", "M4 6a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2z") }
    val Highlight by lazy {
        icon("Highlight", "M3 19h4l10.5 -10.5a2.828 2.828 0 1 0 -4 -4l-10.5 10.5v4", "M12.5 5.5l4 4", "M16 19h5")
    }
    val Shield by lazy {
        icon(
            "Shield",
            "M12 3a12 12 0 0 0 8.5 3a12 12 0 0 1 -8.5 15a12 12 0 0 1 -8.5 -15a12 12 0 0 0 8.5 -3",
            "M9 12l2 2l4 -4",
        )
    }
    val ChevronDown by lazy { icon("ChevronDown", "M6 9l6 6l6 -6") }
    val Calendar by lazy {
        icon(
            "Calendar",
            "M4 7a2 2 0 0 1 2 -2h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-12z",
            "M16 3v4", "M8 3v4", "M4 11h16",
        )
    }
    val Device by lazy {
        icon(
            "Device",
            "M6 5a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-14z",
            "M11 4h2", "M12 17v.01",
        )
    }
}
