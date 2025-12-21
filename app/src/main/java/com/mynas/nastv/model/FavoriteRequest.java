package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * ⭐ 收藏请求模型
 */
public class FavoriteRequest {
    @SerializedName("item_guid")
    private String itemGuid;
    
    @SerializedName("item_type")
    private String itemType; // "movie", "tv", "anime", "episode"
    
    @SerializedName("item_title")
    private String itemTitle;
    
    @SerializedName("folder_name")
    private String folderName; // 收藏夹名称，可选
    
    private String note; // 收藏备注，可选
    
    // 构造函数
    public FavoriteRequest() {}
    
    public FavoriteRequest(String itemGuid, String itemType) {
        this.itemGuid = itemGuid;
        this.itemType = itemType;
    }
    
    public FavoriteRequest(String itemGuid, String itemType, String itemTitle) {
        this.itemGuid = itemGuid;
        this.itemType = itemType;
        this.itemTitle = itemTitle;
    }
    
    // Getters and Setters
    public String getItemGuid() { return itemGuid; }
    public void setItemGuid(String itemGuid) { this.itemGuid = itemGuid; }
    
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    
    public String getItemTitle() { return itemTitle; }
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }
    
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
