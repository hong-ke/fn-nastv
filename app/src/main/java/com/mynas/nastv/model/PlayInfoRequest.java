package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 🎬 播放信息请求数据模型
 * 对应 /v/api/v1/play/info 接口
 */
public class PlayInfoRequest {
    
    @SerializedName("item_guid")
    private String itemGuid;
    
    /**
     * 随机数，用于防重放攻击
     */
    @SerializedName("nonce")
    private String nonce;
    
    // 构造函数
    public PlayInfoRequest() {
        this.nonce = generateNonce();
    }
    
    public PlayInfoRequest(String itemGuid) {
        this.itemGuid = itemGuid;
        this.nonce = generateNonce();
    }
    
    /**
     * 生成6位随机数
     */
    private String generateNonce() {
        return String.format("%06d", (int)(Math.random() * 900000) + 100000);
    }
    
    // Getters
    public String getItemGuid() { return itemGuid; }
    public String getNonce() { return nonce; }
    
    // Setters
    public void setItemGuid(String itemGuid) { this.itemGuid = itemGuid; }
    public void setNonce(String nonce) { this.nonce = nonce; }
    
    @Override
    public String toString() {
        return "PlayInfoRequest{" +
                "itemGuid='" + itemGuid + '\'' +
                ", nonce='" + nonce + '\'' +
                '}';
    }
}