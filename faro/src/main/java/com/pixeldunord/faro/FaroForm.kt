package com.pixeldunord.faro

import org.json.JSONArray
import org.json.JSONObject

/**
 * The request form, with every text already in one language.
 *
 * It comes from the Faro panel (**Widget → Request form**, or the app's own form under
 * **Organization → Applications**), so the team can change a text, add a field or reorder them
 * without shipping a release. Draw [fields] in order; that is the whole form.
 *
 * Get one with [FaroClient.form] (network first, then the copy cached on disk) or
 * [FaroClient.cachedForm] (instant, may be `null`). When Faro cannot be reached and nothing is
 * cached, draw [fallback], or build your own with the public constructor.
 *
 * Texts the team left empty come as `""`: use your own fallback then (the ready-made screen in
 * `faro-ui` does).
 */
public class FaroForm internal constructor(
    /** The language of the texts: the one asked for if the form has it, else the form's main one. */
    public val locale: String,
    /** Every language the form is written in; the first is the main one. */
    public val locales: List<String>,
    /** Heading of the form. */
    public val title: String,
    /** A sentence under the heading. */
    public val intro: String,
    /** Label of the send button. */
    public val submit: String,
    /** What to say once the request is in. */
    public val success: String,
    /** The kinds on offer, in order. Never empty. */
    public val kinds: List<FaroKind>,
    /**
     * Every field to ask, in the team's order: the built-in ones ([NAME] unless it is off, [EMAIL],
     * [MESSAGE]) and the custom ones.
     */
    public val fields: List<FaroField>,
    /** True when this copy came from the disk cache because Faro could not be reached. */
    public val isCached: Boolean,
    /** Every language at once, for [inLocale]; `null` for a form built by hand. */
    internal val definition: FormDefinition?,
) {
    /**
     * A form built by hand: your own fields and texts, for example to draw when Faro cannot be
     * reached (see [fallback] for a minimal one). [validate], [customValues] and [request] work on it
     * like on Faro's.
     *
     * @param locale The language of the texts (and of the emails, through [request]).
     * @param fields The fields in order: [NAME], [EMAIL], [MESSAGE] and custom ones.
     * @param kinds The kinds on offer; empty means all three.
     */
    public constructor(
        locale: String,
        fields: List<FaroField>,
        kinds: List<FaroKind> = FaroKind.entries,
        title: String = "",
        intro: String = "",
        submit: String = "",
        success: String = "",
        locales: List<String> = listOf(locale),
        isCached: Boolean = false,
    ) : this(
        locale = locale,
        locales = locales.ifEmpty { listOf(locale) },
        title = title,
        intro = intro,
        submit = submit,
        success = success,
        kinds = kinds.distinct().ifEmpty { FaroKind.entries },
        fields = fields,
        isCached = isCached,
        definition = null,
    )

    /** The field with this [key], or `null`. */
    public fun field(key: String): FaroField? = fields.firstOrNull { it.key == key }

    /**
     * The same form in another language (no network), resolved like [FaroClient.form] does. A form
     * built by hand has a single language and comes back unchanged; [fallback] follows the language.
     */
    public fun inLocale(locale: String): FaroForm = definition?.resolve(locale, isCached) ?: this

    /**
     * Runs the same checks Faro runs, so a mistake is shown before spending a round trip (Faro also
     * limits how many requests an IP can send per hour, failed ones included).
     *
     * @param kind The chosen kind, or `null` if none yet.
     * @param values Everything typed, by field key ([NAME], [EMAIL], [MESSAGE] and the custom keys).
     *   A checked checkbox is `"true"`.
     * @return The problems by field key, in form order; the kind's problem is under [KIND].
     *   Empty means the request can be sent.
     */
    public fun validate(kind: FaroKind?, values: Map<String, String>): Map<String, FaroIssue> {
        val issues = LinkedHashMap<String, FaroIssue>()
        if (kind == null || kind !in kinds) issues[KIND] = FaroIssue.REQUIRED
        for (field in fields) {
            field.check(values[field.key].orEmpty())?.let { issues[field.key] = it }
        }
        return issues
    }

    /**
     * The request for what was typed: [MESSAGE], [EMAIL] and [NAME] go to their own properties, the
     * custom answers to [FaroRequest.fields] (see [customValues]), and [locale] becomes the language of
     * the emails. Values are trimmed.
     *
     * The name follows what the person saw: with the name turned off in the form it is `""` (no name,
     * not even the [identified][Faro.identify] person's); typed empty, it is `""` too; only when
     * [values] has no [NAME] at all is it `null`, which lets [FaroClient.send] use the identified
     * person's name.
     *
     * @param kind The chosen kind.
     * @param values Everything typed, by field key (as for [validate]).
     * @param attachments Up to [FaroRequest.MAX_ATTACHMENTS] pictures.
     */
    public fun request(
        kind: FaroKind,
        values: Map<String, String>,
        attachments: List<FaroAttachment> = emptyList(),
    ): FaroRequest = FaroRequest(
        kind = kind,
        message = values[MESSAGE]?.trim().orEmpty(),
        email = values[EMAIL]?.trim()?.takeIf { it.isNotEmpty() },
        name = if (field(NAME) == null) "" else values[NAME]?.trim(),
        fields = customValues(values),
        attachments = attachments,
        locale = locale,
    )

    /**
     * The custom answers ready for [FaroRequest.fields]: built-in keys left out, empty values
     * dropped, unchecked checkboxes dropped.
     */
    public fun customValues(values: Map<String, String>): Map<String, String> =
        fields.filter { !it.isBuiltIn }.mapNotNull { field ->
            val v = values[field.key]?.trim().orEmpty()
            when {
                v.isEmpty() -> null
                field.type == FaroFieldType.CHECKBOX -> if (v == "true") field.key to "true" else null
                else -> field.key to v
            }
        }.toMap()

    public companion object {
        /** Key of the built-in name field. */
        public const val NAME: String = "name"

        /** Key of the built-in email field. */
        public const val EMAIL: String = "email"

        /** Key of the built-in message field. */
        public const val MESSAGE: String = "message"

        /** Key under which [validate] reports a missing kind. */
        public const val KIND: String = "kind"

        /** Shortest message Faro accepts, in characters. */
        public const val MESSAGE_MIN: Int = 3

        /** Longest message Faro accepts, in characters. */
        public const val MESSAGE_MAX: Int = 4000

        /** Longest email address Faro accepts. */
        public const val EMAIL_MAX: Int = 254

        /**
         * Parses a `requestForm` object (as `GET /widget/config` returns it under
         * `widget.settings.requestForm`) and resolves it in [locale]. Useful if you fetch the
         * configuration yourself; [FaroClient.form] already does all of this.
         */
        public fun parse(json: JSONObject, locale: String): FaroForm =
            FormDefinition.parse(json).resolve(locale, cached = false)

        /**
         * A minimal form for when Faro cannot be reached and nothing is cached: the three kinds, the
         * message, the email and an optional name, without texts (use your own labels). Its
         * [inLocale] follows the language.
         *
         * @param locale The language for the emails; `null` uses the device's. Cut to its base.
         */
        @JvmStatic
        @JvmOverloads
        public fun fallback(locale: String? = null): FaroForm =
            FALLBACK.resolve(FaroLocale.language(locale), cached = false)

        private val FALLBACK: FormDefinition by lazy {
            FormDefinition.parse(JSONObject().put("order", JSONArray(listOf(MESSAGE, EMAIL, NAME))))
        }
    }
}

