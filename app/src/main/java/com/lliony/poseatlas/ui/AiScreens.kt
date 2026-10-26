package com.lliony.poseatlas.ui

import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lliony.poseatlas.data.ai.*
import com.lliony.poseatlas.domain.logic.*
import com.lliony.poseatlas.domain.model.Photo
import com.lliony.poseatlas.ui.components.PhotoView
import com.lliony.poseatlas.ui.theme.AtlasAccentBrush
import java.net.URI
import java.util.UUID

@Composable fun AiSettingsScreen(vm: AtlasViewModel, back: () -> Unit, recommend: () -> Unit) {
    val config by vm.aiConfig.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var secure by remember { mutableStateOf(true) }
    val activity = LocalActivity.current
    DisposableEffect(secure) {
        if (secure) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var editing by remember { mutableStateOf<AiProfile?>(null) }
    Column {
        PageTitle("AI 模型档案", back) { TextButton(onClick = { editing = AiProfile(UUID.randomUUID().toString(), "", "https://", "", "") }) { Text("新增") } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleRow("防止此页截图", secure) { secure = it }
            Text("使用自己的兼容 Chat Completions 服务。密钥仅存本机，记住时使用 Android Keystore 加密。", style = MaterialTheme.typography.bodySmall)
            config.profiles.forEach { profile -> OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                Text("${URI(profile.baseUrl).host} · ${profile.model}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { editing = profile }) { Text("编辑") }
                    TextButton(onClick = { vm.action("连接测试成功") { vm.testAi(profile) } }, enabled = !busy) { Text("测试连接") }
                    TextButton(onClick = { vm.useForRecommend(profile.id) }) { Text(if (config.recommendId == profile.id) "✓ 推荐模型" else "用于推荐") }
                    TextButton(onClick = { vm.useForDraft(profile.id) }) { Text(if (config.draftId == profile.id) "✓ 填卡模型" else "用于填卡") }
                    TextButton(onClick = { vm.deleteAiProfile(profile.id) }) { Text("删除") }
                }
            } } }
            Button(onClick = recommend, enabled = !busy) { Text("开始 AI 推荐") }
            TextButton(onClick = { vm.clearAiConfig() }) { Text("清除全部 AI 配置") }
            if (busy) TextButton(onClick = vm::cancelOperation) { Text("取消请求") }
        }
    }
    editing?.let { profile -> AlertDialog(onDismissRequest = { editing = null }, title = { Text("模型档案") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(profile.name, { editing = profile.copy(name = it) }, label = { Text("名称") })
        OutlinedTextField(profile.baseUrl, { editing = profile.copy(baseUrl = it) }, label = { Text("HTTPS base URL") })
        OutlinedTextField(profile.model, { editing = profile.copy(model = it) }, label = { Text("模型 ID") })
        OutlinedTextField(profile.apiKey, { editing = profile.copy(apiKey = it) }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation())
        ToggleRow("记住本机", profile.remember) { editing = profile.copy(remember = it) }
    } }, confirmButton = { TextButton(onClick = { vm.upsertAiProfile(profile); editing = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }) }
}

class RecommendationSession {
    val description = mutableStateOf("")
    val mode = mutableStateOf("scene")
    val scope = mutableStateOf("全部图鉴")
    val scopeId = mutableStateOf("")
    val photo = mutableStateOf<Photo?>(null)
    val result = mutableStateOf<Recommendations?>(null)
    val resultScope = mutableStateOf(setOf<String>())
}

