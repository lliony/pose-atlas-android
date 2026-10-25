---
id: PLAN-20261025-ADAPTIVE-CARD
title: 原图比例卡片与图片翻面实施计划
status: completed
last_updated: 2026-10-25
related_spec: ../specs/2026-10-25-adaptive-card-interaction.md
---
# 原图比例卡片与图片翻面实施计划

> 此计划记录首轮实施。用户后续要求“点击背面返回正面、详情和添加覆盖照片”，已由[当前规格](../specs/2026-10-25-adaptive-card-interaction.md)覆盖旧独立顶栏与返回按钮步骤。

> **执行说明：** 使用 `executing-plans` 在当前会话逐项实施；用户选择子代理方式时使用 `subagent-driven-development`。使用 `- [ ]` 跟踪步骤。

**Goal:** 按 HTML 的原图比例卡片布局，加上白边、右上角图标操作与点击图片翻面，替换上一版固定比例和底部按钮方案。

**Architecture:** 在已有 `LibraryScreen` 中局部修改卡片，不新建布局框架或存储接口。继续使用 `photoAspect(photo)`、`PhotoView` 和现有 page / flipped / selected 状态；外层 LazyVerticalGrid 负责长卡片滚动。顶部操作行位于翻转内容之外，图片与正文在原有动画容器内切换教学背面。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Robolectric/Compose UI Test、Gradle。

**Spec:** [原图比例卡片与图片翻面交互](../specs/2026-10-25-adaptive-card-interaction.md)。执行者同时阅读规格与 `~/Downloads/pose-atlas-new-ui.html` 的 `renderCards()`；用户最新交互要求优先于 HTML 的旧 onclick。

## 全局约束

- Android minSdk 26，compileSdk/targetSdk 35，Kotlin 2.1.20，Compose BOM 2025.04.01。
- 原图完整显示，不裁切；图片尺寸与 panel、数据库、PDF/分享均不变；无新依赖。
- 保留 26dp 外圆角；图片内圆角 18dp；卡片内边距 12dp；图标触控区各 48dp、间距 4dp。
- 在已有 `codex/warm-ui-theme` 工作区继续，保留上一轮未提交的 8 个文件改动，不重建 worktree，不恢复旧文件。
- 用户已选择当前会话直接执行；实施与验证进度记录在本计划。
- 提交前必须读取 `enforce-conventions` 的提交阶段与署名映射；中文提交信息、实际 Codex 署名。现有工作区含上一轮改动，不自动全量 stage、不拆分不完整界面提交、不推送；是否提交由后续明确的交付范围决定。

## 文件职责与影响范围

| 文件 | 处理 |
|---|---|
| `app/src/main/java/com/lliony/poseatlas/ui/AtlasApp.kt` | 修改 LibraryScreen 卡片图片比例、留白、顶部图标与翻面事件；保留公共路由、ChoiceRow、NotesView |
| `app/src/test/java/com/lliony/poseatlas/domain/AppSmokeTest.kt` | 增加比例/留白/交互测试；把旧跨卡等高测试改为同图长编号稳定性测试 |
| `app/src/androidTest/java/com/lliony/poseatlas/domain/AppDeviceTest.kt` | 增加设备点击图片翻面及图标隔离测试 |
| `README.md` | 实施后更新当前“手机竖屏”段落，准确说明新布局 |
| `docs/superpowers/specs/manual-crop-ui.md` | 实施后在开头追加卡片呈现规则被新规格替代的链接；原历史验收保持原文 |
| `docs/superpowers/specs/2026-10-25-adaptive-card-interaction.md` | 验收后由 draft 改为 active，记录实际验证边界 |
| `PhotoView.kt`、`CardRules.kt`、`CropFlowTest.kt` | 只读核对，原则上不修改：复用渲染、比例计算，保留正文点击详情入口保障现有裁切流程 |

## 任务 1：原图比例与白边

**文件：** 修改 AtlasApp.kt 的 `LibraryScreen` 卡片块（当前约 215–267 行），测试 AppSmokeTest.kt。

