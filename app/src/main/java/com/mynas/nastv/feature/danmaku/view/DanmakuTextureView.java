package com.mynas.nastv.feature.danmaku.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * 弹幕 TextureView
 * 
 * 使用 TextureView 替代 SurfaceView：
 * - 与视频播放器共享 GPU 管线，减少资源竞争
 * - 支持硬件加速
 * - 可以做动画和变换
 * 
 * @author nastv
 * @version 1.0
 */
public class DanmakuTextureView extends TextureView implements TextureView.SurfaceTextureListener, IDanmakuView {
    
    private static final String TAG = "DanmakuTextureView";
    
    private final Paint textPaint;
    private final Paint strokePaint;
    private final List<DanmakuEntity> renderList = new ArrayList<>();
    private final List<DanmakuEntity> drawList = new ArrayList<>();
    private final Object lockObject = new Object();
    
    // 颜色缓存
    private final java.util.Map<String, Integer> colorCache = new java.util.HashMap<>();
    private static final int MAX_COLOR_CACHE = 50;
    
    private boolean isSurfaceReady = false;
    private long frameCount = 0;
    
    public DanmakuTextureView(Context context) {
        this(context, null);
    }
    
    public DanmakuTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public DanmakuTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        
        setFocusable(false);
        setClickable(false);
        setOpaque(false); // 透明背景
        setSurfaceTextureListener(this);
        
        // 文字画笔
        textPaint = new Paint();
        textPaint.setAntiAlias(false);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(56);
        textPaint.setStyle(Paint.Style.FILL);
        
        // 描边画笔
        strokePaint = new Paint();
        strokePaint.setAntiAlias(false);
        strokePaint.setColor(Color.BLACK);
        strokePaint.setTextSize(56);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(3f);
        
        Log.d(TAG, "DanmakuTextureView 初始化完成");
    }
    
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        isSurfaceReady = true;
        Log.d(TAG, "SurfaceTexture 可用: " + width + "x" + height);
    }
    
    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "SurfaceTexture 尺寸变化: " + width + "x" + height);
    }
    
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        isSurfaceReady = false;
        Log.d(TAG, "SurfaceTexture 销毁");
        return true;
    }
    
    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // 不需要处理
    }
    
    @Override
    public void renderDanmaku(List<DanmakuEntity> danmakuList) {
        if (!isSurfaceReady) {
            return;
        }
        
        synchronized (lockObject) {
            renderList.clear();
            if (danmakuList != null && !danmakuList.isEmpty()) {
                renderList.addAll(danmakuList);
            }
        }
        
        drawDanmaku();
    }
    
    private void drawDanmaku() {
        if (!isSurfaceReady) {
            return;
        }
        
        synchronized (lockObject) {
            drawList.clear();
            drawList.addAll(renderList);
        }
        
        Canvas canvas = null;
        try {
            canvas = lockCanvas();
            if (canvas == null) {
                return;
            }
            
            frameCount++;
            
            // 清空画布
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            
            // 绘制每条弹幕
            for (int i = 0, size = drawList.size(); i < size; i++) {
                DanmakuEntity entity = drawList.get(i);
                if (entity != null && entity.text != null && !entity.text.isEmpty()) {
                    // 先绘制黑色描边
                    canvas.drawText(entity.text, entity.currentX, entity.currentY, strokePaint);
                    // 再绘制彩色文字
                    textPaint.setColor(parseColor(entity.color));
                    canvas.drawText(entity.text, entity.currentX, entity.currentY, textPaint);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "弹幕渲染错误", e);
        } finally {
            if (canvas != null) {
                try {
                    unlockCanvasAndPost(canvas);
                } catch (Exception e) {
                    // 忽略
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
            if (colorCache.size() >= MAX_COLOR_CACHE) colorCache.clear();
            colorCache.put(colorStr, color);
            return color;
        } catch (Exception e) {
            return Color.WHITE;
        }
    }
    
    @Override
    public void clearDanmaku() {
        synchronized (lockObject) {
            renderList.clear();
        }
        
        if (isSurfaceReady) {
            Canvas canvas = null;
            try {
                canvas = lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                }
            } catch (Exception e) {
                // 忽略
            } finally {
                if (canvas != null) {
                    try {
                        unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        // 忽略
                    }
                }
            }
        }
    }
    
    @Override
    public void setTextSize(float textSize) {
        textPaint.setTextSize(textSize);
        strokePaint.setTextSize(textSize);
    }
    
    public long getFrameCount() {
        return frameCount;
    }
    
    @Override
    public void setDanmakuAlpha(float alpha) {
        int alphaInt = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        textPaint.setAlpha(alphaInt);
        strokePaint.setAlpha(alphaInt);
    }
}
