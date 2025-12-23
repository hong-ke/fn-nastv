# NasTV Android 交互设计规范

## 📋 概述

本文档定义了 NasTV Android TV 应用的完整交互逻辑设计，所有页面和接口与 Web 端 (http://192.168.3.20:13381/v) 保持一致。

### 服务器配置
- **主 API 服务器**: `http://192.168.3.20:13381`
- **弹幕 API 服务器**: `http://192.168.3.20:13401`
- **API 路径前缀**: `/v/api/v1`

### 认证机制
- 使用 `Authorization` 头部传递 Token
- 使用 `authx` 头部传递签名
- 签名算法与 fntv-electron 项目一致

---

## 🔐 1. 登录页面 (LoginActivity)

### 页面功能
- 用户名密码登录
- 服务器地址配置
- 登录状态保持

### 接口调用

#### 1.1 用户登录
```
POST /v/api/v1/login
```

**请求头**:
```
Content-Type: application/json
authx: nonce=123456&timestamp=1703232000000&sign=md5hash
```

**请求体**:
```json
{
    "app_name": "trimemedia-web",
    "username": "duanhongke",
    "password": "Hongkee688.",
    "nonce": "123456"
}
```

**响应**:
```json
{
    "code": 0,
    "msg": "success",
    "data": {
        "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
        "user_name": "duanhongke",
        "expires_in": 604800
    }
}
```

### 交互流程
1. 用户输入用户名和密码
2. 点击登录按钮
3. 调用登录接口
4. 成功后保存 Token 到 SharedPreferences
5. 跳转到首页 (MainActivity)

---

## 🏠 2. 首页 (MainActivity)

### 页面布局
- **左侧导航栏**: 媒体库列表
- **顶部**: 用户信息、退出按钮
- **主内容区**: 
  - 继续观看列表 (横向滚动)
  - 各媒体库预览 (横向滚动卡片)

### 接口调用

#### 2.1 获取媒体库列表
```
GET /v/api/v1/mediadb/list
```

**请求头**:
```
Authorization: {token}
authx: nonce=xxx&timestamp=xxx&sign=xxx
```

**响应**:
```json
{
    "code": 0,
    "msg": "success",
    "data": [
        {
            "guid": "fv_xxx",
            "name": "电影",
            "category": "Movie",
            "poster": "/path/to/poster.jpg"
        },
        {
            "guid": "fv_yyy",
            "name": "电视剧",
            "category": "TV",
            "poster": "/path/to/poster.jpg"
        }
    ]
}
```

#### 2.2 获取媒体库统计
```
GET /v/api/v1/mediadb/sum
```

**响应**:
```json
{
    "code": 0,
    "msg": "success",
    "data": {
        "fv_xxx": 150,
        "fv_yyy": 80
    }
}
```

#### 2.3 获取媒体库内容列表
```
POST /v/api/v1/item/list
```

**请求体**:
```json
{
    "parent_guid": "fv_xxx",
    "exclude_folder": 1,
    "sort_column": "sort_title",
    "sort_type": "ASC",
    "nonce": "123456"
}
```

**响应**:
```json
{
    "code": 0,
    "msg": "success",
    "data": {
        "mdb_name": "电影",
        "mdb_category": "Movie",
        "total": 150,
        "list": [
            {
                "guid": "item_xxx",
                "title": "电影名称",
                "poster": "/path/to/poster.jpg",
                "poster_width": 300,
                "poster_height": 450,
                "vote_average": "8.5",
                "runtime": 120,
                "type": "Movie",
                "watched": 0,
                "watched_ts": 0,
                "is_favorite": 0
            }
        ]
    }
}
```

#### 2.4 获取观看历史 (继续观看)
```
GET /v/api/v1/user/watchhistory?page=1&limit=20
```

**响应**:
```json
{
    "code": 0,
    "msg": "success",
    "data": {
        "total": 10,
        "list": [
            {
                "guid": "item_xxx",
                "title": "视频名称",
                "poster": "/path/to/poster.jpg",
                "watched_ts": 3600,
                "duration": 7200,
                "type": "Episode"
            }
        ]
    }
}
```

### 交互流程
1. 页面加载时并行请求:
   - 媒体库列表
   - 媒体库统计
   - 观看历史
2. 显示继续观看区域 (如有记录)
3. 为每个媒体库创建预览区域
4. 点击媒体库标题 → 显示完整内容
5. 点击媒体项目 → 跳转到详情页

---

## 📺 3. 媒体详情页 (MediaDetailActivity)

### 页面布局
- **顶部**: 海报、标题、评分、简介
- **中部**: 演员列表
- **底部**: 
  - 电影: 播放按钮
  - 剧集: 季选择 + 集列表

### 接口调用

#### 3.1 获取播放信息
```
POST /v/api/v1/play/info
```

**请求体**:
```json
{
    "item_guid": "item_xxx",
    "nonce": "123456"
}
```

**响应**:
```json
{
    "code": 0,
    "msg": "success",
    "data": {
        "guid": "item_xxx",
        "parent_guid": "season_xxx",
        "grand_guid": "tv_xxx",
        "media_guid": "media_xxx",
        "video_guid": "video_xxx",
        "audio_guid": "audio_xxx",
        "subtitle_guid": "no_display",
        "type": "Episode",
        "ts": 1800,
        "play_config": {
            "skip_opening": 90,
            "skip_ending": 120
        },
        "item": {
            "guid": "item_xxx",
            "trim_id": "12345",
            "tv_title": "电视剧名称",
            "parent_title": "第 1 季",
            "title": "第1集",
            "posters": "/path/to/poster.jpg",
            "vote_average": "8.5",
            "runtime": 45,
            "overview": "剧情简介...",
            "is_favorite": 0,
            "is_watched": 0,
            "season_number": 1,
            "episode_number": 1,
            "number_of_seasons": 3,
            "number_of_episodes": 24,
            "duration": 2700
        }
    }
}
```

#### 3.2 获取剧集列表
```
GET /v/api/v1/episode/list/{parentGuid}
```

**响应**:
```json
{
    "code": 0,
    "msg": "success",
    "data": [
        {
            "guid": "ep_001",
            "title": "第1集",
            "episode_number": 1,
            "season_number": 1,
            "runtime": 45,
            "watched": 1,
            "watched_ts": 2700,
            "poster": "/path/to/still.jpg"
        }
    ]
}
```

#### 3.3 获取演员列表
```
GET /v/api/v1/person/list/{itemGuid}
```

**响应**:
```json
{
    "code": 0,
    "msg": "success",
    "data": [
        {
            "name": "演员名称",
            "character": "角色名称",
            "profile_path": "/path/to/photo.jpg"
        }
    ]
}
```

### 交互流程
1. 接收 item_guid 参数
2. 调用 play/info 获取详情
3. 根据 type 判断显示方式:
   - Movie: 显示播放按钮
   - TV/Episode: 显示季/集选择器
4. 点击播放 → 跳转到播放页

---

## 🎬 4. 播放页面 (VideoPlayerActivity)

### 页面布局
- **全屏播放器**: ExoPlayer
- **弹幕层**: DanmakuOverlayView
- **控制栏**: 播放/暂停、进度条、音量、字幕、弹幕设置

### 接口调用

#### 4.1 获取流信息
```
POST /v/api/v1/stream
```

**请求体**:
```json
{
    "header": {
        "User-Agent": ["trim_player"]
    },
    "level": 1,
    "media_guid": "media_xxx",
    "ip": "192.168.3.100",
    "nonce": "123456"
}
```

**响应**:
```json
{
    "code": 0,
    "msg": "success",
    "data": {
        "file_stream": {
            "guid": "file_xxx",
            "path": "/path/to/video.mkv",
            "size": 5368709120
        },
        "video_stream": {
            "guid": "video_xxx",
            "resolution_type": "1080p",
            "codec_name": "hevc",
            "width": 1920,
            "height": 1080
        },
        "audio_streams": [...],
        "subtitle_streams": [...],
        "qualities": [
            {
                "bitrate": 8000000,
                "resolution": "1080p",
                "progressive": true
            }
        ]
    }
}
```

#### 4.2 视频播放地址
```
GET /v/api/v1/media/range/{mediaGuid}
```

**请求头**:
```
Authorization: {token}
authx: nonce=xxx&timestamp=xxx&sign=xxx
Cookie: authorization={token}
Range: bytes=0-
```

#### 4.3 获取弹幕 (弹幕服务器)
```
GET http://192.168.3.20:13401/v/api/v1/danmaku?douban_id={doubanId}&episode={episode}&season={season}
```

**响应** (参考 Apifox 文档):
```json
{
    "1": [
        {
            "border": false,
            "color": "#FFFFFF",
            "mode": 0,
            "other": {
                "create_time": "1669508687"
            },
            "style": {},
            "text": "弹幕内容",
            "time": 0.0
        }
    ]
}
```

#### 4.4 记录播放进度
```
POST /v/api/v1/play/record
```

**请求体**:
```json
{
    "item_guid": "item_xxx",
    "media_guid": "media_xxx",
    "video_guid": "video_xxx",
    "audio_guid": "audio_xxx",
    "subtitle_guid": "no_display",
    "play_link": "",
    "ts": 1800,
    "duration": 2700,
    "nonce": "123456"
}
```

#### 4.5 标记已观看
```
POST /v/api/v1/item/watched
```

**请求体**:
```json
{
    "item_guid": "item_xxx",
    "nonce": "123456"
}
```

### 交互流程
1. 接收播放参数:
   - item_guid, media_guid, video_guid, audio_guid
   - douban_id, season_number, episode_number (弹幕用)
2. 构建播放 URL: `{baseUrl}/v/api/v1/media/range/{mediaGuid}`
3. 配置 ExoPlayer 请求头
4. 加载弹幕数据
5. 开始播放
6. 定时上报播放进度 (每30秒)
7. 播放完成时标记已观看

---

## 🔍 5. 搜索页面 (SearchActivity)

### 接口调用

#### 5.1 搜索
```
POST /v/api/v1/search
```

**请求体**:
```json
{
    "keyword": "搜索关键词",
    "page": 1,
    "limit": 20,
    "nonce": "123456"
}
```

---

## ⭐ 6. 收藏功能

### 接口调用

#### 6.1 获取收藏列表
```
GET /v/api/v1/user/favorites?page=1&limit=20
```

#### 6.2 添加收藏
```
POST /v/api/v1/user/favorite
```

**请求体**:
```json
{
    "item_guid": "item_xxx",
    "nonce": "123456"
}
```

#### 6.3 取消收藏
```
DELETE /v/api/v1/user/favorite/{itemGuid}
```

---

## 🖼️ 7. 图片服务

### 海报图片
```
GET /v/api/v1/sys/img?path={posterPath}&width={width}&height={height}
```

### 字幕下载
```
GET /v/api/v1/subtitle/dl/{subtitleGuid}
```

---

## 🔑 8. 签名算法

### 签名生成流程 (与 fntv-electron 一致)

```java
// 1. 生成随机数和时间戳
String nonce = String.format("%06d", (int)(Math.random() * 900000) + 100000);
String timestamp = String.valueOf(System.currentTimeMillis());

// 2. 计算数据哈希
String dataHash = md5(requestBody);

// 3. 构建签名字符串
String signatureString = API_PREFIX + "_" + path + "_" + nonce + "_" + timestamp + "_" + dataHash + "_" + API_KEY;

// 4. 生成签名
String sign = md5(signatureString);

// 5. 构建 authx 头部
String authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;
```

### 常量
```java
API_PREFIX = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
API_KEY = "16CCEB3D-AB42-077D-36A1-F355324E4237"
```

---

## 📱 9. 页面导航流程图

```
┌─────────────┐
│ SplashActivity │
└──────┬──────┘
       │ 检查登录状态
       ▼
┌─────────────┐     未登录     ┌─────────────┐
│ MainActivity │◄────────────│ LoginActivity │
└──────┬──────┘              └─────────────┘
       │ 点击媒体项目
       ▼
┌─────────────────┐
│ MediaDetailActivity │
└──────┬──────────┘
       │ 点击播放
       ▼
┌─────────────────┐
│ VideoPlayerActivity │
└─────────────────┘
```

---

## 🎯 10. 关键改动点

### 10.1 AppConfig.java
```java
public class AppConfig {
    public static final String SERVER_IP = "192.168.3.20";
    public static final String SERVER_PORT = "13381";  // 主API端口
    public static final String DANMU_PORT = "13401";   // 弹幕API端口
}
```

### 10.2 SharedPreferencesManager.java
- 更新默认服务器地址
- 添加弹幕服务器地址配置

### 10.3 ApiService.java
- 确保所有接口路径以 `/v/api/v1` 开头
- 弹幕接口使用独立的 baseUrl

### 10.4 LoginRequest.java
- 添加 `app_name` 字段: `"trimemedia-web"`

### 10.5 VideoPlayerActivity.java
- 使用 douban_id + season + episode 获取弹幕
- 正确构建播放 URL 和请求头

---

## ✅ 11. 验证清单

- [ ] 登录功能正常
- [ ] 首页媒体库列表加载
- [ ] 继续观看列表显示
- [ ] 媒体详情页正确显示
- [ ] 剧集列表加载
- [ ] 视频播放正常
- [ ] 弹幕加载和显示
- [ ] 播放进度记录
- [ ] 收藏功能
- [ ] 搜索功能
