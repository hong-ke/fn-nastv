package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 🔐 登录请求模型
 * 对应 /api/v1/user/login 接口
 */
public class LoginRequest {
    @SerializedName("username")
    private String username;
    
    @SerializedName("password")
    private String password;
    
    public LoginRequest() {}
    
    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
