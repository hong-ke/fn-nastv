package com.mynas.nastv.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.TrackSelectionParameters;
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
 * ExoPlayer 内核
 * 用于播放带有内嵌字幕的视频
 * 支持 onCues 回调获取内嵌字幕
 * 支持 OkHttpProxyCacheManager 本地代理缓存
 */
public class ExoPlayerKernel implements PlayerKernel, Player.Listener {
    private static final String TAG = "ExoPlayerKernel";
    
    private Context context;
    private ExoPlayer exoPlayer;
    private TextureView textureView;
    private Surface surface;
    
    // 回调接口（使用 PlayerKernel 接口）
    private PlayerKernel.SubtitleCallback subtitleCallback;
    private PlayerKernel.PlayerCallback playerCallback;
    
    // 兼容旧的接口（保留用于向后兼容）
    @Deprecated
    public interface SubtitleCallback {
        void onSubtitleChanged(List<androidx.media3.common.text.Cue> cues);
    }
    
    @Deprecated
    public interface PlayerCallback {
        void onPrepared();
        void onError(String error);
        void onCompletion();
        void onBuffering(boolean isBuffering);
        void onVideoSizeChanged(int width, int height);
    }
    
    // 状态
    private boolean isPrepared = false;
    private int videoWidth = 0;
    private int videoHeight = 0;
    
    // 缓存相关
    private boolean useProxyCache = false;
    private String originalUrl = null;
    
    // 主线程 Handler，确保 ExoPlayer 操作在主线程执行
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    public ExoPlayerKernel(Context context) {
        this.context = context.getApplicationContext();
    }
    
    @Override
    public void setSubtitleCallback(PlayerKernel.SubtitleCallback callback) {
        this.subtitleCallback = callback;
    }
    
    @Override
    public void setPlayerCallback(PlayerKernel.PlayerCallback callback) {
        this.playerCallback = callback;
    }
    
    // 兼容旧接口
    @Deprecated
    public void setSubtitleCallback(SubtitleCallback callback) {
        if (callback != null) {
            this.subtitleCallback = new PlayerKernel.SubtitleCallback() {
                @Override
                public void onSubtitleChanged(List<androidx.media3.common.text.Cue> cues) {
                    callback.onSubtitleChanged(cues);
                }
                
                @Override
                public void onTimedText(String text) {
                    // ExoPlayer 不使用 onTimedText，只使用 onSubtitleChanged
                }
            };
        }
    }
    
    @Deprecated
    public void setPlayerCallback(PlayerCallback callback) {
        if (callback != null) {
            this.playerCallback = new PlayerKernel.PlayerCallback() {
                @Override
                public void onPrepared() {
                    callback.onPrepared();
                }
                
                @Override
                public void onError(String error) {
                    callback.onError(error);
                }
                
                @Override
                public void onCompletion() {
                    callback.onCompletion();
                }
                
                @Override
                public void onBuffering(boolean isBuffering) {
                    callback.onBuffering(isBuffering);
                }
                
                @Override
                public void onVideoSizeChanged(int width, int height) {
                    callback.onVideoSizeChanged(width, height);
                }
            };
        }
    }
    
    /**
     * 初始化播放器
     */
    public void init(Map<String, String> headers) {
        Log.d(TAG, "初始化 ExoPlayer 内核");
        
        // 创建 DataSource.Factory
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(60000);
        
        if (headers != null && !headers.isEmpty()) {
            httpFactory.setDefaultRequestProperties(headers);
            Log.d(TAG, "设置请求头: " + headers.keySet());
        }
        
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context, httpFactory);
        
