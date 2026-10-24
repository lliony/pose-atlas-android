package com.lliony.poseatlas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lliony.poseatlas.data.local.WorkspaceRepository
import com.lliony.poseatlas.data.backup.BackupRepository
import com.lliony.poseatlas.data.print.PdfExporter
import com.lliony.poseatlas.data.ai.AiConfigStore
import com.lliony.poseatlas.data.ai.AiRepository
import com.lliony.poseatlas.domain.logic.fillBlankFields
import com.lliony.poseatlas.domain.model.atlasJson
import kotlinx.serialization.encodeToString
import com.lliony.poseatlas.data.prefs.Preferences
import com.lliony.poseatlas.data.prefs.PreferencesStore
import com.lliony.poseatlas.domain.logic.nextCardCode
import com.lliony.poseatlas.domain.model.Card
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

data class EditorSession(val key: String, val card: Card, val original: Card, val restored: Boolean = false, val dirty: Boolean = false)
data class UiMessage(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)
@HiltViewModel class AtlasViewModel @Inject constructor(val repository: WorkspaceRepository, private val preferencesStore: PreferencesStore, val backup: BackupRepository, val pdf: PdfExporter, val ai: AiRepository, val aiConfig: AiConfigStore) : ViewModel() {
    val state = repository.state
    val preferences = preferencesStore.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Preferences())
    val loading = MutableStateFlow(true)
    val loadFailed = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val editor = MutableStateFlow<EditorSession?>(null)
    val recommendationSession = RecommendationSession()
    val cropSession = MutableStateFlow<CropSession?>(null)
    val cropError = MutableStateFlow<String?>(null)
    fun beginCrop(photo: com.lliony.poseatlas.domain.model.Photo) {
        if (busy.value) return
        val card = editor.value?.card ?: return
        if (card.images.none { it.id == photo.id && it.src == photo.src && it.panel == photo.panel }) return
        cropError.value = null
        cropSession.value = CropSession(card.id, photo)
    }
    fun cancelCrop() { if (!busy.value) { cropSession.value = null; cropError.value = null } }
    fun applyCrop(region: com.lliony.poseatlas.domain.logic.NormalizedCrop) = action {
        val target = cropSession.value ?: return@action
        cropError.value = null
        var generated: com.lliony.poseatlas.domain.model.Photo? = null
        var applied = false
        try {
            // Retain the reference inside IO so cancellation on return cannot orphan a completed file.
            withContext(Dispatchers.IO + NonCancellable) { generated = repository.images.crop(target.photo, region) }
            val output = generated!!
            val latest = editor.value?.card
            val same = latest?.id == target.cardId && latest.images.any { it.id == target.photo.id && it.src == target.photo.src && it.panel == target.photo.panel }
            if (!currentCoroutineContext().isActive || cropSession.value != target || !same) {
                currentCoroutineContext().ensureActive()
                cropError.value = "图片已变更，请返回后重新选择裁切图片"
                return@action
            }
            edit(latest!!.copy(images = latest.images.map { if (it.id == target.photo.id) output.copy(notes = it.notes, ai = it.ai) else it }))
            applied = true
            cropSession.value = null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            cropError.value = "裁切保存失败，原图未更改。请检查存储空间后重试。"
        } finally {
            if (!applied) generated?.let { output -> withContext(Dispatchers.IO + NonCancellable) { repository.images.file(output.src)?.delete() } }
        }
    }
    private val messages = Channel<String>(Channel.BUFFERED)
    private val undoMessages = Channel<UiMessage>(Channel.BUFFERED)
    val events = merge(messages.receiveAsFlow().map { UiMessage(it) }, undoMessages.receiveAsFlow())
    private var draftJob: Job? = null
    private var operation: Job? = null
    val editorUndo = MutableStateFlow<Card?>(null)
    init { reload(); viewModelScope.launch { try { aiConfig.load() } catch (e: Exception) { if (e is CancellationException) throw e; messages.send("无法解密 AI 配置，请重新配置模型") } } }
    fun reload() { viewModelScope.launch { loading.value = true; loadFailed.value = false; try { repository.load() } catch (e: Exception) { if (e is CancellationException) throw e; loadFailed.value = true; messages.send("加载失败，请重试") } finally { loading.value = false } } }
    suspend fun readPrintOptions() = preferencesStore.printOptions.first()
    suspend fun savePrintOptions(value: com.lliony.poseatlas.data.print.PrintOptions) { preferencesStore.updatePrintOptions(value) }
    fun action(message: String? = null, block: suspend () -> Unit) {
        if (busy.value) return
        operation = viewModelScope.launch {
            busy.value = true
            try { block(); if (message != null) messages.send(message) }
            catch (e: Exception) { if (e is CancellationException) throw e; messages.send(if (e is IllegalArgumentException) e.message ?: "资料格式不正确" else "操作失败，请检查存储空间或文件后重试") }
            finally { busy.value = false }
        }
    }
    fun cancelOperation() { operation?.cancel() }
    fun fillEditor() = action("已填入空字段，可撤销") {
        val session = editor.value ?: return@action
        val config = aiConfig.state.value
        val profile = config.profiles.find { it.id == config.draftId } ?: throw IllegalArgumentException("请先在设置中添加填卡模型档案")
        val generated = ai.fill(profile, session.card, state.value.categories.find { it.id == session.card.categoryId }?.name.orEmpty())
        val current = editor.value?.takeIf { it.card.id == session.card.id }?.card ?: return@action
        editorUndo.value = current
        edit(fillBlankFields(current, atlasJson.encodeToString(generated)))
    }
    fun undoFill() { editorUndo.value?.let(::edit); editorUndo.value = null }
    fun message(text: String) { viewModelScope.launch { messages.send(text) } }
    fun trash(ids: Set<String>) = action {
        repository.mutate { setDeleted(ids, true) }
        undoMessages.send(UiMessage("已移入回收站", "撤销") { action("已恢复") { repository.mutate { setDeleted(ids, false) } } })
    }
    fun preferences(value: Preferences) = action { preferencesStore.update(value) }
    fun openEditor(id: String) = action {
        draftJob?.cancelAndJoin()
        val original = state.value.cards.find { it.id == id } ?: run {
            val category = state.value.categories.firstOrNull() ?: throw IllegalArgumentException("请先添加分类")
            Card(UUID.randomUUID().toString(), category.id, nextCardCode(category, state.value.cards), "")
        }
        val draft = withContext(Dispatchers.IO) { repository.store.draft(id) }
        editor.value = EditorSession(id, draft ?: original, original, restored = draft != null, dirty = draft != null)
        editorUndo.value = null
    }
    fun edit(card: Card) {
        val session = editor.value ?: return
        editor.value = session.copy(card = card, dirty = true)
        draftJob?.cancel()
        draftJob = viewModelScope.launch { delay(400); try { repository.mutate { saveDraft(session.key, card) } } catch (e: Exception) { if (e is CancellationException) throw e; messages.send("草稿保存失败，请手动保存") } }
    }
    fun saveEditor(done: () -> Unit) = action("卡片已保存") {
        draftJob?.cancelAndJoin()
        val session = editor.value ?: return@action
        repository.mutate(cleanup = true) { saveCard(session.card, session.key) }
        editor.value = null; done()
    }
    fun leaveEditor(keep: Boolean, done: () -> Unit) = action {
        draftJob?.cancelAndJoin()
        editor.value?.let { session -> repository.mutate(cleanup = !keep) { if (keep) saveDraft(session.key, session.card) else deleteDraft(session.key) } }
        editor.value = null; done()
    }
}