**接口：** 消费 `photoAspect(photo: Photo): Double` 与 `PhotoView(photo, store, description, modifier, contentScale = ContentScale.Fit)`；保留 `pose-card-${card.id}`、`card-photo-${card.id}` 标签。产出测试辅助函数 `showRatioPhoto(width: Int, height: Int): String`，返回更新后的卡片 ID。

- [x] **步骤 1：添加真实比例图片夹具与失败测试。** 在现有 AppSmokeTest 类中添加下列函数与用例；使用真实宽高位图，避免只改元数据制造假比例。

```kotlin
private fun showRatioPhoto(width: Int, height: Int): String {
    compose.waitUntil(20_000) {
        compose.onAllNodesWithContentDescription("新建卡片").fetchSemanticsNodes().isNotEmpty()
    }
    val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val bytes = java.io.ByteArrayOutputStream().apply {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, this)
    }.toByteArray()
    bitmap.recycle()
    val photo = vm.repository.images.import(bytes.inputStream())
    val card = vm.state.value.cards.first()
    kotlinx.coroutines.runBlocking {
        vm.repository.mutate { saveCard(card.copy(images = listOf(photo))) }
    }
    compose.waitUntil(10_000) {
        compose.runOnIdle { vm.state.value.cards.first { it.id == card.id }.images.single().id == photo.id }
    }
    compose.waitForIdle()
    return card.id
}

@Test fun cardPhotoUsesItsOwnAspectAndHasRealInsets() {
    listOf(90 to 160, 100 to 100, 120 to 160, 160 to 90, 100 to 150, 160 to 120).forEach { (w, h) ->
        val id = showRatioPhoto(w, h)
        compose.onNodeWithTag("library-grid").performScrollToIndex(0)
        val photo = compose.onNodeWithTag("card-photo-$id").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("pose-card-$id").getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(w.toFloat() / h, (photo.right - photo.left).value / (photo.bottom - photo.top).value, 0.02f)
        org.junit.Assert.assertTrue(photo.left.value - card.left.value >= 11.5f)
        org.junit.Assert.assertTrue(card.right.value - photo.right.value >= 11.5f)
        org.junit.Assert.assertTrue(photo.top.value - card.top.value >= 11.5f)
    }
}
```

- [x] **步骤 2：只运行新测试，确认固定 4:3 或零内边距断言失败。**

```bash
bash ./gradlew testDebugUnitTest --tests '*AppSmokeTest.cardPhotoUsesItsOwnAspectAndHasRealInsets'
```

- [x] **步骤 3：替换图片布局并去掉强制正文最小行数。** 卡片内容最外层增加 `padding(12.dp)`；图片单独使用下面的修饰链，不能把 padding 放在 PhotoView 内部来伪造宽高比。原图片编号与页码仍放在图片 Box 中。全文移除这张卡标题、元信息、摘要的 `minLines` 参数，保留其 `maxLines` 和省略策略。此阶段先保留既有按钮，下一任务一起移动。

```kotlin
val cardContentModifier = Modifier.padding(12.dp).graphicsLayer {
    rotationY = if (rotation <= 90f) rotation else rotation - 180f
    cameraDistance = 12f * density
}

PhotoView(
    photo, vm.repository.images, card.title,
    Modifier.testTag("card-photo-${card.id}")
        .fillMaxWidth()
        .aspectRatio(photoAspect(photo).toFloat())
        .clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
)
```

将当前翻转 Column 的 modifier 参数替换为 `cardContentModifier`，其内容保持原位。任务 2 添加顶部操作行时，把这层 padding 移到包裹操作行与翻转 Column 的外层 Column，避免重复留白。图片不要加 `heightIn(max = ...)`。外层 grid 保持手机单列、平板自适应列数，不引入瀑布流依赖。

- [x] **步骤 4：替换旧等高测试。** 删除 `cardFrontHeightDoesNotDependOnCodeWrapping` 中比较两张不同卡片高度的部分；用同一张卡片前后比较，避免与原图比例要求冲突。

