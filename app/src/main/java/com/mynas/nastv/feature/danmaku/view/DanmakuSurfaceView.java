package com.mynas.nastv.feature.danmaku.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 高性能弹幕 SurfaceView
 * 
 * 使用 SurfaceView 替代普通 View，实现独立线程绘制，
 * 不阻塞 UI 线程，大幅提升渲染性能。
 * 
 * 特性：
 * - 独立绘制线程，不影响 UI 线程
 * - 焦点穿透（不拦截遥控器事件）
 * - 高性能绘制（仅重绘可见弹幕）
 * - 零崩溃保护（所有异常被捕获）
 * 
 * @author nastv
 * @version 2.0
 */
public class DanmakuSurfaceView extends SurfaceView implements SurfaceHolder.Callback, IDanmakuView {
    
    private static final String TAG = "DanmakuSurfaceView";
    
    private final Paint textPaint;
    private final Paint shadowPaint;
    private final List<DanmakuEntity> visibleDanmakuList = new ArrayList<>();
    private final List<DanmakuEntity> renderList = new ArrayList<>(); // 渲染用临时列表
    private final List<DanmakuEntity> drawList = new ArrayList<>(); // 绘制用列表，避免每帧创建
    private final Object lockObject = new Object();
    
    // 颜色缓存，避免每帧解析颜色字符串
    private final java.util.Map<String, Integer> colorCache = new java.util.HashMap<>();
    private static final int MAX_COLOR_CACHE = 50;
    
    private SurfaceHolder surfaceHolder;
    private boolean isSurfaceReady = false;
    private long frameCount = 0;
    
    public DanmakuSurfaceView(Context context) {
        this(context, null);
    }
    
    public DanmakuSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public DanmakuSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        
        // 配置视图属性：焦点穿透
        setFocusable(false);
        setClickable(false);
        
        // 设置透明背景 - 使用 setZOrderOnTop 确保弹幕在最上层
        setZOrderOnTop(true);
        surfaceHolder = getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        surfaceHolder.addCallback(this);
        
        // 初始化文字画笔（优化：使用 ShadowLayer 替代描边，减少一次绘制）
        textPaint = new Paint();
        textPaint.setAntiAlias(false); // 关闭抗锯齿，提升性能
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(56);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setFakeBoldText(true);
        // 使用阴影层替代描边，只需绘制一次
        textPaint.setShadowLayer(3f, 1f, 1f, Color.BLACK);
        
        // shadowPaint 不再使用，保留兼容性
        shadowPaint = new Paint();
        shadowPaint.setColor(Color.BLACK);
        shadowPaint.setTextSize(56);
        
        Log.d(TAG, "DanmakuSurfaceView 初始化完成");
    }
    
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        isSurfaceReady = true;
        Log.d(TAG, "Surface 已创建");
    }
    
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "Surface 尺寸变化: " + width + "x" + height);
    }
    
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isSurfaceReady = false;
        Log.d(TAG, "Surface 已销毁");
    }
    
    /**
     * 渲染弹幕列表
     * 
     * 此方法由外部调用，更新要显示的弹幕。
     * 线程安全：可从任意线程调用。
     * 直接在调用线程绘制，避免 postInvalidate 的开销。
     * 
     * @param danmakuList 要渲染的弹幕列表（可为 null 或空列表表示清空）
     */
    public void renderDanmaku(List<DanmakuEntity> danmakuList) {
        if (!isSurfaceReady) {
            return;
        }
        
        // 复制列表到渲染列表
        synchronized (lockObject) {
            renderList.clear();
            if (danmakuList != null && !danmakuList.isEmpty()) {
                renderList.addAll(danmakuList);
            }
        }
        
        // 直接绘制，不使用 postInvalidate
        drawDanmaku();
    }
    
    /**
     * 直接绘制弹幕到 Surface
     * 
     * 优化：复用 drawList 避免每帧创建新列表
     */
    private void drawDanmaku() {
        if (!isSurfaceReady || surfaceHolder == null) {
            return;
        }
        
        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
            if (canvas == null) {
                return;
            }
            
            frameCount++;
            
            // 清空画布（透明）
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            
            // 复用 drawList，避免每帧创建新列表
            synchronized (lockObject) {
                if (renderList.isEmpty()) {
                    return;
                }
                drawList.clear();
                drawList.addAll(renderList);
            }
            
            // 绘制每条弹幕（在锁外绘制，减少锁持有时间）
            for (int i = 0, size = drawList.size(); i < size; i++) {
                try {
                    drawSingleDanmaku(canvas, drawList.get(i));
                } catch (Exception e) {
                    // 继续绘制下一条，不影响整体渲染
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "弹幕渲染错误（非致命）", e);
        } finally {
            if (canvas != null && surfaceHolder != null) {
                try {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                } catch (Exception e) {
                    // 忽略
                }
            }
        }
    }
    
    /**
     * 清空所有弹幕
     */
    public void clearDanmaku() {
        synchronized (lockObject) {
            renderList.clear();
            visibleDanmakuList.clear();
        }
        
        // 清空画布
        if (isSurfaceReady && surfaceHolder != null) {
            Canvas canvas = null;
            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                }
            } catch (Exception e) {
                // 忽略
            } finally {
                if (canvas != null) {
                    try {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
        }
    }
    
    /**
     * 设置字体大小
     * 
     * @param textSize 字体大小（像素）
     */
    public void setTextSize(float textSize) {
        textPaint.setTextSize(textSize);
        shadowPaint.setTextSize(textSize);
    }
    
    /**
     * 获取当前帧数（用于 FPS 监控）
     * 
     * @return 已渲染的总帧数
     */
    public long getFrameCount() {
        return frameCount;
    }
    
    /**
     * 绘制单条弹幕
     * 
     * 优化：
     * 1. 使用颜色缓存，避免每帧解析颜色字符串
     * 2. 使用 ShadowLayer 替代描边，只需绘制一次
     * 
     * @param canvas 画布
     * @param entity 弹幕实体
     */
    private void drawSingleDanmaku(Canvas canvas, DanmakuEntity entity) {
        if (entity == null || entity.text == null || entity.text.isEmpty()) {
            return;
        }
        
        // 使用颜色缓存
        int color;
        String colorStr = entity.color;
        if (colorStr != null) {
            Integer cached = colorCache.get(colorStr);
            if (cached != null) {
                color = cached;
            } else {
                try {
                    color = Color.parseColor(colorStr);
                } catch (Exception e) {
                    color = Color.WHITE;
                }
                // 限制缓存大小
                if (colorCache.size() >= MAX_COLOR_CACHE) {
                    colorCache.clear();
                }
                colorCache.put(colorStr, color);
            }
        } else {
            color = Color.WHITE;
        }
        
        textPaint.setColor(color);
        
        // 只绘制一次（阴影由 ShadowLayer 自动处理）
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
        shadowPaint.setAlpha(alphaInt);
    }
}
