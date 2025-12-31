package com.mynas.nastv.feature.danmaku.logic;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;
import com.mynas.nastv.feature.danmaku.model.DanmuConfig;
import com.mynas.nastv.feature.danmaku.view.IDanmakuView;
import com.mynas.nastv.feature.danmaku.view.DanmuRenderer;

import java.util.List;
import java.util.Map;

/**
 * 弹幕演示器
 * 
 * 优化版本：使用独立线程进行弹幕计算和渲染
 * 使用 Choreographer 同步 VSync，但在渲染线程执行
 * 
 * @author nastv
 * @version 3.1 (渲染线程 Choreographer)
 */
public class DanmuPresenter {
    
    private static final String TAG = "DanmuPresenter";
    
    private final DanmuRenderer renderer;
    private final IDanmakuView overlayView;
    private final Handler mainHandler;
    
    // 独立渲染线程
    private HandlerThread renderThread;
    private Handler renderHandler;
    private Choreographer renderChoreographer;
    
    private Map<String, List<DanmakuEntity>> danmakuData;
    private volatile long currentPositionMs = 0;
    private volatile boolean isPlaying = false;
    private volatile boolean isVisible = true;
    
    // 帧时间控制（使用纳秒精度）
    private long lastFrameTimeNanos = 0;
    
    // FPS 监控
    private int frameCount = 0;
    private long lastFpsCalculationTimeMs = 0;
    
    // Choreographer 帧回调 - 在渲染线程执行
    // 优化：每3帧更新一次弹幕位置（约20fps），减少绘制调用
    private int frameSkipCounter = 0;
    private static final int FRAME_SKIP = 3; // 每3帧计算一次位置（60/3=20fps）
    private float accumulatedDeltaMs = 0; // 累积的时间差
    
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!isPlaying || !isVisible) {
                return;
            }
            
            // 继续注册下一帧回调
            if (renderChoreographer != null) {
                renderChoreographer.postFrameCallback(this);
            }
            
            // 计算精确的 deltaTime
            final float deltaTimeMs;
            if (lastFrameTimeNanos > 0) {
                float calculatedDelta = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000f;
                deltaTimeMs = Math.min(calculatedDelta, 100f);
            } else {
                deltaTimeMs = 16.67f;
            }
            lastFrameTimeNanos = frameTimeNanos;
            
            // 累积时间
            accumulatedDeltaMs += deltaTimeMs;
            
            // FPS 监控
            long now = System.currentTimeMillis();
            frameCount++;
            if (now - lastFpsCalculationTimeMs >= 1000) {
                frameCount = 0;
                lastFpsCalculationTimeMs = now;
            }
            
            // 每3帧更新一次弹幕（约20fps）
            frameSkipCounter++;
            if (frameSkipCounter >= FRAME_SKIP) {
                frameSkipCounter = 0;
                // 使用累积的 deltaTime
                updateDanmakuFrame(accumulatedDeltaMs);
                accumulatedDeltaMs = 0;
            }
        }
    };
    
    public DanmuPresenter(DanmuRenderer renderer, IDanmakuView overlayView) {
        this.renderer = renderer;
        this.overlayView = overlayView;
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // 创建独立渲染线程
        renderThread = new HandlerThread("DanmakuRenderThread");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
        
        // 在渲染线程创建 Choreographer
        renderHandler.post(() -> {
            renderChoreographer = Choreographer.getInstance();
        });
        
        Log.d(TAG, "DanmuPresenter 初始化完成（渲染线程 Choreographer）");
    }
    
    public void setDanmakuData(Map<String, List<DanmakuEntity>> data) {
        this.danmakuData = data;
        renderer.setDanmakuData(data);
        Log.d(TAG, "弹幕数据已设置，共 " + (data != null ? data.size() : 0) + " 个时间桶");
    }
    
    public void onPlaybackPositionUpdate(long positionMs) {
        this.currentPositionMs = positionMs;
    }
    
    private void updateDanmakuFrame(float deltaTimeMs) {
        if (danmakuData == null || danmakuData.isEmpty()) {
            return;
        }
        
        try {
            final List<DanmakuEntity> visibleList = renderer.calculateVisibleDanmakuSmooth(currentPositionMs, deltaTimeMs);
            
            if (isVisible) {
                overlayView.renderDanmaku(visibleList);
            }
        } catch (Exception e) {
            Log.e(TAG, "更新弹幕帧失败", e);
        }
    }
    
    public void onSeek(long newPositionMs) {
        Log.d(TAG, "跳转到位置: " + newPositionMs + "ms");
        this.currentPositionMs = newPositionMs;
        
        if (renderHandler != null) {
            renderHandler.post(() -> {
                renderer.clear();
                overlayView.clearDanmaku();
            });
        }
        lastFrameTimeNanos = 0;
    }
    
    public void startPlayback() {
        if (!isPlaying) {
            isPlaying = true;
            Log.d(TAG, "开始播放弹幕");
            
            lastFrameTimeNanos = 0;
            lastFpsCalculationTimeMs = System.currentTimeMillis();
            frameCount = 0;
            
            // 在渲染线程启动 Choreographer
            renderHandler.post(() -> {
                if (renderChoreographer != null) {
                    renderChoreographer.postFrameCallback(frameCallback);
                }
            });
        }
    }
    
    public void pausePlayback() {
        if (isPlaying) {
            isPlaying = false;
            Log.d(TAG, "暂停播放弹幕");
            
            renderHandler.post(() -> {
                if (renderChoreographer != null) {
                    renderChoreographer.removeFrameCallback(frameCallback);
                }
            });
        }
    }
    
    public void show() {
        if (!isVisible) {
            isVisible = true;
            Log.d(TAG, "显示弹幕");
            if (isPlaying) {
                lastFrameTimeNanos = 0;
                renderHandler.post(() -> {
                    if (renderChoreographer != null) {
                        renderChoreographer.postFrameCallback(frameCallback);
                    }
                });
            }
        }
    }
    
    public void hide() {
        if (isVisible) {
            isVisible = false;
            Log.d(TAG, "隐藏弹幕");
            renderHandler.post(() -> overlayView.clearDanmaku());
        }
    }
    
    public boolean isVisible() {
        return isVisible;
    }
    
    public void updateViewSize(int width, int height) {
        renderer.updateViewSize(width, height);
        overlayView.setTextSize(renderer.getFontSize());
    }
    
    public void updateConfig(DanmuConfig config) {
        renderer.updateConfig(config);
        overlayView.setDanmakuAlpha(config.opacity);
        overlayView.setTextSize(renderer.getFontSize());
    }
    
    public void clearDanmaku() {
        Log.d(TAG, "清空弹幕缓存");
        pausePlayback();
        this.danmakuData = null;
        this.currentPositionMs = 0;
        
        if (renderHandler != null) {
            renderHandler.post(() -> renderer.clear());
        }
        renderHandler.post(() -> overlayView.clearDanmaku());
    }
    
    public void destroy() {
        Log.d(TAG, "销毁 DanmuPresenter");
        pausePlayback();
        
        if (renderHandler != null) {
            renderHandler.post(() -> {
                if (renderChoreographer != null) {
                    renderChoreographer.removeFrameCallback(frameCallback);
                }
            });
            renderHandler.removeCallbacksAndMessages(null);
            renderHandler = null;
        }
        
        if (renderThread != null) {
            renderThread.quitSafely();
            renderThread = null;
        }
        
        renderer.clear();
        overlayView.clearDanmaku();
    }
}
