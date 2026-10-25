@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.lliony.poseatlas.ui

import androidx.compose.foundation.*
import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.lliony.poseatlas.ui.theme.AtlasAccentBrush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import androidx.navigation.NavDestination.Companion.hasRoute
import com.lliony.poseatlas.domain.logic.*
import com.lliony.poseatlas.domain.model.*
import com.lliony.poseatlas.domain.model.Card as PoseCard
import com.lliony.poseatlas.ui.components.PhotoView
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable object LibraryRoute
@Serializable object SettingsRoute
@Serializable object CategoriesRoute
@Serializable object TrashRoute
@Serializable object AlbumsRoute
@Serializable data class AlbumRoute(val id: String)
@Serializable data class PrintRoute(val ids: String)
@Serializable object AiSettingsRoute

@Serializable data class DetailRoute(val id: String)
@Serializable data class EditorRoute(val id: String)

@Composable fun AtlasApp(vm: AtlasViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val editing = entry?.destination?.hasRoute<EditorRoute>() == true
    val snackbar = remember { SnackbarHostState() }
    var recommendationOpen by rememberSaveable { mutableStateOf(false) }
    val recommendationSession = vm.recommendationSession
    val loading by vm.loading.collectAsStateWithLifecycle()
    val loadFailed by vm.loadFailed.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.events.collect { event -> if (snackbar.showSnackbar(event.text, event.action) == SnackbarResult.ActionPerformed) event.onAction?.invoke() } }
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    Scaffold(
        modifier = Modifier.background(Brush.verticalGradient(listOf(lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primaryContainer, 0.45f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background))),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { if (!recommendationOpen) SnackbarHost(snackbar) }, floatingActionButton = {
        if (!editing && !loading && !loadFailed && (entry?.destination?.hasRoute<LibraryRoute>() == true || entry?.destination?.hasRoute<AlbumsRoute>() == true || entry?.destination?.hasRoute<AlbumRoute>() == true)) {
            FloatingActionButton(onClick = { recommendationOpen = true }, modifier = Modifier.size(58.dp), shape = CircleShape, containerColor = Color.Transparent, contentColor = Color.White) {
                Box(Modifier.fillMaxSize().background(AtlasAccentBrush, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.AutoAwesome, "打开模型推荐") }
            }
        }
    }, bottomBar = {
        if (!wide && !editing) Surface(
            modifier = Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 10.dp,
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                AtlasNavigationItem("图鉴", Icons.Default.PhotoLibrary, entry?.destination?.hasRoute<LibraryRoute>() == true) { nav.navigate(LibraryRoute) { popUpTo<LibraryRoute>(); launchSingleTop = true } }
                AtlasNavigationItem("卡片册", Icons.Default.CollectionsBookmark, entry?.destination?.hasRoute<AlbumsRoute>() == true || entry?.destination?.hasRoute<AlbumRoute>() == true) { nav.navigate(AlbumsRoute) { launchSingleTop = true } }
                AtlasNavigationItem("设置", Icons.Default.Settings, entry?.destination?.hasRoute<SettingsRoute>() == true) { nav.navigate(SettingsRoute) { launchSingleTop = true } }
            }
        }
    }) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (wide && !editing) NavigationRail {
                NavigationRailItem(selected = entry?.destination?.hasRoute<LibraryRoute>() == true, onClick = { nav.navigate(LibraryRoute) { popUpTo<LibraryRoute>(); launchSingleTop = true } }, icon = { Icon(Icons.Default.PhotoLibrary, null) }, label = { Text("图鉴") })
                NavigationRailItem(selected = entry?.destination?.hasRoute<AlbumsRoute>() == true || entry?.destination?.hasRoute<AlbumRoute>() == true, onClick = { nav.navigate(AlbumsRoute) { launchSingleTop = true } }, icon = { Icon(Icons.Default.CollectionsBookmark, null) }, label = { Text("卡片册") })
                NavigationRailItem(selected = entry?.destination?.hasRoute<SettingsRoute>() == true, onClick = { nav.navigate(SettingsRoute) { launchSingleTop = true } }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("设置") })
            }
            Column(Modifier.weight(1f)) {
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    val progress by vm.backup.progress.collectAsStateWithLifecycle()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(progress ?: "正在处理", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton(onClick = vm::cancelOperation) { Text("取消") } }
                }
                if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else if (loadFailed) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button(onClick = vm::reload) { Text("加载资料 / 重试") } }
                else NavHost(nav, startDestination = LibraryRoute) {
                    composable<LibraryRoute> {
                        var selectedDetail by rememberSaveable { mutableStateOf<String?>(null) }
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(if (wide && selectedDetail != null) 0.55f else 1f)) {
                                LibraryScreen(vm, false, { if (wide) selectedDetail = it else nav.navigate(DetailRoute(it)) }, { vm.openEditor("new"); nav.navigate(EditorRoute("new")) }, { nav.navigate(CategoriesRoute) }, { nav.navigate(PrintRoute(it.joinToString(","))) })
                            }
                            if (wide && selectedDetail != null) {
                                VerticalDivider()
                                Box(Modifier.weight(0.45f)) { DetailScreen(vm, selectedDetail!!, { selectedDetail = null }, { val id = selectedDetail!!; vm.openEditor(id); nav.navigate(EditorRoute(id)) }) }
                            }
                        }
                    }
                    composable<TrashRoute> { LibraryScreen(vm, true, { nav.navigate(DetailRoute(it)) }, {}, { nav.popBackStack() }, {}) }
                    composable<SettingsRoute> { SettingsScreen(vm, { nav.navigate(TrashRoute) }, { nav.navigate(CategoriesRoute) }, { nav.navigate(AiSettingsRoute) }) }
                    composable<AiSettingsRoute> { AiSettingsScreen(vm, { nav.popBackStack() }, { recommendationOpen = true }) }

                    composable<AlbumsRoute> { AlbumsScreen(vm) { nav.navigate(AlbumRoute(it)) } }
                    composable<AlbumRoute> { entry -> AlbumDetailScreen(vm, entry.toRoute<AlbumRoute>().id, { nav.popBackStack() }, { nav.navigate(DetailRoute(it)) }, { nav.navigate(PrintRoute(it.joinToString(","))) }) }
                    composable<PrintRoute> { entry -> PrintScreen(vm, entry.toRoute<PrintRoute>().ids.split(','), { nav.popBackStack() }) }
                    composable<CategoriesRoute> { CategoriesScreen(vm) { nav.popBackStack() } }
                    composable<DetailRoute> { entry ->
                        val id = entry.toRoute<DetailRoute>().id
                        DetailScreen(vm, id, { nav.popBackStack() }, { vm.openEditor(id); nav.navigate(EditorRoute(id)) })
                    }
                    composable<EditorRoute> { entry ->
                        val id = entry.toRoute<EditorRoute>().id
                        LaunchedEffect(id, busy) { if (!busy && vm.editor.value?.key != id) vm.openEditor(id) }
                        EditorScreen(vm) { nav.popBackStack() }
                    }
                }
            }
        }
    }
    if (recommendationOpen) {
        ModalBottomSheet(onDismissRequest = { recommendationOpen = false }, containerColor = MaterialTheme.colorScheme.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
                AiRecommendScreen(vm, { recommendationOpen = false }, { recommendationOpen = false; nav.navigate(AiSettingsRoute) { launchSingleTop = true } }, { id -> recommendationOpen = false; nav.navigate(DetailRoute(id)) }, recommendationSession)
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
            }
        }
    }
}
@Composable private fun RowScope.AtlasNavigationItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.weight(1f).clip(MaterialTheme.shapes.medium).selectable(selected = selected, role = Role.Tab, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, null, tint = tint)
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        Box(Modifier.size(16.dp, 3.dp).background(if (selected) tint else Color.Transparent, CircleShape))
    }
}

