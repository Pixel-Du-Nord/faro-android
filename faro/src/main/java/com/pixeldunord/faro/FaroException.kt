package com.pixeldunord.faro

import org.json.JSONObject

/**
 * Everything that can go wrong talking to Faro. Tell the cases apart by type (or by [code], Faro's
 * `errorCode`), never by the message.
 *
 * ```kotlin
 * try {
 *     val ticket = Faro.client.send(request)
 * } catch (e: FaroException.InvalidForm) {
 *     showError(field = e.field)
 * } catch (e: FaroException.RateLimited) {
 *     showNotice("Too many requests, try again later")
 * } catch (e: FaroException.Network) {
 *     showRetry()
 * } catch (e: FaroException) {
 *     showNotice("Something went wrong")
 * }
 * ```
 *
 * @property code Faro's `errorCode` (`INVALID_FORM`, `RATE_LIMITED`…), or `NETWORK`.
 * @property status The HTTP status, or 0 when the server was not reached.
 */
public sealed class FaroException(
    public val code: String,
    public val status: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Faro was not reached: no connection, DNS, timeout. Safe to retry. */
    public class Network(cause: Throwable? = null) :
        FaroException(NETWORK, 0, "Faro could not be reached" + (cause?.message?.let { ": $it" } ?: ""), cause)

    /**
     * A field is missing or not valid for the form.
     *
     * @property field The field key: [FaroForm.KIND], [FaroForm.NAME] or a custom key.
     */
    public class InvalidForm(public val field: String) :
        FaroException("INVALID_FORM", 400, "Invalid form field: $field")

    /** The email is missing or not an address. */
    public class InvalidEmail : FaroException("INVALID_EMAIL_FORMAT", 400, "Invalid or missing email")

    /** The message is shorter than 3 or longer than 4,000 characters (or the kind is unknown). */
    public class InvalidMessage : FaroException("INVALID_TEXT", 400, "The message must have 3 to 4000 characters")

    /**
     * Too many requests: Faro takes 6 per hour from one IP and 3 per hour from one email.
     *
     * @property retryAfterSeconds When to try again, if Faro said so.
     */
    public class RateLimited(public val retryAfterSeconds: Int? = null) :
        FaroException("RATE_LIMITED", 429, "Too many requests")

    /**
     * The anti-spam proof of work was rejected. [FaroClient.send] already retried once with a new
     * one, so this is rare (a very wrong device clock does not matter; a blocked network might).
     *
     * @property reason Faro's reason: `missing`, `signature`, `solution`, `key`, `expired`,
     *   `too_fast` or `reused`.
     */
    public class SpamCheck(public val reason: String? = null) :
        FaroException("SPAM_CHECK_FAILED", 400, "Proof of work rejected" + (reason?.let { ": $it" } ?: ""))

    /** More than three images, or one over 3 MB. */
    public class AttachmentTooLarge : FaroException("FILE_TOO_LARGE", 413, "Attachments: 3 images of 3 MB at most")

    /** An attachment is not PNG, JPEG, GIF or WebP. */
    public class AttachmentType : FaroException("FILE_TYPE_NOT_ALLOWED", 415, "Attachments must be PNG, JPEG, GIF or WebP")

    /** The organization does not have the Requests module. */
    public class ModuleRequired : FaroException("MODULE_REQUIRED", 403, "The organization does not have Requests")

    /** The key does not exist or its organization is not active. Check [FaroConfig.key]. */
    public class InvalidKey : FaroException("WIDGET_NOT_FOUND", 404, "Unknown widget key")

    /** The organization's subscription is not active. */
    public class SubscriptionInactive : FaroException("SUBSCRIPTION_INACTIVE", 403, "Subscription inactive")

    /** Anything else Faro answered (server errors, an unreadable response…). */
    public class Server(code: String, status: Int, message: String) : FaroException(code, status, message)

    internal companion object {
        const val NETWORK = "NETWORK"

        /**
         * The typed error for a Faro error response. [body] is the JSON body
         * (`{errorCode, errorNumber, message, field?, reason?}`), [retryAfter] the `Retry-After` header.
         * `field` and `reason` come apart since Faro 2026-10-23; older answers carry them in the
         * message (`"field: where"`, `"proof: expired"`).
         */
        fun from(status: Int, body: String?, retryAfter: String? = null): FaroException {
            val json = runCatching { JSONObject(body.orEmpty()) }.getOrNull()
            val code = json?.optString("errorCode")?.takeIf { it.isNotEmpty() } ?: "HTTP_$status"
            val message = json?.optString("message").orEmpty()
            return when (code) {
                "INVALID_FORM" -> InvalidForm(
                    json?.optString("field")?.takeIf { it.isNotEmpty() }
                        ?: message.substringAfter("field:", "").trim()
                )
                "INVALID_EMAIL_FORMAT" -> InvalidEmail()
                "INVALID_TEXT" -> InvalidMessage()
                "RATE_LIMITED" -> RateLimited(retryAfter?.trim()?.toIntOrNull())
                "SPAM_CHECK_FAILED" -> SpamCheck(
                    json?.optString("reason")?.takeIf { it.isNotEmpty() }
                        ?: message.substringAfter("proof:", "").trim().ifEmpty { null }
                )
                "FILE_TOO_LARGE" -> AttachmentTooLarge()
                "FILE_TYPE_NOT_ALLOWED" -> AttachmentType()
                "MODULE_REQUIRED" -> ModuleRequired()
                "WIDGET_NOT_FOUND" -> InvalidKey()
                "SUBSCRIPTION_INACTIVE" -> SubscriptionInactive()
                else -> if (status == 429) RateLimited(retryAfter?.trim()?.toIntOrNull())
                else Server(code, status, message.ifEmpty { code })
            }
        }
    }
}

/**
 * Whether the proof of work survives this error. Faro checks the proof LAST, after everything else,
 * and spends it only when the request gets in, so after a validation error (or a request that never
 * arrived) the same proof can be reused to send the corrected request without waiting again.
 */
internal val FaroException.keepsProof: Boolean
    get() = when (this) {
        is FaroException.SpamCheck -> false
        is FaroException.Server -> status in 400..499 && code != "HTTP_$status"
        else -> true
    }
