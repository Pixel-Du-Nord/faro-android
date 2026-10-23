package com.pixeldunord.faro.internal

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** What came back from Faro, whatever the status. */
internal class HttpResponse(val status: Int, val body: String, val retryAfter: String? = null)

/** The HTTP layer, swappable in tests. Throws [IOException] when the server is not reached. */
internal fun interface Transport {
    @Throws(IOException::class)
    fun execute(method: String, url: String, body: ByteArray?, contentType: String?): HttpResponse
}

/** [Transport] on `HttpURLConnection`: no dependency for the app to carry. */
internal class UrlConnectionTransport(private val userAgent: String) : Transport {
    override fun execute(method: String, url: String, body: ByteArray?, contentType: String?): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", userAgent)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(status, text, connection.getHeaderField("Retry-After"))
        } finally {
            connection.disconnect()
        }
    }
}
