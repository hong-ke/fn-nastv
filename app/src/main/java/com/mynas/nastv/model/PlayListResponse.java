package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 🔄 继续观看列表响应数据模型
 * 匹配 /api/v1/play/list 接口的返回格式
 */
public class PlayListResponse {
    private String msg;
    private int code;
    private List<PlayListItem> data;

    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public List<PlayListItem> getData() { return data; }
    public void setData(List<PlayListItem> data) { this.data = data; }

    /**
     * 继续观看项目信息 - 包含观看进度
     */
    public static class PlayListItem {
        private String guid;
        private String lan;
        @SerializedName("douban_id")
        private long doubanId;
        @SerializedName("imdb_id")
        private String imdbId;
        @SerializedName("tv_title")
        private String tvTitle;
        @SerializedName("parent_guid")
        private String parentGuid;
        @SerializedName("parent_title")
        private String parentTitle;
        private String title;
        private String type; // "Episode", "Movie", "TV"
        private String poster; // Relative URL
        @SerializedName("poster_width")
        private int posterWidth;
        @SerializedName("poster_height")
        private int posterHeight;
        private int runtime; // minutes
        @SerializedName("is_favorite")
        private int isFavorite; // 0 or 1
        private int watched; // 0 or 1
        @SerializedName("watched_ts")
        private long watchedTs; // timestamp
        @SerializedName("season_number")
        private int seasonNumber;
        @SerializedName("episode_number")
        private int episodeNumber;
        @SerializedName("ancestor_guid")
        private String ancestorGuid;
        @SerializedName("ancestor_name")
        private String ancestorName;
        @SerializedName("ancestor_category")
        private String ancestorCategory;
        private long ts; // 已观看时长（秒）
        private long duration; // 总时长（秒）
        @SerializedName("media_guid")
        private String mediaGuid;
        @SerializedName("audio_guid")
        private String audioGuid;
        @SerializedName("video_guid")
        private String videoGuid;
        @SerializedName("subtitle_guid")
        private String subtitleGuid;

        // Getters and Setters
        public String getGuid() { return guid; }
        public void setGuid(String guid) { this.guid = guid; }
        public String getLan() { return lan; }
        public void setLan(String lan) { this.lan = lan; }
        public long getDoubanId() { return doubanId; }
        public void setDoubanId(long doubanId) { this.doubanId = doubanId; }
        public String getImdbId() { return imdbId; }
        public void setImdbId(String imdbId) { this.imdbId = imdbId; }
        public String getTvTitle() { return tvTitle; }
        public void setTvTitle(String tvTitle) { this.tvTitle = tvTitle; }
        public String getParentGuid() { return parentGuid; }
        public void setParentGuid(String parentGuid) { this.parentGuid = parentGuid; }
        public String getParentTitle() { return parentTitle; }
        public void setParentTitle(String parentTitle) { this.parentTitle = parentTitle; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getPoster() { return poster; }
        public void setPoster(String poster) { this.poster = poster; }
        public int getPosterWidth() { return posterWidth; }
        public void setPosterWidth(int posterWidth) { this.posterWidth = posterWidth; }
        public int getPosterHeight() { return posterHeight; }
        public void setPosterHeight(int posterHeight) { this.posterHeight = posterHeight; }
        public int getRuntime() { return runtime; }
        public void setRuntime(int runtime) { this.runtime = runtime; }
        public int getIsFavorite() { return isFavorite; }
        public void setIsFavorite(int isFavorite) { this.isFavorite = isFavorite; }
        public int getWatched() { return watched; }
        public void setWatched(int watched) { this.watched = watched; }
        public long getWatchedTs() { return watchedTs; }
        public void setWatchedTs(long watchedTs) { this.watchedTs = watchedTs; }
        public int getSeasonNumber() { return seasonNumber; }
        public void setSeasonNumber(int seasonNumber) { this.seasonNumber = seasonNumber; }
        public int getEpisodeNumber() { return episodeNumber; }
        public void setEpisodeNumber(int episodeNumber) { this.episodeNumber = episodeNumber; }
        public String getAncestorGuid() { return ancestorGuid; }
        public void setAncestorGuid(String ancestorGuid) { this.ancestorGuid = ancestorGuid; }
        public String getAncestorName() { return ancestorName; }
        public void setAncestorName(String ancestorName) { this.ancestorName = ancestorName; }
        public String getAncestorCategory() { return ancestorCategory; }
        public void setAncestorCategory(String ancestorCategory) { this.ancestorCategory = ancestorCategory; }
        public long getTs() { return ts; }
        public void setTs(long ts) { this.ts = ts; }
        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }
        public String getMediaGuid() { return mediaGuid; }
        public void setMediaGuid(String mediaGuid) { this.mediaGuid = mediaGuid; }
        public String getAudioGuid() { return audioGuid; }
        public void setAudioGuid(String audioGuid) { this.audioGuid = audioGuid; }
        public String getVideoGuid() { return videoGuid; }
        public void setVideoGuid(String videoGuid) { this.videoGuid = videoGuid; }
        public String getSubtitleGuid() { return subtitleGuid; }
        public void setSubtitleGuid(String subtitleGuid) { this.subtitleGuid = subtitleGuid; }
        
        /**
         * 计算观看进度百分比
         */
        public float getWatchProgress() {
            if (duration == 0) return 0;
            return (float) ts / duration * 100;
        }
    }
}
