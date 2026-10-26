package com.lliony.poseatlas.data.backup

import android.content.Context
import android.net.Uri
import com.lliony.poseatlas.data.local.WorkspaceRepository
import com.lliony.poseatlas.domain.logic.mergeStates
import com.lliony.poseatlas.domain.logic.validateState
import com.lliony.poseatlas.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.inject.Inject

class BackupRepository @Inject constructor(@ApplicationContext private val context: Context, private val repository: WorkspaceRepository) {
    val progress = MutableStateFlow<String?>(null)
    suspend fun export(uri: Uri, includeImages: Boolean): Int = withContext(Dispatchers.IO) {
        var missing = 0
        val temp = File.createTempFile("export-", ".zip", context.cacheDir)
        val operationContext = currentCoroutineContext()
        try {
            repository.mutate {
                val state = read()
                val sources = state.cards.flatMap { it.images }.map { it.src }.distinct().filter { MediaRef.isImported(it) }
                val present = sources.filter { repository.images.file(it)?.exists() == true }
                missing = sources.size - present.size
                var completed = 0
                progress.value = "正在打包备份"
                temp.outputStream().use { output -> BackupCodec.writeStreaming(output, state, if (includeImages) present else emptyList(), { operationContext.ensureActive() }) { src ->
                    progress.value = "正在打包图片 ${++completed}/${present.size}"
                    repository.images.open(src)
                } }
            }
            ensureActive()
            context.contentResolver.openOutputStream(uri, "wt")?.use { out -> temp.inputStream().use { it.copyTo(out) } } ?: error("无法写入所选文件")
            missing
        } finally { temp.delete(); progress.value = null }
    }
    suspend fun import(uri: Uri, merge: Boolean): String = withContext(Dispatchers.IO) {
        val workspace = File(context.cacheDir, "import/${UUID.randomUUID()}").apply { mkdirs() }
        val input = File(workspace, "input")
        val staged = linkedMapOf<String, File>()
        try {
            progress.value = "正在读取备份文件"
            context.contentResolver.openInputStream(uri)?.use { stream -> input.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024); var total = 0L
                while (true) { ensureActive(); val n = stream.read(buffer); if (n < 0) break; total += n; require(total <= BackupCodec.MAX_TOTAL + BackupCodec.MAX_JSON) { "备份文件过大" }; require(workspace.usableSpace > 200L * 1024 * 1024 + n) { "存储空间不足" }; out.write(buffer, 0, n) }
            } } ?: error("无法读取备份")
            val sizes = mutableMapOf<String, Pair<Int, Int>>()
            fun normalize(src: String, file: File) {
                if (!MediaRef.isImported(src)) return
                val photo = file.inputStream().use(repository.images::import)
                val normalized = repository.images.file(photo.src)!!
                val target = File(workspace, "stage-${staged.size}.jpg")
                try { normalized.copyTo(target); staged[src] = target; sizes[src] = photo.width!! to photo.height!! } finally { normalized.delete() }
            }
            val isZip = input.inputStream().use { it.read() == 0x50 && it.read() == 0x4b }
            val incoming: State
            if (isZip) ZipFile(input).use { zip ->
                val meta = BackupCodec.inspect(zip)
                val total = zip.entries().asSequence().sumOf { it.size }
                require(workspace.usableSpace > total + 200L * 1024 * 1024) { "存储空间不足" }
                meta.images.forEach { (src, path) ->
                    ensureActive()
                    progress.value = "正在处理图片 ${staged.size + 1}/${meta.images.size}"
                    val entry = zip.getEntry(path)
                    if (entry != null) {
                        val image = File(workspace, "entry")
                        image.outputStream().use { BackupCodec.copyChecked(zip, entry, it) }
                        normalize(src, image); image.delete()
                    }
                }
                incoming = meta.state
            } else {
                require(input.length() <= BackupCodec.jsonMemoryBudget()) { "旧 JSON 超过当前设备内存预算，请从 Web 导出 ZIP 后导入" }
                val parsed = BackupCodec.read(input.readBytes())
                parsed.images.forEach { (src, bytes) -> ensureActive(); val image = File(workspace, "entry"); image.writeBytes(bytes); normalize(src, image); image.delete() }
                incoming = parsed.state
            }
            val normalizedState = incoming.copy(cards = incoming.cards.map { c -> c.copy(images = c.images.map { p -> sizes[p.src]?.let { p.copy(width = it.first, height = it.second) } ?: p }) })
            var result = State.Empty; var missing = 0
            ensureActive()
            progress.value = "正在提交资料"
            // Once commit starts, finish or restore both files and database before honoring cancellation.
            withContext(NonCancellable) { repository.mutate {
                val old = read()
                result = if (merge) mergeStates(old, normalizedState) else normalizedState
                validateState(result)
                val originals = linkedMapOf<File, File?>()
                try {
                    staged.forEach { (src, stage) ->
                        val target = repository.images.file(src) ?: throw IllegalArgumentException("图片键无效")
                        val saved = if (target.exists()) File(workspace, "old-${originals.size}.jpg").also { target.copyTo(it) } else null
                        originals[target] = saved
                        stage.copyTo(target, overwrite = true)
                    }
                    replace(result, clearDrafts = !merge)
                } catch (e: Exception) {
                    originals.forEach { (target, saved) -> if (saved == null) target.delete() else saved.copyTo(target, overwrite = true) }
                    throw e
                }
                // Text-only replacement deliberately removes previous user images, after database success.
                val refs = result.cards.flatMap { it.images }.map { it.src }.toSet()
                val kept = if (merge) referencedSources() else refs.intersect(staged.keys)
                repository.images.cleanup(kept)
                missing = refs.count { MediaRef.isImported(it) && repository.images.file(it)?.exists() != true }
            } }
            "导入完成：${result.cards.size} 张卡片、${result.categories.size} 个分类、${result.albums.size} 个卡片册；缺失 $missing 张图片"
        } finally { workspace.deleteRecursively(); progress.value = null }
    }
}
