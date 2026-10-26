package com.lliony.poseatlas.data.backup

import com.lliony.poseatlas.domain.logic.validateState
import com.lliony.poseatlas.domain.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.io.*
import java.time.Instant
import java.util.Base64
import java.util.zip.*

@Serializable data class BackupMeta(val format: String = "pose-atlas-backup", val version: Int = 1, val exportedAt: String = "", val state: State, val images: Map<String, String> = emptyMap())
data class DecodedBackup(val state: State, val images: Map<String, ByteArray>)

object BackupCodec {
    const val MAX_IMAGE = 25L * 1024 * 1024
    const val MAX_JSON = 512L * 1024 * 1024
    const val MAX_TOTAL = 8L * 1024 * 1024 * 1024
    fun jsonMemoryBudget(): Long = minOf(MAX_JSON, 32L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 12)
    private fun sanitized(state: State) = state.copy(cards = state.cards.map { it.copy(source = it.source?.copy(basis = null, evidence = null)) })
    fun metadata(state: State, images: Map<String, String>): String = atlasJson.encodeToString(BackupMeta(exportedAt = Instant.now().toString(), state = sanitized(state), images = images))
    fun parse(text: String): BackupMeta {
        require(text.length <= jsonMemoryBudget()) { "备份文字超过当前设备内存预算，请拆分后导入" }
        val envelope = try { atlasJson.parseToJsonElement(text).jsonObject } catch (e: Exception) { throw IllegalArgumentException("备份 JSON 格式不正确", e) }
        require(envelope["format"]?.jsonPrimitive?.content == "pose-atlas-backup" && envelope["version"]?.jsonPrimitive?.intOrNull == 1) { "备份格式或版本不受支持" }
        val meta = try { atlasJson.decodeFromString<BackupMeta>(text) } catch (e: Exception) { throw IllegalArgumentException("备份 JSON 格式不正确", e) }
        require(meta.format == "pose-atlas-backup" && meta.version == 1) { "备份格式或版本不受支持" }
        val ids = meta.state.cards.map { it.id }.toSet()
        val state = meta.state.copy(albums = meta.state.albums.map { it.copy(cardIds = it.cardIds.filter { id -> id in ids }.distinct()) })
        validateState(state)
        require(meta.images.keys.all { MediaRef.isValid(it) }) { "图片键不正确" }
        return meta.copy(state = state)
    }
    fun validImagePath(path: String): Boolean = path.startsWith("images/") && !path.contains("..") && !path.contains('\\') && !path.contains('\u0000') && path.matches(Regex("images/[a-zA-Z0-9_.-]+"))
    fun inspect(zip: ZipFile): BackupMeta {
        var total = 0L; var count = 0; val names = mutableSetOf<String>()
        zip.entries().asSequence().forEach { entry ->
            count++; require(count <= 50_000 && names.add(entry.name)) { "ZIP 条目过多或重复" }
            require(entry.name in listOf("README.txt", "pose-atlas.json") || validImagePath(entry.name)) { "ZIP 路径无效" }
            require(!entry.isDirectory && entry.size >= 0) { "ZIP 条目无效" }
            val max = if (entry.name == "pose-atlas.json") MAX_JSON else MAX_IMAGE
            require(entry.size <= max) { "ZIP 单条大小超限" }
            total += entry.size; require(total <= MAX_TOTAL) { "ZIP 解压总量超限" }
            require(entry.size == 0L || (entry.compressedSize > 0 && entry.size.toDouble() / entry.compressedSize <= 200)) { "ZIP 压缩比超过 200:1" }
        }
        val entry = zip.getEntry("pose-atlas.json") ?: throw IllegalArgumentException("备份包缺少 pose-atlas.json")
        require(entry.size <= jsonMemoryBudget()) { "备份文字超过当前设备内存预算，请拆分后导入" }
        val meta = parse(readChecked(zip, entry, jsonMemoryBudget()).toString(Charsets.UTF_8))
        require(meta.images.values.all(::validImagePath)) { "图片索引路径无效" }
        return meta
    }
    fun readChecked(zip: ZipFile, entry: ZipEntry, limit: Long): ByteArray = ByteArrayOutputStream().also { out -> copyChecked(zip, entry, out, limit) }.toByteArray()
    fun copyChecked(zip: ZipFile, entry: ZipEntry, output: OutputStream, limit: Long = MAX_IMAGE) {
        val crc = CRC32(); var length = 0L
        zip.getInputStream(entry).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; length += n; require(length <= limit) { "解压数据超限" }; crc.update(buffer, 0, n); output.write(buffer, 0, n) }
        }
        require(length == entry.size && crc.value == entry.crc) { "ZIP 条目校验失败" }
    }
    fun write(output: OutputStream, state: State, images: Map<String, ByteArray>) = writeStreaming(output, state, images.keys.toList()) { images.getValue(it).inputStream() }
    fun writeStreaming(output: OutputStream, state: State, sources: List<String>, checkCancelled: () -> Unit = {}, open: (String) -> InputStream) {
        validateState(state)
        val index = sources.mapIndexed { i, src -> src to "images/${(i + 1).toString().padStart(4, '0')}.jpg" }.toMap()
        ZipOutputStream(output).use { zip ->
            // Stored entries prevent highly repetitive text/images from violating our own compression-ratio guard.
            fun entry(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name).apply { method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size; crc = CRC32().apply { update(bytes) }.value }); zip.write(bytes); zip.closeEntry() }
            entry("README.txt", "姿势图鉴备份包\n通过应用设置导入此 ZIP。".toByteArray())
            entry("pose-atlas.json", metadata(state, index).toByteArray())
            index.forEach { (src, path) ->
                checkCancelled()
                // At most one normalized image is held at a time (25MB upper bound).
                val bytes = open(src).use { input -> val out = ByteArrayOutputStream(); val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) { checkCancelled(); val n = input.read(buffer); if (n < 0) break; total += n; require(total <= MAX_IMAGE) { "图片超过备份限制" }; out.write(buffer, 0, n) }; out.toByteArray() }
                entry(path, bytes)
            }
        }
    }
    fun read(bytes: ByteArray): DecodedBackup {
        if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
            val file = File.createTempFile("atlas-backup-", ".zip")
            try { file.writeBytes(bytes); ZipFile(file).use { zip ->
                val meta = inspect(zip)
                return DecodedBackup(meta.state, meta.images.mapNotNull { (src, path) -> zip.getEntry(path)?.let { src to readChecked(zip, it, MAX_IMAGE) } }.toMap())
            } } finally { file.delete() }
        }
        require(bytes.size <= jsonMemoryBudget()) { "旧 JSON 超过当前设备内存预算，请改用 ZIP" }
        val meta = parse(bytes.toString(Charsets.UTF_8)); var total = 0L
        val images = meta.images.mapValues { (_, data) ->
            require(data.startsWith("data:image/") && data.contains(";base64,")) { "图片数据不正确" }
            val decoded = Base64.getDecoder().decode(data.substringAfter(',')); total += decoded.size
            require(decoded.size <= MAX_IMAGE && total <= MAX_TOTAL) { "图片大小超限" }; decoded
        }
        return DecodedBackup(meta.state, images)
    }
}
