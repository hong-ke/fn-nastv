# Requirements Document

## Introduction

本文档定义了 NasTV Android TV 应用与飞牛影视 Web 端功能对齐的需求规格。目标是让 Android TV 应用的每个页面展示内容与 Web 端完全一致，最大程度还原 Web 端的交互体验。

## Glossary

- **NasTV_App**: Android TV 客户端应用程序
- **Media_Card**: 媒体卡片组件，用于展示海报、评分、标题等信息
- **Continue_Watching_Card**: 继续观看卡片，显示播放进度信息
- **Detail_Page**: 详情页面，包括 TV详情、季详情、剧集详情、电影详情
- **Video_Player**: 视频播放器组件
- **Cast_Section**: 演职人员展示区域
- **File_Info_Section**: 文件信息展示区域
- **Video_Info_Section**: 视频技术信息展示区域（分辨率、编码、码率等）
- **Subtitle_Manager**: 字幕管理功能模块
- **Quality_Selector**: 画质选择器
- **Speed_Controller**: 倍速播放控制器
- **Progress_Recorder**: 播放进度记录器

## Requirements

### Requirement 1: 媒体卡片评分显示

**User Story:** As a user, I want to see the rating on media cards, so that I can quickly assess the quality of content.

#### Acceptance Criteria

1. WHEN a Media_Card is displayed, THE NasTV_App SHALL show the rating value (vote_average) in the top-right corner of the poster
2. WHEN the rating value is greater than 0, THE NasTV_App SHALL display it with one decimal place (e.g., "7.6")
3. WHEN the rating value is 0 or null, THE NasTV_App SHALL hide the rating display

### Requirement 2: 继续观看进度显示

**User Story:** As a user, I want to see my watching progress on continue watching cards, so that I can easily resume where I left off.

#### Acceptance Criteria

1. WHEN a Continue_Watching_Card is displayed, THE NasTV_App SHALL show the progress information (e.g., "第1季·第3集")
2. WHEN a Continue_Watching_Card is displayed, THE NasTV_App SHALL show a progress bar indicating the watched percentage
3. THE NasTV_App SHALL calculate the progress percentage using ts (current position) and duration (total length)

### Requirement 3: 收藏页面功能

**User Story:** As a user, I want to access my favorite content in a dedicated page, so that I can quickly find content I've saved.

#### Acceptance Criteria

1. WHEN the user navigates to the Favorite page, THE NasTV_App SHALL display a list of favorited items
2. WHEN displaying favorite items, THE NasTV_App SHALL show tabs for filtering: 全部 | 电影 | 电视节目
3. WHEN displaying favorite items, THE NasTV_App SHALL show the total count of items
4. WHEN the user clicks on a favorite item, THE NasTV_App SHALL navigate to the corresponding detail page

### Requirement 4: 分类列表页面

**User Story:** As a user, I want to browse content by category, so that I can find specific types of media.

#### Acceptance Criteria

1. WHEN the user navigates to a category page, THE NasTV_App SHALL display items filtered by type (全部/电视节目/电影/其他)
2. WHEN displaying category items, THE NasTV_App SHALL show sorting options (添加日期)
3. WHEN displaying category items, THE NasTV_App SHALL show the total count of items
4. THE NasTV_App SHALL support pagination for large item lists

### Requirement 5: 详情页类型和地区标签

**User Story:** As a user, I want to see genre and region tags on detail pages, so that I can understand the content better.

#### Acceptance Criteria

1. WHEN a Detail_Page is displayed, THE NasTV_App SHALL show genre tags (genres field) as clickable labels
2. WHEN a Detail_Page is displayed, THE NasTV_App SHALL show region/country information (origin_country field)
3. WHEN a Detail_Page is displayed, THE NasTV_App SHALL show the content rating (content_rating field) if available

### Requirement 6: 演职人员列表

**User Story:** As a user, I want to see cast and crew information, so that I can learn about the people involved in the content.

#### Acceptance Criteria

1. WHEN a season detail or movie detail page is displayed, THE NasTV_App SHALL fetch and display the cast list from /v/api/v1/person/list/{item_guid}
2. WHEN displaying cast members, THE NasTV_App SHALL show: avatar image, name, and role (饰演 XXX)
3. WHEN displaying cast members, THE NasTV_App SHALL organize them by type: 导演, 演员, 编剧
4. THE Cast_Section SHALL be horizontally scrollable when there are many cast members

### Requirement 7: 剧集详情页

**User Story:** As a user, I want to see detailed episode information, so that I can make informed viewing decisions.

#### Acceptance Criteria

