package com.pixeldunord.faro.internal

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.security.MessageDigest

/*
 * Faro's anti-spam without a captcha (the Altcha format). `GET /widget/challenge` gives a puzzle:
 * find n in 0..maxnumber such that hex(sha256(salt + n)) == challenge. About 25,000 hashes on
 * average, well under a second on a phone. Server rules:
 *
 *  - not accepted before 3 s from when it was asked for (nobody fills a form that fast);
 *  - expires after 20 min;
 *  - spent once a request gets in, and checked LAST: a validation error leaves it usable.
 */

/** A solved puzzle, good for one request that gets in. */
internal class Proof(
    val salt: String,
    val number: Int,
    val challenge: String,
    val signature: String,
    /** When it was asked for, on the monotonic clock: changing the phone's clock cannot move it. */
    val requestedAt: Long,
) {
    fun age(now: Long): Long = now - requestedAt

    /** Faro honors it for 20 min; renew at 15 to stay clear of the limit. */
    fun isFresh(now: Long): Boolean = age(now) < LIFETIME_MS

    /** What is left before the server accepts it (0 if it already does). */
    fun remainingWait(now: Long): Long = (MINIMUM_WAIT_MS - age(now)).coerceAtLeast(0)

    fun toJson(): JSONObject = JSONObject()
        .put("salt", salt)
        .put("number", number)
        .put("challenge", challenge)
        .put("signature", signature)

    companion object {
        /** The server requires 3 s between asking and using it; 200 ms of margin. */
        const val MINIMUM_WAIT_MS = 3_200L
        const val LIFETIME_MS = 15 * 60 * 1000L
    }
}

internal object ProofOfWork {
    /**
     * The n in 0..[max] such that the hex SHA-256 of [salt] followed by n in decimal equals
     * [challenge], or `null` if there is none. Checks for cancellation every 2,000 tries so a
     * closed screen stops burning battery.
     */
    suspend fun solve(salt: String, challenge: String, max: Int): Int? {
        val target = hexToBytes(challenge) ?: return null
        if (target.size != 32) return null
        val sha = MessageDigest.getInstance("SHA-256")
        val prefix = salt.toByteArray(Charsets.UTF_8)
        for (n in 0..max) {
            if (n % 2_000 == 0) currentCoroutineContext().ensureActive()
            sha.update(prefix)
            if (sha.digest(n.toString().toByteArray(Charsets.US_ASCII)).contentEquals(target)) return n
        }
        return null
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[2 * i], 16)
            val lo = Character.digit(hex[2 * i + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
