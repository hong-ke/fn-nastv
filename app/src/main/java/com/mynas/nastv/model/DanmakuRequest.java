package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 🌐 弹幕发送请求模型
 */
public class DanmakuRequest {
    @SerializedName("episode_guid")
    private String episodeGuid;
    
    private String text; // 弹幕内容
    private long time; // 弹幕时间点 (毫秒)
    private String type; // 弹幕类型: "scroll", "top", "bottom"
    private String color; // 弹幕颜色 (十六进制，如 "#FFFFFF")
    private int size; // 弹幕大小 (1-3: 小中大)
    
    @SerializedName("user_hash")
    private String userHash; // 用户哈希（匿名标识）
    
    @SerializedName("device_id")
    private String deviceId; // 设备标识
    
    @SerializedName("client_type")
    private String clientType; // 客户端类型 "android_tv"
    
    // 构造函数
    public DanmakuRequest() {}
    
    public DanmakuRequest(String episodeGuid, String text, long time) {
        this.episodeGuid = episodeGuid;
        this.text = text;
        this.time = time;
        this.type = "scroll"; // 默认滚动弹幕
        this.color = "#FFFFFF"; // 默认白色
        this.size = 2; // 默认中等大小
        this.clientType = "android_tv";
    }
    
    public DanmakuRequest(String episodeGuid, String text, long time, String type, String color) {
        this.episodeGuid = episodeGuid;
        this.text = text;
        this.time = time;
        this.type = type;
        this.color = color;
        this.size = 2;
        this.clientType = "android_tv";
    }
    
    // Getters and Setters
    public String getEpisodeGuid() { return episodeGuid; }
    public void setEpisodeGuid(String episodeGuid) { this.episodeGuid = episodeGuid; }
    
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public long getTime() { return time; }
    public void setTime(long time) { this.time = time; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    
    public String getUserHash() { return userHash; }
    public void setUserHash(String userHash) { this.userHash = userHash; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public String getClientType() { return clientType; }
    public void setClientType(String clientType) { this.clientType = clientType; }
    
    /**
     * 验证弹幕内容是否有效
     */
    public boolean isValid() {
        return text != null && !text.trim().isEmpty() && 
               text.length() <= 100 && // 限制弹幕长度
               episodeGuid != null && !episodeGuid.isEmpty() &&
               time >= 0;
    }
    
    /**
     * 获取弹幕类型的显示名称
     */
    public String getTypeDisplayName() {
        switch (type != null ? type : "scroll") {
            case "scroll":
                return "滚动";
            case "top":
                return "顶部";
            case "bottom":
                return "底部";
            default:
                return "滚动";
        }
    }
}
