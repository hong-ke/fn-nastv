package com.mynas.nastv.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * 🎬 媒体项目数据模型
 * 对应Web项目中的媒体项目显示
 * 用于首页、列表页、搜索结果等场景
 */
public class MediaItem implements Parcelable {
    private String id;              // 媒体项目ID (guid)
    private String title;           // 标题
    private String subtitle;        // 副标题 (年份、分辨率、编码等信息)
    private String type;            // 类型 (movie, tv, anime等)
    private String posterUrl;       // 海报图片URL
    private String backdropUrl;     // 背景图片URL
    private int posterResource;     // 本地海报资源ID (备用)
    
    // 📊 媒体信息
    private String year;            // 年份
    private String genre;           // 类型/标签
    private float rating;           // 评分
    private int duration;           // 时长 (分钟)
    private String resolution;      // 分辨率
    private String codec;           // 编码格式
    
    // 📖 播放相关
    private float watchedProgress;  // 观看进度 (0-100)
    private long lastWatchedTime;   // 最后观看时间戳
    private long watchedTs;         // 已观看时长（秒）
    private long totalDuration;     // 总时长（秒）
    private boolean isFavorite;     // 是否收藏
    private boolean isNew;          // 是否新增内容
    
    // 🎬 剧集相关 (电视剧/动漫)
    private int totalEpisodes;      // 总集数
    private int watchedEpisodes;    // 已观看集数
    private String currentEpisode;  // 当前集数信息
    
    // 🔗 关联信息 (用于继续观看导航)
    private String parentGuid;      // 父级GUID (Episode -> Season)
    private String ancestorGuid;    // 祖先GUID (Episode -> TV)
    private String mediaGuid;       // 媒体文件GUID (用于直接播放)
    
    // 🎬 弹幕相关
    private long doubanId;          // 豆瓣ID (用于获取弹幕)
    private int seasonNumber;       // 季数
    private int episodeNumber;      // 集数
    private String tvTitle;         // 电视剧标题（用于弹幕搜索）
    
    // 🔧 构造函数
    public MediaItem() {}
    
