# 字幕功能问题总结

## 当前状态

字幕功能已部分实现，但存在以下限制：

### ✅ 已实现
- 字幕列表获取：`GET /v/api/v1/stream/list/{itemGuid}` 返回 `subtitle_streams`
- 外挂字幕加载：`GET /v/api/v1/subtitle/dl/{subtitleGuid}` 下载字幕文件
- 字幕渲染：使用 ExoPlayer 的 `MergingMediaSource` 合并视频和字幕
- 字幕菜单：支持选择/关闭字幕

### ❌ 未解决：内嵌字幕不显示

**问题描述**：
- 视频有 2 个内嵌字幕（`is_external=0`）：简体、繁体
- 服务器 API `/v/api/v1/subtitle/dl/{guid}` 返回 404，不支持提取内嵌字幕
- 直连模式使用 `ParallelDataSource`，无法解析 MKV 容器中的字幕轨道

**日志证据**：
```
📝 Found 2 subtitle streams
📝 Subtitle 0: 简体 (chi) external=false guid=9f2e2c35f5e84e18870812f5c8306d5d
📝 Subtitle 1: 繁体 (chi) external=false guid=d4472ff3db7a461685cad33c28f786e1
📝 Downloading subtitle from API: /v/api/v1/subtitle/dl/9f2e2c35f5e84e18870812f5c8306d5d
📝 Subtitle download response: 404
```

## iOS 客户端抓包分析 (2025-12-23)

从 HAR 文件分析，iOS 客户端播放同一视频时：

### 关键发现
1. **iOS 客户端在 `play/record` 中传递 `subtitle_guid`**
   ```json
   {
     "subtitle_guid": "9f2e2c35f5e84e18870812f5c8306d5d",
     "play_link": "https://dl-pc-zb.drive.quark.cn/..."
   }
   ```

2. **iOS 客户端没有调用 `/subtitle/dl/` API**
   - 说明内嵌字幕不是通过下载获取的
   - 可能使用服务器端字幕注入或播放器内置解析

3. **iOS 使用夸克网盘直连**
   - 与我们的实现相同
   - 但 iOS AVPlayer 可能能解析 MKV 内嵌字幕

### 对比分析
| 项目 | iOS 客户端 | 我们的实现 |
|------|-----------|-----------|
| 字幕 GUID | 传递给服务器 | 尝试下载 |
| 下载 API | 未调用 | 调用返回 404 |
| 播放器 | AVPlayer (原生 MKV 支持) | ExoPlayer + ParallelDataSource |
| 字幕渲染 | 播放器内置 | 需要外部加载 |

## 技术分析

### 字幕类型
| 类型 | is_external | API下载 | 直连模式 | HLS模式 |
|------|-------------|---------|----------|---------|
| 外挂字幕 | 1 | ✅ 支持 | ✅ 可用 | ✅ 可用 |
| 内嵌字幕 | 0 | ❌ 404 | ❌ 不可用 | ⚠️ 待验证 |

### 根本原因
1. **服务器限制**：`/v/api/v1/subtitle/dl/{guid}` 只支持外挂字幕
2. **直连模式限制**：`ParallelDataSource` 是简单的 HTTP 数据源，不解析容器格式
3. **fntv-electron 行为**：只处理外挂字幕 `streams.filter(stream => stream.is_external)`
4. **iOS 优势**：AVPlayer 原生支持 MKV 容器内嵌字幕解析

## 解决方案

### 方案 A：使用 HLS 转码流（推荐）
- 服务器 `getStream` API 返回 `qualities` 数组，可能包含 HLS 流（`is_m3u8=true`）
- HLS 流由服务器转码，会提取内嵌字幕作为单独轨道
- 需要实现：检测内嵌字幕时自动切换到 HLS 模式

**实现步骤**：
1. 调用 `POST /v/api/v1/stream` 获取 `qualities`
2. 查找 `is_m3u8=true` 的质量选项
3. 使用 HLS URL 替代直连 URL
4. ExoPlayer 原生支持 HLS 字幕轨道选择

### 方案 B：使用标准 ExoPlayer 数据源解析 MKV
- 放弃 `ParallelDataSource`，使用 `DefaultHttpDataSource`
- 配合 `DefaultExtractorsFactory` 让 ExoPlayer 解析 MKV 容器
- 缺点：可能影响下载速度

### 方案 C：服务器端支持提取内嵌字幕
- 修改服务器 API，支持从 MKV 容器提取内嵌字幕
- 需要服务器端开发

## 相关文件
- `VideoPlayerActivity.java` - 字幕加载逻辑
- `StreamListResponse.java` - 字幕数据模型
- `StreamResponse.java` - 包含 HLS 流信息
- `MediaManager.java` - `getStream` API 调用

## 参考
- fntv-electron 字幕处理：`src/modules/fn_api/api.ts` 的 `getSubtitle()` 方法
- ExoPlayer 字幕文档：https://developer.android.com/media/media3/exoplayer/track-selection
- iOS HAR 抓包：`_bmad-output/analysis/Stream-2025-12-23 15_30_01.har`
