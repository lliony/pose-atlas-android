---
id: ADMIN-RELEASE
title: 发布管理与扫码下载
status: active
last_updated: 2026-10-26
---
# 发布管理

## 当前安装包

当前版本：**1.0.0-beta.4**。

[![扫码下载 Beta.4 APK](docs/images/beta-download-qr.png)](https://github.com/lliony/pose-atlas-android/releases/download/v1.0.0-beta.4/pose-atlas-1.0.0-beta.4-debug.apk)

[直接下载 APK](https://github.com/lliony/pose-atlas-android/releases/download/v1.0.0-beta.4/pose-atlas-1.0.0-beta.4-debug.apk) · [发布说明和升级注意事项](docs/releases/v1.0.0-beta.4.md) · [文档导航](docs/README.md)

## 每次发布必须提供二维码

1. 更新版本号，完成本地自动测试、Lint 和 APK 构建。
2. 二维码必须指向该版本 APK 的公开直链，不得沿用上一版二维码或指向仓库首页。
3. Release 附件必须同时提供 APK、`SHA256SUMS.txt` 和 `beta-download-qr.png`；发布正文必须显示二维码。
4. 同步更新本文件中的版本和链接、根目录 README 下载入口、`docs/images/beta-download-qr.png`、版本发布说明及文档索引。
5. 发布后检查公开下载链接、远程附件校验值，并扫码核对目标版本；向用户交付时同时提供下载链接和二维码。

标签发布由 [Android 工作流](.github/workflows/android.yml) 根据版本标签自动生成并上传二维码；手动发布同样必须完成以上步骤。

## 签名与覆盖安装

覆盖安装要求包名和签名一致、版本号递增。发布前必须核对实际旧包与新包的签名，不能仅凭版本名称推断。

CI 使用共享签名时需配置 `ANDROID_DEBUG_KEYSTORE_BASE64`。未配置时 runner 会生成临时签名，不能与本机包混用；避免自动发布覆盖已核验签名的附件。密钥不得提交到仓库。

签名不同时遵循对应版本发布说明中的备份和迁移步骤，不得要求用户直接卸载而不备份。
