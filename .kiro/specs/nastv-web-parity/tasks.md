# Implementation Plan: NasTV Web Parity

## Overview

本实现计划将 NasTV Android TV 应用与飞牛影视 Web 端功能对齐。按照优先级和依赖关系组织任务，确保增量开发和持续可测试。

## Tasks

- [x] 1. 创建 FormatUtils 工具类
  - [x] 1.1 创建 FormatUtils.java 文件，实现基础格式化方法
    - 实现 formatRating(double rating) - 评分格式化
    - 实现 calculateProgressPercent(long currentPosition, long duration) - 进度百分比计算
    - 实现 formatProgressText(int seasonNumber, int episodeNumber) - 进度文本格式化
    - _Requirements: 1.2, 2.1, 2.3_
  - [ ]* 1.2 编写 FormatUtils 属性测试
    - **Property 1: Rating Formatting**
    - **Property 2: Progress Percentage Calculation**
    - **Property 3: Progress Text Formatting**
    - **Validates: Requirements 1.2, 2.1, 2.3**
  - [x] 1.3 实现文件和时长格式化方法
    - 实现 formatFileSize(long bytes) - 文件大小格式化
    - 实现 formatDuration(long seconds) - 时长格式化
    - 实现 formatDate(long timestamp) - 日期格式化
    - _Requirements: 8.2, 8.3, 14.3_
  - [ ]* 1.4 编写文件和时长格式化属性测试
    - **Property 9: File Size Formatting**
    - **Property 10: Duration Formatting**
    - **Validates: Requirements 8.2, 14.3**

- [x] 2. 扩展数据模型
  - [x] 2.1 创建 PersonInfo.java 演职人员数据模型
    - 包含 guid, name, role, job, profilePath, order 字段
    - 实现 JSON 解析
    - _Requirements: 6.2_
  - [x] 2.2 创建 StreamListResponse.java 流信息响应模型
    - 包含 FileInfo, VideoStream, AudioStream, SubtitleStream 内部类
    - 实现 JSON 解析
    - _Requirements: 9.1, 9.2, 9.3_
  - [x] 2.3 创建 PlayRecordRequest.java 播放进度记录请求模型
    - 包含 item_guid, media_guid, video_guid, audio_guid, subtitle_guid, ts, duration 字段
    - _Requirements: 13.1_
  - [x] 2.4 更新 MediaItem.java 添加缺失字段
    - 添加 genres, origin_country, content_rating, imdb_id 字段
    - _Requirements: 5.1, 5.2, 5.3, 15.4_

- [x] 3. Checkpoint - 确保基础组件完成
  - 确保所有测试通过，如有问题请询问用户

- [x] 4. 扩展 ApiService 接口
  - [x] 4.1 添加演职人员列表接口
    - GET /v/api/v1/person/list/{itemGuid}
    - 返回 List<PersonInfo>
    - _Requirements: 6.1_
  - [x] 4.2 添加流信息列表接口
    - GET /v/api/v1/stream/list/{itemGuid}
    - 返回 StreamListResponse
    - _Requirements: 9.4_
  - [x] 4.3 添加收藏列表接口
    - GET /v/api/v1/favorite/list
    - 支持 type, page, page_size 参数
    - _Requirements: 3.1_
  - [x] 4.4 添加播放进度记录接口
    - POST /v/api/v1/play/record
    - 请求体为 PlayRecordRequest
    - _Requirements: 13.1_

- [x] 5. 实现 FormatUtils 流信息格式化
  - [x] 5.1 实现视频/音频/字幕信息格式化方法
    - 实现 formatVideoInfo(VideoStream) - 视频信息格式化
    - 实现 formatAudioInfo(AudioStream) - 音频信息格式化
    - 实现 formatSubtitleInfo(SubtitleStream) - 字幕信息格式化
    - _Requirements: 9.1, 9.2, 9.3_
  - [ ]* 5.2 编写流信息格式化属性测试
    - **Property 11: Stream Info Formatting**
    - **Validates: Requirements 9.1, 9.2, 9.3**

