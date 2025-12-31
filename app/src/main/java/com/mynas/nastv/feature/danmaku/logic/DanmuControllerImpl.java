package com.mynas.nastv.feature.danmaku.logic;

import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.mynas.nastv.feature.danmaku.api.IDanmuController;
import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;
import com.mynas.nastv.feature.danmaku.model.DanmuConfig;
import com.mynas.nastv.feature.danmaku.view.DanmakuSurfaceView;
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
    private DanmakuSurfaceView overlayView;  // 使用 SurfaceView，独立渲染不影响主线程
    private DanmuRenderer renderer;
    private DanmuPresenter presenter;
    private DanmuRepository repository;
    private DanmuConfig config;
    
    private boolean isInitialized = false;
    
    public DanmuControllerImpl() {
        Log.d(TAG, "DanmuControllerImpl created");
    }
    
    @Override
    public void initialize(Context context, ViewGroup parentContainer) {
        if (isInitialized) throw new IllegalStateException("Already initialized");
        this.context = context;
        this.config = DanmuConfig.loadFromPrefs();
        // 使用 SurfaceView，独立渲染线程
        this.overlayView = new DanmakuSurfaceView(context);
        this.overlayView.setDanmakuAlpha(config.opacity);
        
        parentContainer.addView(overlayView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        
        this.renderer = new DanmuRenderer(context, config);
        this.presenter = new DanmuPresenter(renderer, overlayView);
        this.repository = new DanmuRepository();
        
        overlayView.post(() -> {
            int width = overlayView.getWidth();
            int height = overlayView.getHeight();
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
        if (overlayView != null && overlayView.getParent() != null) {
            ((ViewGroup)overlayView.getParent()).removeView(overlayView);
        }
        isInitialized = false;
    }
    
    private void checkInitialized() {
        if (!isInitialized) throw new IllegalStateException("Not initialized");
    }
}
