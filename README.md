# 金幕影音 · GoldenTVPlayer

![Version](https://img.shields.io/badge/version-1.0.0-blue)
![Build](https://github.com/yc7en/GoldenTVPlayer/actions/workflows/build.yml/badge.svg)
![License](https://img.shields.io/badge/license-GPL--3.0-green)
![Platform](https://img.shields.io/badge/platform-Android--TV%20%2F%20Android-6a0dad)
![Stars](https://img.shields.io/github/stars/yc7en/Golden-TVPlayer)
![Forks](https://img.shields.io/github/forks/yc7en/Golden-TVPlayer)

> 基于 FongMi TV v5.8.3 加固、瘦身与品牌改造的 Android TV / 手机端聚合视频播放器。

## 概述

金幕影音（GoldenTVPlayer）是 [FongMi/TV](https://github.com/FongMi/TV)（影视TV）的深度改造分叉。在保留原版浏览、点播、直播能力的基础上，v1.0.0 围绕**安全加固、体积瘦身与播放栈重构**做了系统性改造，应用名与图标改为「金幕影音 / GoldenTVPlayer」。支持 Android TV（leanback）、手机与平板（mobile）两类设备。

## 功能特性

按功能域划分，完整逐条清单见 [docs/Features.md](docs/Features.md)。

- **浏览与发现（F-1xx）**：首页聚合、跨站搜索、收藏、观看历史、无痕模式、同剧聚合、海报墙。
- **点播播放（F-2xx）**：断点续播、选集与换线、字幕（内嵌/外挂/在线搜索/时间偏移）、弹幕、倍速（0.25×–4×）、音轨切换、画面调整、预加载、片头片尾跳过、后台与画中画、音频增强（含音轨偏移）、媒体会话、m3u8 插播广告过滤。
- **直播（F-3xx）**：频道分组、EPG 节目单、多线路切换（失败自动降级）、开机看直播。
- **数据源与配置（F-4xx）**：多数据源聚合、自定义接口地址、抓包/订阅配置。

### 安全与体积（v1.0.0 破坏性变更）

- TLS 证书校验默认严格，设置页可显式开启「允许不安全证书」（默认关，需二次确认）。
- 本地服务默认仅监听本机，局域网访问需 6 位配对码。
- 默认禁用脚本源（type=3 Spider）与自更新；运行时插件目录默认空，需构建时注入。
- 移除磁力 / BT / ed2k 播放（thunder 模块）与 mpv 第二引擎，主 APK 单引擎化（media3 / ExoPlayer）。

## 版本

当前版本 **v1.0.0**（2026-10-29 首个对外发布）。版本号规则与逐版变更见 [docs/ChangeLog.md](docs/ChangeLog.md)。

## 环境要求（构建）

- JDK 21
- Android SDK（`compileSdk 37` / `targetSdk 37` / `minSdk 24`），需 `platforms;android-37` 与 `build-tools;37.0.0`
- Android Gradle Plugin 9.4.0 / Gradle 9.7.1（由 Gradle Wrapper 管理，无需手动安装）

本地构建：

```bash
./gradlew assembleMobileRelease assembleLeanbackRelease
# 产物：app/build/outputs/apk/{mobile,leanback}/release/*.apk
```

## 持续集成（GitHub Actions）

流水线见 [`.github/workflows/build.yml`](.github/workflows/build.yml)，产物按 ABI 拆分，共 4 个 APK：

| 设备 | ABI | 文件名 |
|---|---|---|
| 手机 / 平板 | arm64-v8a | `GoldenTVPlayer-v<版本>-mobile-arm64_v8a.apk` |
| 手机 / 平板 | armeabi-v7a | `GoldenTVPlayer-v<版本>-mobile-armeabi_v7a.apk` |
| Android TV | arm64-v8a | `GoldenTVPlayer-v<版本>-leanback-arm64_v8a.apk` |
| Android TV | armeabi-v7a | `GoldenTVPlayer-v<版本>-leanback-armeabi_v7a.apk` |

触发方式：

- **push 到 `main`**：编译并将 APK 上传为 workflow artifact（保留 30 天，不发布 Release）
- **推送 `v*` 标签**：编译并创建 GitHub Release，附上 4 个 APK
- **Pull Request**：仅编译校验
- **手动触发**：Actions 页面 → Build APK → Run workflow

版本号在构建时从 [`docs/ChangeLog.md`](docs/ChangeLog.md) 最新条目标题解析：`## 2026-10-29 (v1.0.0)` → `versionName=1.0.0`、`versionCode=20261029`。

正式签名需在仓库 **Settings → Secrets and variables → Actions** 配置：

| Secret | 说明 |
|---|---|
| `KEYSTORE_BASE64` | `release.jks` 的 base64（`base64 -w0 release.jks`） |
| `KEY_ALIAS` | keystore 中的 key alias |
| `KEYSTORE_PASSWORD` | keystore 与 key 的密码 |

未配置时流水线仍会跑通，但产出文件名带 `-unsigned` 后缀，**无法直接安装**（仅用于编译校验）。


## 安装与使用

1. 安装对应设备类型的 APK（电视选 leanback 包，手机/平板选 mobile 包）。
2. 打开应用，进入「设置 → 配置」，填入数据源接口地址（JSON 订阅 / 聚合地址），完成数据源加载。
3. 即可浏览、点播与观看直播。

## 数据源

- 支持多数据源聚合与跨站搜索。可以在下方地址获取聚合资源：
  - https://b.zhuiju.me/p/3
  - https://github.com/laoma528/awesome-zhuiju-free
  - https://github.com/KooKang/TVBox18/tree/main
- 脚本源（type=3 Spider）已在配置加载阶段禁用（v1.0.0 变更）。
- 运行时插件目录默认空，需在构建时注入。



## 项目结构

```
Golden-TVPlayer/
├── app/            # 主应用（leanback / mobile 两个 flavor）
├── catvod/         # 数据源解析与爬虫核心（Spider / 运行时）
├── tvbus/          # 直播源解析
├── quickjs/        # QuickJS 引擎封装
├── chaquo/         # Python 运行时集成
├── nodejs/         # Node.js 运行时（脚本源，默认禁用）
├── buildSrc/       # 构建逻辑（ABI 拆分等）
├── docs/           # 对外文档（Features.md / ChangeLog.md）
└── .github/        # CI：build.yml（APK 构建/发布）/ pages.yml（官网）
```


## 贡献

欢迎提交 Issue 与 Pull Request。涉及功能或行为变更时，请同步更新 `docs/` 下的对外文档（[Features.md](docs/Features.md) / [ChangeLog.md](docs/ChangeLog.md)），保持发布文档与代码一致。

## 安全

v1.0.0 起默认收紧安全基线：TLS 严格校验、本地服务鉴权（配对码）、默认禁用脚本源与自更新。发现安全漏洞请通过 GitHub Issue 私下反馈，请勿公开讨论尚未修复的细节。

## 许可证

[GNU GPL v3](LICENSE.md) — 本应用为 [FongMi/TV](https://github.com/FongMi/TV) 的分叉与改造。

## 作者

[yc7en](https://github.com/yc7en)
