package com.mynas.nastv.feature.danmaku.api;

import android.content.Context;
import android.view.ViewGroup;

import com.mynas.nastv.feature.danmaku.model.DanmuConfig;

/**
 * 弹幕控制器接口
 */
public interface IDanmuController {
    
    void initialize(Context context, ViewGroup parentContainer);
    
    // Legacy
    void loadDanmaku(String videoId, String episodeId);
    
    // New API - 使用 title, season, episode, guid, parentGuid
    void loadDanmaku(String title, int episode, int season, String guid, String parentGuid);
    
    void show();
    void hide();
    boolean isVisible();
    void seekTo(long timestampMs);
    void updatePlaybackPosition(long currentPositionMs);
    void startPlayback();
    void pausePlayback();
    void updateConfig(DanmuConfig config);
    void destroy();
}