/**
 * A field of the form. Faro's come resolved in [FaroForm.fields]; build your own for a
 * [hand-made form][FaroForm].
 */
public class FaroField(
    /** The key the answer travels under (`name`, `email`, `message` or the custom key). */
    public val key: String,
    /** How to ask it. */
    public val type: FaroFieldType,
    /** The question. May be `""` for the built-in fields: use your own then. */
    public val label: String = "",
    /** Hint shown inside the empty input. */
    public val placeholder: String = "",
    /** A line under the input. */
    public val help: String = "",
    /** Whether it must be answered (a required checkbox must be checked). */
    public val required: Boolean = false,
    /** The choices of a [FaroFieldType.SELECT] field, in order. */
    public val options: List<FaroOption> = emptyList(),
    /** Headings that group [options] (each option names its group); often empty. */
    public val groups: List<FaroOptionGroup> = emptyList(),
    /** The team asked for a half-width input (two side by side on wide screens). */
    public val halfWidth: Boolean = false,
) {
    /** True for [FaroForm.NAME], [FaroForm.EMAIL] and [FaroForm.MESSAGE]. */
    public val isBuiltIn: Boolean
        get() = key == FaroForm.NAME || key == FaroForm.EMAIL || key == FaroForm.MESSAGE

    internal fun check(raw: String): FaroIssue? {
        val v = raw.trim()
        when (key) {
            FaroForm.EMAIL -> return when {
                v.isEmpty() -> FaroIssue.REQUIRED
                !isValidEmail(v) -> FaroIssue.INVALID_EMAIL
                else -> null
            }
            FaroForm.MESSAGE -> {
                val n = v.codePointCount(0, v.length)
                return when {
                    n == 0 -> FaroIssue.REQUIRED
                    n < FaroForm.MESSAGE_MIN -> FaroIssue.TOO_SHORT
                    n > FaroForm.MESSAGE_MAX -> FaroIssue.TOO_LONG
                    else -> null
                }
            }
        }
        if (type == FaroFieldType.CHECKBOX) {
            return if (required && v != "true") FaroIssue.REQUIRED else null
        }
        if (v.isEmpty()) return if (required) FaroIssue.REQUIRED else null
        val valid = when (type) {
            FaroFieldType.SELECT -> options.any { it.value == v }
            FaroFieldType.NUMBER -> v.replace(',', '.').toDoubleOrNull() != null
            FaroFieldType.TEL -> PHONE.matches(v)
            FaroFieldType.URL -> URL_RE.matches(v)
            FaroFieldType.DATE -> isValidDate(v)
            else -> true
        }
        return if (valid) null else FaroIssue.INVALID
    }

    override fun toString(): String = "FaroField($key, $type, required=$required)"
}

