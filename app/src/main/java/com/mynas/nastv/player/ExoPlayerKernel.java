package com.mynas.nastv.player;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.mynas.nastv.cache.OkHttpProxyCacheManager;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 📝 ExoPlayer 内核
 * 用于播放带有内嵌字幕的视频
 * 支持 onCues 回调获取内嵌字幕
 * 🔑 支持 OkHttpProxyCacheManager 本地代理缓存
 */
public class ExoPlayerKernel implements Player.Listener {
    private static final String TAG = "ExoPlayerKernel";
    
    private Context context;
    private ExoPlayer exoPlayer;
    private TextureView textureView;
    private Surface surface;
    
    // 回调接口
    private SubtitleCallback subtitleCallback;
    private PlayerCallback playerCallback;
    
    // 状态
    private boolean isPrepared = false;
    private int videoWidth = 0;
    private int videoHeight = 0;
    
    // 🔑 缓存相关
    private boolean useProxyCache = false;
    private String originalUrl = null;
    
    /**
     * 字幕回调接口
     */
    public interface SubtitleCallback {
        void onSubtitleChanged(List<androidx.media3.common.text.Cue> cues);
    }
    
    /**
     * 播放器回调接口
     */
    public interface PlayerCallback {
        void onPrepared();
        void onError(String error);
        void onCompletion();
        void onBuffering(boolean isBuffering);
        void onVideoSizeChanged(int width, int height);
    }
    
    public ExoPlayerKernel(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public void setSubtitleCallback(SubtitleCallback callback) {
        this.subtitleCallback = callback;
    }
    
    public void setPlayerCallback(PlayerCallback callback) {
        this.playerCallback = callback;
    }
    
    /**
     * 初始化播放器
     */
    public void init(Map<String, String> headers) {
        Log.i(TAG, "📝 初始化 ExoPlayer 内核");
        
        // 创建 DataSource.Factory
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(60000);
        
        if (headers != null && !headers.isEmpty()) {
            httpFactory.setDefaultRequestProperties(headers);
            Log.i(TAG, "📝 设置请求头: " + headers.keySet());
        }
        
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context, httpFactory);
        
        // 创建 LoadControl - 优化缓冲策略
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5000,   // minBufferMs
                30000,  // maxBufferMs
                1000,   // bufferForPlaybackMs
                2000    // bufferForPlaybackAfterRebufferMs
            )
            .build();
        
        // 创建 RenderersFactory
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
        
        // 创建 ExoPlayer
        exoPlayer = new ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
            .build();
        
        // 添加监听器
        exoPlayer.addListener(this);
        
