package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 👤 用户信息响应模型
 */
public class UserInfoResponse {
    private String username;
    private String name;
    private String nickname;
    @SerializedName("user_name")
    private String userName;
    private String email;
    private String avatar;
    @SerializedName("user_id")
    private String userId;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("last_login")
    private String lastLogin;
    
    // VIP信息
    @SerializedName("is_vip")
    private boolean isVip;
    @SerializedName("vip_expire_date")
    private String vipExpireDate;
    
    // 统计信息
    @SerializedName("watch_count")
    private int watchCount;
    @SerializedName("favorite_count")
    private int favoriteCount;
    @SerializedName("total_watch_time")
    private long totalWatchTime;
    
    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    
    public String getLastLogin() { return lastLogin; }
    public void setLastLogin(String lastLogin) { this.lastLogin = lastLogin; }
    
    public boolean isVip() { return isVip; }
    public void setVip(boolean vip) { isVip = vip; }
    
    public String getVipExpireDate() { return vipExpireDate; }
    public void setVipExpireDate(String vipExpireDate) { this.vipExpireDate = vipExpireDate; }
    
    public int getWatchCount() { return watchCount; }
    public void setWatchCount(int watchCount) { this.watchCount = watchCount; }
    
    public int getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(int favoriteCount) { this.favoriteCount = favoriteCount; }
    
    public long getTotalWatchTime() { return totalWatchTime; }
    public void setTotalWatchTime(long totalWatchTime) { this.totalWatchTime = totalWatchTime; }
    
    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        if (username != null && !username.isEmpty()) {
            return username;
        } else if (name != null && !name.isEmpty()) {
            return name;
        } else if (nickname != null && !nickname.isEmpty()) {
            return nickname;
        } else if (userName != null && !userName.isEmpty()) {
            return userName;
        } else {
            return "用户";
        }
    }
}