/** How a field is asked. Anything Faro adds later arrives as [TEXT]. */
public enum class FaroFieldType {
    /** One line of text. */
    TEXT,

    /** Several lines (the built-in message is one). */
    TEXTAREA,

    /** One of [FaroField.options]. */
    SELECT,

    /** Yes or no; checked is `"true"`. */
    CHECKBOX,

    /** A number (`,` or `.` as decimal separator). */
    NUMBER,

    /** A phone number. */
    TEL,

    /** An `http(s)://` address. */
    URL,

    /** A date as `yyyy-MM-dd`. */
    DATE,

    /** An email address (the built-in email field). */
    EMAIL,
    ;

    internal companion object {
        fun parse(value: String?): FaroFieldType = when (value) {
            "textarea" -> TEXTAREA
            "select" -> SELECT
            "checkbox" -> CHECKBOX
            "number" -> NUMBER
            "tel" -> TEL
            "url" -> URL
            "date" -> DATE
            else -> TEXT
        }
    }
}

/** A choice of a list: [value] is sent, [label] is read. */
public class FaroOption(
    public val value: String,
    public val label: String = value,
    /** The [FaroOptionGroup.key] it belongs to, or `null`. */
    public val group: String? = null,
) {
    override fun toString(): String = "FaroOption($value)"
}

/** A heading over some options of a list. */
public class FaroOptionGroup(public val key: String, public val label: String)

/** Why a field is not ready to send. Your UI words it. */
public enum class FaroIssue {
    /** Empty, or a required checkbox unchecked, or no kind chosen. */
    REQUIRED,

    /** Not an email address. */
    INVALID_EMAIL,

    /** The message is shorter than [FaroForm.MESSAGE_MIN] characters. */
    TOO_SHORT,

    /** The message is longer than [FaroForm.MESSAGE_MAX] characters. */
    TOO_LONG,

    /** Not a valid value for its type (an unknown option, a bad number, phone, URL or date). */
    INVALID,
}

// ---- the multilingual definition, as Faro sends it --------------------------------------------

