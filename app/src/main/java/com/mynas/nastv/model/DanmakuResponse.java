package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 🌐 弹幕响应模型
 */
public class DanmakuResponse {
    @SerializedName("episode_guid")
    private String episodeGuid;
    
    private List<DanmakuItem> danmaku;
    
    @SerializedName("total_count")
    private int totalCount;
    
    @SerializedName("max_time")
    private long maxTime; // 最大时间点
    
    @SerializedName("danmaku_config")
    private DanmakuConfig config;
    
    // 弹幕配置信息
    public static class DanmakuConfig {
        @SerializedName("max_count")
        private int maxCount; // 最大同屏弹幕数
        
        @SerializedName("speed_factor")
        private float speedFactor; // 速度系数
        
        @SerializedName("opacity")
        private int opacity; // 透明度 (0-255)
        
        @SerializedName("font_size")
        private int fontSize; // 字体大小
        
        @SerializedName("show_types")
        private List<String> showTypes; // 显示的弹幕类型
        
        // Getters and Setters
        public int getMaxCount() { return maxCount; }
        public void setMaxCount(int maxCount) { this.maxCount = maxCount; }
        
        public float getSpeedFactor() { return speedFactor; }
        public void setSpeedFactor(float speedFactor) { this.speedFactor = speedFactor; }
        
        public int getOpacity() { return opacity; }
        public void setOpacity(int opacity) { this.opacity = opacity; }
        
        public int getFontSize() { return fontSize; }
        public void setFontSize(int fontSize) { this.fontSize = fontSize; }
        
        public List<String> getShowTypes() { return showTypes; }
        public void setShowTypes(List<String> showTypes) { this.showTypes = showTypes; }
    }
    
    // Getters and Setters
    public String getEpisodeGuid() { return episodeGuid; }
    public void setEpisodeGuid(String episodeGuid) { this.episodeGuid = episodeGuid; }
    
    public List<DanmakuItem> getDanmaku() { return danmaku; }
    public void setDanmaku(List<DanmakuItem> danmaku) { this.danmaku = danmaku; }
    
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    
    public long getMaxTime() { return maxTime; }
    public void setMaxTime(long maxTime) { this.maxTime = maxTime; }
    
    public DanmakuConfig getConfig() { return config; }
    public void setConfig(DanmakuConfig config) { this.config = config; }
    
    /**
     * 是否有弹幕数据
     */
    public boolean hasDanmaku() {
        return danmaku != null && !danmaku.isEmpty();
    }
    
    /**
     * 获取指定时间范围内的弹幕
     */
    public List<DanmakuItem> getDanmakuByTimeRange(long startTime, long endTime) {
        if (danmaku == null) return null;
        
        return danmaku.stream()
                .filter(item -> item.getTimeSeconds() >= startTime && item.getTimeSeconds() <= endTime)
                .collect(java.util.stream.Collectors.toList());
    }
}
