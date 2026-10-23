package com.pixeldunord.faro

import com.pixeldunord.faro.internal.Breadcrumbs
import com.pixeldunord.faro.internal.DeviceFacts
import com.pixeldunord.faro.internal.FilePart
import com.pixeldunord.faro.internal.FormStore
import com.pixeldunord.faro.internal.HttpResponse
import com.pixeldunord.faro.internal.Multipart
import com.pixeldunord.faro.internal.Proof
import com.pixeldunord.faro.internal.ProofOfWork
import com.pixeldunord.faro.internal.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

/**
 * Talks to Faro: the form definition, the anti-spam proof of work and sending requests.
 *
 * Get it from [Faro.client] after [Faro.configure]. Every `suspend` function is main-safe: the
 * network and the hashing move to background threads on their own.
 *
 * The usual flow for your own screen:
 *
 * ```kotlin
 * // When the screen opens
 * Faro.client.prepare()                       // starts the 3-second anti-spam clock
 * val form = Faro.client.cachedForm()         // draw instantly, if there is a copy
 *     ?: Faro.client.form()                   // or fetch it
 *
 * // When the person taps Send
 * val ticket = Faro.client.send(
 *     FaroRequest(kind = FaroKind.PROBLEM, message = text, email = email),
 * )
 * show("Request #${ticket.number} sent")
 * ```
 */
