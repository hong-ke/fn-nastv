package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * 📚 获取媒体库内容的请求参数
 * 对应Web项目中的POST /api/v1/item/list请求
 */
public class MediaLibraryItemsRequest {
    
    @SerializedName("ancestor_guid")
    private String ancestorGuid;
    
    private Tags tags;
    
    @SerializedName("exclude_grouped_video")
    private int excludeGroupedVideo;
    
    @SerializedName("sort_type")
    private String sortType;
    
    @SerializedName("sort_column")
    private String sortColumn;
    
    @SerializedName("page_size")
    private int pageSize;
    
    // 构造函数
    public MediaLibraryItemsRequest(String ancestorGuid, int pageSize) {
        this.ancestorGuid = ancestorGuid;
        this.tags = new Tags();
        this.excludeGroupedVideo = 1;
        this.sortType = "DESC";
        this.sortColumn = "create_time";
        this.pageSize = pageSize;
    }
    
    // Tags内部类
    public static class Tags {
        private List<String> type;
        
        public Tags() {
            this.type = List.of("Movie", "TV", "Directory", "Video");
        }
        
        public List<String> getType() { return type; }
        public void setType(List<String> type) { this.type = type; }
    }
    
    // Getters and Setters
    public String getAncestorGuid() { return ancestorGuid; }
    public void setAncestorGuid(String ancestorGuid) { this.ancestorGuid = ancestorGuid; }
    
    public Tags getTags() { return tags; }
    public void setTags(Tags tags) { this.tags = tags; }
    
    public int getExcludeGroupedVideo() { return excludeGroupedVideo; }
    public void setExcludeGroupedVideo(int excludeGroupedVideo) { this.excludeGroupedVideo = excludeGroupedVideo; }
    
    public String getSortType() { return sortType; }
    public void setSortType(String sortType) { this.sortType = sortType; }
    
    public String getSortColumn() { return sortColumn; }
    public void setSortColumn(String sortColumn) { this.sortColumn = sortColumn; }
    
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}
