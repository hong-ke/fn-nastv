package com.mynas.nastv.player;

import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import java.util.List;
import java.util.Map;

/**
 * 播放器内核接口
 * 统一 IJKPlayer 和 ExoPlayer 的操作接口
 */
public interface PlayerKernel {
    
    /**
     * 播放器回调接口
     */
    interface PlayerCallback {
        void onPrepared();
        void onError(String error);
        void onCompletion();
        void onBuffering(boolean isBuffering);
        void onVideoSizeChanged(int width, int height);
    }
    
    /**
     * 字幕回调接口
     */
    interface SubtitleCallback {
        void onSubtitleChanged(List<androidx.media3.common.text.Cue> cues);
        void onTimedText(String text);
    }
    
    /**
     * 初始化播放器
     */
    void init(Map<String, String> headers);
    
    /**
     * 设置 Surface
     */
    void setSurface(Surface surface);
    
    /**
     * 设置 TextureView
     */
    void setTextureView(TextureView textureView);
    
    /**
     * 播放视频（直接 URL）
     */
    void play(String url);
    
    /**
     * 使用代理缓存播放视频
     */
    void playWithProxyCache(String originUrl, Map<String, String> headers, java.io.File cacheDir);
    
    /**
     * 开始播放
     */
    void start();
    
    /**
     * 暂停
     */
    void pause();
    
    /**
     * 停止
     */
    void stop();
    
    /**
     * 跳转
     */
    void seekTo(long positionMs);
    
    /**
     * 获取当前位置
     */
    long getCurrentPosition();
    
    /**
     * 获取总时长
     */
    long getDuration();
    
    /**
     * 是否正在播放
     */
    boolean isPlaying();
    
    /**
     * 设置播放速度
     */
    void setSpeed(float speed);
    
    /**
     * 设置音量
     */
    void setVolume(float volume);
    
    /**
     * 获取视频宽度
     */
    int getVideoWidth();
    
    /**
     * 获取视频高度
     */
    int getVideoHeight();
    
    /**
     * 获取播放器视图（用于显示/隐藏）
     */
    View getPlayerView();
    
    /**
     * 设置播放器回调
     */
    void setPlayerCallback(PlayerCallback callback);
    
    /**
     * 设置字幕回调
     */
    void setSubtitleCallback(SubtitleCallback callback);
    
    /**
     * 选择音频轨道
     */
    void selectAudioTrack(int trackIndex);
    
    /**
     * 选择字幕轨道
     */
    void selectSubtitleTrack(int trackIndex);
    
    /**
     * 获取音频轨道信息
     */
    List<TrackInfo> getAudioTracks();
    
    /**
     * 获取字幕轨道信息
     */
    List<TrackInfo> getSubtitleTracks();
    
    /**
     * 释放资源
     */
    void release();
    
    /**
     * 重置播放器
     */
    void reset();
    
    /**
     * 轨道信息
     */
    class TrackInfo {
        public int index;
        public String language;
        public String title;
        public String codec;
        
        public TrackInfo(int index, String language, String title, String codec) {
            this.index = index;
            this.language = language;
            this.title = title;
            this.codec = codec;
        }
    }
}

