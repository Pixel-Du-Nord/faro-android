package com.pixeldunord.faro

import com.pixeldunord.faro.internal.FilePart
import com.pixeldunord.faro.internal.Multipart
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MultipartTest {

    @Test
    fun buildsADataPartAndOneFilePartPerImage() {
        val data = """{"kind":"problem"}""".toByteArray()
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x0D, 0x0A, 0xFF.toByte(), 0xD9.toByte())
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val body = Multipart.build(
            "B0UND",
            data,
            listOf(FilePart(jpeg, "image/jpeg", "screen.jpg"), FilePart(png, "image/png", "two.png")),
        )

        val expected = java.io.ByteArrayOutputStream().apply {
            write(
                (
                    "--B0UND\r\n" +
                        "Content-Disposition: form-data; name=\"data\"\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n\r\n"
                    ).toByteArray(),
            )
            write(data)
            write("\r\n--B0UND\r\nContent-Disposition: form-data; name=\"file\"; filename=\"screen.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            write(jpeg)
            write("\r\n--B0UND\r\nContent-Disposition: form-data; name=\"file\"; filename=\"two.png\"\r\nContent-Type: image/png\r\n\r\n".toByteArray())
            write(png)
            write("\r\n--B0UND--\r\n".toByteArray())
        }.toByteArray()
        assertArrayEquals(expected, body)
    }

    @Test
    fun contentTypeCarriesTheBoundary() {
        assertEquals("multipart/form-data; boundary=faro-1", Multipart.contentType("faro-1"))
    }

    @Test
    fun filenamesCannotBreakTheHeader() {
        assertEquals("evil__.jpg", Multipart.safeFilename("../../evil\"\n.jpg"))
        assertEquals("captura_.jpg", Multipart.safeFilename("capturañ.jpg"))
        assertEquals("image", Multipart.safeFilename(""))
    }
}
