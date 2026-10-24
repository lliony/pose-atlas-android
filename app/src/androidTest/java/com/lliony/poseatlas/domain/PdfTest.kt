package com.lliony.poseatlas.domain

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.data.image.ImageStore
import com.lliony.poseatlas.data.local.SeedLoader
import com.lliony.poseatlas.data.print.PdfExporter
import com.lliony.poseatlas.data.print.PrintOptions
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PdfTest {
    @Test fun singleCardCreatesTwoPages() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seed = SeedLoader(context).load()
        val exporter = PdfExporter(context, ImageStore(context))
        val output = exporter.render(seed.cards.take(1), seed.categories, PrintOptions())
        assertThat(output.length()).isGreaterThan(100L)
        PdfRenderer(ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf -> assertThat(pdf.pageCount).isEqualTo(2) }
    }
}
