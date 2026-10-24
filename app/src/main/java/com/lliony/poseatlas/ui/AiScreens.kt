package com.lliony.poseatlas.ui

import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lliony.poseatlas.data.ai.*
import com.lliony.poseatlas.domain.logic.*
import com.lliony.poseatlas.domain.model.Photo
import com.lliony.poseatlas.ui.components.PhotoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                    TextButton(onClick = { vm.action("连接测试成功") { vm.ai.request(profile, "请只回复 OK。", "连接测试") } }, enabled = !busy) { Text("测试连接") }
                    TextButton(onClick = { vm.action { vm.aiConfig.set(config.copy(recommendId = profile.id)) } }) { Text(if (config.recommendId == profile.id) "✓ 推荐模型" else "用于推荐") }
                    TextButton(onClick = { vm.action { vm.aiConfig.set(config.copy(draftId = profile.id)) } }) { Text(if (config.draftId == profile.id) "✓ 填卡模型" else "用于填卡") }
                    TextButton(onClick = { vm.action { vm.aiConfig.set(config.copy(profiles = config.profiles.filterNot { it.id == profile.id }, recommendId = config.recommendId.takeUnless { it == profile.id }, draftId = config.draftId.takeUnless { it == profile.id })) } }) { Text("删除") }
                }
            } } }
            Button(onClick = recommend, enabled = !busy) { Text("开始 AI 推荐") }
            TextButton(onClick = { vm.action("AI 配置已清除") { vm.aiConfig.set(AiConfiguration()) } }) { Text("清除全部 AI 配置") }
            if (busy) TextButton(onClick = vm::cancelOperation) { Text("取消请求") }
        }
    }
    editing?.let { profile -> AlertDialog(onDismissRequest = { editing = null }, title = { Text("模型档案") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(profile.name, { editing = profile.copy(name = it) }, label = { Text("名称") })
        OutlinedTextField(profile.baseUrl, { editing = profile.copy(baseUrl = it) }, label = { Text("HTTPS base URL") })
        OutlinedTextField(profile.model, { editing = profile.copy(model = it) }, label = { Text("模型 ID") })
        OutlinedTextField(profile.apiKey, { editing = profile.copy(apiKey = it) }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation())
        ToggleRow("记住本机", profile.remember) { editing = profile.copy(remember = it) }
    } }, confirmButton = { TextButton(onClick = { vm.action {
        require(profile.name.isNotBlank() && profile.model.isNotBlank() && profile.apiKey.isNotBlank()) { "请填写名称、模型和 API key" }
        val normalized = profile.copy(baseUrl = normalizeAiUrl(profile.baseUrl))
        vm.aiConfig.set(config.copy(profiles = config.profiles.filterNot { it.id == profile.id } + normalized, recommendId = config.recommendId ?: profile.id, draftId = config.draftId ?: profile.id)); editing = null
    } }) { Text("保存") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }) }
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.action { photo = withContext(Dispatchers.IO) { vm.repository.images.import(uri) } } }
    val candidates = state.cards.filter { !it.deleted && when (scope) { "指定分类" -> it.categoryId == scopeId; "指定卡片册" -> it.id in state.albums.find { a -> a.id == scopeId }?.cardIds.orEmpty(); else -> true } }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("AI 姿势推荐", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = settings, enabled = !busy) { Text("模型设置") }
            IconButton(onClick = back) { Icon(Icons.Default.Close, "收起模型推荐") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("描述这次拍摄，让模型帮你从图鉴中挑选姿势。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceRow("匹配方式", listOf("场景适配", "参考图匹配"), if (mode == "scene") "场景适配" else "参考图匹配") { mode = if (it == "场景适配") "scene" else "reference" }
            OutlinedTextField(description, { description = it }, label = { Text("描述场景、光线或想要的姿势") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            photo?.let { PhotoView(it, vm.repository.images, "AI 参考图片", Modifier.height(180.dp).fillMaxWidth()); TextButton(onClick = { photo = null }) { Text("移除参考图") } }
            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Text("选择一张参考图") }
            ChoiceRow("推荐范围", listOf("全部图鉴", "指定分类", "指定卡片册"), scope) { scope = it; scopeId = if (it == "指定分类") state.categories.firstOrNull()?.id.orEmpty() else state.albums.firstOrNull()?.id.orEmpty() }
            if (scope != "全部图鉴") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val choices = if (scope == "指定分类") state.categories.map { it.id to it.name } else state.albums.map { it.id to it.name }
                choices.forEach { (id, name) -> FilterChip(scopeId == id, { scopeId = id }, { Text(name) }) }
            }
            Text("候选 ${candidates.size} 张卡片。${if (profile == null) "请先配置推荐模型。" else "图片和描述会发送给 ${URI(profile.baseUrl).host}"}", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { if (profile == null) settings() else vm.action {
                result = null; resultScope = candidates.map { it.id }.toSet()
                result = vm.ai.recommend(profile, candidates, state.categories, description, mode, photo)
            } }, enabled = !busy && candidates.isNotEmpty()) { Text(if (profile == null) "配置模型" else "推荐姿势") }
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onClick = vm::cancelOperation) { Text("取消请求") } }
            result?.let { response ->
                Text(response.sceneSummary, style = MaterialTheme.typography.titleMedium)
                val valid = response.recommendations.filter { recommendation -> state.cards.any { it.id == recommendation.cardId && !it.deleted && it.id in resultScope && it.id in candidates.map { c -> c.id } } }
                if (valid.isEmpty()) Text("没有匹配的卡片")
                valid.forEach { recommendation -> OutlinedCard(onClick = { detail(recommendation.cardId) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(state.cards.first { it.id == recommendation.cardId }.title, style = MaterialTheme.typography.titleMedium)
                    Text(recommendation.reason); Text(recommendation.adjustment)
                } } }
                if (valid.isNotEmpty()) OutlinedButton(onClick = { addToAlbum = true }) { Text("将推荐结果加入卡片册") }
            }
        }
    }
    if (addToAlbum) AlertDialog(onDismissRequest = { addToAlbum = false }, title = { Text("选择卡片册") }, text = { Column { if (state.albums.isEmpty()) Text("请先在卡片册页面新建。") else state.albums.forEach { album -> TextButton(onClick = { vm.action("已加入卡片册") {
        val ids = result?.recommendations.orEmpty().map { it.cardId }.filter { id -> vm.state.value.cards.any { it.id == id && !it.deleted } }
        vm.repository.mutate { saveAlbums(vm.state.value.albums.map { if (it.id == album.id) it.copy(cardIds = (it.cardIds + ids).distinct()) else it }) }; addToAlbum = false
    } }) { Text(album.name) } } } }, confirmButton = { TextButton(onClick = { addToAlbum = false }) { Text("关闭") } })
}
