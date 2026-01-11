package com.mynas.nastv.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
 * Media3 视频播放器
 * 
 * 基于 androidx.media3 ExoPlayer 实现
 * 使用 OkHttpProxyCacheManager 实现代理缓存
 */
public class Media3VideoPlayer extends FrameLayout implements Player.Listener {
    
    private static final String TAG = "Media3VideoPlayer";
    
    private Context context;
    private ExoPlayer exoPlayer;
    private SurfaceView surfaceView;
    private TextureView textureView;
    
    // 渲染模式
    public enum RenderMode {
        SURFACE_VIEW,
        TEXTURE_VIEW
    }
    
    private RenderMode renderMode = RenderMode.SURFACE_VIEW;
    
    // 回调
    private PlayerCallback playerCallback;
    private SubtitleCallback subtitleCallback;
    
    // 状态
    private boolean isPrepared = false;
    private int videoWidth = 0;
    private int videoHeight = 0;
    private String currentUrl;
    private Map<String, String> currentHeaders;
    
    // 代理缓存
    private boolean useProxyCache = false;
    private String originalUrl = null;
    
    // 重试机制
    private static final int MAX_RETRY_COUNT = 3;
    private int retryCount = 0;
    private boolean isRetrying = false;
    
    // 主线程 Handler
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    public interface PlayerCallback {
        void onPrepared();
        void onError(String error);
        void onCompletion();
        void onBuffering(boolean isBuffering);
        void onVideoSizeChanged(int width, int height);
    }
    
    public interface SubtitleCallback {
        void onSubtitleChanged(List<androidx.media3.common.text.Cue> cues);
    }
    
    public Media3VideoPlayer(@NonNull Context context) {
        super(context);
        init(context);
    }
    
    public Media3VideoPlayer(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public Media3VideoPlayer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        this.context = context.getApplicationContext();
        setBackgroundColor(0xFF000000);
    }
    
    public void setRenderMode(RenderMode mode) {
        this.renderMode = mode;
    }
    
    /**
     * 初始化播放器
     */
    public void setup(Map<String, String> headers) {
        this.currentHeaders = headers;
        
        // 创建渲染视图
        createRenderView();
        
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
        
        // 创建 LoadControl - 优化缓冲策略
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 5_000, 5_000)
            .setTargetBufferBytes(C.LENGTH_UNSET)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build();
        
        // 创建 RenderersFactory - 优先硬件解码，启用高质量渲染，减少丢帧
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
            // 启用异步解码队列，提升解码性能和画质稳定性
            .forceEnableMediaCodecAsynchronousQueueing()
            // 允许更长的视频无缝切换时间，减少切换时的画质损失
            .setAllowedVideoJoiningTimeMs(5000);
        
        // 创建 ExoPlayer - 使用高质量缩放模式，优化帧保留
        exoPlayer = new ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
            // 默认使用适应模式 - 保持原始比例，可能有黑边
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            // 设置 Seek 步进
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(10000)
            .build();
        
