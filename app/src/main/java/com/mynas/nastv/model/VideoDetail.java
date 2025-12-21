package com.mynas.nastv.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * 🎬 视频详情数据模型
 * 对应Web项目中的VideoData.vue详情页面
 * 包含完整的视频信息、剧集、演员等详细数据
 */
public class VideoDetail implements Parcelable {
    // 🆔 基本信息
    private String id;                  // 视频ID (guid)
    private String title;               // 标题
    private String originalTitle;       // 原始标题
    private String year;                // 年份
    private String type;                // 类型 (movie, tv, anime)
    private String status;              // 状态 (ongoing, completed等)
    
    // 🖼️ 图片信息
    private String posterUrl;           // 海报URL
    private String backdropUrl;         // 背景图URL
    private List<String> screenshots;   // 剧照列表
    
    // 📊 评分和统计
    private float rating;               // 评分
    private String ratingSource;        // 评分来源 (IMDb, 豆瓣等)
    private int voteCount;              // 评分人数
    private long viewCount;             // 观看次数
    
    // 📝 描述信息
    private String overview;            // 简介/描述
    private String plotSummary;         // 剧情简介
    private List<String> genres;        // 类型标签列表
    private List<String> tags;          // 标签列表
    private String language;            // 语言
    private String country;             // 制作国家/地区
    
    // 🎬 制作信息
    private String director;            // 导演
    private List<String> directors;     // 导演列表
    private List<String> writers;       // 编剧列表
    private List<String> producers;     // 制片人列表
    private String studio;              // 制作公司
    private String network;             // 播出平台
    
    // 📅 时间信息
    private String releaseDate;         // 首播/上映日期
    private String lastAirDate;         // 最后播出日期
    private int runtime;                // 单集时长 (分钟)
    private int totalRuntime;           // 总时长 (分钟)
    
    // 📺 剧集信息 (电视剧/动漫)
    private int totalSeasons;           // 总季数
    private int totalEpisodes;          // 总集数
    private int currentSeason;          // 当前季
    private int currentEpisode;         // 当前集
    private List<Season> seasons;       // 季度列表
    
    // 👥 演员信息
    private List<Actor> cast;           // 演员列表
    private List<Actor> crew;           // 制作团队
    
    // 📖 播放相关
    private float watchedProgress;      // 观看进度
    private long lastWatchedTime;       // 最后观看时间
    private String lastWatchedEpisode;  // 最后观看的剧集
    private boolean isFavorite;         // 是否收藏
    private boolean isInWatchlist;      // 是否在观看列表中
    
    // 📱 技术信息
    private List<String> availableQualities; // 可用画质列表
    private List<String> availableLanguages; // 可用语言列表
    private List<String> availableSubtitles; // 可用字幕列表
    private boolean hasHEVC;            // 是否支持HEVC
    private boolean has4K;              // 是否支持4K
    private boolean hasDolbyVision;     // 是否支持杜比视界
    private boolean hasHDR;             // 是否支持HDR
    
    // 🔧 构造函数
    public VideoDetail() {
        this.screenshots = new ArrayList<>();
        this.genres = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.directors = new ArrayList<>();
        this.writers = new ArrayList<>();
        this.producers = new ArrayList<>();
        this.seasons = new ArrayList<>();
        this.cast = new ArrayList<>();
        this.crew = new ArrayList<>();
        this.availableQualities = new ArrayList<>();
        this.availableLanguages = new ArrayList<>();
        this.availableSubtitles = new ArrayList<>();
    }
    
