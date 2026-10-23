package com.pixeldunord.faro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMappingTest {

    private fun body(code: String, number: Int, message: String) =
        """{"errorCode":"$code","errorNumber":$number,"message":"$message"}"""

    @Test
    fun invalidFormCarriesTheField() {
        val e = FaroException.from(400, body("INVALID_FORM", 400028, "field: where"))
        assertTrue(e is FaroException.InvalidForm)
        assertEquals("where", (e as FaroException.InvalidForm).field)
        assertEquals("INVALID_FORM", e.code)
        assertEquals(400, e.status)
    }

    @Test
    fun spamCheckCarriesTheReason() {
        val e = FaroException.from(400, body("SPAM_CHECK_FAILED", 400027, "proof: too_fast"))
        assertEquals("too_fast", (e as FaroException.SpamCheck).reason)
        assertFalse(e.keepsProof)
    }

    @Test
    fun rateLimitedReadsRetryAfter() {
        val e = FaroException.from(429, body("RATE_LIMITED", 429001, "x"), retryAfter = "1800")
        assertEquals(1800, (e as FaroException.RateLimited).retryAfterSeconds)
        assertTrue(e.keepsProof)
        // A 429 without Faro's body (a proxy) is still a rate limit.
        assertTrue(FaroException.from(429, "<html>") is FaroException.RateLimited)
    }

    @Test
    fun mapsEveryKnownCode() {
        assertTrue(FaroException.from(400, body("INVALID_EMAIL_FORMAT", 400003, "")) is FaroException.InvalidEmail)
        assertTrue(FaroException.from(400, body("INVALID_TEXT", 400017, "")) is FaroException.InvalidMessage)
        assertTrue(FaroException.from(413, body("FILE_TOO_LARGE", 413001, "")) is FaroException.AttachmentTooLarge)
        assertTrue(FaroException.from(415, body("FILE_TYPE_NOT_ALLOWED", 415001, "")) is FaroException.AttachmentType)
        assertTrue(FaroException.from(403, body("MODULE_REQUIRED", 403012, "")) is FaroException.ModuleRequired)
        assertTrue(FaroException.from(404, body("WIDGET_NOT_FOUND", 404010, "")) is FaroException.InvalidKey)
        assertTrue(FaroException.from(403, body("SUBSCRIPTION_INACTIVE", 403006, "")) is FaroException.SubscriptionInactive)
    }

    @Test
    fun anythingElseIsAServerError() {
        val e = FaroException.from(500, body("INTERNAL_ERROR", 500001, "Error interno"))
        assertTrue(e is FaroException.Server)
        assertEquals("INTERNAL_ERROR", e.code)
        assertFalse(e.keepsProof)

        val garbage = FaroException.from(502, "Bad gateway")
        assertEquals("HTTP_502", garbage.code)
        assertFalse(garbage.keepsProof)

        val body = FaroException.from(400, body("INVALID_BODY", 400002, ""))
        assertTrue(body is FaroException.Server)
        assertTrue(body.keepsProof) // rejected before the proof was looked at
    }

    @Test
    fun invalidFormWithoutFieldGivesAnEmptyField() {
        val e = FaroException.from(400, body("INVALID_FORM", 400028, "Falta un dato"))
        assertEquals("", (e as FaroException.InvalidForm).field)
    }

    @Test
    fun networkErrorsKeepTheProof() {
        val e = FaroException.Network(java.io.IOException("timeout"))
        assertEquals("NETWORK", e.code)
        assertEquals(0, e.status)
        assertTrue(e.keepsProof)
        assertNull(FaroException.SpamCheck().reason)
    }
}
