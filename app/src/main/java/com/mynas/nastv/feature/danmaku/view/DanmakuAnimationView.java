package com.mynas.nastv.feature.danmaku.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 基于动画的弹幕视图
 * 
 * 核心思路：
 * - 不使用每帧 invalidate()，而是使用 ValueAnimator 驱动弹幕移动
 * - ValueAnimator 由系统 Choreographer 驱动，与 VSync 同步
 * - 只在动画更新时重绘，减少不必要的绘制调用
 * 
 * @author nastv
 * @version 1.0
 */
public class DanmakuAnimationView extends View implements IDanmakuView {
    
    private static final String TAG = "DanmakuAnimationView";
    
    private final Paint textPaint;
    private final List<DanmakuEntity> activeDanmakuList = new ArrayList<>();
    private final Map<DanmakuEntity, ValueAnimator> animatorMap = new HashMap<>();
    private final Object lockObject = new Object();
    
    // 颜色缓存
    private final Map<String, Integer> colorCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 100;
    
    private long frameCount = 0;
    private int viewWidth = 1920;
    
    // 全局动画驱动器
    private ValueAnimator globalAnimator;
    private boolean isAnimating = false;
    
    public DanmakuAnimationView(Context context) {
        this(context, null);
    }
    
    public DanmakuAnimationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public DanmakuAnimationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        
        // 硬件加速
        setLayerType(LAYER_TYPE_HARDWARE, null);
        
        setFocusable(false);
        setClickable(false);
        setWillNotDraw(false);
        setBackground(null);
        
        // 文字画笔
        textPaint = new Paint();
        textPaint.setAntiAlias(false);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(56);
        textPaint.setStyle(Paint.Style.FILL);
        
        // 初始化全局动画驱动器
        initGlobalAnimator();
        
        Log.d(TAG, "DanmakuAnimationView 初始化完成");
    }
    
    private void initGlobalAnimator() {
        globalAnimator = ValueAnimator.ofFloat(0f, 1f);
        globalAnimator.setDuration(16); // 约60fps
        globalAnimator.setRepeatCount(ValueAnimator.INFINITE);
        globalAnimator.setInterpolator(new LinearInterpolator());
        globalAnimator.addUpdateListener(animation -> {
            // 更新所有弹幕位置
            updateDanmakuPositions();
            // 触发重绘
            invalidate();
        });
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
    }
    
    @Override
    public void renderDanmaku(List<DanmakuEntity> danmakuList) {
        synchronized (lockObject) {
            // 添加新弹幕
            if (danmakuList != null) {
                for (DanmakuEntity entity : danmakuList) {
                    if (!activeDanmakuList.contains(entity)) {
                        activeDanmakuList.add(entity);
                    }
                }
            }
        }
        
        // 启动动画
        if (!isAnimating && !activeDanmakuList.isEmpty()) {
            startAnimation();
        }
    }
    
    private void startAnimation() {
        if (!isAnimating && globalAnimator != null) {
            isAnimating = true;
            globalAnimator.start();
            Log.d(TAG, "弹幕动画已启动");
        }
    }
    
    private void stopAnimation() {
        if (isAnimating && globalAnimator != null) {
            isAnimating = false;
            globalAnimator.cancel();
            Log.d(TAG, "弹幕动画已停止");
        }
    }
    
    private void updateDanmakuPositions() {
        synchronized (lockObject) {
            Iterator<DanmakuEntity> iterator = activeDanmakuList.iterator();
            while (iterator.hasNext()) {
                DanmakuEntity entity = iterator.next();
                
                // 更新位置（每帧移动约3像素，对应180像素/秒）
                entity.currentX -= 3f;
                
                // 检查是否已离开屏幕
                float textWidth = entity.text != null ? entity.text.length() * 30f : 100f;
                if (entity.currentX < -textWidth) {
                    iterator.remove();
                    entity.recycle();
                }
            }
            
            // 如果没有弹幕了，停止动画
            if (activeDanmakuList.isEmpty()) {
                stopAnimation();
            }
        }
    }
    
    @Override
    public void clearDanmaku() {
        synchronized (lockObject) {
            for (DanmakuEntity entity : activeDanmakuList) {
                entity.recycle();
            }
            activeDanmakuList.clear();
        }
        stopAnimation();
        invalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        frameCount++;
        
        synchronized (lockObject) {
            if (activeDanmakuList.isEmpty()) {
                return;
            }
            
            for (DanmakuEntity entity : activeDanmakuList) {
                if (entity != null && entity.text != null && !entity.text.isEmpty()) {
                    textPaint.setColor(parseColor(entity.color));
                    canvas.drawText(entity.text, entity.currentX, entity.currentY, textPaint);
                }
            }
        }
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
    }
    
    @Override
    public long getFrameCount() {
        return frameCount;
    }
    
    @Override
    public void setDanmakuAlpha(float alpha) {
        int alphaInt = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        textPaint.setAlpha(alphaInt);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
        if (globalAnimator != null) {
            globalAnimator.removeAllUpdateListeners();
            globalAnimator = null;
        }
    }
}
