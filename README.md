# KnockLauncher · 指关节敲击启动器

用**指关节敲击屏幕**触发动作的 Android 小工具（需要 root）。
同时附带一个副产品：**瞬截**——打开即截图。

> 目标机型：Redmi K90 Pro Max (myron) + PixelOS (Android 17) + KernelSU
> 但原理通用，别的机型只要改触摸设备节点即可。

---

## 它做什么

**KnockLauncher**
- 常驻前台服务，监听三路信号：**加速度计 + 麦克风 + 触摸事件流**
- 三路在时间窗内同时命中 → 判定为「指关节敲击」
- 命中后：弹出选择器（可选「应用」或「Activity」），或直达你设定的默认目标
- 长按选择器里的条目 = 设为默认（之后敲一下直接打开）

**瞬截 (QuickShot)**
- 打开即截屏（全透明 Activity，所以截到的是「打开它之前那一屏」）
- 存到 `/sdcard/Pictures/Screenshots/`

---

## 为什么不用「接触面积」？

实测发现：**focaltech 触摸驱动不上报 `ABS_MT_TOUCH_MAJOR` / `ABS_MT_PRESSURE`**（能力表里有，但固件一条都不发）。
所以只能退而用**多信号互证**。详见 [docs/notes.md](docs/notes.md)。

---

## 构建（不需要 Gradle）

用 SDK 自带的 `aapt2 / javac / d8 / zipalign / apksigner` 直接出 APK。

```powershell
# 需要：JDK 17、Android SDK（build-tools + platforms/android-34）
.\build.ps1 -App knocklauncher
.\build.ps1 -App quickshot
```

脚本会自动：
1. 探测 SDK 路径（`ANDROID_HOME` 或 `%LOCALAPPDATA%\Android\Sdk`）
2. 首次运行时生成调试签名（`debug.keystore`，口令 `android`）
3. 编译 → dex → 打包 → 对齐 → 签名，输出 `<app>.apk`

### 踩过的坑（写在前面）

- `javac` 必须显式 `-encoding UTF-8`，否则中文串在 GBK 环境下编译报错
- **不能用 lambda**（plain javac 下 `android.jar` 缺 `LambdaMetafactory`），一律用匿名内部类
- `d8` 的输入不要给 `*.class` 通配符（PowerShell 不展开），用 `Get-ChildItem` 展开成文件列表
- 签名别给 `--key-pass`（PKCS12 用同一个口令，给了反而报错）；建议 `--ks-pass file:kspass.txt`
- **改了签名必须卸载重装**，而且 KernelSU 的 root 授权会随之失效

---

## 权限

| 权限 | 用途 |
|---|---|
| root (KernelSU) | 读 `/dev/input/event*` 触摸事件流、调 `screencap` |
| 麦克风 | 敲击声判定（前台服务类型 microphone） |
| 悬浮窗 | 允许后台弹出选择器 |
| 通知 | 前台服务常驻通知 |

---

## 已知限制

- **必须 root**：不 root 拿不到全局触摸数据
- 触摸设备节点写死在 `/dev/input/event7`（换机型请改 `KnockService.DEV`）
- 播放音乐/视频时麦克风被占用，判定会变差
- 灵敏度阈值是**按人标定**的（见 `RecorderActivity`），默认值未必适合你

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