        // 设置精确 Seek 模式，确保 seek 到精确位置而不是最近的关键帧
        exoPlayer.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT);
        
        // 画质优先设置 - 强制最高质量
        TrackSelectionParameters qualityParams = exoPlayer.getTrackSelectionParameters()
            .buildUpon()
            .setForceHighestSupportedBitrate(true)  // 强制最高码率
            .setMaxVideoFrameRate(Integer.MAX_VALUE)  // 不限制帧率
            .setMaxVideoBitrate(Integer.MAX_VALUE)  // 不限制码率
            .setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)  // 不限制分辨率
            .setMinVideoFrameRate(0)  // 不设置最低帧率限制
            .setMinVideoBitrate(0)  // 不设置最低码率限制
            .setMinVideoSize(0, 0)  // 不设置最低分辨率限制
            .setViewportSizeToPhysicalDisplaySize(context, true)  // 根据屏幕选择最佳分辨率
            // 优先选择高质量编码格式
            .setPreferredVideoMimeTypes("video/hevc", "video/avc", "video/av01")
            .build();
        exoPlayer.setTrackSelectionParameters(qualityParams);
        
        // 添加监听器
        exoPlayer.addListener(this);
        
        // 绑定渲染视图
        if (renderMode == RenderMode.SURFACE_VIEW && surfaceView != null) {
            exoPlayer.setVideoSurfaceView(surfaceView);
        } else if (textureView != null) {
            exoPlayer.setVideoTextureView(textureView);
        }
        
        Log.d(TAG, "Media3VideoPlayer 初始化完成");
    }
    
    private void createRenderView() {
        removeAllViews();
        
        if (renderMode == RenderMode.SURFACE_VIEW) {
            surfaceView = new SurfaceView(context);
            surfaceView.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            addView(surfaceView);
            Log.d(TAG, "使用 SurfaceView 渲染");
        } else {
            textureView = new TextureView(context);
            textureView.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            textureView.setOpaque(false);
            addView(textureView);
            Log.d(TAG, "使用 TextureView 渲染");
        }
    }
    
    /**
     * 播放视频（直接播放）
     */
    public void play(String url) {
        if (exoPlayer == null) {
            Log.e(TAG, "ExoPlayer 未初始化");
            return;
        }
        
        this.currentUrl = url;
        Log.d(TAG, "播放: " + url.substring(0, Math.min(80, url.length())));
        
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }
    
    /**
     * 使用代理缓存播放视频
     */
    public void playWithCache(String originUrl, Map<String, String> headers, File cacheDir) {
        if (exoPlayer == null) {
            Log.e(TAG, "ExoPlayer 未初始化");
            return;
        }
        
        // 重置准备状态，确保切换视频时能重新触发 onPrepared
        isPrepared = false;
        
        this.originalUrl = originUrl;
        this.useProxyCache = true;
        
        // 设置 ExoPlayer 正在使用代理
        OkHttpProxyCacheManager.setExoPlayerUsingProxy(true);
        
        // 设置 headers
        OkHttpProxyCacheManager.setCurrentHeaders(headers);
        Log.d(TAG, "设置代理缓存 headers");
        
        // 获取代理 URL
        OkHttpProxyCacheManager cacheManager = OkHttpProxyCacheManager.instance();
        String proxyUrl = cacheManager.getProxyUrl(context, originUrl, headers, cacheDir);
        
        if (proxyUrl != null && !proxyUrl.equals(originUrl)) {
            Log.d(TAG, "使用代理缓存: " + proxyUrl);
        } else {
            Log.d(TAG, "直接播放（无代理）: " + originUrl.substring(0, Math.min(80, originUrl.length())));
            proxyUrl = originUrl;
        }
        
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(proxyUrl));
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }
    
    /**
     * 使用缓存播放（兼容旧接口）
     */
    public void playWithCache(String url, File cacheDir) {
        playWithCache(url, currentHeaders, cacheDir);
    }
    
    // ==================== 播放控制 ====================
    
    public void start() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(true);
        }
    }
    
    public void pause() {
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
        }
    }
    
    public void stop() {
        if (exoPlayer != null) {
            exoPlayer.stop();
        }
    }
    
    public void seekTo(long positionMs) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(positionMs);
        }
    }
    
    public long getCurrentPosition() {
        return exoPlayer != null ? exoPlayer.getCurrentPosition() : 0;
    }
    
    public long getDuration() {
        return exoPlayer != null ? exoPlayer.getDuration() : 0;
    }
    
    public int getBufferedPercentage() {
        return exoPlayer != null ? exoPlayer.getBufferedPercentage() : 0;
    }
    
    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }
    
    public void setSpeed(float speed) {
        if (exoPlayer != null && speed > 0) {
            exoPlayer.setPlaybackParameters(new PlaybackParameters(speed));
        }
    }
    
    public void setVolume(float volume) {
        if (exoPlayer != null) {
            exoPlayer.setVolume(volume);
        }
    }
    
    /**
     * 设置视频缩放模式
     * @param mode 0=适应(可能有黑边), 1=裁剪填充(可能裁剪), 2=拉伸填充
     */
    public void setVideoScalingMode(int mode) {
        if (exoPlayer != null) {
            int scalingMode;
            switch (mode) {
                case 1:
                    // 裁剪填充 - 保持比例，裁剪多余部分，画质最清晰
                    scalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING;
                    break;
                case 2:
                    // 拉伸填充 - 可能变形
                    scalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT;
                    break;
                default:
                    // 默认适应 - 保持比例，可能有黑边
                    scalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT;
                    break;
            }
            exoPlayer.setVideoScalingMode(scalingMode);
            Log.d(TAG, "设置视频缩放模式: " + mode);
        }
    }
    
    /**
     * 强制选择最高画质轨道
     */
    public void forceHighestQuality() {
        if (exoPlayer != null) {
            TrackSelectionParameters params = exoPlayer.getTrackSelectionParameters()
                .buildUpon()
                .setForceHighestSupportedBitrate(true)
                .setMaxVideoFrameRate(Integer.MAX_VALUE)
                .setMaxVideoBitrate(Integer.MAX_VALUE)
                .setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)
                .build();
            exoPlayer.setTrackSelectionParameters(params);
            Log.d(TAG, "已强制选择最高画质");
        }
    }
    
    // ==================== 视频轨道选择 ====================
    
    /**
     * 视频轨道信息
     */
    public static class VideoTrackInfo {
        public int groupIndex;
        public int trackIndex;
        public String codec;
        public String mimeType;
        public int width;
        public int height;
        public int frameRate;
        public int bitrate;
        public boolean isSelected;
        public boolean isSupported;
        public String colorInfo;  // HDR 信息
        
        public String getDisplayName() {
            StringBuilder sb = new StringBuilder();
            // 分辨率
            if (width > 0 && height > 0) {
                sb.append(width).append("x").append(height);
            }
            // 编码格式
            if (codec != null && !codec.isEmpty()) {
                sb.append(" ").append(codec);
            }
            // HDR 信息
            if (colorInfo != null && !colorInfo.isEmpty()) {
                sb.append(" ").append(colorInfo);
            }
            // 帧率
            if (frameRate > 0) {
                sb.append(" ").append(frameRate).append("fps");
            }
            // 码率
            if (bitrate > 0) {
                sb.append(" ").append(bitrate / 1000000).append("Mbps");
            }
            if (!isSupported) {
                sb.append(" [不支持]");
            }
            return sb.toString().trim();
        }
    }
    
    /**
     * 获取所有视频轨道
     */
    public java.util.List<VideoTrackInfo> getVideoTracks() {
        java.util.List<VideoTrackInfo> tracks = new java.util.ArrayList<>();
        if (exoPlayer == null) return tracks;
        
        androidx.media3.common.Tracks currentTracks = exoPlayer.getCurrentTracks();
        int groupIndex = 0;
        
        for (androidx.media3.common.Tracks.Group group : currentTracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_VIDEO) {
                for (int i = 0; i < group.length; i++) {
                    androidx.media3.common.Format format = group.getTrackFormat(i);
                    VideoTrackInfo info = new VideoTrackInfo();
                    info.groupIndex = groupIndex;
                    info.trackIndex = i;
                    info.mimeType = format.sampleMimeType;
                    info.codec = getVideoCodecName(format.sampleMimeType);
                    info.width = format.width;
                    info.height = format.height;
                    info.frameRate = (int) format.frameRate;
                    info.bitrate = format.bitrate;
                    info.isSelected = group.isTrackSelected(i);
                    info.isSupported = group.isTrackSupported(i);
                    info.colorInfo = getColorInfoString(format);
                    tracks.add(info);
                    Log.d(TAG, "视频轨道: " + info.getDisplayName() + ", selected=" + info.isSelected + ", supported=" + info.isSupported);
                }
            }
            groupIndex++;
        }
        
        return tracks;
    }
    
    /**
     * 获取视频编解码器友好名称
     */
    private String getVideoCodecName(String mimeType) {
        if (mimeType == null) return "";
        switch (mimeType) {
            case "video/hevc": return "HEVC";
            case "video/avc": return "H.264";
            case "video/av01": return "AV1";
            case "video/vp9": return "VP9";
            case "video/vp8": return "VP8";
            case "video/dolby-vision": return "Dolby Vision";
            case "video/x-vnd.on2.vp9": return "VP9";
            default: return mimeType.replace("video/", "").toUpperCase();
        }
    }
    
    /**
     * 获取 HDR/色彩信息字符串
     */
    private String getColorInfoString(androidx.media3.common.Format format) {
        if (format.colorInfo == null) return "";
        
        androidx.media3.common.ColorInfo colorInfo = format.colorInfo;
        StringBuilder sb = new StringBuilder();
        
        // 检查 HDR 类型
        int colorTransfer = colorInfo.colorTransfer;
        int colorSpace = colorInfo.colorSpace;
        
        // HDR10
        if (colorTransfer == C.COLOR_TRANSFER_ST2084) {
            sb.append("HDR10");
        }
        // HLG
        else if (colorTransfer == C.COLOR_TRANSFER_HLG) {
            sb.append("HLG");
        }
        // Dolby Vision
        else if (colorTransfer == C.COLOR_TRANSFER_LINEAR) {
            // 可能是 Dolby Vision
        }
        
        // BT.2020 色域
        if (colorSpace == C.COLOR_SPACE_BT2020) {
            if (sb.length() > 0) sb.append(" ");
            sb.append("BT.2020");
        }
        
        return sb.toString();
    }
    
    /**
     * 选择视频轨道
     */
    public void selectVideoTrack(int groupIndex, int trackIndex) {
        if (exoPlayer == null) return;
        
        androidx.media3.common.Tracks currentTracks = exoPlayer.getCurrentTracks();
        int currentGroupIndex = 0;
        
        for (androidx.media3.common.Tracks.Group group : currentTracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_VIDEO && currentGroupIndex == groupIndex) {
                TrackSelectionParameters.Builder builder = exoPlayer.getTrackSelectionParameters().buildUpon();
                builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO);
                
                androidx.media3.common.TrackSelectionOverride override = 
                    new androidx.media3.common.TrackSelectionOverride(
                        group.getMediaTrackGroup(), 
                        java.util.Collections.singletonList(trackIndex)
                    );
                builder.addOverride(override);
                
                exoPlayer.setTrackSelectionParameters(builder.build());
                Log.d(TAG, "已选择视频轨道: groupIndex=" + groupIndex + ", trackIndex=" + trackIndex);
                return;
            }
            if (group.getType() == C.TRACK_TYPE_VIDEO) {
                currentGroupIndex++;
            }
        }
    }
    
    /**
     * 检查当前视频轨道是否支持
     * @return null 如果支持，否则返回不支持的原因
     */
    public String checkVideoTrackSupport() {
        java.util.List<VideoTrackInfo> tracks = getVideoTracks();
        if (tracks.isEmpty()) {
            return "未检测到视频轨道";
        }
        
        // 检查是否有选中的轨道
        for (VideoTrackInfo track : tracks) {
            if (track.isSelected) {
                if (!track.isSupported) {
                    return "视频格式不支持: " + track.getDisplayName();
                }
                return null; // 支持
            }
        }
        
        // 没有选中的轨道，检查是否有任何支持的轨道
        for (VideoTrackInfo track : tracks) {
            if (track.isSupported) {
                return null; // 有支持的轨道
            }
        }
        
        return "所有视频轨道均不支持";
    }
    
    // ==================== 音频轨道选择 ====================
    
    /**
     * 音频轨道信息
     */
    public static class AudioTrackInfo {
        public int groupIndex;
        public int trackIndex;
        public String language;
        public String label;
        public String codec;
        public String mimeType;
        public int channelCount;
        public int sampleRate;
        public boolean isSelected;
        public boolean isSupported;  // 是否支持该格式
        
        public String getDisplayName() {
            StringBuilder sb = new StringBuilder();
            if (label != null && !label.isEmpty()) {
                sb.append(label);
            } else if (language != null && !language.isEmpty()) {
                sb.append(language);
            } else {
                sb.append("音轨 " + (trackIndex + 1));
            }
            if (codec != null && !codec.isEmpty()) {
                sb.append(" (").append(codec);
                if (channelCount > 0) {
                    sb.append(" ").append(channelCount).append("ch");
                }
                sb.append(")");
            }
            if (!isSupported) {
                sb.append(" [不支持]");
            }
            return sb.toString();
        }
    }
    
    /**
     * 获取所有音频轨道
     */
    public java.util.List<AudioTrackInfo> getAudioTracks() {
        java.util.List<AudioTrackInfo> tracks = new java.util.ArrayList<>();
        if (exoPlayer == null) return tracks;
        
        androidx.media3.common.Tracks currentTracks = exoPlayer.getCurrentTracks();
        int groupIndex = 0;
        
        for (androidx.media3.common.Tracks.Group group : currentTracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_AUDIO) {
                for (int i = 0; i < group.length; i++) {
                    androidx.media3.common.Format format = group.getTrackFormat(i);
                    AudioTrackInfo info = new AudioTrackInfo();
                    info.groupIndex = groupIndex;
                    info.trackIndex = i;
                    info.language = format.language;
                    info.label = format.label;
                    info.mimeType = format.sampleMimeType;
                    info.codec = getCodecName(format.sampleMimeType);
                    info.channelCount = format.channelCount;
                    info.sampleRate = format.sampleRate;
                    info.isSelected = group.isTrackSelected(i);
                    // 检查格式是否支持
                    info.isSupported = group.isTrackSupported(i);
                    tracks.add(info);
                    Log.d(TAG, "音频轨道: " + info.getDisplayName() + ", selected=" + info.isSelected + ", supported=" + info.isSupported);
                }
            }
            groupIndex++;
        }
        
        return tracks;
    }
    
    /**
     * 选择音频轨道
     */
    public void selectAudioTrack(int groupIndex, int trackIndex) {
        if (exoPlayer == null) return;
        
        androidx.media3.common.Tracks currentTracks = exoPlayer.getCurrentTracks();
        int currentGroupIndex = 0;
        
        for (androidx.media3.common.Tracks.Group group : currentTracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_AUDIO && currentGroupIndex == groupIndex) {
                // 构建轨道选择覆盖
                TrackSelectionParameters.Builder builder = exoPlayer.getTrackSelectionParameters().buildUpon();
                
                // 清除之前的音频轨道覆盖
                builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO);
                
                // 设置新的音频轨道覆盖
                androidx.media3.common.TrackSelectionOverride override = 
                    new androidx.media3.common.TrackSelectionOverride(
                        group.getMediaTrackGroup(), 
                        java.util.Collections.singletonList(trackIndex)
                    );
                builder.addOverride(override);
                
                exoPlayer.setTrackSelectionParameters(builder.build());
                Log.d(TAG, "已选择音频轨道: groupIndex=" + groupIndex + ", trackIndex=" + trackIndex);
                return;
            }
            if (group.getType() == C.TRACK_TYPE_AUDIO) {
                currentGroupIndex++;
            }
        }
    }
    
    /**
     * 获取编解码器友好名称
     */
    private String getCodecName(String mimeType) {
        if (mimeType == null) return "";
        switch (mimeType) {
            case "audio/eac3": return "EAC3";
            case "audio/ac3": return "AC3";
            case "audio/mp4a-latm": return "AAC";
            case "audio/mpeg": return "MP3";
            case "audio/opus": return "Opus";
            case "audio/vorbis": return "Vorbis";
            case "audio/flac": return "FLAC";
            case "audio/raw": return "PCM";
            case "audio/true-hd": return "TrueHD";
            case "audio/mlp": return "TrueHD";
            case "audio/vnd.dts": return "DTS";
            case "audio/vnd.dts.hd": return "DTS-HD";
            default: return mimeType.replace("audio/", "").toUpperCase();
        }
    }
    
    public int getVideoWidth() {
        return videoWidth;
    }
    
    public int getVideoHeight() {
        return videoHeight;
    }
    
    // ==================== 回调设置 ====================
    
    public void setPlayerCallback(PlayerCallback callback) {
        this.playerCallback = callback;
    }
    
    public void setSubtitleCallback(SubtitleCallback callback) {
        this.subtitleCallback = callback;
    }
    
    // ==================== Player.Listener ====================
    
    @Override
    public void onTracksChanged(@NonNull androidx.media3.common.Tracks tracks) {
        Log.d(TAG, "========== 轨道信息变化 ==========");
        int videoTrackCount = 0;
        int audioTrackCount = 0;
        
        for (androidx.media3.common.Tracks.Group group : tracks.getGroups()) {
            int trackType = group.getType();
            String typeName = trackType == C.TRACK_TYPE_VIDEO ? "VIDEO" : 
                             trackType == C.TRACK_TYPE_AUDIO ? "AUDIO" : 
                             trackType == C.TRACK_TYPE_TEXT ? "TEXT" : "OTHER(" + trackType + ")";
            
            Log.d(TAG, "轨道组: " + typeName + ", 轨道数: " + group.length);
            
            for (int i = 0; i < group.length; i++) {
                androidx.media3.common.Format format = group.getTrackFormat(i);
                boolean isSelected = group.isTrackSelected(i);
                boolean isSupported = group.isTrackSupported(i);
                
                if (trackType == C.TRACK_TYPE_VIDEO) {
                    videoTrackCount++;
                    String colorInfo = "";
                    if (format.colorInfo != null) {
                        colorInfo = ", colorTransfer=" + format.colorInfo.colorTransfer + 
                                   ", colorSpace=" + format.colorInfo.colorSpace;
                    }
                    Log.d(TAG, "  视频轨道[" + i + "]: " + format.width + "x" + format.height + 
                          ", codec=" + format.sampleMimeType + 
                          ", selected=" + isSelected + 
                          ", supported=" + isSupported + colorInfo);
                } else if (trackType == C.TRACK_TYPE_AUDIO) {
                    audioTrackCount++;
                    Log.d(TAG, "  音频轨道[" + i + "]: " + format.sampleMimeType + 
                          ", ch=" + format.channelCount + 
                          ", selected=" + isSelected + 
                          ", supported=" + isSupported);
                }
            }
        }
        
        Log.d(TAG, "总计: 视频轨道=" + videoTrackCount + ", 音频轨道=" + audioTrackCount);
        Log.d(TAG, "========================================");
    }
    
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        switch (playbackState) {
            case Player.STATE_READY:
                // 播放成功，重置重试计数
                if (isRetrying) {
                    Log.d(TAG, "重试成功，播放器已就绪");
                    isRetrying = false;
                    retryCount = 0;
                }
                
                if (!isPrepared) {
                    isPrepared = true;
                    if (playerCallback != null) {
                        playerCallback.onPrepared();
                    }
                }
                if (playerCallback != null) {
                    playerCallback.onBuffering(false);
                }
                break;
            case Player.STATE_BUFFERING:
                if (playerCallback != null) {
                    playerCallback.onBuffering(true);
                }
                break;
            case Player.STATE_ENDED:
                if (playerCallback != null) {
                    playerCallback.onCompletion();
                }
                break;
        }
    }
    
    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Log.e(TAG, "播放错误: " + error.getMessage(), error);
        
        // 检查错误类型，判断是否可重试
        boolean isRetryable = isRetryableError(error);
        
        // 如果使用代理缓存且错误是流中断，可能是代理缓存问题，直接回退
        if (useProxyCache && isRetryable && error.getMessage() != null && 
            error.getMessage().contains("unexpected end of stream")) {
            if (retryCount == 0) {
                // 第一次遇到流中断，直接回退到直接播放
                Log.w(TAG, "代理缓存流中断，直接回退到直接播放");
                fallbackToDirectPlay();
                return;
            }
        }
        
        if (isRetryable && retryCount < MAX_RETRY_COUNT && !isRetrying) {
            retryCount++;
            Log.w(TAG, "尝试重试播放 (第 " + retryCount + " 次)");
            isRetrying = true;
            
            // 延迟重试，避免立即重试导致的问题
            mainHandler.postDelayed(() -> {
                retryPlayback();
            }, 1000 * retryCount); // 递增延迟：1s, 2s, 3s
        } else {
            // 如果不可重试或已达到最大重试次数，尝试回退到直接播放
            if (useProxyCache && (retryCount >= MAX_RETRY_COUNT || !isRetryable)) {
                Log.w(TAG, "代理缓存播放失败，尝试直接播放");
                fallbackToDirectPlay();
            } else {
                // 最终失败，通知回调
                if (playerCallback != null) {
                    String errorMsg = error.getMessage();
                    if (error.getCause() != null) {
                        errorMsg += ": " + error.getCause().getMessage();
                    }
                    playerCallback.onError(errorMsg);
                }
            }
        }
    }
    
    /**
     * 判断错误是否可重试
     */
    private boolean isRetryableError(PlaybackException error) {
        int errorCode = error.errorCode;
        
        // 网络相关错误可以重试
        if (errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
            errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED) {
            return true;
        }
        
        // 检查错误消息中是否包含可重试的关键词
        String errorMsg = error.getMessage();
        if (errorMsg != null) {
            String lowerMsg = errorMsg.toLowerCase();
            if (lowerMsg.contains("unexpected end of stream") ||
                lowerMsg.contains("protocol exception") ||
                lowerMsg.contains("connection") ||
                lowerMsg.contains("timeout") ||
                lowerMsg.contains("network") ||
                lowerMsg.contains("source error")) {
                return true;
            }
        }
        
        // 检查异常原因
        Throwable cause = error.getCause();
        if (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null) {
                String lowerCause = causeMsg.toLowerCase();
                if (lowerCause.contains("unexpected end of stream") ||
                    lowerCause.contains("protocol exception") ||
                    lowerCause.contains("connection")) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 重试播放
     */
    private void retryPlayback() {
        if (exoPlayer == null) {
            isRetrying = false;
            return;
        }
        
        try {
            // 重置播放器状态
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            
            // 重新准备播放
            if (useProxyCache && originalUrl != null) {
                // 重新使用代理缓存播放
                File cacheDir = new File(context.getCacheDir(), "video_cache");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
                playWithCache(originalUrl, currentHeaders, cacheDir);
            } else if (currentUrl != null) {
                // 直接播放
                play(currentUrl);
            } else {
                Log.e(TAG, "重试失败：没有可用的播放 URL");
                isRetrying = false;
                if (playerCallback != null) {
                    playerCallback.onError("重试失败：没有可用的播放 URL");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "重试播放异常: " + e.getMessage(), e);
            isRetrying = false;
            if (playerCallback != null) {
                playerCallback.onError("重试失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 回退到直接播放（不使用代理缓存）
     */
    private void fallbackToDirectPlay() {
        if (exoPlayer == null || originalUrl == null) {
            Log.e(TAG, "无法回退到直接播放：缺少必要信息");
            if (playerCallback != null) {
                playerCallback.onError("播放失败，且无法回退到直接播放");
            }
            return;
        }
        
        Log.w(TAG, "回退到直接播放: " + originalUrl.substring(0, Math.min(80, originalUrl.length())));
        
        try {
            // 清除代理标志
            OkHttpProxyCacheManager.setExoPlayerUsingProxy(false);
            
            // 重置状态
            useProxyCache = false;
            retryCount = 0;
            isRetrying = false;
            
            // 停止当前播放
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
            
            // 直接播放原始 URL
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(originalUrl));
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);
            
            Log.d(TAG, "已切换到直接播放模式");
        } catch (Exception e) {
            Log.e(TAG, "回退到直接播放失败: " + e.getMessage(), e);
            isRetrying = false;
            if (playerCallback != null) {
                playerCallback.onError("播放失败: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        videoWidth = videoSize.width;
        videoHeight = videoSize.height;
        Log.d(TAG, "视频尺寸: " + videoWidth + "x" + videoHeight);
        if (playerCallback != null) {
            playerCallback.onVideoSizeChanged(videoWidth, videoHeight);
        }
    }
    
    @Override
    public void onCues(@NonNull CueGroup cueGroup) {
        if (subtitleCallback != null && cueGroup.cues != null) {
            subtitleCallback.onSubtitleChanged(cueGroup.cues);
        }
    }
    
    // ==================== 生命周期 ====================
    
    public void release() {
        Log.d(TAG, "释放 Media3VideoPlayer");
        
        // 清除代理标志
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
    
    public void reset() {
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }
        isPrepared = false;
        retryCount = 0;
        isRetrying = false;
    }
}
