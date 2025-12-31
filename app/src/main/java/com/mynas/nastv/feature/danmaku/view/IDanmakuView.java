package com.mynas.nastv.feature.danmaku.view;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;

import java.util.List;

/**
 * 弹幕视图接口
 * 
 * 统一 DanmakuOverlayView 和 DanmakuSurfaceView 的接口
 */
public interface IDanmakuView {
    
    /**
     * 渲染弹幕列表
     * @param danmakuList 要渲染的弹幕列表
     */
    void renderDanmaku(List<DanmakuEntity> danmakuList);
    
    /**
     * 清空所有弹幕
     */
    void clearDanmaku();
    
    /**
     * 设置字体大小
     * @param textSize 字体大小（像素）
     */
    void setTextSize(float textSize);
    
    /**
     * 设置全局透明度
     * @param alpha 透明度（0.0 - 1.0）
     */
    void setDanmakuAlpha(float alpha);
    
    /**
     * 获取当前帧数（用于 FPS 监控）
     * @return 已渲染的总帧数
     */
    long getFrameCount();
}
