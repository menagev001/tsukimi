<div align="center">

# 月見 Tsukimi

![Platform](https://img.shields.io/badge/platform-Android-lightgrey)
![License](https://img.shields.io/badge/license-GPLv3-blue)
[![Download](https://img.shields.io/github/downloads/menagev001/tsukimi/total?label=download)](https://github.com/menagev001/tsukimi/releases)
[![Latest download](https://img.shields.io/github/downloads/menagev001/tsukimi/latest/total?label=latest%20download)](https://github.com/menagev001/tsukimi/releases/latest)

[English](README.md) | **简体中文**

Tsukimi 是一款 Android 日语沉浸学习 App：阅读 EPUB 书籍和漫画，通过 Jellyfin 观看视频并点击日语字幕查词，使用 Yomitan 辞典查词，让 AI 解释单词和句子，并在后台把读到和看到的内容制成 Anki 卡片。

Tsukimi 基于 HuangAntimony 的 [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android) 修改而来，后者是 Manhhao 的 [Hoshi Reader](https://github.com/Manhhao/Hoshi-Reader) 的 Android 原生复刻版。继承与新增内容见[基于](#基于)。

<table>
  <tr>
    <td><img src="docs/images/readme/bookshelf.jpg" alt="书架" width="100%"></td>
    <td><img src="docs/images/readme/reader-lookup-popup.jpg" alt="阅读器查词弹窗" width="100%"></td>
    <td><img src="docs/images/readme/reader-dark-theme.jpg" alt="深色阅读器" width="100%"></td>
    <td><img src="docs/images/readme/reader-eink-mode.jpg" alt="墨水屏阅读器" width="100%"></td>
  </tr>
  <tr>
    <td><img src="docs/images/readme/sasayaki-audiobook.jpg" alt="Sasayaki 有声书" width="100%"></td>
    <td><img src="docs/images/readme/reader-statistics.jpg" alt="阅读统计" width="100%"></td>
    <td><img src="docs/images/readme/reader-highlights.jpg" alt="阅读器高亮" width="100%"></td>
    <td><img src="docs/images/readme/dictionary-recursive-lookup.jpg" alt="辞典递归查词" width="100%"></td>
  </tr>
  <tr>
    <td><img src="docs/images/readme/reader-appearance-settings.jpg" alt="阅读器外观设置" width="100%"></td>
    <td><img src="docs/images/readme/dictionary-management.jpg" alt="辞典管理" width="100%"></td>
    <td><img src="docs/images/readme/anki-card-settings.jpg" alt="Anki 制卡设置" width="100%"></td>
    <td><img src="docs/images/readme/sync-settings.jpg" alt="同步设置" width="100%"></td>
  </tr>
</table>

</div>

## 功能

标有 **（Tsukimi）** 的部分是 Tsukimi 新增的功能，其余继承自 Hoshi Reader Android。

### 观看（Tsukimi）

- 登录你自己的 [Jellyfin](https://jellyfin.org) 服务器（版本 12 或更新），浏览继续观看、下一集以及电影和剧集库。
- 全屏播放电影和剧集。设备能解码的文件直接播放，其他格式由 Jellyfin 转码。播放会从上次停下的位置继续，观看进度和已看状态会保存到 Jellyfin。
- 默认选择日语音轨，可为移动数据设置较低的串流画质。
- 日语字幕由 Tsukimi 绘制，有四种字号。点击单词会暂停并查词，双击字幕行获得句子解析，双击视频左侧或右侧后退或前进 10 秒。
- 字幕列表面板（从右边缘向内滑动打开）显示整条字幕轨，跟随播放，点击时间跳转到该行，也能像字幕一样查词。
- 按剧集调整字幕时间：以 0.1 秒为步长（按住可加快），或在列表中长按某一行，把它同步到当前视频时间。
- 视频继续播放的同时制作 Anki 卡片，附带截图、该行音频、作为翻译的英文字幕，以及剧集和时间来源。用「+ 上一行 / + 下一行」合并跨越多条字幕的句子。

### 书架

- 单本、批量或按文件夹递归导入 EPUB，并在书架查看阅读进度。
- 使用自定义书架整理书籍。
- 导出 EPUB，或把远端同步的书籍拉回本地书库。

### 阅读

- 以竖排或横排阅读日文书籍，并在分页和连续滚动之间切换。
- 自定义主题、字体、段落间距和阅读器控件，并支持配置自定义阅读主题。
- 支持沉浸式专注模式、音量键翻页和墨水屏显示选项。
- 全屏查看阅读器图片，支持缩放、复制、保存和分享。
- **（Tsukimi）** 为还不认识的单词添加振假名（根据辞典生成并写入章节），并在 Onyx Boox 设备上减少查词弹窗关闭后的墨水屏残影。

### 漫画（Tsukimi）

- 在独立的漫画标签页阅读带有 [mokuro](https://github.com/kha-white/mokuro) OCR 数据的 CBZ 卷：点击对话气泡显示其文字，再点击单词查词；双击气泡可获得句子解析。
- 把卷归入系列，自动或手动裁剪扫描页边距，并统计漫画阅读数据。
- 漫画卡片以气泡周围的画面作为图片，并用黄色标出所挖的句子。

### 查词

- 导入、下载、更新和管理 Yomitan 辞典。
- 在阅读器中点击文本查词，在辞典页搜索，或从其他 Android App 选中文本查词。
- 点击释义中的生词进行递归查询。
- 注入自定义 CSS 样式。
- 使用在线或本地单词音频。
- **（Tsukimi）** 用音量键在查词弹窗的词条间切换，通过 `tsukimi://search?text=...` 链接打开查词，并把单词及其句子导出为 JSON。

### AI 查词（Tsukimi）

- 让 AI（Claude，使用你自己的 API 密钥）结合句子解释查到的单词，或双击文本获得句子解析。在观看中，会把前后几行字幕作为上下文一起发送。
- 所有提示词都可以在 AI 设置中编辑，AI 服务繁忙时会自动重试。

### 已掌握词汇（Tsukimi）

- 维护一份已掌握词汇列表：导入词表（JSON 或 CSV），在查词弹窗中标记为已掌握，并在每次打开 Tsukimi 时自动加入 Anki 中已熟练卡片（间隔 21 天或以上）的词。
- 书中哪些词显示振假名由这份列表决定。

### 标注与统计

- 阅读时添加五色高亮标注，并随时进行跳转。
- 阅读数据统计，包括阅读的字符数、时长、阅读速度，可在阅读时实时显示。

### Anki 制卡

- 通过 AnkiDroid 或 AnkiConnect 制卡。
- 使用 [Lapis](https://github.com/donkuri/lapis) 兼容字段，支持重复检查和媒体导出。
- **（Tsukimi）** 书籍、漫画和视频的卡片在后台添加并显示进度，阅读和播放不会中断。三者共用一种卡片格式，新增来源、句子翻译、截图、句子音频、句子振假名和 AI 回答等字段；一次 Fetch 即可映射所有 Lapis 字段。对应的 Lapis 模板修改见 [docs/ANKI_LAPIS_SETUP.md](docs/ANKI_LAPIS_SETUP.md)。
- **（Tsukimi）** 书籍、漫画和视频的卡片可分别加入各自的牌组，并按来源和标题加标签。
- **（Tsukimi）** 句子较长时，可以保留原句或让 AI 为该词写一个简短例句（长按 Anki 按钮可对任意句子做此选择）。AI 字段生成失败的卡片可以稍后在 Anki 设置中补全。

### 有声书跟读

- 将有声书字幕文件匹配到书籍以高亮当前句子。
- 跟随高亮自动翻页。
- 控制播放速度、跳转动作和 Android 系统媒体控制。

### 数据同步与迁移

- 通过 Google Drive 同步阅读进度、统计和书籍，兼容 ッツ Reader。
- 导入或导出 ッツ bookdata，并使用 `.hoshi` 归档备份或恢复书籍和辞典，兼容 Hoshi Reader iOS。
- **（Tsukimi）** 还可以备份漫画，以及包含配置文件、字体和已掌握词汇在内的全部设置，方便把 Tsukimi 迁移到另一台设备。从 Hoshi Reader 迁移？见 [docs/MIGRATING_FROM_HOSHI_READER.md](docs/MIGRATING_FROM_HOSHI_READER.md)。

## 下载

请从 [GitHub Releases](https://github.com/menagev001/tsukimi/releases/latest) 下载最新的 Tsukimi APK。

Tsukimi 需要 Android 8.0 或更高版本。

## 开发状态

Tsukimi 主要为作者个人在 Onyx Boox 墨水屏阅读器和 Android 平板上的使用而开发，按现状分享。已发布的用户可见变化见 [docs/CHANGELOG.md](docs/CHANGELOG.md)。

欢迎通过 [Issue](https://github.com/menagev001/tsukimi/issues) 反馈问题和提出功能请求。

## 隐私与数据

Tsukimi 会把导入的书籍、漫画、辞典、字体、有声书数据、阅读进度、高亮、统计、已掌握词汇和设置保存在 App 本地存储中。

Google Drive 同步使用由用户配置的 Google Cloud OAuth 设备代码流程。Anki 制卡会与 AnkiDroid 或已配置的 AnkiConnect 地址通信。观看只与你登录的 Jellyfin 服务器通信，只保存会话令牌，从不保存密码。AI 查词仅在你请求时，用你自己的密钥把单词、句子和前后几行发送到 Anthropic API。更新检查会读取 GitHub Release 元数据。

## 基于

Tsukimi 是 HuangAntimony 的 [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android) 的修改版，后者是 Manhhao 的 [Hoshi Reader](https://github.com/Manhhao/Hoshi-Reader) 的 Android 原生复刻版。两者均采用 GPL-3.0 许可，Tsukimi 同样如此。代码在 Hoshi Reader Android v1.3.3 之后不久分叉，此后不再与上游同步。

Tsukimi 新增：通过 Jellyfin 观看视频并点击字幕查词、漫画支持（CBZ 与 mokuro）、AI 单词和句子查询、书籍漫画和视频的后台 Anki 制卡、已掌握词汇与自动振假名、更多备份，以及 Tsukimi 名称和发布渠道。

它建立在以下生态之上：

- [hoshidicts](https://github.com/Manhhao/hoshidicts) 及 [hoshidicts-kotlin-bridge](https://github.com/HuangAntimony/hoshidicts-kotlin-bridge)：Yomitan 辞典支持。
- [Yomitan](https://github.com/yomidevs/yomitan)：辞典格式和查词体验参考。
- [AnkiDroid](https://github.com/ankidroid/Anki-Android)：Android 制卡集成。
- [Ankiconnect Android](https://github.com/KamWithK/AnkiconnectAndroid)：本地音频行为和 AnkiDroid 重复范围 / checksum 查询参考。
- [ッツ Ebook Reader](https://github.com/ttu-ttu/ebook-reader)：阅读器、统计和同步兼容性参考。
- [mokuro](https://github.com/kha-white/mokuro)：漫画 OCR 数据。
- [Jellyfin](https://jellyfin.org) 及其 [Kotlin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin)：视频库和串流，使用 Android Media3 播放。
- [Zen Antique](https://fonts.google.com/specimen/Zen+Antique)（SIL Open Font License）：App 图标中的「月見」。

## 许可证

本项目基于 GNU General Public License v3.0 发布。详情见 [LICENSE](LICENSE)。
