package com.pixeldunord.faro

/**
 * How the SDK reaches your Faro organization.
 *
 * ```kotlin
 * Faro.configure(this, FaroConfig(key = "wk_…", app = "my-app"))
 * ```
 *
 * @property key The public widget key (`wk_…`) from **Widget → Installation** in the Faro panel.
 *   It is public by design: it only lets people file requests, never read anything.
 * @property app The slug of the application in Faro (**Organization → Applications**). Requests are
 *   then grouped by app in the panel and, if the app has its own request form, that form is the one
 *   drawn and validated. `null` uses the organization's form.
 * @property locale The language your app is shown in (`"es"`, `"en"`, `"pt-BR"`…). It picks the form
 *   texts and the language of the emails the person receives. `null` uses the device language. If your
 *   app lets people pick a language, pass that one, not the phone's.
 * @property apiUrl Faro's API. Only change it for a self-hosted or staging Faro.
 */
public data class FaroConfig(
    val key: String,
    val app: String? = null,
    val locale: String? = null,
    val apiUrl: String = DEFAULT_API_URL,
) {
    init {
        require(key.isNotBlank()) { "FaroConfig.key is empty: use the wk_… key from the Faro panel." }
        require(apiUrl.startsWith("https://") || apiUrl.startsWith("http://")) {
            "FaroConfig.apiUrl must be an http(s) URL."
        }
    }

    public companion object {
        /** Faro's production API. */
        public const val DEFAULT_API_URL: String = "https://api-faro.pixeldunord.com"
    }
}

/**
 * Who is using the app, as far as support is concerned. Set it with [Faro.identify].
 *
 * - [email] and [name] prefill the form, so a signed-in person does not type them again.
 * - [id] is your own user id. Sent alone it travels with the device facts (`userId`) so your team can
 *   look the person up.
 * - With [signature] (an HMAC-SHA256 of [id] made by **your server** with the organization's identity
 *   secret, see Faro's "Signed identity" guide) Faro trusts the person: the email counts as confirmed,
 *   they get an acknowledgement and the request is linked to their contact. Never ship the secret
 *   inside the app.
 */
public data class FaroUser(
    val id: String? = null,
    val email: String? = null,
    val name: String? = null,
    val signature: String? = null,
) {
    /** True when Faro can verify this person ([id] and [signature] are both set). */
    val isSigned: Boolean get() = !id.isNullOrBlank() && !signature.isNullOrBlank()
}
