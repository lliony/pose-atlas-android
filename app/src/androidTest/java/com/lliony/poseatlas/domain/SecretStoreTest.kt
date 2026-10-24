package com.lliony.poseatlas.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.data.security.SecretStore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretStoreTest {
    @Test fun encryptedConfigSurvivesInstanceRecreation() {
        val store = SecretStore()
        val plaintext = "private-test-key-123"
        val encrypted = store.encrypt(plaintext)
        assertThat(encrypted).doesNotContain(plaintext)
        assertThat(SecretStore().decrypt(encrypted)).isEqualTo(plaintext)
    }
}
