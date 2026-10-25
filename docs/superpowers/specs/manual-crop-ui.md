# 手机卡片、手动裁切与 AI 入口

> 卡片呈现规则现由[原图比例卡片与图片翻面交互](2026-10-25-adaptive-card-interaction.md)替代；本页裁切、保存与 AI 规则继续有效，下方验收记录是历史结果。


按用户确认方案实施；不修改最初归档的设计文档。

## 行为

- 推荐入口为 56 dp 圆形星光按钮，只保留无障碍标签；推荐浮层行为不变。
- 手机卡片单列、左右 16 dp；图片框为 4:3，Fit 完整显示，不拉伸或自动截断原图。标题预留两行，标签一行，摘要三行；图片分页行统一预留，编号单行省略。字号随系统缩放，卡片正面同宽等高。
- 移除设置中的图鉴显示比例控件，保留旧 DataStore 字段兼容；打印排版和导出保持原规则。
- 编辑页顶部固定 AI 填充主色按钮，附带服务提示、忙碌/取消、撤销；只填空字段。
- 上传后仍保留图片。点击裁切进入全屏编辑，选择原比例/1:1/2:3/3:2/3:4/4:3/9:16/16:9；拖动画面、双指缩放或使用滑块、重置。
- 九宫格和遮罩显示裁切范围，图像始终覆盖框。切换比例保留中心并限制越界。横向裁切界面使用左右分区，竖向使用上下分区，控制区可滚动。
- 确认后生成新图片并保留图片 ID、笔记、AI 标记；新图取消 panel 并更新尺寸。取消不改变图片，保存失败留在裁切页，可重试。正常保存后走既有草稿与卡片保存流程。

## 实现与一致性

- `NormalizedCrop` 表达方向校正后图片或选中 panel 内的归一化矩形；`cropWindow` 处理比例、缩放、中心和边界。
- `ImageStore.crop(photo, region)` 在 IO 线程创建新 JPEG，不覆盖原文件。
- 裁切会话绑定卡片、图片 ID、源引用与 panel；写入完成后重新核对。取消或目标变更时清理未使用的新文件，不覆盖新的编辑内容。
- `PhotoView` 接受 ContentScale，默认 Fit；没有数据库迁移或新权限，没有外部裁切服务。

## 验证记录

- 新裁切接口测试首次因缺少接口编译失败；实现后几何、尺寸、元数据和原图保留测试通过。
- 圆形推荐入口 UI 测试先发现宽高不一致；长编号等高测试先发现编号换行撑高卡片。
- 裁切确认最初 UI 测试仅轮询 ViewModel 字段，在 Robolectric 暂停主线程队列时超时；后台线程均为空闲。改为等待裁切页面的语义状态，以同步主线程，不增加任意延时。
- 独立只读审查未发现裁切坐标/取消/元数据错误；提出的长编号撑高问题已修复并加入测试。
- 补充 Android 像素级测试：EXIF 旋转后选择区域、选中 panel 内裁切；UI 流程覆盖取消、比例、缩放、拖动、确认。

### 最终验收（2026-10-24）

- 46 项 JVM 测试、6 项 Android API 35 模拟器测试通过，失败与错误均为 0。覆盖保存失败后原图保留及重试、顶部 AI 忙碌/撤销、裁切交互和 EXIF/panel 像素结果。
- `testDebugUnitTest lintDebug assembleDebug assembleRelease` 成功；Lint 为 0 错误、55 条警告。日志：`.tools/crop-final.log`；设备验证日志：`.tools/crop-verified.log`。
- Release 产物副本使用本地调试密钥签名后，在模拟器安装、冷启动成功；正式 Release 产物仍未签名。未验证实体手机或真实模型服务请求。
- 已检查单列卡片、顶部 AI 操作区、手动裁切，以及小屏、深色、200% 字体与横屏裁切界面。截图：`.tools/cards-fit.png`、`.tools/editor-ai-top.png`、`.tools/manual-crop-portrait.png`、`.tools/crop-small-dark.png`、`.tools/crop-landscape.png`。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，39,745,602 字节；SHA-256：`0DF275F53101874D233CB061260293DAB62258751EDE66D5E546DDE947602AD5`。
- Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`，20,170,105 字节。
