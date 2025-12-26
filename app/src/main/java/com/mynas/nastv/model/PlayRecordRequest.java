package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 播放记录请求模型
 * 对应 /v/api/v1/play/record 接口
 * 与 fntv-electron 的 PlayStatusData 保持一致
 */
public class PlayRecordRequest {
    
    /**
     * 视频项目的唯一标识符
     */
    @SerializedName("item_guid")
    private String itemGuid;
    
    /**
     * 媒体文件的唯一标识符
     */
    @SerializedName("media_guid")
    private String mediaGuid;
    
    /**
     * 视频流的唯一标识符
     */
    @SerializedName("video_guid")
    private String videoGuid;
    
    /**
     * 音频流的唯一标识符
     */
    @SerializedName("audio_guid")
    private String audioGuid;
    
    /**
     * 字幕流的唯一标识符
     */
    @SerializedName("subtitle_guid")
    private String subtitleGuid;
    
    /**
     * 播放链接 (可为空)
     */
    @SerializedName("play_link")
    private String playLink;
    
    /**
     * 播放进度时间戳 (秒)
     */
    @SerializedName("ts")
    private long ts;
    
    /**
     * 视频总时长 (秒)
     */
    @SerializedName("duration")
    private long duration;
    
    /**
     * 分辨率 (如 "超清", "高清", "标清")
     */
    @SerializedName("resolution")
    private String resolution;
    
    /**
     * 码率 (bps)
     */
    @SerializedName("bitrate")
    private long bitrate;
    
    /**
     * 随机数，用于防重放攻击
     */
    @SerializedName("nonce")
    private String nonce;
    
    // 兼容旧字段
    @SerializedName("episode_guid")
    private String episodeGuid;
    
    @SerializedName("watched_ts")
    private long watchedTimestamp;
    
    @SerializedName("total_ts")
    private long totalTimestamp;
    
    // 构造函数
    public PlayRecordRequest() {
        this.nonce = generateNonce();
        this.playLink = "";
        this.subtitleGuid = "no_display";
    }
    
    /**
     * 新版构造函数 (与 fntv-electron 一致)
     */
    public PlayRecordRequest(String itemGuid, String mediaGuid, String videoGuid, 
                            String audioGuid, String subtitleGuid, long ts, long duration) {
        this.itemGuid = itemGuid;
        this.mediaGuid = mediaGuid;
        this.videoGuid = videoGuid;
        this.audioGuid = audioGuid;
        this.subtitleGuid = subtitleGuid != null ? subtitleGuid : "no_display";
        this.playLink = "";
        this.ts = ts;
        this.duration = duration;
        this.nonce = generateNonce();
    }
    
    /**
     * 旧版构造函数 (兼容)
     */
    public PlayRecordRequest(String itemGuid, String episodeGuid, long watchedTimestamp, long totalTimestamp) {
        this.itemGuid = itemGuid;
        this.episodeGuid = episodeGuid;
        this.watchedTimestamp = watchedTimestamp;
        this.totalTimestamp = totalTimestamp;
        this.ts = watchedTimestamp;
        this.duration = totalTimestamp;
        this.nonce = generateNonce();
        this.playLink = "";
        this.subtitleGuid = "no_display";
    }
    
    /**
     * 生成6位随机数
     */
    private String generateNonce() {
        return String.format("%06d", (int)(Math.random() * 900000) + 100000);
    }
    
    // Getters and Setters
    public String getItemGuid() { return itemGuid; }
    public void setItemGuid(String itemGuid) { this.itemGuid = itemGuid; }
    
    public String getMediaGuid() { return mediaGuid; }
    public void setMediaGuid(String mediaGuid) { this.mediaGuid = mediaGuid; }
    
    public String getVideoGuid() { return videoGuid; }
    public void setVideoGuid(String videoGuid) { this.videoGuid = videoGuid; }
    
    public String getAudioGuid() { return audioGuid; }
    public void setAudioGuid(String audioGuid) { this.audioGuid = audioGuid; }
    
    public String getSubtitleGuid() { return subtitleGuid; }
    public void setSubtitleGuid(String subtitleGuid) { this.subtitleGuid = subtitleGuid; }
    
    public String getPlayLink() { return playLink; }
    public void setPlayLink(String playLink) { this.playLink = playLink; }
    
    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }
    
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    
    public long getBitrate() { return bitrate; }
    public void setBitrate(long bitrate) { this.bitrate = bitrate; }
    
    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
    
    // 兼容旧方法
    public String getEpisodeGuid() { return episodeGuid; }
    public void setEpisodeGuid(String episodeGuid) { this.episodeGuid = episodeGuid; }
    
    public long getWatchedTimestamp() { return watchedTimestamp; }
    public void setWatchedTimestamp(long watchedTimestamp) { 
        this.watchedTimestamp = watchedTimestamp;
        this.ts = watchedTimestamp;
    }
    
    public long getTotalTimestamp() { return totalTimestamp; }
    public void setTotalTimestamp(long totalTimestamp) { 
        this.totalTimestamp = totalTimestamp;
        this.duration = totalTimestamp;
    }
}
