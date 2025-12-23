package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * 弹幕列表响应
 * 
 * 新 API 响应格式 (参考 Apifox 文档):
 * {
 *     "1": [
 *         {
 *             "border": false,
 *             "color": "#FFFFFF",
 *             "mode": 0,
 *             "other": { "create_time": "1669508687" },
 *             "style": {},
 *             "text": "弹幕内容",
 *             "time": 0.0
 *         }
 *     ]
 * }
 */
public class DanmakuListResponse extends BaseResponse<List<DanmakuListResponse.DanmakuData>> {

    /**
     * 弹幕数据项
     */
    public static class DanmakuData {
        
        /**
         * 弹幕出现时间 (秒)
         */
        @SerializedName("time")
        private double time;
        
        /**
         * 弹幕文本内容
         */
        @SerializedName("text")
        private String text;
        
        /**
         * 弹幕颜色 (如 "#FFFFFF")
         */
        @SerializedName("color")
        private String color;
        
        /**
         * 弹幕模式
         * 0: 滚动弹幕
         * 1: 顶部弹幕
         * 2: 底部弹幕
         */
        @SerializedName("mode")
        private int mode;
        
        /**
         * 是否有边框
         */
        @SerializedName("border")
        private boolean border;
        
        /**
         * 其他信息
         */
        @SerializedName("other")
        private Map<String, String> other;
        
        /**
         * 样式信息
         */
        @SerializedName("style")
        private Map<String, Object> style;
        
        // 兼容旧字段名
        @SerializedName("content")
        private String content;
        
        @SerializedName("type")
        private int type;
        
        // Getters
        public double getTime() { return time; }
        public void setTime(double time) { this.time = time; }
        
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        
        /**
         * 获取弹幕内容 (兼容新旧字段)
         */
        public String getContent() { 
            return text != null ? text : content; 
        }
        public void setContent(String content) { this.content = content; }
        
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        
        public int getMode() { return mode; }
        public void setMode(int mode) { this.mode = mode; }
        
        /**
         * 获取弹幕类型 (兼容新旧字段)
         */
        public int getType() { 
            return mode != 0 ? mode : type; 
        }
        public void setType(int type) { this.type = type; }
        
        public boolean isBorder() { return border; }
        public void setBorder(boolean border) { this.border = border; }
        
        public Map<String, String> getOther() { return other; }
        public void setOther(Map<String, String> other) { this.other = other; }
        
        public Map<String, Object> getStyle() { return style; }
        public void setStyle(Map<String, Object> style) { this.style = style; }
    }
}
