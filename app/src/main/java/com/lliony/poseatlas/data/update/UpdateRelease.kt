package com.lliony.poseatlas.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

internal const val RELEASES_URL = "https://api.github.com/repos/lliony/pose-atlas-android/releases?per_page=100"
internal const val DOWNLOAD_PREFIX = "https://github.com/lliony/pose-atlas-android/releases/download/"
internal const val MAX_APK_BYTES = 200L * 1024 * 1024

@Serializable internal data class ReleaseAsset(val name: String, val browser_download_url: String, val size: Long, val digest: String? = null)
@Serializable internal data class GithubRelease(val tag_name: String, val body: String? = null, val draft: Boolean = false, val assets: List<ReleaseAsset> = emptyList())
data class UpdateRelease(val version: String, val notes: String, val url: String, val size: Long, val sha256: String)

/** 比较项目的语义版本，Beta 的数字序号按数值排序，正式版高于同版本预发布。 */
internal fun compareVersions(left: String, right: String): Int {
    fun parts(value: String): List<String> {
        val match = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z.-]+)?$").matchEntire(value)
            ?: throw IllegalArgumentException("无法识别版本号：$value")
        return match.groupValues.drop(1)
    }
    fun numeric(a: String, b: String): Int = a.trimStart('0').length.compareTo(b.trimStart('0').length).takeIf { it != 0 } ?: a.trimStart('0').compareTo(b.trimStart('0'))
    val a = parts(left); val b = parts(right)
    for (i in 0..2) numeric(a[i], b[i]).takeIf { it != 0 }?.let { return it }
    if (a[3] == b[3]) return 0
    if (a[3].isEmpty()) return 1
    if (b[3].isEmpty()) return -1
    val x = a[3].split('.'); val y = b[3].split('.')
    for (i in 0 until minOf(x.size, y.size)) {
        val xn = x[i].all(Char::isDigit); val yn = y[i].all(Char::isDigit)
        val diff = when {
            xn && yn -> numeric(x[i], y[i])
            xn != yn -> if (xn) -1 else 1
            else -> x[i].compareTo(y[i])
        }
        if (diff != 0) return diff
    }
    return x.size.compareTo(y.size)
}

/** 使用发布列表而非 latest 接口，以包含项目正在分发的 Beta 版本。 */
internal fun selectUpdate(json: String, currentVersion: String): UpdateRelease? {
    compareVersions(currentVersion, currentVersion)
    val releases = Json { ignoreUnknownKeys = true }.decodeFromString<List<GithubRelease>>(json)
    return releases.filter { !it.draft }.mapNotNull { release ->
        if (runCatching { compareVersions(release.tag_name, currentVersion) > 0 }.getOrDefault(false).not()) return@mapNotNull null
        val version = release.tag_name.removePrefix("v")
        val asset = release.assets.singleOrNull { it.name == "pose-atlas-$version-debug.apk" } ?: return@mapNotNull null
        require(asset.browser_download_url == "$DOWNLOAD_PREFIX${release.tag_name}/${asset.name}") { "更新下载地址不可信" }
        require(asset.size in 1..MAX_APK_BYTES) { "更新包大小异常" }
        val digest = asset.digest?.removePrefix("sha256:")
        require(asset.digest?.startsWith("sha256:") == true && digest?.matches(Regex("[a-fA-F0-9]{64}")) == true) { "更新包缺少有效校验值，请稍后重试" }
        UpdateRelease(version, release.body.orEmpty(), asset.browser_download_url, asset.size, digest!!.lowercase())
    }.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
}

internal fun trustedUpdateUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
        uri.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
}.getOrDefault(false)
