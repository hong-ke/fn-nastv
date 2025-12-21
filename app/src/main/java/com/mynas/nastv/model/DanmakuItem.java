package com.mynas.nastv.model;

import android.graphics.Color;

/**
 * 🎨 弹幕数据模型
 */
public class DanmakuItem {
    private String text;
    private float timeSeconds;
    private int color;
    private int type; // 弹幕类型：滚动、顶部、底部
    
    public DanmakuItem() {}
    
    public DanmakuItem(String text, float timeSeconds, int color) {
        this.text = text;
        this.timeSeconds = timeSeconds;
        this.color = color;
        this.type = 1; // 默认滚动弹幕
    }
    
    // Getters and Setters
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public float getTimeSeconds() { return timeSeconds; }
    public void setTimeSeconds(float timeSeconds) { this.timeSeconds = timeSeconds; }
    
    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }
    
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
}
