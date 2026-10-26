package com.lliony.poseatlas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lliony.poseatlas.data.local.WorkspaceRepository
import com.lliony.poseatlas.data.backup.BackupRepository
import com.lliony.poseatlas.data.print.PdfExporter
import com.lliony.poseatlas.data.ai.AiConfigStore
import com.lliony.poseatlas.data.ai.AiRepository
import com.lliony.poseatlas.domain.logic.fillBlankFields
import com.lliony.poseatlas.data.prefs.Preferences
import com.lliony.poseatlas.data.prefs.PreferencesStore
import com.lliony.poseatlas.domain.model.Card
import com.lliony.poseatlas.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import android.net.Uri
import java.io.File
import javax.inject.Inject
import com.lliony.poseatlas.data.image.ImageStore
import com.lliony.poseatlas.data.ai.AiProfile
import com.lliony.poseatlas.data.print.PrintOptions
import com.lliony.poseatlas.domain.logic.Recommendations
import com.lliony.poseatlas.domain.logic.NormalizedCrop
import com.lliony.poseatlas.domain.logic.AiProfileRules
import com.lliony.poseatlas.domain.model.Photo

data class UiMessage(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)
@HiltViewModel class AtlasViewModel @Inject constructor(private val repository: WorkspaceRepository, private val preferencesStore: PreferencesStore, private val backup: BackupRepository, private val pdf: PdfExporter, private val ai: AiRepository, val aiConfig: AiConfigStore) : ViewModel() {
    val state = repository.state
    // 只读显示解码柄：界面取图/解码用它，写入不经过它（见下方命名动作）。
    val images: ImageStore = repository.images
    val backupProgress: StateFlow<String?> = backup.progress
    val preferences = preferencesStore.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Preferences())
    val loading = MutableStateFlow(true)
    val loadFailed = MutableStateFlow(false)
    val busy = MutableStateFlow(false)

    // 编辑器会话专用写/读 seam：把持久化挡在会话事务（C2 EditorSessionController）之外。
    private val editorWorkspace = object : EditorWorkspace {
        override suspend fun draft(key: String): Card? = withContext(Dispatchers.IO) { repository.store.draft(key) }
        override suspend fun saveDraft(key: String, card: Card) = repository.mutate { saveDraft(key, card) }
        override suspend fun saveCard(key: String, card: Card) = repository.mutate(cleanup = true) { saveCard(card, key) }
        override suspend fun deleteDraft(key: String) = repository.mutate(cleanup = true) { deleteDraft(key) }
    }
    private val controller = EditorSessionController(viewModelScope, editorWorkspace, images, { message(it) })
    val editor: StateFlow<EditorSession?> = controller.editor
    val recommendationSession = RecommendationSession()
    val cropSession: StateFlow<CropSession?> = controller.cropSession
    val cropError: StateFlow<String?> = controller.cropError

    fun beginCrop(photo: Photo) { if (!busy.value) controller.beginCrop(photo) }
    fun cancelCrop() { if (!busy.value) controller.cancelCrop() }
    fun applyCrop(region: NormalizedCrop) = action { controller.applyCrop(region) }
    private val messages = Channel<String>(Channel.BUFFERED)
    private val undoMessages = Channel<UiMessage>(Channel.BUFFERED)
    val events = merge(messages.receiveAsFlow().map { UiMessage(it) }, undoMessages.receiveAsFlow())
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
        edit(fillBlankFields(current, generated))
    }
    fun undoFill() { editorUndo.value?.let(::edit); editorUndo.value = null }
    fun message(text: String) { viewModelScope.launch { messages.send(text) } }
    fun trash(ids: Set<String>) = action {
        repository.mutate { setDeleted(ids, true) }
        undoMessages.send(UiMessage("已移入回收站", "撤销") { restoreCards(ids) })
    }
    // 工作区写路径统一走命名动作，屏幕不再直接持有 repository 语义。
    fun restoreCards(ids: Set<String>, message: String = "已恢复") = action(message) { repository.mutate { setDeleted(ids, false) } }
    fun toggleTrash(ids: Set<String>, deleted: Boolean) = action(if (deleted) "已移入回收站" else "已恢复卡片") { repository.mutate { setDeleted(ids, deleted) } }
    fun deletePermanently(ids: Set<String>) = action("已永久删除") { repository.mutate(cleanup = true) { deletePermanently(ids) } }
    fun addCardsToAlbum(albumId: String, ids: List<String>) = action("已加入卡片册") { repository.mutate { addCardsToAlbum(albumId, ids) } }
    fun setAlbumCards(albumId: String, ids: List<String>) = action { repository.mutate { setAlbumCards(albumId, ids) } }
    fun moveCardInAlbum(albumId: String, cardId: String, up: Boolean) = action { repository.mutate { moveCardInAlbum(albumId, cardId, up) } }
    fun removeCardsFromAlbum(albumId: String, ids: List<String>) = action { repository.mutate { removeCardsFromAlbum(albumId, ids) } }
    fun reorderAlbums(albumId: String, up: Boolean) = action { repository.mutate { reorderAlbums(albumId, up) } }
    fun reorderCategories(categoryId: String, up: Boolean) = action { repository.mutate { reorderCategories(categoryId, up) } }
    fun saveAlbum(id: String, name: String, description: String) = action { repository.mutate { saveAlbum(id, name, description) } }
    fun deleteAlbum(albumId: String) = action { repository.mutate { deleteAlbum(albumId) } }
    fun saveCategory(category: Category) = action { repository.mutate { saveCategory(category) } }
    fun deleteCategory(source: String, target: String) = action { repository.mutate { deleteCategory(source, target) } }
    // 副作用收进命名动作，屏幕不再持有 pdf/ai/import 柄（X1）。
    suspend fun importPhoto(uri: Uri): Photo = withContext(Dispatchers.IO) { repository.images.import(uri) }
    suspend fun importPhotos(uris: List<Uri>): List<Photo> = withContext(Dispatchers.IO) { uris.map { repository.images.import(it) } }
    suspend fun testAi(profile: AiProfile): String = withContext(Dispatchers.IO) { ai.request(profile, "请只回复 OK。", "连接测试") }
    suspend fun requestRecommendation(profile: AiProfile, cards: List<Card>, description: String, mode: String, photo: Photo?): Recommendations = withContext(Dispatchers.IO) { ai.recommend(profile, cards, state.value.categories, description, mode, photo) }
    suspend fun renderPdf(cards: List<Card>, options: PrintOptions): File = withContext(Dispatchers.IO) { pdf.render(cards, state.value.categories, options) }
    suspend fun sharePdfImages(card: Card, photo: Photo): List<File> = withContext(Dispatchers.IO) { pdf.shareImages(card, photo, state.value.categories.find { it.id == card.categoryId }?.name.orEmpty()) }
    suspend fun exportBackup(uri: Uri, includeImages: Boolean): Int = backup.export(uri, includeImages)
    suspend fun importBackup(uri: Uri, merge: Boolean): String = backup.import(uri, merge)
    fun preferences(value: Preferences) = action { preferencesStore.update(value) }
    fun openEditor(id: String) = action {
        controller.open(id, state.value.cards, state.value.categories)
        editorUndo.value = null
    }
    fun edit(card: Card) = controller.edit(card)
    fun saveEditor(done: () -> Unit) = action("卡片已保存") { controller.save(done) }
    fun leaveEditor(keep: Boolean, done: () -> Unit) = action { controller.leave(keep, done) }
    // AI 配置写入收成命名动作，屏幕只读 aiConfig.state（C3）。
    fun useForRecommend(id: String) = action { aiConfig.useForRecommend(id) }
    fun useForDraft(id: String) = action { aiConfig.useForDraft(id) }
    fun upsertAiProfile(profile: AiProfile) = action { aiConfig.upsert(AiProfileRules.normalize(profile)) }
    fun deleteAiProfile(id: String) = action { aiConfig.delete(id) }
    fun clearAiConfig() = action("AI 配置已清除") { aiConfig.clear() }
}
