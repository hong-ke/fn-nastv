package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 🎬 媒体项目列表响应数据模型
 * 匹配 /api/v1/item/list 接口的返回格式
 */
public class MediaItemListResponse {
    private String msg;
    private int code;
    private MediaItemData data;
    
    /**
     * 媒体项目数据容器
     */
    public static class MediaItemData {
        private int total;
        private List<MediaItemInfo> list;
        
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public List<MediaItemInfo> getList() { return list; }
        public void setList(List<MediaItemInfo> list) { this.list = list; }
    }
    
    // 基础响应字段的getters
    public String getMessage() { return msg; }
    public void setMessage(String msg) { this.msg = msg; }
    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public MediaItemData getData() { return data; }
    public void setData(MediaItemData data) { this.data = data; }
    
    /**
     * 媒体项目信息 - 匹配实际API返回格式
     */
    public static class MediaItemInfo {
        private String guid;
        private String lan;
        
        @SerializedName("douban_id")
        private long doubanId;
        
        @SerializedName("imdb_id") 
        private String imdbId;
        
        @SerializedName("trim_id")
        private String trimId;
        
        @SerializedName("tv_title")
        private String tvTitle;
        
        @SerializedName("parent_guid")
        private String parentGuid;
        
        @SerializedName("parent_title")
        private String parentTitle;
        
        private String title;
        private String type;
        private String poster;
        
        @SerializedName("poster_width")
        private int posterWidth;
        
        @SerializedName("poster_height")
        private int posterHeight;
        
        private int runtime;
        
        @SerializedName("is_favorite")
        private int isFavorite;
        
        private int watched;
        
        @SerializedName("watched_ts")
        private long watchedTs;
        
        @SerializedName("vote_average")
        private String voteAverage;
        
        @SerializedName("season_number")
        private int seasonNumber;
        
        @SerializedName("episode_number")
        private int episodeNumber;
        
        @SerializedName("air_date")
        private String airDate;
        
        @SerializedName("number_of_seasons")
        private int numberOfSeasons;
        
        @SerializedName("number_of_episodes")
        private int numberOfEpisodes;
        
        private String status;
        private String overview;
        
        @SerializedName("ancestor_guid")
        private String ancestorGuid;
        
        @SerializedName("ancestor_name")
        private String ancestorName;
        
        @SerializedName("ancestor_category")
        private String ancestorCategory;
        
        private long ts;
        private int duration;
        
        @SerializedName("video_guid")
        private String videoGuid;
        
        @SerializedName("file_name")
        private String fileName;
        
        // Getters
        public String getGuid() { return guid; }
        public String getLan() { return lan; }
        public long getDoubanId() { return doubanId; }
        public String getImdbId() { return imdbId; }
        public String getTrimId() { return trimId; }
        public String getTvTitle() { return tvTitle; }
        public String getParentGuid() { return parentGuid; }
        public String getParentTitle() { return parentTitle; }
        public String getTitle() { return title; }
        public String getType() { return type; }
        public String getPoster() { return poster; }
        public int getPosterWidth() { return posterWidth; }
        public int getPosterHeight() { return posterHeight; }
        public int getRuntime() { return runtime; }
        public int getIsFavorite() { return isFavorite; }
        public int getWatched() { return watched; }
        public long getWatchedTs() { return watchedTs; }
        public String getVoteAverage() { return voteAverage; }
        public int getSeasonNumber() { return seasonNumber; }
        public int getEpisodeNumber() { return episodeNumber; }
        public String getAirDate() { return airDate; }
        public int getNumberOfSeasons() { return numberOfSeasons; }
        public int getNumberOfEpisodes() { return numberOfEpisodes; }
        public String getStatus() { return status; }
        public String getOverview() { return overview; }
        public String getAncestorGuid() { return ancestorGuid; }
        public String getAncestorName() { return ancestorName; }
        public String getAncestorCategory() { return ancestorCategory; }
        public long getTs() { return ts; }
        public int getDuration() { return duration; }
        public String getVideoGuid() { return videoGuid; }
        public String getFileName() { return fileName; }
    }
}
