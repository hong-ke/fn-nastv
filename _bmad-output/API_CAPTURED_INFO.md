# NasTV API 捕获信息

## 📅 捕获日期: 2024-12-22

## 🌐 Web端URL结构

### 页面路由
| 页面类型 | URL格式 | 示例 |
|---------|---------|------|
| 首页 | `/v` | `http://192.168.3.20:13381/v` |
| 电视剧详情 | `/v/tv/{tv_guid}` | `/v/tv/0947ca73d69047e48a88eb3908153037` |
| 季详情 | `/v/tv/season/{season_guid}` | `/v/tv/season/418ab356e88043dcad49837cfa1c4b5a` |
| 剧集详情 | `/v/tv/episode/{episode_guid}` | `/v/tv/episode/7e1ea630d8ef4b879fec57eb1f93dcbf` |
| 播放器 | `/v/video/{item_guid}?media_guid={media_guid}` | `/v/video/e69af50770de48419a24a73dcd186dcd?media_guid=bfea99919e15458c824fbe1b83d7a3a2` |

---

## 📡 API接口列表

### 1. 系统配置
```
GET /v/api/v1/sys/config
GET /v/api/v1/sys/version
GET /v/api/v1/server/info
```

### 2. 用户相关
```
POST /v/api/v1/login
GET /v/api/v1/user/info
GET /v/api/v1/play/list          # 继续观看列表
```

### 3. 媒体库
```
GET /v/api/v1/mediadb/list       # 媒体库列表
GET /v/api/v1/mediadb/sum        # 媒体库统计
POST /v/api/v1/item/list         # 获取项目列表
```

### 4. 详情页 (核心)
```
GET /v/api/v1/item/{guid}        # 获取项目详情 (TV/Season/Episode/Movie)
GET /v/api/v1/season/list/{tv_guid}     # 获取季列表
GET /v/api/v1/episode/list/{season_guid} # 获取剧集列表
GET /v/api/v1/person/list/{item_guid}   # 获取演职人员
GET /v/api/v1/stream/list/{item_guid}   # 获取流信息
```

### 5. 播放相关
```
POST /v/api/v1/play/info         # 获取播放信息
POST /v/api/v1/stream            # 获取流详情 (质量选择)
POST /v/api/v1/play/record       # 记录播放进度
GET /v/api/v1/media/range/{media_guid}  # 视频直链
```

---

## 📺 电视剧详情页流程

### 第一层: TV详情页 (`/v/tv/{tv_guid}`)
**API调用顺序:**
1. `GET /v/api/v1/item/{tv_guid}` - 获取电视剧详情
2. `GET /v/api/v1/stream/list/{tv_guid}` - 获取流信息
3. `GET /v/api/v1/season/list/{tv_guid}` - 获取季列表
4. `POST /v/api/v1/play/info` - 获取播放信息

**显示内容:**
- 电视剧标题、海报、评分、年份
- 简介
- 季列表 (点击进入第二层)

### 第二层: 季详情页 (`/v/tv/season/{season_guid}`)
**API调用顺序:**
1. `GET /v/api/v1/item/{season_guid}` - 获取季详情
2. `GET /v/api/v1/person/list/{season_guid}` - 获取演职人员
3. `GET /v/api/v1/episode/list/{season_guid}` - 获取剧集列表
4. `GET /v/api/v1/stream/list/{season_guid}` - 获取流信息
5. `POST /v/api/v1/play/info` - 获取播放信息

**显示内容:**
- 电视剧标题 + 季信息
- 剧集列表 (带标题和简介)
- 演职人员

### 第三层: 剧集详情页 (`/v/tv/episode/{episode_guid}`)
**API调用顺序:**
1. `GET /v/api/v1/item/{episode_guid}` - 获取剧集详情
2. `GET /v/api/v1/stream/list/{episode_guid}` - 获取流信息
3. `POST /v/api/v1/play/info` - 获取播放信息

**显示内容:**
- 剧集标题、简介
- 文件信息 (位置、大小、日期)
- 视频信息 (分辨率、编码、音频)
- 播放按钮

---

## 🎬 播放流程

### 开始播放
1. 调用 `POST /v/api/v1/play/info` 获取 `media_guid`
2. 构建播放URL: `/v/api/v1/media/range/{media_guid}`
3. 跳转到播放器页面

### 播放器页面 (`/v/video/{item_guid}?media_guid={media_guid}`)
**API调用:**
1. `POST /v/api/v1/play/info` - 获取播放信息
2. `POST /v/api/v1/stream` - 获取流详情 (质量列表)
3. `GET /v/api/v1/episode/list/{season_guid}` - 获取剧集列表 (用于选集)

**播放器功能:**
- 进度条
- 画质选择 (原画等)
- 选集
- 倍速播放

---

## 📝 请求示例

### POST /v/api/v1/play/info
**请求体:**
```json
{
    "item_guid": "e69af50770de48419a24a73dcd186dcd"
}
```

**响应:**
```json
{
    "code": 0,
    "msg": "success",
    "data": {
        "guid": "e69af50770de48419a24a73dcd186dcd",
        "parent_guid": "f7f3ed835e9949e8862940bf65b8888a",
        "grand_guid": "xxx",
        "media_guid": "bfea99919e15458c824fbe1b83d7a3a2",
        "video_guid": "xxx",
        "audio_guid": "xxx",
        "subtitle_guid": "xxx",
        "type": "Episode",
        "ts": 95,
        "play_config": {
            "skip_opening": null,
            "skip_ending": null
        },
        "item": {
            "guid": "e69af50770de48419a24a73dcd186dcd",
            "tv_title": "知否知否应是绿肥红瘦",
            "parent_title": "第 1 季",
            "title": "盛明兰出头赢聘雁",
            "episode_number": 1,
            "season_number": 1,
            "duration": 2584,
            "overview": "...",
            "poster": "/b3/09/xxx.webp"
        }
    }
}
```

### POST /v/api/v1/item/list
**请求体:**
```json
{
    "ancestor_guid": "fv_xxx",
    "tags": {
        "type": ["Movie", "TV", "Directory", "Video"]
    },
    "exclude_grouped_video": 1,
    "sort_type": "DESC",
    "sort_column": "create_time",
    "page_size": 100
}
```

---

## 🔧 Android实现对应

| Web功能 | Android Activity | 说明 |
|---------|-----------------|------|
| 首页 | MainActivity | 媒体库列表 + 继续观看 |
| TV详情 | MediaDetailActivity | 电视剧第一层，显示季列表 |
| 季详情 | SeasonDetailActivity | 电视剧第二层，显示剧集列表 |
| 播放器 | VideoPlayerActivity | 视频播放 |

---

## ✅ 已实现功能

- [x] 登录认证
- [x] 首页媒体库列表
- [x] 继续观看列表
- [x] 媒体库内容浏览
- [x] 电视剧详情页 (第一层)
- [x] 季详情页 (第二层)
- [x] 剧集播放
- [x] 电影播放

## 🔄 待优化

- [ ] 播放进度记录
- [ ] 弹幕功能
- [ ] 搜索功能
- [ ] 收藏功能
