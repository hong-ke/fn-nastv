package com.mynas.nastv.feature.danmaku.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 高性能硬件加速弹幕视图
 * 
 * 替代 SurfaceView 的 lockCanvas/unlockCanvasAndPost 方案：
 * - 使用 View + onDraw()，无需 lockCanvas
 * - 强制硬件加速，利用 GPU 渲染
 * - 优化绘制性能，减少绘制调用
 * - 使用 DisplayList 缓存，减少重绘
 * 
 * 性能优势：
 * 1. 无需每帧 lockCanvas/unlockCanvasAndPost（这是 SurfaceView 的主要瓶颈）
 * 2. 硬件加速由系统自动管理，性能更好
 * 3. 支持 DisplayList 缓存，相同内容无需重绘
 * 4. 与视频播放器共享 GPU 管线，减少资源竞争
 * 
 * @author nastv
 * @version 1.0 (Hardware Accelerated)
 */
public class DanmakuHardwareView extends View implements IDanmakuView {
    
    private static final String TAG = "DanmakuHardwareView";
    
    private final Paint textPaint;
    private final Paint strokePaint;
    private final List<DanmakuEntity> visibleDanmakuList = new ArrayList<>();
    private final Object lockObject = new Object();
    
    // 颜色缓存
    private final Map<String, Integer> colorCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 100;
    
    private long frameCount = 0;
    private boolean needsRedraw = false;
    
    public DanmakuHardwareView(Context context) {
        this(context, null);
    }
    
    public DanmakuHardwareView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public DanmakuHardwareView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        
        // 关键：强制硬件加速
        setLayerType(LAYER_TYPE_HARDWARE, null);
        
        // 配置视图属性
        setFocusable(false);
        setClickable(false);
        setWillNotDraw(false);
        
        // 优化：设置背景透明，避免不必要的绘制
        setBackground(null);
        
        // 文字画笔 - 优化配置
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setAntiAlias(false); // 关闭抗锯齿提升性能
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(56);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setFakeBoldText(true);
        
        // 描边画笔 - 用于文字描边（可选）
        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setAntiAlias(false);
        strokePaint.setColor(Color.BLACK);
        strokePaint.setTextSize(56);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);
        
        Log.d(TAG, "DanmakuHardwareView 初始化完成 (硬件加速)");
    }
    
    @Override
    public void renderDanmaku(List<DanmakuEntity> danmakuList) {
        synchronized (lockObject) {
            visibleDanmakuList.clear();
            if (danmakuList != null && !danmakuList.isEmpty()) {
                visibleDanmakuList.addAll(danmakuList);
            }
            needsRedraw = true;
        }
        
        // 使用 postInvalidate 触发重绘（线程安全）
        // 注意：不需要 lockCanvas，系统会自动管理硬件加速的 Canvas
        postInvalidate();
    }
    
    @Override
    public void clearDanmaku() {
        synchronized (lockObject) {
            visibleDanmakuList.clear();
            needsRedraw = true;
        }
        postInvalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        // 硬件加速的 Canvas，无需 lockCanvas/unlockCanvasAndPost
        // 系统会自动管理渲染
        
        try {
            frameCount++;
            
            synchronized (lockObject) {
                if (visibleDanmakuList.isEmpty()) {
                    return;
                }
                
                // 绘制每条弹幕
                for (DanmakuEntity entity : visibleDanmakuList) {
                    try {
                        drawSingleDanmaku(canvas, entity);
                    } catch (Exception e) {
                        Log.e(TAG, "绘制单条弹幕失败", e);
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "弹幕渲染错误", e);
        }
    }
    
    /**
     * 绘制单条弹幕
     * 优化：减少绘制调用，只绘制一次文字（不绘制描边以提升性能）
     */
    private void drawSingleDanmaku(Canvas canvas, DanmakuEntity entity) {
        if (entity == null || entity.text == null || entity.text.isEmpty()) {
            return;
        }
        
        // 使用颜色缓存
        int color = parseColor(entity.color);
        textPaint.setColor(color);
        
        // 只绘制文字，不绘制描边（提升性能）
        // 如果需要描边效果，可以取消下面的注释
        // canvas.drawText(entity.text, entity.currentX, entity.currentY, strokePaint);
        canvas.drawText(entity.text, entity.currentX, entity.currentY, textPaint);
    }
    
    private int parseColor(String colorStr) {
        if (colorStr == null) return Color.WHITE;
        Integer cached = colorCache.get(colorStr);
        if (cached != null) return cached;
        try {
            int color = Color.parseColor(colorStr);
            if (colorCache.size() >= MAX_CACHE_SIZE) {
                colorCache.clear();
            }
            colorCache.put(colorStr, color);
            return color;
        } catch (Exception e) {
            return Color.WHITE;
        }
    }
    
    @Override
    public void setTextSize(float textSize) {
        textPaint.setTextSize(textSize);
        strokePaint.setTextSize(textSize);
        invalidate();
    }
    
    @Override
    public long getFrameCount() {
        return frameCount;
    }
    
    @Override
    public void setDanmakuAlpha(float alpha) {
        int alphaInt = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        textPaint.setAlpha(alphaInt);
        strokePaint.setAlpha(alphaInt);
        invalidate();
    }
    
    /**
     * 确保硬件加速已启用
     */
    public boolean isHardwareAccelerated() {
        return getLayerType() == LAYER_TYPE_HARDWARE;
    }
}

