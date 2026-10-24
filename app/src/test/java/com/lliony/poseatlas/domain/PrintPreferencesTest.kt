package com.lliony.poseatlas.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.data.prefs.PreferencesStore
import com.lliony.poseatlas.data.print.PrintOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PrintPreferencesTest {
    @Test fun printChoicesSurviveStoreRecreationWithoutPersistingCalibration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PreferencesStore(context)
        val options = PrintOptions("letter", landscape = false, shortEdge = false, frame = false, calibration = true)
        store.updatePrintOptions(options)
        val restored = PreferencesStore(context).printOptions.first()
        assertThat(restored).isEqualTo(options.copy(calibration = false))
        store.update(store.flow.first().copy(theme = "dark"))
        assertThat(store.printOptions.first()).isEqualTo(restored)
    }
}
