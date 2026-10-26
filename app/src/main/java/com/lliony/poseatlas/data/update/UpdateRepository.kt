package com.lliony.poseatlas.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** 从固定发布源检查、下载和验证安装包；不接收 AI 服务配置或凭据。 */
class UpdateRepository @Inject constructor(@ApplicationContext private val context: Context) {
    val currentVersion: String get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES)
        .followRedirects(false).followSslRedirects(false).build()

    private fun response(url: String): Response {
        var target = url
        repeat(6) {
            require(trustedUpdateUrl(target)) { "更新服务返回了不可信的地址" }
            val result = client.newCall(Request.Builder().url(target).header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PoseAtlas-Android").build()).execute()
            if (result.code in 300..399) {
                target = result.use { it.header("Location")?.let { location -> it.request.url.resolve(location)?.toString() } }
                    ?: error("更新服务重定向无效")
            } else {
                if (!result.isSuccessful) {
                    val code = result.code; result.close()
                    error(if (code == 403 || code == 429) "检查过于频繁，请稍后重试" else "更新服务不可用（HTTP $code）")
                }
                return result
            }
        }
        error("更新服务重定向过多")
    }

    suspend fun check(): UpdateRelease? = withContext(Dispatchers.IO) {
        response(RELEASES_URL).use { result ->
            val bytes = result.body!!.byteStream().use { it.readBytesBounded(2 * 1024 * 1024) }
            currentCoroutineContext().ensureActive()
            selectUpdate(bytes.toString(Charsets.UTF_8), currentVersion)
        }
    }

    suspend fun download(release: UpdateRelease, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        require(release.url.startsWith(DOWNLOAD_PREFIX) && release.size in 1..MAX_APK_BYTES) { "更新包信息异常" }
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(folder, "update.apk.part")
        val ready = File(folder, "update.apk")
        ready.delete()
        try {
            response(release.url).use { result ->
                var total = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                result.body!!.byteStream().use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= release.size) { "下载包大小与发布信息不符" }
                        output.write(buffer, 0, count); digest.update(buffer, 0, count)
                        progress((total * 100 / release.size).toInt())
                    }
                } }
                require(total == release.size && digest.digest().hex() == release.sha256) { "安装包校验失败，请重新下载" }
            }
            currentCoroutineContext().ensureActive()
            verifyApk(partial, release)
            check(partial.renameTo(ready)) { "无法保存安装包，请检查存储空间" }
            ready
        } finally { partial.delete() }
    }

    /** 签名或包名不符时禁止交给安装器，避免引导用户卸载导致本地资料丢失。 */
    @Suppress("DEPRECATION")
    fun verifyApk(file: File, release: UpdateRelease) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = pm.getPackageInfo(context.packageName, flags)
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error("无法读取安装包，请重新下载")
        require(archive.packageName == context.packageName) { "安装包不属于此应用" }
        require(PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(installed) && archive.versionName == release.version) { "安装包版本不匹配或不是更新版本" }
        fun signatures(info: android.content.pm.PackageInfo): Set<String> {
            val values = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return values.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
        }
        val existing = signatures(installed)
        require(existing.isNotEmpty() && signatures(archive) == existing) { "新版签名与当前应用不同，无法安全覆盖安装。请保留当前应用并先导出备份。" }
    }
}

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "更新响应过大，请稍后重试" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
