<div align="center">

# 月見 Tsukimi

![Platform](https://img.shields.io/badge/platform-Android-lightgrey)
![License](https://img.shields.io/badge/license-GPLv3-blue)
[![Download](https://img.shields.io/github/downloads/menagev001/tsukimi/total?label=download)](https://github.com/menagev001/tsukimi/releases)
[![Latest download](https://img.shields.io/github/downloads/menagev001/tsukimi/latest/total?label=latest%20download)](https://github.com/menagev001/tsukimi/releases/latest)

[English](README.md) | **简体中文**

Tsukimi 是一款 Android 日语沉浸学习 App：阅读 EPUB 书籍和漫画，使用 Yomitan 辞典查词，让 AI 解释单词和句子，并把阅读内容制成 Anki 卡片。带可点击字幕的视频观看功能正在规划中。

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

### 书架

- 单本、批量或按文件夹递归导入 EPUB，并在书架查看阅读进度。
- 使用自定义书架整理书籍。
- 导出 EPUB，或把远端同步的书籍拉回本地书库。

### 阅读

- 以竖排或横排阅读日文书籍，并在分页和连续滚动之间切换。
- 自定义主题、字体、段落间距和阅读器控件，并支持配置自定义阅读主题。
- 支持沉浸式专注模式、音量键翻页和墨水屏显示选项。
- 全屏查看阅读器图片，支持缩放、复制、保存和分享。

### 查词

- 导入、下载、更新和管理 Yomitan 辞典。
- 在阅读器中点击文本查词，在辞典页搜索，或从其他 Android App 选中文本查词。
- 点击释义中的生词进行递归查询。
- 注入自定义 CSS 样式。
- 使用在线或本地单词音频。

### 标注与统计

- 阅读时添加五色高亮标注，并随时进行跳转。
- 阅读数据统计，包括阅读的字符数、时长、阅读速度，可在阅读时实时显示。

### AI 查词

- 让 AI（Claude）结合句子解释查到的单词，或双击文本获得句子解析。两种提示词都可以在 AI 设置中编辑，AI 服务繁忙时会自动重试。
- 将高亮及其句子上下文导出为 JSON，供 Migaku 使用。

### 漫画

- 阅读带有 [mokuro](https://github.com/kha-white/mokuro) OCR 数据的 CBZ 卷：点击对话气泡显示其文字，再点击单词查词；双击气泡可获得句子解析。
- 把卷归入系列，自动或手动裁剪扫描页边距，并统计漫画阅读数据。

### Anki 制卡

- 通过 AnkiDroid 或 AnkiConnect 制卡。
- 使用 [Lapis](https://github.com/donkuri/lapis) 兼容字段，支持重复检查和媒体导出。

### 有声书跟读

- 将有声书字幕文件匹配到书籍以高亮当前句子。
- 跟随高亮自动翻页。
- 控制播放速度、跳转动作和 Android 系统媒体控制。

### 数据同步与迁移

- 通过 Google Drive 同步阅读进度、统计和书籍，兼容 ッツ Reader。
- 导入或导出 ッツ bookdata，并使用 `.hoshi` 归档备份或恢复书籍和辞典，兼容 Hoshi Reader iOS。

## 下载

请从 [GitHub Releases](https://github.com/menagev001/tsukimi/releases/latest) 下载最新的 Tsukimi APK。

Tsukimi 需要 Android 8.0 或更高版本。

## 开发状态

Tsukimi 主要为作者个人在墨水屏 Android 平板上的使用而开发，按现状分享。已发布的用户可见变化见 [docs/CHANGELOG.md](docs/CHANGELOG.md)。

欢迎通过 [Issue](https://github.com/menagev001/tsukimi/issues) 反馈问题和提出功能请求。

## 隐私与数据

Tsukimi 会把导入的书籍、辞典、字体、有声书数据、阅读进度、高亮、统计和设置保存在 App 本地存储中。

Google Drive 同步使用由用户配置的 Google Cloud OAuth 设备代码流程。Anki 制卡会与 AnkiDroid 或已配置的 AnkiConnect 地址通信。更新检查会读取 GitHub Release 元数据。

## 基于

Tsukimi 是 HuangAntimony 的 [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android) 的修改版，后者是 Manhhao 的 [Hoshi Reader](https://github.com/Manhhao/Hoshi-Reader) 的 Android 原生复刻版。两者均采用 GPL-3.0 许可，Tsukimi 同样如此。代码在 Hoshi Reader Android v1.3.3 之后不久分叉，此后不再与上游同步。

Tsukimi 新增：AI 单词和句子查询、Migaku JSON 导出、漫画支持（CBZ 与 mokuro），以及 Tsukimi 名称和发布渠道。

它建立在以下生态之上：

- [hoshidicts](https://github.com/Manhhao/hoshidicts) 及 [hoshidicts-kotlin-bridge](https://github.com/HuangAntimony/hoshidicts-kotlin-bridge)：Yomitan 辞典支持。
- [Yomitan](https://github.com/yomidevs/yomitan)：辞典格式和查词体验参考。
- [AnkiDroid](https://github.com/ankidroid/Anki-Android)：Android 制卡集成。
- [Ankiconnect Android](https://github.com/KamWithK/AnkiconnectAndroid)：本地音频行为和 AnkiDroid 重复范围 / checksum 查询参考。
- [ッツ Ebook Reader](https://github.com/ttu-ttu/ebook-reader)：阅读器、统计和同步兼容性参考。
- [mokuro](https://github.com/kha-white/mokuro)：漫画 OCR 数据。

## 许可证

本项目基于 GNU General Public License v3.0 发布。详情见 [LICENSE](LICENSE)。