```kotlin
@Test fun longCodeDoesNotChangeTheSamePhotoCardHeight() {
    val id = showRatioPhoto(120, 160)
    val before = compose.onNodeWithTag("pose-card-$id").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
    val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
    val card = vm.state.value.cards.first { it.id == id }
    kotlinx.coroutines.runBlocking {
        vm.repository.mutate { saveCard(card.copy(code = "01\n02\n03\n04\n05")) }
    }
    compose.waitUntil(10_000) {
        compose.onAllNodesWithText("01\n02\n03\n04\n05").fetchSemanticsNodes().isNotEmpty()
    }
    val after = compose.onNodeWithTag("pose-card-$id").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
    org.junit.Assert.assertEquals(before, after, 1f)
}
```

- [x] **步骤 5：运行 AppSmokeTest，检查同图稳定性、比例与原有流程。**

```bash
bash ./gradlew testDebugUnitTest --tests '*AppSmokeTest'
```

预期：新比例、真实内边距、同图长编号测试通过；现有搜索、草稿、裁切与系统栏测试继续通过。记录结果后审查任务 1 的差异；不自动提交之前尚未提交的主题改动。

## 任务 2：右上角图标与点击图片翻面

**文件：** 修改 AtlasApp.kt 的同一卡片块，测试 AppSmokeTest.kt 和 AppDeviceTest.kt。

**接口：** 消费已有 `flipped: Boolean`、`selected: Set<String>`、`detail: (String) -> Unit`；正文容器独立处理点击详情和长按选择，外卡片不接收点击，避免整卡合并点击区域与图片翻面冲突。新增语义标签 `card-controls-${card.id}`、`card-back-${card.id}`；详情描述为 `查看详情：${card.title}`，选择描述为 `选择卡片：${card.title}`，返回描述为 `返回正面：${card.title}`。

- [x] **步骤 1：在 AppSmokeTest 添加图像、详情和选中动作隔离的失败测试。**

```kotlin
@Test fun photoFlipsWhileTopIconsKeepTheirOwnActions() {
    val id = showRatioPhoto(120, 160)
    val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[com.lliony.poseatlas.ui.AtlasViewModel::class.java]
    val title = vm.state.value.cards.first { it.id == id }.title
    compose.onNodeWithTag("card-photo-$id").performClick()
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("card-back-$id").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("card-back-$id").assertExists()
    compose.onNodeWithText("编辑", useUnmergedTree = true).assertDoesNotExist()
    compose.onNodeWithContentDescription("返回正面：$title").performClick()
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("card-photo-$id").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("card-photo-$id").assertExists()
    compose.onNodeWithContentDescription("选择卡片：$title").performClick().assertIsOn()
    compose.onNodeWithText("已选 1").assertExists()
    compose.onNodeWithTag("card-back-$id").assertDoesNotExist()
    compose.onNodeWithContentDescription("选择卡片：$title").performClick().assertIsOff()
    compose.onNodeWithContentDescription("查看详情：$title").performClick()
    compose.onNodeWithText("编辑").assertExists()
    compose.onNodeWithContentDescription("返回").performClick()
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("card-photo-$id").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("card-photo-$id").assertExists()
    compose.onNodeWithText("翻面看教学").assertDoesNotExist()
}
```

- [x] **步骤 2：运行该用例，确认当前图片打开详情而非翻面导致失败。**

```bash
bash ./gradlew testDebugUnitTest --tests '*AppSmokeTest.photoFlipsWhileTopIconsKeepTheirOwnActions'
```

- [x] **步骤 3：在 12dp 内边距内、旋转容器外增加顶部操作行。** `Icons.Default.OpenInFull`、`CheckCircle`、`RadioButtonUnchecked`、`Flip` 已由现有 filled 通配导入覆盖。详情、选择分配固定 48dp，间距 4dp；140dp 紧凑卡的可用宽度为 116dp，两个主操作合计 100dp。详情和选择组成的 Row 始终先排列；背面返回图标在其后，可换行到顶部区域的第二行，确保紧凑卡的两个主操作仍在最上方。