    // 🔄 Parcelable实现
    protected VideoDetail(Parcel in) {
        id = in.readString();
        title = in.readString();
        originalTitle = in.readString();
        year = in.readString();
        type = in.readString();
        status = in.readString();
        posterUrl = in.readString();
        backdropUrl = in.readString();
        screenshots = in.createStringArrayList();
        rating = in.readFloat();
        ratingSource = in.readString();
        voteCount = in.readInt();
        viewCount = in.readLong();
        overview = in.readString();
        plotSummary = in.readString();
        genres = in.createStringArrayList();
        tags = in.createStringArrayList();
        language = in.readString();
        country = in.readString();
        director = in.readString();
        directors = in.createStringArrayList();
        writers = in.createStringArrayList();
        producers = in.createStringArrayList();
        studio = in.readString();
        network = in.readString();
        releaseDate = in.readString();
        lastAirDate = in.readString();
        runtime = in.readInt();
        totalRuntime = in.readInt();
        totalSeasons = in.readInt();
        totalEpisodes = in.readInt();
        currentSeason = in.readInt();
        currentEpisode = in.readInt();
        seasons = in.createTypedArrayList(Season.CREATOR);
        cast = in.createTypedArrayList(Actor.CREATOR);
        crew = in.createTypedArrayList(Actor.CREATOR);
        watchedProgress = in.readFloat();
        lastWatchedTime = in.readLong();
        lastWatchedEpisode = in.readString();
        isFavorite = in.readByte() != 0;
        isInWatchlist = in.readByte() != 0;
        availableQualities = in.createStringArrayList();
        availableLanguages = in.createStringArrayList();
        availableSubtitles = in.createStringArrayList();
        hasHEVC = in.readByte() != 0;
        has4K = in.readByte() != 0;
        hasDolbyVision = in.readByte() != 0;
        hasHDR = in.readByte() != 0;
    }
    
    public static final Creator<VideoDetail> CREATOR = new Creator<VideoDetail>() {
        @Override
        public VideoDetail createFromParcel(Parcel in) {
            return new VideoDetail(in);
        }
        
        @Override
        public VideoDetail[] newArray(int size) {
            return new VideoDetail[size];
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
        dest.writeString(year);
        dest.writeString(type);
        dest.writeString(status);
        dest.writeString(posterUrl);
        dest.writeString(backdropUrl);
        dest.writeStringList(screenshots);
        dest.writeFloat(rating);
        dest.writeString(ratingSource);
        dest.writeInt(voteCount);
        dest.writeLong(viewCount);
        dest.writeString(overview);
        dest.writeString(plotSummary);
        dest.writeStringList(genres);
        dest.writeStringList(tags);
        dest.writeString(language);
        dest.writeString(country);
        dest.writeString(director);
        dest.writeStringList(directors);
        dest.writeStringList(writers);
        dest.writeStringList(producers);
        dest.writeString(studio);
        dest.writeString(network);
        dest.writeString(releaseDate);
        dest.writeString(lastAirDate);
        dest.writeInt(runtime);
        dest.writeInt(totalRuntime);
        dest.writeInt(totalSeasons);
        dest.writeInt(totalEpisodes);
        dest.writeInt(currentSeason);
        dest.writeInt(currentEpisode);
        dest.writeTypedList(seasons);
        dest.writeTypedList(cast);
        dest.writeTypedList(crew);
        dest.writeFloat(watchedProgress);
        dest.writeLong(lastWatchedTime);
        dest.writeString(lastWatchedEpisode);
        dest.writeByte((byte) (isFavorite ? 1 : 0));
        dest.writeByte((byte) (isInWatchlist ? 1 : 0));
        dest.writeStringList(availableQualities);
        dest.writeStringList(availableLanguages);
        dest.writeStringList(availableSubtitles);
        dest.writeByte((byte) (hasHEVC ? 1 : 0));
        dest.writeByte((byte) (has4K ? 1 : 0));
        dest.writeByte((byte) (hasDolbyVision ? 1 : 0));
        dest.writeByte((byte) (hasHDR ? 1 : 0));
    }
    
    // 📖 Getter和Setter方法 (基本信息)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getOriginalTitle() { return originalTitle; }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }
    
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    // 📖 图片信息
    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
    
    public String getBackdropUrl() { return backdropUrl; }
    public void setBackdropUrl(String backdropUrl) { this.backdropUrl = backdropUrl; }
    
    public List<String> getScreenshots() { return screenshots; }
    public void setScreenshots(List<String> screenshots) { this.screenshots = screenshots; }
    
    // 📖 评分统计
    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }
    
