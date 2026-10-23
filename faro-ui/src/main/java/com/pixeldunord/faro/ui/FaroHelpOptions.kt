package com.pixeldunord.faro.ui

import androidx.compose.runtime.Immutable
import com.pixeldunord.faro.FaroKind

/**
 * What the ready-made screen asks and shows.
 *
 * @property locale The language of the form and the screen. `null` uses `FaroConfig.locale` (or the
 *   device language).
 * @property initialKind A kind chosen in advance (for example `QUESTION` from a "Ask us" button).
 * @property prefill Values to start with, by field key (`email`, `name`, `message` or a custom key).
 *   The identified user's email and name are used on their own.
 * @property extraDevice Your own facts for the request (see `FaroRequest.extraDevice`).
 * @property showDeviceInfo Show people what travels with the request (recommended).
 * @property allowAttachments Let people attach up to three pictures.
 * @property offerScreenshot Offer the screenshot taken with [FaroScreenshot.capture] before the
 *   screen opened. It is never attached without the person choosing to.
 * @property rememberContact Remember the email and name typed last time, on this device only.
 * @property contactEmail An address shown at the bottom as a last resort (never opened by itself).
 * @property strings Your own texts; `null` picks English, Spanish or Portuguese by language.
 */
@Immutable
public data class FaroHelpOptions(
    val locale: String? = null,
    val initialKind: FaroKind? = null,
    val prefill: Map<String, String> = emptyMap(),
    val extraDevice: Map<String, String> = emptyMap(),
    val showDeviceInfo: Boolean = true,
    val allowAttachments: Boolean = true,
    val offerScreenshot: Boolean = true,
    val rememberContact: Boolean = true,
    val contactEmail: String? = null,
    val strings: FaroStrings? = null,
)
