# NurIME — 百度输入法风格三语输入法

一款参考百度输入法 UI/UX 设计的 Android 输入法 (IME)，使用 Kotlin 自绘 Canvas 引擎实现，支持**维语/中文/英文**三语切换。

## 核心功能

### 键盘引擎
- **自绘 Canvas 键盘** — 不依赖系统 KeyboardView，全部按键通过 Canvas 直接绘制
- **百度同款视觉** — 白色圆角按键、灰色功能键、蓝色回车(#2a7aff)、灰色键盘背景(#d1d5db)
- **按键预览弹出大字** — 按下字母/数字键时弹出白色圆角大字预览，带弹簧动画
- **长按符号滑动选取** — 长按字母键弹出符号条，手指滑动精准选中，松开输入

### 三语模式

| 模式 | 功能 |
|------|------|
| **维语** | 32/26键布局، 动态元音形态切换(初始态hamza前缀→激活态独立形式)، composing实时候选 |
| **中文** | 26键全拼/9键T9/手写布局切换، 模糊拼音(z/zh/c/ch/s/sh/n/l/f/h)، 句子级DP分割، 200+词语字典، TTS语音朗读 |
| **英文** | QWERTY布局، Shift三态切换(⇧普通→⇧临时大写→⇪大写锁定) |

### 高级功能
- **剪贴板粘贴提示** — 复制内容后键盘上方自动显示粘贴条，预览+一键粘贴
- **Emoji面板** — 10分类(最近/笑脸/手势/动物/食物/活动/物品/符号/旗帜/颜文字)، 最近使用持久化
- **符号面板** — 完整符号分类，简易符号栏+完整面板三态切换
- **快捷工具栏** — 粘贴/自定义短语/常用功能
- **候选词栏** — 拼音蓝色显示، 第一个候选词蓝色加粗، 横向滑动，×关闭按钮
- **删除键长按连删** — 200ms触发، 60ms→30ms加速重复
- **空格键长按** — 触发语音输入
- **震动+音效反馈** — 可配置开关

## 项目结构

```
NurIME/
├── app/src/main/java/com/tilpad/ime/
│   ├── TilPadKeyboardView.kt          # 自绘键盘引擎(Canvas渲染+触摸+预览弹出)
│   ├── TilPadInputMethodService.kt    # IME服务(三语切换+候选+面板管理+TTS)
│   ├── PinyinEngine.kt               # 拼音引擎(模糊音+DP分割+词语字典)
│   ├── EmojiPanelView.kt             # Emoji面板(ViewPager2+10分类)
│   ├── SymbolPanelView.kt            # 完整符号面板
│   ├── SimpleSymbolView.kt           # 简易符号栏
│   ├── QuickToolbarView.kt           # 快捷工具栏(粘贴/短语)
│   ├── UyghurDictHelper.kt           # 维语词典
│   ├── ClipboardHelper.kt            # 剪贴板管理
│   ├── GuideActivity.kt              # 启用引导
│   └── SettingsActivity.kt            # 设置(震动/音效/暗黑模式/TTS)
├── app/src/main/res/
│   ├── layout/input_view_tilpad.xml  # 主布局(工具栏+剪贴板条+候选栏+键盘)
│   ├── drawable/                      # 百度风格drawable(圆形按钮/按键背景)
│   └── xml/method.xml                # IME subtype定义
└── README.md
```

## 环境要求

- JDK 17
- Android SDK Platform 34 + Build Tools 34.0.0
- Gradle 8.5
- AGP 8.1.4 / Kotlin 1.9.22
- minSdk 21 / targetSdk 34

## 编译

```bash
cd NurIME
./gradlew assembleDebug
# 输出: app/build/outputs/apk/debug/app-debug.apk
```

## 安装使用

1. 安装 APK
2. 设置 → 系统 → 语言和输入法 → 屏幕键盘 → 启用 **TilPad**
3. 任意文本框长按 → 输入法 → 选择 **TilPad**
4. 键盘顶部按钮切换语言：ئۇ(维语) / 中(中文) / En(英文)

## 百度输入法参考

本项目 UI/UX 参考了百度输入法定制版 8.5.302.766 的视觉设计：
- 按键颜色: 白底#FFFFFF / 功能灰#adb5bd / 回车蓝#2a7aff
- 圆角: 8dp
- 按键间距: 5dp
- 按键高度: 48dp
- 候选栏: 浅蓝灰背景#DDE0E7
- Shift三态: 空心箭头⇧(普通) → 临时大写 → 实心箭头⇪(锁定)
