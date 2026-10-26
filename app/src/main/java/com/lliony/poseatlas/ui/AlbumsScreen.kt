package com.lliony.poseatlas.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.lliony.poseatlas.ui.components.PhotoView
import androidx.compose.ui.text.style.TextOverflow
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
    val activeCards = remember(state.cards) { state.cards.filterNot { it.deleted }.associateBy { it.id } }
    Column {
        PageTitle("卡片册") { FilledTonalButton(onClick = { edit = Album(UUID.randomUUID().toString(), ""); name = ""; description = "" }) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("新建") } }
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.albums, key = { it.id }) { album -> Card(onClick = { open(album.id) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val cover = album.cardIds.firstNotNullOfOrNull { activeCards[it]?.images?.firstOrNull() }
                    val coverModifier = Modifier.size(width = 88.dp, height = 112.dp)
                        .clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant)
                    if (cover != null) PhotoView(cover, vm.images, "${album.name}封面", coverModifier, contentScale = ContentScale.Crop)
                    else Box(coverModifier, contentAlignment = Alignment.Center) {
                        Text("暂无封面", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${album.cardIds.count { it in activeCards }} 张卡片", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (album.description.isNotBlank()) Text(album.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { edit = album; name = album.name; description = album.description }) { Text("编辑") }
                    val index = state.albums.indexOf(album)
                    TextButton(onClick = { vm.reorderAlbums(album.id, true) }, enabled = index > 0) { Text("上移") }
                    TextButton(onClick = { vm.reorderAlbums(album.id, false) }, enabled = index < state.albums.lastIndex) { Text("下移") }
                    TextButton(onClick = { deleting = album }) { Text("删除") }
                }
            } } }
            if (state.albums.isEmpty()) item {
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.CollectionsBookmark, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("创建一本卡片册，整理下一次拍摄的姿势。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (edit != null) AlertDialog(onDismissRequest = { edit = null }, title = { Text("卡片册") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("名称") }); OutlinedTextField(description, { description = it }, label = { Text("描述") }) } }, confirmButton = { TextButton(onClick = { vm.saveAlbum(edit!!.id, name, description); edit = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { edit = null }) { Text("取消") } })
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除卡片册？") }, text = { Text("册内卡片仍保留在图鉴中。") }, confirmButton = { TextButton(onClick = { val id = deleting!!.id; vm.deleteAlbum(id); deleting = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
}

@Composable fun AlbumDetailScreen(vm: AtlasViewModel, id: String, back: () -> Unit, detail: (String) -> Unit, print: (List<String>) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val album = state.albums.find { it.id == id } ?: return
    var adding by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    Column {
        PageTitle(album.name, back) { TextButton(onClick = { chosen = album.cardIds.toSet(); adding = true }) { Text("加卡") }; TextButton(onClick = { print(album.cardIds) }, enabled = album.cardIds.isNotEmpty()) { Text("打印 / PDF") } }
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(album.cardIds, key = { it }) { cardId ->
                val card = state.cards.find { it.id == cardId && !it.deleted }
                if (card != null) Card(onClick = { detail(cardId) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        card.images.firstOrNull()?.let { photo ->
                            PhotoView(photo, vm.images, "${card.title}预览",
                                Modifier.size(width = 80.dp, height = 104.dp).clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(card.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(card.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row {
                        val index = album.cardIds.indexOf(cardId)
                        TextButton(onClick = { vm.moveCardInAlbum(id, cardId, true) }, enabled = index > 0) { Text("上移") }
                        TextButton(onClick = { vm.moveCardInAlbum(id, cardId, false) }, enabled = index < album.cardIds.lastIndex) { Text("下移") }
                        TextButton(onClick = { vm.removeCardsFromAlbum(id, listOf(cardId)) }) { Text("移出") }
                    }
                } }
            }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("加入卡片册") }, text = { LazyColumn { items(state.cards.filterNot { it.deleted }, key = { it.id }) { card -> Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(card.id in chosen, { chosen = if (it) chosen + card.id else chosen - card.id }); Text(card.title) } } } }, confirmButton = { TextButton(onClick = { vm.setAlbumCards(id, album.cardIds.filter { it in chosen } + chosen.filter { it !in album.cardIds }); adding = false }) { Text("保存") } }, dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } })
}
