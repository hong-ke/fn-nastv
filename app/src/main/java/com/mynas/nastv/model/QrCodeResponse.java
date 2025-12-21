package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * 🔑 二维码登录响应数据模型
 */
public class QrCodeResponse {
    private int code;
    private String msg;
    @SerializedName("data")
    private QrCodeData data;
    
    /**
     * 二维码数据
     */
    public static class QrCodeData {
        @SerializedName("code")
        private String code;  // 服务器实际返回的字段名
        @SerializedName("login_code")
        private String loginCode;
        @SerializedName("qr_url")
        private String qrUrl;
        @SerializedName("expire_time")
        private long expireTime;
        
        // Getters and Setters
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public String getLoginCode() {
            return loginCode;
        }
        
        public void setLoginCode(String loginCode) {
            this.loginCode = loginCode;
        }
        
        public String getQrUrl() {
            // 如果qrUrl为空，使用code字段作为二维码内容
            return qrUrl != null ? qrUrl : code;
        }
        
        public void setQrUrl(String qrUrl) {
            this.qrUrl = qrUrl;
        }
        
        public long getExpireTime() {
            return expireTime;
        }
        
        public void setExpireTime(long expireTime) {
            this.expireTime = expireTime;
        }
    }
    
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
    
    public QrCodeData getData() {
        return data;
    }
    
    public void setData(QrCodeData data) {
        this.data = data;
    }
}