    public MediaItem(String id, String title, String subtitle, int posterResource) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.posterResource = posterResource;
        this.watchedProgress = 0;
        this.isFavorite = false;
        this.isNew = false;
    }
    
    public MediaItem(String id, String title, String subtitle, String posterUrl) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.posterUrl = posterUrl;
        this.watchedProgress = 0;
        this.isFavorite = false;
        this.isNew = false;
    }
    
    // 🔄 Parcelable实现
    protected MediaItem(Parcel in) {
        id = in.readString();
        title = in.readString();
        subtitle = in.readString();
        type = in.readString();
        posterUrl = in.readString();
        backdropUrl = in.readString();
        posterResource = in.readInt();
        year = in.readString();
        genre = in.readString();
        rating = in.readFloat();
        duration = in.readInt();
        resolution = in.readString();
        codec = in.readString();
        watchedProgress = in.readFloat();
        lastWatchedTime = in.readLong();
        watchedTs = in.readLong();
        totalDuration = in.readLong();
        isFavorite = in.readByte() != 0;
        isNew = in.readByte() != 0;
        totalEpisodes = in.readInt();
        watchedEpisodes = in.readInt();
        currentEpisode = in.readString();
        parentGuid = in.readString();
        ancestorGuid = in.readString();
        mediaGuid = in.readString();
        doubanId = in.readLong();
        seasonNumber = in.readInt();
        episodeNumber = in.readInt();
        tvTitle = in.readString();
    }
    
    public static final Creator<MediaItem> CREATOR = new Creator<MediaItem>() {
        @Override
        public MediaItem createFromParcel(Parcel in) {
            return new MediaItem(in);
        }
        
        @Override
        public MediaItem[] newArray(int size) {
            return new MediaItem[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(title);
        dest.writeString(subtitle);
        dest.writeString(type);
        dest.writeString(posterUrl);
        dest.writeString(backdropUrl);
        dest.writeInt(posterResource);
        dest.writeString(year);
        dest.writeString(genre);
        dest.writeFloat(rating);
        dest.writeInt(duration);
        dest.writeString(resolution);
        dest.writeString(codec);
        dest.writeFloat(watchedProgress);
        dest.writeLong(lastWatchedTime);
        dest.writeLong(watchedTs);
        dest.writeLong(totalDuration);
        dest.writeByte((byte) (isFavorite ? 1 : 0));
        dest.writeByte((byte) (isNew ? 1 : 0));
        dest.writeInt(totalEpisodes);
        dest.writeInt(watchedEpisodes);
        dest.writeString(currentEpisode);
        dest.writeString(parentGuid);
        dest.writeString(ancestorGuid);
        dest.writeString(mediaGuid);
        dest.writeLong(doubanId);
        dest.writeInt(seasonNumber);
        dest.writeInt(episodeNumber);
        dest.writeString(tvTitle);
    }
    
    // 📖 Getter和Setter方法
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    // 兼容方法：为了保持API一致性，getGuid()等同于getId()
    public String getGuid() { return id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
    
    public String getBackdropUrl() { return backdropUrl; }
    public void setBackdropUrl(String backdropUrl) { this.backdropUrl = backdropUrl; }
    
    public int getPosterResource() { return posterResource; }
    public void setPosterResource(int posterResource) { this.posterResource = posterResource; }
    
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    
    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }
    
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    
    public String getCodec() { return codec; }
    public void setCodec(String codec) { this.codec = codec; }
    
    public float getWatchedProgress() { return watchedProgress; }
    public void setWatchedProgress(float watchedProgress) { this.watchedProgress = watchedProgress; }
    
    public long getLastWatchedTime() { return lastWatchedTime; }
    public void setLastWatchedTime(long lastWatchedTime) { this.lastWatchedTime = lastWatchedTime; }
    
    public long getWatchedTs() { return watchedTs; }
    public void setWatchedTs(long watchedTs) { 
        this.watchedTs = watchedTs;
        // 自动计算观看进度
        if (totalDuration > 0) {
            this.watchedProgress = (float) watchedTs / totalDuration * 100;
        }
    }
    
    public long getTotalDuration() { return totalDuration; }
    public void setTotalDuration(long totalDuration) { this.totalDuration = totalDuration; }
    
    /**
     * 设置时长（秒），同时更新 totalDuration
     */
    public void setDuration(long durationSeconds) { 
        this.totalDuration = durationSeconds;
        this.duration = (int)(durationSeconds / 60); // 转换为分钟
        // 重新计算观看进度
        if (totalDuration > 0 && watchedTs > 0) {
            this.watchedProgress = (float) watchedTs / totalDuration * 100;
        }
    }
    
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
    
    public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    
    public int getTotalEpisodes() { return totalEpisodes; }
    public void setTotalEpisodes(int totalEpisodes) { this.totalEpisodes = totalEpisodes; }
    
    public int getWatchedEpisodes() { return watchedEpisodes; }
    public void setWatchedEpisodes(int watchedEpisodes) { this.watchedEpisodes = watchedEpisodes; }
    
    public String getCurrentEpisode() { return currentEpisode; }
    public void setCurrentEpisode(String currentEpisode) { this.currentEpisode = currentEpisode; }
    
    public String getParentGuid() { return parentGuid; }
    public void setParentGuid(String parentGuid) { this.parentGuid = parentGuid; }
    
    public String getAncestorGuid() { return ancestorGuid; }
    public void setAncestorGuid(String ancestorGuid) { this.ancestorGuid = ancestorGuid; }
    
    public String getMediaGuid() { return mediaGuid; }
    public void setMediaGuid(String mediaGuid) { this.mediaGuid = mediaGuid; }
    
    public long getDoubanId() { return doubanId; }
    public void setDoubanId(long doubanId) { this.doubanId = doubanId; }
    
    public int getSeasonNumber() { return seasonNumber; }
    public void setSeasonNumber(int seasonNumber) { this.seasonNumber = seasonNumber; }
    
    public int getEpisodeNumber() { return episodeNumber; }
    public void setEpisodeNumber(int episodeNumber) { this.episodeNumber = episodeNumber; }
    
    public String getTvTitle() { return tvTitle; }
    public void setTvTitle(String tvTitle) { this.tvTitle = tvTitle; }
    
    // 🔧 辅助方法
    
    /**
     * 📊 获取格式化的进度文本
     */
    public String getProgressText() {
        if (watchedProgress <= 0) {
            return "未观看";
        } else if (watchedProgress >= 95) {
            return "已观看";
        } else {
            return String.format("%.0f%%", watchedProgress);
        }
    }
    
    /**
     * 📺 获取剧集进度文本
     */
    public String getEpisodeProgressText() {
        if (totalEpisodes <= 1) {
            return getProgressText();
        } else {
            return String.format("第%d/%d集", watchedEpisodes, totalEpisodes);
        }
    }
    
    /**
     * 🎬 是否为电视剧类型
     */
    public boolean isTvSeries() {
        return "tv".equals(type) || "anime".equals(type) || totalEpisodes > 1;
    }
    
    /**
     * 📖 是否有观看进度
     */
    public boolean hasWatchProgress() {
        return watchedProgress > 0 && watchedProgress < 95;
    }
    
    /**
     * 🆕 是否应该显示"新"标签
     */
    public boolean shouldShowNewBadge() {
        return isNew || (System.currentTimeMillis() - lastWatchedTime) < 7 * 24 * 60 * 60 * 1000; // 7天内
    }
    
    @Override
    public String toString() {
        return "MediaItem{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", year='" + year + '\'' +
                ", rating=" + rating +
                ", watchedProgress=" + watchedProgress +
                ", isFavorite=" + isFavorite +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MediaItem mediaItem = (MediaItem) obj;
        return id != null ? id.equals(mediaItem.id) : mediaItem.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