@Composable fun PageTitle(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(windowInsets = WindowInsets(0, 0, 0, 0), colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent), title = { Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }, actions = actions)
}

@Composable private fun LibraryScreen(vm: AtlasViewModel, trash: Boolean, detail: (String) -> Unit, create: () -> Unit, categories: () -> Unit, print: (List<String>) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var gender by rememberSaveable { mutableStateOf<String?>(null) }
    var style by rememberSaveable { mutableStateOf<String?>(null) }
    var ratio by rememberSaveable { mutableStateOf<String?>(null) }
    var filters by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addingToAlbum by remember { mutableStateOf(false) }
    val phone = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } < 600.dp
    val cards = remember(state, query, category, gender, style, ratio, trash) { filterCards(state.cards, CardFilter(query, category, gender, style, ratio, trash)) }
    Column {
        if (trash) PageTitle("回收站", categories) {
            TextButton(onClick = { selected = state.cards.filter { it.deleted }.map { it.id }.toSet(); confirmDelete = selected.isNotEmpty() }) { Text("清空") }
        } else Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("POSE ATLAS", style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                Text("今日灵感", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = categories) { Icon(Icons.Default.Category, "管理分类", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            FilledIconButton(onClick = create, shape = CircleShape) { Icon(Icons.Default.Add, "新建卡片") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), placeholder = { Text("搜索标题、编号、标签", maxLines = 1, overflow = TextOverflow.Ellipsis) }, singleLine = true, shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
            leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { IconButton(onClick = { filters = true }) { Icon(Icons.Default.Tune, "筛选") } })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AtlasFilterChip(category == null, "全部 ${cards.size}") { category = null }
            state.categories.forEach { c -> AtlasFilterChip(category == c.id, c.name) { category = c.id } }
        }
        if (selected.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("已选 ${selected.size}")
            if (!trash) { TextButton(onClick = { print(selected.toList()) }) { Text("打印") }; TextButton(onClick = { addingToAlbum = true }) { Text("加入册子") } }
            if (trash) TextButton(onClick = { val ids = selected; vm.action("已恢复") { vm.repository.mutate { setDeleted(ids, false) } }; selected = emptySet() }) { Text("恢复") }
            TextButton(onClick = { confirmDelete = true }) { Text(if (trash) "永久删除" else "移入回收站") }
            TextButton(onClick = { selected = emptySet() }) { Text("取消") }
        }
        if (cards.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (trash) "回收站为空" else if (state.cards.none { !it.deleted }) "还没有卡片，点击右上角新建" else "筛选无结果，试试其他条件") }
        else LazyVerticalGrid(if (phone) GridCells.Fixed(1) else GridCells.Adaptive(if (prefs.dense) 140.dp else 200.dp), modifier = Modifier.testTag("library-grid"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(cards, key = { it.id }) { card ->
                var flipped by rememberSaveable(card.id) { mutableStateOf(false) }
                val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(if (ValueAnimator.areAnimatorsEnabled()) 500 else 0), label = "卡片翻面")
                var page by rememberSaveable(card.id) { mutableIntStateOf(0) }
                val photo = card.images.getOrNull(page) ?: card.images.first()
                Card(
                    Modifier.fillMaxWidth().graphicsLayer {
                        // 在侧面切换内容并反向补偿，让边框和文字一起翻转且背面不镜像。
                        rotationY = if (rotation <= 90f) rotation else rotation - 180f
                        cameraDistance = 16f * density
                    }.testTag("pose-card-${card.id}"),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                    border = if (prefs.frame) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (rotation <= 90f) {
                            Box {
                                PhotoView(photo, vm.repository.images, card.title, Modifier.testTag("card-photo-${card.id}").fillMaxWidth().aspectRatio(photoAspect(photo).toFloat()).clip(RoundedCornerShape(18.dp)).combinedClickable(onClickLabel = "翻面看教学", onClick = { flipped = true }, onLongClickLabel = "选择卡片", onLongClick = { selected = selected + card.id }).background(MaterialTheme.colorScheme.surfaceVariant))
                                Row(
                                    Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("card-controls-${card.id}"),
                                ) {
                                    IconButton(onClick = { detail(card.id) }, modifier = Modifier.size(48.dp)) {
                                        PhotoActionIcon(Icons.Default.OpenInFull, "查看详情：${card.title}")
                                    }
                                    IconToggleButton(
                                        checked = card.id in selected,
                                        onCheckedChange = { checked -> selected = if (checked) selected + card.id else selected - card.id },
                                        modifier = Modifier.size(48.dp).semantics { contentDescription = "选择卡片：${card.title}" },
                                    ) {
                                        PhotoActionIcon(if (card.id in selected) Icons.Default.CheckCircle else Icons.Default.Add, null)
                                    }
                                }
                            }
                            Column(Modifier.combinedClickable(onClick = { detail(card.id) }, onLongClick = { selected = selected + card.id }).padding(horizontal = if (phone) 8.dp else 0.dp).padding(top = 16.dp)) {
                                Text(card.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(card.title, style = if (phone) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text((listOfNotNull(card.gender) + card.styles.orEmpty() + card.tags).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                Text(resolveNotes(card, photo).composition, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { page = (page - 1 + card.images.size) % card.images.size }, enabled = card.images.size > 1) { Icon(Icons.Default.ChevronLeft, "上一张图片") }
                                    Text("${page + 1} / ${card.images.size}", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    IconButton(onClick = { page = (page + 1) % card.images.size }, enabled = card.images.size > 1) { Icon(Icons.Default.ChevronRight, "下一张图片") }
                                }
                            }
                        } else Column(Modifier.testTag("card-back-${card.id}").fillMaxWidth().combinedClickable(onClickLabel = "返回正面", onClick = { flipped = false }, onLongClickLabel = "选择卡片", onLongClick = { selected = selected + card.id }).padding(20.dp)) {
                            Text(card.title, style = MaterialTheme.typography.titleLarge)
                            Text("拍摄教学", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                            NotesView(resolveNotes(card, photo))
                        }
                    }
                }
            }
        }
    }
    if (filters) ModalBottomSheet(onDismissRequest = { filters = false }) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
            Text("筛选", style = MaterialTheme.typography.headlineSmall)
            ChoiceRow("性别", listOf("全部") + genders, gender ?: "全部") { gender = it.takeUnless { it == "全部" } }
            ChoiceRow("气质风格", listOf("全部") + styles, style ?: "全部") { style = it.takeUnless { it == "全部" } }
            ChoiceRow("图片比例", listOf("全部", "1:1", "2:3", "3:4", "4:3", "9:16", "16:9"), ratio ?: "全部") { ratio = it.takeUnless { it == "全部" } }
            TextButton(onClick = { gender = null; style = null; ratio = null; category = null; query = "" }) { Text("重置筛选") }
            Button(onClick = { filters = false }, modifier = Modifier.fillMaxWidth()) { Text("查看结果") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(if (trash) "永久删除所选卡片？" else "移入回收站？") }, text = { Text(if (trash) "此操作无法撤销。" else "可以在设置中的回收站恢复。") }, confirmButton = { TextButton(onClick = {
        val ids = selected
        if (trash) vm.action("已永久删除") { vm.repository.mutate(cleanup = true) { deletePermanently(ids) } } else vm.trash(ids)
        selected = emptySet(); confirmDelete = false
    }) { Text("确认") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
    if (addingToAlbum) AlertDialog(onDismissRequest = { addingToAlbum = false }, title = { Text("加入卡片册") }, text = { Column {
        if (state.albums.isEmpty()) Text("请先在卡片册页面新建。")
        state.albums.forEach { album ->
            TextButton(onClick = {
                val ids = selected
                vm.action("已加入卡片册") {
                    vm.repository.mutate {
                        saveAlbums(vm.state.value.albums.map { if (it.id == album.id) it.copy(cardIds = (it.cardIds + ids).distinct()) else it })
                    }
                    addingToAlbum = false
                }
            }) { Text(album.name) }
        }
    } }, confirmButton = { TextButton(onClick = { addingToAlbum = false }) { Text("关闭") } })
}

// 深色轮廓让透明图标在亮背景上可读，白色主体则适配暗照片。
@Composable private fun PhotoActionIcon(icon: ImageVector, description: String?) {
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, modifier = Modifier.size(30.dp), tint = Color.Black.copy(alpha = 0.8f))
        Icon(icon, description, modifier = Modifier.size(26.dp), tint = Color.White)
    }
}

@Composable private fun AtlasFilterChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected, onClick, { Text(label) }, shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface, selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = MaterialTheme.colorScheme.outlineVariant, selectedBorderColor = Color.Transparent))
}

