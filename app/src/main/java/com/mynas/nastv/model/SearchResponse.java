package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 🔍 搜索响应模型
 */
public class SearchResponse {
    private List<MediaItem> results;
    
    @SerializedName("total_count")
    private int totalCount;
    
    @SerializedName("current_page")
    private int currentPage;
    
    @SerializedName("total_pages")
    private int totalPages;
    
    @SerializedName("page_size")
    private int pageSize;
    
    private String keyword;
    private String type;
    
    @SerializedName("search_time")
    private long searchTime; // 搜索耗时 (毫秒)
    
    @SerializedName("has_more")
    private boolean hasMore;
    
    // 搜索建议
    private List<String> suggestions;
    
    // 相关搜索
    @SerializedName("related_searches")
    private List<String> relatedSearches;
    
    // Getters and Setters
    public List<MediaItem> getResults() { return results; }
    public void setResults(List<MediaItem> results) { this.results = results; }
    
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    
    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
    
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public long getSearchTime() { return searchTime; }
    public void setSearchTime(long searchTime) { this.searchTime = searchTime; }
    
    public boolean hasMore() { return hasMore; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
    
    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
    
    public List<String> getRelatedSearches() { return relatedSearches; }
    public void setRelatedSearches(List<String> relatedSearches) { this.relatedSearches = relatedSearches; }
    
    /**
     * 是否为空结果
     */
    public boolean isEmpty() {
        return results == null || results.isEmpty();
    }
    
    /**
     * 获取格式化的搜索结果统计
     */
    public String getResultStatsText() {
        if (totalCount == 0) {
            return "未找到相关内容";
        } else if (totalCount == 1) {
            return "找到 1 个结果";
        } else {
            return String.format("找到 %d 个结果", totalCount);
        }
    }
    
    /**
     * 获取格式化的搜索时间
     */
    public String getSearchTimeText() {
        if (searchTime > 0) {
            return String.format("搜索耗时 %d 毫秒", searchTime);
        }
        return "";
    }
}
