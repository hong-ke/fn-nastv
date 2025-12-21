package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 🎬 播放信息请求数据模型
 */
public class PlayInfoRequest {
    
    @SerializedName("item_guid")
    private String itemGuid;
    
    // 构造函数
    public PlayInfoRequest() {}
    
    public PlayInfoRequest(String itemGuid) {
        this.itemGuid = itemGuid;
    }
    
    // Getters
    public String getItemGuid() { return itemGuid; }
    
    // Setters
    public void setItemGuid(String itemGuid) { this.itemGuid = itemGuid; }
    
    @Override
    public String toString() {
        return "PlayInfoRequest{" +
                "itemGuid='" + itemGuid + '\'' +
                '}';
    }
}