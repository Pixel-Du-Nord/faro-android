package com.pixeldunord.faro.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixeldunord.faro.Faro
import com.pixeldunord.faro.FaroAttachment
import com.pixeldunord.faro.FaroException
import com.pixeldunord.faro.FaroForm
import com.pixeldunord.faro.FaroIssue
import com.pixeldunord.faro.FaroKind
import com.pixeldunord.faro.FaroRequest
import com.pixeldunord.faro.FaroTicket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the screen is. */
internal enum class Phase { EDITING, SENDING, SENT }

/** How the form got here. */
internal enum class Link { LOADING, LIVE, CACHED, OFFLINE, UNAVAILABLE }

/** Notices not tied to a field. */
internal enum class Notice { CHECK_FIELDS, OFFLINE, RATE_LIMITED, FAILED, IMAGE, UNAVAILABLE }

/** What is being drawn on in the cover editor. */
internal sealed interface EditTarget {
    val bitmap: Bitmap

    /** The screenshot offered before attaching it. */
    class Suggestion(override val bitmap: Bitmap) : EditTarget

    /** A picture already attached. */
    class Attached(val id: String, override val bitmap: Bitmap) : EditTarget
}

internal data class HelpState(
    val form: FaroForm? = null,
    val link: Link = Link.LOADING,
    val kind: FaroKind? = null,
    /** Everything typed, by field key; a checked box is "true". */
    val values: Map<String, String> = emptyMap(),
    val attachments: List<FaroAttachment> = emptyList(),
    /** The screenshot of the previous screen, until it is attached or dismissed. */
    val suggestion: Bitmap? = null,
    val issues: Map<String, FaroIssue> = emptyMap(),
    val notice: Notice? = null,
    val phase: Phase = Phase.EDITING,
    val ticket: FaroTicket? = null,
    val editing: EditTarget? = null,
    /** Bumped on every failed validation so the screen scrolls to the first issue again. */
    val issueSignal: Int = 0,
)

/**
 * The brain of the ready-made screen. Lives as long as the screen's owner (survives rotation).
 * On start it prepares the proof of work and shows the cached form while it fetches the fresh one,
 * so everything is ready by the time the person finishes typing.
 */