    public String getRatingSource() { return ratingSource; }
    public void setRatingSource(String ratingSource) { this.ratingSource = ratingSource; }
    
    public int getVoteCount() { return voteCount; }
    public void setVoteCount(int voteCount) { this.voteCount = voteCount; }
    
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    
    // 📖 描述信息
    public String getOverview() { return overview; }
    public void setOverview(String overview) { this.overview = overview; }
    
    public String getPlotSummary() { return plotSummary; }
    public void setPlotSummary(String plotSummary) { this.plotSummary = plotSummary; }
    
    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }
    
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    
    // 📖 制作信息
    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }
    
    public List<String> getDirectors() { return directors; }
    public void setDirectors(List<String> directors) { this.directors = directors; }
    
    public List<String> getWriters() { return writers; }
    public void setWriters(List<String> writers) { this.writers = writers; }
    
    public List<String> getProducers() { return producers; }
    public void setProducers(List<String> producers) { this.producers = producers; }
    
    public String getStudio() { return studio; }
    public void setStudio(String studio) { this.studio = studio; }
    
    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }
    
    // 📖 时间信息
    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
    
    public String getLastAirDate() { return lastAirDate; }
    public void setLastAirDate(String lastAirDate) { this.lastAirDate = lastAirDate; }
    
    public int getRuntime() { return runtime; }
    public void setRuntime(int runtime) { this.runtime = runtime; }
    
    public int getTotalRuntime() { return totalRuntime; }
    public void setTotalRuntime(int totalRuntime) { this.totalRuntime = totalRuntime; }
    
    // 📖 剧集信息
    public int getTotalSeasons() { return totalSeasons; }
    public void setTotalSeasons(int totalSeasons) { this.totalSeasons = totalSeasons; }
    
    public int getTotalEpisodes() { return totalEpisodes; }
    public void setTotalEpisodes(int totalEpisodes) { this.totalEpisodes = totalEpisodes; }
    
    public int getCurrentSeason() { return currentSeason; }
    public void setCurrentSeason(int currentSeason) { this.currentSeason = currentSeason; }
    
    public int getCurrentEpisode() { return currentEpisode; }
    public void setCurrentEpisode(int currentEpisode) { this.currentEpisode = currentEpisode; }
    
    public List<Season> getSeasons() { return seasons; }
    public void setSeasons(List<Season> seasons) { this.seasons = seasons; }
    
    // 📖 演员信息
    public List<Actor> getCast() { return cast; }
    public void setCast(List<Actor> cast) { this.cast = cast; }
    
    public List<Actor> getCrew() { return crew; }
    public void setCrew(List<Actor> crew) { this.crew = crew; }
    
    // 📖 播放相关
    public float getWatchedProgress() { return watchedProgress; }
    public void setWatchedProgress(float watchedProgress) { this.watchedProgress = watchedProgress; }
    
    public long getLastWatchedTime() { return lastWatchedTime; }
    public void setLastWatchedTime(long lastWatchedTime) { this.lastWatchedTime = lastWatchedTime; }
    
    public String getLastWatchedEpisode() { return lastWatchedEpisode; }
    public void setLastWatchedEpisode(String lastWatchedEpisode) { this.lastWatchedEpisode = lastWatchedEpisode; }
    
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
    
    public boolean isInWatchlist() { return isInWatchlist; }
    public void setInWatchlist(boolean inWatchlist) { isInWatchlist = inWatchlist; }
    
    // 📖 技术信息
    public List<String> getAvailableQualities() { return availableQualities; }
    public void setAvailableQualities(List<String> availableQualities) { this.availableQualities = availableQualities; }
    
    public List<String> getAvailableLanguages() { return availableLanguages; }
    public void setAvailableLanguages(List<String> availableLanguages) { this.availableLanguages = availableLanguages; }
    
    public List<String> getAvailableSubtitles() { return availableSubtitles; }
    public void setAvailableSubtitles(List<String> availableSubtitles) { this.availableSubtitles = availableSubtitles; }
    
    public boolean hasHEVC() { return hasHEVC; }
    public void setHasHEVC(boolean hasHEVC) { this.hasHEVC = hasHEVC; }
    
    public boolean has4K() { return has4K; }
    public void setHas4K(boolean has4K) { this.has4K = has4K; }
    
    public boolean hasDolbyVision() { return hasDolbyVision; }
    public void setHasDolbyVision(boolean hasDolbyVision) { this.hasDolbyVision = hasDolbyVision; }
    
    public boolean hasHDR() { return hasHDR; }
    public void setHasHDR(boolean hasHDR) { this.hasHDR = hasHDR; }
    
    // 🔧 辅助方法
    
    /**
     * 📺 是否为电视剧类型
     */
    public boolean isTvSeries() {
        return "tv".equals(type) || "anime".equals(type) || totalEpisodes > 1;
    }
    
    /**
     * 🎬 是否为电影类型
     */
    public boolean isMovie() {
        return "movie".equals(type) && totalEpisodes <= 1;
    }
    
    /**
     * 📊 获取格式化的评分文本
     */
    public String getFormattedRating() {
        if (rating > 0) {
            return String.format("%.1f", rating);
        }
        return "暂无评分";
    }
    
    /**
     * 🎭 获取格式化的类型文本
     */
    public String getFormattedGenres() {
        if (genres != null && !genres.isEmpty()) {
            return String.join(" / ", genres);
        }
        return "";
    }
    
    /**
     * 🎭 获取类型字符串 (向后兼容方法)
     */
    public String getGenre() {
        return getFormattedGenres();
    }
    
    /**
     * 🎭 设置类型字符串 (向后兼容方法)
     */
    public void setGenre(String genre) {
        if (genre != null && !genre.isEmpty()) {
            this.genres = new ArrayList<>();
            String[] genreArray = genre.split(" / ");
            for (String g : genreArray) {
                this.genres.add(g.trim());
            }
        }
    }
    
    /**
     * ⏱️ 获取格式化的时长文本
     */
    public String getFormattedRuntime() {
        if (runtime > 0) {
            int hours = runtime / 60;
            int minutes = runtime % 60;
            if (hours > 0) {
                return String.format("%d小时%d分钟", hours, minutes);
            } else {
                return String.format("%d分钟", minutes);
            }
        }
        return "";
    }
    
    /**
     * 📺 获取剧集信息文本
     */
    public String getEpisodeInfoText() {
        if (isTvSeries()) {
            if (totalSeasons > 1) {
                return String.format("%d季 • %d集", totalSeasons, totalEpisodes);
            } else {
                return String.format("%d集", totalEpisodes);
            }
        }
        return getFormattedRuntime();
    }
    
    /**
     * 🔍 是否有观看进度
     */
    public boolean hasWatchProgress() {
        return watchedProgress > 0 && watchedProgress < 95;
    }
    
    /**
     * 📱 获取技术规格文本
     */
    public String getTechSpecsText() {
        List<String> specs = new ArrayList<>();
        if (has4K) specs.add("4K");
        if (hasHEVC) specs.add("HEVC");
        if (hasHDR) specs.add("HDR");
        if (hasDolbyVision) specs.add("杜比视界");
        
        return specs.isEmpty() ? "" : String.join(" • ", specs);
    }
    
    @Override
    public String toString() {
        return "VideoDetail{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", year='" + year + '\'' +
                ", type='" + type + '\'' +
                ", rating=" + rating +
                ", totalEpisodes=" + totalEpisodes +
                '}';
    }
}
