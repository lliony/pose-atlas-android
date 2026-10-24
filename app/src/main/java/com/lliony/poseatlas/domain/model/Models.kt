package com.lliony.poseatlas.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val atlasJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
val noteLabels = linkedMapOf("pose" to "动作步骤", "camera" to "拍摄机位", "composition" to "构图思路", "light" to "光线建议", "cautions" to "注意事项")
val genders = listOf("女性", "男性", "多人")
val styles = listOf("甜美", "优雅", "御姐", "清爽", "斯文", "成熟", "酷飒", "自然")

@Serializable data class Notes(val pose: String = "", val camera: String = "", val composition: String = "", val light: String = "", val cautions: String = "") {
    fun values() = listOf(pose, camera, composition, light, cautions)
    fun with(index: Int, value: String): Notes = from(values().toMutableList().apply { set(index, value) })
    companion object { fun from(values: List<String>) = Notes(values[0], values[1], values[2], values[3], values[4]) }
}
@Serializable data class Photo(val id: String, val src: String, val panel: Int? = null, val ai: Boolean = false, val notes: Notes = Notes(), val width: Int? = null, val height: Int? = null)
@Serializable data class Source(val url: String = "", val title: String = "", val author: String = "", val basis: String? = null, val evidence: String? = null)
@Serializable data class Category(val id: String, val name: String, val code: String)
@Serializable data class Card(val id: String, val categoryId: String, val code: String, val title: String, val tags: List<String> = emptyList(), val gender: String? = null, val styles: List<String>? = null, val notes: Notes = Notes(), val privateNote: String = "", val images: List<Photo> = emptyList(), val deleted: Boolean = false, val source: Source? = null)
@Serializable data class Album(val id: String, val name: String, val description: String = "", val cardIds: List<String> = emptyList())
@Serializable data class State(val categories: List<Category>, val cards: List<Card>, val albums: List<Album>) {
    companion object { val Empty = State(emptyList(), emptyList(), emptyList()) }
}
data class CardFilter(val query: String = "", val category: String? = null, val gender: String? = null, val style: String? = null, val ratio: String? = null, val deleted: Boolean = false)
