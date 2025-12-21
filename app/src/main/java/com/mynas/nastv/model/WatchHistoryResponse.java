package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 📖 观看历史响应模型
 */
public class WatchHistoryResponse {
    private List<WatchHistoryItem> history;
    
    @SerializedName("total_count")
    private int totalCount;
    
    @SerializedName("current_page")
    private int currentPage;
    
    @SerializedName("total_pages")
    private int totalPages;
    
    @SerializedName("page_size")
    private int pageSize;
    
    @SerializedName("has_more")
    private boolean hasMore;
    
    // 观看历史项
    public static class WatchHistoryItem {
        @SerializedName("item_guid")
        private String itemGuid;
        
        @SerializedName("episode_guid")
        private String episodeGuid;
        
        @SerializedName("item_title")
        private String itemTitle;
        
        @SerializedName("episode_title")
        private String episodeTitle;
        
        @SerializedName("episode_number")
        private int episodeNumber;
        
        @SerializedName("watched_ts")
        private long watchedTimestamp;
        
        @SerializedName("total_ts")
        private long totalTimestamp;
        
        @SerializedName("watched_progress")
        private float watchedProgress;
        
        @SerializedName("watch_date")
        private String watchDate;
        
        @SerializedName("last_watch_time")
        private String lastWatchTime;
        
        @SerializedName("poster_url")
        private String posterUrl;
        
        @SerializedName("item_type")
        private String itemType;
        
        private String quality;
        
        // Getters and Setters
        public String getItemGuid() { return itemGuid; }
        public void setItemGuid(String itemGuid) { this.itemGuid = itemGuid; }
        
        public String getEpisodeGuid() { return episodeGuid; }
        public void setEpisodeGuid(String episodeGuid) { this.episodeGuid = episodeGuid; }
        
        public String getItemTitle() { return itemTitle; }
        public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }
        
        public String getEpisodeTitle() { return episodeTitle; }
        public void setEpisodeTitle(String episodeTitle) { this.episodeTitle = episodeTitle; }
        
        public int getEpisodeNumber() { return episodeNumber; }
        public void setEpisodeNumber(int episodeNumber) { this.episodeNumber = episodeNumber; }
        
        public long getWatchedTimestamp() { return watchedTimestamp; }
        public void setWatchedTimestamp(long watchedTimestamp) { this.watchedTimestamp = watchedTimestamp; }
        
        public long getTotalTimestamp() { return totalTimestamp; }
        public void setTotalTimestamp(long totalTimestamp) { this.totalTimestamp = totalTimestamp; }
        
        public float getWatchedProgress() { return watchedProgress; }
        public void setWatchedProgress(float watchedProgress) { this.watchedProgress = watchedProgress; }
        
        public String getWatchDate() { return watchDate; }
        public void setWatchDate(String watchDate) { this.watchDate = watchDate; }
        
        public String getLastWatchTime() { return lastWatchTime; }
        public void setLastWatchTime(String lastWatchTime) { this.lastWatchTime = lastWatchTime; }
        
        public String getPosterUrl() { return posterUrl; }
        public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }
        
        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }
        
        public String getQuality() { return quality; }
        public void setQuality(String quality) { this.quality = quality; }
        
        /**
         * 获取显示标题
         */
        public String getDisplayTitle() {
            if (episodeTitle != null && !episodeTitle.isEmpty()) {
                return String.format("%s - %s", itemTitle, episodeTitle);
            } else if (episodeNumber > 0) {
                return String.format("%s - 第%d集", itemTitle, episodeNumber);
            } else {
                return itemTitle;
            }
        }
        
        /**
         * 获取观看进度文本
         */
        public String getProgressText() {
            if (watchedProgress >= 95) {
                return "已观看";
            } else if (watchedProgress > 0) {
                return String.format("%.0f%%", watchedProgress);
            } else {
                return "未开始";
            }
        }
        
        /**
         * 是否可以继续观看
         */
        public boolean canContinueWatching() {
            return watchedProgress > 0 && watchedProgress < 95;
        }
    }
    
    // Getters and Setters
    public List<WatchHistoryItem> getHistory() { return history; }
    public void setHistory(List<WatchHistoryItem> history) { this.history = history; }
    
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    
    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
    
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    
    public boolean hasMore() { return hasMore; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
    
    /**
     * 是否为空历史
     */
    public boolean isEmpty() {
        return history == null || history.isEmpty();
    }
}