@Composable fun AiRecommendScreen(vm: AtlasViewModel, back: () -> Unit, settings: () -> Unit, detail: (String) -> Unit, session: RecommendationSession = remember { RecommendationSession() }) {
    val state by vm.state.collectAsStateWithLifecycle()
    val config by vm.aiConfig.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var description by session.description
    var mode by session.mode
    var scope by session.scope
    var scopeId by session.scopeId
    var photo by session.photo
    var result by session.result
    var resultScope by session.resultScope
    var addToAlbum by remember { mutableStateOf(false) }
    val profile = config.profiles.find { it.id == config.recommendId }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.action { photo = vm.importPhoto(uri) } }
    val candidates = state.cards.filter { !it.deleted && when (scope) { "指定分类" -> it.categoryId == scopeId; "指定卡片册" -> it.id in state.albums.find { a -> a.id == scopeId }?.cardIds.orEmpty(); else -> true } }
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("AI 姿势推荐", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = settings, enabled = !busy) { Text("模型设置") }
            IconButton(onClick = back) { Icon(Icons.Default.Close, "收起模型推荐") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("描述这次拍摄，让模型帮你从图鉴中挑选姿势。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("scene" to "场景适配", "reference" to "参考图匹配").forEach { (value, label) ->
                    Surface(
                        modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                            .selectable(selected = mode == value, role = Role.Tab, onClick = { mode = value }),
                        shape = MaterialTheme.shapes.small,
                        color = if (mode == value) MaterialTheme.colorScheme.surface else Color.Transparent,
                        contentColor = if (mode == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        shadowElevation = if (mode == value) 2.dp else 0.dp,
                    ) {
                        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            OutlinedTextField(
                description, { description = it }, label = { Text("描述场景、光线或想要的姿势") },
                placeholder = { Text("比如：逆光的窗边，手捧热咖啡，安静温柔") },
                minLines = 3, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
            photo?.let {
                PhotoView(it, vm.images, "AI 参考图片", Modifier.height(180.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium))
                TextButton(onClick = { photo = null }) { Text("移除参考图") }
            }
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) { Text("＋ 选择一张参考图") }
            ChoiceRow("推荐范围", listOf("全部图鉴", "指定分类", "指定卡片册"), scope) { scope = it; scopeId = if (it == "指定分类") state.categories.firstOrNull()?.id.orEmpty() else state.albums.firstOrNull()?.id.orEmpty() }
            if (scope != "全部图鉴") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val choices = if (scope == "指定分类") state.categories.map { it.id to it.name } else state.albums.map { it.id to it.name }
                choices.forEach { (id, name) -> FilterChip(scopeId == id, { scopeId = id }, { Text(name) }) }
            }
            Text("候选 ${candidates.size} 张卡片。${if (profile == null) "请先配置推荐模型。" else "图片和描述会发送给 ${URI(profile.baseUrl).host}"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { if (profile == null) settings() else vm.action {
                result = null; resultScope = candidates.map { it.id }.toSet()
                result = vm.requestRecommendation(profile, candidates, description, mode, photo)
            } }, enabled = !busy && candidates.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).background(AtlasAccentBrush, MaterialTheme.shapes.medium),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White, disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (profile == null) "配置模型" else "推荐姿势", style = MaterialTheme.typography.titleSmall)
            }
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = vm::cancelOperation) { Text("取消请求") } }
            result?.let { response ->
                val valid = response.recommendations.filter { recommendation -> state.cards.any { it.id == recommendation.cardId && !it.deleted && it.id in resultScope && it.id in candidates.map { c -> c.id } } }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("推荐结果", style = MaterialTheme.typography.titleMedium)
                    Text("${valid.size} 张姿势卡", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(response.sceneSummary, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (valid.isEmpty()) Text("没有匹配的卡片")
                valid.forEachIndexed { index, recommendation ->
                    OutlinedCard(
                        onClick = { detail(recommendation.cardId) }, modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("%02d".format(index + 1), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(state.cards.first { it.id == recommendation.cardId }.title, style = MaterialTheme.typography.titleMedium)
                                Text(recommendation.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(recommendation.adjustment, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (valid.isNotEmpty()) Button(
                    onClick = { addToAlbum = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                ) { Text("将推荐结果加入卡片册") }
            }
        }
    }
    if (addToAlbum) AlertDialog(onDismissRequest = { addToAlbum = false }, title = { Text("选择卡片册") }, text = { Column { if (state.albums.isEmpty()) Text("请先在卡片册页面新建。") else state.albums.forEach { album -> TextButton(onClick = {
        val ids = result?.recommendations.orEmpty().map { it.cardId }.filter { id -> state.cards.any { it.id == id && !it.deleted } }
        vm.addCardsToAlbum(album.id, ids); addToAlbum = false
    }) { Text(album.name) } } } }, confirmButton = { TextButton(onClick = { addToAlbum = false }) { Text("关闭") } })
}
