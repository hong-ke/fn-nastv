package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 📖 播放记录请求模型
 */
public class PlayRecordRequest {
    @SerializedName("item_guid")
    private String itemGuid;
    
    @SerializedName("episode_guid")
    private String episodeGuid;
    
    @SerializedName("watched_ts")
    private long watchedTimestamp; // 观看位置 (秒)
    
    @SerializedName("total_ts")
    private long totalTimestamp; // 总时长 (秒)
    
    @SerializedName("watched_progress")
    private float watchedProgress; // 观看进度 (0-100)
    
    @SerializedName("session_id")
    private String sessionId;
    
    @SerializedName("device_id")
    private String deviceId;
    
    @SerializedName("play_method")
    private String playMethod; // "direct", "transcode"
    
    private String quality; // 播放画质
    private String codec; // 使用的编码
    
    @SerializedName("watch_date")
    private String watchDate; // 观看日期
    
    // 构造函数
    public PlayRecordRequest() {}
    
    public PlayRecordRequest(String itemGuid, String episodeGuid, long watchedTimestamp, long totalTimestamp) {
        this.itemGuid = itemGuid;
        this.episodeGuid = episodeGuid;
        this.watchedTimestamp = watchedTimestamp;
        this.totalTimestamp = totalTimestamp;
        
        // 计算观看进度
        if (totalTimestamp > 0) {
            this.watchedProgress = (float) watchedTimestamp / totalTimestamp * 100;
        }
    }
    
    // Getters and Setters
    public String getItemGuid() { return itemGuid; }
    public void setItemGuid(String itemGuid) { this.itemGuid = itemGuid; }
    
    public String getEpisodeGuid() { return episodeGuid; }
    public void setEpisodeGuid(String episodeGuid) { this.episodeGuid = episodeGuid; }
    
    public long getWatchedTimestamp() { return watchedTimestamp; }
    public void setWatchedTimestamp(long watchedTimestamp) { 
        this.watchedTimestamp = watchedTimestamp;
        // 自动更新观看进度
        if (totalTimestamp > 0) {
            this.watchedProgress = (float) watchedTimestamp / totalTimestamp * 100;
        }
    }
    
    public long getTotalTimestamp() { return totalTimestamp; }
    public void setTotalTimestamp(long totalTimestamp) { this.totalTimestamp = totalTimestamp; }
    
    public float getWatchedProgress() { return watchedProgress; }
    public void setWatchedProgress(float watchedProgress) { this.watchedProgress = watchedProgress; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public String getPlayMethod() { return playMethod; }
    public void setPlayMethod(String playMethod) { this.playMethod = playMethod; }
    
    public String getQuality() { return quality; }
    public void setQuality(String quality) { this.quality = quality; }
    
    public String getCodec() { return codec; }
    public void setCodec(String codec) { this.codec = codec; }
    
    public String getWatchDate() { return watchDate; }
    public void setWatchDate(String watchDate) { this.watchDate = watchDate; }
}
