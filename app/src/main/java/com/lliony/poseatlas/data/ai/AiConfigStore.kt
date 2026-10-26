package com.lliony.poseatlas.data.ai

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.lliony.poseatlas.data.security.SecretStore
import com.lliony.poseatlas.domain.logic.AiProfileRules
import com.lliony.poseatlas.domain.model.atlasJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiPreferences by preferencesDataStore("encrypted-ai")
@Singleton class AiConfigStore @Inject constructor(@ApplicationContext context: Context, private val secrets: SecretStore) {
    private val preferences = context.aiPreferences
    private val encryptedKey = stringPreferencesKey("encrypted_config")
    private val mutex = Mutex()
    private var loaded = false
    private val mutable = MutableStateFlow(AiConfiguration())
    val state = mutable.asStateFlow()
    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock {
        if (!loaded) {
            val encrypted = preferences.data.first()[encryptedKey]
            mutable.value = if (encrypted == null) AiConfiguration() else atlasJson.decodeFromString(secrets.decrypt(encrypted))
            loaded = true
        }
    } }
    suspend fun useForRecommend(id: String) = update { it.copy(recommendId = id) }
    suspend fun useForDraft(id: String) = update { it.copy(draftId = id) }
    suspend fun upsert(profile: AiProfile) = update { AiProfileRules.upsert(it, profile) }
    suspend fun delete(id: String) = update { AiProfileRules.delete(it, id) }
    suspend fun clear() = update { AiConfiguration() }
    private suspend fun update(transform: (AiConfiguration) -> AiConfiguration) = withContext(Dispatchers.IO) { mutex.withLock {
        val value = transform(mutable.value)
        val saved = value.profiles.filter { it.remember }
        val persisted = value.copy(profiles = saved, recommendId = value.recommendId?.takeIf { id -> saved.any { it.id == id } }, draftId = value.draftId?.takeIf { id -> saved.any { it.id == id } })
        preferences.edit { if (saved.isEmpty()) it.remove(encryptedKey) else it[encryptedKey] = secrets.encrypt(atlasJson.encodeToString(persisted)) }
        mutable.value = value; loaded = true
    } }
}
