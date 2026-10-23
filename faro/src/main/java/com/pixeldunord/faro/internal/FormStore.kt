package com.pixeldunord.faro.internal

import java.io.File
import java.security.MessageDigest

/** Where the last form definition is kept, so the form shows up instantly (and offline). */
internal interface FormStore {
    fun read(): String?
    fun write(json: String)
}

/** A file per key and app under the app's no-backup files (not synced to other devices). */
internal class FileFormStore(directory: File, key: String, app: String?) : FormStore {
    private val file = File(directory, "form-${digest("$key|${app.orEmpty()}")}.json")

    override fun read(): String? = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()

    override fun write(json: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json)
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }
    }

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(8)
            .joinToString("") { "%02x".format(it) }
}
