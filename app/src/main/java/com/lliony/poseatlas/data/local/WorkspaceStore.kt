package com.lliony.poseatlas.data.local

import androidx.room.withTransaction
import com.lliony.poseatlas.domain.logic.validateState
import com.lliony.poseatlas.domain.logic.mergeStates
import com.lliony.poseatlas.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import javax.inject.Inject

class WorkspaceStore @Inject constructor(private val db: AtlasDatabase) {
    private val dao get() = db.dao()
    suspend fun read(): State = db.withTransaction {
        val photos = dao.photos().groupBy { it.cardId }
        val links = dao.albumCards().groupBy { it.albumId }
        State(dao.categories().map { Category(it.id, it.name, it.code) }, dao.cards().map { c ->
            Card(c.id, c.categoryId, c.code, c.title, atlasJson.decodeFromString(c.tagsJson), c.gender,
                c.stylesJson?.let { atlasJson.decodeFromString(it) }, atlasJson.decodeFromString(c.notesJson), c.privateNote,
                photos[c.id].orEmpty().map { p -> Photo(p.id, p.src, p.panel, p.ai, atlasJson.decodeFromString(p.notesJson), p.width, p.height) }, c.deleted,
                c.sourceJson?.let { atlasJson.decodeFromString(it) })
        }, dao.albums().map { Album(it.id, it.name, it.description, links[it.id].orEmpty().map { link -> link.cardId }) })
    }
    suspend fun seedIfNeeded(seed: () -> State) = db.withTransaction {
        if (dao.metadata("seed_completed") == null) {
            if (dao.categories().isEmpty() && dao.cards().isEmpty()) replace(seed())
            dao.metadata(MetadataRow("seed_completed", "true"))
        }
    }
    suspend fun replace(state: State, clearDrafts: Boolean = true) = db.withTransaction {
        validateState(state)
        dao.clearAlbumCards(); dao.clearAlbums(); dao.clearCards(); dao.clearCategories()
        if (clearDrafts) dao.clearDrafts()
        dao.categories(state.categories.mapIndexed { i, c -> CategoryRow(c.id, c.name, c.code, i) })
        dao.cards(state.cards.mapIndexed { i, c -> c.row(i) })
        dao.photos(state.cards.flatMap { c -> c.photoRows() })
        dao.albums(state.albums.mapIndexed { i, a -> AlbumRow(a.id, a.name, a.description, i) })
        dao.albumCards(state.albums.flatMap { a -> a.cardIds.mapIndexed { i, id -> AlbumCardRow(a.id, id, i) } })
    }
    suspend fun merge(incoming: State) = db.withTransaction { replace(mergeStates(read(), incoming), clearDrafts = false) }
    suspend fun saveCard(card: Card, draftKey: String = card.id) = db.withTransaction {
        require(card.title.isNotBlank()) { "请填写标题" }
        val old = read()
        val index = old.cards.indexOfFirst { it.id == card.id }
        val cards = old.cards.toMutableList().apply { if (index < 0) add(card) else set(index, card) }
        validateState(old.copy(cards = cards))
        dao.cards(listOf(card.row(if (index < 0) cards.lastIndex else index)))
        dao.clearPhotos(card.id); dao.photos(card.photoRows()); dao.deleteDraft(draftKey)
    }
    suspend fun setDeleted(ids: Set<String>, deleted: Boolean) = db.withTransaction { dao.setDeleted(ids, deleted) }
    suspend fun deletePermanently(ids: Set<String>) = db.withTransaction { dao.deleteCards(ids); ids.forEach { dao.deleteDraft(it) } }
    suspend fun deleteCategory(source: String, target: String) = db.withTransaction {
        val categories = dao.categories()
        require(categories.size > 1 && source != target && categories.any { it.id == target } && categories.any { it.id == source }) { "请选择有效的接收分类，至少保留一个分类" }
        dao.moveCategory(source, target)
        dao.drafts().forEach { row ->
            val card = atlasJson.decodeFromString<Card>(row.payloadJson)
            if (card.categoryId == source) dao.draft(row.copy(payloadJson = atlasJson.encodeToString(card.copy(categoryId = target))))
        }
        dao.deleteCategory(source)
    }
    suspend fun saveCategories(categories: List<Category>) = db.withTransaction {
        require(categories.map { it.name.trim() }.distinct().size == categories.size) { "分类名称不能重复" }
        validateState(read().copy(categories = categories))
        dao.categories(categories.mapIndexed { i, c -> CategoryRow(c.id, c.name.trim(), c.code, i) })
    }
    suspend fun saveAlbums(albums: List<Album>) = db.withTransaction {
        validateState(read().copy(albums = albums))
        dao.clearAlbumCards(); dao.clearAlbums()
        dao.albums(albums.mapIndexed { i, a -> AlbumRow(a.id, a.name, a.description, i) })
        dao.albumCards(albums.flatMap { a -> a.cardIds.mapIndexed { i, id -> AlbumCardRow(a.id, id, i) } })
    }
    suspend fun saveDraft(key: String, card: Card) = dao.draft(DraftRow(key, atlasJson.encodeToString(card), System.currentTimeMillis()))
    suspend fun draft(key: String): Card? = dao.draft(key)?.let { atlasJson.decodeFromString(it.payloadJson) }
    suspend fun deleteDraft(key: String) = dao.deleteDraft(key)
    suspend fun referencedSources(): Set<String> = db.withTransaction {
        dao.photos().map { it.src }.toSet() + dao.drafts().flatMap { atlasJson.decodeFromString<Card>(it.payloadJson).images.map { p -> p.src } }
    }
    private fun Card.row(sort: Int) = CardRow(id, categoryId, code, title, atlasJson.encodeToString(tags), gender, styles?.let { atlasJson.encodeToString(it) }, atlasJson.encodeToString(notes), privateNote, deleted, source?.let { atlasJson.encodeToString(it) }, sort)
    private fun Card.photoRows() = images.mapIndexed { i, p -> PhotoRow(p.id, id, p.src, p.panel, p.ai, p.width, p.height, atlasJson.encodeToString(p.notes), i) }
}
