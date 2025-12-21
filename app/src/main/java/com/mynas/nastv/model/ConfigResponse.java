package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * ⚙️ 系统配置响应数据模型
 */
public class ConfigResponse {
    @SerializedName("server_name")
    private String serverName;
    @SerializedName("server_version")
    private String serverVersion;
    @SerializedName("api_version")
    private String apiVersion;
    @SerializedName("max_upload_size")
    private long maxUploadSize;
    @SerializedName("danmaku_enabled")
    private boolean danmakuEnabled;
    @SerializedName("transcode_enabled")
    private boolean transcodeEnabled;
    
    // Getters and Setters
    public String getServerName() {
        return serverName;
    }
    
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
    
    public String getServerVersion() {
        return serverVersion;
    }
    
    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }
    
    public String getApiVersion() {
        return apiVersion;
    }
    
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
    
    public long getMaxUploadSize() {
        return maxUploadSize;
    }
    
    public void setMaxUploadSize(long maxUploadSize) {
        this.maxUploadSize = maxUploadSize;
    }
    
    public boolean isDanmakuEnabled() {
        return danmakuEnabled;
    }
    
    public void setDanmakuEnabled(boolean danmakuEnabled) {
        this.danmakuEnabled = danmakuEnabled;
    }
    
    public boolean isTranscodeEnabled() {
        return transcodeEnabled;
    }
    
    public void setTranscodeEnabled(boolean transcodeEnabled) {
        this.transcodeEnabled = transcodeEnabled;
    }
}