@Composable fun ChoiceRow(label: String, choices: List<String>, selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { choices.forEach { FilterChip(it == selected, { onSelect(it) }, { Text(it) }, enabled = enabled, shape = CircleShape) } }
}
@Composable fun NotesView(notes: Notes) {
    noteLabels.values.zip(notes.values()).forEach { (label, text) ->
        Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        Text(text.ifBlank { "暂无说明" }, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun DetailScreen(vm: AtlasViewModel, id: String, back: () -> Unit, edit: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val card = state.cards.find { it.id == id }
    Column {
        PageTitle(card?.title ?: "卡片不存在", back) { if (card != null && !card.deleted) TextButton(onClick = edit) { Text("编辑") } }
        if (card != null) LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(card.images, key = { it.id }) { photo -> Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) { Column(Modifier.padding(16.dp)) {
                PhotoView(photo, vm.repository.images, card.title, Modifier.fillMaxWidth().heightIn(max = 500.dp).aspectRatio(photoAspect(photo).toFloat()))
                NotesView(resolveNotes(card, photo))
                OutlinedButton(onClick = { vm.action {
                    val files = withContext(Dispatchers.IO) { vm.pdf.shareImages(card, photo, state.categories.find { it.id == card.categoryId }?.name.orEmpty()) }
                    shareImages(context, files)
                } }) { Text("分享此图的卡片正反面") }
            } } }
            if (card.privateNote.isNotBlank()) item { Text("私人备注 · 仅本机", style = MaterialTheme.typography.titleSmall); Text(card.privateNote) }
            item { OutlinedButton(onClick = { vm.action(if (card.deleted) "已恢复卡片" else "已移入回收站") { vm.repository.mutate { setDeleted(setOf(card.id), !card.deleted) }; back() } }) { Text(if (card.deleted) "恢复卡片" else "移入回收站") } }
        }
    }
}