- [x] 6. 更新 MediaManager 添加新方法
  - [x] 6.1 添加获取演职人员列表方法
    - getPersonList(String itemGuid, MediaCallback<List<PersonInfo>> callback)
    - _Requirements: 6.1_
  - [x] 6.2 添加获取流信息列表方法
    - getStreamList(String itemGuid, MediaCallback<StreamListResponse> callback)
    - _Requirements: 9.4_
  - [x] 6.3 添加收藏相关方法
    - getFavoriteList(String type, int page, MediaCallback<FavoriteListResponse> callback)
    - addFavorite(String itemGuid, MediaCallback<Boolean> callback)
    - removeFavorite(String itemGuid, MediaCallback<Boolean> callback)
    - _Requirements: 3.1_

- [x] 7. 创建 ProgressRecorder 播放进度记录器
  - [x] 7.1 实现 ProgressRecorder.java
    - 实现 startRecording(String itemGuid, String mediaGuid)
    - 实现 updateProgress(long position, long duration)
    - 实现 saveImmediately()
    - 实现 stopRecording()
    - 使用 Handler 实现 10 秒定时记录
    - _Requirements: 13.1, 13.2, 13.3_

- [x] 8. Checkpoint - 确保 Manager 层完成
  - 确保所有测试通过，如有问题请询问用户

- [x] 9. 更新 MainActivity 媒体卡片
  - [x] 9.1 修改媒体卡片布局添加评分显示
    - 在海报右上角添加评分 TextView
    - 根据 vote_average 值显示/隐藏
    - _Requirements: 1.1, 1.2, 1.3_
  - [x] 9.2 修改继续观看卡片添加进度显示
    - 添加进度文本 (第X季·第X集)
    - 添加进度条 ProgressBar
    - _Requirements: 2.1, 2.2_

- [x] 10. 更新 SeasonDetailActivity 剧集列表
  - [x] 10.1 增强剧集列表项显示
    - 添加剧集缩略图 (still_path)
    - 添加剧集标题和简介
    - 添加时长显示 (使用 formatDuration)
    - _Requirements: 14.1, 14.2, 14.3_
  - [x] 10.2 添加当前剧集高亮
    - 根据播放进度高亮当前/上次观看的剧集
    - _Requirements: 14.4_
  - [ ]* 10.3 编写剧集高亮属性测试
    - **Property 13: Current Episode Highlighting**
    - **Validates: Requirements 14.4**
  - [x] 10.4 添加演职人员列表区域
    - 调用 getPersonList API
    - 按类型分组显示 (导演/演员/编剧)
    - 水平滚动布局
    - _Requirements: 6.1, 6.2, 6.3, 6.4_
  - [ ]* 10.5 编写演职人员分组属性测试
    - **Property 8: Cast Member Grouping**
    - **Validates: Requirements 6.2, 6.3**

- [x] 11. 更新 MediaDetailActivity 详情页
  - [x] 11.1 添加类型和地区标签显示
    - 解析 genres 字段显示类型标签
    - 解析 origin_country 字段显示地区
    - 显示 content_rating 分级信息
    - _Requirements: 5.1, 5.2, 5.3_
  - [ ]* 11.2 编写元数据格式化属性测试
    - **Property 7: Metadata Formatting**
    - **Validates: Requirements 5.1, 5.2**
  - [x] 11.3 添加评分显示
    - 在详情页显示评分
    - _Requirements: 1.1_

- [x] 12. 创建 EpisodeDetailActivity 剧集详情页
  - [x] 12.1 创建 EpisodeDetailActivity.java 和布局文件
    - 显示剧集标题、简介、时长、播出日期
    - 添加播放按钮
    - _Requirements: 7.1, 7.2_
  - [x] 12.2 添加文件信息区域
    - 调用 getStreamList API 获取文件信息
    - 显示文件路径、大小、创建日期、添加日期
    - _Requirements: 7.3, 8.1_
  - [x] 12.3 添加视频技术信息区域
    - 显示视频信息 (分辨率、编码、码率、位深、HDR)
    - 显示音频信息 (语言、编码、声道、采样率)
    - 显示字幕信息 (语言、格式、是否外挂)
    - _Requirements: 7.4, 9.1, 9.2, 9.3_

