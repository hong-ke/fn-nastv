package com.mynas.nastv.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * 季度数据模型
 * 对应Web项目中的电视剧/动漫的季度信息
 * 用于VideoDetailActivity的季度选择功能
 */
public class Season implements Parcelable {
    private String id;              // 季度ID
    private String name;            // 季度名称 ("第1季", "Season 1"等)
    private String originalName;    // 原始名称
    private int seasonNumber;       // 季度编号
    private String year;            // 年份
    private String airDate;         // 首播日期
    private String overview;        // 季度简介
    
    // 统计信息
    private int episodeCount;       // 集数
    private int watchedEpisodes;    // 已观看集数
    private float watchedProgress;  // 观看进度 (0-100)
    
    // 图片信息
    private String posterUrl;       // 季度海报URL
    private String backdropUrl;     // 季度背景图URL
    
    // 剧集列表
    private List<Episode> episodes; // 该季度的所有剧集
    
    // 评分信息
    private float rating;           // 季度评分
    private int voteCount;          // 评分人数
    
    // 状态信息
    private String status;          // 状态 (ongoing, completed, upcoming等)
    private boolean isCurrentSeason; // 是否为当前季
    private boolean isSelected;     // 是否选中状态
    
    // 构造函数
    public Season() {
        this.episodes = new ArrayList<>();
    }
    
    public Season(String id, String name, String year, int episodeCount) {
        this.id = id;
        this.name = name;
        this.year = year;
        this.episodeCount = episodeCount;
        this.episodes = new ArrayList<>();
        this.watchedEpisodes = 0;
        this.watchedProgress = 0;
        this.isSelected = false;
    }
    
    public Season(String id, String name, int seasonNumber, String year, int episodeCount) {
        this.id = id;
        this.name = name;
        this.seasonNumber = seasonNumber;
        this.year = year;
        this.episodeCount = episodeCount;
        this.episodes = new ArrayList<>();
        this.watchedEpisodes = 0;
        this.watchedProgress = 0;
        this.isSelected = false;
    }
    
    // Parcelable实现
    protected Season(Parcel in) {
        id = in.readString();
        name = in.readString();
        originalName = in.readString();
        seasonNumber = in.readInt();
        year = in.readString();
        airDate = in.readString();
        overview = in.readString();
        episodeCount = in.readInt();
        watchedEpisodes = in.readInt();
        watchedProgress = in.readFloat();
        posterUrl = in.readString();
        backdropUrl = in.readString();
        episodes = in.createTypedArrayList(Episode.CREATOR);
        rating = in.readFloat();
        voteCount = in.readInt();
        status = in.readString();
        isCurrentSeason = in.readByte() != 0;
        isSelected = in.readByte() != 0;
    }
    
