package com.mynas.nastv.feature.danmaku.logic;

import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.util.Log;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;
import com.mynas.nastv.feature.danmaku.model.DanmuConfig;
import com.mynas.nastv.feature.danmaku.view.DanmakuOverlayView;
import com.mynas.nastv.feature.danmaku.view.DanmuRenderer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 弹幕演示器
 * 
 * 职责：
 * - 管理弹幕渲染状态
 * - 处理播放位置更新
 * - 协调 Renderer 和 OverlayView
 * - 实现 PTS 同步（FR-06, FR-07）
 * - 使用 Choreographer 实现帧同步的线性滚动
 * 
 * @author nastv
 * @version 1.0
 */
public class DanmuPresenter {
    
    private static final String TAG = "DanmuPresenter";
    
    private final DanmuRenderer renderer;
    private final DanmakuOverlayView overlayView;
    private final Handler updateHandler;
    
    private Map<String, List<DanmakuEntity>> danmakuData;
    private long currentPositionMs = 0;
    private boolean isPlaying = false;
    private boolean isVisible = true;
    
    // 帧同步滚动相关
    private long lastFrameTimeNanos = 0;
    private long lastRenderTimeNanos = 0;
    
    // FPS 监控与熔断相关 (Epic 4)
    private int frameCount = 0;
    private long lastFpsCalculationTimeMs = 0;
    private int lowFpsStreak = 0; // 连续低 FPS 计数
    private boolean isCircuitBroken = false; // 熔断状态