/** A text per language: `{"en": "Your name", "es": "Tu nombre"}`; a plain string sits under `""`. */
internal class Text(private val byLocale: Map<String, String>) {
    fun get(locale: String, primary: String): String =
        byLocale[locale]?.takeIf { it.isNotBlank() }
            ?: byLocale[primary]?.takeIf { it.isNotBlank() }
            ?: byLocale[""].orEmpty()

    companion object {
        val EMPTY = Text(emptyMap())

        fun parse(value: Any?): Text = when (value) {
            is String -> Text(mapOf("" to value))
            is JSONObject -> Text(
                value.keys().asSequence().mapNotNull { k -> (value.opt(k) as? String)?.let { k to it } }.toMap(),
            )
            else -> EMPTY
        }
    }
}

internal class BuiltInDef(val mode: String, val label: Text, val placeholder: Text, val help: Text, val half: Boolean)

internal class OptionDef(val value: String, val label: Text, val group: String?)

internal class GroupDef(val key: String, val label: Text)

internal class FieldDef(
    val key: String,
    val type: String,
    val label: Text,
    val placeholder: Text,
    val help: Text,
    val required: Boolean,
    val half: Boolean,
    val options: List<OptionDef>,
    val groups: List<GroupDef>,
)

/** `widget.settings.requestForm`, every language at once. Lenient: what is missing takes its default. */
internal class FormDefinition(
    val locales: List<String>,
    val title: Text,
    val intro: Text,
    val submit: Text,
    val success: Text,
    val kinds: List<FaroKind>,
    val name: BuiltInDef,
    val email: BuiltInDef,
    val message: BuiltInDef,
    val fields: List<FieldDef>,
    val order: List<String>,
) {
    /** The form language for an app in [wanted]: exact, then its base (`pt-BR` → `pt`), else the main one. */
    fun localeFor(wanted: String): String {
        val tag = wanted.trim().replace('_', '-').lowercase()
        val base = tag.substringBefore('-')
        return when {
            tag in locales -> tag
            base in locales -> base
            else -> locales.firstOrNull() ?: base
        }
    }

    fun resolve(wanted: String, cached: Boolean): FaroForm {
        val locale = localeFor(wanted)
        val primary = locales.firstOrNull().orEmpty()
        fun t(text: Text) = text.get(locale, primary)
        fun builtIn(key: String, def: BuiltInDef, type: FaroFieldType, required: Boolean) = FaroField(
            key = key, type = type, label = t(def.label), placeholder = t(def.placeholder), help = t(def.help),
            required = required, options = emptyList(), groups = emptyList(), halfWidth = def.half,
        )
        val resolved = order.mapNotNull { key ->
            when (key) {
                FaroForm.NAME ->
                    if (name.mode == "off") null
                    else builtIn(key, name, FaroFieldType.TEXT, required = name.mode == "required")
                FaroForm.EMAIL -> builtIn(key, email, FaroFieldType.EMAIL, required = true)
                FaroForm.MESSAGE -> builtIn(key, message, FaroFieldType.TEXTAREA, required = true)
                else -> fields.firstOrNull { it.key == key }?.let { f ->
                    FaroField(
                        key = f.key,
                        type = FaroFieldType.parse(f.type),
                        label = t(f.label),
                        placeholder = t(f.placeholder),
                        help = t(f.help),
                        required = f.required,
                        options = f.options.map { FaroOption(it.value, t(it.label).ifEmpty { it.value }, it.group) },
                        groups = f.groups.map { FaroOptionGroup(it.key, t(it.label)) },
                        halfWidth = f.half,
                    )
                }
            }
        }
        return FaroForm(
            locale = locale,
            locales = locales.ifEmpty { listOf(locale) },
            title = t(title),
            intro = t(intro),
            submit = t(submit),
            success = t(success),
            kinds = kinds,
            fields = resolved,
            isCached = cached,
            definition = this,
        )
    }

    companion object {
        private val DEFAULT_KINDS = listOf(FaroKind.PROBLEM, FaroKind.SUGGESTION, FaroKind.QUESTION)

        fun parse(json: JSONObject): FormDefinition {
            fun builtIn(value: Any?): BuiltInDef {
                // Older shape of `name`: a bare string with the mode.
                if (value is String) return BuiltInDef(value, Text.EMPTY, Text.EMPTY, Text.EMPTY, false)
                val o = value as? JSONObject
                    ?: return BuiltInDef("optional", Text.EMPTY, Text.EMPTY, Text.EMPTY, false)
                return BuiltInDef(
                    mode = o.optString("mode").ifEmpty { "optional" },
                    label = Text.parse(o.opt("label")),
                    placeholder = Text.parse(o.opt("placeholder")),
                    help = Text.parse(o.opt("help")),
                    half = o.optString("width") == "half",
                )
            }

            val fields = json.optJSONArray("fields").objects().mapNotNull { o ->
                val key = o.optString("key").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val type = o.optString("type").ifEmpty { "text" }
                FieldDef(
                    key = key,
                    type = type,
                    label = Text.parse(o.opt("label")),
                    placeholder = Text.parse(o.opt("placeholder")),
                    help = Text.parse(o.opt("help")),
                    required = o.optBoolean("required", false),
                    half = o.optString("width") == "half" && type != "textarea",
                    options = o.optJSONArray("options").items().mapNotNull { option ->
                        when (option) {
                            // Older shape: the option is a string (value and label at once).
                            is String -> OptionDef(option, Text(mapOf("" to option)), null)
                            is JSONObject -> {
                                val label = Text.parse(option.opt("label"))
                                val value = option.optString("value").ifEmpty { label.get("", "") }
                                if (value.isEmpty()) null
                                else OptionDef(value, label, option.optString("group").ifEmpty { null })
                            }
                            else -> null
                        }
                    },
                    groups = o.optJSONArray("groups").objects().mapNotNull { g ->
                        g.optString("key").takeIf { it.isNotEmpty() }?.let { GroupDef(it, Text.parse(g.opt("label"))) }
                    },
                )
            }.distinctBy { it.key }.filter { it.key !in BUILT_INS }

            val kinds = json.optJSONArray("kinds").items()
                .mapNotNull { FaroKind.fromWire(it as? String) }
                .distinct()
                .ifEmpty { DEFAULT_KINDS }

            // Faro's order without unknown keys; what is missing goes last (custom fields before the
            // built-in ones, as Faro itself does).
            val existing = fields.map { it.key } + BUILT_INS
            val given = json.optJSONArray("order").items()
                .mapNotNull { it as? String }
                .filter { it in existing }
                .distinct()
            val order = given + existing.filterNot(given::contains)

            return FormDefinition(
                locales = json.optJSONArray("locales").items().mapNotNull { (it as? String)?.lowercase() }.distinct(),
                title = Text.parse(json.opt("title")),
                intro = Text.parse(json.opt("intro")),
                submit = Text.parse(json.opt("submit")),
                success = Text.parse(json.opt("success")),
                kinds = kinds,
                name = builtIn(json.opt("name")),
                email = builtIn(json.opt("email")),
                message = builtIn(json.opt("message")),
                fields = fields,
                order = order,
            )
        }

        private val BUILT_INS = listOf(FaroForm.NAME, FaroForm.EMAIL, FaroForm.MESSAGE)
    }
}

// ---- helpers ----------------------------------------------------------------------------------

private val EMAIL_RE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
private val PHONE = Regex("^[0-9+()\\-.\\s]{5,30}$")
private val URL_RE = Regex("^https?://[^\\s/?#]+[^\\s]*$", RegexOption.IGNORE_CASE)
private val DATE_RE = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")

internal fun isValidEmail(value: String): Boolean = value.length <= FaroForm.EMAIL_MAX && EMAIL_RE.matches(value)

private fun isValidDate(value: String): Boolean {
    val m = DATE_RE.matchEntire(value) ?: return false
    val (y, mo, d) = m.destructured
    val year = y.toInt()
    val month = mo.toInt()
    val day = d.toInt()
    if (month !in 1..12 || day < 1) return false
    val leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[month - 1]
    return day <= days
}

internal fun JSONArray?.items(): List<Any?> = if (this == null) emptyList() else (0 until length()).map { opt(it) }

internal fun JSONArray?.objects(): List<JSONObject> = items().filterIsInstance<JSONObject>()
