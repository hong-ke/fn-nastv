package com.mynas.nastv.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * 弹幕 API 响应 - 直接返回 Map 格式
 * 
 * API 响应格式 (参考 Apifox 文档):
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
 *     ],
 *     "2": [ ... ]
 * }
 * 
 * Key 是分钟数，Value 是该分钟内的弹幕列表
 */
public class DanmakuMapResponse {

    /**
     * 弹幕数据项
     */
    public static class DanmakuItem {
        
        /**
         * 弹幕出现时间 (秒)
         */
        @SerializedName("time")
        public double time;
        
        /**
         * 弹幕文本内容
         */
        @SerializedName("text")
        public String text;
        
        /**
         * 弹幕颜色 (如 "#FFFFFF")
         */
        @SerializedName("color")
        public String color;
        
        /**
         * 弹幕模式
         * 0: 滚动弹幕
         * 1: 顶部弹幕
         * 2: 底部弹幕
         */
        @SerializedName("mode")
        public int mode;
        
        /**
         * 是否有边框
         */
        @SerializedName("border")
        public boolean border;
        
        /**
         * 其他信息
         */
        @SerializedName("other")
        public Map<String, String> other;
        
        /**
         * 样式信息
         */
        @SerializedName("style")
        public Map<String, Object> style;
    }
}
