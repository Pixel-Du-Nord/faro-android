package com.pixeldunord.faro.internal

import java.io.ByteArrayOutputStream

/** One file part of a multipart body. */
internal class FilePart(val bytes: ByteArray, val mimeType: String, val filename: String)

/**
 * `multipart/form-data` as `POST /widget/requests` reads it: a `data` part with the request JSON and
 * one `file` part per image.
 */
internal object Multipart {
    fun contentType(boundary: String): String = "multipart/form-data; boundary=$boundary"

    fun build(boundary: String, data: ByteArray, files: List<FilePart>): ByteArray {
        val body = ByteArrayOutputStream()
        fun ascii(text: String) = body.write(text.toByteArray(Charsets.UTF_8))

        ascii("--$boundary\r\n")
        ascii("Content-Disposition: form-data; name=\"data\"\r\n")
        ascii("Content-Type: application/json; charset=utf-8\r\n\r\n")
        body.write(data)
        ascii("\r\n")
        for (file in files) {
            ascii("--$boundary\r\n")
            ascii("Content-Disposition: form-data; name=\"file\"; filename=\"${safeFilename(file.filename)}\"\r\n")
            ascii("Content-Type: ${file.mimeType}\r\n\r\n")
            body.write(file.bytes)
            ascii("\r\n")
        }
        ascii("--$boundary--\r\n")
        return body.toByteArray()
    }

    /** A filename that cannot break the header: no quotes, no line breaks, no paths, ASCII only. */
    fun safeFilename(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .map { c -> if (c.code in 0x20..0x7e && c != '"' && c != '\\') c else '_' }
            .joinToString("")
            .take(80)
            .ifBlank { "image" }
}
