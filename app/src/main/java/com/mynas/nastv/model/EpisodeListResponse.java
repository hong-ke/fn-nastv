package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 🎬 剧集列表响应模型
 */
public class EpisodeListResponse {
    
    @SerializedName("code")
    private int code;
    
    @SerializedName("message")
    private String message;
    
    @SerializedName("data")
    private List<Episode> data;
    
    // Getters and setters
    public int getCode() {
        return code;
    }
    
    public void setCode(int code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public List<Episode> getData() {
        return data;
    }
    
    public void setData(List<Episode> data) {
        this.data = data;
    }
    
    /**
     * 📺 单个剧集信息
     */
    public static class Episode {
        @SerializedName("guid")
        private String guid;
        
        @SerializedName("title")
        private String title;
        
        @SerializedName("episode_number")
        private int episodeNumber;
        
        @SerializedName("season_number")
        private int seasonNumber;
        
        @SerializedName("air_date")
        private String airDate;
        
        @SerializedName("overview")
        private String overview;
        
        @SerializedName("runtime")
        private int runtime;
        
        @SerializedName("still_path")
        private String stillPath;
        
        // Getters and setters
        public String getGuid() {
            return guid;
        }
        
        public void setGuid(String guid) {
            this.guid = guid;
        }
        
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public int getEpisodeNumber() {
            return episodeNumber;
        }
        
        public void setEpisodeNumber(int episodeNumber) {
            this.episodeNumber = episodeNumber;
        }
        
        public int getSeasonNumber() {
            return seasonNumber;
        }
        
        public void setSeasonNumber(int seasonNumber) {
            this.seasonNumber = seasonNumber;
        }
        
        public String getAirDate() {
            return airDate;
        }
        
        public void setAirDate(String airDate) {
            this.airDate = airDate;
        }
        
        public String getOverview() {
            return overview;
        }
        
        public void setOverview(String overview) {
            this.overview = overview;
        }
        
        public int getRuntime() {
            return runtime;
        }
        
        public void setRuntime(int runtime) {
            this.runtime = runtime;
        }
        
        public String getStillPath() {
            return stillPath;
        }
        
        public void setStillPath(String stillPath) {
            this.stillPath = stillPath;
        }
        
        @Override
        public String toString() {
            return "Episode{" +
                    "guid='" + guid + '\'' +
                    ", title='" + title + '\'' +
                    ", episodeNumber=" + episodeNumber +
                    ", seasonNumber=" + seasonNumber +
                    ", airDate='" + airDate + '\'' +
                    ", overview='" + overview + '\'' +
                    ", runtime=" + runtime +
                    ", stillPath='" + stillPath + '\'' +
                    '}';
        }
    }
}