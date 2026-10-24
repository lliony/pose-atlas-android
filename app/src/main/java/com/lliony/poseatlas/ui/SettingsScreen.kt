@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lliony.poseatlas.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lliony.poseatlas.domain.model.Category
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable fun SettingsScreen(vm: AtlasViewModel, trash: () -> Unit, categories: () -> Unit, aiSettings: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var includeImages by remember { mutableStateOf(true) }
    var merge by remember { mutableStateOf(true) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) vm.action {
        val missing = vm.backup.export(uri, includeImages); vm.message("备份已保存${if (missing > 0) "，缺失 $missing 张图片" else ""}")
    } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingImport = it }
    Column {
        PageTitle("设置")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("外观", style = MaterialTheme.typography.titleLarge)
            val themes = linkedMapOf("跟随系统" to "system", "纸本" to "paper", "白墙" to "white", "暗房" to "dark")
            ChoiceRow("配色", themes.keys.toList(), themes.entries.firstOrNull { it.value == prefs.theme }?.key ?: "跟随系统") { vm.preferences(prefs.copy(theme = themes.getValue(it))) }
            ToggleRow("细边框", prefs.frame) { vm.preferences(prefs.copy(frame = it)) }
            ToggleRow("平板紧凑网格", prefs.dense) { vm.preferences(prefs.copy(dense = it)) }
            HorizontalDivider()
            Text("备份与恢复", style = MaterialTheme.typography.titleLarge)
            ToggleRow("备份包含图片", includeImages) { includeImages = it }
            Button(onClick = { export.launch("pose-atlas-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.zip") }, enabled = !busy) { Text("导出 ZIP") }
            ChoiceRow("导入方式", listOf("合并", "替换"), if (merge) "合并" else "替换") { merge = it == "合并" }
            OutlinedButton(onClick = { import.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) }, enabled = !busy) { Text("选择 ZIP / JSON 导入") }
            Text("与 Web 版互通。备份包含私人备注，不包含 AI 配置和密钥。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("资料", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = aiSettings) { Text("AI 模型配置") }
            Text("${state.cards.count { !it.deleted }} 张卡片 · ${state.categories.size} 个分类 · ${state.albums.size} 个卡片册\n${state.cards.flatMap { it.images }.map { it.src }.distinct().size} 张独立图片")
            OutlinedButton(onClick = categories) { Text("管理分类") }
            OutlinedButton(onClick = trash) { Text("回收站（${state.cards.count { it.deleted }}）") }
            HorizontalDivider()
            Text("姿势图鉴 1.0.0", style = MaterialTheme.typography.titleMedium)
            Text("资料仅保存在本机。卸载或清除应用数据前请导出备份。\n开源组件：Jetpack Compose、Room、Hilt、Coil、Kotlinx Serialization。", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pendingImport != null) AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text(if (merge) "合并导入资料？" else "替换全部资料？") }, text = { Text(if (merge) "相同 ID 的条目将由备份覆盖，其余保留。" else "当前资料将被替换。仅文字备份会清空本地旧图，建议先导出当前备份。") }, confirmButton = { TextButton(onClick = {
        val uri = pendingImport!!; pendingImport = null; vm.action { vm.message(vm.backup.import(uri, merge)) }
    }) { Text("导入") } }, dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } })
}

@Composable fun ToggleRow(label: String, value: Boolean, enabled: Boolean = true, change: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(value, change, enabled = enabled) } }

@Composable fun CategoriesScreen(vm: AtlasViewModel, back: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Category?>(null) }
    var target by remember { mutableStateOf("") }
    Column {
        PageTitle("管理分类", back) { TextButton(onClick = {
            editing = Category(UUID.randomUUID().toString(), "", ((state.categories.mapNotNull { it.code.toIntOrNull() }.maxOrNull() ?: 0) + 1).toString().padStart(2, '0')); name = ""; code = editing!!.code
        }) { Text("新增") } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.categories.forEachIndexed { index, category -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("${category.code} · ${category.name}", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { editing = category; name = category.name; code = category.code }) { Text("编辑") }
                    TextButton(onClick = { vm.action { val items = state.categories.toMutableList(); java.util.Collections.swap(items, index, index - 1); vm.repository.mutate { saveCategories(items) } } }, enabled = index > 0) { Text("上移") }
                    TextButton(onClick = { vm.action { val items = state.categories.toMutableList(); java.util.Collections.swap(items, index, index + 1); vm.repository.mutate { saveCategories(items) } } }, enabled = index < state.categories.lastIndex) { Text("下移") }
                    TextButton(onClick = { deleting = category; target = state.categories.first { it.id != category.id }.id }, enabled = state.categories.size > 1) { Text("删除") }
                }
            } } }
        }
    }
    if (editing != null) AlertDialog(onDismissRequest = { editing = null }, title = { Text("分类") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("名称") }); OutlinedTextField(code, { code = it }, label = { Text("代码") }) } }, confirmButton = { TextButton(onClick = {
        val category = editing!!.copy(name = name.trim(), code = code.trim()); vm.action { val items = state.categories.toMutableList(); val index = items.indexOfFirst { it.id == category.id }; if (index < 0) items.add(category) else items[index] = category; vm.repository.mutate { saveCategories(items) }; editing = null }
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } })
    if (deleting != null) AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除分类并迁移卡片") }, text = { Column {
        Text("卡片不会删除，编号不变。请选择接收分类：")
        state.categories.filter { it.id != deleting!!.id }.forEach { category -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(target == category.id, { target = category.id }); Text(category.name) } }
    } }, confirmButton = { TextButton(onClick = { val source = deleting!!.id; vm.action { vm.repository.mutate { deleteCategory(source, target) }; deleting = null } }) { Text("迁移并删除") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
}
