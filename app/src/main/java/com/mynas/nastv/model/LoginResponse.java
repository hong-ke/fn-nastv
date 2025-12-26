package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 登录响应数据模型
 */
public class LoginResponse {
    private int code;
    private String msg;
    @SerializedName("data")
    private LoginData data;
    
    /**
     * 登录数据
     */
    public static class LoginData {
        @SerializedName("status")
        private String status;  // "Pending", "Success", "Expired" 等状态
        @SerializedName("token")
        private String token;
        @SerializedName("user_info")
        private String userInfo;
        @SerializedName("user_name")
        private String userName;
        @SerializedName("expires_in")
        private long expiresIn;
        
        // Getters and Setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        
        public String getUserInfo() { return userInfo; }
        public void setUserInfo(String userInfo) { this.userInfo = userInfo; }
        
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        
        public long getExpiresIn() { return expiresIn; }
        public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }
    }
    
    // 保持向后兼容的字段
    @SerializedName("token")
    private String token;
    @SerializedName("user_info")
    private String userInfo;
    @SerializedName("user_name")
    private String userName;
    @SerializedName("expires_in")
    private long expiresIn;
    
    // Getters and Setters
    public int getCode() {
        return code;
    }
    
    public void setCode(int code) {
        this.code = code;
    }
    
    public String getMsg() {
        return msg;
    }
    
    public void setMsg(String msg) {
        this.msg = msg;
    }
    
    public LoginData getData() {
        return data;
    }
    
    public void setData(LoginData data) {
        this.data = data;
    }
    
    // 向后兼容的方法，优先从data中获取，fallback到顶级字段
    public String getToken() {
        return data != null && data.getToken() != null ? data.getToken() : token;
    }
    
    public void setToken(String token) {
        this.token = token;
    }
    
    public String getUserInfo() {
        return data != null && data.getUserInfo() != null ? data.getUserInfo() : userInfo;
    }
    
    public void setUserInfo(String userInfo) {
        this.userInfo = userInfo;
    }
    
    public String getUserName() {
        return data != null && data.getUserName() != null ? data.getUserName() : userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public long getExpiresIn() {
        return data != null ? data.getExpiresIn() : expiresIn;
    }
    
    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}


