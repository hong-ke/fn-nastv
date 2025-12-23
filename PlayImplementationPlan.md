# 🎬 视频播放实现计划 [已完成]

## ✅ 实现方案 (遵循 fntv-electron 架构)

### 核心流程
1. **获取播放信息** (`MediaManager.getPlayInfo`)
   - API: `POST /v/api/v1/play/info`
   - 参数: `item_guid`
   - 返回: 包含 `media_guid` 的 `PlayInfoResponse`

2. **构建播放 URL** (`MediaManager.startPlay`)
   - 模式: Direct Play (直连播放)
   - URL: `/v/api/v1/media/range/{media_guid}`
   - 播放器: `ExoPlayer` (ProgressiveMediaSource)

3. **Danmaku 集成** (`VideoPlayerActivity` + `DanmuRepository`)
   - API: `GET /v/api/v1/danmaku`
   - 参数: `douban_id`, `episode`, `season`
   - 渲染: `DanmuContainer`

## 🛠️ 已完成改动
- [x] 更新 `ApiService`: 添加 `/v/api/v1/` 相关接口 (`getPlayInfo`, `getDanmaku` 等)
- [x] 重构 `MediaManager`: 实现 `startPlay` 流程，移除过时代码
- [x] 优化 `MediaDetailActivity`: 实现剧集选择和正确的播放参数传递
- [x] 更新 `VideoPlayerActivity`: 支持新 API 的弹幕加载和直连播放
- [x] 清理: 移除了未使用的 `SeasonDetailActivity`

## 📝 下一步
- 进行真机测试，验证 `media_guid` 有效性和播放兼容性。
- 如果需要转码播放 (HLS)，后续可扩展 `getStream` 流程。
