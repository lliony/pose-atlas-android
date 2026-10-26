package com.lliony.poseatlas.domain.logic

import com.lliony.poseatlas.domain.model.*
import kotlin.math.abs

/** 每张卡片的图片数量上限；valid 门禁与界面（导入/按钮）共用这一处持主。 */
const val MAX_IMAGES_PER_CARD = 20

fun resolveNotes(card: Card, photo: Photo): Notes = Notes.from(photo.notes.values().zip(card.notes.values()) { image, general -> image.trim().ifEmpty { general } })

fun nextCardCode(category: Category, cards: List<Card>): String {
    val pattern = Regex("^${Regex.escape(category.code)}-(\\d+)$")
    val max = cards.mapNotNull { pattern.matchEntire(it.code)?.groupValues?.get(1)?.toIntOrNull() }.maxOrNull() ?: 0
    require(max < Int.MAX_VALUE) { "编号已超出上限" }
    return "${category.code}-${(max + 1).toString().padStart(3, '0')}"
}

fun validateState(state: State) {
    require(state.cards.size <= 2000 && state.categories.size <= 200 && state.albums.size <= 200) { "资料数量超出当前上限" }
    val ids = mutableSetOf<String>()
    fun id(value: String) { require(value.matches(Regex("^[a-zA-Z0-9_-]{1,80}$")) && ids.add(value)) { "标识重复或无效" } }
    fun text(value: String, max: Int) { require(value.length <= max) { "文字过长，最多 $max 字" } }
    fun notes(value: Notes) = value.values().forEach { text(it, 2000) }
    state.categories.forEach { id(it.id); text(it.name, 60); text(it.code, 12); require(it.name.isNotBlank()) { "分类名不能为空" } }
    val categories = state.categories.map { it.id }.toSet()
    state.cards.forEach { c ->
        id(c.id); text(c.title, 100); text(c.code, 40); notes(c.notes); text(c.privateNote, 4000)
        require(c.categoryId in categories) { "分类不存在" }
        require(c.tags.size <= 12) { "标签不能超过 12 个" }; c.tags.forEach { text(it, 40) }
        require(c.gender == null || c.gender == "" || c.gender in genders) { "性别分类无效" }
        require(c.styles == null || (c.styles.all { it in styles } && c.styles.distinct() == c.styles)) { "气质风格无效" }
        require(c.images.size in 1..MAX_IMAGES_PER_CARD) { "每张卡片需 1 至 $MAX_IMAGES_PER_CARD 张图片" }
        c.images.forEach { p ->
            id(p.id); notes(p.notes)
            require(MediaRef.isValid(p.src)) { "图片地址不正确" }
            require(p.panel == null || p.panel in 0..(if (p.src == "/images/pose-reference-styles.png") 5 else 2)) { "图片分栏无效" }
            require(listOfNotNull(p.width, p.height).all { it in 1..30000 }) { "图片尺寸无效" }
        }
    }
    val cards = state.cards.map { it.id }.toSet()
    state.albums.forEach { a ->
        id(a.id); text(a.name, 100); text(a.description, 1000)
        require(a.cardIds.size <= 2000 && a.cardIds.distinct() == a.cardIds && a.cardIds.all { it in cards }) { "册子引用无效" }
    }
}

fun photoAspect(photo: Photo): Double = when {
    photo.width != null && photo.height != null -> photo.width.toDouble() / photo.height
    photo.panel != null -> if (photo.src.endsWith("pose-reference-styles.png")) 0.75 else 1.0
    photo.src.endsWith("pose-reference-styles.png") -> 2304.0 / 2048
    MediaRef.isSeed(photo.src) -> 3.0
    else -> 0.75
}

fun filterCards(cards: List<Card>, filter: CardFilter) = cards.filter { c ->
    c.deleted == filter.deleted && (filter.category == null || c.categoryId == filter.category) &&
        (filter.gender == null || c.gender == filter.gender) && (filter.style == null || filter.style in c.styles.orEmpty()) &&
        (filter.query.isBlank() || (listOf(c.title, c.code, c.gender.orEmpty()) + c.tags + c.styles.orEmpty()).joinToString(" ").contains(filter.query.trim(), true)) &&
        (filter.ratio == null || c.images.any { p ->
            val parts = filter.ratio.split(':').map { it.toDouble() }
            abs(photoAspect(p) / (parts[0] / parts[1]) - 1) < 0.05
        })
}

fun mergeStates(current: State, incoming: State): State {
    fun <T> merge(a: List<T>, b: List<T>, id: (T) -> String): List<T> = a.associateByTo(linkedMapOf(), id).apply { b.forEach { put(id(it), it) } }.values.toList()
    return State(merge(current.categories, incoming.categories) { it.id }, merge(current.cards, incoming.cards) { it.id }, merge(current.albums, incoming.albums) { it.id })
}
