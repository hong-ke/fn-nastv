package com.mynas.nastv.feature.danmaku.model;

import java.io.Serializable;

/**
 * 弹幕配置类
 * 
 * 存储用户偏好设置和渲染配置。
 * 默认值符合 FR-02 影院模式规范。
 * 
 * @author nastv
 * @version 1.0
 */
public class DanmuConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 弹幕透明度（范围：0.0 - 1.0）
     * 默认 0.5（50%透明度，符合影院模式）
     */
    public float opacity = 0.5f;
    
    /**
     * 弹幕字体大小（像素）
     * 默认值会根据屏幕分辨率动态计算
     */
    public int fontSize = 36;
    
    /**
     * 顶部边距百分比（范围：0.0 - 1.0）
     * 默认 0.0（从屏幕顶部开始）
     */
    public float topMarginPercent = 0.0f;
    
    /**
     * 底部边距百分比（范围：0.0 - 1.0）
     * 默认 0.80（弹幕仅显示在顶部 20% 区域，即屏幕上方1/5）
     */
    public float bottomMarginPercent = 0.80f;
    
    /**
     * 弹幕是否启用
     * 默认 true（开启）
     */
    public boolean enabled = true;
    
    /**
     * 滚动速度（像素/秒）
     * 默认 200 px/s
     */
    public float scrollSpeed = 200f;
    
    /**
     * 最大同屏弹幕数量
     * 超过此数量时会触发丢弃逻辑
     * 默认 100 条（符合 NFR-02 性能要求）
     */
    public int maxOnScreenCount = 100;
    
    /**
     * 构造函数
     */
    public DanmuConfig() {
        // 使用默认值
    }
    
    /**
     * 创建影院模式配置（默认推荐）
     * 弹幕仅显示在屏幕顶部 1/5 区域
     * 
     * @return 影院模式配置实例
     */
    public static DanmuConfig createCinemaMode() {
        DanmuConfig config = new DanmuConfig();
        config.opacity = 0.5f;
        config.topMarginPercent = 0.0f;
        config.bottomMarginPercent = 0.80f; // 仅顶部 20%（1/5）
        config.enabled = true;
        return config;
    }
    
    /**
     * 从 SharedPreferences 加载用户配置
     * 
     * @return 最新的配置实例
     */
    public static DanmuConfig loadFromPrefs() {
        DanmuConfig config = new DanmuConfig();
        config.enabled = com.mynas.nastv.utils.SharedPreferencesManager.isDanmakuEnabled();
        config.opacity = com.mynas.nastv.utils.SharedPreferencesManager.getDanmakuAlpha() / 255.0f;
        config.fontSize = com.mynas.nastv.utils.SharedPreferencesManager.getDanmakuTextSize();
        config.scrollSpeed = 200f * com.mynas.nastv.utils.SharedPreferencesManager.getDanmakuSpeed();
        
        // 默认区域为20%（屏幕上方1/5）
        int region = com.mynas.nastv.utils.SharedPreferencesManager.getDanmakuRegion();
        if (region == 100) {
            // 如果是默认值100%，改为20%
            region = 20;
        }
        config.topMarginPercent = 0.0f;
        config.bottomMarginPercent = 1.0f - (region / 100.0f);
        
        return config;
    }

    /**
     * 创建全屏模式配置
     * 
     * @return 全屏模式配置实例
     */
    public static DanmuConfig createFullScreenMode() {
        DanmuConfig config = new DanmuConfig();
        config.opacity = 0.7f;
        config.topMarginPercent = 0.0f;
        config.bottomMarginPercent = 0.0f; // 全屏显示
        config.enabled = true;
        return config;
    }
    
    /**
     * 获取可渲染区域高度百分比
     * 
     * @return 可渲染高度比例（0.0 - 1.0）
     */
    public float getRenderableHeightPercent() {
        return 1.0f - topMarginPercent - bottomMarginPercent;
    }
    
    /**
     * 验证配置有效性
     * 
     * @return true 如果配置有效
     */
    public boolean isValid() {
        return opacity >= 0.0f && opacity <= 1.0f &&
               topMarginPercent >= 0.0f && topMarginPercent <= 1.0f &&
               bottomMarginPercent >= 0.0f && bottomMarginPercent <= 1.0f &&
               fontSize > 0 &&
               scrollSpeed > 0 &&
               maxOnScreenCount > 0;
    }
    
    @Override
    public String toString() {
        return "DanmuConfig{" +
                "opacity=" + opacity +
                ", fontSize=" + fontSize +
                ", topMarginPercent=" + topMarginPercent +
                ", bottomMarginPercent=" + bottomMarginPercent +
                ", enabled=" + enabled +
                ", scrollSpeed=" + scrollSpeed +
                ", maxOnScreenCount=" + maxOnScreenCount +
                '}';
    }
}
