package com.pixeldunord.faro

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormTest {

    private fun fixture(): JSONObject =
        JSONObject(javaClass.classLoader!!.getResource("parques-form.json")!!.readText())

    @Test
    fun resolvesTextsInTheAskedLocale() {
        val es = FaroForm.parse(fixture(), "es")
        assertEquals("es", es.locale)
        assertEquals("Escríbenos", es.title)
        assertEquals("Enviar", es.submit)
        assertEquals("Tu correo", es.field(FaroForm.EMAIL)!!.label)

        val en = FaroForm.parse(fixture(), "en")
        assertEquals("Write to us", en.title)
        assertEquals("Online game", en.field("where")!!.options.first().label)
    }

    @Test
    fun regionalVariantFallsBackToItsBaseLanguage() {
        assertEquals("en", FaroForm.parse(fixture(), "en-GB").locale)
        assertEquals("es", FaroForm.parse(fixture(), "es_CO").locale)
    }

    @Test
    fun unknownLocaleUsesTheMainOne() {
        val pt = FaroForm.parse(fixture(), "pt-BR")
        assertEquals("es", pt.locale)
        assertEquals("Escríbenos", pt.title)
    }

    @Test
    fun keepsTheTeamsOrderWithBuiltInsAndCustomFields() {
        val form = FaroForm.parse(fixture(), "en")
        assertEquals(listOf("where", "message", "email", "name"), form.fields.map { it.key })
        assertEquals(FaroFieldType.SELECT, form.field("where")!!.type)
        assertEquals(FaroFieldType.TEXTAREA, form.field("message")!!.type)
        assertEquals(FaroFieldType.EMAIL, form.field("email")!!.type)
        assertFalse(form.field("name")!!.required)
        assertTrue(form.field("email")!!.required)
        assertEquals(listOf(FaroKind.PROBLEM, FaroKind.SUGGESTION, FaroKind.QUESTION), form.kinds)
    }

    @Test
    fun missingKeysGoLastAndUnknownOnesAreDropped() {
        val json = JSONObject(
            """
            {"locales":["en"],"kinds":["question","bogus"],"name":"off",
             "fields":[{"key":"plan","type":"text","label":"Plan"},{"key":"os","type":"weird","label":"OS"}],
             "order":["message","ghost","plan"]}
            """,
        )
        val form = FaroForm.parse(json, "en")
        // name is off: never asked.
        assertEquals(listOf("message", "plan", "os", "email"), form.fields.map { it.key })
        assertEquals(FaroFieldType.TEXT, form.field("os")!!.type)
        assertEquals(listOf(FaroKind.QUESTION), form.kinds)
    }

    @Test
    fun readsOlderShapes() {
        val json = JSONObject(
            """
            {"title":"Contact","name":"required",
             "fields":[{"key":"topic","type":"select","label":"Topic","options":["Billing","Other"]}]}
            """,
        )
        val form = FaroForm.parse(json, "fr")
        assertEquals("Contact", form.title)
        assertTrue(form.field("name")!!.required)
        assertEquals(listOf("Billing", "Other"), form.field("topic")!!.options.map { it.value })
        assertEquals(3, form.kinds.size)
    }

    @Test
    fun emptyDefinitionStillGivesTheBuiltInForm() {
        val form = FaroForm.parse(JSONObject(), "en")
        assertEquals(listOf("name", "email", "message"), form.fields.map { it.key })
        assertEquals("", form.title)
    }

    @Test
    fun validatesLikeFaro() {
        val form = FaroForm.parse(fixture(), "en")
        val empty = form.validate(null, emptyMap())
        assertEquals(FaroIssue.REQUIRED, empty[FaroForm.KIND])
        assertEquals(FaroIssue.REQUIRED, empty[FaroForm.MESSAGE])
        assertEquals(FaroIssue.REQUIRED, empty[FaroForm.EMAIL])
        assertNull(empty["where"]) // optional
        assertNull(empty[FaroForm.NAME]) // optional

        val bad = form.validate(
            FaroKind.PROBLEM,
            mapOf("message" to "hi", "email" to "nope", "where" to "moon"),
        )
        assertEquals(FaroIssue.TOO_SHORT, bad[FaroForm.MESSAGE])
        assertEquals(FaroIssue.INVALID_EMAIL, bad[FaroForm.EMAIL])
        assertEquals(FaroIssue.INVALID, bad["where"])

        val ok = form.validate(
            FaroKind.PROBLEM,
            mapOf("message" to "The board froze", "email" to "ana@example.com", "where" to "online"),
        )
        assertTrue(ok.isEmpty())
    }

    @Test
    fun validatesEveryCustomType() {
        val json = JSONObject(
            """
            {"locales":["en"],"fields":[
              {"key":"n","type":"number","label":"N"},
              {"key":"t","type":"tel","label":"T"},
              {"key":"u","type":"url","label":"U"},
              {"key":"d","type":"date","label":"D"},
              {"key":"c","type":"checkbox","label":"C","required":true}]}
            """,
        )
        val form = FaroForm.parse(json, "en")
        val base = mapOf("message" to "Hello there", "email" to "a@b.co")
        val bad = form.validate(FaroKind.PROBLEM, base + mapOf("n" to "x1", "t" to "abc", "u" to "ftp://x", "d" to "2026-02-30"))
        assertEquals(setOf("n", "t", "u", "d", "c"), bad.keys)
        val good = form.validate(
            FaroKind.PROBLEM,
            base + mapOf("n" to "3,5", "t" to "+57 318 000 0000", "u" to "https://pixeldunord.com/x", "d" to "2028-02-29", "c" to "true"),
        )
        assertTrue(good.toString(), good.isEmpty())
    }

    @Test
    fun customValuesLeaveOutBuiltInsEmptiesAndUncheckedBoxes() {
        val json = JSONObject(
            """{"locales":["en"],"fields":[{"key":"plan","type":"text","label":"P"},{"key":"ok","type":"checkbox","label":"OK"},{"key":"x","type":"text","label":"X"}]}""",
        )
        val form = FaroForm.parse(json, "en")
        val values = form.customValues(mapOf("plan" to " pro ", "ok" to "", "x" to "", "email" to "a@b.co"))
        assertEquals(mapOf("plan" to "pro"), values)
    }
}
