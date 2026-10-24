package com.lliony.poseatlas.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "categories") data class CategoryRow(@PrimaryKey val id: String, val name: String, val code: String, val sort: Int)
@Entity(tableName = "cards", foreignKeys = [ForeignKey(entity = CategoryRow::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)], indices = [Index(value = ["categoryId", "deleted", "sort"]), Index("sort")])
data class CardRow(@PrimaryKey val id: String, val categoryId: String, val code: String, val title: String, val tagsJson: String, val gender: String?, val stylesJson: String?, val notesJson: String, val privateNote: String, val deleted: Boolean, val sourceJson: String?, val sort: Int)
@Entity(tableName = "photos", foreignKeys = [ForeignKey(entity = CardRow::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["cardId", "sort"])])
data class PhotoRow(@PrimaryKey val id: String, val cardId: String, val src: String, val panel: Int?, val ai: Boolean, val width: Int?, val height: Int?, val notesJson: String, val sort: Int)
@Entity(tableName = "albums") data class AlbumRow(@PrimaryKey val id: String, val name: String, val description: String, val sort: Int)
@Entity(tableName = "album_cards", primaryKeys = ["albumId", "cardId"], foreignKeys = [ForeignKey(entity = AlbumRow::class, parentColumns = ["id"], childColumns = ["albumId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = CardRow::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["albumId", "sort"]), Index("cardId")])
data class AlbumCardRow(val albumId: String, val cardId: String, val sort: Int)
@Entity(tableName = "drafts") data class DraftRow(@PrimaryKey val cardId: String, val payloadJson: String, val updatedAt: Long)
@Entity(tableName = "metadata") data class MetadataRow(@PrimaryKey val key: String, val value: String)

@Dao interface AtlasDao {
    @Query("SELECT * FROM categories ORDER BY sort") suspend fun categories(): List<CategoryRow>
    @Query("SELECT * FROM cards ORDER BY sort") suspend fun cards(): List<CardRow>
    @Query("SELECT * FROM photos ORDER BY sort") suspend fun photos(): List<PhotoRow>
    @Query("SELECT * FROM albums ORDER BY sort") suspend fun albums(): List<AlbumRow>
    @Query("SELECT * FROM album_cards ORDER BY sort") suspend fun albumCards(): List<AlbumCardRow>
    @Query("SELECT * FROM drafts WHERE cardId = :id") suspend fun draft(id: String): DraftRow?
    @Query("SELECT * FROM drafts") suspend fun drafts(): List<DraftRow>
    @Query("SELECT value FROM metadata WHERE `key` = :key") suspend fun metadata(key: String): String?
    @Query("SELECT COUNT(*) FROM cards") fun observeCards(): Flow<Int>
    @Upsert suspend fun categories(rows: List<CategoryRow>)
    @Upsert suspend fun cards(rows: List<CardRow>)
    @Upsert suspend fun photos(rows: List<PhotoRow>)
    @Upsert suspend fun albums(rows: List<AlbumRow>)
    @Upsert suspend fun albumCards(rows: List<AlbumCardRow>)
    @Upsert suspend fun draft(row: DraftRow)
    @Upsert suspend fun metadata(row: MetadataRow)
    @Query("DELETE FROM album_cards") suspend fun clearAlbumCards()
    @Query("DELETE FROM albums") suspend fun clearAlbums()
    @Query("DELETE FROM cards") suspend fun clearCards()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM drafts") suspend fun clearDrafts()
    @Query("DELETE FROM photos WHERE cardId = :id") suspend fun clearPhotos(id: String)
    @Query("DELETE FROM drafts WHERE cardId = :id") suspend fun deleteDraft(id: String)
    @Query("DELETE FROM cards WHERE id IN (:ids)") suspend fun deleteCards(ids: Set<String>)
    @Query("UPDATE cards SET deleted = :deleted WHERE id IN (:ids)") suspend fun setDeleted(ids: Set<String>, deleted: Boolean)
    @Query("UPDATE cards SET categoryId = :target WHERE categoryId = :source") suspend fun moveCategory(source: String, target: String)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: String)
}

@Database(entities = [CategoryRow::class, CardRow::class, PhotoRow::class, AlbumRow::class, AlbumCardRow::class, DraftRow::class, MetadataRow::class], version = 1, exportSchema = true)
abstract class AtlasDatabase : RoomDatabase() { abstract fun dao(): AtlasDao }
