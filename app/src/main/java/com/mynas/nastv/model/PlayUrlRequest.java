package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 🎮 播放URL请求数据模型
 */
public class PlayUrlRequest {
    @SerializedName("item_guid")
    private String itemGuid;
    @SerializedName("video_guid")
    private String videoGuid;
    @SerializedName("video_encoder")
    private String videoEncoder;
    @SerializedName("resolution")
    private String resolution;
    @SerializedName("bitrate")
    private long bitrate;
    @SerializedName("start_position")
    private long startPosition;
    @SerializedName("audio_guid")
    private String audioGuid;
    @SerializedName("audio_encoder")
    private String audioEncoder;
    @SerializedName("subtitle_guid")
    private String subtitleGuid;
    @SerializedName("play_type")
    private int playType;
    
    public PlayUrlRequest(String itemGuid, String videoGuid, String videoEncoder, 
                         String resolution, long bitrate, long startPosition,
                         String audioGuid, String audioEncoder, String subtitleGuid, int playType) {
        this.itemGuid = itemGuid;
        this.videoGuid = videoGuid;
        this.videoEncoder = videoEncoder;
        this.resolution = resolution;
        this.bitrate = bitrate;
        this.startPosition = startPosition;
        this.audioGuid = audioGuid;
        this.audioEncoder = audioEncoder;
        this.subtitleGuid = subtitleGuid;
        this.playType = playType;
    }
    
    // Getters and Setters
    public String getItemGuid() {
        return itemGuid;
    }
    
    public void setItemGuid(String itemGuid) {
        this.itemGuid = itemGuid;
    }
    
    public String getVideoGuid() {
        return videoGuid;
    }
    
    public void setVideoGuid(String videoGuid) {
        this.videoGuid = videoGuid;
    }
    
    public String getVideoEncoder() {
        return videoEncoder;
    }
    
    public void setVideoEncoder(String videoEncoder) {
        this.videoEncoder = videoEncoder;
    }
    
    public String getResolution() {
        return resolution;
    }
    
    public void setResolution(String resolution) {
        this.resolution = resolution;
    }
    
    public long getBitrate() {
        return bitrate;
    }
    
    public void setBitrate(long bitrate) {
        this.bitrate = bitrate;
    }
    
    public long getStartPosition() {
        return startPosition;
    }
    
    public void setStartPosition(long startPosition) {
        this.startPosition = startPosition;
    }
    
    public String getAudioGuid() {
        return audioGuid;
    }
    
    public void setAudioGuid(String audioGuid) {
        this.audioGuid = audioGuid;
    }
    
    public String getAudioEncoder() {
        return audioEncoder;
    }
    
    public void setAudioEncoder(String audioEncoder) {
        this.audioEncoder = audioEncoder;
    }
    
    public String getSubtitleGuid() {
        return subtitleGuid;
    }
    
    public void setSubtitleGuid(String subtitleGuid) {
        this.subtitleGuid = subtitleGuid;
    }
    
    public int getPlayType() {
        return playType;
    }
    
    public void setPlayType(int playType) {
        this.playType = playType;
    }
}
