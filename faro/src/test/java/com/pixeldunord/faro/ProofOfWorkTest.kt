package com.pixeldunord.faro

import com.pixeldunord.faro.internal.Proof
import com.pixeldunord.faro.internal.ProofOfWork
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProofOfWorkTest {

    // A known vector, computed independently:
    // python3 -c "import hashlib; print(hashlib.sha256(b'5f2b9c0e7a1d4c3b?t=1792650000000&k=wk_test&31337').hexdigest())"
    private val salt = "5f2b9c0e7a1d4c3b?t=1792650000000&k=wk_test&"
    private val challenge = "5f2316dfd6a30f38b8e8d6de68e94900ff1c0b0e345e852184a760a06a310307"

    @Test
    fun findsTheNumberOfAKnownVector() = runTest {
        assertEquals(31337, ProofOfWork.solve(salt, challenge, 50_000))
    }

    @Test
    fun givesUpWhenTheNumberIsOutOfRange() = runTest {
        assertNull(ProofOfWork.solve(salt, challenge, 31_336))
    }

    @Test
    fun rejectsAMalformedChallenge() = runTest {
        assertNull(ProofOfWork.solve(salt, "zz", 10))
        assertNull(ProofOfWork.solve(salt, "abc", 10))
        assertNull(ProofOfWork.solve(salt, "00ff", 10)) // not 32 bytes
    }

    @Test
    fun waitsTheServersThreeSecondsAndExpiresAtFifteenMinutes() {
        val proof = Proof(salt, 31337, challenge, "sig", requestedAt = 1_000)
        assertEquals(3_200, proof.remainingWait(1_000))
        assertEquals(1_200, proof.remainingWait(3_000))
        assertEquals(0, proof.remainingWait(10_000))
        assertTrue(proof.isFresh(1_000 + 14 * 60_000))
        assertFalse(proof.isFresh(1_000 + 15 * 60_000))
    }

    @Test
    fun serializesAsFaroExpects() {
        val json = Proof(salt, 31337, challenge, "sig", 0).toJson()
        assertEquals(setOf("salt", "number", "challenge", "signature"), json.keys().asSequence().toSet())
        assertEquals(31337, json.getInt("number"))
    }
}
