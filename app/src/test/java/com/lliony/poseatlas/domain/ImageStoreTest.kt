package com.lliony.poseatlas.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.data.image.ImageStore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ImageStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun explicitCropPreservesMetadataAndOriginal() {
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        val store = ImageStore(context)
        val bytes = ByteArrayOutputStream().apply { source.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        val photo = store.import(bytes.inputStream()).copy(ai = true, notes = com.lliony.poseatlas.domain.model.Notes(pose = "保留笔记"))
        val original = store.file(photo.src)!!.readBytes()
        val cropped = store.crop(photo, com.lliony.poseatlas.domain.logic.NormalizedCrop(.5f, 0f, 1f, 1f))
        assertThat(cropped.width).isEqualTo(100)
        assertThat(cropped.height).isEqualTo(100)
        assertThat(cropped.id).isEqualTo(photo.id)
        assertThat(cropped.notes).isEqualTo(photo.notes)
        assertThat(cropped.ai).isTrue()
        assertThat(store.file(photo.src)!!.readBytes()).isEqualTo(original)
        source.recycle()
    }
    @Test fun normalizesToJpegWithoutUpscalingAndCropsToRatio() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        val bytes = ByteArrayOutputStream().apply { source.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        val store = ImageStore(context)
        val photo = store.import(bytes.inputStream())
        assertThat(photo.width).isEqualTo(100)
        assertThat(photo.height).isEqualTo(100)
        val file = store.file(photo.src)!!
        assertThat(file.readBytes().take(2)).containsExactly(0xff.toByte(), 0xd8.toByte()).inOrder()
        val cropped = store.crop(photo, 3f / 4f)
        assertThat(cropped.src).isNotEqualTo(photo.src)
        assertThat(cropped.width).isEqualTo(75)
        assertThat(cropped.height).isEqualTo(100)
        assertThat(BitmapFactory.decodeFile(store.file(cropped.src)!!.path).width).isEqualTo(75)
        source.recycle()
    }

    @Test fun cleaningRetainsReferencedFilesAndRejectsUnsafeKeys() {
        val store = ImageStore(context)
        assertThat(store.file("/api/media/../secret")).isNull()
        assertThat(store.file("/images/street.png")).isNull()
        val retained = store.file("/api/media/retained")!!.apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        val garbage = store.file("/api/media/garbage")!!.apply { writeBytes(byteArrayOf(1)) }
        store.cleanup(setOf("/api/media/retained"))
        assertThat(retained.exists()).isTrue()
        assertThat(garbage.exists()).isFalse()
    }
}
