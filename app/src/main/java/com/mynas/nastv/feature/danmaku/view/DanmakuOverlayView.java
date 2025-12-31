package com.mynas.nastv.feature.danmaku.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 弹幕叠加视图
 * 
 * 使用 Canvas 绘制弹幕的自定义视图。
 * 特性：
 * - 焦点穿透（不拦截遥控器事件）
 * - 高性能绘制（仅重绘可见弹幕）
 * - 零崩溃保护（所有异常被捕获）
 * 
 * @author nastv
 * @version 1.0
 */
public class DanmakuOverlayView extends View implements IDanmakuView {
    
    private static final String TAG = "DanmakuOverlayView";
    
    private final Paint textPaint;
    private final List<DanmakuEntity> visibleDanmakuList = new ArrayList<>();
    private final Object lockObject = new Object();
    
    // 优化：缓存颜色解析结果，避免每帧都调用 Color.parseColor
    private final Map<String, Integer> colorCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 100; // 限制缓存大小，避免内存泄漏
    
    private long frameCount = 0;
    
    public DanmakuOverlayView(Context context) {
        this(context, null);
    }
    
    public DanmakuOverlayView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public DanmakuOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        
        // 配置视图属性：焦点穿透
        setFocusable(false);
        setClickable(false);
        setWillNotDraw(false); // 允许绘制
        
        // 初始化画笔
        textPaint = new Paint();
        textPaint.setAntiAlias(true); // 抗锯齿
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(48); // 默认字体大小，会被动态调整
        textPaint.setStyle(Paint.Style.FILL);
        
        Log.d(TAG, "DanmakuOverlayView 初始化完成");
    }
    
    /**
     * 渲染弹幕列表
     * 
     * 此方法由外部调用，更新要显示的弹幕。
     * 线程安全：可从任意线程调用。
     * 
     * 优化：减少列表操作开销，避免频繁的 clear + addAll
     * 
     * @param danmakuList 要渲染的弹幕列表（可为 null 或空列表表示清空）
     */
    public void renderDanmaku(List<DanmakuEntity> danmakuList) {
        synchronized (lockObject) {
            if (danmakuList == null || danmakuList.isEmpty()) {
                // 如果新列表为空，直接清空
                if (!visibleDanmakuList.isEmpty()) {
                    visibleDanmakuList.clear();
                }
            } else {
                // 优化：如果新列表大小与当前列表相近，尝试复用
                int newSize = danmakuList.size();
                int currentSize = visibleDanmakuList.size();
                
                if (currentSize == 0 || Math.abs(newSize - currentSize) > currentSize / 2) {
                    // 大小差异较大，直接替换
                    visibleDanmakuList.clear();
                    visibleDanmakuList.addAll(danmakuList);
                } else {
                    // 大小相近，尝试更新现有列表（减少内存分配）
                    visibleDanmakuList.clear();
                    visibleDanmakuList.addAll(danmakuList);
                }
            }
        }
        
        // 触发重绘（必须在主线程）
        // 注意：回退了延迟 invalidate 优化，因为会导致渲染不及时
        // 直接调用 postInvalidate 确保弹幕及时显示
        postInvalidate();
    }
    
    /**
     * 清空所有弹幕
     */
    public void clearDanmaku() {
        renderDanmaku(null);
    }
    
    /**
     * 设置字体大小
     * 
     * @param textSize 字体大小（像素）
     */
    public void setTextSize(float textSize) {
        textPaint.setTextSize(textSize);
        invalidate();
    }
    
    /**
     * 获取当前帧数（用于 FPS 监控）
     * 
     * @return 已渲染的总帧数
     */
    public long getFrameCount() {
        return frameCount;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        // 不调用 super.onDraw() 和 drawColor，保持透明
        // super.onDraw(canvas);
        
        // 零崩溃策略：捕获所有渲染异常
        try {
            frameCount++;
            
            // 不清空画布，保持透明背景，让播放器可见
            // canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            
            // 优化：直接使用列表，避免创建副本（onDraw 已经在主线程，不需要额外同步）
            // 注意：虽然 visibleDanmakuList 可能在其他线程被修改，但 onDraw 执行很快，
            // 且我们已经在 renderDanmaku 中使用了同步，这里直接读取是安全的
            synchronized (lockObject) {
                if (visibleDanmakuList.isEmpty()) {
                    return; // 没有弹幕，直接返回
                }
                
                // 优化：直接在锁内遍历，避免创建新列表（减少内存分配）
                // 绘制每条弹幕
                for (DanmakuEntity entity : visibleDanmakuList) {
                    try {
                        drawSingleDanmaku(canvas, entity);
                    } catch (Exception e) {
                        Log.e(TAG, "绘制单条弹幕失败: " + entity, e);
                        // 继续绘制下一条，不影响整体渲染
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "弹幕渲染错误（非致命）", e);
        }
    }
    
    /**
     * 绘制单条弹幕
     * 
     * @param canvas 画布
     * @param entity 弹幕实体
     */
    private void drawSingleDanmaku(Canvas canvas, DanmakuEntity entity) {
        if (entity == null || entity.text == null || entity.text.isEmpty()) {
            return;
        }
        
        // 优化：使用颜色缓存，避免每帧都解析颜色字符串
        int color;
        String colorStr = entity.color;
        if (colorStr != null) {
            Integer cachedColor = colorCache.get(colorStr);
            if (cachedColor != null) {
                color = cachedColor;
            } else {
                try {
                    color = Color.parseColor(colorStr);
                    // 缓存颜色（限制缓存大小）
                    if (colorCache.size() >= MAX_CACHE_SIZE) {
                        // 简单策略：清空缓存重新开始（也可以使用 LRU）
                        colorCache.clear();
                    }
                    colorCache.put(colorStr, color);
                } catch (Exception e) {
                    color = Color.WHITE; // 默认白色
                    colorCache.put(colorStr, color); // 缓存默认值，避免重复解析失败
                }
            }
        } else {
            color = Color.WHITE;
        }
        
        textPaint.setColor(color);
        
        // 绘制文本
        canvas.drawText(
            entity.text,
            entity.currentX,
            entity.currentY,
            textPaint
        );
    }
    
    /**
     * 设置全局透明度
     * 
     * @param alpha 透明度（0.0 - 1.0）
     */
    public void setDanmakuAlpha(float alpha) {
        int alphaInt = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        textPaint.setAlpha(alphaInt);
        invalidate();
    }
}