    public static final Creator<Season> CREATOR = new Creator<Season>() {
        @Override
        public Season createFromParcel(Parcel in) {
            return new Season(in);
        }
        
        @Override
        public Season[] newArray(int size) {
            return new Season[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(originalName);
        dest.writeInt(seasonNumber);
        dest.writeString(year);
        dest.writeString(airDate);
        dest.writeString(overview);
        dest.writeInt(episodeCount);
        dest.writeInt(watchedEpisodes);
        dest.writeFloat(watchedProgress);
        dest.writeString(posterUrl);
        dest.writeString(backdropUrl);
        dest.writeTypedList(episodes);
        dest.writeFloat(rating);
        dest.writeInt(voteCount);
        dest.writeString(status);
        dest.writeByte((byte) (isCurrentSeason ? 1 : 0));
        dest.writeByte((byte) (isSelected ? 1 : 0));
    }
    
    // Getter和Setter方法
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    
    public int getSeasonNumber() { return seasonNumber; }
    public void setSeasonNumber(int seasonNumber) { this.seasonNumber = seasonNumber; }
    
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    
    public String getAirDate() { return airDate; }
    public void setAirDate(String airDate) { this.airDate = airDate; }
    
    public String getOverview() { return overview; }
    public void setOverview(String overview) { this.overview = overview; }
    
    public int getEpisodeCount() { return episodeCount; }
    public void setEpisodeCount(int episodeCount) { this.episodeCount = episodeCount; }
    
    public int getWatchedEpisodes() { return watchedEpisodes; }
    public void setWatchedEpisodes(int watchedEpisodes) { 
        this.watchedEpisodes = watchedEpisodes;
        // 自动计算观看进度
        if (episodeCount > 0) {
            this.watchedProgress = (float) watchedEpisodes / episodeCount * 100;
        }
    }
    
    public float getWatchedProgress() { return watchedProgress; }
    public void setWatchedProgress(float watchedProgress) { this.watchedProgress = watchedProgress; }
    
    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
    
    public String getBackdropUrl() { return backdropUrl; }
    public void setBackdropUrl(String backdropUrl) { this.backdropUrl = backdropUrl; }
    
    public List<Episode> getEpisodes() { return episodes; }
    public void setEpisodes(List<Episode> episodes) { 
        this.episodes = episodes;
        if (episodes != null) {
            this.episodeCount = episodes.size();
            // 计算已观看集数
            this.watchedEpisodes = 0;
            for (Episode episode : episodes) {
                if (episode.getWatchedProgress() >= 95) { // 95%以上认为已观看
                    this.watchedEpisodes++;
                }
            }
            // 自动计算观看进度
            if (episodeCount > 0) {
                this.watchedProgress = (float) watchedEpisodes / episodeCount * 100;
            }
        }
    }
    
    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }
    
    public int getVoteCount() { return voteCount; }
    public void setVoteCount(int voteCount) { this.voteCount = voteCount; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public boolean isCurrentSeason() { return isCurrentSeason; }
    public void setCurrentSeason(boolean currentSeason) { isCurrentSeason = currentSeason; }
    
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
    
    // 辅助方法
    
    /**
     * 获取观看进度文本
     */
    public String getProgressText() {
        if (watchedEpisodes <= 0) {
            return "未观看";
        } else if (watchedEpisodes >= episodeCount) {
            return "已观看";
        } else {
            return String.format("%d/%d集", watchedEpisodes, episodeCount);
        }
    }
    
    /**
     * 获取观看进度百分比文本
     */
    public String getProgressPercentageText() {
        if (watchedProgress <= 0) {
            return "0%";
        } else if (watchedProgress >= 99) {
            return "100%";
        } else {
            return String.format("%.0f%%", watchedProgress);
        }
    }
    
    /**
     * 是否有观看进度
     */
    public boolean hasWatchProgress() {
        return watchedProgress > 0 && watchedProgress < 99;
    }
    
    /**
     * 是否已观看完毕
     */
    public boolean isCompleted() {
        return watchedProgress >= 99 || watchedEpisodes >= episodeCount;
    }
    
    /**
     * 获取季度状态文本
     */
    public String getStatusText() {
        switch (status != null ? status.toLowerCase() : "") {
            case "ongoing":
                return "更新中";
            case "completed":
                return "已完结";
            case "upcoming":
                return "即将播出";
            case "canceled":
                return "已取消";
            default:
                return "";
        }
    }
    
    /**
     * 是否为新季度
     */
    public boolean isNewSeason() {
        if (airDate == null) return false;
        
        try {
            // 简单判断：如果首播日期在3个月内，认为是新季度
            // 实际实现可以使用更精确的日期解析
            long currentTime = System.currentTimeMillis();
            long threeMonthsAgo = currentTime - (3L * 30 * 24 * 60 * 60 * 1000);
            
            // 这里需要根据实际的日期格式进行解析
            // 临时返回false，实际实现时需要解析airDate
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取下一个未观看的剧集
     */
    public Episode getNextUnwatchedEpisode() {
        if (episodes == null || episodes.isEmpty()) {
            return null;
        }
        
        for (Episode episode : episodes) {
            if (episode.getWatchedProgress() < 95) { // 95%以上认为已观看
                return episode;
            }
        }
        
        return null; // 全部已观看
    }
    
    /**
     * 根据集数获取剧集
     */
    public Episode getEpisodeByNumber(int episodeNumber) {
        if (episodes == null || episodes.isEmpty()) {
            return null;
        }
        
        for (Episode episode : episodes) {
            if (episode.getEpisodeNumber() == episodeNumber) {
                return episode;
            }
        }
        
        return null;
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
     * 获取季度显示名称
     */
    public String getDisplayName() {
        if (name != null && !name.isEmpty()) {
            return name;
        } else if (seasonNumber > 0) {
            return "第" + seasonNumber + "季";
        } else {
            return "季度";
        }
    }
    
    /**
     * 获取年份和集数信息
     */
    public String getYearAndEpisodeInfo() {
        StringBuilder info = new StringBuilder();
        if (year != null && !year.isEmpty()) {
            info.append(year);
        }
        if (episodeCount > 0) {
            if (info.length() > 0) {
                info.append(" • ");
            }
            info.append(episodeCount).append("集");
        }
        return info.toString();
    }
    
    @Override
    public String toString() {
        return "Season{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", seasonNumber=" + seasonNumber +
                ", year='" + year + '\'' +
                ", episodeCount=" + episodeCount +
                ", watchedEpisodes=" + watchedEpisodes +
                ", watchedProgress=" + watchedProgress +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Season season = (Season) obj;
        return id != null ? id.equals(season.id) : season.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
