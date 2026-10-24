package com.lliony.poseatlas.data.local

import com.lliony.poseatlas.data.image.ImageStore
import com.lliony.poseatlas.data.prefs.PreferencesStore
import com.lliony.poseatlas.domain.model.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class WorkspaceRepository @Inject constructor(val store: WorkspaceStore, private val seed: SeedLoader, val images: ImageStore, private val preferences: PreferencesStore) {
    private val mutable = MutableStateFlow(State.Empty)
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { store.seedIfNeeded(seed::load); preferences.markSeeded(); mutable.value = store.read() } }
    suspend fun mutate(cleanup: Boolean = false, action: suspend WorkspaceStore.() -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock { store.action(); mutable.value = store.read(); if (cleanup) images.cleanup(store.referencedSources()) }
    }
}
