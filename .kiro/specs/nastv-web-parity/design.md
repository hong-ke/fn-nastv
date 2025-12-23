# Design Document: NasTV Web Parity

## Overview

本设计文档描述了 NasTV Android TV 应用与飞牛影视 Web 端功能对齐的技术实现方案。主要涉及 UI 组件增强、新页面开发、播放器功能扩展和数据模型完善。

## Architecture

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer                                │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │MainActivity│ │DetailPage│ │FavoritePage│ │VideoPlayer │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘   │
├─────────────────────────────────────────────────────────────┤
│                    Manager Layer                             │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐    │
│  │ MediaManager │ │ PlayManager  │ │ ProgressRecorder │    │
│  └──────────────┘ └──────────────┘ └──────────────────┘    │
├─────────────────────────────────────────────────────────────┤
│                    Network Layer                             │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐    │
│  │  ApiService  │ │  ApiClient   │ │ SignatureUtils   │    │
│  └──────────────┘ └──────────────┘ └──────────────────┘    │
├─────────────────────────────────────────────────────────────┤
│                     Model Layer                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │MediaItem │ │StreamInfo│ │PersonInfo│ │PlayRecord│       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
└─────────────────────────────────────────────────────────────┘
```

### 新增/修改组件

1. **UI Components**
   - `RatingView` - 评分显示组件
   - `ProgressCardView` - 带进度的卡片组件
   - `CastListView` - 演职人员列表组件
   - `FileInfoView` - 文件信息展示组件
   - `StreamInfoView` - 视频技术信息展示组件
   - `QualitySelector` - 画质选择器
   - `SpeedController` - 倍速控制器

2. **Activities**
   - `FavoriteActivity` - 收藏页面
   - `CategoryActivity` - 分类列表页面
   - `EpisodeDetailActivity` - 剧集详情页面
   - `MovieDetailActivity` - 电影详情页面（增强）

3. **Utilities**
   - `FormatUtils` - 格式化工具类
   - `ProgressRecorder` - 播放进度记录器

## Components and Interfaces

### 1. FormatUtils - 格式化工具类

```java
public class FormatUtils {
    /**
     * 格式化评分显示
     * @param rating 评分值 (0-10)
     * @return 格式化后的字符串，如 "7.6"
     */
    public static String formatRating(double rating);
    
    /**
     * 计算播放进度百分比
     * @param currentPosition 当前位置（秒）
     * @param duration 总时长（秒）
     * @return 百分比 (0-100)
     */
    public static int calculateProgressPercent(long currentPosition, long duration);
    
    /**
     * 格式化进度文本
     * @param seasonNumber 季数
     * @param episodeNumber 集数
     * @return 格式化后的字符串，如 "第1季·第3集"
     */
    public static String formatProgressText(int seasonNumber, int episodeNumber);
    
    /**
     * 格式化文件大小
     * @param bytes 字节数
     * @return 人类可读格式，如 "596.51 MB"
     */
    public static String formatFileSize(long bytes);
    
    /**
     * 格式化时长
     * @param seconds 秒数
     * @return 人类可读格式，如 "46分钟46秒"
     */
    public static String formatDuration(long seconds);
    
    /**
     * 格式化日期
     * @param timestamp Unix时间戳（秒）
     * @return 本地化日期字符串
     */
    public static String formatDate(long timestamp);
    
    /**
     * 格式化视频信息
     * @param videoStream 视频流信息
     * @return 格式化字符串，如 "1080P H264 8Mbps·10bit SDR"
     */
    public static String formatVideoInfo(VideoStream videoStream);
    
    /**
     * 格式化音频信息
     * @param audioStream 音频流信息
     * @return 格式化字符串，如 "中文 AAC stereo·48kHz"
     */
    public static String formatAudioInfo(AudioStream audioStream);
    
    /**
     * 格式化字幕信息
     * @param subtitleStream 字幕流信息
     * @return 格式化字符串，如 "中文 SRT 外挂"
     */
    public static String formatSubtitleInfo(SubtitleStream subtitleStream);
}
```

### 2. ProgressRecorder - 播放进度记录器

```java
public class ProgressRecorder {
    private static final long RECORD_INTERVAL_MS = 10000; // 10秒
    
