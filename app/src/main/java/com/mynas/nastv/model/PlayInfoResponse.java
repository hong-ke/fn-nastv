package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * 播放信息响应数据模型
 * 根据实际API响应结构定义
 */
public class PlayInfoResponse {
    
    @SerializedName("code")
    private int code;
    
    @SerializedName("msg")
    private String message;
    
    @SerializedName("data")
    private PlayInfoData data;
    
    // Getters
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public PlayInfoData getData() { return data; }
    
    // Setters
    public void setCode(int code) { this.code = code; }
    public void setMessage(String message) { this.message = message; }
    public void setData(PlayInfoData data) { this.data = data; }
    
    /**
     * 播放信息数据内容
     * 基于实际API响应结构: {"guid":"...", "parent_guid":"...", "media_guid":"...", ...}
     */
    public static class PlayInfoData {
        
        @SerializedName("guid")
        private String guid;
        
        @SerializedName("parent_guid")
        private String parentGuid;
        
        @SerializedName("media_guid")
        private String mediaGuid;
        
        @SerializedName("video_guid")
        private String videoGuid;
        
        @SerializedName("audio_guid")
        private String audioGuid;
        
        @SerializedName("subtitle_guid")
        private String subtitleGuid;
        
        @SerializedName("type")
        private String type;
        
        @SerializedName("ts")
        private int ts;
        
        @SerializedName("grand_guid")
        private String grandGuid;
        
        @SerializedName("play_config")
        private Map<String, Object> playConfig;
        
        @SerializedName("item")
        private Map<String, Object> item;
        
        // 支持play_link字段（如果存在）
        @SerializedName("play_link")
        private String playLink;
        
        // Getters
        public String getGuid() { return guid; }
        public String getParentGuid() { return parentGuid; }
        public String getMediaGuid() { return mediaGuid; }
        public String getVideoGuid() { return videoGuid; }
        public String getAudioGuid() { return audioGuid; }
        public String getSubtitleGuid() { return subtitleGuid; }
        public String getType() { return type; }
        public int getTs() { return ts; }
        public String getGrandGuid() { return grandGuid; }
        public Map<String, Object> getPlayConfig() { return playConfig; }
        public Map<String, Object> getItem() { return item; }
        public String getPlayLink() { return playLink; }
        
        // Setters
        public void setGuid(String guid) { this.guid = guid; }
        public void setParentGuid(String parentGuid) { this.parentGuid = parentGuid; }
        public void setMediaGuid(String mediaGuid) { this.mediaGuid = mediaGuid; }
        public void setVideoGuid(String videoGuid) { this.videoGuid = videoGuid; }
        public void setAudioGuid(String audioGuid) { this.audioGuid = audioGuid; }
        public void setSubtitleGuid(String subtitleGuid) { this.subtitleGuid = subtitleGuid; }
        public void setType(String type) { this.type = type; }
        public void setTs(int ts) { this.ts = ts; }
        public void setGrandGuid(String grandGuid) { this.grandGuid = grandGuid; }
        public void setPlayConfig(Map<String, Object> playConfig) { this.playConfig = playConfig; }
        public void setItem(Map<String, Object> item) { this.item = item; }
        public void setPlayLink(String playLink) { this.playLink = playLink; }
        
        @Override
        public String toString() {
            return "PlayInfoData{" +
                    "guid='" + guid + '\'' +
                    ", parentGuid='" + parentGuid + '\'' +
                    ", mediaGuid='" + mediaGuid + '\'' +
                    ", videoGuid='" + videoGuid + '\'' +
                    ", audioGuid='" + audioGuid + '\'' +
                    ", type='" + type + '\'' +
                    ", playLink='" + playLink + '\'' +
                    '}';
        }
    }
}