package com.lliony.poseatlas.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.lliony.poseatlas.data.local.*
import com.lliony.poseatlas.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class WorkspaceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AtlasDatabase::class.java).allowMainThreadQueries().build()
    private val store = WorkspaceStore(db)
    @After fun close() = db.close()

    @Test fun roundTripPreservesSeedAndOrder() = runTest {
        val seed = SeedLoader(context).load()
        store.replace(seed)
        assertThat(store.read()).isEqualTo(seed)
    }

    @Test fun permanentDeleteRemovesAlbumMembershipAndDraft() = runTest {
        val seed = SeedLoader(context).load()
        store.replace(seed)
        val id = seed.cards.first().id
        store.saveDraft(id, seed.cards.first())
        store.deletePermanently(setOf(id))
        val result = store.read()
        assertThat(result.cards.map { it.id }).doesNotContain(id)
        assertThat(result.albums.flatMap { it.cardIds }).doesNotContain(id)
        assertThat(store.draft(id)).isNull()
    }

    @Test fun categoryMigrationKeepsCodes() = runTest {
        val seed = SeedLoader(context).load()
        store.replace(seed)
        val source = seed.categories[0].id
        val target = seed.categories[1].id
        val draft = seed.cards.first { it.categoryId == source }.copy(title = "未保存草稿")
        store.saveDraft("new", draft)
        store.deleteCategory(source, target)
        assertThat(store.draft("new")).isEqualTo(draft.copy(categoryId = target))
        val result = store.read()
        assertThat(result.categories.map { it.id }).doesNotContain(source)
        seed.cards.filter { it.categoryId == source }.forEach { old ->
            val moved = result.cards.single { it.id == old.id }
            assertThat(moved.categoryId).isEqualTo(target)
            assertThat(moved.code).isEqualTo(old.code)
        }
    }

    @Test fun saveNewCardClearsNewDraftAndSoftDeleteKeepsAlbum() = runTest {
        val seed = SeedLoader(context).load()
        store.replace(seed)
        val original = seed.cards.first()
        store.saveDraft("new", original)
        store.saveCard(original.copy(title = "修改"), "new")
        assertThat(store.draft("new")).isNull()
        store.setDeleted(setOf(original.id), true)
        assertThat(store.read().cards.first().deleted).isTrue()
        assertThat(store.read().albums).isEqualTo(seed.albums)
    }

    @Test fun invalidReplacementDoesNotChangeExistingData() = runTest {
        val seed = SeedLoader(context).load()
        store.replace(seed)
        try { store.replace(seed.copy(categories = emptyList())) } catch (_: IllegalArgumentException) { }
        assertThat(store.read()).isEqualTo(seed)
    }
    @Test fun mergingBackupRetainsUnrelatedDrafts() = runTest {
        val seed = SeedLoader(context).load()
        store.replace(seed)
        val draft = seed.cards.first().copy(title = "未保存修改")
        store.saveDraft(draft.id, draft)
        store.merge(seed.copy(cards = seed.cards.map { it.copy(title = "导入标题") }))
        assertThat(store.draft(draft.id)).isEqualTo(draft)
    }
    @Test fun largeLibraryRoundTripAndEditPreserveTwoThousandPhotos() = runTest {
        val seed = SeedLoader(context).load()
        val template = seed.cards.first()
        val cards = (0 until 500).map { i -> template.copy(id = "large-card-$i", code = "01-${i + 1}", images = (0 until 4).map { j -> template.images.first().copy(id = "large-photo-$i-$j") }) }
        val large = seed.copy(cards = cards, albums = emptyList())
        store.replace(large)
        store.saveCard(cards[250].copy(title = "大图库编辑"))
        val loaded = store.read()
        assertThat(loaded.cards).hasSize(500)
        assertThat(loaded.cards.sumOf { it.images.size }).isEqualTo(2000)
        assertThat(loaded.cards[250].title).isEqualTo("大图库编辑")
    }

    // —— C1 命名写入操作 ——
    private suspend fun albumFixture(): Triple<String, String, String> {
        val seed = SeedLoader(context).load()
        val ids = seed.cards.take(3).map { it.id }
        store.replace(seed.copy(albums = listOf(Album("album-a", "甲", cardIds = listOf(ids[0], ids[1])), Album("album-b", "乙", cardIds = listOf(ids[2])))))
        return Triple(ids[0], ids[1], ids[2])
    }

    @Test fun addToAlbumAppendsAndDeduplicates() = runTest {
        val (c0, c1, c2) = albumFixture()
        store.addCardsToAlbum("album-a", listOf(c1, c2))
        assertThat(store.read().albums.single { it.id == "album-a" }.cardIds).isEqualTo(listOf(c0, c1, c2))
    }
    @Test fun setAlbumCardsReplacesOrder() = runTest {
        val (c0, c1, _) = albumFixture()
        store.setAlbumCards("album-a", listOf(c1, c0))
        assertThat(store.read().albums.single { it.id == "album-a" }.cardIds).isEqualTo(listOf(c1, c0))
    }
    @Test fun moveCardInAlbumSwapsAndClamps() = runTest {
        val (c0, c1, _) = albumFixture()
        store.moveCardInAlbum("album-a", c1, true)   // 上移
        assertThat(store.read().albums.single { it.id == "album-a" }.cardIds).isEqualTo(listOf(c1, c0))
        store.moveCardInAlbum("album-a", c1, true)   // 已在顶部，钳制
        assertThat(store.read().albums.single { it.id == "album-a" }.cardIds).isEqualTo(listOf(c1, c0))
    }
    @Test fun removeCardsFromAlbum() = runTest {
        val (c0, _, _) = albumFixture()
        store.removeCardsFromAlbum("album-a", listOf(c0))
        assertThat(store.read().albums.single { it.id == "album-a" }.cardIds).doesNotContain(c0)
    }
    @Test fun reorderAlbumsClampsAtEdge() = runTest {
        albumFixture()
        store.reorderAlbums("album-a", true)   // 已在顶部，钳制
        assertThat(store.read().albums.map { it.id }).isEqualTo(listOf("album-a", "album-b"))
        store.reorderAlbums("album-a", false)  // 下移一格
        assertThat(store.read().albums.map { it.id }).isEqualTo(listOf("album-b", "album-a"))
    }
    @Test fun deleteAlbumKeepsCardsAndOtherAlbums() = runTest {
        val (c0, _, _) = albumFixture()
        store.deleteAlbum("album-a")
        val result = store.read()
        assertThat(result.albums.map { it.id }).isEqualTo(listOf("album-b"))
        assertThat(result.cards.map { it.id }).contains(c0)
    }
    @Test fun saveAlbumCreatesAndRenames() = runTest {
        albumFixture()
        store.saveAlbum("album-new", "新建册", "")
        assertThat(store.read().albums.last().name).isEqualTo("新建册")
        store.saveAlbum("album-new", "改名册", "描述")
        assertThat(store.read().albums.last().name).isEqualTo("改名册")
    }
    @Test fun reorderCategoriesClamps() = runTest {
        val seed = SeedLoader(context).load()
        store.replace(seed)
        val id = seed.categories[0].id
        store.reorderCategories(id, true)   // 已在顶部，钳制
        assertThat(store.read().categories.first().id).isEqualTo(id)
        store.reorderCategories(id, false)
        assertThat(store.read().categories.first().id).isEqualTo(seed.categories[1].id)
    }
    @Test fun saveCategoryRejectsDuplicateName() = runTest {
        val seed = SeedLoader(context).load()
        store.replace(seed)
        val dupeName = seed.categories[0].name
        try { store.saveCategory(seed.categories[0].copy(id = "cat-new", code = "99")) } catch (_: IllegalArgumentException) { }
        // 重名应被拒绝：库里仍只有一个同名分类，而不是新增第二个。
        assertThat(store.read().categories.count { it.name == dupeName }).isEqualTo(1)
    }
}
