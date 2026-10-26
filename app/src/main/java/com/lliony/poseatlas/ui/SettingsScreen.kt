@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lliony.poseatlas.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            val themes = linkedMapOf("跟随系统" to "system", "纸本" to "paper", "白墙" to "white", "暗房" to "dark")
            SettingsGroup("外观") {
                ChoiceRow("配色", themes.keys.toList(), themes.entries.firstOrNull { it.value == prefs.theme }?.key ?: "跟随系统") { vm.preferences(prefs.copy(theme = themes.getValue(it))) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow("细边框", prefs.frame) { vm.preferences(prefs.copy(frame = it)) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow("平板紧凑网格", prefs.dense) { vm.preferences(prefs.copy(dense = it)) }
            }
            SettingsGroup("备份与恢复") {
                ToggleRow("备份包含图片", includeImages) { includeImages = it }
                Button(onClick = { export.launch("pose-atlas-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.zip") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("导出 ZIP") }
                ChoiceRow("导入方式", listOf("合并", "替换"), if (merge) "合并" else "替换") { merge = it == "合并" }
                OutlinedButton(onClick = { import.launch(arrayOf("application/zip", "application/json", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("选择 ZIP / JSON 导入") }
                Text("与 Web 版互通。备份包含私人备注，不包含 AI 配置和密钥。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsGroup("资料") {
                SettingsLink("AI 模型配置", Icons.Default.AutoAwesome, aiSettings)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsLink("管理分类", Icons.Default.Category, categories)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsLink("回收站（${state.cards.count { it.deleted }}）", Icons.Default.DeleteOutline, trash)
                Text("${state.cards.count { !it.deleted }} 张卡片 · ${state.categories.size} 个分类 · ${state.albums.size} 个卡片册\n${state.cards.flatMap { it.images }.map { it.src }.distinct().size} 张独立图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsGroup("关于") {
                UpdateSection()
                Text("资料仅保存在本机。卸载或清除应用数据前请导出备份。\n开源组件：Jetpack Compose、Room、Hilt、Coil、Kotlinx Serialization。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pendingImport != null) AlertDialog(onDismissRequest = { pendingImport = null }, title = { Text(if (merge) "合并导入资料？" else "替换全部资料？") }, text = { Text(if (merge) "相同 ID 的条目将由备份覆盖，其余保留。" else "当前资料将被替换。仅文字备份会清空本地旧图，建议先导出当前备份。") }, confirmButton = { TextButton(onClick = {
        val uri = pendingImport!!; pendingImport = null; vm.action { vm.message(vm.backup.import(uri, merge)) }
    }) { Text("导入") } }, dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("取消") } })
}

@Composable private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable private fun SettingsLink(label: String, icon: ImageVector, click: () -> Unit) {
    Surface(onClick = click, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.categories.forEachIndexed { index, category -> Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) { Column(Modifier.padding(16.dp)) {
                Text("${category.code} · ${category.name}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
