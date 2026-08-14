# TilPad — 多语言输入法原型

一个 Android 输入法 (IME) 最小可运行原型，使用 Kotlin 编写。核心功能：用户用拉丁字母键盘输入，实时把拉丁维语 (ULY) 转换为阿拉伯字母维语 (UAY) 并上屏，同时支持维/中/英三语键盘切换框架。

## 项目结构

```
TilPad/
├── settings.gradle.kts                          # Gradle 项目配置
├── build.gradle.kts                             # 项目级 build（声明 AGP / Kotlin 版本）
├── gradle.properties                            # Kotlin 代码风格 / AndroidX 等配置
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties            # Gradle 8.5 分发地址
├── README.md                                    # 本文件
└── app/
    ├── build.gradle.kts                         # app 模块 build（minSdk 21 / targetSdk 34）
    ├── proguard-rules.pro                       # ProGuard 规则（空）
    └── src/main/
        ├── AndroidManifest.xml                  # 声明 InputMethodService + BIND_INPUT_METHOD
        ├── java/com/nur/ime/
        │   ├── UyghurConverter.kt               # 拉丁维语→阿拉伯维语 转换引擎
        │   ├── NurKeyboardView.kt               # 自定义键盘视图（继承 KeyboardView）
        │   └── NurInputMethodService.kt         # 输入法服务核心
        └── res/
            ├── xml/
            │   ├── method.xml                   # IME subtype 定义（ug_CN / zh_CN / en_US）
            │   ├── keyboard_uyghur.xml          # 维语键盘布局（拉丁 QWERTY + ö + 功能键）
            │   ├── keyboard_english.xml         # 英文键盘布局（QWERTY + 功能键）
            │   └── keyboard_chinese.xml         # 中文键盘布局（拼音 26 键 + 功能键）
            ├── layout/
            │   ├── keyboard_view.xml            # NurKeyboardView 布局
            │   └── key_preview.xml              # 按键预览弹出布局
            ├── drawable/
            │   └── key_background.xml           # 按键背景 selector
            └── values/
                ├── strings.xml                  # 字符串资源
                └── themes.xml                   # 应用主题
```

## 核心转换逻辑

### UyghurConverter — longest-match 贪心算法

`UyghurConverter.convert(latin: String): String` 的工作流程：

1. **NFC 归一化**：将带附加符号的分解形式（如 `e` + `´`）合并为预组合形式（`é`）。
2. **统一小写**：使用 `Locale.ROOT` 避免 Turkish locale 的 `I→ı` 问题。
3. **贪心遍历**：从左到右扫描，每个位置先尝试匹配 2 字母 digraph（ch/sh/zh/ng/gh），命中则前进 2 位；否则尝试 1 字母，命中则前进 1 位。
4. **未匹配保留**：数字、标点、空格等不在映射表中的字符原样输出。

映射表（硬编码在 `UyghurConverter.kt` 中）：

| 拉丁 | 阿拉伯 | 拉丁 | 阿拉伯 | 拉丁 | 阿拉伯 |
|------|--------|------|--------|------|--------|
| ch   | چ     | a    | ا     | b    | ب     |
| sh   | ش     | e    | ە     | p    | پ     |
| zh   | ژ     | é    | ې     | t    | ت     |
| ng   | ڭ     | o    | و     | j    | ج     |
| gh   | غ     | u    | ۇ     | x    | خ     |
|      |        | ö    | ۆ     | d    | د     |
|      |        | ü    | ۈ     | r    | ر     |
|      |        | i    | ى     | z    | ز     |
|      |        | y    | ي     | s    | س     |
|      |        | w    | ۋ     | f    | ف     |
|      |        | q    | ق     | l    | ل     |
|      |        | k    | ك     | m    | م     |
|      |        | g    | گ     | h    | ھ     |
|      |        | n    | ن     |      |        |

验证用例：

| 输入 (ULY) | 输出 (UAY) | 说明 |
|-----------|-----------|------|
| `salam` | `سالام` | 基本单词 |
| `yaxshimusiz` | `ياخشىمۇسىز` | 含 sh digraph |
| `xosh` | `خوش` | 含 sh digraph |

### 维语模式输入流程

1. 用户按字母键 → 字母追加到 `composingBuffer`
2. `UyghurConverter.convert(composingBuffer)` 转换为阿拉伯文
3. `InputConnection.setComposingText()` 实时预览（带下划线）
4. 用户按空格/回车 → `commitText()` 正式上屏，清空缓冲区
5. 退格 → 删除缓冲区最后一个字符并更新预览

### RTL 处理

上屏前在阿拉伯文本前添加 U+200F（Right-to-Left Mark），确保在 LTR 上下文中也能正确渲染方向。

