package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;

/**
 * API响应基础模型
 * 所有API响应的通用结构
 */
public class BaseResponse<T> {
    
    @SerializedName("code")
    private int code;
    
    @SerializedName("msg")
    private String message;
    
    @SerializedName("data")
    private T data;
    
    public BaseResponse() {}
    
    public BaseResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    public int getCode() {
        return code;
    }
    
    public void setCode(int code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    /**
     * 判断请求是否成功
     */
    public boolean isSuccess() {
        return code == 0;
    }
    
    @Override
    public String toString() {
        return "BaseResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                '}';
    }
}
