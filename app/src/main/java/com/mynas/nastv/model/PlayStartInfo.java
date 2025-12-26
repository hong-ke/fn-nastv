package com.mynas.nastv.model;

/**
 * 播放启动信息
 * 包含播放URL和恢复位置等信息
 */
public class PlayStartInfo {
    
    private String playUrl;
    private long resumePositionSeconds; // ts 值，恢复播放位置（秒）
    private String mediaGuid;
    private String videoGuid;
    private String audioGuid;
    private String subtitleGuid;
    
    public PlayStartInfo(String playUrl, long resumePositionSeconds) {
        this.playUrl = playUrl;
        this.resumePositionSeconds = resumePositionSeconds;
    }
    
    // Getters
    public String getPlayUrl() { return playUrl; }
    public long getResumePositionSeconds() { return resumePositionSeconds; }
    public String getMediaGuid() { return mediaGuid; }
    public String getVideoGuid() { return videoGuid; }
    public String getAudioGuid() { return audioGuid; }
    public String getSubtitleGuid() { return subtitleGuid; }
    
    // Setters
    public void setPlayUrl(String playUrl) { this.playUrl = playUrl; }
    public void setResumePositionSeconds(long resumePositionSeconds) { this.resumePositionSeconds = resumePositionSeconds; }
    public void setMediaGuid(String mediaGuid) { this.mediaGuid = mediaGuid; }
    public void setVideoGuid(String videoGuid) { this.videoGuid = videoGuid; }
    public void setAudioGuid(String audioGuid) { this.audioGuid = audioGuid; }
    public void setSubtitleGuid(String subtitleGuid) { this.subtitleGuid = subtitleGuid; }
    
    @Override
    public String toString() {
        return "PlayStartInfo{" +
                "playUrl='" + playUrl + '\'' +
                ", resumePositionSeconds=" + resumePositionSeconds +
                ", mediaGuid='" + mediaGuid + '\'' +
                ", videoGuid='" + videoGuid + '\'' +
                '}';
    }
}
