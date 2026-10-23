package com.pixeldunord.faro

import com.pixeldunord.faro.internal.Breadcrumbs
import com.pixeldunord.faro.internal.DeviceFacts
import com.pixeldunord.faro.internal.FormStore
import com.pixeldunord.faro.internal.HttpResponse
import com.pixeldunord.faro.internal.Transport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class ClientTest {

    private class MemoryStore(var value: String? = null) : FormStore {
        override fun read(): String? = value
        override fun write(json: String) {
            value = json
        }
    }

    /** A fake Faro: puzzles with a small known answer and scripted answers to POST. */
    private class FakeFaro : Transport {
        var challenges = 0
        val posts = mutableListOf<Pair<String?, ByteArray>>()
        val postAnswers = ArrayDeque<() -> HttpResponse>()
        var config: () -> HttpResponse = { HttpResponse(200, """{"widget":{"settings":{"requestForm":{"locales":["en"],"title":{"en":"Hi"}}}}}""") }
        var offline = false

        override fun execute(method: String, url: String, body: ByteArray?, contentType: String?): HttpResponse {
            if (offline) throw IOException("offline")
            return when {
                url.contains("/widget/challenge") -> {
                    challenges++
                    val salt = "salt$challenges?t=1&k=wk_test&"
                    val n = 7 + challenges
                    val challenge = MessageDigest.getInstance("SHA-256").digest("$salt$n".toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    HttpResponse(200, """{"algorithm":"SHA-256","salt":"$salt","challenge":"$challenge","maxnumber":50000,"signature":"s$challenges"}""")
                }
                url.contains("/widget/config") -> config()
                url.endsWith("/widget/requests") -> {
                    posts += contentType to body!!
                    postAnswers.removeFirstOrNull()?.invoke()
                        ?: HttpResponse(200, """{"success":true,"ticket":{"number":42,"email":"ana@example.com","confirmed":false}}""")
                }
                else -> HttpResponse(404, "{}")
            }
        }

        fun lastJson(): JSONObject = JSONObject(String(posts.last().second))
    }

    private fun TestScope.client(
        faro: FakeFaro,
        store: FormStore = MemoryStore(),
        user: FaroUser? = null,
        crumbs: Breadcrumbs = Breadcrumbs(now = { 100_000 }),
        app: String? = "parques",
    ): FaroClient {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return FaroClient(
            config = FaroConfig(key = "wk_test", app = app, locale = "es"),
            transport = faro,
            store = store,
            collectDevice = { mapOf("appVersion" to "2.3.0", "build" to "241", "os" to "Android", "model" to "Pixel 8") },
            breadcrumbs = crumbs,
            user = { user },
            clock = { testScheduler.currentTime },
            wallClock = { 160_000 },
            io = dispatcher,
            cpu = dispatcher,
        )
    }

    private val request = FaroRequest(kind = FaroKind.PROBLEM, message = "  The board froze  ", email = "Ana@Example.com")

    private fun error(code: String, status: Int, message: String) =
        { HttpResponse(status, """{"errorCode":"$code","errorNumber":${status}000,"message":"$message"}""") }

    @Test
    fun sendsAfterTheThreeSecondWaitAndBuildsTheBody() = runTest {
        val faro = FakeFaro()
        val crumbs = Breadcrumbs(now = { 100_000 }).apply { add("Opened settings"); add("Tapped Save") }
        val client = client(faro, crumbs = crumbs)
        client.prepare()
        val ticket = client.send(request.copy(fields = mapOf("where" to "online"), extraDevice = mapOf("plan" to "pro")))

        assertEquals(FaroTicket(42, "ana@example.com", false), ticket)
        assertTrue("waited ${testScheduler.currentTime}", testScheduler.currentTime >= 3_200)
        assertEquals(1, faro.challenges)
        val body = faro.lastJson()
        assertEquals("application/json; charset=utf-8", faro.posts.last().first)
        assertEquals("wk_test", body.getString("key"))
        assertEquals("parques", body.getString("app"))
        assertEquals("android", body.getString("platform"))
        assertEquals("problem", body.getString("kind"))
        assertEquals("The board froze", body.getString("message"))
        assertEquals("ana@example.com", body.getString("email"))
        assertEquals("es", body.getString("locale"))
        assertEquals("", body.getString("website"))
        assertEquals("online", body.getJSONObject("fields").getString("where"))
        val device = body.getJSONObject("device")
        assertEquals("2.3.0", device.getString("appVersion"))
        assertEquals("pro", device.getString("plan"))
        assertEquals("Opened settings › Tapped Save", device.getString("trail"))
        val trail = body.getJSONObject("context").getJSONArray("breadcrumbs")
        assertEquals(2, trail.length())
        assertEquals(60, trail.getJSONObject(0).getInt("ago"))
        val proof = body.getJSONObject("proof")
        assertEquals(8, proof.getInt("number"))
        assertEquals("s1", proof.getString("signature"))
        assertFalse(body.has("user"))
    }

    @Test
    fun aValidationErrorKeepsTheProofForTheCorrectedRequest() = runTest {
        val faro = FakeFaro()
        faro.postAnswers += error("INVALID_FORM", 400, "field: where")
        val client = client(faro)
        try {
            client.send(request)
            fail("expected InvalidForm")
        } catch (e: FaroException.InvalidForm) {
            assertEquals("where", e.field)
        }
        val before = testScheduler.currentTime
        client.send(request.copy(fields = mapOf("where" to "online")))
        assertEquals("one puzzle for both tries", 1, faro.challenges)
        assertEquals("no second wait", before, testScheduler.currentTime)
        assertEquals(2, faro.posts.size)
    }

    @Test
    fun aRejectedProofIsRetriedOnceWithANewPuzzle() = runTest {
        val faro = FakeFaro()
        faro.postAnswers += error("SPAM_CHECK_FAILED", 400, "proof: reused")
        val ticket = client(faro).send(request)
        assertEquals(42, ticket.number)
        assertEquals(2, faro.challenges)
        assertEquals(2, faro.posts.size)
        assertEquals("s2", faro.lastJson().getJSONObject("proof").getString("signature"))
    }

    @Test
    fun twoRejectedProofsGiveUp() = runTest {
        val faro = FakeFaro()
        faro.postAnswers += error("SPAM_CHECK_FAILED", 400, "proof: expired")
        faro.postAnswers += error("SPAM_CHECK_FAILED", 400, "proof: expired")
        try {
            client(faro).send(request)
            fail("expected SpamCheck")
        } catch (e: FaroException.SpamCheck) {
            assertEquals("expired", e.reason)
        }
        assertEquals(2, faro.posts.size)
    }

    @Test
    fun aSuccessSpendsTheProof() = runTest {
        val faro = FakeFaro()
        val client = client(faro)
        client.send(request)
        client.send(request)
        assertEquals(2, faro.challenges)
    }

    @Test
    fun localChecksDoNotTouchTheNetwork() = runTest {
        val faro = FakeFaro()
        val client = client(faro)
        val cases = listOf(
            request.copy(message = "hi") to FaroException.InvalidMessage::class,
            request.copy(email = null) to FaroException.InvalidEmail::class,
            request.copy(email = "not-an-email") to FaroException.InvalidEmail::class,
        )
        for ((bad, type) in cases) {
            try {
                client.send(bad)
                fail("expected $type")
            } catch (e: FaroException) {
                assertEquals(type, e::class)
            }
        }
        assertTrue(faro.posts.isEmpty())
        assertEquals(0, faro.challenges)
    }

    @Test
    fun theIdentifiedUserFillsTheGapsAndSignedOnesTravelAsUser() = runTest {
        val faro = FakeFaro()
        client(faro, user = FaroUser(id = "u-1", email = "luis@example.com", name = "Luis"))
            .send(request.copy(email = null))
        var body = faro.lastJson()
        assertEquals("luis@example.com", body.getString("email"))
        assertEquals("Luis", body.getString("name"))
        assertEquals("u-1", body.getJSONObject("device").getString("userId"))
        assertFalse(body.has("user"))

        client(faro, user = FaroUser(id = "u-2", email = "eva@example.com", signature = "abc"))
            .send(request.copy(email = null))
        body = faro.lastJson()
        val user = body.getJSONObject("user")
        assertEquals("u-2", user.getString("id"))
        assertEquals("abc", user.getString("signature"))
        assertFalse(body.getJSONObject("device").has("userId"))
    }

    @Test
    fun imagesGoAsMultipart() = runTest {
        val faro = FakeFaro()
        val image = FaroAttachment(byteArrayOf(1, 2, 3), "image/jpeg", "shot.jpg", 1, 1)
        client(faro).send(request.copy(attachments = listOf(image)))
        val (type, body) = faro.posts.last()
        assertTrue(type!!.startsWith("multipart/form-data; boundary=faro-"))
        val text = String(body, Charsets.ISO_8859_1)
        assertTrue(text.contains("name=\"data\""))
        assertTrue(text.contains("name=\"file\"; filename=\"shot.jpg\""))
    }

    @Test
    fun offlineKeepsTheProofAndTheFormFallsBackToTheCache() = runTest {
        val faro = FakeFaro()
        val store = MemoryStore()
        val client = client(faro, store = store)
        assertEquals("Hi", client.form("en").title)
        assertFalse(client.form("en").isCached)
        assertNotNull(store.value)

        faro.offline = true
        val cached = client.form("en")
        assertTrue(cached.isCached)
        assertEquals("Hi", cached.title)

        try {
            client.send(request)
            fail("expected Network")
        } catch (e: FaroException.Network) {
            // The puzzle could not even be fetched.
        }
        faro.offline = false
        client.send(request)
        assertEquals(1, faro.posts.size)
    }

    @Test
    fun withoutCacheAnOfflineFormIsANetworkError() = runTest {
        val faro = FakeFaro().apply { offline = true }
        try {
            client(faro).form()
            fail("expected Network")
        } catch (e: FaroException.Network) {
            // expected
        }
    }

    @Test
    fun aWrongKeyIsNotHiddenByTheCache() = runTest {
        val faro = FakeFaro()
        val store = MemoryStore("""{"locales":["en"]}""")
        faro.config = error("WIDGET_NOT_FOUND", 404, "Widget no encontrado")
        try {
            client(faro, store = store).form()
            fail("expected InvalidKey")
        } catch (e: FaroException.InvalidKey) {
            // expected
        }
    }

    @Test
    fun deviceFactsRespectFarosLimits() {
        val base = (1..10).associate { "k$it" to "v$it" }
        val composed = DeviceFacts.compose(
            base = base,
            trail = "A › B",
            userId = "u",
            extra = mapOf("plan" to "pro", "app" to "nope", "k1" to "override"),
        )
        assertEquals(12, composed.size)
        assertEquals("override", composed["k1"])
        assertEquals("A › B", composed["trail"])
        assertFalse("plan" in composed) // no room left after the SDK's facts
        assertFalse("app" in composed)
        val long = DeviceFacts.compose(mapOf("model" to "x".repeat(300)), null, null, emptyMap())
        assertEquals(120, long["model"]!!.length)
    }

    @Test
    fun theTrailKeepsTheNewestActionsThatFit() {
        val crumbs = Breadcrumbs(capacity = 20, now = { 0 })
        repeat(25) { crumbs.add("Screen number $it") }
        val all = crumbs.snapshot()
        assertEquals(20, all.size)
        assertEquals("Screen number 5", all.first().text)
        val trail = Breadcrumbs.trail(all)
        assertTrue(trail.length <= 120)
        assertTrue(trail.endsWith("Screen number 24"))
        crumbs.add("x".repeat(500))
        assertEquals(Breadcrumbs.TEXT_MAX, crumbs.snapshot().last().text.length)
    }
}
