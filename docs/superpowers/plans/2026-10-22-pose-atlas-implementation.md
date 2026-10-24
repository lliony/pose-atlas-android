# Pose Atlas Android Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task-by-task. Track verification in progress.md.

**Goal:** 实现已归档规格中的原生离线图鉴，按 P1–P4 递进验收。

**Architecture:** 单 app 模块，Compose → ViewModel → Repository → Room / 私有文件 / DataStore。领域规则可独立单测；所有状态写入经过事务，图片通过稳定 src 关联。

**Tech Stack:** Kotlin 2.1.20, AGP 8.9.2, Gradle 8.11.1, Compose, Hilt, Room, kotlinx.serialization。

## Global Constraints

- minSdk 26，compileSdk/targetSdk 35，JVM target 17。
- 包名 com.lliony.poseatlas；名称 姿势图鉴；versionCode 1 / versionName 1.0.0。
- 没有服务端、账号、云同步；密钥不进入备份；只支持 HTTPS AI。
- 依赖固定版本；工具链与本机路径不提交；不改 Web 基线。
- 当前目录是新项目，原地实施；不创建远端、不推送。设计文档保持原样。

## Task 1 — 工程、领域与种子

- [ ] 配置 Gradle wrapper、版本目录、Manifest、Hilt、Compose、Room。
- [ ] 在 app/src/test/java/com/lliony/poseatlas/domain/DomainTest.kt 测试图片笔记继承、全局 ID、分类引用、编号最大值递增。
- [ ] 在 domain/model/Models.kt 与 domain/logic/CardRules.kt 实现 State/Category/Card/Photo/Notes/Album 和规则。
- [ ] 原样复制 seed.json 与七张 PNG；实现 SeedLoader 扩充六张人物参考卡。
- [ ] 验证种子 24 卡/7 分类/1 卡册，合法且可反序列化往返。
- [ ] 运行 .\gradlew.bat testDebugUnitTest，先记录预期失败，再记录通过。

## Task 2 — 持久化与导航

- [ ] Room 实体、DAO、事务、Hilt 模块、StateFlow 仓库；DataStore 种子标记与外观。
- [ ] Robolectric 测试重启不重复播种、删除级联、分类迁移、排序、草稿保存删除。
- [ ] Material 3 三主题、图鉴和设置导航、启动加载与失败重试。
- [ ] .\gradlew.bat testDebugUnitTest lintDebug assembleDebug。

## Task 3 — P1 用户流程

- [ ] 图鉴搜索与性别/风格/比例筛选、分页图片、翻面、详情、多选回收。
- [ ] 编辑表单、图片选择与规范化、裁切、封面排序、图片级笔记、400ms 草稿、返回确认。
- [ ] 分类增改排序迁移、回收站恢复/永久删除、外观设置与统计。
- [ ] 图片 bounds/采样/EXIF/白底 JPEG 测试；筛选与草稿测试。

## Task 4 — 备份互通

- [ ] 按 Web packBackup 制作 fixture；测试 ZIP/旧 JSON、清洗、合并、替换、缺图。
- [ ] 实现 BackupRepository 与 SAF 导入导出、进度取消和中文错误。
- [ ] 测试路径穿越、CRC、压缩比、条目与大小限制；失败后数据库和原图片均不变。
- [ ] Web 双向 fixture 往返；P1 全量构建与验收记录。

## Task 5 — 卡片册、打印和分享

- [ ] 卡片册 CRUD/顺序/去重、网格选卡。
- [ ] 移植 Web print 版面与 backIndex；测试纸张方向、边翻转、尾页和 A4 横向 3:4 八张。
- [ ] PdfDocument/StaticLayout 溢出预检、PdfRenderer 预览、校验页、SAF 和 PrintManager。
- [ ] FileProvider 分享 PNG/PDF；验证私人备注不输出。

## Task 6 — AI

- [ ] 模型档案、Keystore AES-GCM、进程内配置、加密持久化与清除、HTTPS 地址规范化。
- [ ] openai-kotlin + Ktor OkHttp；超时取消、候选隐私过滤、请求/响应限额和错误映射。
- [ ] 推荐输入/范围/结果去重过滤、加入卡片册；编辑器只填空字段及撤销。
- [ ] 单测 URL、候选、响应、填卡；真实服务联调依赖用户档案，不能虚报通过。

## Task 7 — 发布验收

- [ ] 平板导航双栏、TalkBack、字体 200%、关闭动画、大图库测试。
- [ ] CI testDebugUnitTest lintDebug assembleDebug、R8、外部签名配置、README。
- [ ] 最终独立审查与修复；记录自动化结果和需要真机的剩余验收。