        // 创建 LoadControl - 优化缓冲策略：缩短首播与 seek 后等待时间
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5_000,   // minBufferMs：降低到 5s，加快启动和 seek 后恢复
                30_000,  // maxBufferMs：30s，足够平衡稳定性
                700,     // bufferForPlaybackMs：首播前缓冲 0.7s
                1_500    // bufferForPlaybackAfterRebufferMs：seek/重缓冲后缓冲 1.5s
            )
            .setTargetBufferBytes(C.LENGTH_UNSET) // 不限制缓冲大小
            .setPrioritizeTimeOverSizeThresholds(false) // 保持画质优先
            .build();
        
        // 创建 RenderersFactory - 优先使用硬件解码器
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true); // 启用解码器回退，确保兼容性
        
        // 创建 ExoPlayer
        exoPlayer = new ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING) // 视频缩放模式
            .build();

        // 画质优先：强制选择设备支持的最高码率/分辨率轨道
        TrackSelectionParameters qualityParams = exoPlayer.getTrackSelectionParameters()
                .buildUpon()
                .setForceHighestSupportedBitrate(true)
                .build();
        exoPlayer.setTrackSelectionParameters(qualityParams);
        
        // 添加监听器
        exoPlayer.addListener(this);
        
        // 设置视频缩放模式为高质量
        exoPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        
        Log.d(TAG, "ExoPlayer 初始化完成（已优化画质设置）");
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
            Log.e(TAG, "ExoPlayer 未初始化");
            return;
        }
        
        Log.d(TAG, "播放视频: " + url.substring(0, Math.min(80, url.length())));
        
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }
    
    /**
     * 使用代理缓存播放视频
     * @param originUrl 原始视频 URL
     * @param headers 请求头
     * @param cacheDir 缓存目录
     */
    public void playWithProxyCache(String originUrl, Map<String, String> headers, File cacheDir) {
        if (exoPlayer == null) {
            Log.e(TAG, "ExoPlayer 未初始化");
            return;
        }
        
        this.originalUrl = originUrl;
        this.useProxyCache = true;
        
        // 设置 ExoPlayer 正在使用代理（防止 GSYVideoPlayer release 时停止代理）
        OkHttpProxyCacheManager.setExoPlayerUsingProxy(true);
        
        // 设置 OkHttpProxyCacheManager 的 headers
        OkHttpProxyCacheManager.setCurrentHeaders(headers);
        Log.d(TAG, "ExoPlayer: OkHttpProxyCacheManager headers set");
        
        // 通过 OkHttpProxyCacheManager 获取代理 URL
        OkHttpProxyCacheManager cacheManager = OkHttpProxyCacheManager.instance();
        String proxyUrl = cacheManager.getProxyUrl(context, originUrl, headers, cacheDir);
        
        if (proxyUrl != null && !proxyUrl.equals(originUrl)) {
            Log.d(TAG, "ExoPlayer 使用代理缓存: " + proxyUrl);
        } else {
            Log.d(TAG, "ExoPlayer 直接播放（无代理）: " + originUrl.substring(0, Math.min(80, originUrl.length())));
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
            Log.d(TAG, "start() 调用 - 当前状态: " + getPlaybackStateName() + ", isPlaying: " + exoPlayer.isPlaying());
            // 确保在主线程执行
            if (Looper.myLooper() == Looper.getMainLooper()) {
                exoPlayer.setPlayWhenReady(true);
                Log.d(TAG, "start() 执行完成 - setPlayWhenReady(true)");
            } else {
                mainHandler.post(() -> {
                    if (exoPlayer != null) {
                        exoPlayer.setPlayWhenReady(true);
                        Log.d(TAG, "start() 执行完成（异步）- setPlayWhenReady(true)");
                    }
                });
            }
        } else {
            Log.e(TAG, "start() 失败 - ExoPlayer 为 null");
        }
    }
    
    /**
     * 暂停
     */
    public void pause() {
        if (exoPlayer != null) {
            Log.d(TAG, "pause() 调用 - 当前状态: " + getPlaybackStateName() + ", isPlaying: " + exoPlayer.isPlaying());
            // 确保在主线程执行
            if (Looper.myLooper() == Looper.getMainLooper()) {
                exoPlayer.setPlayWhenReady(false);
                Log.d(TAG, "pause() 执行完成 - setPlayWhenReady(false)");
            } else {
                mainHandler.post(() -> {
                    if (exoPlayer != null) {
                        exoPlayer.setPlayWhenReady(false);
                        Log.d(TAG, "pause() 执行完成（异步）- setPlayWhenReady(false)");
                    }
                });
            }
        } else {
            Log.e(TAG, "pause() 失败 - ExoPlayer 为 null");
        }
    }
    
    /**
     * 停止
     */
    public void stop() {
        if (exoPlayer != null) {
            // 确保在主线程执行
            if (Looper.myLooper() == Looper.getMainLooper()) {
                exoPlayer.stop();
            } else {
                mainHandler.post(() -> {
                    if (exoPlayer != null) {
                        exoPlayer.stop();
                    }
                });
            }
        }
    }
    
    /**
     * 跳转
     */
    public void seekTo(long positionMs) {
        if (exoPlayer != null) {
            int currentState = exoPlayer.getPlaybackState();
            Log.d(TAG, "seekTo() 调用 - 目标位置: " + positionMs + "ms, 当前状态: " + getPlaybackStateName() + ", 当前位置: " + exoPlayer.getCurrentPosition() + "ms");
            
            // 确保在主线程执行，并且播放器已准备就绪（允许在 BUFFERING 和 READY 状态下执行）
            if (Looper.myLooper() == Looper.getMainLooper()) {
                if (currentState != Player.STATE_IDLE && currentState != Player.STATE_ENDED) {
                    exoPlayer.seekTo(positionMs);
                    Log.d(TAG, "seekTo() 执行完成 - 跳转到: " + positionMs + "ms");
                } else {
                    Log.w(TAG, "seekTo() 失败 - ExoPlayer 状态不允许: " + getPlaybackStateName());
                }
            } else {
                mainHandler.post(() -> {
                    if (exoPlayer != null) {
                        int state = exoPlayer.getPlaybackState();
                        if (state != Player.STATE_IDLE && state != Player.STATE_ENDED) {
                            exoPlayer.seekTo(positionMs);
                            Log.d(TAG, "seekTo() 执行完成（异步）- 跳转到: " + positionMs + "ms");
                        } else {
                            Log.w(TAG, "seekTo() 失败（异步）- ExoPlayer 状态不允许: " + getStateName(state));
                        }
                    }
                });
            }
        } else {
            Log.e(TAG, "seekTo() 失败 - ExoPlayer 为 null");
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
     * ExoPlayer 的 isPlaying() 返回 getPlayWhenReady() && getPlaybackState() == STATE_READY
     * 这里直接使用 ExoPlayer 的 isPlaying() 方法
     */
    public boolean isPlaying() {
        if (exoPlayer != null) {
            // ExoPlayer 的 isPlaying() 是线程安全的
            boolean playing = exoPlayer.isPlaying();
            boolean playWhenReady = exoPlayer.getPlayWhenReady();
            int state = exoPlayer.getPlaybackState();
            Log.d(TAG, "isPlaying() - 返回: " + playing + ", playWhenReady: " + playWhenReady + ", 状态: " + getStateName(state));
            return playing;
        }
        Log.d(TAG, "isPlaying() - ExoPlayer 为 null，返回 false");
        return false;
    }
    
    /**
     * 获取播放状态名称（用于日志）
     */
    private String getPlaybackStateName() {
        if (exoPlayer == null) {
            return "NULL";
        }
        return getStateName(exoPlayer.getPlaybackState());
    }
    
    /**
     * 将状态码转换为名称
     */
    private String getStateName(int state) {
        switch (state) {
            case Player.STATE_IDLE: return "IDLE";
            case Player.STATE_BUFFERING: return "BUFFERING";
            case Player.STATE_READY: return "READY";
            case Player.STATE_ENDED: return "ENDED";
            default: return "UNKNOWN(" + state + ")";
        }
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
    
    @Override
    public View getPlayerView() {
        return textureView;
    }
    
    @Override
    public void selectAudioTrack(int trackIndex) {
        // ExoPlayer 的音频轨道选择需要更复杂的实现
        // 暂时留空，后续可以扩展
    }
    
    @Override
    public void selectSubtitleTrack(int trackIndex) {
        // ExoPlayer 的字幕轨道选择需要更复杂的实现
        // 暂时留空，后续可以扩展
    }
    
    @Override
    public List<TrackInfo> getAudioTracks() {
        // ExoPlayer 的轨道信息获取需要更复杂的实现
        return new java.util.ArrayList<>();
    }
    
    @Override
    public List<TrackInfo> getSubtitleTracks() {
        // ExoPlayer 的轨道信息获取需要更复杂的实现
        return new java.util.ArrayList<>();
    }
    
    /**
     * 释放资源
     */
    public void release() {
        Log.d(TAG, "释放 ExoPlayer");
        
        // 清除 ExoPlayer 使用代理的标志
        if (useProxyCache) {
            OkHttpProxyCacheManager.setExoPlayerUsingProxy(false);
        }
        
        if (exoPlayer != null) {
            exoPlayer.removeListener(this);
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            exoPlayer.release();
            exoPlayer = null;
        }
        isPrepared = false;
        useProxyCache = false;
        originalUrl = null;
        videoWidth = 0;
        videoHeight = 0;
    }
    
    /**
     * 重置播放器（用于切换视频）
     */
    public void reset() {
        Log.d(TAG, "重置 ExoPlayer");
        
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }
        isPrepared = false;
        videoWidth = 0;
        videoHeight = 0;
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
        Log.d(TAG, "播放状态: " + stateName);
        
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
        Log.e(TAG, "播放错误: " + error.getMessage(), error);
        
        // 检查是否是可恢复的错误
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
            // 网络相关错误，尝试重试
            Log.d(TAG, "网络错误，尝试重试...");
            if (exoPlayer != null) {
                exoPlayer.prepare();
            }
            return;
        }
        
        // MediaCodec 错误通常是解码器问题，可以尝试继续播放
        if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED) {
            Log.w(TAG, "解码器错误，尝试继续播放...");
            // 不回调错误，让播放器尝试恢复
            return;
        }
        
        if (playerCallback != null) {
            playerCallback.onError(error.getMessage());
        }
    }
    
    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
        videoWidth = videoSize.width;
        videoHeight = videoSize.height;
        Log.d(TAG, "视频尺寸: " + videoWidth + "x" + videoHeight);
        if (playerCallback != null) {
            playerCallback.onVideoSizeChanged(videoWidth, videoHeight);
        }
    }
    
    @Override
    public void onCues(CueGroup cueGroup) {
        // 关键：内嵌字幕回调
        if (subtitleCallback != null && cueGroup != null && cueGroup.cues != null) {
            subtitleCallback.onSubtitleChanged(cueGroup.cues);
            if (!cueGroup.cues.isEmpty()) {
                Log.d(TAG, "字幕: " + cueGroup.cues.get(0).text);
            }
        }
    }
}