public class FaroClient internal constructor(
    /** The configuration this client was made with. */
    public val config: FaroConfig,
    private val transport: Transport,
    private val store: FormStore,
    private val collectDevice: () -> Map<String, String>,
    private val breadcrumbs: Breadcrumbs,
    private val user: () -> FaroUser?,
    private val clock: () -> Long,
    private val wallClock: () -> Long,
    private val io: CoroutineDispatcher,
    private val cpu: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + cpu)
    private val lock = Any()
    private var proofJob: Deferred<Proof?>? = null
    private val sending = Mutex()

    /** The language used when none is given: [FaroConfig.locale] or the device's. */
    public val defaultLocale: String
        get() = config.locale?.takeIf { it.isNotBlank() } ?: Locale.getDefault().toLanguageTag()

    // ---- the form -------------------------------------------------------------------------------

    /**
     * The last form fetched, from disk, without touching the network: draw it instantly while
     * [form] refreshes it. `null` the very first time (or if the copy cannot be read).
     */
    public fun cachedForm(locale: String = defaultLocale): FaroForm? = runCatching {
        store.read()?.let { FormDefinition.parse(JSONObject(it)).resolve(locale, cached = true) }
    }.getOrNull()

    /**
     * Fetches the form from Faro (`GET /widget/config`) and keeps a copy on disk. If Faro cannot be
     * reached and there is a copy, returns it with [FaroForm.isCached] set.
     *
     * @param locale The language for the texts (exact, then its base language, then the form's
     *   main one). Defaults to [defaultLocale].
     * @throws FaroException.Network when offline and nothing is cached.
     * @throws FaroException.InvalidKey when the key is wrong.
     * @throws FaroException for any other error Faro answers.
     */
    public suspend fun form(locale: String = defaultLocale): FaroForm {
        val definition = try {
            withContext(io) {
                val url = "${config.apiUrl.trimEnd('/')}/widget/config?key=${encode(config.key)}" +
                    (config.app?.takeIf { it.isNotBlank() }?.let { "&app=${encode(it)}" } ?: "")
                val body = call("GET", url)
                val raw = body.optJSONObject("widget")?.optJSONObject("settings")?.optJSONObject("requestForm")
                    ?: JSONObject()
                val parsed = FormDefinition.parse(raw)
                store.write(raw.toString())
                parsed
            }
        } catch (e: FaroException) {
            val fallback = e is FaroException.Network || (e is FaroException.Server && e.status >= 500)
            if (fallback) cachedForm(locale)?.let { return it }
            throw e
        }
        return definition.resolve(locale, cached = false)
    }

    // ---- the proof of work ----------------------------------------------------------------------

    /**
     * Asks Faro for an anti-spam puzzle and solves it in the background. Call it when your form
     * opens: Faro rejects a proof used less than 3 seconds after it was asked for, and by the time
     * the person finishes typing that wait is long over. Calling it again while a proof is pending
     * or ready does nothing. [send] works without it too; it just waits the 3 seconds then.
     */
    public fun prepare() {
        currentProofJob()
    }

    private fun currentProofJob(): Deferred<Proof?> = synchronized(lock) {
        proofJob?.takeIf { it.isUsable() } ?: scope.async {
            try {
                newProof()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }.also { proofJob = it }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Deferred<Proof?>.isUsable(): Boolean = when {
        isActive -> true
        isCancelled -> false
        else -> getCompleted()?.isFresh(clock()) == true
    }

    private fun keep(proof: Proof) = synchronized(lock) { proofJob = CompletableDeferred(proof) }

    private fun discardProof() = synchronized(lock) { proofJob = null }

    /** `GET /widget/challenge` and the search for n. */
    private suspend fun newProof(): Proof {
        val requestedAt = clock()
        val puzzle = withContext(io) {
            call("GET", "${config.apiUrl.trimEnd('/')}/widget/challenge?key=${encode(config.key)}")
        }
        val salt = puzzle.optString("salt")
        val challenge = puzzle.optString("challenge")
        val max = puzzle.optInt("maxnumber", 0)
        val number = withContext(cpu) { ProofOfWork.solve(salt, challenge, max) }
            ?: throw FaroException.SpamCheck("solution")
        return Proof(salt, number, challenge, puzzle.optString("signature"), requestedAt)
    }

    // ---- sending --------------------------------------------------------------------------------

    /**
     * Sends a request (`POST /widget/requests`) with the device facts, the breadcrumbs and the
     * identified user.
     *
     * Uses the proof from [prepare] (or makes one), waits whatever is left of Faro's 3 seconds, and
     * if Faro rejects the proof it retries once with a new one. After a validation error the same
     * proof is kept for the corrected request. One request at a time: a second call waits for the
     * first.
     *
     * Before any network call it checks what can be checked locally (message length, email,
     * attachments) and throws the same errors Faro would, so a typo does not count against Faro's
     * hourly limit.
     *
     * @return The ticket with the request number.
     * @throws FaroException.InvalidMessage, FaroException.InvalidEmail, FaroException.InvalidForm,
     *   FaroException.RateLimited, FaroException.AttachmentTooLarge, FaroException.AttachmentType,
     *   FaroException.Network, FaroException.SpamCheck and the rest of [FaroException].
     */
    public suspend fun send(request: FaroRequest): FaroTicket = sending.withLock { sendLocked(request) }

    private suspend fun sendLocked(request: FaroRequest): FaroTicket {
        val prepared = precheck(request)
        var proof = currentProofJob().await() ?: run { discardProof(); newProof() }
        var retried = false
        while (true) {
            val wait = proof.remainingWait(clock())
            if (wait > 0) delay(wait)
            try {
                val ticket = withContext(io) { post(prepared, proof) }
                discardProof() // spent: the next request needs a new one
                return ticket
            } catch (e: FaroException) {
                if (e is FaroException.SpamCheck && !retried) {
                    retried = true
                    discardProof()
                    proof = newProof()
                    continue
                }
                if (e.keepsProof) keep(proof) else discardProof()
                throw e
            }
        }
    }

    /**
     * The device facts that would travel with a request right now, as Faro will store them (useful to
     * show people what is sent).
     */
    public fun deviceFacts(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val identified = user()
        val crumbs = breadcrumbs.snapshot()
        return DeviceFacts.compose(
            base = collectDevice(),
            trail = Breadcrumbs.trail(crumbs).ifEmpty { null },
            userId = identified?.id?.takeIf { !identified.isSigned },
            extra = extra,
        )
    }

    /** Cancels the background work (a proof being solved). The client can still be used. */
    public fun cancelPending() {
        synchronized(lock) {
            proofJob?.cancel()
            proofJob = null
        }
    }

    internal fun close() {
        scope.cancel()
    }

    // ---- internals ------------------------------------------------------------------------------

    /** A request with every default filled in and checked. */
    internal class Prepared(
        val request: FaroRequest,
        val email: String,
        val name: String,
        val locale: String,
        val user: FaroUser?,
    )

    private fun precheck(request: FaroRequest): Prepared {
        val message = request.message.trim()
        val length = message.codePointCount(0, message.length)
        if (length < FaroForm.MESSAGE_MIN || length > FaroForm.MESSAGE_MAX) throw FaroException.InvalidMessage()
        val identified = user()
        val email = request.email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: identified?.email?.trim()?.lowercase().orEmpty()
        val signedEmail = identified?.isSigned == true && !identified.email.isNullOrBlank()
        if (email.isEmpty() && !signedEmail) throw FaroException.InvalidEmail()
        if (email.isNotEmpty() && !isValidEmail(email)) throw FaroException.InvalidEmail()
        if (request.attachments.size > FaroRequest.MAX_ATTACHMENTS) throw FaroException.AttachmentTooLarge()
        if (request.attachments.any { it.bytes.size > FaroAttachment.MAX_BYTES }) throw FaroException.AttachmentTooLarge()
        val name = request.name?.trim()?.takeIf { it.isNotEmpty() } ?: identified?.name?.trim().orEmpty()
        val locale = request.locale?.takeIf { it.isNotBlank() } ?: defaultLocale
        return Prepared(request.copy(message = message), email, name, locale, identified)
    }

    /** The JSON of `POST /widget/requests` (the `data` part when there are images). */
    internal fun body(prepared: Prepared, proof: Proof): JSONObject {
        val request = prepared.request
        val crumbs = breadcrumbs.snapshot()
        val identified = prepared.user
        val device = DeviceFacts.compose(
            base = collectDevice(),
            trail = Breadcrumbs.trail(crumbs).ifEmpty { null },
            userId = identified?.id?.takeIf { !identified.isSigned },
            extra = request.extraDevice,
        )
        return JSONObject().apply {
            put("key", config.key)
            config.app?.takeIf { it.isNotBlank() }?.let { put("app", it) }
            put("platform", "android")
            put("kind", request.kind.wire)
            put("message", request.message)
            put("email", prepared.email)
            put("name", prepared.name)
            put("locale", prepared.locale)
            request.subject?.trim()?.takeIf { it.isNotEmpty() }?.let { put("subject", it) }
            put("fields", JSONObject(request.fields.filterValues { it.isNotBlank() }))
            put("device", JSONObject(device as Map<*, *>))
            if (identified != null && identified.isSigned) {
                put(
                    "user",
                    JSONObject()
                        .put("id", identified.id)
                        .put("email", identified.email.orEmpty())
                        .put("name", identified.name.orEmpty())
                        .put("signature", identified.signature),
                )
            }
            if (crumbs.isNotEmpty()) {
                put("context", JSONObject().put("breadcrumbs", Breadcrumbs.toJson(crumbs, wallClock())))
            }
            // The honeypot: invisible to people, always empty.
            put("website", "")
            put("proof", proof.toJson())
        }
    }

    private fun post(prepared: Prepared, proof: Proof): FaroTicket {
        val data = body(prepared, proof).toString().toByteArray(Charsets.UTF_8)
        val url = "${config.apiUrl.trimEnd('/')}/widget/requests"
        val attachments = prepared.request.attachments
        val response = if (attachments.isEmpty()) {
            call("POST", url, data, "application/json; charset=utf-8")
        } else {
            val boundary = "faro-${UUID.randomUUID()}"
            val files = attachments.map { FilePart(it.bytes, it.mimeType, it.filename) }
            call("POST", url, Multipart.build(boundary, data, files), Multipart.contentType(boundary))
        }
        val ticket = response.optJSONObject("ticket")
            ?: throw FaroException.Server("NO_TICKET", 200, "Faro's answer has no ticket")
        return FaroTicket(
            number = ticket.optInt("number", 0),
            email = ticket.optString("email").ifEmpty { prepared.email },
            confirmed = ticket.optBoolean("confirmed", false),
        )
    }

    /** Performs a call and returns the JSON; network and Faro errors come out as [FaroException]. */
    private fun call(method: String, url: String, body: ByteArray? = null, contentType: String? = null): JSONObject {
        val response: HttpResponse = try {
            transport.execute(method, url, body, contentType)
        } catch (e: IOException) {
            throw FaroException.Network(e)
        }
        if (response.status !in 200..299) {
            throw FaroException.from(response.status, response.body, response.retryAfter)
        }
        return runCatching { JSONObject(response.body) }.getOrElse {
            throw FaroException.Server("INVALID_RESPONSE", response.status, "Faro's answer is not JSON")
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
