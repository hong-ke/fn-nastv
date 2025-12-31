package com.mynas.nastv.feature.danmaku.logic;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.mynas.nastv.feature.danmaku.api.IDanmuController;
import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;
import com.mynas.nastv.feature.danmaku.model.DanmuConfig;
import com.mynas.nastv.feature.danmaku.view.DanmakuSurfaceView;
import com.mynas.nastv.feature.danmaku.view.DanmakuHardwareView;
import com.mynas.nastv.feature.danmaku.view.DanmakuOverlayView;
import com.mynas.nastv.feature.danmaku.view.IDanmakuView;
import com.mynas.nastv.feature.danmaku.view.DanmuRenderer;

import java.util.List;
import java.util.Map;

/**
 * Danmu Controller Implementation
 */
public class DanmuControllerImpl implements IDanmuController {
    
    private static final String TAG = "DanmuControllerImpl";
    
    private Context context;
    private IDanmakuView overlayView;  // 使用接口，支持多种实现
    private View overlayViewInstance;  // 保存View实例，用于removeView
    private DanmuRenderer renderer;
    private DanmuPresenter presenter;
    private DanmuRepository repository;
    private DanmuConfig config;
    
    // 弹幕视图类型枚举
    public enum DanmakuViewType {
        SURFACE_VIEW,      // SurfaceView（推荐，性能最佳）
        HARDWARE_VIEW,     // 硬件加速View
        OVERLAY_VIEW       // 普通View
    }
    
    private static final DanmakuViewType DEFAULT_VIEW_TYPE = DanmakuViewType.SURFACE_VIEW;
    
    private boolean isInitialized = false;
    
    public DanmuControllerImpl() {
        Log.d(TAG, "DanmuControllerImpl created");
    }
    
    @Override
    public void initialize(Context context, ViewGroup parentContainer) {
        initialize(context, parentContainer, DEFAULT_VIEW_TYPE);
    }
    
    /**
     * 初始化弹幕控制器，支持选择不同的视图实现
     * 
     * @param context 上下文
     * @param parentContainer 父容器
     * @param viewType 视图类型
     *   - HARDWARE_VIEW: 硬件加速View（推荐，无需lockCanvas，性能最佳）
     *   - OVERLAY_VIEW: 普通View（备选方案）
     *   - SURFACE_VIEW: SurfaceView（旧方案，低端设备上lockCanvas慢）
     */
    public void initialize(Context context, ViewGroup parentContainer, DanmakuViewType viewType) {
        if (isInitialized) throw new IllegalStateException("Already initialized");
        this.context = context;
        this.config = DanmuConfig.loadFromPrefs();
        
        // 根据类型创建不同的视图实现
        View view;
        switch (viewType) {
            case HARDWARE_VIEW:
                // 硬件加速View
                view = new DanmakuHardwareView(context);
                Log.d(TAG, "使用 DanmakuHardwareView（硬件加速）");
                break;
            case OVERLAY_VIEW:
                // 普通View
                view = new DanmakuOverlayView(context);
                Log.d(TAG, "使用 DanmakuOverlayView");
                break;
            case SURFACE_VIEW:
            default:
                // 推荐：SurfaceView，性能最佳
                view = new DanmakuSurfaceView(context);
                Log.d(TAG, "使用 DanmakuSurfaceView（推荐，性能最佳）");
                break;
        }
        
        this.overlayView = (IDanmakuView) view;
        this.overlayViewInstance = view;  // 保存View实例
        this.overlayView.setDanmakuAlpha(config.opacity);
        
        parentContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        this.renderer = new DanmuRenderer(context, config);
        this.presenter = new DanmuPresenter(renderer, overlayView);
        this.repository = new DanmuRepository();
        
        view.post(() -> {
            int width = view.getWidth();
            int height = view.getHeight();
            if (width > 0 && height > 0) {
                presenter.updateViewSize(width, height);
            }
        });
        
        isInitialized = true;
    }
    
    @Override
    public void loadDanmaku(String videoId, String episodeId) {
        checkInitialized();
        repository.fetchDanmaku(videoId, episodeId, new DanmuRepository.RepositoryCallback() {
            @Override
            public void onSuccess(Map<String, List<DanmakuEntity>> data) {
                presenter.setDanmakuData(data);
            }
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Load failed", e);
            }
        });
    }

    @Override
    public void loadDanmaku(String title, int episode, int season, String guid, String parentGuid) {
        checkInitialized();
        repository.fetchDanmaku(title, episode, season, guid, parentGuid, new DanmuRepository.RepositoryCallback() {
            @Override
            public void onSuccess(Map<String, List<DanmakuEntity>> data) {
                 Log.d(TAG, "New API Danmaku load success: " + data.size() + " buckets");
                 presenter.setDanmakuData(data);
            }
            @Override
            public void onError(Exception e) {
                 Log.e(TAG, "New API Danmaku load failed", e);
            }
        });
    }
    
    @Override
    public void show() {
        checkInitialized();
        presenter.show();
    }
    
    @Override
    public void hide() {
        checkInitialized();
        presenter.hide();
    }
    
    @Override
    public boolean isVisible() {
        checkInitialized();
        return presenter.isVisible();
    }
    
    @Override
    public void seekTo(long timestampMs) {
        checkInitialized();
        presenter.onSeek(timestampMs);
    }
    
    @Override
    public void updatePlaybackPosition(long currentPositionMs) {
        checkInitialized();
        presenter.onPlaybackPositionUpdate(currentPositionMs);
    }

    @Override
    public void startPlayback() {
        checkInitialized();
        presenter.startPlayback();
    }

    @Override
    public void pausePlayback() {
        checkInitialized();
        presenter.pausePlayback();
    }
    
    @Override
    public void updateConfig(DanmuConfig newConfig) {
        checkInitialized();
        this.config = newConfig != null ? newConfig : DanmuConfig.loadFromPrefs();
        overlayView.setDanmakuAlpha(config.opacity);
        renderer.updateConfig(config);
        presenter.updateConfig(config);
    }

    @Override
    public void clearDanmaku() {
        checkInitialized();
        Log.d(TAG, "清空弹幕缓存");
        presenter.clearDanmaku();
    }

    @Override
    public void destroy() {
        if (!isInitialized) return;
        if (presenter != null) presenter.destroy();
        if (overlayViewInstance != null && overlayViewInstance.getParent() != null) {
            ((ViewGroup)overlayViewInstance.getParent()).removeView(overlayViewInstance);
        }
        overlayView = null;
        overlayViewInstance = null;
        isInitialized = false;
    }
    
    private void checkInitialized() {
        if (!isInitialized) throw new IllegalStateException("Not initialized");
    }
}
