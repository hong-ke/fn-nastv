package com.mynas.nastv.feature.danmaku.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.HandlerThread;
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
 * 优化版本：使用独立绘制线程，避免阻塞调用线程
 * 双缓冲机制：渲染线程写入 buffer，绘制线程读取 buffer
 * 
 * @author nastv
 * @version 3.2 (Async Draw Thread)
 */
public class DanmakuSurfaceView extends SurfaceView implements SurfaceHolder.Callback, IDanmakuView {
    
    private static final String TAG = "DanmakuSurfaceView";
    
    private final Paint textPaint;
    private final Paint strokePaint;
    
    // 双缓冲：两个列表交替使用
    private final List<DanmakuEntity> bufferA = new ArrayList<>();
    private final List<DanmakuEntity> bufferB = new ArrayList<>();
    private volatile List<DanmakuEntity> writeBuffer = bufferA;  // 渲染线程写入
    private volatile List<DanmakuEntity> readBuffer = bufferB;   // 绘制线程读取
    private final Object swapLock = new Object();
    
    // 绘制线程专用列表，避免每帧创建
    private final List<DanmakuEntity> drawListCache = new ArrayList<>();
    
    // 绘制线程
    private HandlerThread drawThread;
    private Handler drawHandler;
    private volatile boolean pendingDraw = false;
    
    // 颜色缓存
    private final java.util.Map<String, Integer> colorCache = new java.util.HashMap<>();
    private static final int MAX_COLOR_CACHE = 50;
    
    private SurfaceHolder surfaceHolder;
    private volatile boolean isSurfaceReady = false;
    private long frameCount = 0;
    
    public DanmakuSurfaceView(Context context) {
        this(context, null);
    }
    
    public DanmakuSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public DanmakuSurfaceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        
        setFocusable(false);
        setClickable(false);
        
        // 必须用 setZOrderOnTop(true) 否则黑屏
        setZOrderOnTop(true);
        surfaceHolder = getHolder();
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
        surfaceHolder.addCallback(this);
        
        // 文字画笔 - 极简配置
        textPaint = new Paint();
        textPaint.setAntiAlias(false); // 关闭抗锯齿提升性能
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
        
        // 创建独立绘制线程
        drawThread = new HandlerThread("DanmakuDrawThread");
        drawThread.start();
        drawHandler = new Handler(drawThread.getLooper());
        
        Log.d(TAG, "DanmakuSurfaceView 初始化完成 (Async Draw)");
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
    
    @Override
    public void renderDanmaku(List<DanmakuEntity> danmakuList) {
        if (!isSurfaceReady) {
            return;
        }
        
        // 写入 writeBuffer（不阻塞）
        // 优化：不再深拷贝，直接复用对象，只更新位置
        synchronized (swapLock) {
            writeBuffer.clear();
            if (danmakuList != null && !danmakuList.isEmpty()) {
                // 直接添加引用，不创建新对象
                // 因为 DanmuRenderer 已经管理了对象生命周期
                writeBuffer.addAll(danmakuList);
            }
            
            // 交换缓冲区
            List<DanmakuEntity> temp = writeBuffer;
            writeBuffer = readBuffer;
            readBuffer = temp;
        }
        
        // 异步绘制（如果没有待处理的绘制任务）
        if (!pendingDraw) {
            pendingDraw = true;
            drawHandler.post(this::drawDanmakuAsync);
        }
    }
    
    private void drawDanmakuAsync() {
        pendingDraw = false;
        
        if (!isSurfaceReady || surfaceHolder == null) {
            return;
        }
        
        // 复用 drawListCache，避免每帧创建新 ArrayList
        drawListCache.clear();
        synchronized (swapLock) {
            drawListCache.addAll(readBuffer);
        }
        
        int danmakuCount = drawListCache.size();
        
        Canvas canvas = null;
        try {
            canvas = surfaceHolder.lockCanvas();
            if (canvas == null) {
                return;
            }
            
            frameCount++;
            
            // 每100帧打印一次性能日志
            if (frameCount % 100 == 0) {
                Log.d(TAG, "帧=" + frameCount + ", 弹幕数=" + danmakuCount);
            }
            
            // 清空画布
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            
            // 绘制每条弹幕
            for (int i = 0, size = drawListCache.size(); i < size; i++) {
                DanmakuEntity entity = drawListCache.get(i);
                if (entity != null && entity.text != null && !entity.text.isEmpty()) {
                    textPaint.setColor(parseColor(entity.color));
                    canvas.drawText(entity.text, entity.currentX, entity.currentY, textPaint);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "弹幕渲染错误", e);
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
        synchronized (swapLock) {
            bufferA.clear();
            bufferB.clear();
        }
        
        if (isSurfaceReady && surfaceHolder != null) {
            drawHandler.post(() -> {
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
            });
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
    
    /**
     * 释放资源
     */
    public void release() {
        if (drawHandler != null) {
            drawHandler.removeCallbacksAndMessages(null);
            drawHandler = null;
        }
        if (drawThread != null) {
            drawThread.quitSafely();
            drawThread = null;
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }
}
