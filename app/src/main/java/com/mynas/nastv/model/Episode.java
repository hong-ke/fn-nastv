package com.mynas.nastv.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 剧集数据模型
 * 对应Web项目中的单集信息
 * 用于VideoDetailActivity和VideoPlayerActivity
 */
public class Episode implements Parcelable {
    private String id;              // 剧集ID (episode_guid)
    private String title;           // 剧集标题
    private String originalTitle;   // 原始标题
    private int episodeNumber;      // 集数编号
    private int seasonNumber;       // 季数编号
    private String overview;        // 剧集简介
    private String plotSummary;     // 详细剧情
    
    // 时间信息
    private String airDate;         // 播出日期
    private String airTime;         // 播出时间
    private String duration;        // 时长 (格式: "45:30")
    private int durationMinutes;    // 时长 (分钟)
    
    // 图片信息
    private String stillUrl;        // 剧照URL
    private String thumbnailUrl;    // 缩略图URL
    
    // 评分和统计
    private float rating;           // 剧集评分
    private int voteCount;          // 评分人数
    private long viewCount;         // 观看次数
    
    // 播放相关
    private float watchedProgress;  // 观看进度 (0-100)
    private long watchedTimestamp;  // 观看位置 (秒)
    private long lastWatchedTime;   // 最后观看时间戳
    private boolean isWatched;      // 是否已观看
    private boolean isFavorite;     // 是否收藏
    
    // 技术信息
    private String[] availableQualities; // 可用画质
    private String[] availableLanguages; // 可用语言
    private String[] availableSubtitles; // 可用字幕
    private boolean hasHEVC;        // 是否支持HEVC
    private boolean has4K;          // 是否支持4K
    private String codec;           // 主要编码格式
    
    // 制作信息
    private String director;        // 导演
    private String writer;          // 编剧
    private String[] guestStars;    // 客串演员
    
    // 状态信息
    private String status;          // 状态 (available, upcoming, error等)
    private boolean isCurrentEpisode; // 是否为当前播放集
    private boolean isSelected;     // 是否选中状态
    private boolean isDownloaded;   // 是否已下载
    
    // 构造函数
    public Episode() {}
    
    public Episode(String id, int episodeNumber, String title, String duration) {
        this.id = id;
        this.episodeNumber = episodeNumber;
        this.title = title;
        this.duration = duration;
        this.watchedProgress = 0;
        this.isWatched = false;
        this.isFavorite = false;
        this.isSelected = false;
        this.isDownloaded = false;
    }
    
    public Episode(String id, int episodeNumber, String title, String overview, String duration, float watchedProgress) {
        this.id = id;
        this.episodeNumber = episodeNumber;
        this.title = title;
        this.overview = overview;
        this.duration = duration;
        this.watchedProgress = watchedProgress;
        this.isWatched = watchedProgress >= 95; // 95%以上认为已观看
        this.isFavorite = false;
        this.isSelected = false;
        this.isDownloaded = false;
    }
    
    // Parcelable实现
    protected Episode(Parcel in) {
        id = in.readString();
        title = in.readString();
        originalTitle = in.readString();
        episodeNumber = in.readInt();
        seasonNumber = in.readInt();
        overview = in.readString();
        plotSummary = in.readString();
        airDate = in.readString();
        airTime = in.readString();
        duration = in.readString();
        durationMinutes = in.readInt();
        stillUrl = in.readString();
        thumbnailUrl = in.readString();
        rating = in.readFloat();
        voteCount = in.readInt();
        viewCount = in.readLong();
        watchedProgress = in.readFloat();
        watchedTimestamp = in.readLong();
        lastWatchedTime = in.readLong();
        isWatched = in.readByte() != 0;
        isFavorite = in.readByte() != 0;
        availableQualities = in.createStringArray();
        availableLanguages = in.createStringArray();
        availableSubtitles = in.createStringArray();
        hasHEVC = in.readByte() != 0;
        has4K = in.readByte() != 0;
        codec = in.readString();
        director = in.readString();
        writer = in.readString();
        guestStars = in.createStringArray();
        status = in.readString();
        isCurrentEpisode = in.readByte() != 0;
        isSelected = in.readByte() != 0;
        isDownloaded = in.readByte() != 0;
    }
    
