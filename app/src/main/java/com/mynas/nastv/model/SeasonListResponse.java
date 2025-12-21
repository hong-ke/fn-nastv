package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 🎬 季列表响应模型
 */
public class SeasonListResponse {
    
    @SerializedName("code")
    private int code;
    
    @SerializedName("message")
    private String message;
    
    @SerializedName("data")
    private List<Season> data;
    
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
    
    public List<Season> getData() {
        return data;
    }
    
    public void setData(List<Season> data) {
        this.data = data;
    }
    
    /**
     * 📺 单个季信息
     */
    public static class Season {
        @SerializedName("guid")
        private String guid;
        
        @SerializedName("season_number")
        private int seasonNumber;
        
        @SerializedName("name")
        private String name;
        
        @SerializedName("overview")
        private String overview;
        
        @SerializedName("air_date")
        private String airDate;
        
        @SerializedName("poster_path")
        private String posterPath;
        
        @SerializedName("episode_count")
        private int episodeCount;
        
        // Getters and setters
        public String getGuid() {
            return guid;
        }
        
        public void setGuid(String guid) {
            this.guid = guid;
        }
        
        public int getSeasonNumber() {
            return seasonNumber;
        }
        
        public void setSeasonNumber(int seasonNumber) {
            this.seasonNumber = seasonNumber;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getOverview() {
            return overview;
        }
        
        public void setOverview(String overview) {
            this.overview = overview;
        }
        
        public String getAirDate() {
            return airDate;
        }
        
        public void setAirDate(String airDate) {
            this.airDate = airDate;
        }
        
        public String getPosterPath() {
            return posterPath;
        }
        
        public void setPosterPath(String posterPath) {
            this.posterPath = posterPath;
        }
        
        public int getEpisodeCount() {
            return episodeCount;
        }
        
        public void setEpisodeCount(int episodeCount) {
            this.episodeCount = episodeCount;
        }
        
        @Override
        public String toString() {
            return "Season{" +
                    "guid='" + guid + '\'' +
                    ", seasonNumber=" + seasonNumber +
                    ", name='" + name + '\'' +
                    ", overview='" + overview + '\'' +
                    ", airDate='" + airDate + '\'' +
                    ", posterPath='" + posterPath + '\'' +
                    ", episodeCount=" + episodeCount +
                    '}';
        }
    }
}