```kotlin
FlowRow(
    Modifier.fillMaxWidth().testTag("card-controls-${card.id}"),
    horizontalArrangement = Arrangement.End,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { detail(card.id) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.OpenInFull, "查看详情：${card.title}")
        }
        IconToggleButton(
            checked = card.id in selected,
            onCheckedChange = { checked -> selected = if (checked) selected + card.id else selected - card.id },
            modifier = Modifier.size(48.dp).semantics { contentDescription = "选择卡片：${card.title}" },
        ) {
            Icon(if (card.id in selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null)
        }
    }
    if (flipped) IconButton(onClick = { flipped = false }, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Default.Flip, "返回正面：${card.title}")
    }
}
Spacer(Modifier.height(12.dp))
```

从图片覆盖层中删除原文字“详情” FilledTonalButton；编号保持图片左上角单行省略，不与顶部图标争抢横向空间。删除底部 HorizontalDivider + 包含 Checkbox / TextButton 的 FlowRow。多图分页行原样保留，不将分页箭头变为翻面入口。

- [x] **步骤 4：只在图片增加点击与长按语义，背面增加测试标签。**

```kotlin
// 追加到图片的 clip 后、background 前；不绑定到整张背面。
.combinedClickable(
    onClickLabel = "翻面看教学",
    onClick = { flipped = true },
    onLongClickLabel = "选择卡片",
    onLongClick = { selected = selected + card.id },
)

// 替换现有教学背面的 Column 修饰链，原 NotesView 内容保留。
Column(Modifier.padding(20.dp).testTag("card-back-${card.id}")) {
    Text(card.title, style = MaterialTheme.typography.titleLarge)
    Text("拍摄教学", color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    NotesView(resolveNotes(card, photo))
}
```

正反面的长内容由外层列表滚动，不固定背面高度，不裁掉教学笔记。保留 `ValueAnimator.areAnimatorsEnabled()`、现有旋转时长和超过 90° 的反向补偿。IconButton / IconToggleButton 与图片是不同点击目标，使用 Compose 事件消费机制，不额外编写全局手势拦截。

- [x] **步骤 5：增加原生设备回归用例并运行相关测试。** 在 AppDeviceTest 类中使用真实种子卡验证图片点击；通过顶部返回回正面、选中而不翻面、详情进入编辑入口。复制下列完整用例，不调用 JVM 测试的夹具。

```kotlin
@Test fun cardImageAndTopIconsRemainSeparateOnDevice() {
    compose.waitUntil(20_000) { compose.onAllNodesWithTag("card-photo-card-01-001").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("card-photo-card-01-001").performClick()
    compose.waitUntil(5_000) { compose.onAllNodesWithTag("card-back-card-01-001").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag("card-back-card-01-001").assertExists()
    compose.onNodeWithContentDescription("返回正面：往前走，拍一个背影").performClick()
    compose.onNodeWithContentDescription("选择卡片：往前走，拍一个背影").performClick().assertIsOn()
    compose.onNodeWithTag("card-photo-card-01-001").assertExists()
    compose.onNodeWithContentDescription("查看详情：往前走，拍一个背影").performClick()
    compose.onNodeWithText("编辑").assertExists()
}
```

```bash
bash ./gradlew testDebugUnitTest --tests '*AppSmokeTest' assembleDebug assembleDebugAndroidTest
bash ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lliony.poseatlas.domain.AppDeviceTest
```

预期所有断言通过。若长图使原测试的正文标题处于屏幕外，只在对应断言前增加 `performScrollToNode` 滚动到既有标签，不降低断言或删除用例。审查事件隔离及 140dp 顶栏后再进入最终验收。

## 任务 3：视觉验收与文档同步

