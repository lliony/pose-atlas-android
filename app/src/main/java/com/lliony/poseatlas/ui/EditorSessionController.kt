package com.lliony.poseatlas.ui

import com.lliony.poseatlas.data.image.ImageStore
import com.lliony.poseatlas.domain.logic.EditorRules
import com.lliony.poseatlas.domain.logic.NormalizedCrop
import com.lliony.poseatlas.domain.model.Card
import com.lliony.poseatlas.domain.model.Category
import com.lliony.poseatlas.domain.model.Photo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** 一次编辑器会话。key 既作草稿键、也即卡片 id（新建时 key="new"，落库前换成 UUID）。 */
data class EditorSession(val key: String, val card: Card, val original: Card, val restored: Boolean = false, val dirty: Boolean = false)

/** 编辑器会话的读写 seam：把持久化（WorkspaceStore）挡在会话事务之外，测试可替换成内存实现。 */
interface EditorWorkspace {
    suspend fun draft(key: String): Card?
    suspend fun saveDraft(key: String, card: Card)
    suspend fun saveCard(key: String, card: Card)
    suspend fun deleteDraft(key: String)
}

/**
 * 编辑器会话模块：拥有 card/original/draft/crop 的合并状态与过渡，是裁切事务、
 * 400ms 草稿防抖与卡片图唯一持有者。busy 闸/消息仍由 VM 编排，本模块不碰它们。
 */
internal class EditorSessionController(
    private val scope: CoroutineScope,
    private val workspace: EditorWorkspace,
    private val images: ImageStore,
    private val onError: (String) -> Unit,
) {
    private val _editor = MutableStateFlow<EditorSession?>(null)
    val editor: StateFlow<EditorSession?> = _editor.asStateFlow()
    private val _cropSession = MutableStateFlow<CropSession?>(null)
    val cropSession: StateFlow<CropSession?> = _cropSession.asStateFlow()
    private val _cropError = MutableStateFlow<String?>(null)
    val cropError: StateFlow<String?> = _cropError.asStateFlow()
    private var draftJob: Job? = null

    /** 打开编辑：库中已有→带草稿（若存在）恢复；否则用所选分类新建一张卡。 */
    suspend fun open(id: String, cards: List<Card>, categories: List<Category>) {
        draftJob?.cancelAndJoin()
        val target = EditorRules.openTarget(id, cards, categories)
        val original = when (target) {
            is EditorRules.OpenTarget.Existing -> target.card
            is EditorRules.OpenTarget.Create -> Card(UUID.randomUUID().toString(), target.category.id, target.code, "")
        }
        val draft = workspace.draft(id)
        _editor.value = EditorSession(id, draft ?: original, original, restored = draft != null, dirty = draft != null)
    }

    /** 编辑改动：置脏并排一个 400ms 草稿防抖（窗口内合并，取消旧的）。 */
    fun edit(card: Card) {
        val session = _editor.value ?: return
        _editor.value = session.copy(card = card, dirty = true)
        draftJob?.cancel()
        draftJob = scope.launch {
            delay(400)
            try { workspace.saveDraft(session.key, card) }
            catch (e: Exception) { if (e is CancellationException) throw e; onError("草稿保存失败，请手动保存") }
        }
    }

    /** 保存：先落掉待写入的草稿 job，再把当前卡写入库（清掉同键草稿与孤儿图）。 */
    suspend fun save(done: () -> Unit) {
        draftJob?.cancelAndJoin()
        val session = _editor.value ?: return
        workspace.saveCard(session.key, session.card)
        _editor.value = null
        done()
    }

    /** 离开编辑器：keep→留存草稿；否则丢弃（并清孤儿图）。 */
    suspend fun leave(keep: Boolean, done: () -> Unit) {
        draftJob?.cancelAndJoin()
        _editor.value?.let { session ->
            if (keep) workspace.saveDraft(session.key, session.card) else workspace.deleteDraft(session.key)
        }
        _editor.value = null
        done()
    }

    fun beginCrop(photo: Photo) {
        val card = _editor.value?.card ?: return
        if (!EditorRules.sameCardAndImage(card, card.id, photo)) return
        _cropError.value = null
        _cropSession.value = CropSession(card.id, photo)
    }

    fun cancelCrop() {
        _cropSession.value = null
        _cropError.value = null
    }

    /** 裁切事务：NonCancellable 完成裁清 → 身份重查 → 换图；失败时清掉孤儿文件。 */
    suspend fun applyCrop(region: NormalizedCrop) {
        val target = _cropSession.value ?: return
        _cropError.value = null
        var generated: Photo? = null
        var applied = false
        try {
            withContext(Dispatchers.IO + NonCancellable) { generated = images.crop(target.photo, region) }
            val output = generated!!
            val latest = _editor.value?.card
            if (!currentCoroutineContext().isActive || _cropSession.value != target || !EditorRules.sameCardAndImage(latest, target.cardId, target.photo)) {
                currentCoroutineContext().ensureActive()
                _cropError.value = "图片已变更，请返回后重新选择裁切图片"
                return
            }
            edit(latest!!.copy(images = latest.images.map { if (it.id == target.photo.id) output.copy(notes = it.notes, ai = it.ai) else it }))
            applied = true
            _cropSession.value = null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _cropError.value = "裁切保存失败，原图未更改。请检查存储空间后重试。"
        } finally {
            if (!applied) generated?.let { output -> withContext(Dispatchers.IO + NonCancellable) { images.file(output.src)?.delete() } }
        }
    }
}