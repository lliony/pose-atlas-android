package com.lliony.poseatlas.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.data.local.SeedLoader
import com.lliony.poseatlas.domain.logic.validateState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SeedTest {
    @Test fun seedMatchesWebAndEveryImageExists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = SeedLoader(context).load()
        validateState(state)
        assertThat(state.cards).hasSize(24)
        assertThat(state.categories).hasSize(7)
        assertThat(state.albums).hasSize(1)
        val sources = state.cards.flatMap { it.images }.map { it.src }.toSet()
        assertThat(sources).hasSize(7)
        sources.forEach { src -> context.assets.open("seed${src}").use { assertThat(it.read()).isAtLeast(0) } }
    }
}
