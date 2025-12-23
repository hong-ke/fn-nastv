package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import com.mynas.nastv.config.AppConfig;

/**
 * 🔐 登录请求模型
 * 对应 /v/api/v1/login 接口
 * 与 fntv-electron 项目保持一致
 */
public class LoginRequest {
    
    /**
     * 应用名称，与 Web 端保持一致
     */
    @SerializedName("app_name")
    private String appName;
    
    @SerializedName("username")
    private String username;
    
    @SerializedName("password")
    private String password;
    
    /**
     * 随机数，用于防重放攻击
     */
    @SerializedName("nonce")
    private String nonce;
    
    public LoginRequest() {
        this.appName = AppConfig.APP_NAME;
    }
    
    public LoginRequest(String username, String password) {
        this.appName = AppConfig.APP_NAME;
        this.username = username;
        this.password = password;
        this.nonce = generateNonce();
    }
    
    /**
     * 生成6位随机数
     */
    private String generateNonce() {
        return String.format("%06d", (int)(Math.random() * 900000) + 100000);
    }
    
    // Getters and Setters
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
}
