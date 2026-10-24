# Android 验收记录

本轮按用户确认的顺序执行：规格差距核对 → 测试和构建 → 模拟器检查 → 修复和交付。

## 1. 规格与实现核对

| 范围 | 现有实现 | 验收状态 |
| --- | --- | --- |
| 工程和离线数据 | Compose、Hilt、Room、种子、事务、草稿 | 本轮重跑测试 |
| 图鉴与编辑 | 搜索筛选、多选、详情、多图、裁切、笔记、回收站、分类 | 已有 UI 烟雾测试，补模拟器检查 |
| 备份 | ZIP/旧 JSON、Web fixture、SAF、取消和进度、异常回滚 | 格式测试已有；进程强杀时的文件/数据库一致性未保证 |
| 卡片册 | CRUD、加入移出、去重、上下移动排序 | 拖拽排序尚未实现 |
| 打印分享 | 排版、预检、PDF/PNG、系统打印、校验页 | 本轮补齐设置持久化；实际打印机未验收 |
| AI | 档案加密、推荐、填空与撤销、HTTPS 请求 | 规则与 Keystore 可测；无服务凭据，真实服务未验收 |
| 适配发布 | 平板双栏、主题、CI 配置、R8、外部签名 | 本轮检查布局、构建；CI 远端未执行 |

## 差距和验收边界

- 旧 JSON 导入受设备内存预算限制（最多 32 MiB，且不超过堆上限的 1/12），比规格的 512 MiB 更保守。大备份应使用 ZIP。
- 表单错误主要通过消息提示，尚未全部就地显示并定位。
- 大图库仓库测试不能代替真实图片的滚动帧率与峰值内存测量。
- TalkBack、接收端分享、真实双面打印、AI 联调和生产签名需要对应环境；不得按自动化通过推断。
- Ruling: 复用现有项目和进度记录，不重新初始化、不提交或推送；本轮专注验收与发现的问题修复，保留原始设计不变。

## 2–4. 执行结果

### 修复与回归

- 打印纸张、方向、翻转、裁切框通过 DataStore 保存；校验页开关每次进入重置。`PrintPreferencesTest` 验证新存储对象恢复及外观设置不会覆盖打印偏好。
- 空备份导入后可继续进入分类和设置；将加载失败与空资料分开判断。`AppSmokeTest.emptyReplacementKeepsCategoryRecoveryAvailable` 先因找不到管理分类失败，修复后通过。
- 分类迁移在同一事务内迁移草稿引用；`WorkspaceTest.categoryMigrationKeepsCodes` 先发现草稿仍指旧分类，修复后通过。
- PDF 生成时禁用设置控件，并使用请求快照发布结果。`AppSmokeTest.printOptionsCannotChangeDuringGeneration` 先因纸张仍可选失败，修复后通过。
- PNG 分享在分配位图前检查尺寸及像素预算，极端比例给出先裁切提示。`ShareSizeTest` 覆盖正常 300 dpi 尺寸和极端/无效比例。
- 新增的打印偏好和分享尺寸测试首次因接口缺失编译失败，接口实现后通过；这两项没有将编译失败描述为运行时问题复现。

### 自动化

- 基线：`testDebugUnitTest lintDebug assembleDebug assembleRelease` 成功；日志 `.tools/acceptance-baseline.log`。
- 修复后：`testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest` 成功，36 项 JVM 测试、0 失败；日志 `.tools/acceptance-final.log`。
- Lint：0 错误、55 警告；未将警告数隐去或声称零警告。
- `connectedDebugAndroidTest`：API 35 模拟器 3 项通过、0 失败，覆盖 PDF/PdfRenderer、Keystore 与搜索/草稿恢复；日志 `.tools/acceptance-device.log`。
- R8 安装验证：对 unsigned Release 的副本使用本机调试证书签名，安装成功，冷启动 `Status: ok`（1062 ms），图鉴显示正常，crash buffer 为空。副本在 `.tools/pose-atlas-release-smoke.apk`，仅供本地测试，不是生产签名。
- 交付 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，39,703,577 字节（37.86 MiB）。Release unsigned：20,137,337 字节（19.20 MiB）。
- 原设计源文件与归档文件 SHA-256 仍一致。
- JVM 测试包含 500 卡 / 2000 图片引用的数据库往返与编辑，但未测真实 2000 张图片的解码内存或滚动帧率。

### 视觉检查

- API 35 / 1080×2400 手机：24 卡种子、双列图鉴、导航、图片正常。
- 字体 200%：标题、卡片和导航放大，网格可滚动；搜索占位文字会换行，占据更多空间。未等同于 TalkBack 验收。
- 1920×1200、240 dpi 平板：侧边导航、六列网格、点击后图鉴与详情双栏正常。
- 截图：`.tools/phone.png`、`.tools/phone-font200-clean.png`、`.tools/tablet.png`、`.tools/tablet-detail.png`。检查后已恢复模拟器尺寸、密度与字体。

### 审查决策

- 独立只读审查由 `acceptance_review` 完成；四项重要问题均已修复并有回归覆盖，不重复派发审查。
- Ruling: 正反面映射保持规格与 Web 的公式；纵向纸张的实体双面对应仍需校验页确认。若打印机解释不同，需调整翻转选项，不能以模拟器渲染推断真机一致。
- Final: minor (deferred): PNG 页脚目前沿用 PDF 点到毫米的换算，标出的卡尺寸不准确；不影响位图像素尺寸，待单独校正。
- Final: minor (deferred): 卡片册拖拽、字段就地错误和大字体搜索占位布局尚待完善。
- Final: minor (deferred): R8 启动截图中底部导航当前项未高亮；点击导航可用，选中态判断需改为类型化路由匹配，避免混淆后的类名比较。

Debug APK SHA-256：`B3879AE63B38119F4A50BDC1387B50BEFC924D61F87E6EFE8AF5A2B2D33A1CDD`。
