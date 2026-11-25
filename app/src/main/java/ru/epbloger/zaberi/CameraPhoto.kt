package ru.epbloger.zaberi

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

// A dedicated provider avoids device-specific issues with using FileProvider directly.
class CameraPhotoProvider : FileProvider()

internal object CameraPhoto {
    private val safeName = Regex("capture-[A-Za-z0-9_-]+\\.jpg")
    private fun directory(context: Context) = File(context.cacheDir, "camera")
    fun create(context: Context): File {
        val dir = directory(context)
        check(dir.isDirectory || dir.mkdirs()) { "Не удалось создать временный файл" }
        val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        dir.listFiles()?.filter { safeName.matches(it.name) && it.lastModified() < cutoff }?.forEach { it.delete() }
        return File.createTempFile("capture-", ".jpg", dir)
    }
    fun restore(context: Context, name: String?): File? =
        name?.takeIf { safeName.matches(it) }?.let { File(directory(context), it) }?.takeIf { it.isFile }
    fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".camera", file)
}
