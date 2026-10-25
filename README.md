# 姿势图鉴 · Android

Kotlin + Jetpack Compose 原生离线摄影姿势卡应用。Android 8.0（API 26）及以上。

## 当前状态

已实现离线图鉴、编辑与草稿、分类与回收站、卡片册、ZIP 备份、PDF/PNG 分享和可配置 AI。当前版本为 `1.0.0-beta.2`，见 [Beta 发布说明](docs/releases/v1.0.0-beta.2.md)；安装包通过 GitHub 预发布页面提供。
与 [设计规格](docs/superpowers/specs/2026-10-22-pose-atlas-android-design.md) 的差距和验证证据见 [验收记录](docs/superpowers/plans/acceptance.md)。

文档导航见 [文档索引](docs/README.md)。

## 扫码下载 Beta

[![扫码下载 v1.0.0-beta.2 APK](docs/images/beta-download-qr.png)](https://github.com/lliony/pose-atlas-android/releases/download/v1.0.0-beta.2/pose-atlas-1.0.0-beta.2-debug.apk)

[直接下载 APK](https://github.com/lliony/pose-atlas-android/releases/download/v1.0.0-beta.2/pose-atlas-1.0.0-beta.2-debug.apk) · [发布说明与附件](https://github.com/lliony/pose-atlas-android/releases/tag/v1.0.0-beta.2)

仓库公开，扫码后可直接下载安装包，无需登录 GitHub。此安装包为调试签名 Beta 测试版。

从旧 Release beta.1 升级时，因签名不同，请先在旧应用中导出 ZIP 备份并确认保存，再卸载旧版、安装新版并导入。直接卸载会删除本地数据。

## 构建

安装 JDK 17 或 21、Android SDK Platform 35、Build Tools 35.0.0，在本机 `local.properties` 中设置 `sdk.dir`，或配置 `ANDROID_HOME`。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

macOS/Linux 使用 `./gradlew`。首次构建需要访问 Google Maven、Maven Central 和 Gradle 分发服务器。Gradle Wrapper 固定为 8.11.1，包含官方 SHA-256 校验；依赖版本集中在 `gradle/libs.versions.toml`。

Debug APK 路径：`app/build/outputs/apk/debug/app-debug.apk`。安装：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 数据与隐私

设计采用 Room 和应用私有文件存储，不需要账号或服务端。种子数据来自本地 Web 基线；源 JSON 原样保留，人物气质参考数据独立扩充。用户迁移通过应用内 ZIP，系统自动备份禁用。

`.tools/` 是本机开发工具与缓存，`local.properties` 是本机 SDK 路径，均不提交。签名材料、API 密钥不得加入版本控制。

## 使用与架构

图鉴支持搜索、筛选、多选和翻面；编辑器支持多图、手动裁切、每图笔记、草稿恢复。上传保留原图，选中图片后可进入全屏裁切，选择比例并拖动、缩放画面，确认后应用；取消保留原图，保存失败可重试。设置中管理分类、回收站和 ZIP 导入导出。卡片册可整理卡片并生成正反面 PDF；纸张、方向和翻转设置会保存在本机。

手机竖屏采用单列等宽、随当前图片原比例自适应高度的卡片，图片四周保留 12dp 内边距。点击图片翻到教学背面；详情和添加使用照片右上角无底色的覆盖图标，点击背面返回正面。长卡片随列表滚动，多图分页仍可用。底部为“图鉴 / 卡片册 / 设置”。图鉴和卡片册右下角的圆形星光图标打开应用内推荐浮层，收起后保留本次输入与结果，不需要系统悬浮窗权限。新增页的“AI 填充”位于“添加照片”下方并随表单滚动；编辑已有卡片时仍在顶部固定显示，显示目标服务，支持取消与撤销，仅填充空字段。模型配置仍在设置中管理。卡片交互与验证见 [原图比例卡片规格](docs/superpowers/specs/2026-10-25-adaptive-card-interaction.md)，历史裁切验收见 [手动裁切与 AI 入口验收](docs/superpowers/specs/manual-crop-ui.md)。

单模块分为 `ui`（Compose / ViewModel）、`domain`（规则和模型）、`data`（Room、图片、备份、打印、AI）。图片存于应用私有目录，Room 保存稳定引用；备份是跨设备迁移方式。

## 备份互通

设置 → 导出 ZIP，可选择是否包含图片。导入支持 Web ZIP 和旧 JSON，提供合并和替换。合并保留未保存草稿；替换会清除原草稿。私人备注保留，模型密钥不进入备份。缺失图片会报告数量。

旧 JSON 导入采用设备内存预算（最多 32 MiB，且不超过最大堆的 1/12），大备份请在 Web 导出为 ZIP。正常异常支持导入回滚，但进程在提交文件与数据库之间被强杀的恢复尚未实现。

## AI 配置

在设置中添加兼容 Chat Completions 的 HTTPS 地址、模型名和 API key，可为推荐与填卡分别选择档案。勾选记住后通过 Android Keystore AES-GCM 加密保存；否则仅保存在进程内。清除应用数据会删除配置。

AI 请求由手机直连配置的服务商，仅在主动操作时发送输入和必要候选资料；私人备注不发送。照片和描述会离开设备，界面显示目标主机。没有内置服务或密钥，真实服务联调需自行配置。

## Release 与签名

`./gradlew assembleRelease` 开启 R8 和资源压缩。没有生产签名时输出 `app/build/outputs/apk/release/app-release-unsigned.apk`，不能直接安装。生产签名由本机 `keystore.properties` 提供 `storeFile/storePassword/keyAlias/keyPassword`，或设置对应的 `POSE_ATLAS_STORE_FILE`、`POSE_ATLAS_STORE_PASSWORD`、`POSE_ATLAS_KEY_ALIAS`、`POSE_ATLAS_KEY_PASSWORD` 环境变量。

CI 配置位于 `.github/workflows/android.yml`，执行测试、Lint、构建并上传产物。推送 `v*` 标签时，`release` 任务会在验证通过后把 Debug 包改名为 `pose-atlas-<版本>-debug.apk`，连同 `SHA256SUMS.txt` 附加到该标签对应的 GitHub Release，并把 `docs/releases/<标签>.md` 作为发布说明，含 `-` 的标签自动标记为预发布。若希望 CI 产物与此前本机分发的 Debug 包使用同一证书（可覆盖安装），需在仓库 Secrets 中配置 `ANDROID_DEBUG_KEYSTORE_BASE64`（本机 `~/.android/debug.keystore` 的 Base64）；未配置时 CI 使用 runner 自带调试证书，签名不同只能卸载重装。远端 CI 已在 2026-10-24 的 push 中完整通过测试、Lint 与构建。

## 验收边界

自动化构建不能替代真机打印、分享接收、TalkBack、性能或用户自带 AI 服务的联调。未执行的检查会明确记录，不将其标记为通过。

卡片册目前使用上下移动排序，尚无拖拽；部分字段错误尚未就地定位。极端狭长图片须先裁切再分享，避免生成过大的位图。PNG 底部尺寸标签的单位换算仍待校正。完整剩余项见验收记录。
