package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 剧集列表响应模型
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
     * 单个剧集信息
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
        
        @SerializedName("poster")
        private String poster;
        
        @SerializedName("media_stream")
        private MediaStream mediaStream;
        
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
            return poster;
        }
        
        public void setStillPath(String poster) {
            this.poster = poster;
        }
        
        public String getPoster() {
            return poster;
        }
        
        public void setPoster(String poster) {
            this.poster = poster;
        }
        
        public MediaStream getMediaStream() {
            return mediaStream;
        }
        
        public void setMediaStream(MediaStream mediaStream) {
            this.mediaStream = mediaStream;
        }
        
        /**
         * 获取最高清晰度（如 "1080p"）
         */
        public String getResolution() {
            if (mediaStream != null && mediaStream.getResolutions() != null && !mediaStream.getResolutions().isEmpty()) {
                return mediaStream.getResolutions().get(0);
            }
            return null;
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
                    ", poster='" + poster + '\'' +
                    '}';
        }
    }
    
    /**
     * 媒体流信息
     */
    public static class MediaStream {
        @SerializedName("resolutions")
        private List<String> resolutions;
        
        @SerializedName("audio_type")
        private String audioType;
        
        @SerializedName("color_range_type")
        private String colorRangeType;
        
        public List<String> getResolutions() {
            return resolutions;
        }
        
        public void setResolutions(List<String> resolutions) {
            this.resolutions = resolutions;
        }
        
        public String getAudioType() {
            return audioType;
        }
        
        public void setAudioType(String audioType) {
            this.audioType = audioType;
        }
        
        public String getColorRangeType() {
            return colorRangeType;
        }
        
        public void setColorRangeType(String colorRangeType) {
            this.colorRangeType = colorRangeType;
        }
    }
}