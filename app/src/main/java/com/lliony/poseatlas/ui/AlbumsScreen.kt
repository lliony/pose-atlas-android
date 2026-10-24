package com.lliony.poseatlas.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lliony.poseatlas.domain.model.Album
import java.util.UUID

@Composable fun AlbumsScreen(vm: AtlasViewModel, open: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<Album?>(null) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Album?>(null) }
    Column {
        PageTitle("卡片册") { TextButton(onClick = { edit = Album(UUID.randomUUID().toString(), ""); name = ""; description = "" }) { Text("新建") } }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.albums, key = { it.id }) { album -> OutlinedCard(onClick = { open(album.id) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(album.name, style = MaterialTheme.typography.titleLarge)
                Text("${album.cardIds.count { id -> state.cards.any { it.id == id && !it.deleted } }} 张卡片 · ${album.description}")
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { edit = album; name = album.name; description = album.description }) { Text("编辑") }
                    val index = state.albums.indexOf(album)
                    TextButton(onClick = { vm.action { val albums = state.albums.toMutableList(); java.util.Collections.swap(albums, index, index - 1); vm.repository.mutate { saveAlbums(albums) } } }, enabled = index > 0) { Text("上移") }
                    TextButton(onClick = { vm.action { val albums = state.albums.toMutableList(); java.util.Collections.swap(albums, index, index + 1); vm.repository.mutate { saveAlbums(albums) } } }, enabled = index < state.albums.lastIndex) { Text("下移") }
                    TextButton(onClick = { deleting = album }) { Text("删除") }
                }
            } } }
            if (state.albums.isEmpty()) item { Text("创建一本卡片册，整理下一次拍摄的姿势。") }
        }
    }
    if (edit != null) AlertDialog(onDismissRequest = { edit = null }, title = { Text("卡片册") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("名称") }); OutlinedTextField(description, { description = it }, label = { Text("描述") }) } }, confirmButton = { TextButton(onClick = { vm.action {
        require(name.isNotBlank()) { "请填写名称" }; val album = edit!!.copy(name = name.trim(), description = description)
        val albums = state.albums.toMutableList(); val index = albums.indexOfFirst { it.id == album.id }; if (index < 0) albums.add(album) else albums[index] = album
        vm.repository.mutate { saveAlbums(albums) }; edit = null
    } }) { Text("保存") } }, dismissButton = { TextButton(onClick = { edit = null }) { Text("取消") } })
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除卡片册？") }, text = { Text("册内卡片仍保留在图鉴中。") }, confirmButton = { TextButton(onClick = { val id = deleting!!.id; vm.action { vm.repository.mutate { saveAlbums(vm.state.value.albums.filterNot { it.id == id }) } }; deleting = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
}

@Composable fun AlbumDetailScreen(vm: AtlasViewModel, id: String, back: () -> Unit, detail: (String) -> Unit, print: (List<String>) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val album = state.albums.find { it.id == id } ?: return
    var adding by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    fun save(ids: List<String>) { vm.action { vm.repository.mutate { saveAlbums(vm.state.value.albums.map { if (it.id == id) it.copy(cardIds = ids.distinct()) else it }) } } }
    Column {
        PageTitle(album.name, back) { TextButton(onClick = { chosen = album.cardIds.toSet(); adding = true }) { Text("加卡") }; TextButton(onClick = { print(album.cardIds) }, enabled = album.cardIds.isNotEmpty()) { Text("打印 / PDF") } }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(album.cardIds, key = { it }) { cardId ->
                val card = state.cards.find { it.id == cardId && !it.deleted }
                if (card != null) OutlinedCard(onClick = { detail(cardId) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("${card.code} · ${card.title}", style = MaterialTheme.typography.titleMedium)
                    Row {
                        val index = album.cardIds.indexOf(cardId)
                        TextButton(onClick = { val ids = album.cardIds.toMutableList(); java.util.Collections.swap(ids, index, index - 1); save(ids) }, enabled = index > 0) { Text("上移") }
                        TextButton(onClick = { val ids = album.cardIds.toMutableList(); java.util.Collections.swap(ids, index, index + 1); save(ids) }, enabled = index < album.cardIds.lastIndex) { Text("下移") }
                        TextButton(onClick = { save(album.cardIds - cardId) }) { Text("移出") }
                    }
                } }
            }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("加入卡片册") }, text = { LazyColumn { items(state.cards.filterNot { it.deleted }, key = { it.id }) { card -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(card.id in chosen, { chosen = if (it) chosen + card.id else chosen - card.id }); Text(card.title) } } } }, confirmButton = { TextButton(onClick = { save(album.cardIds.filter { it in chosen } + chosen.filter { it !in album.cardIds }); adding = false }) { Text("保存") } }, dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } })
}