- [x] 13. Checkpoint - 确保详情页完成
  - 确保所有测试通过，如有问题请询问用户

- [x] 14. 创建 FavoriteActivity 收藏页面
  - [x] 14.1 创建 FavoriteActivity.java 和布局文件
    - 添加标签切换 (全部/电影/电视节目)
    - 添加总数显示
    - 使用 RecyclerView 显示收藏列表
    - _Requirements: 3.1, 3.2, 3.3_
  - [x] 14.2 实现收藏项点击导航
    - 点击跳转到对应详情页
    - _Requirements: 3.4_
  - [ ]* 14.3 编写列表计数属性测试
    - **Property 4: List Count Accuracy**
    - **Validates: Requirements 3.3, 4.3**

- [x] 15. 创建 CategoryActivity 分类列表页面
  - [x] 15.1 创建 CategoryActivity.java 和布局文件
    - 支持类型筛选 (全部/电视节目/电影/其他)
    - 添加排序选项
    - 添加总数显示
    - _Requirements: 4.1, 4.2, 4.3_
  - [x] 15.2 实现分页加载
    - 使用 RecyclerView 分页加载
    - _Requirements: 4.4_
  - [ ]* 15.3 编写过滤和分页属性测试
    - **Property 5: Item Filtering by Type**
    - **Property 6: Pagination Logic**
    - **Validates: Requirements 4.1, 4.4**

- [x] 16. 更新 VideoPlayerActivity 播放器功能
  - [x] 16.1 集成 ProgressRecorder
    - 在播放开始时调用 startRecording
    - 在播放过程中调用 updateProgress
    - 在暂停/退出时调用 saveImmediately
    - _Requirements: 13.1, 13.2, 13.3_
  - [x] 16.2 实现恢复播放位置
    - 从 PlayInfo 获取 ts 值
    - 播放开始时 seek 到保存的位置
    - _Requirements: 13.4_
  - [ ]* 16.3 编写恢复位置属性测试
    - **Property 12: Resume Position Calculation**
    - **Validates: Requirements 13.4**
  - [x] 16.4 添加字幕选择功能
    - 从 StreamListResponse 获取字幕列表
    - 在设置面板显示字幕选项
    - 实现字幕切换
    - _Requirements: 10.1, 10.2, 10.3, 10.4_
  - [x] 16.5 添加画质选择功能
    - 从 /v/api/v1/stream API 获取画质选项
    - 在设置面板显示画质选项
    - 实现画质切换
    - _Requirements: 11.1, 11.2, 11.3, 11.4_
  - [x] 16.6 添加倍速播放功能
    - 添加倍速选项 (0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x)
    - 在设置面板显示倍速选项
    - 实现倍速切换
    - _Requirements: 12.1, 12.2, 12.3, 12.4_

- [x] 17. 更新导航和入口
  - [x] 17.1 在 MainActivity 添加收藏入口
    - 在侧边栏或顶部添加收藏按钮
    - 点击跳转到 FavoriteActivity
    - _Requirements: 3.1_
  - [x] 17.2 在 MainActivity 添加分类入口
    - 在侧边栏添加分类菜单
    - 点击跳转到 CategoryActivity
    - _Requirements: 4.1_
  - [x] 17.3 更新 SeasonDetailActivity 剧集点击
    - 点击剧集跳转到 EpisodeDetailActivity
    - _Requirements: 7.1_

- [x] 18. 完善电影详情页
  - [x] 18.1 更新 MediaDetailActivity 电影模式
    - 添加演职人员列表
    - 添加文件信息区域
    - 添加视频技术信息区域
    - 添加 IMDB 链接
    - _Requirements: 15.1, 15.2, 15.3, 15.4_
  - [ ]* 18.2 编写电影详情格式化属性测试
    - **Property 17: Movie Detail Formatting** (combined from 15.1)
    - **Validates: Requirements 15.1**

- [x] 19. Final Checkpoint - 确保所有功能完成
  - 确保所有测试通过，如有问题请询问用户
  - 验证所有页面与 Web 端一致

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
- 使用 jqwik 作为属性测试框架
- 测试文件放在 app/src/test/java/com/mynas/nastv/ 目录下
