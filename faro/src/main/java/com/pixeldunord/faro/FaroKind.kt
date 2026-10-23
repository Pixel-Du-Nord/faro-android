package com.pixeldunord.faro

/**
 * What a request is about. Faro offers up to three; the form says which ([FaroForm.kinds]).
 * Faro sends only the keys: the names people read ("Something's wrong", "An idea"…) are the app's.
 */
public enum class FaroKind(
    /** The value on the wire (`problem`, `suggestion`, `question`). */
    public val wire: String,
) {
    /** Something does not work as it should. */
    PROBLEM("problem"),

    /** A feature or an improvement. */
    SUGGESTION("suggestion"),

    /** "How do I…?" */
    QUESTION("question");

    public companion object {
        /** The kind for a wire value, or `null` if Faro does not know it. */
        public fun fromWire(value: String?): FaroKind? = entries.firstOrNull { it.wire == value }
    }
}