    public static final Creator<Episode> CREATOR = new Creator<Episode>() {
        @Override
        public Episode createFromParcel(Parcel in) {
            return new Episode(in);
        }
        
        @Override
        public Episode[] newArray(int size) {
            return new Episode[size];
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
        dest.writeString(originalTitle);
        dest.writeInt(episodeNumber);
        dest.writeInt(seasonNumber);
        dest.writeString(overview);
        dest.writeString(plotSummary);
        dest.writeString(airDate);
        dest.writeString(airTime);
        dest.writeString(duration);
        dest.writeInt(durationMinutes);
        dest.writeString(stillUrl);
        dest.writeString(thumbnailUrl);
        dest.writeFloat(rating);
        dest.writeInt(voteCount);
        dest.writeLong(viewCount);
        dest.writeFloat(watchedProgress);
        dest.writeLong(watchedTimestamp);
        dest.writeLong(lastWatchedTime);
        dest.writeByte((byte) (isWatched ? 1 : 0));
        dest.writeByte((byte) (isFavorite ? 1 : 0));
        dest.writeStringArray(availableQualities);
        dest.writeStringArray(availableLanguages);
        dest.writeStringArray(availableSubtitles);
        dest.writeByte((byte) (hasHEVC ? 1 : 0));
        dest.writeByte((byte) (has4K ? 1 : 0));
        dest.writeString(codec);
        dest.writeString(director);
        dest.writeString(writer);
        dest.writeStringArray(guestStars);
        dest.writeString(status);
        dest.writeByte((byte) (isCurrentEpisode ? 1 : 0));
        dest.writeByte((byte) (isSelected ? 1 : 0));
        dest.writeByte((byte) (isDownloaded ? 1 : 0));
    }
    
    // Getter和Setter方法
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getOriginalTitle() { return originalTitle; }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }
    
    public int getEpisodeNumber() { return episodeNumber; }
    public void setEpisodeNumber(int episodeNumber) { this.episodeNumber = episodeNumber; }
    
    public int getSeasonNumber() { return seasonNumber; }
    public void setSeasonNumber(int seasonNumber) { this.seasonNumber = seasonNumber; }
    
    public String getOverview() { return overview; }
    public void setOverview(String overview) { this.overview = overview; }
    
    public String getPlotSummary() { return plotSummary; }
    public void setPlotSummary(String plotSummary) { this.plotSummary = plotSummary; }
    
    public String getAirDate() { return airDate; }
    public void setAirDate(String airDate) { this.airDate = airDate; }
    
    public String getAirTime() { return airTime; }
    public void setAirTime(String airTime) { this.airTime = airTime; }
    