1. WHEN the user clicks on an episode, THE NasTV_App SHALL display a detailed episode page
2. WHEN displaying episode details, THE NasTV_App SHALL show: episode title, overview, duration, air_date
3. WHEN displaying episode details, THE NasTV_App SHALL show file information section
4. WHEN displaying episode details, THE NasTV_App SHALL show video/audio/subtitle technical information

### Requirement 8: 文件信息展示

**User Story:** As a user, I want to see file information, so that I can understand the source file details.

#### Acceptance Criteria

1. WHEN the File_Info_Section is displayed, THE NasTV_App SHALL show: file path, file size, file creation date, add date
2. THE NasTV_App SHALL format file size in human-readable format (e.g., "596.51 MB")
3. THE NasTV_App SHALL format dates in localized format

### Requirement 9: 视频技术信息展示

**User Story:** As a user, I want to see video technical information, so that I can understand the quality of the media file.

#### Acceptance Criteria

1. WHEN the Video_Info_Section is displayed, THE NasTV_App SHALL show video info: resolution, codec, bitrate, bit depth, HDR type
2. WHEN the Video_Info_Section is displayed, THE NasTV_App SHALL show audio info: language, codec, channels, sample rate
3. WHEN the Video_Info_Section is displayed, THE NasTV_App SHALL show subtitle info: language, format, is_external flag
4. THE NasTV_App SHALL fetch this information from /v/api/v1/stream/list/{item_guid}

### Requirement 10: 字幕管理功能

**User Story:** As a user, I want to manage subtitles, so that I can watch content with my preferred subtitles.

#### Acceptance Criteria

1. WHEN playing a video, THE Video_Player SHALL display current subtitle status
2. WHEN the user opens subtitle settings, THE NasTV_App SHALL show available subtitle tracks
3. WHEN the user selects a subtitle, THE Video_Player SHALL apply the selected subtitle immediately
4. THE NasTV_App SHALL support switching between internal and external subtitles

### Requirement 11: 画质选择功能

**User Story:** As a user, I want to select video quality, so that I can balance between quality and bandwidth.

#### Acceptance Criteria

1. WHEN playing a video, THE Video_Player SHALL provide a quality selection menu
2. WHEN displaying quality options, THE NasTV_App SHALL show available resolutions (原画/1080P/720P等)
3. WHEN the user selects a quality, THE Video_Player SHALL switch to the selected quality
4. THE NasTV_App SHALL fetch quality options from /v/api/v1/stream API

### Requirement 12: 倍速播放功能

**User Story:** As a user, I want to control playback speed, so that I can watch content at my preferred pace.

#### Acceptance Criteria

1. WHEN playing a video, THE Video_Player SHALL provide speed control options
2. THE Video_Player SHALL support speeds: 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x
3. WHEN the user selects a speed, THE Video_Player SHALL apply the speed immediately
4. THE Video_Player SHALL remember the selected speed for the current session

### Requirement 13: 播放进度记录

**User Story:** As a user, I want my playback progress to be saved, so that I can resume watching from where I left off.

#### Acceptance Criteria

1. WHILE playing a video, THE Progress_Recorder SHALL periodically send progress to /v/api/v1/play/record
2. THE Progress_Recorder SHALL send progress at least every 10 seconds during playback
3. WHEN the user pauses or exits playback, THE Progress_Recorder SHALL immediately save the current position
4. WHEN resuming playback, THE Video_Player SHALL start from the saved position (ts field)

### Requirement 14: 季详情页剧集列表增强

**User Story:** As a user, I want to see detailed episode information in the season page, so that I can choose which episode to watch.

#### Acceptance Criteria

1. WHEN displaying the episode list, THE NasTV_App SHALL show episode thumbnail/still image
2. WHEN displaying the episode list, THE NasTV_App SHALL show episode title and overview
3. WHEN displaying the episode list, THE NasTV_App SHALL show episode duration in human-readable format (e.g., "46分钟46秒")
4. THE NasTV_App SHALL highlight the currently playing or last watched episode

### Requirement 15: 电影详情页完善

**User Story:** As a user, I want to see complete movie information, so that I can make informed viewing decisions.

#### Acceptance Criteria

1. WHEN displaying a movie detail page, THE NasTV_App SHALL show: title, rating, year, genres, regions, content_rating
2. WHEN displaying a movie detail page, THE NasTV_App SHALL show the cast and crew section
3. WHEN displaying a movie detail page, THE NasTV_App SHALL show file and video information sections
4. WHEN displaying a movie detail page, THE NasTV_App SHALL show IMDB link if available
