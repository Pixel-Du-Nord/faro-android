package com.pixeldunord.faro

/**
 * A request to send with [FaroClient.send].
 *
 * The device facts (app version, build, OS, model, language, time zone, screen), the recent
 * [breadcrumbs][Faro.breadcrumb] and the [identified user][Faro.identify] are added on their own.
 *
 * @property kind What it is about; one of [FaroForm.kinds].
 * @property message What the person wrote: 3 to 4,000 characters. Its first line becomes the subject
 *   unless [subject] is set.
 * @property email Where the team replies. `null` or blank uses the identified user's email.
 * @property name Optional (required if the form says so). `null` uses the identified user's name;
 *   `""` sends no name at all, even for an identified person (what [FaroForm.request] does when the
 *   form has the name turned off).
 * @property fields The answers to the custom fields, by key (see [FaroForm.customValues]).
 * @property attachments Up to [MAX_ATTACHMENTS] images (see [FaroAttachment]).
 * @property subject Overrides the subject taken from the first line (up to 120 characters).
 * @property locale The language of the emails the person will get, cut to its base (`"pt-BR"` →
 *   `"pt"`). `null` uses [FaroConfig.locale] or the device language. Pass [FaroForm.locale] when you
 *   drew a [FaroForm] ([FaroForm.request] does).
 * @property extraDevice Your own facts to travel with the device ones (for example `"plan" to "pro"`).
 *   Faro keeps 12 entries in total of up to 120 characters; the SDK's come first. Never put keys,
 *   tokens, account names or personal data here.
 */
public data class FaroRequest(
    val kind: FaroKind,
    val message: String,
    val email: String? = null,
    val name: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val attachments: List<FaroAttachment> = emptyList(),
    val subject: String? = null,
    val locale: String? = null,
    val extraDevice: Map<String, String> = emptyMap(),
) {
    public companion object {
        /** Faro takes up to three images per request. */
        public const val MAX_ATTACHMENTS: Int = 3
    }
}

/**
 * What Faro answers once the request is in.
 *
 * @property number The request number the team sees (`#128`). Show it: people quote it when they
 *   write again.
 * @property email Where the reply will go.
 * @property confirmed True when Faro trusts the address (a [signed][FaroUser.signature] user): the
 *   person gets an acknowledgement right away. Otherwise the first reply asks them to confirm it.
 */
public data class FaroTicket(
    val number: Int,
    val email: String,
    val confirmed: Boolean,
)
