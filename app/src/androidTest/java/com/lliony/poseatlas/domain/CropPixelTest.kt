package com.lliony.poseatlas.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lliony.poseatlas.data.image.ImageStore
import com.lliony.poseatlas.domain.logic.NormalizedCrop
import com.lliony.poseatlas.domain.model.Photo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CropPixelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun userSelectedRegionUsesOrientedPixelsAndPreservesOriginal() {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.RED)
            drawRect(100f, 0f, 200f, 100f, Paint().apply { color = Color.BLUE })
        }
        val input = File.createTempFile("crop-orientation", ".jpg", context.cacheDir)
        try {
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            ExifInterface(input).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
            val store = ImageStore(context)
            val photo = input.inputStream().use(store::import)
            assertEquals(100, photo.width); assertEquals(200, photo.height)
            val original = store.file(photo.src)!!.readBytes()
            val result = store.crop(photo, NormalizedCrop(0f, .5f, 1f, 1f))
            val decoded = store.decode(result)!!
            val pixel = decoded.getPixel(decoded.width / 2, decoded.height / 2)
            assertTrue(Color.blue(pixel) > 220 && Color.red(pixel) < 30)
            assertEquals(100, result.width); assertEquals(100, result.height)
            assertArrayEquals(original, store.file(photo.src)!!.readBytes())
            decoded.recycle()
        } finally { bitmap.recycle(); input.delete() }
    }
    @Test fun panelCropUsesTheSelectedPanelRatherThanTheWholeSheet() {
        val store = ImageStore(context)
        val photo = Photo("panel-test", "/images/pose-reference-styles.png", panel = 4)
        val source = store.decode(photo, 2048)!!
        val cropped = store.crop(photo, NormalizedCrop(.25f, .25f, .75f, .75f))
        assertNull(cropped.panel)
        assertEquals(source.width / 2, cropped.width)
        assertEquals(source.height / 2, cropped.height)
        val output = store.decode(cropped, 2048)!!
        val expected = source.getPixel(source.width / 2, source.height / 2)
        val actual = output.getPixel(output.width / 2, output.height / 2)
        assertTrue(kotlin.math.abs(Color.red(expected) - Color.red(actual)) < 35)
        assertTrue(kotlin.math.abs(Color.green(expected) - Color.green(actual)) < 35)
        assertTrue(kotlin.math.abs(Color.blue(expected) - Color.blue(actual)) < 35)
        source.recycle(); output.recycle()
    }
}