internal class HelpModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("faro_ui", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(HelpState())
    val state: StateFlow<HelpState> = _state.asStateFlow()

    private var options = FaroHelpOptions()
    private var started = false
    private var sending: Job? = null

    private val locale: String
        get() = options.locale?.takeIf { it.isNotBlank() } ?: Faro.client.defaultLocale

    fun start(options: FaroHelpOptions) {
        if (started) return
        started = true
        this.options = options
        if (!Faro.isConfigured) {
            Log.e(TAG, "Faro.configure(context, FaroConfig(...)) was not called before opening the help screen.")
            _state.value = HelpState(link = Link.UNAVAILABLE)
            return
        }
        val client = Faro.client
        client.prepare()
        val cached = client.cachedForm(locale)
        _state.value = HelpState(
            form = cached,
            link = if (cached != null) Link.CACHED else Link.LOADING,
            kind = options.initialKind,
            values = initialValues(),
            suggestion = if (options.offerScreenshot && options.allowAttachments) FaroScreenshot.take() else null,
        )
        load()
    }

    private fun initialValues(): Map<String, String> = buildMap {
        if (options.rememberContact) {
            prefs.getString(KEY_EMAIL, null)?.let { put(FaroForm.EMAIL, it) }
            prefs.getString(KEY_NAME, null)?.let { put(FaroForm.NAME, it) }
        }
        Faro.user?.let { user ->
            user.email?.takeIf { it.isNotBlank() }?.let { put(FaroForm.EMAIL, it) }
            user.name?.takeIf { it.isNotBlank() }?.let { put(FaroForm.NAME, it) }
        }
        putAll(options.prefill)
    }

    /** Fetches (or re-fetches) the form. */
    fun load() {
        if (!Faro.isConfigured) return
        _state.update { if (it.form == null) it.copy(link = Link.LOADING) else it }
        viewModelScope.launch {
            try {
                val fresh = Faro.client.form(locale)
                _state.update { it.copy(form = fresh, link = if (fresh.isCached) Link.CACHED else Link.LIVE) }
                // The puzzle may have failed offline: ask again now that there is a network.
                Faro.client.prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FaroException) {
                _state.update {
                    when {
                        it.form != null -> it.copy(link = if (e is FaroException.Network) Link.CACHED else it.link)
                        e is FaroException.Network || (e is FaroException.Server && e.status >= 500) ->
                            it.copy(link = Link.OFFLINE)
                        else -> {
                            Log.w(TAG, "Faro refused the form: ${e.code} ${e.message}")
                            it.copy(link = Link.UNAVAILABLE)
                        }
                    }
                }
            }
        }
    }

    fun chooseKind(kind: FaroKind) = _state.update {
        it.copy(kind = kind, issues = it.issues - FaroForm.KIND, notice = it.notice.clearedBy(it.issues - FaroForm.KIND))
    }

    fun set(key: String, value: String) = _state.update {
        val issues = it.issues - key
        it.copy(values = it.values + (key to value), issues = issues, notice = it.notice.clearedBy(issues))
    }

    private fun Notice?.clearedBy(issues: Map<String, FaroIssue>): Notice? =
        if (this == Notice.CHECK_FIELDS && issues.isEmpty()) null else this

    // ---- pictures ---------------------------------------------------------------------------

    fun attach(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val room = FaroRequest.MAX_ATTACHMENTS - _state.value.attachments.size
            val added = uris.take(room.coerceAtLeast(0)).mapNotNull { FaroAttachment.fromUri(getApplication(), it) }
            _state.update {
                it.copy(
                    attachments = (it.attachments + added).take(FaroRequest.MAX_ATTACHMENTS),
                    notice = if (added.size < uris.size.coerceAtMost(room)) Notice.IMAGE else it.notice.takeIf { n -> n != Notice.IMAGE },
                )
            }
        }
    }

    fun remove(id: String) = _state.update {
        it.copy(attachments = it.attachments.filterNot { a -> a.id == id }, notice = it.notice.takeIf { n -> n != Notice.IMAGE })
    }

    /** Attaches the offered screenshot as is. */
    fun attachSuggestion() {
        val bitmap = _state.value.suggestion ?: return
        addBitmap(bitmap) { it.copy(suggestion = null) }
    }

    fun dismissSuggestion() = _state.update { it.copy(suggestion = null) }

    fun editSuggestion() = _state.update { s -> s.suggestion?.let { s.copy(editing = EditTarget.Suggestion(it)) } ?: s }

    fun editAttachment(id: String) {
        val attachment = _state.value.attachments.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(attachment.bytes, 0, attachment.bytes.size)
            } ?: return@launch
            _state.update { it.copy(editing = EditTarget.Attached(id, bitmap)) }
        }
    }

    fun cancelEdit() = _state.update { it.copy(editing = null) }

    /** The cover editor is done: [result] replaces what was being edited. */
    fun finishEdit(result: Bitmap) {
        when (val target = _state.value.editing ?: return) {
            is EditTarget.Suggestion -> addBitmap(result) { it.copy(suggestion = null, editing = null) }
            is EditTarget.Attached -> viewModelScope.launch {
                val encoded = withContext(Dispatchers.Default) { FaroAttachment.fromBitmap(result) }
                _state.update { s ->
                    s.copy(
                        editing = null,
                        attachments = if (encoded == null) s.attachments
                        else s.attachments.map { if (it.id == target.id) encoded else it },
                    )
                }
            }
        }
    }

    private fun addBitmap(bitmap: Bitmap, then: (HelpState) -> HelpState) {
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.Default) { FaroAttachment.fromBitmap(bitmap) }
            _state.update { s ->
                val next = if (encoded != null && s.attachments.size < FaroRequest.MAX_ATTACHMENTS) {
                    s.copy(attachments = s.attachments + encoded)
                } else {
                    s.copy(notice = Notice.IMAGE)
                }
                then(next)
            }
        }
    }

    // ---- sending ----------------------------------------------------------------------------

    fun send() {
        if (sending?.isActive == true) return
        val s = _state.value
        val form = s.form ?: return
        val found = form.validate(s.kind, s.values)
        if (found.isNotEmpty()) {
            _state.update { it.copy(issues = found, notice = Notice.CHECK_FIELDS, issueSignal = it.issueSignal + 1) }
            return
        }
        val hasName = form.field(FaroForm.NAME) != null
        val request = FaroRequest(
            kind = s.kind!!,
            message = s.values[FaroForm.MESSAGE].orEmpty(),
            email = s.values[FaroForm.EMAIL]?.trim(),
            name = if (hasName) s.values[FaroForm.NAME]?.trim() else null,
            fields = form.customValues(s.values),
            attachments = s.attachments,
            locale = form.locale,
            extraDevice = options.extraDevice,
        )
        _state.update { it.copy(phase = Phase.SENDING, notice = null, issues = emptyMap()) }
        sending = viewModelScope.launch {
            try {
                val ticket = Faro.client.send(request)
                if (options.rememberContact) remember(request.email.orEmpty(), request.name.orEmpty())
                _state.update { it.copy(phase = Phase.SENT, ticket = ticket) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FaroException) {
                _state.update { it.copy(phase = Phase.EDITING).withError(e) }
            } catch (e: Exception) {
                // Nothing unexpected may crash the host app: it becomes "try again".
                Log.w(TAG, "Unexpected error sending a request", e)
                _state.update { it.copy(phase = Phase.EDITING, notice = Notice.FAILED) }
            }
        }
    }

    /** After sending: another request, keeping name and email. */
    fun startOver() {
        _state.update {
            it.copy(
                phase = Phase.EDITING,
                ticket = null,
                kind = options.initialKind,
                attachments = emptyList(),
                issues = emptyMap(),
                notice = null,
                values = it.values.filterKeys { k -> k == FaroForm.NAME || k == FaroForm.EMAIL },
            )
        }
        if (Faro.isConfigured) Faro.client.prepare()
    }

    private fun remember(email: String, name: String) {
        prefs.edit().apply {
            if (email.isBlank()) remove(KEY_EMAIL) else putString(KEY_EMAIL, email)
            if (name.isBlank()) remove(KEY_NAME) else putString(KEY_NAME, name)
        }.apply()
    }

    private fun HelpState.withError(e: FaroException): HelpState = when (e) {
        is FaroException.Network -> copy(notice = Notice.OFFLINE)
        is FaroException.RateLimited -> copy(notice = Notice.RATE_LIMITED)
        is FaroException.InvalidForm -> {
            val key = e.field.takeIf { it.isNotEmpty() } ?: FaroForm.KIND
            copy(issues = mapOf(key to if (key == FaroForm.KIND || key == FaroForm.NAME) FaroIssue.REQUIRED else FaroIssue.INVALID), notice = Notice.CHECK_FIELDS, issueSignal = issueSignal + 1)
        }
        is FaroException.InvalidEmail ->
            copy(issues = mapOf(FaroForm.EMAIL to FaroIssue.INVALID_EMAIL), notice = Notice.CHECK_FIELDS, issueSignal = issueSignal + 1)
        is FaroException.InvalidMessage ->
            copy(issues = mapOf(FaroForm.MESSAGE to FaroIssue.INVALID), notice = Notice.CHECK_FIELDS, issueSignal = issueSignal + 1)
        is FaroException.AttachmentTooLarge, is FaroException.AttachmentType -> copy(notice = Notice.IMAGE)
        is FaroException.ModuleRequired, is FaroException.InvalidKey, is FaroException.SubscriptionInactive ->
            copy(notice = Notice.UNAVAILABLE)
        else -> copy(notice = Notice.FAILED)
    }

    private companion object {
        const val TAG = "FaroUI"
        const val KEY_EMAIL = "lastEmail"
        const val KEY_NAME = "lastName"
    }
}
