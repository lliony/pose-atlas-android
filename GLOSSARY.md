# GLOSSARY — 架构评审词汇表

> 记录本仓库在架构评审（`/improve-codebase-architecture`）过程中成型的、可复用的模块/接缝/接口词汇。随实现惰性增补。
> 术语始终优先于「组件/服务/边界/分层/封装」这类空泛词。

## 工作区写入接口（workspace-write interface）

- **状态**：已落地（C1 建议，本仓库顶层完成项）。
- **是什么**：`AtlasViewModel` 暴露的**命名写入动作**，是屏幕写工作区的唯一入口。屏幕不再直接调 `repository.mutate`/`saveAlbums`/`saveCategories`。
- **动作集合**：`addCardsToAlbum` / `setAlbumCards` / `moveCardInAlbum` / `removeCardsFromAlbum` / `reorderAlbums` / `reorderCategories` / `saveAlbum` / `saveCategory` / `deleteAlbum` / `deleteCategory` / `trash` / `restoreCards` / `toggleTrash` / `deletePermanently`。
- **为什么是接缝**：写入语义（去重、排序、边界钳制、撤销）被收进一个可替换的平面，界面只看得到动作名；`saveAlbums`/`saveCategories` 整表重写入口已删除。
- **保留**：`repository.mutate` 仍存在（备份 `BackupRepository` 与编辑器走它），只是退出了界面写路径；图片加载仍直接走 `repository`（归 X1 处理）。

## WorkspaceRules 模块

- **是什么**：`domain/logic/WorkspaceRules.kt`，工作区命名操作的**纯列表变换**规则，零数据库依赖。
- **函数**：`moveInList`（±1 换位、边界钳制、缺 id 原样返回）、`addCardIds`（追加并去重、保序）、`removeCardIds`（滤除）。
- **为什么**：规则可被 `WorkspaceTest` 直接覆盖，且与持久化解耦。

## WorkspaceStore 深模块

- **是什么**：`data/local/WorkspaceStore.kt`，所有工作区写入都包在 `db.withTransaction` 里、只写被触碰的行（新增 `syncAlbumCards` 将册成员整组/单条按 `sort` 重写；`saveAlbum`/`saveCategory` 做校验）。
- **已有能力**：`read` / `replace` / `merge` / `saveCard` / `setDeleted` / `deletePermanently` / `deleteCategory` / `saveDraft` / `draft` / `deleteDraft` / `referencedSources`，事务性 + `validateState`。

## 界面副作用动作（UI side-effect actions）+ 仓库读取缝

- **状态**：已落地（X1 建议）。
- **是什么**：`AtlasViewModel` 把原先散在屏幕里的 data 句柄副作用收成**命名动作**：`importPhoto`/`importPhotos`/`testAi`/`requestRecommendation`/`renderPdf`/`sharePdfImages`/`exportBackup`/`importBackup`。`repository`/`pdf`/`ai`/`backup` 已 `private`，屏幕不再摸 data。
- **图片只读柄**：屏幕展示仍经 `vm.images`（`repository.images` 的只读代理）`PhotoView` 解码；写/导入一律走命名动作。
- **保留公开**：`aiConfig` 只读（读 `state`；其写入经命名动作，见 C3）；`repository` 的 `store` 仅供编辑器会话（归 C2）。
- **读取缝**：`WorkspaceEntryPoint`（`data/local/WorkspaceRepository.kt` 的 `@EntryPoint`）让测试从应用组件取同一个 `WorkspaceRepository` 单例布置数据。

## 编辑器会话模块（EditorSessionController）+ EditorRules

- **状态**：已落地（C2 建议）。
- **是什么**：`ui/EditorSessionController.kt`，拥有 card/original/draft/crop 的**合并状态**（`editor`/`cropSession`/`cropError` + 400ms 草稿防抖 job）。是裁切事务（`NonCancellable` 裁清 → 身份重查 → 换图 → 孤儿清理）与草稿去重的唯一持有者；VM 只留 busy 闸与 `action` 编排。
- **读写 seam**：`EditorWorkspace`（`draft`/`saveDraft`/`saveCard`/`deleteDraft`）把持久化挡在会话事务之外；VM 用 `repository` 实现，测试可替换成内存实现。
- **纯规则**：`domain/logic/EditorRules.kt` —— `openTarget`（已有→编辑 / 缺失→用 `nextCardCode` 新建，无分类抛「请先添加分类」）、`sameCardAndImage`（裁切身份比对）。`EditorRulesTest` 6 用例覆盖。
- **保留在 VM**：`fillEditor`/`undoFill`/`editorUndo`（AI 填空与撤销属 C3 范畴，不与草稿/裁切共址）。

