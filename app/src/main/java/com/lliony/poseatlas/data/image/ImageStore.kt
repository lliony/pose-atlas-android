package com.lliony.poseatlas.data.image

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.lliony.poseatlas.domain.model.Photo
import com.lliony.poseatlas.domain.logic.NormalizedCrop
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton class ImageStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val directory = File(context.filesDir, "images").apply { mkdirs() }
    fun file(src: String): File? = Regex("^/api/media/([a-zA-Z0-9_-]+)$").matchEntire(src)?.let { File(directory, "${it.groupValues[1]}.jpg") }
    fun open(src: String): InputStream = if (src.matches(Regex("^/images/(street|cafe|park|sea|indoor|stairs|pose-reference-styles)\\.png$"))) context.assets.open("seed$src") else file(src)?.inputStream() ?: error("图片不存在")
    fun import(uri: Uri): Photo = context.contentResolver.openInputStream(uri)?.use { import(it) } ?: error("无法读取图片")
    fun import(input: InputStream): Photo {
        val temp = File.createTempFile("image-", ".input", context.cacheDir)
        try {
            temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var total = 0L
                while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 50L * 1024 * 1024) { "单张图片不能超过 50MB" }; output.write(buffer, 0, n) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000) { "图片格式不支持或超过 100MP" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
            var bitmap = BitmapFactory.decodeFile(temp.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("无法解码图片")
            try {
                val exif = ExifInterface(temp)
                val matrix = Matrix().apply {
                    if (exif.isFlipped) postScale(-1f, 1f)
                    postRotate(exif.rotationDegrees.toFloat())
                }
                if (!matrix.isIdentity) {
                    val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (oriented !== bitmap) { bitmap.recycle(); bitmap = oriented }
                }
                return save(bitmap)
            } finally { bitmap.recycle() }
        } finally { temp.delete() }
    }
    fun decode(photo: Photo, target: Int = 1024): Bitmap? = runCatching {
        open(photo.src).use { stream ->
            @Suppress("DEPRECATION") val decoder = BitmapRegionDecoder.newInstance(stream, false) ?: return null
            try {
                val panel = photo.panel
                val rect = if (panel != null) {
                    val rows = if (photo.src.endsWith("pose-reference-styles.png")) 2 else 1
                    require(panel in 0 until 3 * rows)
                    val w = decoder.width / 3; val h = decoder.height / rows
                    Rect(panel % 3 * w, panel / 3 * h, (panel % 3 + 1) * w, (panel / 3 + 1) * h)
                } else Rect(0, 0, decoder.width, decoder.height)
                var sample = 1
                while (maxOf(rect.width(), rect.height()) / sample > target * 2) sample *= 2
                decoder.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = sample })
            } finally { decoder.recycle() }
        }
    }.getOrNull()
    fun crop(photo: Photo, ratio: Float): Photo {
        require(ratio.isFinite() && ratio > 0)
        val bitmap = decode(photo, 2048) ?: error("无法读取待裁切图片")
        try {
            val w = minOf(bitmap.width, (bitmap.height * ratio).roundToInt()).coerceAtLeast(1)
            val h = minOf(bitmap.height, (bitmap.width / ratio).roundToInt()).coerceAtLeast(1)
            val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - w) / 2, (bitmap.height - h) / 2, w, h)
            try { return save(cropped).copy(id = photo.id, notes = photo.notes, ai = photo.ai) }
            finally { if (cropped !== bitmap) cropped.recycle() }
        } finally { bitmap.recycle() }
    }
    fun crop(photo: Photo, region: NormalizedCrop): Photo {
        val bitmap = decode(photo, 2048) ?: error("无法读取待裁切图片")
        try {
            val left = (region.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
            val top = (region.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
            val right = (region.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
            val bottom = (region.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
            val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            try { return save(cropped).copy(id = photo.id, notes = photo.notes, ai = photo.ai) }
            finally { if (cropped !== bitmap) cropped.recycle() }
        } finally { bitmap.recycle() }
    }
    private fun save(bitmap: Bitmap): Photo {
        val scale = minOf(1.0, 2048.0 / maxOf(bitmap.width, bitmap.height))
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val src = "/api/media/${UUID.randomUUID()}"
        val destination = file(src)!!
        val temporary = File(directory, "${destination.name}.tmp")
        try {
            Canvas(output).apply { drawColor(Color.WHITE); drawBitmap(bitmap, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG)) }
            temporary.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "图片保存失败" } }
            check(temporary.renameTo(destination)) { "图片保存失败" }
            return Photo(UUID.randomUUID().toString(), src, width = w, height = h)
        } finally { output.recycle(); temporary.delete() }
    }
    fun cleanup(referenced: Set<String>) {
        val keep = referenced.mapNotNull { file(it)?.name }.toSet()
        directory.listFiles()?.filter { it.extension == "jpg" && it.name !in keep }?.forEach { it.delete() }
    }
    fun sizeBytes(): Long = directory.listFiles()?.sumOf { it.length() } ?: 0
}
