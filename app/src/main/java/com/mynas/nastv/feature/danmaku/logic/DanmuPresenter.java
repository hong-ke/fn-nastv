package com.mynas.nastv.feature.danmaku.logic;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;
import com.mynas.nastv.feature.danmaku.model.DanmuConfig;
import com.mynas.nastv.feature.danmaku.view.IDanmakuView;
import com.mynas.nastv.feature.danmaku.view.DanmuRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 弹幕演示器
 * 
 * 优化版本：使用独立线程进行弹幕计算，不阻塞主线程
 * 
 * 职责：
 * - 管理弹幕渲染状态
 * - 处理播放位置更新
 * - 协调 Renderer 和 OverlayView
 * - 使用独立线程计算弹幕位置
 * 
 * @author nastv
 * @version 2.0
 */
public class DanmuPresenter {
    
    private static final String TAG = "DanmuPresenter";
    
    private final DanmuRenderer renderer;
    private final IDanmakuView overlayView;
    private final Handler mainHandler;
    
    // 独立渲染线程
    private HandlerThread renderThread;
    private Handler renderHandler;
    
    private Map<String, List<DanmakuEntity>> danmakuData;
    private volatile long currentPositionMs = 0;
    private volatile boolean isPlaying = false;
    private volatile boolean isVisible = true;
    
    // 帧时间控制
    private long lastRenderTimeMs = 0;
    private static final long FRAME_INTERVAL_MS = 33; // 30fps
    
    // FPS 监控（仅用于日志）
    private int frameCount = 0;
    private long lastFpsCalculationTimeMs = 0;
    
    // 渲染循环 Runnable
    private final Runnable renderRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlaying || !isVisible) {
                // 继续调度下一帧，等待恢复
                if (isPlaying && renderHandler != null) {
                    renderHandler.postDelayed(this, FRAME_INTERVAL_MS);
                }
                return;
            }
            
            long now = System.currentTimeMillis();
            float deltaTimeMs;
            
            if (lastRenderTimeMs > 0) {
                deltaTimeMs = now - lastRenderTimeMs;
                if (deltaTimeMs > 100f) {
                    deltaTimeMs = 100f;
                }
            } else {
                deltaTimeMs = FRAME_INTERVAL_MS;
            }
            lastRenderTimeMs = now;
            
            // FPS 监控（仅日志）
            frameCount++;
            if (now - lastFpsCalculationTimeMs >= 1000) {
                frameCount = 0;
                lastFpsCalculationTimeMs = now;
            }
            
            // 在渲染线程计算弹幕
            updateDanmakuFrame(deltaTimeMs);
            
            // 调度下一帧
            if (isPlaying && renderHandler != null) {
                renderHandler.postDelayed(this, FRAME_INTERVAL_MS);
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
        
        Log.d(TAG, "DanmuPresenter 初始化完成（独立渲染线程）");
    }

    
    /**
     * 设置弹幕数据
     */
    public void setDanmakuData(Map<String, List<DanmakuEntity>> data) {
        this.danmakuData = data;
        renderer.setDanmakuData(data);
        Log.d(TAG, "弹幕数据已设置，共 " + (data != null ? data.size() : 0) + " 个时间桶");
    }
    
    /**
     * 更新播放位置
     */
    public void onPlaybackPositionUpdate(long positionMs) {
        this.currentPositionMs = positionMs;
    }
    
    /**
     * 在渲染线程更新弹幕
     * 
     * 优化：复用列表，避免每帧创建新对象
     */
    private void updateDanmakuFrame(float deltaTimeMs) {
        if (danmakuData == null || danmakuData.isEmpty()) {
            return;
        }
        
        try {
            // 在渲染线程计算可见弹幕
            final List<DanmakuEntity> visibleList = renderer.calculateVisibleDanmakuSmooth(currentPositionMs, deltaTimeMs);
            
            // 调试日志（降低频率）
            if (System.currentTimeMillis() % 1000 < 50) {
                Log.d(TAG, "弹幕帧更新: position=" + currentPositionMs + "ms, visible=" + visibleList.size());
            }
            
            // 渲染弹幕
            if (!visibleList.isEmpty() || frameCount % 30 == 0) {
                if (isVisible) {
                    overlayView.renderDanmaku(visibleList);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "更新弹幕帧失败", e);
        }
    }
    
    /**
     * 跳转到指定位置
     */
    public void onSeek(long newPositionMs) {
        Log.d(TAG, "跳转到位置: " + newPositionMs + "ms");
        this.currentPositionMs = newPositionMs;
        
        // 在渲染线程清除弹幕
        if (renderHandler != null) {
            renderHandler.post(() -> {
                renderer.clear();
                mainHandler.post(() -> overlayView.clearDanmaku());
            });
        }
        
        lastRenderTimeMs = 0;
    }
    
    /**
     * 开始播放
     */
    public void startPlayback() {
        if (!isPlaying) {
            isPlaying = true;
            Log.d(TAG, "开始播放弹幕");
            
            lastRenderTimeMs = 0;
            lastFpsCalculationTimeMs = System.currentTimeMillis();
            frameCount = 0;
            
            // 在渲染线程启动渲染循环
            if (renderHandler != null) {
                renderHandler.post(renderRunnable);
            }
        }
    }
    
    /**
     * 暂停播放
     */
    public void pausePlayback() {
        if (isPlaying) {
            isPlaying = false;
            Log.d(TAG, "暂停播放弹幕");
            
            if (renderHandler != null) {
                renderHandler.removeCallbacks(renderRunnable);
            }
        }
    }
    
    /**
     * 显示弹幕
     */
    public void show() {
        if (!isVisible) {
            isVisible = true;
            Log.d(TAG, "显示弹幕");
            if (isPlaying && renderHandler != null) {
                lastRenderTimeMs = 0;
                renderHandler.post(renderRunnable);
            }
        }
    }
    
    /**
     * 隐藏弹幕
     */
    public void hide() {
        if (isVisible) {
            isVisible = false;
            Log.d(TAG, "隐藏弹幕");
            mainHandler.post(() -> overlayView.clearDanmaku());
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
    
    /**
     * 清空弹幕缓存
     */
    public void clearDanmaku() {
        Log.d(TAG, "清空弹幕缓存");
        pausePlayback();
        this.danmakuData = null;
        this.currentPositionMs = 0;
        
        if (renderHandler != null) {
            renderHandler.post(() -> renderer.clear());
        }
        mainHandler.post(() -> overlayView.clearDanmaku());
    }
    
    /**
     * 销毁资源
     */
    public void destroy() {
        Log.d(TAG, "销毁 DanmuPresenter");
        pausePlayback();
        
        if (renderHandler != null) {
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