**文件：** README.md、旧规格、新规格及本计划；截图保存到本次工作区外的可写产物目录或被忽略的 `.tools/`，不覆盖用户 HTML。

**接口：** 消费任务 1、2 的卡片与测试标签；产出可安装 Debug APK、实测截图和准确的验证记录，无运行时新增接口。

- [x] **步骤 1：运行完整验证。** 沿用已安装的 JDK 21 与 Android SDK 35 环境；路径通过 JAVA_HOME / ANDROID_HOME 指定，不写进项目文档中的本机用户名目录。

```bash
bash ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
bash ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.lliony.poseatlas.domain.AppDeviceTest
```

- [x] **步骤 2：按规格完成模拟器验收。** 导入六种比例的实际图片；逐个核对图片框比例、12dp 留白、两图标顶端位置；卡片底部最后一个内容元素与外框间距至少 12dp，通过布局检查和截图确认。对 9:16 长卡滚动到卡底并返回顶部，点击图片翻面、滚动长教学内容、通过顶部返回正面。多图换页时核对随当前图调整高度。分别检查手机、140dp 紧凑网格、横屏、深色、1.3 倍字号和动画关闭状态。截图必须来自运行中的最终 APK；记录并恢复模拟器原尺寸、字号、动画设置。

- [x] **步骤 3：更新当前行为文档。** 将 README 中以“手机竖屏采用单列等宽卡片”起始的段落中的卡片描述替换为下列文字，原 AI 入口说明接在其后。旧规格顶部增加下列历史范围声明，保留其 2026-10-24 验收记录。

```text
手机竖屏采用单列等宽、随当前图片原比例自适应高度的卡片，图片四周保留 12dp 内边距。点击图片翻到教学背面；详情和选择使用右上角图标，背面可从顶部返回正面。长卡片随列表滚动，多图分页仍可用。底部为“图鉴 / 卡片册 / 设置”。
```

```markdown
> 卡片呈现部分在新方案实施后由[原图比例卡片与图片翻面交互](2026-10-25-adaptive-card-interaction.md)替代；本页裁切、保存与 AI 规则继续有效，下方验收记录是历史结果。
```

新规格标记 active，并追加实际测试数量、Lint 错误数量、模拟器覆盖与未测项；不把上一轮 47 项测试数量照抄为新结果。

- [x] **步骤 4：检查差异与文档。**

```bash
git diff --check
python3 ~/.agents/skills/ai-project-standards/scripts/validate-project-standards.py --root .
```

对本次计划/规格另行检查相对链接、双向引用、无个人绝对路径、没有空章节。权威校验器对尚未采用的正式治理目录会跳过，不能将跳过当作已校验所有新文档。

- [x] **步骤 5：交付。** 提供截图与 APK、测试结果、分支和未提交状态。若用户随后要求提交，按 enforce-conventions 的署名规则提交完整一致的 UI 改动；无推送授权时只保留本地。未验证的真实设备、TalkBack 或 AI 服务明确列出，不自动扩大到性能或全项目治理整改。

## 计划自检

- 比例/留白/长卡滚动：任务 1 和任务 3；右上角图标/事件隔离/移除底部按钮：任务 2。
- 所有新增测试标签及辅助函数均在本计划定义；既有接口已从 owning source 核对。
- 旧“跨卡等高”断言被同图稳定性断言替代；不删除原搜索、草稿、裁切、打印锁定、推荐会话测试。
- 本计划已进入实施；验收仅使用本轮重新执行的测试与构建结果。

## 执行记录

已在当前会话直接实施。实际差异：使用 DpRect 的边界差计算宽高；测试查询图片节点时使用未合并语义树；正文独立点击避免整卡点击范围覆盖图片；旧裁切测试先滚动到正文；正文复用外层留白，避免紧凑卡边距叠加。

验证结果和边界见[规格中的实施与验证](../specs/2026-10-25-adaptive-card-interaction.md#2026-10-25-实施与验证)。保留 `codex/warm-ui-theme` 工作区和全部未提交主题改动，没有合并或推送。
