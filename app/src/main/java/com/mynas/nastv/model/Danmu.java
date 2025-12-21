package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * 🎬 弹幕数据模型
 */
public class Danmu {
    @SerializedName("text")
    private String text;
    
    @SerializedName("time")
    private int time;  // 出现时间（秒）
    
    @SerializedName("color")
    private String color;  // 颜色，如 "#FFFFFF"
    
    @SerializedName("mode")
    private int mode;  // 弹幕模式：0=滚动弹幕，1=顶部，2=底部
    
    @SerializedName("border")
    private boolean border;  // 是否有边框
    
    @SerializedName("style")
    private Map<String, Object> style;  // 额外样式
    
    // 构造器
    public Danmu() {}
    
    public Danmu(String text, int time, String color, int mode) {
        this.text = text;
        this.time = time;
        this.color = color;
        this.mode = mode;
        this.border = false;
    }
    
    // Getters and Setters
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    
    public int getTime() { return time; }
    public void setTime(int time) { this.time = time; }
    
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    
    public int getMode() { return mode; }
    public void setMode(int mode) { this.mode = mode; }
    
    public boolean isBorder() { return border; }
    public void setBorder(boolean border) { this.border = border; }
    
    public Map<String, Object> getStyle() { return style; }
    public void setStyle(Map<String, Object> style) { this.style = style; }
    
    @Override
    public String toString() {
        return "Danmu{" +
                "text='" + text + '\'' +
                ", time=" + time +
                ", color='" + color + '\'' +
                ", mode=" + mode +
                ", border=" + border +
                '}';
    }
    
    /**
     * 🎨 弹幕模式常量
     */
    public static class Mode {
        public static final int SCROLL = 0;   // 滚动弹幕
        public static final int TOP = 1;      // 顶部弹幕
        public static final int BOTTOM = 2;   // 底部弹幕
    }
}