    /**
     * 开始记录进度
     * @param itemGuid 项目GUID
     * @param mediaGuid 媒体GUID
     */
    public void startRecording(String itemGuid, String mediaGuid);
    
    /**
     * 更新当前进度
     * @param position 当前位置（毫秒）
     * @param duration 总时长（毫秒）
     */
    public void updateProgress(long position, long duration);
    
    /**
     * 立即保存进度
     */
    public void saveImmediately();
    
    /**
     * 停止记录
     */
    public void stopRecording();
}
```

### 3. PersonInfo - 演职人员数据模型

```java
public class PersonInfo {
    private String guid;
    private String name;
    private String role;        // 角色名
    private String job;         // 职位：Director/Actor/Writer
    private String profilePath; // 头像路径
    private int order;          // 排序
}
```

### 4. StreamListResponse - 流信息响应模型

```java
public class StreamListResponse {
    private List<FileInfo> files;
    private List<VideoStream> videoStreams;
    private List<AudioStream> audioStreams;
    private List<SubtitleStream> subtitleStreams;
}

public class FileInfo {
    private String guid;
    private String path;
    private long size;
    private long fileBirthTime;
    private long createTime;
}

public class VideoStream {
    private String guid;
    private String mediaGuid;
    private String resolutionType;  // 1080P/4K
    private String colorRangeType;  // SDR/HDR
    private String codecName;       // H264/H265
    private int bps;                // 码率
    private int bitDepth;           // 8/10
    private int width;
    private int height;
}

public class AudioStream {
    private String guid;
    private String mediaGuid;
    private String language;
    private String codecName;       // AAC/DTS
    private String channelLayout;   // stereo/5.1
    private String sampleRate;      // 48000
}

public class SubtitleStream {
    private String guid;
    private String mediaGuid;
    private String language;
    private String format;          // SRT/ASS
    private int isExternal;         // 0/1
}
```

### 5. API 接口扩展

```java
// ApiService.java 新增接口

// 收藏列表
@GET("/v/api/v1/favorite/list")
Call<BaseResponse<FavoriteListResponse>> getFavoriteList(
    @Header("Authorization") String token,
    @Header("authx") String signature,
    @Query("type") String type,  // all/movie/tv
    @Query("page") int page,
    @Query("page_size") int pageSize
);

// 添加收藏
@POST("/v/api/v1/favorite/add")
Call<BaseResponse<Object>> addFavorite(
    @Header("Authorization") String token,
    @Header("authx") String signature,
    @Body FavoriteRequest request
);

// 取消收藏
@POST("/v/api/v1/favorite/remove")
Call<BaseResponse<Object>> removeFavorite(
    @Header("Authorization") String token,
    @Header("authx") String signature,
    @Body FavoriteRequest request
);

// 演职人员列表
@GET("/v/api/v1/person/list/{itemGuid}")
Call<BaseResponse<List<PersonInfo>>> getPersonList(
    @Header("Authorization") String token,
    @Header("authx") String signature,
    @Path("itemGuid") String itemGuid
);

// 流信息列表
@GET("/v/api/v1/stream/list/{itemGuid}")
Call<StreamListResponse> getStreamList(
    @Header("Authorization") String token,
    @Header("authx") String signature,
    @Path("itemGuid") String itemGuid
);

