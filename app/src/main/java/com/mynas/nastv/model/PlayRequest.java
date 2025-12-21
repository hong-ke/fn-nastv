package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 🎬 播放请求模型
 */
public class PlayRequest {
    
    @SerializedName("media_guid")
    private String mediaGuid;
    
    @SerializedName("video_guid")
    private String videoGuid;
    
    @SerializedName("video_encoder")
    private String videoEncoder;
    
    @SerializedName("resolution")
    private String resolution;
    
    @SerializedName("bitrate")
    private long bitrate;
    
    @SerializedName("startTimestamp")
    private int startTimestamp;
    
    @SerializedName("audio_encoder")
    private String audioEncoder;
    
    @SerializedName("audio_guid")
    private String audioGuid;
    
    @SerializedName("subtitle_guid")
    private String subtitleGuid;
    
    @SerializedName("channels")
    private int channels;
    
    // 构造函数
    public PlayRequest() {
        this.startTimestamp = 0; // 默认从开始播放
    }
    
    public PlayRequest(String mediaGuid, String videoGuid, String audioGuid) {
        this();
        this.mediaGuid = mediaGuid;
        this.videoGuid = videoGuid;
        this.audioGuid = audioGuid;
    }
    
    // Getters and setters
    public String getMediaGuid() {
        return mediaGuid;
    }
    
    public void setMediaGuid(String mediaGuid) {
        this.mediaGuid = mediaGuid;
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
    
    public int getStartTimestamp() {
        return startTimestamp;
    }
    
    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }
    
    public String getAudioEncoder() {
        return audioEncoder;
    }
    
    public void setAudioEncoder(String audioEncoder) {
        this.audioEncoder = audioEncoder;
    }
    
    public String getAudioGuid() {
        return audioGuid;
    }
    
    public void setAudioGuid(String audioGuid) {
        this.audioGuid = audioGuid;
    }
    
    public String getSubtitleGuid() {
        return subtitleGuid;
    }
    
    public void setSubtitleGuid(String subtitleGuid) {
        this.subtitleGuid = subtitleGuid;
    }
    
    public int getChannels() {
        return channels;
    }
    
    public void setChannels(int channels) {
        this.channels = channels;
    }
    
    @Override
    public String toString() {
        return "PlayRequest{" +
                "mediaGuid='" + mediaGuid + '\'' +
                ", videoGuid='" + videoGuid + '\'' +
                ", videoEncoder='" + videoEncoder + '\'' +
                ", resolution='" + resolution + '\'' +
                ", bitrate=" + bitrate +
                ", startTimestamp=" + startTimestamp +
                ", audioEncoder='" + audioEncoder + '\'' +
                ", audioGuid='" + audioGuid + '\'' +
                ", subtitleGuid='" + subtitleGuid + '\'' +
                ", channels=" + channels +
                '}';
    }
}
