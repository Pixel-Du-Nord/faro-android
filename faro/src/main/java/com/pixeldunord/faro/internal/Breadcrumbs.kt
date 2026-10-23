package com.pixeldunord.faro.internal

import org.json.JSONArray
import org.json.JSONObject

/** One recorded action: what the app said and when (wall clock, ms). */
internal class Crumb(val text: String, val at: Long)

/**
 * The last [capacity] actions of the app, newest last. Thread-safe: apps call
 * [com.pixeldunord.faro.Faro.breadcrumb] from anywhere.
 */
internal class Breadcrumbs(private val capacity: Int = CAPACITY, private val now: () -> Long = System::currentTimeMillis) {
    private val items = ArrayDeque<Crumb>()

    fun add(text: String) {
        val clean = text.replace(Regex("\\s+"), " ").trim().take(TEXT_MAX)
        if (clean.isEmpty()) return
        synchronized(items) {
            items.addLast(Crumb(clean, now()))
            while (items.size > capacity) items.removeFirst()
        }
    }

    fun clear() = synchronized(items) { items.clear() }

    fun snapshot(): List<Crumb> = synchronized(items) { items.toList() }

    companion object {
        const val CAPACITY = 20
        const val TEXT_MAX = 60

        /**
         * The newest actions that fit in [max] characters, oldest first, joined with ` › `. Faro
         * keeps device values up to 120 characters, so this is the trail the team sees today.
         */
        fun trail(crumbs: List<Crumb>, max: Int = 120): String {
            val picked = ArrayDeque<String>()
            var length = 0
            for (crumb in crumbs.asReversed()) {
                val extra = crumb.text.length + if (picked.isEmpty()) 0 else SEPARATOR.length
                if (length + extra > max) {
                    if (picked.isEmpty()) picked.addFirst(crumb.text.take(max))
                    break
                }
                picked.addFirst(crumb.text)
                length += extra
            }
            return picked.joinToString(SEPARATOR)
        }

        /** Every action with how many seconds before [sentAt] it happened (for `context.breadcrumbs`). */
        fun toJson(crumbs: List<Crumb>, sentAt: Long): JSONArray = JSONArray().apply {
            crumbs.forEach { put(JSONObject().put("text", it.text).put("ago", ((sentAt - it.at) / 1000).coerceAtLeast(0))) }
        }

        private const val SEPARATOR = " › "
    }
}
