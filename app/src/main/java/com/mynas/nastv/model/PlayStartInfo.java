package com.mynas.nastv.model;

/**
 * 播放启动信息
 * 包含播放URL和恢复位置等信息
 */
public class PlayStartInfo {
    
    private String playUrl;
    private long resumePositionMs; // ts 值，恢复播放位置（毫秒）
    private String mediaGuid;
    private String videoGuid;
    private String audioGuid;
    private String subtitleGuid;
    
    public PlayStartInfo(String playUrl, long resumePositionMs) {
        this.playUrl = playUrl;
        this.resumePositionMs = resumePositionMs;
    }
    
    // Getters
    public String getPlayUrl() { return playUrl; }
    /**
     * @deprecated 使用 getResumePositionMs() 代替，ts 值实际是毫秒
     */
    @Deprecated
    public long getResumePositionSeconds() { return resumePositionMs; }
    public long getResumePositionMs() { return resumePositionMs; }
    public String getMediaGuid() { return mediaGuid; }
    public String getVideoGuid() { return videoGuid; }
    public String getAudioGuid() { return audioGuid; }
    public String getSubtitleGuid() { return subtitleGuid; }
    
    // Setters
    public void setPlayUrl(String playUrl) { this.playUrl = playUrl; }
    public void setResumePositionMs(long resumePositionMs) { this.resumePositionMs = resumePositionMs; }
    public void setMediaGuid(String mediaGuid) { this.mediaGuid = mediaGuid; }
    public void setVideoGuid(String videoGuid) { this.videoGuid = videoGuid; }
    public void setAudioGuid(String audioGuid) { this.audioGuid = audioGuid; }
    public void setSubtitleGuid(String subtitleGuid) { this.subtitleGuid = subtitleGuid; }
    
    @Override
    public String toString() {
        return "PlayStartInfo{" +
                "playUrl='" + playUrl + '\'' +
                ", resumePositionMs=" + resumePositionMs +
                ", mediaGuid='" + mediaGuid + '\'' +
                ", videoGuid='" + videoGuid + '\'' +
                '}';
    }
}