// 播放进度记录
@POST("/v/api/v1/play/record")
Call<BaseResponse<Object>> recordPlayProgress(
    @Header("Authorization") String token,
    @Header("authx") String signature,
    @Body PlayRecordRequest request
);
```

## Data Models

### PlayRecordRequest

```java
public class PlayRecordRequest {
    private String item_guid;
    private String media_guid;
    private String video_guid;
    private String audio_guid;
    private String subtitle_guid;
    private long ts;           // 当前位置（秒）
    private long duration;     // 总时长（秒）
}
```

### FavoriteListResponse

```java
public class FavoriteListResponse {
    private int total;
    private List<MediaItem> list;
}
```

### Quality Options

```java
public class QualityOption {
    private String resolution;  // "原画"/"1080P"/"720P"
    private int bitrate;
    private boolean progressive;
    private boolean isM3u8;
}
```

### Speed Options

```java
public class SpeedOption {
    public static final float[] SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    public static final String[] LABELS = {"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"};
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Rating Formatting

*For any* rating value between 0 and 10, the formatRating function SHALL return a string with exactly one decimal place.

**Validates: Requirements 1.2**

### Property 2: Progress Percentage Calculation

*For any* currentPosition and duration where duration > 0, the calculateProgressPercent function SHALL return a value between 0 and 100, calculated as (currentPosition / duration) * 100.

**Validates: Requirements 2.3**

### Property 3: Progress Text Formatting

*For any* seasonNumber and episodeNumber, the formatProgressText function SHALL return a string in the format "第{seasonNumber}季·第{episodeNumber}集".

**Validates: Requirements 2.1**

### Property 4: List Count Accuracy

*For any* list of items displayed, the shown count SHALL equal the actual number of items in the list.

**Validates: Requirements 3.3, 4.3**

### Property 5: Item Filtering by Type

*For any* list of items and a filter type, the filtered result SHALL contain only items matching that type, and all matching items SHALL be included.

**Validates: Requirements 4.1**

### Property 6: Pagination Logic

*For any* list of items with page_size and page_number, the returned items SHALL be the correct subset starting at index (page_number - 1) * page_size with at most page_size items.

**Validates: Requirements 4.4**

### Property 7: Metadata Formatting

*For any* list of genres or regions, the formatting function SHALL join them with appropriate separators and handle empty lists gracefully.

**Validates: Requirements 5.1, 5.2**

### Property 8: Cast Member Grouping

*For any* list of cast members, the grouping function SHALL organize them by job type (导演/演员/编剧) while preserving the order within each group.

**Validates: Requirements 6.2, 6.3**

### Property 9: File Size Formatting

*For any* file size in bytes, the formatFileSize function SHALL return a human-readable string with appropriate unit (B/KB/MB/GB) and two decimal places.

**Validates: Requirements 8.2**

### Property 10: Duration Formatting

*For any* duration in seconds, the formatDuration function SHALL return a human-readable string in the format "X分钟Y秒" or "X小时Y分钟" as appropriate.

**Validates: Requirements 14.3**

### Property 11: Stream Info Formatting

*For any* video/audio/subtitle stream object, the formatting functions SHALL produce a non-empty string containing the key technical details.

**Validates: Requirements 9.1, 9.2, 9.3**

### Property 12: Resume Position Calculation

*For any* saved ts value, when resuming playback, the player SHALL seek to that position (converted to milliseconds).

**Validates: Requirements 13.4**

### Property 13: Current Episode Highlighting

*For any* episode list and current episode guid, the highlighting logic SHALL correctly identify and mark the current episode.

**Validates: Requirements 14.4**

## Error Handling

### Network Errors

1. **API 请求失败**: 显示 Toast 提示，保留当前页面状态
2. **超时处理**: 30秒超时，自动重试一次
3. **认证失败**: 跳转到登录页面

### Data Errors

1. **空数据处理**: 显示"暂无数据"占位符
2. **格式错误**: 使用默认值或隐藏相关 UI 元素
3. **图片加载失败**: 显示默认占位图

### Player Errors

1. **播放失败**: 显示错误信息，提供重试选项
2. **字幕加载失败**: 继续播放，提示字幕不可用
3. **画质切换失败**: 保持当前画质，提示切换失败

## Testing Strategy

### Unit Tests

使用 JUnit 和 Mockito 进行单元测试：

1. **FormatUtils 测试**
   - 测试各种格式化函数的边界情况
   - 测试空值和异常输入处理

2. **数据模型测试**
   - 测试 JSON 解析
   - 测试数据转换逻辑

### Property-Based Tests

使用 jqwik 进行属性测试（最少 100 次迭代）：

1. **格式化函数属性测试**
   - 评分格式化
   - 进度计算
   - 文件大小格式化
   - 时长格式化

2. **过滤和分页属性测试**
   - 类型过滤
   - 分页逻辑

### Integration Tests

1. **API 集成测试**
   - 使用 MockWebServer 模拟 API 响应
   - 测试完整的数据流

2. **UI 集成测试**
   - 使用 Espresso 进行 UI 测试
   - 测试页面导航和交互