        Log.i(TAG, "📝 ExoPlayer 初始化完成");
    }

    /**
     * 设置 Surface
     */
    public void setSurface(Surface surface) {
        this.surface = surface;
        if (exoPlayer != null) {
            exoPlayer.setVideoSurface(surface);
        }
    }
    
    /**
     * 设置 TextureView
     */
    public void setTextureView(TextureView textureView) {
        this.textureView = textureView;
        if (exoPlayer != null && textureView != null) {
            exoPlayer.setVideoTextureView(textureView);
        }
    }
    
    /**
     * 播放视频
     */
    public void play(String url) {
        if (exoPlayer == null) {
            Log.e(TAG, "📝 ExoPlayer 未初始化");
            return;
        }
        
        Log.i(TAG, "📝 播放视频: " + url.substring(0, Math.min(80, url.length())));
        
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }
    
    /**
     * 🔑 使用代理缓存播放视频
     * @param originUrl 原始视频 URL
     * @param headers 请求头
     * @param cacheDir 缓存目录
     */
    public void playWithProxyCache(String originUrl, Map<String, String> headers, File cacheDir) {
        if (exoPlayer == null) {
            Log.e(TAG, "📝 ExoPlayer 未初始化");
            return;
        }
        
        this.originalUrl = originUrl;
        this.useProxyCache = true;
        
        // 🔑 设置 ExoPlayer 正在使用代理（防止 GSYVideoPlayer release 时停止代理）
        OkHttpProxyCacheManager.setExoPlayerUsingProxy(true);
        
        // 🔑 设置 OkHttpProxyCacheManager 的 headers
        OkHttpProxyCacheManager.setCurrentHeaders(headers);
        Log.i(TAG, "🔑 ExoPlayer: OkHttpProxyCacheManager headers set");
        
        // 🔑 通过 OkHttpProxyCacheManager 获取代理 URL
        OkHttpProxyCacheManager cacheManager = OkHttpProxyCacheManager.instance();
        String proxyUrl = cacheManager.getProxyUrl(context, originUrl, headers, cacheDir);
        
        if (proxyUrl != null && !proxyUrl.equals(originUrl)) {
            Log.i(TAG, "🔑 ExoPlayer 使用代理缓存: " + proxyUrl);
        } else {
            Log.i(TAG, "📝 ExoPlayer 直接播放（无代理）: " + originUrl.substring(0, Math.min(80, originUrl.length())));
            proxyUrl = originUrl;
        }
        
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(proxyUrl));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }
    
    /**
     * 开始播放
     */
    public void start() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(true);
        }
    }
    
    /**
     * 暂停
     */
    public void pause() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
        }
    }
    
    /**
     * 停止
     */
    public void stop() {
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
    }
    
    /**
     * 跳转
     */
    public void seekTo(long positionMs) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(positionMs);
        }
    }
    
    /**
     * 获取当前位置
     */
    public long getCurrentPosition() {
        if (exoPlayer != null) {
            return exoPlayer.getCurrentPosition();
        }
        return 0;
    }
    
    /**
     * 获取总时长
     */
    public long getDuration() {
        if (exoPlayer != null) {
            return exoPlayer.getDuration();
        }
        return 0;
    }
    
    /**
     * 是否正在播放
     */
    public boolean isPlaying() {
        if (exoPlayer != null) {
            return exoPlayer.isPlaying();
        }
        return false;
    }
    
    /**
     * 设置播放速度
     */
    public void setSpeed(float speed) {
        if (exoPlayer != null && speed > 0) {
            exoPlayer.setPlaybackParameters(new PlaybackParameters(speed));
        }
    }
    
    /**
     * 设置音量
     */
    public void setVolume(float volume) {
        if (exoPlayer != null) {
            exoPlayer.setVolume(volume);
        }
    }
    
    /**
     * 获取视频宽度
     */
    public int getVideoWidth() {
        return videoWidth;
    }
    
    /**
     * 获取视频高度
     */
    public int getVideoHeight() {
        return videoHeight;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        Log.i(TAG, "📝 释放 ExoPlayer");
        
        // 🔑 清除 ExoPlayer 使用代理的标志
        if (useProxyCache) {
            OkHttpProxyCacheManager.setExoPlayerUsingProxy(false);
        }
        
        if (exoPlayer != null) {
            exoPlayer.removeListener(this);
            exoPlayer.release();
            exoPlayer = null;
        }
        isPrepared = false;
        useProxyCache = false;
        originalUrl = null;
    }
    
    // ==================== Player.Listener 回调 ====================
    
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        String stateName = "UNKNOWN";
        switch (playbackState) {
            case Player.STATE_IDLE: stateName = "IDLE"; break;
            case Player.STATE_BUFFERING: stateName = "BUFFERING"; break;
            case Player.STATE_READY: stateName = "READY"; break;
            case Player.STATE_ENDED: stateName = "ENDED"; break;
        }
        Log.i(TAG, "📝 播放状态: " + stateName);
        
        if (playbackState == Player.STATE_READY && !isPrepared) {
            isPrepared = true;
            if (playerCallback != null) {
                playerCallback.onPrepared();
            }
        } else if (playbackState == Player.STATE_BUFFERING) {
            if (playerCallback != null) {
                playerCallback.onBuffering(true);
            }
        } else if (playbackState == Player.STATE_READY) {
            if (playerCallback != null) {
                playerCallback.onBuffering(false);
            }
        } else if (playbackState == Player.STATE_ENDED) {
            if (playerCallback != null) {
                playerCallback.onCompletion();
            }
        }
    }
    
    @Override
    public void onPlayerError(PlaybackException error) {
        Log.e(TAG, "📝 播放错误: " + error.getMessage(), error);
        if (playerCallback != null) {
            playerCallback.onError(error.getMessage());
        }
    }
    
    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
        videoWidth = videoSize.width;
        videoHeight = videoSize.height;
        Log.i(TAG, "📝 视频尺寸: " + videoWidth + "x" + videoHeight);
        if (playerCallback != null) {
            playerCallback.onVideoSizeChanged(videoWidth, videoHeight);
        }
    }
    
    @Override
    public void onCues(CueGroup cueGroup) {
        // 📝 关键：内嵌字幕回调
        if (subtitleCallback != null && cueGroup != null && cueGroup.cues != null) {
            subtitleCallback.onSubtitleChanged(cueGroup.cues);
            if (!cueGroup.cues.isEmpty()) {
                Log.d(TAG, "📝 字幕: " + cueGroup.cues.get(0).text);
            }
        }
    }
}
