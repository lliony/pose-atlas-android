@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lliony.poseatlas.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lliony.poseatlas.domain.logic.nextCardCode
import com.lliony.poseatlas.domain.logic.MAX_IMAGES_PER_CARD
import com.lliony.poseatlas.domain.model.*
import com.lliony.poseatlas.ui.components.PhotoView
import com.lliony.poseatlas.ui.theme.AtlasAccentBrush
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable private fun AiFillBar(vm: AtlasViewModel, modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    val config by vm.aiConfig.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val undo by vm.editorUndo.collectAsStateWithLifecycle()
    val profile = config.profiles.find { it.id == config.draftId }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp).testTag("ai-fill-bar"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = vm::fillEditor, enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).background(AtlasAccentBrush, MaterialTheme.shapes.medium),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White, disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "处理中…" else "AI 填充")
                }
                if (busy) TextButton(onClick = vm::cancelOperation) { Text("取消") }
                else if (undo != null) TextButton(onClick = vm::undoFill) { Text("撤销填充") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (profile == null) "仅填空字段 · 请先在设置中配置填卡模型" else "仅填空字段 · 图片和标题会发送给 ${java.net.URI(profile.baseUrl).host}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun EditorScreen(vm: AtlasViewModel, back: () -> Unit) {
    val session by vm.editor.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val current = session
    val focus = LocalFocusManager.current
    var selected by remember { mutableIntStateOf(0) }
    var perPhoto by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val cropTarget by vm.cropSession.collectAsStateWithLifecycle()
    val editorScroll = rememberScrollState()
    val addPhoto = rememberPhotoInput(vm, multiple = true) { uris ->
        val card = vm.editor.value?.card ?: return@rememberPhotoInput
        require(card.images.size + uris.size <= MAX_IMAGES_PER_CARD) { "每张卡片最多 $MAX_IMAGES_PER_CARD 张图片" }
        val photos = vm.importPhotos(uris)
        val currentCard = vm.editor.value?.card?.takeIf { it.id == card.id } ?: return@rememberPhotoInput
        require(currentCard.images.size + photos.size <= MAX_IMAGES_PER_CARD) { "每张卡片最多 $MAX_IMAGES_PER_CARD 张图片" }
        vm.edit(currentCard.copy(images = currentCard.images + photos))
    }
    fun close() { if (current?.dirty == true) leaving = true else vm.leaveEditor(false, back) }
    if (cropTarget != null) { CropScreen(vm, cropTarget!!); return }
    BackHandler { close() }
    Column {
        PageTitle(if (current?.key == "new") "新建卡片" else "编辑卡片", ::close) { TextButton(onClick = { vm.saveEditor(back) }, enabled = !busy && current != null) { Text("保存") } }
        if (current == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return@Column }
        val card = current.card
        if (current.key != "new") AiFillBar(vm)
        val photoListState = rememberLazyListState()
        val reorder = rememberReorderableLazyListState(photoListState) { from, to ->
            val latest = vm.editor.value?.card
            if (latest != null && from.index in latest.images.indices && to.index in latest.images.indices) {
                val selectedId = latest.images.getOrNull(selected)?.id
                val photos = latest.images.toMutableList().apply { add(to.index, removeAt(from.index)) }
                vm.edit(latest.copy(images = photos)); selected = photos.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(editorScroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (current.restored) Text("已恢复上次未保存的修改", color = MaterialTheme.colorScheme.primary)
            LazyRow(state = photoListState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(card.images, key = { _, photo -> photo.id }) { index, photo ->
                    ReorderableItem(reorder, key = photo.id) {
                    OutlinedCard(onClick = { selected = index }) { Column(Modifier.padding(8.dp)) {
                        PhotoView(photo, vm.images, "第 ${index + 1} 张图片", Modifier.size(96.dp))
                        Text(if (index == 0) "封面" else "第 ${index + 1} 张", style = MaterialTheme.typography.labelMedium)
                        if (index == selected) Text("已选中", color = MaterialTheme.colorScheme.primary)
                        Text("按住拖动排序", Modifier.longPressDraggableHandle().padding(vertical = 12.dp), style = MaterialTheme.typography.labelSmall)
                    } }
                    }
                }
            }
            Button(onClick = addPhoto, enabled = !busy && card.images.size < MAX_IMAGES_PER_CARD) { Text("＋ 添加照片 (${card.images.size}/$MAX_IMAGES_PER_CARD)") }
            if (current.key == "new") AiFillBar(vm, Modifier)
            val photo = card.images.getOrNull(selected)
            if (photo != null) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { vm.edit(card.copy(images = listOf(photo) + card.images.filterNot { it.id == photo.id })); selected = 0 }) { Text("设为封面") }
                    TextButton(onClick = { if (selected > 0) { val photos = card.images.toMutableList(); java.util.Collections.swap(photos, selected, selected - 1); selected--; vm.edit(card.copy(images = photos)) } }, enabled = selected > 0) { Text("前移") }
                    TextButton(onClick = { if (selected < card.images.lastIndex) { val photos = card.images.toMutableList(); java.util.Collections.swap(photos, selected, selected + 1); selected++; vm.edit(card.copy(images = photos)) } }, enabled = selected < card.images.lastIndex) { Text("后移") }
                    TextButton(onClick = { vm.edit(card.copy(images = card.images.filterNot { it.id == photo.id })); selected = 0 }) { Text("移除") }
                }
                OutlinedButton(onClick = { focus.clearFocus(); vm.beginCrop(photo) }, enabled = !busy) { Text("裁切当前图片") }
            }
            OutlinedTextField(card.title, { vm.edit(card.copy(title = it)) }, label = { Text("标题 *") }, modifier = Modifier.fillMaxWidth(), isError = card.title.length > 100, supportingText = { Text("${card.title.length}/100") })
            Text("分类", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.categories.forEach { category -> FilterChip(card.categoryId == category.id, {
                vm.edit(card.copy(categoryId = category.id, code = if (current.key == "new") nextCardCode(category, state.cards) else card.code))
            }, { Text(category.name) }) } }
            OutlinedTextField(card.code, { vm.edit(card.copy(code = it)) }, label = { Text("编号") }, modifier = Modifier.fillMaxWidth())
            var tagsText by remember(card.id) { mutableStateOf(card.tags.joinToString("，")) }
            OutlinedTextField(tagsText, { tagsText = it; vm.edit(card.copy(tags = it.split(',', '，').map(String::trim).filter(String::isNotBlank))) }, label = { Text("标签（逗号分隔）") }, modifier = Modifier.fillMaxWidth())
            ChoiceRow("性别", listOf("未指定") + genders, card.gender ?: "未指定") { vm.edit(card.copy(gender = it.takeUnless { it == "未指定" })) }
            Text("气质风格（多选）", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { styles.forEach { style -> FilterChip(style in card.styles.orEmpty(), { vm.edit(card.copy(styles = if (style in card.styles.orEmpty()) card.styles.orEmpty() - style else card.styles.orEmpty() + style)) }, { Text(style) }) } }
            ChoiceRow("教学说明", listOf("卡片通用教学", "当前图片专属"), if (perPhoto) "当前图片专属" else "卡片通用教学") { perPhoto = it == "当前图片专属" }
            if (perPhoto) Text("留空继承卡片通用教学。", style = MaterialTheme.typography.bodySmall)
            val notes = if (perPhoto) photo?.notes ?: Notes() else card.notes
            noteLabels.values.forEachIndexed { index, label -> OutlinedTextField(notes.values()[index], { value ->
                val updated = notes.with(index, value)
                vm.edit(if (perPhoto && photo != null) card.copy(images = card.images.map { if (it.id == photo.id) it.copy(notes = updated) else it }) else card.copy(notes = updated))
            }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = 2, enabled = !perPhoto || photo != null) }
            OutlinedTextField(card.privateNote, { vm.edit(card.copy(privateNote = it)) }, label = { Text("私人备注（不打印、不分享）") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            TextButton(onClick = { vm.leaveEditor(false, back) }, enabled = !busy) { Text("放弃草稿") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("保留未保存的修改？") }, text = { Text("保留草稿后，下次编辑会自动恢复。") }, confirmButton = { TextButton(onClick = { leaving = false; vm.leaveEditor(true, back) }) { Text("保留草稿") } }, dismissButton = { TextButton(onClick = { leaving = false; vm.leaveEditor(false, back) }) { Text("放弃修改") } })
}