    /**
     * 帧同步回调 - 实现线性滚动
     * 使用 Choreographer 确保每帧更新，避免卡顿
     */
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!isPlaying || !isVisible) {
                return;
            }
            
            // 计算帧间隔时间（纳秒转毫秒）
            float deltaTimeMs = 0;
            if (lastRenderTimeNanos > 0) {
                deltaTimeMs = (frameTimeNanos - lastRenderTimeNanos) / 1_000_000f;
            }
            lastRenderTimeNanos = frameTimeNanos;
            
            // FPS 监控
            frameCount++;
            long now = System.currentTimeMillis();
            if (now - lastFpsCalculationTimeMs >= 1000) {
                float currentFps = frameCount * 1000f / (now - lastFpsCalculationTimeMs);
                // Log.v(TAG, "弹幕 FPS: " + currentFps);
                checkCircuitBreaker(currentFps);
                frameCount = 0;
                lastFpsCalculationTimeMs = now;
            }
            
            // 更新弹幕位置并渲染
            if (!isCircuitBroken) {
                updateDanmakuFrame(deltaTimeMs);
            }
            
            // 继续下一帧
            Choreographer.getInstance().postFrameCallback(this);
        }
    };
    
    public DanmuPresenter(DanmuRenderer renderer, DanmakuOverlayView overlayView) {
        this.renderer = renderer;
        this.overlayView = overlayView;
        this.updateHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * 设置弹幕数据
     * 
     * @param data 弹幕数据映射
     */
    public void setDanmakuData(Map<String, List<DanmakuEntity>> data) {
        this.danmakuData = data;
        renderer.setDanmakuData(data);
        Log.d(TAG, "弹幕数据已设置");
    }
    
    /**
     * 更新播放位置
     * 
     * @param positionMs 当前播放位置（毫秒）
     */
    public void onPlaybackPositionUpdate(long positionMs) {
        this.currentPositionMs = positionMs;
        // 位置更新由 frameCallback 处理，这里只更新时间戳
    }
    
    /**
     * 帧同步更新弹幕
     * 
     * @param deltaTimeMs 距离上一帧的时间间隔（毫秒）
     */
    private void updateDanmakuFrame(float deltaTimeMs) {
        if (danmakuData == null || danmakuData.isEmpty()) {
            return;
        }
        
        try {
            // 使用帧间隔时间计算可见弹幕（实现线性滚动）
            List<DanmakuEntity> visibleList = renderer.calculateVisibleDanmakuSmooth(currentPositionMs, deltaTimeMs);
            
            // 调试日志：每秒打印一次
            if (System.currentTimeMillis() % 1000 < 20) {
                Log.d(TAG, "弹幕帧更新: position=" + currentPositionMs + "ms, visible=" + visibleList.size());
            }
            
            // 更新视图
            overlayView.renderDanmaku(visibleList);
            
        } catch (Exception e) {
            Log.e(TAG, "更新弹幕帧失败", e);
        }
    }
    
    /**
     * 跳转到指定位置（Seek操作）
     * 
     * @param newPositionMs 新的播放位置（毫秒）
     */
    public void onSeek(long newPositionMs) {
        Log.d(TAG, "跳转到位置: " + newPositionMs + "ms");
        this.currentPositionMs = newPositionMs;
        
        // 清除旧弹幕
        renderer.clear();
        overlayView.clearDanmaku();
        
        // 重置帧时间
        lastRenderTimeNanos = 0;
    }
    
    /**
     * 开始播放
     */
    public void startPlayback() {
        if (!isPlaying) {
            isPlaying = true;
            Log.d(TAG, "开始播放弹幕");
            
            // 重置帧时间
            lastRenderTimeNanos = 0;
            lastFpsCalculationTimeMs = System.currentTimeMillis();
            frameCount = 0;
            
            // 启动帧同步回调
            Choreographer.getInstance().postFrameCallback(frameCallback);
        }
    }
    
    /**
     * 暂停播放
     */
    public void pausePlayback() {
        if (isPlaying) {
            isPlaying = false;
            Log.d(TAG, "暂停播放弹幕");
            Choreographer.getInstance().removeFrameCallback(frameCallback);
        }
    }
    
    /**
     * 显示弹幕
     */
    public void show() {
        if (!isVisible) {
            isVisible = true;
            Log.d(TAG, "显示弹幕");
            if (isPlaying) {
                lastRenderTimeNanos = 0;
                Choreographer.getInstance().postFrameCallback(frameCallback);
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
            overlayView.clearDanmaku();
            Choreographer.getInstance().removeFrameCallback(frameCallback);
        }
    }

    /**
     * 检查并触发熔断机制 (Story 4.2)
     */
    private void checkCircuitBreaker(float fps) {
        if (fps < 20) {
            lowFpsStreak++;
            if (lowFpsStreak >= 3) { // 持续 3 秒低 FPS
                if (!isCircuitBroken) {
                    isCircuitBroken = true;
                    Log.w(TAG, "触发熔断：FPS 持续过低 (" + fps + ")，自动隐藏弹幕以保护性能");
                    mainHandler.post(() -> {
                        overlayView.clearDanmaku();
                    });
                }
            }
        } else {
            lowFpsStreak = 0;
            if (isCircuitBroken && fps > 30) {
                isCircuitBroken = false;
                Log.i(TAG, "️ 熔断恢复：性能已回升 (" + fps + ")");
            }
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    /**
     * 检查弹幕是否可见
     * 
     * @return true 如果可见
     */
    public boolean isVisible() {
        return isVisible;
    }
    
    /**
     * 更新视图尺寸
     * 
     * @param width  宽度
     * @param height 高度
     */
    public void updateViewSize(int width, int height) {
        renderer.updateViewSize(width, height);
        overlayView.setTextSize(renderer.getFontSize());
    }
    
    /**
     * 更新配置
     * 
     * @param config 新配置
     */
    public void updateConfig(DanmuConfig config) {
        renderer.updateConfig(config);
        overlayView.setDanmakuAlpha(config.opacity);
        overlayView.setTextSize(renderer.getFontSize());
    }
    
    /**
     * 清空弹幕缓存数据
     * 用于切换剧集时清除旧弹幕
     */
    public void clearDanmaku() {
        Log.d(TAG, "清空弹幕缓存数据");
        pausePlayback();
        this.danmakuData = null;
        this.currentPositionMs = 0;
        renderer.clear();
        overlayView.clearDanmaku();
    }
    
    /**
     * 销毁资源
     */
    public void destroy() {
        pausePlayback();
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        renderer.clear();
        overlayView.clearDanmaku();
        Log.d(TAG, "DanmuPresenter 已销毁");
    }
}
