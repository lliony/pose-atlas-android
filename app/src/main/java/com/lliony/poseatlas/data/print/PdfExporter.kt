package com.lliony.poseatlas.data.print

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.lliony.poseatlas.data.image.ImageStore
import com.lliony.poseatlas.domain.logic.*
import com.lliony.poseatlas.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.math.*

data class PrintOptions(val paper: String = "a4", val landscape: Boolean = true, val shortEdge: Boolean = true, val frame: Boolean = true, val calibration: Boolean = false)
fun shareImageSize(aspect: Double): Pair<Int, Int> {
    require(aspect.isFinite() && aspect > 0) { "图片比例无效" }
    val short = 70 * 300.0 / 25.4
    val width = if (aspect >= 1) short * aspect else short
    val height = if (aspect >= 1) short else short / aspect
    require(width <= 4096 && height <= 4096 && width * height <= 8_000_000) {
        "图片比例过于狭长，请先裁切后分享"
    }
    return width.roundToInt() to height.roundToInt()
}
private data class PrintCard(val card: Card, val photo: Photo)
class PdfExporter @Inject constructor(@ApplicationContext private val context: Context, private val images: ImageStore) {
    private val ink = Color.rgb(32, 60, 52)
    fun shareImages(card: Card, photo: Photo, category: String): List<File> {
        val aspect = photoAspect(photo)
        val (width, height) = shareImageSize(aspect)
        val item = PrintCard(card, photo)
        val scale = fitNotes(item, width.toFloat(), height.toFloat())
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val outputs = mutableListOf<File>()
        try {
            repeat(2) { side ->
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    drawCard(Canvas(bitmap), item, category, width.toFloat(), height.toFloat(), side == 1, scale, false, side + 1)
                    val file = File(directory, "pose-atlas-${UUID.randomUUID()}-${if (side == 0) "front" else "back"}.png")
                    outputs += file
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "图片分享生成失败" } }
                } finally { bitmap.recycle() }
            }
            return outputs
        } catch (e: Exception) { outputs.forEach { it.delete() }; throw e }
    }
    fun render(cards: List<Card>, categories: List<Category>, options: PrintOptions): File {
        require(cards.isNotEmpty() || options.calibration) { "请先选择卡片" }
        val document = PdfDocument()
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val output = File(directory, "pose-atlas-${UUID.randomUUID()}.pdf")
        var pageNumber = 0
        try {
            if (options.calibration) {
                val layout = printLayout(options.paper, options.landscape, 0.75)
                repeat(2) { side ->
                    val page = document.startPage(PdfDocument.PageInfo.Builder((layout.paperWidth * 72 / 25.4).roundToInt(), (layout.paperHeight * 72 / 25.4).roundToInt(), ++pageNumber).create())
                    val paint = Paint().apply { color = ink; strokeWidth = 2f }
                    val w = page.info.pageWidth.toFloat(); val h = page.info.pageHeight.toFloat()
                    listOf(20f to 20f, w - 20 to 20f, 20f to h - 20, w - 20 to h - 20).forEachIndexed { i, (x, y) -> page.canvas.drawLine(x - 8, y, x + 8, y, paint); page.canvas.drawLine(x, y - 8, x, y + 8, paint); paint.textSize = 12f; page.canvas.drawText("${i + 1}", x + 9, y + 9, paint) }
                    paint.textSize = 20f; page.canvas.drawText("${if (side == 0) "正面" else "背面"} · ${if (options.shortEdge) "短边翻转" else "长边翻转"}", 50f, h / 2, paint)
                    document.finishPage(page)
                }
            } else {
                val groups = cards.filterNot { it.deleted }.flatMap { card -> card.images.map { PrintCard(card, it) } }.groupBy { (photoAspect(it.photo) * 10000).roundToInt() }.values.sortedByDescending { it.size }
                groups.forEach { group ->
                    val aspect = photoAspect(group.first().photo)
                    val full = printLayout(options.paper, options.landscape, aspect)
                    group.chunked(full.capacity).forEach { chunk ->
                        val layout = printLayout(options.paper, options.landscape, aspect, chunk.size)
                        val scale = 72.0 / 25.4
                        val w = (layout.cardWidth * scale).toFloat(); val h = (layout.cardHeight * scale).toFloat()
                        val fontScales = chunk.map { fitNotes(it, w, h) }
                        repeat(2) { side ->
                            val page = document.startPage(PdfDocument.PageInfo.Builder((layout.paperWidth * scale).roundToInt(), (layout.paperHeight * scale).roundToInt(), ++pageNumber).create())
                            val gapX = (page.info.pageWidth - w * layout.cols) / (layout.cols + 1)
                            val gapY = (page.info.pageHeight - h * layout.rows) / (layout.rows + 1)
                            for (slot in 0 until layout.capacity) {
                                val index = if (side == 0) slot else backIndex(slot, layout.cols, layout.rows, options.shortEdge)
                                val item = chunk.getOrNull(index) ?: continue
                                page.canvas.save()
                                page.canvas.translate(gapX + slot % layout.cols * (w + gapX), gapY + slot / layout.cols * (h + gapY))
                                drawCard(page.canvas, item, categories.find { it.id == item.card.categoryId }?.name.orEmpty(), w, h, side == 1, fontScales[index], options.frame, pageNumber)
                                page.canvas.restore()
                            }
                            document.finishPage(page)
                        }
                    }
                }
            }
            output.outputStream().use(document::writeTo)
            return output
        } catch (e: Exception) { output.delete(); throw e } finally { document.close() }
    }
    private fun textLayout(text: String, size: Float, width: Float, bold: Boolean = false): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = size; typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL) }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.roundToInt().coerceAtLeast(1)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.3f).setIncludePad(false).build()
    }
    private fun fitNotes(item: PrintCard, w: Float, h: Float): Float {
        val em = min(w, h) * 0.0271f; val inset = em * 1.58f; val width = w - 2 * inset
        val title = textLayout(item.card.title, em * 2.05f, width, true)
        require(title.height <= h * 0.2f) { "${item.card.code} ${item.card.title}：标题过长，请精简后打印" }
        for (step in 0..6) {
            val scale = max(0.78f, 1f - step * 0.04f)
            val needed = resolveNotes(item.card, item.photo).values().zip(noteLabels.values).sumOf { (text, label) ->
                (textLayout(label, em, width, true).height + textLayout(text, em * 1.3f * scale, width).height + em * 2f).toDouble()
            }
            if (needed <= h - 2 * inset - title.height - em * 5) return scale
        }
        throw IllegalArgumentException("${item.card.code} ${item.card.title}：教学说明超出版面，请精简后打印")
    }
    private fun drawCard(canvas: Canvas, item: PrintCard, category: String, w: Float, h: Float, back: Boolean, scale: Float, frame: Boolean, page: Int) {
        val em = min(w, h) * 0.0271f; val inset = em * 1.58f; val width = w - 2 * inset
        canvas.clipRect(0f, 0f, w, h)
        canvas.drawColor(if (back) Color.rgb(241, 243, 236) else Color.WHITE)
        if (frame) canvas.drawRect(0f, 0f, w, h, Paint().apply { color = Color.rgb(204, 211, 198); style = Paint.Style.STROKE; strokeWidth = 0.56f; pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f) })
        var y = inset
        fun text(value: String, size: Float, bold: Boolean = false) { val layout = textLayout(value, size, width, bold); canvas.save(); canvas.translate(inset, y); layout.draw(canvas); canvas.restore(); y += layout.height + em }
        text("${item.card.code} · $category", em)
        if (back) {
            text(item.card.title, em * 2.05f, true)
            noteLabels.values.zip(resolveNotes(item.card, item.photo).values()).forEach { (label, value) -> text(label, em, true); text(value, em * 1.3f * scale) }
        } else {
            val imageBottom = h * 0.65f
            val bitmap = images.decode(item.photo, 1400)
            if (bitmap != null) {
                val target = RectF(inset, y, w - inset, imageBottom)
                val factor = max(target.width() / bitmap.width, target.height() / bitmap.height)
                val sw = target.width() / factor; val sh = target.height() / factor
                val src = Rect(((bitmap.width - sw) / 2).roundToInt(), ((bitmap.height - sh) / 2).roundToInt(), ((bitmap.width + sw) / 2).roundToInt(), ((bitmap.height + sh) / 2).roundToInt())
                canvas.drawBitmap(bitmap, src, target, Paint(Paint.FILTER_BITMAP_FLAG)); bitmap.recycle()
            } else text("图片缺失", em * 2)
            y = imageBottom + em * 2
            text(item.card.title, em * 2.05f, true)
            text((listOfNotNull(item.card.gender) + item.card.styles.orEmpty() + item.card.tags).joinToString(" · "), em)
        }
        val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = em * 0.8f }
        canvas.drawText("POSE ATLAS · ${(w * 25.4 / 72).roundToInt()}×${(h * 25.4 / 72).roundToInt()}mm · $page", inset, h - inset / 2, footer)
    }
}
