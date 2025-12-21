package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 🎮 播放URL响应数据模型
 */
public class PlayUrlResponse {
    @SerializedName("play_link")
    private String playLink;
    @SerializedName("content_type")
    private String contentType;
    @SerializedName("duration")
    private long duration;
    @SerializedName("file_size")
    private long fileSize;
    
    // Getters and Setters
    public String getPlayLink() {
        return playLink;
    }
    
    public void setPlayLink(String playLink) {
        this.playLink = playLink;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}
