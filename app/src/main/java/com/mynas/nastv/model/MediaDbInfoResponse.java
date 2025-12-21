package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * 📚 媒体库详细信息响应模型
 */
public class MediaDbInfoResponse {
    
    // 媒体库信息映射，key为category (如 "Movies", "TV Shows")
    private Map<String, MediaDbInfo> info;
    
    @SerializedName("total_count")
    private int totalCount;
    
    @SerializedName("last_updated")
    private String lastUpdated;
    
    // 媒体库信息
    public static class MediaDbInfo {
        private String guid;
        private String title;
        private String category;
        private List<MediaItem> list;
        
        @SerializedName("item_count")
        private int itemCount;
        
        @SerializedName("last_scan")
        private String lastScan;
        
        @SerializedName("scan_status")
        private String scanStatus;
        
        // Getters and Setters
        public String getGuid() { return guid; }
        public void setGuid(String guid) { this.guid = guid; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public List<MediaItem> getList() { return list; }
        public void setList(List<MediaItem> list) { this.list = list; }
        
        public int getItemCount() { return itemCount; }
        public void setItemCount(int itemCount) { this.itemCount = itemCount; }
        
        public String getLastScan() { return lastScan; }
        public void setLastScan(String lastScan) { this.lastScan = lastScan; }
        
        public String getScanStatus() { return scanStatus; }
        public void setScanStatus(String scanStatus) { this.scanStatus = scanStatus; }
    }
    
    // Getters and Setters
    public Map<String, MediaDbInfo> getInfo() { return info; }
    public void setInfo(Map<String, MediaDbInfo> info) { this.info = info; }
    
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    
    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
    
    /**
     * 获取指定分类的媒体信息
     */
    public MediaDbInfo getMediaInfoByCategory(String category) {
        if (info != null) {
            return info.get(category);
        }
        return null;
    }
    
    /**
     * 是否有数据
     */
    public boolean hasData() {
        return info != null && !info.isEmpty();
    }
}
