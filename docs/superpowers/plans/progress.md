# Implementation ledger — 2026-10-22-pose-atlas-implementation.md

## 当前状态

2026-10-24 手动裁切与 AI 入口调整完成：56 dp 圆形推荐图标、统一 4:3 完整显示框、顶部固定 AI 填充、全屏比例选择与拖动缩放裁切。最终 46 项 JVM 测试和 6 项 API 35 模拟器测试通过；Lint 无错误（55 条警告），Debug/Release 构建成功。截图、产物及验证详情见 [手动裁切与 AI 入口](../specs/manual-crop-ui.md)。

2026-10-24 手机 UI 更新完成：竖屏单列、图鉴/卡片册/设置顺序、应用内推荐浮层及状态保留；类型化路由修复导航高亮。最终 37 项 JVM 测试、Lint、Debug/Release 构建成功，API 35 安装启动成功。详见 [手机界面调整](../specs/mobile-ui-refresh.md)。

本地验收的详细差距与证据见 [acceptance.md](acceptance.md)。原始设计与 Web 基线未修改。2026-10-24 用户授权提交、推送至 `lliony/pose-atlas-android` 并发布 Beta；版本为 `1.0.0-beta.1`，说明见 [Beta 发布记录](../../releases/v1.0.0-beta.1.md)。

## 实现范围

- Tasks 1–2：Gradle Wrapper、API 35 工具链、Compose/Hilt、领域规则、种子、Room 事务与草稿、DataStore、导航已实现。
- Tasks 3–4：图鉴、编辑、分类、回收站、设置、图片处理、ZIP/JSON 导入导出、Web fixture、进度取消已实现。
- Task 5：卡片册、PDF 排版预检与预览、系统打印、PDF/PNG 分享已实现；实体打印和分享接收端未验收。
- Task 6：模型档案、Keystore 加密、推荐、填卡与撤销已实现；无用户服务配置，未执行真实 AI 联调。
- Task 7：平板和大字体视觉检查、CI 配置、R8 构建、README 和独立审查已完成本轮可执行部分；TalkBack、生产签名、远端 CI 和真实大图库性能未验收。

## 验证历史

- 初次领域/仓库测试因缺少实现失败，随后 26 项通过（.tools/tests-green.log）。
- Robolectric 不支持实际 PdfDocument nativeCreate，PDF 集成测试改为 Android instrumentation；不把 JVM 模拟结果当作真实 PDF 成功。
- 本轮完整构建成功，36 项 JVM 测试、0 失败（.tools/acceptance-final.log）；设备测试结果记录在验收文档。
- 500 卡 / 2000 图片引用数据库测试通过，仅证明数据操作，不证明图片负载下性能。
- 本轮审查修复：空库恢复入口、草稿分类迁移、打印生成设置竞态、PNG 极端比例内存风险；另补齐打印设置持久化。

## 决策与限制

- Ruling: 复用当前目录与已有项目，不创建工作树或远端；保留代码本地待审阅状态。
- Ruling: 旧 JSON 使用设备内存预算，较规格上限保守，大备份改用 ZIP；代价是部分大 JSON 无法直接导入。
- Ruling: 双面映射遵循规格/Web；纵向纸张的实际送纸效果需打印校验页确认。
- 导入普通异常有回滚，强杀进程时文件与数据库的联合恢复尚无持久日志保证。
- 卡片册拖拽、字段就地错误、PNG 页脚毫米标签换算与大字体搜索占位布局仍待完善。