    public String getDuration() { return duration; }
    public void setDuration(String duration) { 
        this.duration = duration;
        // 自动解析时长
        this.durationMinutes = parseDurationToMinutes(duration);
    }
    
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    
    public String getStillUrl() { return stillUrl; }
    public void setStillUrl(String stillUrl) { this.stillUrl = stillUrl; }
    
    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
    
    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }
    
    public int getVoteCount() { return voteCount; }
    public void setVoteCount(int voteCount) { this.voteCount = voteCount; }
    
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    
    public float getWatchedProgress() { return watchedProgress; }
    public void setWatchedProgress(float watchedProgress) { 
        this.watchedProgress = watchedProgress;
        this.isWatched = watchedProgress >= 95; // 95%以上认为已观看
    }
    
    public long getWatchedTimestamp() { return watchedTimestamp; }
    public void setWatchedTimestamp(long watchedTimestamp) { this.watchedTimestamp = watchedTimestamp; }
    
    public long getLastWatchedTime() { return lastWatchedTime; }
    public void setLastWatchedTime(long lastWatchedTime) { this.lastWatchedTime = lastWatchedTime; }
    
    public boolean isWatched() { return isWatched; }
    public void setWatched(boolean watched) { isWatched = watched; }
    
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
    
    public String[] getAvailableQualities() { return availableQualities; }
    public void setAvailableQualities(String[] availableQualities) { this.availableQualities = availableQualities; }
    
    public String[] getAvailableLanguages() { return availableLanguages; }
    public void setAvailableLanguages(String[] availableLanguages) { this.availableLanguages = availableLanguages; }
    
    public String[] getAvailableSubtitles() { return availableSubtitles; }
    public void setAvailableSubtitles(String[] availableSubtitles) { this.availableSubtitles = availableSubtitles; }
    
    public boolean hasHEVC() { return hasHEVC; }
    public void setHasHEVC(boolean hasHEVC) { this.hasHEVC = hasHEVC; }
    
    public boolean has4K() { return has4K; }
    public void setHas4K(boolean has4K) { this.has4K = has4K; }
    
    public String getCodec() { return codec; }
    public void setCodec(String codec) { this.codec = codec; }
    
    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }
    
    public String getWriter() { return writer; }
    public void setWriter(String writer) { this.writer = writer; }
    
    public String[] getGuestStars() { return guestStars; }
    public void setGuestStars(String[] guestStars) { this.guestStars = guestStars; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public boolean isCurrentEpisode() { return isCurrentEpisode; }
    public void setCurrentEpisode(boolean currentEpisode) { isCurrentEpisode = currentEpisode; }
    
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
    
    public boolean isDownloaded() { return isDownloaded; }
    public void setDownloaded(boolean downloaded) { isDownloaded = downloaded; }
    
    // 辅助方法
    
    /**
     * 获取剧集显示标题
     */
    public String getDisplayTitle() {
        if (title != null && !title.isEmpty()) {
            return title;
        } else {
            return "第" + episodeNumber + "集";
        }
    }
    
    /**
     * 获取剧集编号文本
     */
    public String getEpisodeNumberText() {
        if (seasonNumber > 0) {
            return String.format("S%02dE%02d", seasonNumber, episodeNumber);
        } else {
            return String.format("E%02d", episodeNumber);
        }
    }
    
    /**
     * 获取观看进度文本
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
     * 获取观看位置文本
     */
    public String getWatchedPositionText() {
        if (watchedTimestamp <= 0 || durationMinutes <= 0) {
            return "";
        }
        
        long remainingSeconds = (durationMinutes * 60) - watchedTimestamp;
        if (remainingSeconds <= 0) {
            return "已观看完毕";
        }
        
        return String.format("还剩 %s", formatDuration(remainingSeconds));
    }
    
    /**
     * 获取格式化的评分文本
     */
    public String getFormattedRating() {
        if (rating > 0) {
            return String.format("%.1f", rating);
        }
        return "";
    }
    
    /**
     * 获取格式化的播出日期
     */
    public String getFormattedAirDate() {
        if (airDate == null || airDate.isEmpty()) {
            return "";
        }
        
        try {
            // 假设airDate格式为 "yyyy-MM-dd"
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy年M月d日", Locale.getDefault());
            Date date = inputFormat.parse(airDate);
            return outputFormat.format(date);
        } catch (Exception e) {
            return airDate; // 解析失败返回原始字符串
        }
    }
    
    /**
     * 是否为新剧集
     */
    public boolean isNewEpisode() {
        if (airDate == null) return false;
        
        try {
            // 简单判断：如果播出日期在7天内，认为是新剧集
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date air = format.parse(airDate);
            long currentTime = System.currentTimeMillis();
            long oneWeekAgo = currentTime - (7L * 24 * 60 * 60 * 1000);
            
            return air != null && air.getTime() > oneWeekAgo;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取技术规格标签
     */
    public String getTechSpecsText() {
        StringBuilder specs = new StringBuilder();
        
        if (has4K) specs.append("4K ");
        if (hasHEVC) specs.append("HEVC ");
        if (codec != null && !codec.isEmpty()) {
            specs.append(codec.toUpperCase()).append(" ");
        }
        
        return specs.toString().trim();
    }
    
    /**
     * 是否可以播放
     */
    public boolean isPlayable() {
        return "available".equals(status) || status == null || status.isEmpty();
    }
    
    /**
     * 是否有观看进度
     */
    public boolean hasWatchProgress() {
        return watchedProgress > 0 && watchedProgress < 95;
    }
    
    /**
     * 解析时长字符串到分钟数
     */
    private int parseDurationToMinutes(String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) {
            return 0;
        }
        
        try {
            // 支持格式: "45:30", "1:23:45", "90" (分钟)
            String[] parts = durationStr.split(":");
            
            if (parts.length == 1) {
                // 只有分钟数
                return Integer.parseInt(parts[0]);
            } else if (parts.length == 2) {
                // MM:SS 格式
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return minutes + (seconds >= 30 ? 1 : 0); // 30秒以上进位
            } else if (parts.length == 3) {
                // HH:MM:SS 格式
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                return hours * 60 + minutes + (seconds >= 30 ? 1 : 0);
            }
        } catch (NumberFormatException e) {
            // 解析失败，返回0
        }
        
        return 0;
    }
    
    /**
     * 格式化时长（秒数）
     */
    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%d:%02d", minutes, secs);
        }
    }
    
    @Override
    public String toString() {
        return "Episode{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", episodeNumber=" + episodeNumber +
                ", seasonNumber=" + seasonNumber +
                ", duration='" + duration + '\'' +
                ", watchedProgress=" + watchedProgress +
                ", isWatched=" + isWatched +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Episode episode = (Episode) obj;
        return id != null ? id.equals(episode.id) : episode.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
