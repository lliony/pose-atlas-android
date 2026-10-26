package com.lliony.poseatlas.domain.model

import java.util.UUID

/**
 * 「图片地址」的唯一持主：种子资源 `/images/` 下的 png 与导入文件 `/api/media/{uuid}` 两套惯例只在此声明，
 * 其余五处（校验 / 落盘 / 备份 / 展示）一律引用，不再各自硬编码。
 */
object MediaRef {
    const val MEDIA_PREFIX = "/api/media/"
    const val SEED_PREFIX = "/images/"

    private val IMPORTED = Regex("^/api/media/([a-zA-Z0-9_-]+)$")
    private val SEED = Regex("^/images/(street|cafe|park|sea|indoor|stairs|pose-reference-styles)\\.png$")

    fun isImported(src: String) = src.startsWith(MEDIA_PREFIX)
    fun isSeed(src: String) = src.startsWith(SEED_PREFIX)
    fun isValid(src: String) = IMPORTED.matches(src) || SEED.matches(src)
    /** 仅对导入文件：取出 {uuid} 落盘文件名；非导入地址返回 null。 */
    fun mediaFileName(src: String): String? = IMPORTED.matchEntire(src)?.groupValues?.get(1)
    fun newId(): String = "$MEDIA_PREFIX${UUID.randomUUID()}"
}