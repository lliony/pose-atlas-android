package com.lliony.poseatlas.data.local

import com.lliony.poseatlas.data.image.ImageStore
import com.lliony.poseatlas.data.prefs.PreferencesStore
import com.lliony.poseatlas.domain.model.State
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 工作区仓库的可注入读取缝：让测试/宿主代码能从应用组件拿到同一个单例。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkspaceEntryPoint {
    fun repository(): WorkspaceRepository
}

@Singleton class WorkspaceRepository @Inject constructor(val store: WorkspaceStore, private val seed: SeedLoader, val images: ImageStore, private val preferences: PreferencesStore) {
    private val mutable = MutableStateFlow(State.Empty)
    val state = mutable.asStateFlow()
    private val mutex = Mutex()
    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { store.seedIfNeeded(seed::load); preferences.markSeeded(); mutable.value = store.read() } }
    suspend fun mutate(cleanup: Boolean = false, action: suspend WorkspaceStore.() -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock { store.action(); mutable.value = store.read(); if (cleanup) images.cleanup(store.referencedSources()) }
    }
}