## AI 配置写入（AiConfigStore 命名操作）+ AiProfileRules

- **状态**：已落地（C3 建议）。
- **是什么**：`AiConfigStore` 把公开的 `set(AiConfiguration)` 收成 **private `update(transform)`**，只留命名操作：`useForRecommend(id)`/`useForDraft(id)`/`upsert(profile)`/`delete(id)`/`clear()`。transform 在 mutex 内基于当前 `mutable.value` 合并——**上游整份 `config.copy` 快照变换消失**，「删档案复活、陈旧覆盖」隐患随之灭。
- **纯规则**：`domain/logic/AiProfileRules.kt` —— `normalize`（校验名/模型/key 非空 + `normalizeAiUrl`）、`upsert`（按 id 去重视导 + `recommendId/draftId` 为空则默认指向新档案）、`delete`（移除 + 清指向）。`AiProfileRulesTest` 6 用例。
- **VM 命名动作**：`upsertAiProfile`（内含 normalize）/`deleteAiProfile`/`useForRecommend`/`useForDraft`/`clearAiConfig`，全部过 busy 闸；屏幕只读 `aiConfig.state`，不再碰写入。

## 图片地址唯一持主（MediaRef）

- **状态**：已落地（X3 建议）。
- **是什么**：`domain/model/MediaRef.kt`，`/api/media/` 导入与 `/images/` 种子两套地址惯例的唯一声明处。
- **谓词**：`isImported` / `isSeed` / `isValid`（只认两套，种子限 `street|cafe|park|sea|indoor|stairs|pose-reference-styles`）`mediaFileName`（取 uuid 落盘名）/ `newId`。
- **为什么**：原先七处（`CardRules` 校验/宽高比、`ImageStore` 落盘/取源、`BackupCodec` 键校验、`BackupRepository` 三处、`PhotoView` 展示）各写各的正则/前缀，出现过「备份允许任意 `images/*.png` 而校验只认七张」的惯例分叉；现全部改引一个持主。

## AI 填空结构化（parseGenerated + GeneratedCard）与图片上限（MAX_IMAGES_PER_CARD）

- **状态**：已落地（X2 建议）。
- **是什么**：`domain/logic/AiRules.kt` 的 `GeneratedCard` 由 `private` 转 `public`，新增边界 `parseGenerated(raw)` 只在 AI 响应处解析一次；`fillBlankFields(card, generated: GeneratedCard)` 只做填充逻辑，不再收 JSON 串。`AiRepository.fill` 改为返回 `GeneratedCard`，VM 直接 `fillBlankFields(current, generated)` —— 消除了「先把 Card 编回 JSON 再重解析」的双重往返。
- **单一持主**：`CardRules.kt` 顶层 `const val MAX_IMAGES_PER_CARD = 20`；`validateState` 门禁、`EditorScreen` 选择器上限/两处 require/按钮禁用全部引用它，不再散落字面 `20`。

## 图谱

- `WorkspaceRules(纯规则) ──由──> WorkspaceStore(写行) ──由──> AtlasViewModel(命名动作=interface) ──由──> 界面`
- 编辑器链路：`EditorRules(纯规则) ──由──> EditorSessionController(会话模块) ──EditorWorkspace seam──> WorkspaceStore`
- 界面读取：`AtlasViewModel ──只读代理──> vm.images ──> ImageStore`
- AI 配置：`AiProfileRules(纯规则) ──由──> AiConfigStore(命名操作) ──由──> AtlasViewModel(动作) ──由──> AiScreens 只读 state`
- AI 填空：`ai.fill ──parseGenerated──> GeneratedCard ──fillBlankFields──> Card`
- 图片地址：`CardRules / ImageStore / BackupCodec / BackupRepository / PhotoView ──引用──> MediaRef`
- 测试：`WorkspaceEntryPoint ──> WorkspaceRepository` 单例