## 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17（Android Studio 内置）
- Android SDK Platform 34
- Gradle 8.5（项目已配置 wrapper）

## 如何用 Android Studio 打开和编译

### 第一步：打开项目

1. 打开 Android Studio
2. 选择 **File → Open**（或欢迎页的 **Open**）
3. 浏览到 `TilPad/` 目录并选中
4. 点击 **OK**

### 第二步：等待 Gradle 同步

- Android Studio 会自动检测 `gradle-wrapper.properties` 并下载 Gradle 8.5
- 如果提示 SDK 版本不匹配，在 **File → Project Structure → SDK Location** 中确认 compileSdk = 34
- 等待右下角进度条显示 "Gradle sync finished"

> **注意**：如果项目中缺少 `gradle-wrapper.jar`（二进制文件无法通过文本创建），Android Studio 会在首次同步时自动生成它。也可以在项目根目录运行 `gradle wrapper` 命令手动生成。

### 第三步：编译

- 菜单栏选择 **Build → Make Project**（或按 `Ctrl+F9` / `Cmd+F9`）
- 等待底部 Build 面板显示 "BUILD SUCCESSFUL"

## 如何安装到手机

### 方式一：USB 调试安装

1. 手机开启 **开发者选项** 和 **USB 调试**（设置 → 关于手机 → 连续点击"版本号"7 次 → 返回设置 → 系统 → 开发者选项 → USB 调试）
2. 用 USB 线连接手机和电脑
3. 在 Android Studio 顶部的设备下拉框中选择你的手机
4. 点击 **Run** 按钮（绿色三角形，或按 `Shift+F10`）
5. 等待 APK 编译并安装到手机上

### 方式二：手动安装 APK

1. 菜单栏选择 **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. 编译完成后点击通知栏的 **locate** 链接找到 APK 文件
3. 将 APK 传到手机并安装（需开启"允许未知来源应用安装"）

## 如何在系统设置中启用输入法

1. 打开手机 **设置**
2. 进入 **系统 → 语言和输入法 → 屏幕键盘**（不同品牌路径可能不同：小米在"更多设置"中，华为在"系统和更新"中）
3. 找到 **TilPad** 并打开开关
4. 系统会弹出安全提示，点击 **确定**（输入法需要读取你输入的内容，这是所有输入法的标准权限声明）

## 如何设为默认输入法

1. 在 **屏幕键盘** 设置页面中，将默认输入法从系统输入法切换为 **TilPad**
2. 或者在任意文本输入框中长按 → 选择 **输入法** → 选择 **TilPad**

## 如何测试打字

### 测试维语转换

1. 打开任意可输入文本的应用（如备忘录、短信）
2. 确认键盘左下角显示 **Uyghur** 字样（表示当前为维语模式）
3. 依次输入以下拉丁字母并观察实时转换效果：

| 输入 | 预期上屏结果 | 操作 |
|------|-------------|------|
| `salam` | سالام | 输入后按空格键 |
| `yaxshimusiz` | ياخشىمۇسىز | 输入后按空格键 |
| `xosh` | خوش | 输入后按回车键 |

4. 输入过程中可以看到阿拉伯文实时预览（带下划线）
5. 按退格键可以逐个删除未提交的字母

### 测试语言切换

1. 点击键盘上的 **🌐** 键
2. 空格键上的文字会从 "Uyghur" → "中文" → "English" 循环切换
3. 切换到 English 模式后，输入的字母会直接上屏（不转换）
4. 切换到中文模式后，输入的字母也会直接上屏（原型不含拼音词库）

### 测试功能键

| 按键 | 功能 |
|------|------|
| ⌫ (退格) | 删除光标前一个字符（维语模式优先删除 composing 缓冲区） |
| CLR (清空) | 清空当前 composing 缓冲区 |
| ↵ (回车) | 提交 composing 文本并执行回车/完成动作 |
| 🌐 (语言切换) | 维→中→英→维 循环切换键盘 |
| 空格 | 提交 composing 文本并插入空格 |

## 技术说明

- **AGP 版本**：8.1.4
- **Kotlin 版本**：1.9.22
- **Gradle 版本**：8.5
- **minSdk**：21（Android 5.0 Lollipop）
- **targetSdk**：34（Android 14）
- **键盘框架**：使用 `android.inputmethodservice.KeyboardView` + `Keyboard`（API 29 起 deprecated 但仍可正常工作，适合原型阶段）
- **依赖**：androidx.appcompat、androidx.core-ktx、kotlinx-coroutines-android
- **无网络依赖**：映射表全部硬编码在 `UyghurConverter.kt` 中，不需要联网或外部词库文件
