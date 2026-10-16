# KnockLauncher · 指关节敲击启动器

用**指关节敲击屏幕**触发动作的 Android 小工具。附带一个副产品：**瞬截**——打开即截图。

> 🤖 本项目由 **[DeepSeek](https://www.deepseek.com/)** 生成（经由 OpenClaw 智能体）。

![license](https://img.shields.io/badge/license-GPL--3.0-blue)
![platform](https://img.shields.io/badge/platform-Android%208.0%2B-green)
![root](https://img.shields.io/badge/root-required-orange)

> 开发环境：Redmi K90 Pro Max (myron) + PixelOS (Android 17) + KernelSU
> 原理通用，换机型只需改触摸设备节点。

---

## 目录

- [它做什么](#它做什么)
- [工作原理](#工作原理)
- [环境要求](#环境要求)
- [构建](#构建)
- [使用](#使用)
- [常见问题](#常见问题)
- [项目结构](#项目结构)
- [已知限制](#已知限制)
- [License](#license)

---

## 它做什么

### KnockLauncher

- **指关节敲击屏幕** → 弹出选择器：列出所有 App，或所有 Activity（组件）
- **长按任意一项 = 设为默认** → 之后敲一下**直接打开**，不再弹选择器
- 首页有**实时信号面板**，能直接看到三路信号有没有越过阈值
- 内置**样本录制**，用来按你自己的手感标定阈值

### 瞬截 (QuickShot)

- 打开即截屏，存到 `/sdcard/Pictures/Screenshots/`
- 是**全透明 Activity**，所以截到的是「打开它之前那一屏」，它自己不会出现在图里

---

## 工作原理

三路信号在时间窗内**同时命中**，才判定为指关节敲击：

| 信号 | 采集方式 |
|---|---|
| 加速度计 | `SensorManager`，取 `\|a\|-g\| ` 的峰值 |
| 麦克风 | `AudioRecord` 48kHz 单声道，1ms 一跳的 RMS 包络 |
| 触摸事件流 | root 读 `/dev/input/event*`，统计单次触控内的事件条数 |

**为什么不用「接触面积」？** 因为实测这台机器的 focaltech 驱动**不上报** `ABS_MT_TOUCH_MAJOR` / `ABS_MT_PRESSURE`。

**实测分布、阈值、踩坑记录 → [docs/notes.md](docs/notes.md)**

---

## 环境要求

- **Android 8.0+**（minSdk 26）
- **Root**（KernelSU 或 Magisk）—— 不 root 拿不到全局触摸数据
- 触摸设备节点：默认写死 `/dev/input/event7`，换机型请改 `KnockService.DEV`

**权限：** root / 麦克风 / 悬浮窗（后台弹窗用）/ 通知（前台服务常驻）

---

## 构建

**不需要 Gradle**，直接用 SDK 自带的工具链出包。

前置：**JDK 17** + **Android SDK**（build-tools、platforms/android-34 或更新）

```powershell
.\build.ps1 -App knocklauncher
.\build.ps1 -App quickshot
```

脚本会自动探测 SDK、生成调试签名（首次）、编译 → dex → 打包 → 对齐 → 签名。
产物：`<app>\<app>.apk`

安装：

```bash
adb install -r knocklauncher\knocklauncher.apk
```

### 构建踩过的坑（写在前面）

- `javac` 必须显式 `-encoding UTF-8`，否则中文串在 GBK 环境下报错
- **不能用 lambda**（plain javac 下 `android.jar` 缺 `LambdaMetafactory`），用匿名内部类
- `d8` 的输入别给 `*.class` 通配符，用 `Get-ChildItem` 展开成文件列表
- 签名别给 `--key-pass`（PKCS12 同一个口令），建议 `--ks-pass file:kspass.txt`
- **改了签名必须卸载重装**，且 KernelSU 授权会随之失效

---

## 使用

### KnockLauncher

1. 打开 App，**允许麦克风 / 通知**
2. 点 **「授权：悬浮窗」** —— 不授权的话后台弹不出选择器
3. 点 **「开启服务」** —— 通知栏会出现常驻通知
4. 第一次会弹 **root 请求**（要读触摸）→ 允许并记住
5. **用指关节敲一下屏幕** → 弹出选择器

**想直接打开某个目标：** 在选择器里**长按**它 → 设为默认。之后敲一下直达。
（首页有「清除默认目标」可以撤销）

**觉得灵敏度不合适：** 点「录样本（调参数）」，分别录几段「指关节敲」和「手指用力按」，用真实数据重新标定。

### 瞬截

授权 root 后，把它放到桌面/手势上，点一下即截图。

---

## 常见问题

**Q：一定要 root 吗？**
是。App 只能收到自己窗口内的触摸事件，安卓没有开放「全局触摸」给普通应用。

**Q：为什么我的指关节敲不触发？**
阈值是按开发者的手感标定的。用「录样本」按你自己的力度标定。

**Q：会不会误触发？**
三路信号同时命中才触发，实测手指点击不会误报。但「很用力按压」有可能越线，可以调高阈值。

**Q：听歌/看视频时会不会失效？**
会。麦克风同一时刻只能被一个采集方占用，判定会变差。

**Q：换了签名重装后，为什么服务读不到触摸了？**
KernelSU 的 root 授权跟包绑定，重装要重新授权。

**Q：选择器里搜不到东西？**
搜索框支持应用名 / 包名 / 类名。Activity 模式下条目很多，列表默认只渲染前 80 条，**输入关键词筛选**。

---

## 项目结构

```
.
├─ build.ps1                      一键构建（无 Gradle）
├─ docs/notes.md                  技术笔记：实测数据与踩坑
├─ knocklauncher/
│   ├─ AndroidManifest.xml
│   └─ src/com/example/knockcollector/
│       ├─ MainActivity.java      首页：服务开关 / 实时信号 / 授权
│       ├─ KnockService.java      核心：三路信号识别引擎
│       ├─ PickerActivity.java    选择器：应用 / Activity 双模式
│       ├─ RecorderActivity.java  样本录制（阈值标定）
│       └─ LaunchUtil.java        启动 + root 兜底
└─ quickshot/
    ├─ AndroidManifest.xml
    └─ src/com/example/quickshot/MainActivity.java
```

---

## 已知限制

- 必须 root
- 触摸设备节点写死（换机型要改）
- 麦克风被占用时判定变差
- 阈值需要按人标定
- 部分 Activity 需要启动参数，直接开会白屏/闪退（这是它自身的要求）

---

## License

**GNU General Public License v3.0 (GPL-3.0)** — Copyright (C) 2026 Typeun

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but **WITHOUT ANY
WARRANTY**; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.

完整文本见 [LICENSE](LICENSE)。

---

## 生成说明

**本项目的全部内容——代码、构建脚本、技术文档、本 README——由 [DeepSeek](https://www.deepseek.com/) 生成**（经由 OpenClaw 智能体）。

- 未使用任何模板、脚手架或现成工程，所有文件从零生成
- 文中实测数据、失败记录与踩坑结论，来自生成过程中在真实设备上的验证（见 [docs/notes.md](docs/notes.md)）
