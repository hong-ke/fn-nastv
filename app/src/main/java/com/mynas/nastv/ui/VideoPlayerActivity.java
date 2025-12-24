package com.mynas.nastv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;

import com.mynas.nastv.R;
import com.mynas.nastv.feature.danmaku.api.IDanmuController;
import com.mynas.nastv.feature.danmaku.logic.DanmuControllerImpl;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.player.ProgressRecorder;
import com.mynas.nastv.utils.SharedPreferencesManager;

import java.util.HashMap;
import java.util.Map;

/**
 * 🎬 VideoPlayerActivity
 * Plays video and handles Danmaku.
 */
public class VideoPlayerActivity extends AppCompatActivity {
    private static final String TAG = "VideoPlayerActivity";
    
    // UI
    private PlayerView playerView;
    private androidx.media3.ui.SubtitleView subtitleView;
    private ImageView posterImageView;
    private TextView titleText;
    private TextView infoText;
    private View loadingLayout;
    private View errorLayout;
    private TextView errorText;
    private FrameLayout danmuContainer;
    
    private ExoPlayer exoPlayer;
    private IDanmuController danmuController;
    
    // Data
    private String videoUrl;
    private String mediaTitle;
    private String mediaGuid;
    private String videoGuid;
    private String audioGuid;
    private String episodeGuid;
    
    // Danmaku Params
    private String doubanId;
    private int episodeNumber;
    private int seasonNumber;
    private String parentGuid; // 父级GUID（季GUID）
    private String tvTitle;    // 电视剧标题（用于弹幕搜索）
    private String seasonGuid; // 季GUID（用于获取剧集列表）
    
    // 🎬 恢复播放位置
    private long resumePositionSeconds = 0;
    
    // 🎬 跳过片头标志
    private boolean hasSkippedIntro = false;
    
    // 📺 剧集列表（用于选集和下一集功能）
    private java.util.List<com.mynas.nastv.model.EpisodeListResponse.Episode> episodeList;
    
    // 📝 字幕相关
    private java.util.List<com.mynas.nastv.model.StreamListResponse.SubtitleStream> subtitleStreams;
    private int currentSubtitleIndex = -1; // -1 表示关闭字幕
    private String currentVideoUrl; // 保存当前视频URL用于字幕重载
    private boolean isDirectLinkMode = false; // 是否为直连模式
    
    // 🚀 缓存预加载相关
    private com.mynas.nastv.player.CachedDataSourceFactory cachedDataSourceFactory;
    private com.mynas.nastv.player.VideoPrefetchService prefetchService;
    
    // Manager
    private MediaManager mediaManager;
    
    // 🎬 播放进度记录器
    private ProgressRecorder progressRecorder;
    
    private boolean isPlayerReady = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        
        initializeData();
        initializeViews();
        initializePlayer();
        loadMediaContent();
    }
    
    private void initializeData() {
        Intent intent = getIntent();
        videoUrl = intent.getStringExtra("video_url");
        mediaTitle = intent.getStringExtra("video_title");
        if (mediaTitle == null) mediaTitle = intent.getStringExtra("media_title");
        
        // 电视剧标题（用于弹幕搜索）
        tvTitle = intent.getStringExtra("tv_title");
        if (tvTitle == null) tvTitle = intent.getStringExtra("media_title");
        if (tvTitle == null) tvTitle = mediaTitle;
        
        mediaGuid = intent.getStringExtra("media_guid");
        videoGuid = intent.getStringExtra("video_guid");
        audioGuid = intent.getStringExtra("audio_guid");
        episodeGuid = intent.getStringExtra("episode_guid");
        parentGuid = intent.getStringExtra("season_guid");
        if (parentGuid == null) parentGuid = intent.getStringExtra("parent_guid");
        seasonGuid = intent.getStringExtra("season_guid"); // 保存季GUID用于获取剧集列表
        
        // Danmaku Params
        doubanId = intent.getStringExtra("douban_id");
        episodeNumber = intent.getIntExtra("episode_number", 0);
        seasonNumber = intent.getIntExtra("season_number", 0);
        
        // 🎬 恢复播放位置（秒）
        resumePositionSeconds = intent.getLongExtra("resume_position", 0);
        if (resumePositionSeconds <= 0) {
            resumePositionSeconds = intent.getLongExtra("ts", 0);
        }
        
        // 🎬 电影弹幕修复：电影没有季/集概念，但弹幕API需要season=1, episode=1
        // 参考Web端请求：电影使用 season_number=1, episode_number=1
        if (episodeNumber <= 0) episodeNumber = 1;
        if (seasonNumber <= 0) seasonNumber = 1;
        
        // 如果没有parentGuid，电影使用自身guid作为parent_guid
        if (parentGuid == null || parentGuid.isEmpty()) {
            parentGuid = episodeGuid != null ? episodeGuid : mediaGuid;
        }
        
        if (mediaTitle == null) mediaTitle = "Unknown";
        
        mediaManager = new MediaManager(this);
        
        // 🎬 初始化播放进度记录器
        progressRecorder = new ProgressRecorder();
        
        // 📺 加载剧集列表（用于选集和下一集功能）
        if (seasonGuid != null && !seasonGuid.isEmpty()) {
            loadEpisodeListForPlayer();
        }
        
        Log.d(TAG, "Data Initialized: " + mediaTitle + ", URL: " + videoUrl);
        Log.d(TAG, "Danmaku Params: title=" + tvTitle + ", s" + seasonNumber + "e" + episodeNumber + ", guid=" + episodeGuid + ", parentGuid=" + parentGuid);
    }
    
    /**
     * 📺 加载剧集列表（用于选集和下一集功能）
     */
    private void loadEpisodeListForPlayer() {
        mediaManager.getEpisodeList(seasonGuid, new MediaManager.MediaCallback<java.util.List<com.mynas.nastv.model.EpisodeListResponse.Episode>>() {
            @Override
            public void onSuccess(java.util.List<com.mynas.nastv.model.EpisodeListResponse.Episode> episodes) {
                episodeList = episodes;
                Log.d(TAG, "📺 Loaded " + episodes.size() + " episodes for player");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "📺 Failed to load episode list: " + error);
            }
        });
    }
    
    private void initializeViews() {
        playerView = findViewById(R.id.player_view);
        subtitleView = findViewById(R.id.subtitle_view);
        posterImageView = findViewById(R.id.poster_image);
        titleText = findViewById(R.id.title_text);
        infoText = findViewById(R.id.info_text);
        loadingLayout = findViewById(R.id.loading_layout);
        errorLayout = findViewById(R.id.error_layout);
        errorText = findViewById(R.id.error_text);
        danmuContainer = findViewById(R.id.danmu_container);
        
        titleText.setText(mediaTitle);
        infoText.setText(episodeNumber > 0 ? ("S" + seasonNumber + " E" + episodeNumber) : "");
        
        // 添加点击屏幕呼出/隐藏菜单
        playerView.setOnClickListener(v -> {
            if (isMenuVisible) {
                hideSettingsMenu();
            } else {
                showSettingsMenu();
            }
        });
        
        if (danmuContainer != null) {
            try {
                danmuController = new DanmuControllerImpl();
                danmuController.initialize(this, danmuContainer);
                Log.d(TAG, "DanmuController Initialized");
            } catch (Exception e) {
                Log.e(TAG, "DanmuController Init Failed", e);
            }
        }
    }
    
    private void initializePlayer() {
        try {
            // 🎬 优化播放体验：快速启动 + 后台缓冲
            // 策略：先用少量缓冲快速开始播放，然后后台持续缓冲
            
            // 获取可用内存，动态计算缓冲大小
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo memInfo = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);
            
            // 使用可用内存的 15% 作为视频缓冲，最小100MB，最大300MB
            long availableMB = memInfo.availMem / (1024 * 1024);
            int targetBufferBytes = (int) Math.min(300 * 1024 * 1024, 
                                     Math.max(100 * 1024 * 1024, availableMB * 1024 * 1024 * 15 / 100));
            
            Log.d(TAG, "🎬 Available memory: " + availableMB + "MB, target buffer: " + (targetBufferBytes / 1024 / 1024) + "MB");
            
            // 🔑 优化缓冲策略：快速启动 + 持续缓冲
            // - 首次播放只需2秒缓冲（快速启动）
            // - 卡顿后只需3秒恢复（快速恢复）
            // - 后台持续缓冲到5分钟
            androidx.media3.exoplayer.DefaultLoadControl loadControl = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30000,   // minBufferMs: 最小保持30秒缓冲
                    300000,  // maxBufferMs: 最大缓冲300秒（5分钟）
                    2000,    // bufferForPlaybackMs: 只需2秒就开始播放（快速启动！）
                    3000     // bufferForPlaybackAfterRebufferMs: 卡顿后只需3秒恢复（快速恢复！）
                )
                .setTargetBufferBytes(targetBufferBytes)
                .setPrioritizeTimeOverSizeThresholds(true) // 优先保证时间缓冲
                .setBackBuffer(30000, true) // 保留30秒回看缓冲
                .build();
            
            exoPlayer = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build();
            
            // 设置视频缩放模式
            playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
            playerView.setPlayer(exoPlayer);
            playerView.setUseController(false); // 禁用默认控制器，使用自定义菜单
            
            // 🔑 配置字幕输出到 SubtitleView
            if (subtitleView != null) {
                // 设置字幕样式 - 无背景，白色文字带黑色描边
                androidx.media3.ui.CaptionStyleCompat captionStyle = new androidx.media3.ui.CaptionStyleCompat(
                    android.graphics.Color.WHITE,           // 前景色（文字颜色）
                    android.graphics.Color.TRANSPARENT,     // 背景色（透明）
                    android.graphics.Color.TRANSPARENT,     // 窗口颜色（透明）
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, // 边缘类型（描边）
                    android.graphics.Color.BLACK,           // 边缘颜色（黑色描边）
                    null                                    // 字体
                );
                subtitleView.setStyle(captionStyle);
                subtitleView.setFractionalTextSize(0.05f); // 字幕大小（屏幕高度的5%）
                subtitleView.setApplyEmbeddedStyles(false); // 不使用内嵌样式，使用我们的样式
                subtitleView.setApplyEmbeddedFontSizes(false);
                subtitleView.setVisibility(View.VISIBLE);
                // 设置字幕位置 - 底部偏下
                subtitleView.setBottomPaddingFraction(0.08f); // 距离底部8%的位置
                
                // 将字幕输出连接到 SubtitleView
                exoPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onCues(androidx.media3.common.text.CueGroup cueGroup) {
                        subtitleView.setCues(cueGroup.cues);
                    }
                });
                
                Log.d(TAG, "📝 SubtitleView configured");
            } else {
                Log.e(TAG, "📝 SubtitleView is NULL!");
            }
            
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    String stateName = "UNKNOWN";
                    switch (playbackState) {
                        case Player.STATE_IDLE: stateName = "IDLE"; break;
                        case Player.STATE_BUFFERING: stateName = "BUFFERING"; break;
                        case Player.STATE_READY: stateName = "READY"; break;
                        case Player.STATE_ENDED: stateName = "ENDED"; break;
                    }
                    Log.d(TAG, "🎬 PlaybackState changed: " + stateName);
                    
                    if (playbackState == Player.STATE_READY) {
                        isPlayerReady = true;
                        Log.d(TAG, "🎬 Player READY, showing player view");
                        showPlayer();
                        hideBufferingIndicator(); // 隐藏缓冲指示器
                        
                        // 🎬 通知预缓存服务卡顿结束
                        if (prefetchService != null) {
                            prefetchService.notifyBufferingEnd();
                        }
                        
                        // 🎬 恢复播放位置
                        if (resumePositionSeconds > 0) {
                            long resumePositionMs = resumePositionSeconds * 1000;
                            Log.d(TAG, "🎬 Resuming playback at position: " + resumePositionSeconds + "s");
                            exoPlayer.seekTo(resumePositionMs);
                            resumePositionSeconds = 0; // 只恢复一次
                        } else {
                            // 🎬 跳过片头功能
                            int skipIntro = SharedPreferencesManager.getSkipIntro();
                            if (skipIntro > 0 && !hasSkippedIntro) {
                                Log.d(TAG, "🎬 Skipping intro: " + skipIntro + "s");
                                exoPlayer.seekTo(skipIntro * 1000L);
                                hasSkippedIntro = true;
                            }
                        }
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        // 🔑 卡顿时显示加载提示
                        Log.d(TAG, "🎬 Buffering...");
                        if (isPlayerReady) {
                            // 已经开始播放后的卡顿，显示缓冲指示器
                            showBufferingIndicator();
                            
                            // 🎬 通知预缓存服务：发生卡顿，需要加速缓存
                            if (prefetchService != null) {
                                prefetchService.notifyBufferingStart();
                            }
                        }
                    } else if (playbackState == Player.STATE_ENDED) {
                        // 🎬 自动连播：播放结束时自动播放下一集
                        if (SharedPreferencesManager.isAutoPlayNext() && episodeList != null && !episodeList.isEmpty()) {
                            playNextEpisodeAuto();
                        } else {
                            finish();
                        }
                    }
                }
                
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    Log.d(TAG, "🎬 isPlaying changed: " + isPlaying);
                    if (danmuController != null) {
                        if (isPlaying) {
                            danmuController.startPlayback();
                            startPositionUpdate();
                        } else {
                            danmuController.pausePlayback();
                            stopPositionUpdate();
                        }
                    }
                    
                    // 🎬 播放进度记录
                    if (progressRecorder != null) {
                        if (isPlaying) {
                            // 开始播放时启动记录
                            if (!progressRecorder.isRecording()) {
                                String itemGuid = episodeGuid != null ? episodeGuid : mediaGuid;
                                progressRecorder.startRecording(itemGuid, mediaGuid);
                                progressRecorder.setStreamGuids(videoGuid, audioGuid, null);
                            }
                        } else {
                            // 暂停时立即保存进度
                            progressRecorder.saveImmediately();
                        }
                    }
                }
                
                @Override
                public void onVideoSizeChanged(androidx.media3.common.VideoSize videoSize) {
                    Log.d(TAG, "🎬 Video size: " + videoSize.width + "x" + videoSize.height);
                }
                
                @Override
                public void onRenderedFirstFrame() {
                    Log.d(TAG, "🎬 First frame rendered!");
                }
                
                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    Log.e(TAG, "Player Error", error);
                    showError("Player Error: " + error.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "ExoPlayer Init Failed", e);
            showError("Player Init Failed");
        }
    }
    
    // 🔑 缓冲指示器 - 播放中卡顿时显示
    private View bufferingIndicator;
    private TextView bufferingText;
    
    private void showBufferingIndicator() {
        runOnUiThread(() -> {
            if (bufferingIndicator == null) {
                bufferingIndicator = findViewById(R.id.buffering_indicator);
                bufferingText = findViewById(R.id.buffering_text);
            }
            if (bufferingIndicator != null) {
                bufferingIndicator.setVisibility(View.VISIBLE);
                if (bufferingText != null) {
                    bufferingText.setText("缓冲中...");
                }
            } else {
                // 如果没有专门的缓冲指示器，使用 Toast
                Toast.makeText(this, "缓冲中...", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void hideBufferingIndicator() {
        runOnUiThread(() -> {
            if (bufferingIndicator != null) {
                bufferingIndicator.setVisibility(View.GONE);
            }
        });
    }
    
    private void loadMediaContent() {
        if (videoUrl != null && !videoUrl.isEmpty()) {
            playMedia(videoUrl);
        } else {
            showError("No video URL provided");
        }
    }
    
    private void playMedia(String url) {
        Log.d(TAG, "Playing URL: " + url);
        Log.d(TAG, "🎬 Danmaku params for playback: title=" + tvTitle + ", s" + seasonNumber + "e" + episodeNumber + ", guid=" + episodeGuid);
        showLoading("Loading...");
        
        // 保存当前视频URL
        currentVideoUrl = url;
        
        try {
            MediaItem mediaItem = createMediaItemWithHeaders(url);
            if (mediaItem != null) {
                exoPlayer.setMediaItem(mediaItem); // Should ideally set source
            }
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);
            
            // Load Danmaku - 使用 title + season + episode + guid 获取弹幕
            if (danmuController != null) {
                if (tvTitle != null && !tvTitle.isEmpty()) {
                    Log.d(TAG, "🎬 Loading danmaku with title=" + tvTitle + ", s" + seasonNumber + "e" + episodeNumber);
                    danmuController.loadDanmaku(tvTitle, episodeNumber, seasonNumber, episodeGuid, parentGuid);
                } else {
                    Log.w(TAG, "🎬 No valid title for danmaku, skipping. title=" + tvTitle);
                }
            }
            
            // 📝 加载字幕列表
            loadSubtitleList();
            
        } catch (Exception e) {
            Log.e(TAG, "Play Failed", e);
            showError("Play Failed: " + e.getMessage());
        }
    }
    
    /**
     * 📝 加载字幕列表
     */
    private void loadSubtitleList() {
        String itemGuid = episodeGuid != null ? episodeGuid : mediaGuid;
        if (itemGuid == null || itemGuid.isEmpty()) {
            Log.e(TAG, "📝 No item guid for subtitle loading");
            return;
        }
        
        Log.e(TAG, "📝 Loading subtitle list for item: " + itemGuid);
        
        new Thread(() -> {
            try {
                String token = SharedPreferencesManager.getAuthToken();
                String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature(
                    "GET", "/v/api/v1/stream/list/" + itemGuid, "", null);
                
                Log.e(TAG, "📝 Calling getStreamList API...");
                
                retrofit2.Response<com.mynas.nastv.model.StreamListResponse> response = 
                    com.mynas.nastv.network.ApiClient.getApiService()
                        .getStreamList(token, signature, itemGuid)
                        .execute();
                
                Log.e(TAG, "📝 getStreamList response: " + response.code());
                
                if (response.isSuccessful() && response.body() != null) {
                    com.mynas.nastv.model.StreamListResponse.StreamData data = response.body().getData();
                    if (data != null && data.getSubtitleStreams() != null) {
                        subtitleStreams = data.getSubtitleStreams();
                        Log.e(TAG, "📝 Found " + subtitleStreams.size() + " subtitle streams");
                        
                        // 打印所有字幕信息
                        for (int i = 0; i < subtitleStreams.size(); i++) {
                            com.mynas.nastv.model.StreamListResponse.SubtitleStream sub = subtitleStreams.get(i);
                            Log.e(TAG, "📝 Subtitle " + i + ": " + sub.getTitle() + " (" + sub.getLanguage() + ") external=" + sub.isExternal() + " guid=" + sub.getGuid());
                        }
                        
                        // 🚀 新逻辑：使用 CacheDataSource + DefaultExtractorsFactory 后，
                        // ExoPlayer 可以解析 MKV 内嵌字幕，优先使用内嵌字幕
                        int firstSubtitleIndex = -1;
                        int firstExternalIndex = -1;
                        int firstInternalIndex = -1;
                        
                        for (int i = 0; i < subtitleStreams.size(); i++) {
                            com.mynas.nastv.model.StreamListResponse.SubtitleStream sub = subtitleStreams.get(i);
                            if (sub.isExternal() && firstExternalIndex == -1) {
                                firstExternalIndex = i;
                            }
                            if (!sub.isExternal() && firstInternalIndex == -1) {
                                firstInternalIndex = i;
                            }
                        }
                        
                        // 优先使用内嵌字幕（ExoPlayer 可以直接解析 MKV）
                        if (firstInternalIndex >= 0) {
                            firstSubtitleIndex = firstInternalIndex;
                            Log.e(TAG, "📝 Will use internal subtitle at index " + firstSubtitleIndex);
                        } else if (firstExternalIndex >= 0) {
                            firstSubtitleIndex = firstExternalIndex;
                            Log.e(TAG, "📝 Will use external subtitle at index " + firstSubtitleIndex);
                        }
                        
                        if (firstSubtitleIndex >= 0) {
                            final int index = firstSubtitleIndex;
                            final boolean isInternal = !subtitleStreams.get(index).isExternal();
                            
                            if (isInternal) {
                                // 内嵌字幕：等待 ExoPlayer 解析 MKV 后自动选择
                                Log.e(TAG, "📝 Internal subtitle will be auto-selected by ExoPlayer");
                                runOnUiThread(() -> enableInternalSubtitle(index));
                            } else {
                                // 外挂字幕：下载并加载
                                Log.e(TAG, "📝 Auto-loading external subtitle at index " + index);
                                runOnUiThread(() -> loadSubtitle(index));
                            }
                        } else {
                            Log.e(TAG, "📝 No subtitles found");
                        }
                    } else {
                        Log.e(TAG, "📝 No subtitle streams found in response");
                    }
                } else {
                    Log.e(TAG, "📝 Failed to load subtitle list: " + response.code());
                    if (response.errorBody() != null) {
                        Log.e(TAG, "📝 Error body: " + response.errorBody().string());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "📝 Error loading subtitle list", e);
            }
        }).start();
    }
    
    /**
     * 📝 加载指定字幕
     */
    private void loadSubtitle(int index) {
        if (subtitleStreams == null || index < 0 || index >= subtitleStreams.size()) {
            Log.w(TAG, "📝 Invalid subtitle index: " + index);
            return;
        }
        
        com.mynas.nastv.model.StreamListResponse.SubtitleStream subtitle = subtitleStreams.get(index);
        String subtitleGuid = subtitle.getGuid();
        
        // 获取字幕格式：优先使用 format，其次使用 codec_name
        String format = subtitle.getFormat();
        if (format == null || format.isEmpty()) {
            format = subtitle.getCodecName();
        }
        if (format == null || format.isEmpty()) {
            format = "srt";
        }
        
        // 规范化格式名称
        format = normalizeSubtitleFormat(format);
        
        Log.e(TAG, "📝 Loading subtitle: " + subtitle.getTitle() + " guid=" + subtitleGuid + 
              " format=" + format + " codec=" + subtitle.getCodecName() + " external=" + subtitle.isExternal());
        
        final String finalFormat = format;
        new Thread(() -> {
            try {
                String token = SharedPreferencesManager.getAuthToken();
                String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature(
                    "GET", "/v/api/v1/subtitle/dl/" + subtitleGuid, "", null);
                
                Log.e(TAG, "📝 Downloading subtitle from API: /v/api/v1/subtitle/dl/" + subtitleGuid);
                
                retrofit2.Response<okhttp3.ResponseBody> response = 
                    com.mynas.nastv.network.ApiClient.getApiService()
                        .downloadSubtitle(token, signature, subtitleGuid)
                        .execute();
                
                Log.e(TAG, "📝 Subtitle download response: " + response.code());
                
                if (response.isSuccessful() && response.body() != null) {
                    // 保存字幕到临时文件
                    byte[] subtitleBytes = response.body().bytes();
                    Log.e(TAG, "📝 Subtitle content size: " + subtitleBytes.length + " bytes");
                    
                    // 打印字幕内容前200字符用于调试
                    String preview = new String(subtitleBytes, 0, Math.min(200, subtitleBytes.length), "UTF-8");
                    Log.e(TAG, "📝 Subtitle preview: " + preview.replace("\n", "\\n"));
                    
                    java.io.File subtitleFile = new java.io.File(getCacheDir(), "subtitle_" + subtitleGuid + "." + finalFormat);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(subtitleFile);
                    fos.write(subtitleBytes);
                    fos.close();
                    
                    Log.e(TAG, "📝 Subtitle downloaded to: " + subtitleFile.getAbsolutePath());
                    
                    // 在主线程中添加字幕到播放器
                    final int subtitleIndex = index;
                    runOnUiThread(() -> {
                        try {
                            addSubtitleToPlayer(subtitleFile, subtitle, finalFormat, subtitleIndex);
                        } catch (Exception e) {
                            Log.e(TAG, "📝 Error adding subtitle to player", e);
                        }
                    });
                } else {
                    Log.e(TAG, "📝 Failed to download subtitle: " + response.code());
                    if (response.errorBody() != null) {
                        Log.e(TAG, "📝 Error body: " + response.errorBody().string());
                    }
                    runOnUiThread(() -> Toast.makeText(this, "字幕下载失败: " + response.code(), Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "📝 Error downloading subtitle", e);
                runOnUiThread(() -> Toast.makeText(this, "字幕下载错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    /**
     * 📝 规范化字幕格式名称
     */
    private String normalizeSubtitleFormat(String format) {
        if (format == null) return "srt";
        
        switch (format.toLowerCase()) {
            case "subrip":
                return "srt";
            case "ass":
            case "ssa":
                return "ass";
            case "webvtt":
            case "vtt":
                return "vtt";
            case "ttml":
                return "ttml";
            default:
                return format.toLowerCase();
        }
    }
    
    /**
     * 📝 添加字幕到播放器
     */
    private void addSubtitleToPlayer(java.io.File subtitleFile, 
            com.mynas.nastv.model.StreamListResponse.SubtitleStream subtitle,
            String format, int subtitleIndex) {
        
        // 获取字幕 MIME 类型
        String mimeType = getMimeTypeForSubtitle(format);
        
        Log.e(TAG, "📝 Adding subtitle to player: file=" + subtitleFile.getAbsolutePath() + 
              " format=" + format + " mimeType=" + mimeType);
        
        // 创建字幕配置 - 使用 SELECTION_FLAG_DEFAULT 和 SELECTION_FLAG_AUTOSELECT
        androidx.media3.common.MediaItem.SubtitleConfiguration subtitleConfig =
            new androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(
                android.net.Uri.fromFile(subtitleFile))
                .setMimeType(mimeType)
                .setLanguage(subtitle.getLanguage() != null ? subtitle.getLanguage() : "und")
                .setLabel(subtitle.getTitle() != null ? subtitle.getTitle() : "字幕")
                .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT | 
                                   androidx.media3.common.C.SELECTION_FLAG_AUTOSELECT)
                .build();
        
        long currentPosition = exoPlayer.getCurrentPosition();
        boolean wasPlaying = exoPlayer.isPlaying();
        
        Log.e(TAG, "📝 Current position: " + currentPosition + ", wasPlaying: " + wasPlaying);
        
        if (isDirectLinkMode) {
            // 📝 直连模式：使用 MergingMediaSource 合并视频和字幕
            Log.e(TAG, "📝 Direct link mode: using MergingMediaSource");
            
            // 创建字幕 MediaSource
            androidx.media3.exoplayer.source.SingleSampleMediaSource subtitleSource =
                new androidx.media3.exoplayer.source.SingleSampleMediaSource.Factory(
                    new androidx.media3.datasource.DefaultDataSource.Factory(this))
                    .createMediaSource(subtitleConfig, androidx.media3.common.C.TIME_UNSET);
            
            // 获取当前的视频 MediaSource（需要重新创建）
            // 由于直连模式使用 ParallelDataSource，我们需要重新创建视频源
            androidx.media3.exoplayer.source.MediaSource videoSource = createDirectLinkMediaSource(currentVideoUrl);
            
            if (videoSource != null) {
                // 合并视频和字幕
                androidx.media3.exoplayer.source.MergingMediaSource mergingSource =
                    new androidx.media3.exoplayer.source.MergingMediaSource(videoSource, subtitleSource);
                
                exoPlayer.setMediaSource(mergingSource);
                exoPlayer.prepare();
                
                // 🔑 关键：启用字幕轨道
                enableSubtitleTrack();
                
                exoPlayer.seekTo(currentPosition);
                if (wasPlaying) {
                    exoPlayer.play();
                }
                
                currentSubtitleIndex = subtitleIndex;
                Log.e(TAG, "📝 Subtitle added via MergingMediaSource");
                Toast.makeText(this, "字幕已加载: " + subtitle.getTitle(), Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "📝 Failed to create video source for merging");
                Toast.makeText(this, "字幕加载失败", Toast.LENGTH_SHORT).show();
            }
        } else {
            // 📝 普通模式：使用 MediaItem 的字幕配置
            Log.e(TAG, "📝 Normal mode: using MediaItem subtitle configuration");
            
            androidx.media3.common.MediaItem currentItem = exoPlayer.getCurrentMediaItem();
            if (currentItem != null) {
                java.util.List<androidx.media3.common.MediaItem.SubtitleConfiguration> subtitles = 
                    new java.util.ArrayList<>();
                subtitles.add(subtitleConfig);
                
                androidx.media3.common.MediaItem newItem = currentItem.buildUpon()
                    .setSubtitleConfigurations(subtitles)
                    .build();
                
                exoPlayer.setMediaItem(newItem);
                exoPlayer.prepare();
                
                // 🔑 关键：启用字幕轨道
                enableSubtitleTrack();
                
                exoPlayer.seekTo(currentPosition);
                if (wasPlaying) {
                    exoPlayer.play();
                }
                
                currentSubtitleIndex = subtitleIndex;
                Log.e(TAG, "📝 Subtitle added to player");
                Toast.makeText(this, "字幕已加载: " + subtitle.getTitle(), Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * 📝 启用字幕轨道
     */
    private void enableSubtitleTrack() {
        if (exoPlayer == null) return;
        
        // 添加监听器，在播放器准备好后启用字幕
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    try {
                        // 确保字幕轨道未被禁用
                        androidx.media3.common.TrackSelectionParameters params = exoPlayer.getTrackSelectionParameters()
                            .buildUpon()
                            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                            .build();
                        exoPlayer.setTrackSelectionParameters(params);
                        
                        // 打印当前轨道信息
                        logCurrentTracks();
                        
                        Log.e(TAG, "📝 Subtitle track enabled after player ready");
                    } catch (Exception e) {
                        Log.e(TAG, "📝 Error enabling subtitle track", e);
                    }
                    exoPlayer.removeListener(this);
                }
            }
        });
        
        // 如果播放器已经准备好，直接启用
        if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
            try {
                androidx.media3.common.TrackSelectionParameters params = exoPlayer.getTrackSelectionParameters()
                    .buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                    .build();
                exoPlayer.setTrackSelectionParameters(params);
                logCurrentTracks();
                Log.e(TAG, "📝 Subtitle track enabled immediately");
            } catch (Exception e) {
                Log.e(TAG, "📝 Error enabling subtitle track", e);
            }
        }
    }
    
    /**
     * 📝 打印当前轨道信息（用于调试）
     */
    private void logCurrentTracks() {
        if (exoPlayer == null) return;
        
        try {
            androidx.media3.common.Tracks tracks = exoPlayer.getCurrentTracks();
            java.util.List<androidx.media3.common.Tracks.Group> groups = tracks.getGroups();
            
            Log.e(TAG, "📝 ===== Current Tracks =====");
            Log.e(TAG, "📝 Total track groups: " + groups.size());
            
            for (int i = 0; i < groups.size(); i++) {
                androidx.media3.common.Tracks.Group group = groups.get(i);
                int trackType = group.getType();
                String typeName = "UNKNOWN";
                
                switch (trackType) {
                    case androidx.media3.common.C.TRACK_TYPE_VIDEO: typeName = "VIDEO"; break;
                    case androidx.media3.common.C.TRACK_TYPE_AUDIO: typeName = "AUDIO"; break;
                    case androidx.media3.common.C.TRACK_TYPE_TEXT: typeName = "TEXT"; break;
                }
                
                Log.e(TAG, "📝 Group " + i + " [" + typeName + "]: " + group.length + " tracks, selected=" + group.isSelected());
                
                for (int j = 0; j < group.length; j++) {
                    androidx.media3.common.Format format = group.getTrackFormat(j);
                    boolean isSelected = group.isTrackSelected(j);
                    boolean isSupported = group.isTrackSupported(j);
                    
                    Log.e(TAG, "📝   Track " + j + ": lang=" + format.language + 
                          ", label=" + format.label + 
                          ", mime=" + format.sampleMimeType +
                          ", selected=" + isSelected + 
                          ", supported=" + isSupported);
                }
            }
            Log.e(TAG, "📝 ===========================");
        } catch (Exception e) {
            Log.e(TAG, "📝 Error logging tracks", e);
        }
    }
    
    /**
     * 📝 创建直连视频 MediaSource（用于字幕合并）
     * 使用缓存数据源，支持 MKV 内嵌字幕解析
     */
    private androidx.media3.exoplayer.source.MediaSource createDirectLinkMediaSource(String url) {
        if (url == null) return null;
        
        try {
            // 如果已有缓存数据源工厂，直接使用
            if (cachedDataSourceFactory != null) {
                Log.d(TAG, "📝 Reusing existing CachedDataSourceFactory");
                return new androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(cachedDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url));
            }
            
            // 否则创建新的
            boolean isProxyDirectLink = url.contains("direct_link_quality_index");
            
            // 提取域名用于 Referer
            String referer = "https://pan.quark.cn/";
            try {
                java.net.URL parsedUrl = new java.net.URL(url);
                referer = parsedUrl.getProtocol() + "://" + parsedUrl.getHost() + "/";
            } catch (Exception e) {
                Log.w(TAG, "Parse URL failed", e);
            }
            final String finalReferer = referer;
            
            // 配置 OkHttpClient
            okhttp3.Dispatcher dispatcher = new okhttp3.Dispatcher();
            dispatcher.setMaxRequests(64);
            dispatcher.setMaxRequestsPerHost(16);
            
            okhttp3.ConnectionPool connectionPool = new okhttp3.ConnectionPool(
                16, 5, java.util.concurrent.TimeUnit.MINUTES);
            
            // 构建请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.put("Accept", "*/*");
            headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            headers.put("Accept-Encoding", "identity");
            headers.put("Connection", "keep-alive");
            
            if (isProxyDirectLink) {
                String token = SharedPreferencesManager.getAuthToken();
                if (token != null && !token.isEmpty()) {
                    String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
                    headers.put("Cookie", "Trim-MC-token=" + authToken);
                    headers.put("Authorization", authToken);
                    
                    try {
                        String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature("GET", url, "", null);
                        if (signature != null) {
                            headers.put("authx", signature);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Sign failed", e);
                    }
                }
            } else {
                headers.put("Referer", finalReferer);
                headers.put("Origin", finalReferer.substring(0, finalReferer.length() - 1));
                headers.put("Sec-Fetch-Dest", "video");
                headers.put("Sec-Fetch-Mode", "cors");
                headers.put("Sec-Fetch-Site", "cross-site");
            }
            
            okhttp3.OkHttpClient directLinkClient = new okhttp3.OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
            
            // 使用缓存数据源
            String cacheKey = "video_" + url.hashCode();
            com.mynas.nastv.player.CachedDataSourceFactory factory = 
                new com.mynas.nastv.player.CachedDataSourceFactory(this, directLinkClient, headers, cacheKey);
            
            return new androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(url));
                
        } catch (Exception e) {
            Log.e(TAG, "📝 Error creating direct link media source", e);
            return null;
        }
    }
    
    /**
     * 📝 获取字幕 MIME 类型
     */
    private String getMimeTypeForSubtitle(String format) {
        if (format == null) return androidx.media3.common.MimeTypes.APPLICATION_SUBRIP;
        
        switch (format.toLowerCase()) {
            case "srt":
                return androidx.media3.common.MimeTypes.APPLICATION_SUBRIP;
            case "ass":
            case "ssa":
                return androidx.media3.common.MimeTypes.TEXT_SSA;
            case "vtt":
            case "webvtt":
                return androidx.media3.common.MimeTypes.TEXT_VTT;
            case "ttml":
                return androidx.media3.common.MimeTypes.APPLICATION_TTML;
            default:
                return androidx.media3.common.MimeTypes.APPLICATION_SUBRIP;
        }
    }
    
    /**
     * 📝 启用内嵌字幕（通过轨道选择）
     */
    private void enableInternalSubtitle(int index) {
        if (exoPlayer == null || subtitleStreams == null || index < 0 || index >= subtitleStreams.size()) {
            Log.e(TAG, "📝 Cannot enable internal subtitle: invalid state");
            return;
        }
        
        Log.e(TAG, "📝 Enabling internal subtitle at index " + index);
        
        // 等待播放器准备好后再选择字幕轨道
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(androidx.media3.common.Tracks tracks) {
                Log.e(TAG, "📝 Tracks changed, selecting subtitle track");
                selectSubtitleTrack(index);
                exoPlayer.removeListener(this);
            }
        });
        
        // 如果播放器已经准备好，直接选择
        if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
            selectSubtitleTrack(index);
        }
        
        currentSubtitleIndex = index;
    }
    
    /**
     * 📝 选择字幕轨道
     */
    private void selectSubtitleTrack(int subtitleIndex) {
        if (exoPlayer == null) return;
        
        try {
            androidx.media3.common.Tracks tracks = exoPlayer.getCurrentTracks();
            java.util.List<androidx.media3.common.Tracks.Group> groups = tracks.getGroups();
            
            Log.e(TAG, "📝 Total track groups: " + groups.size());
            
            int textTrackCount = 0;
            for (int i = 0; i < groups.size(); i++) {
                androidx.media3.common.Tracks.Group group = groups.get(i);
                int trackType = group.getType();
                
                if (trackType == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                    Log.e(TAG, "📝 Found text track group at index " + i + ", tracks: " + group.length);
                    
                    for (int j = 0; j < group.length; j++) {
                        androidx.media3.common.Format format = group.getTrackFormat(j);
                        Log.e(TAG, "📝   Track " + j + ": " + format.language + " - " + format.label);
                        
                        if (textTrackCount == subtitleIndex) {
                            // 选择这个字幕轨道
                            androidx.media3.common.TrackSelectionParameters params = exoPlayer.getTrackSelectionParameters()
                                .buildUpon()
                                .setPreferredTextLanguage(format.language)
                                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                .build();
                            exoPlayer.setTrackSelectionParameters(params);
                            
                            Log.e(TAG, "📝 Selected subtitle track: " + format.language);
                            
                            String title = subtitleStreams.get(subtitleIndex).getTitle();
                            Toast.makeText(this, "字幕已启用: " + title, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        textTrackCount++;
                    }
                }
            }
            
            Log.e(TAG, "📝 No matching subtitle track found for index " + subtitleIndex);
        } catch (Exception e) {
            Log.e(TAG, "📝 Error selecting subtitle track", e);
        }
    }
    
    private MediaItem createMediaItemWithHeaders(String url) {
        Log.d(TAG, "Creating media item for URL: " + url);
        
        // 🔑 判断是否为直连URL
        // 1. 外部云存储URL: https://dl-pc-zb-w.drive.quark.cn/...
        // 2. 本地服务器代理的直连: /v/api/v1/media/range/...?direct_link_quality_index=0
        boolean isExternalDirectLink = url.startsWith("https://") && !url.contains("192.168.") && !url.contains("localhost");
        boolean isProxyDirectLink = url.contains("direct_link_quality_index");
        boolean isDirectLink = isExternalDirectLink || isProxyDirectLink;
        
        Log.d(TAG, "🚀 URL analysis: isExternalDirectLink=" + isExternalDirectLink + ", isProxyDirectLink=" + isProxyDirectLink);
        
        // 设置直连模式标志
        isDirectLinkMode = isDirectLink;
        
        if (isDirectLink) {
            // 🚀 直连URL - 使用缓存数据源 + 多线程预缓存
            Log.d(TAG, "🚀 Using CachedDataSource with prefetch for direct link");
            
            // 提取域名用于 Referer
            String referer = "https://pan.quark.cn/";
            try {
                java.net.URL parsedUrl = new java.net.URL(url);
                referer = parsedUrl.getProtocol() + "://" + parsedUrl.getHost() + "/";
            } catch (Exception e) {
                Log.w(TAG, "Parse URL failed", e);
            }
            final String finalReferer = referer;
            
            // 🔑 多线程加速：使用 Dispatcher 配置并发请求
            okhttp3.Dispatcher dispatcher = new okhttp3.Dispatcher();
            dispatcher.setMaxRequests(64);           // 最大并发请求数
            dispatcher.setMaxRequestsPerHost(16);    // 每个主机最大并发数
            
            // 使用连接池优化
            okhttp3.ConnectionPool connectionPool = new okhttp3.ConnectionPool(
                16, // 最大空闲连接数
                5, java.util.concurrent.TimeUnit.MINUTES
            );
            
            // 构建请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.put("Accept", "*/*");
            headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            headers.put("Accept-Encoding", "identity");
            headers.put("Connection", "keep-alive");
            
            // 🔑 如果是代理直连，需要添加认证头
            if (isProxyDirectLink) {
                String token = SharedPreferencesManager.getAuthToken();
                if (token != null && !token.isEmpty()) {
                    String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
                    headers.put("Cookie", "Trim-MC-token=" + authToken);
                    headers.put("Authorization", authToken);
                    
                    // Sign request for authx
                    try {
                        String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature("GET", url, "", null);
                        if (signature != null) {
                            headers.put("authx", signature);
                            Log.d(TAG, "🚀 Added authx header for proxy direct link");
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Sign failed", e);
                    }
                }
            } else {
                // 外部直连需要 Referer
                headers.put("Referer", finalReferer);
                headers.put("Origin", finalReferer.substring(0, finalReferer.length() - 1));
                headers.put("Sec-Fetch-Dest", "video");
                headers.put("Sec-Fetch-Mode", "cors");
                headers.put("Sec-Fetch-Site", "cross-site");
            }
            
            // 创建 OkHttpClient
            okhttp3.OkHttpClient directLinkClient = new okhttp3.OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
            
            // 🚀 使用缓存数据源
            String cacheKey = "video_" + url.hashCode();
            Log.d(TAG, "🚀 Using CachedDataSource, cacheKey=" + cacheKey);
            
            // 创建缓存数据源工厂
            cachedDataSourceFactory = new com.mynas.nastv.player.CachedDataSourceFactory(
                this, directLinkClient, headers, cacheKey);
            
            // 启动多线程预缓存服务
            prefetchService = cachedDataSourceFactory.startPrefetch(url);
            
            // 使用 ProgressiveMediaSource（支持 MKV 解析）
            androidx.media3.exoplayer.source.ProgressiveMediaSource mediaSource = 
                new androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(cachedDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url));
            exoPlayer.setMediaSource(mediaSource);
            
            Log.d(TAG, "🚀 CachedDataSource + Prefetch configured");
            return null;
        }
        
        // 本地服务器URL需要添加认证头
        String token = SharedPreferencesManager.getAuthToken();
        Log.d(TAG, "Creating media item with headers, token: " + (token != null ? "present" : "null"));
        
        if (token != null && !token.isEmpty()) {
            Map<String, String> headers = new HashMap<>();
            
            // 添加认证头 - 使用与Web端一致的Cookie名称 Trim-MC-token
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            headers.put("Cookie", "Trim-MC-token=" + authToken);
            headers.put("Authorization", authToken);
            
            // Sign request for authx
            try {
                String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature("GET", url, "", null);
                if (signature != null) {
                    headers.put("authx", signature);
                    Log.d(TAG, "Added authx header: " + signature);
                }
            } catch (Exception e) {
                Log.w(TAG, "Sign failed", e);
            }
            
            Log.d(TAG, "Headers: " + headers.keySet());
            
            // 使用 OkHttp 作为数据源，优化网络配置
            okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(chain -> {
                    okhttp3.Request original = chain.request();
                    okhttp3.Request.Builder builder = original.newBuilder();
                    
                    // 如果没有 Range 头，添加 Range: bytes=0-
                    if (original.header("Range") == null) {
                        builder.header("Range", "bytes=0-");
                        Log.d(TAG, "Added Range header: bytes=0-");
                    }
                    
                    return chain.proceed(builder.build());
                })
                .build();
            
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory okHttpDataSourceFactory = 
                new androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
                    .setDefaultRequestProperties(headers);
                
            MediaSource mediaSource = new DefaultMediaSourceFactory(okHttpDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url));
            exoPlayer.setMediaSource(mediaSource);
            return null;
        } else {
            Log.w(TAG, "No token, playing without auth headers");
            return MediaItem.fromUri(url);
        }
    }
    
    private void showLoading(String msg) {
        runOnUiThread(() -> {
            loadingLayout.setVisibility(View.VISIBLE);
            if (errorLayout != null) errorLayout.setVisibility(View.GONE);
            playerView.setVisibility(View.GONE);
        });
    }
    
    private void showPlayer() {
        runOnUiThread(() -> {
            loadingLayout.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
            // 隐藏海报，显示视频
            if (posterImageView != null) {
                posterImageView.setVisibility(View.GONE);
            }
        });
    }
    
    private void showError(String msg) {
        runOnUiThread(() -> {
            loadingLayout.setVisibility(View.GONE);
            errorLayout.setVisibility(View.VISIBLE);
            errorText.setText(msg);
        });
    }
    
    private Handler positionHandler = new Handler(Looper.getMainLooper());
    private Runnable positionRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null) {
                long currentPosition = exoPlayer.getCurrentPosition();
                long duration = exoPlayer.getDuration();
                
                // 更新弹幕位置
                if (danmuController != null) {
                    danmuController.updatePlaybackPosition(currentPosition);
                }
                
                // 🎬 更新播放进度记录器
                if (progressRecorder != null && duration > 0) {
                    // 转换为秒
                    progressRecorder.updateProgress(currentPosition / 1000, duration / 1000);
                }
                
                // 🚀 更新预缓存服务的播放位置（用于调整下载优先级）
                if (prefetchService != null && duration > 0) {
                    // 将时间位置转换为字节位置（估算）
                    long contentLength = prefetchService.getContentLength();
                    if (contentLength > 0) {
                        long bytePosition = (currentPosition * contentLength) / duration;
                        prefetchService.updatePlaybackPosition(bytePosition);
                    }
                }
                
                positionHandler.postDelayed(this, 100);
            }
        }
    };
    
    private void startPositionUpdate() {
        positionHandler.post(positionRunnable);
    }
    
    private void stopPositionUpdate() {
        positionHandler.removeCallbacks(positionRunnable);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPositionUpdate();
        
        // 🎬 停止播放进度记录
        if (progressRecorder != null) {
            progressRecorder.stopRecording();
            progressRecorder = null;
        }
        
        // 清理图标隐藏任务
        if (hideIconRunnable != null) {
            iconHandler.removeCallbacks(hideIconRunnable);
        }
        
        // 🚀 停止预缓存服务
        if (cachedDataSourceFactory != null) {
            cachedDataSourceFactory.stopPrefetch();
            cachedDataSourceFactory = null;
        }
        prefetchService = null;
        
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        if (danmuController != null) {
            danmuController.destroy();
            danmuController = null;
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 如果菜单可见，按返回键隐藏菜单
        if (keyCode == KeyEvent.KEYCODE_BACK && isMenuVisible) {
            hideSettingsMenu();
            return true;
        }
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
             if (exoPlayer != null) {
                 if (exoPlayer.isPlaying()) {
                     exoPlayer.pause();
                     showCenterIcon(false); // 显示暂停图标
                 } else {
                     exoPlayer.play();
                     showCenterIcon(true); // 显示播放图标
                 }
                 return true;
             }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            // 按下键呼出/隐藏设置菜单和进度条
            if (isMenuVisible) {
                hideSettingsMenu();
            } else {
                showSettingsMenu();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && isMenuVisible) {
            // 菜单可见时，按上键隐藏菜单
            hideSettingsMenu();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !isMenuVisible) {
            // 左键快退10秒（菜单不可见时）
            if (exoPlayer != null) {
                long newPosition = Math.max(0, exoPlayer.getCurrentPosition() - 10000);
                exoPlayer.seekTo(newPosition);
                showSeekProgressOverlay(newPosition, false);
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !isMenuVisible) {
            // 右键快进10秒（菜单不可见时）
            if (exoPlayer != null) {
                long newPosition = Math.min(exoPlayer.getDuration(), exoPlayer.getCurrentPosition() + 10000);
                exoPlayer.seekTo(newPosition);
                showSeekProgressOverlay(newPosition, true);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        // 点击屏幕呼出/隐藏菜单
        if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
            if (isMenuVisible) {
                hideSettingsMenu();
            } else {
                showSettingsMenu();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
    
    // UI - 底部菜单
    private LinearLayout bottomMenuContainer;
    private TextView menuNextEpisode, menuSpeed, menuEpisode, menuQuality, menuSubtitle, menuDanmaku, menuSettings;
    private boolean isMenuVisible = false;
    
    // UI - 进度条
    private TextView progressCurrentTime, progressTotalTime;
    private TextView bufferInfoText;
    private android.widget.SeekBar progressSeekbar;
    private android.widget.ProgressBar bufferProgressbar;
    private boolean isSeekbarTracking = false;
    
    // UI - 中央播放/暂停图标
    private ImageView centerPlayIcon;
    private Handler iconHandler = new Handler(Looper.getMainLooper());
    private Runnable hideIconRunnable;
    
    // 当前播放速度
    private float currentSpeed = 1.0f;
    private static final float[] SPEED_OPTIONS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private static final String[] SPEED_LABELS = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};
    
    private void showSettingsMenu() {
        if (bottomMenuContainer == null) {
            bottomMenuContainer = findViewById(R.id.bottom_menu_container);
            menuNextEpisode = findViewById(R.id.menu_next_episode);
            menuSpeed = findViewById(R.id.menu_speed);
            menuEpisode = findViewById(R.id.menu_episode);
            menuQuality = findViewById(R.id.menu_quality);
            menuSubtitle = findViewById(R.id.menu_subtitle);
            menuDanmaku = findViewById(R.id.menu_danmaku);
            
            // 进度条
            progressCurrentTime = findViewById(R.id.progress_current_time);
            progressTotalTime = findViewById(R.id.progress_total_time);
            progressSeekbar = findViewById(R.id.progress_seekbar);
            bufferProgressbar = findViewById(R.id.buffer_progressbar);
            bufferInfoText = findViewById(R.id.buffer_info_text);
            
            // 设置点击事件
            if (menuNextEpisode != null) {
                menuNextEpisode.setOnClickListener(v -> playNextEpisode());
            }
            menuSpeed.setOnClickListener(v -> showSpeedMenu());
            menuEpisode.setOnClickListener(v -> showEpisodeMenu());
            menuQuality.setOnClickListener(v -> showQualityMenu());
            menuSubtitle.setOnClickListener(v -> showSubtitleMenu());
            menuDanmaku.setOnClickListener(v -> toggleDanmaku());
            menuSettings = findViewById(R.id.menu_settings);
            if (menuSettings != null) {
                menuSettings.setOnClickListener(v -> showSettingsDialog());
            }
            
            // 进度条拖动监听
            if (progressSeekbar != null) {
                progressSeekbar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && exoPlayer != null) {
                            long duration = exoPlayer.getDuration();
                            long newPosition = (duration * progress) / 100;
                            progressCurrentTime.setText(formatTime(newPosition));
                        }
                    }
                    
                    @Override
                    public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
                        isSeekbarTracking = true;
                    }
                    
                    @Override
                    public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                        isSeekbarTracking = false;
                        if (exoPlayer != null) {
                            long duration = exoPlayer.getDuration();
                            long newPosition = (duration * seekBar.getProgress()) / 100;
                            exoPlayer.seekTo(newPosition);
                        }
                    }
                });
            }
            
            // 更新当前速度显示
            updateSpeedLabel();
            // 更新弹幕状态
            updateDanmakuLabel();
        }
        
        // 更新进度条
        updateProgressBar();
        
        bottomMenuContainer.setVisibility(View.VISIBLE);
        menuSpeed.requestFocus();
        isMenuVisible = true;
        
        // 开始进度更新
        startProgressUpdate();
    }
    
    // 进度条更新
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isMenuVisible && !isSeekbarTracking) {
                updateProgressBar();
            }
            if (isMenuVisible) {
                progressHandler.postDelayed(this, 500);
            }
        }
    };
    
    private void startProgressUpdate() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }
    
    private void stopProgressUpdate() {
        progressHandler.removeCallbacks(progressRunnable);
    }
    
    private void updateProgressBar() {
        if (exoPlayer != null && progressSeekbar != null) {
            long currentPosition = exoPlayer.getCurrentPosition();
            long duration = exoPlayer.getDuration();
            long bufferedPosition = exoPlayer.getBufferedPosition();
            
            if (duration > 0) {
                // 播放进度
                int progress = (int) ((currentPosition * 100) / duration);
                progressSeekbar.setProgress(progress);
                progressCurrentTime.setText(formatTime(currentPosition));
                progressTotalTime.setText(formatTime(duration));
                
                // 缓存进度
                int bufferProgress = (int) ((bufferedPosition * 100) / duration);
                if (bufferProgressbar != null) {
                    bufferProgressbar.setProgress(bufferProgress);
                }
                
                // 缓存信息文本
                if (bufferInfoText != null) {
                    long bufferedSeconds = (bufferedPosition - currentPosition) / 1000;
                    if (bufferedSeconds > 0 && bufferedSeconds < 300) {
                        // 显示缓存了多少秒
                        bufferInfoText.setText("已缓存 " + bufferedSeconds + "s");
                    } else if (bufferProgress >= 99) {
                        bufferInfoText.setText("缓存完成");
                    } else {
                        bufferInfoText.setText("");
                    }
                }
            }
        }
    }
    
    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
    
    // 显示中央播放/暂停图标
    private void showCenterIcon(boolean isPlaying) {
        if (centerPlayIcon == null) {
            centerPlayIcon = findViewById(R.id.center_play_icon);
        }
        
        if (centerPlayIcon != null) {
            // 设置图标：播放时显示播放图标，暂停时显示暂停图标
            centerPlayIcon.setImageResource(isPlaying ? R.drawable.ic_play_arrow : R.drawable.ic_pause);
            centerPlayIcon.setVisibility(View.VISIBLE);
            
            // 取消之前的隐藏任务
            if (hideIconRunnable != null) {
                iconHandler.removeCallbacks(hideIconRunnable);
            }
            
            // 1秒后自动隐藏
            hideIconRunnable = () -> {
                if (centerPlayIcon != null) {
                    centerPlayIcon.setVisibility(View.GONE);
                }
            };
            iconHandler.postDelayed(hideIconRunnable, 1000);
        }
    }
    
    // 🎬 快进/快退进度条相关
    private View seekProgressOverlay;
    private TextView seekTimeText;
    private android.widget.ProgressBar seekProgressBar;
    private Handler seekOverlayHandler = new Handler(Looper.getMainLooper());
    private Runnable hideSeekOverlayRunnable;
    
    /**
     * 🎬 显示快进/快退进度条
     */
    private void showSeekProgressOverlay(long newPosition, boolean isForward) {
        if (exoPlayer == null) return;
        
        long duration = exoPlayer.getDuration();
        if (duration <= 0) return;
        
        // 初始化进度条视图
        if (seekProgressOverlay == null) {
            seekProgressOverlay = findViewById(R.id.seek_progress_overlay);
            seekTimeText = findViewById(R.id.seek_time_text);
            seekProgressBar = findViewById(R.id.seek_progress_bar);
        }
        
        // 如果布局中没有这个视图，动态创建
        if (seekProgressOverlay == null) {
            createSeekProgressOverlay();
        }
        
        if (seekProgressOverlay != null && seekTimeText != null && seekProgressBar != null) {
            // 显示进度条
            seekProgressOverlay.setVisibility(View.VISIBLE);
            
            // 设置时间文本
            String timeText = (isForward ? "▶▶ " : "◀◀ ") + formatTime(newPosition) + " / " + formatTime(duration);
            seekTimeText.setText(timeText);
            
            // 设置进度条
            int progress = (int) ((newPosition * 100) / duration);
            
            // 动画更新进度条
            android.animation.ObjectAnimator animator = android.animation.ObjectAnimator.ofInt(
                seekProgressBar, "progress", seekProgressBar.getProgress(), progress);
            animator.setDuration(200);
            animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
            animator.start();
            
            // 取消之前的隐藏任务
            if (hideSeekOverlayRunnable != null) {
                seekOverlayHandler.removeCallbacks(hideSeekOverlayRunnable);
            }
            
            // 2秒后自动隐藏
            hideSeekOverlayRunnable = () -> {
                if (seekProgressOverlay != null) {
                    // 淡出动画
                    seekProgressOverlay.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction(() -> {
                            seekProgressOverlay.setVisibility(View.GONE);
                            seekProgressOverlay.setAlpha(1f);
                        })
                        .start();
                }
            };
            seekOverlayHandler.postDelayed(hideSeekOverlayRunnable, 2000);
            
            // 淡入动画
            seekProgressOverlay.setAlpha(0f);
            seekProgressOverlay.animate().alpha(1f).setDuration(150).start();
        }
    }
    
    /**
     * 🎬 动态创建快进/快退进度条视图
     */
    private void createSeekProgressOverlay() {
        // 创建容器
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setId(View.generateViewId());
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setGravity(android.view.Gravity.CENTER);
        container.setPadding(60, 30, 60, 30);
        
        // 设置背景
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xCC000000);
        bg.setCornerRadius(24);
        container.setBackground(bg);
        
        // 时间文本
        seekTimeText = new TextView(this);
        seekTimeText.setTextColor(0xFFFFFFFF);
        seekTimeText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        seekTimeText.setGravity(android.view.Gravity.CENTER);
        container.addView(seekTimeText);
        
        // 进度条
        seekProgressBar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        android.widget.LinearLayout.LayoutParams progressParams = new android.widget.LinearLayout.LayoutParams(
            600, 12);
        progressParams.topMargin = 20;
        seekProgressBar.setLayoutParams(progressParams);
        seekProgressBar.setMax(100);
        seekProgressBar.setProgress(0);
        seekProgressBar.setProgressDrawable(getResources().getDrawable(R.drawable.seekbar_progress_bg, null));
        container.addView(seekProgressBar);
        
        // 添加到根布局
        android.widget.RelativeLayout.LayoutParams params = new android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.bottomMargin = 200;
        
        android.view.ViewGroup rootView = findViewById(android.R.id.content);
        if (rootView instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) rootView.getChildAt(0)).addView(container, params);
        }
        
        seekProgressOverlay = container;
        seekProgressOverlay.setVisibility(View.GONE);
    }
    
    private void hideSettingsMenu() {
        if (bottomMenuContainer != null) {
            bottomMenuContainer.setVisibility(View.GONE);
        }
        // 停止进度更新
        stopProgressUpdate();
        isMenuVisible = false;
    }
    
    private void updateSpeedLabel() {
        if (menuSpeed != null) {
            int index = 2; // 默认1.0x
            for (int i = 0; i < SPEED_OPTIONS.length; i++) {
                if (Math.abs(SPEED_OPTIONS[i] - currentSpeed) < 0.01f) {
                    index = i;
                    break;
                }
            }
            menuSpeed.setText("倍速 " + SPEED_LABELS[index]);
        }
    }
    
    private void updateDanmakuLabel() {
        if (menuDanmaku != null) {
            menuDanmaku.setText(isDanmakuEnabled ? "弹幕 开" : "弹幕 关");
        }
    }
    
    private void showSpeedMenu() {
        int currentIndex = 2;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            if (Math.abs(SPEED_OPTIONS[i] - currentSpeed) < 0.01f) {
                currentIndex = i;
                break;
            }
        }
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("播放速度")
            .setSingleChoiceItems(SPEED_LABELS, currentIndex, (dialog, which) -> {
                currentSpeed = SPEED_OPTIONS[which];
                if (exoPlayer != null) {
                    exoPlayer.setPlaybackSpeed(currentSpeed);
                }
                updateSpeedLabel();
                Toast.makeText(this, "播放速度: " + SPEED_LABELS[which], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            })
            .show();
    }
    
    private void showEpisodeMenu() {
        if (episodeList == null || episodeList.isEmpty()) {
            Toast.makeText(this, "暂无剧集列表", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 构建剧集选项
        String[] episodeLabels = new String[episodeList.size()];
        int currentIndex = -1;
        
        for (int i = 0; i < episodeList.size(); i++) {
            com.mynas.nastv.model.EpisodeListResponse.Episode ep = episodeList.get(i);
            String title = ep.getTitle();
            if (title != null && !title.isEmpty()) {
                episodeLabels[i] = "第" + ep.getEpisodeNumber() + "集 " + title;
            } else {
                episodeLabels[i] = "第" + ep.getEpisodeNumber() + "集";
            }
            
            // 找到当前播放的剧集
            if (ep.getEpisodeNumber() == episodeNumber) {
                currentIndex = i;
            }
        }
        
        final int checkedItem = currentIndex;
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("选集")
            .setSingleChoiceItems(episodeLabels, checkedItem, (dialog, which) -> {
                com.mynas.nastv.model.EpisodeListResponse.Episode selectedEp = episodeList.get(which);
                if (selectedEp.getEpisodeNumber() != episodeNumber) {
                    // 切换到选中的剧集
                    playEpisode(selectedEp);
                }
                dialog.dismiss();
            })
            .show();
    }
    
    /**
     * 📺 播放指定剧集
     */
    private void playEpisode(com.mynas.nastv.model.EpisodeListResponse.Episode episode) {
        Toast.makeText(this, "正在加载第" + episode.getEpisodeNumber() + "集...", Toast.LENGTH_SHORT).show();
        
        mediaManager.startPlayWithInfo(episode.getGuid(), new MediaManager.MediaCallback<com.mynas.nastv.model.PlayStartInfo>() {
            @Override
            public void onSuccess(com.mynas.nastv.model.PlayStartInfo playInfo) {
                runOnUiThread(() -> {
                    // 更新当前剧集信息
                    episodeNumber = episode.getEpisodeNumber();
                    episodeGuid = episode.getGuid();
                    videoGuid = playInfo.getVideoGuid();
                    audioGuid = playInfo.getAudioGuid();
                    mediaGuid = playInfo.getMediaGuid();
                    
                    // 更新标题
                    String newTitle = episode.getTitle() != null ? episode.getTitle() : "第" + episode.getEpisodeNumber() + "集";
                    mediaTitle = newTitle;
                    titleText.setText(tvTitle != null ? tvTitle : newTitle);
                    infoText.setText("S" + seasonNumber + " E" + episodeNumber);
                    
                    // 重置恢复位置
                    resumePositionSeconds = playInfo.getResumePositionSeconds();
                    
                    // 停止当前播放
                    if (exoPlayer != null) {
                        exoPlayer.stop();
                    }
                    
                    // 停止弹幕
                    if (danmuController != null) {
                        danmuController.pausePlayback();
                    }
                    
                    // 播放新视频
                    videoUrl = playInfo.getPlayUrl();
                    playMedia(videoUrl);
                    
                    hideSettingsMenu();
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(VideoPlayerActivity.this, "加载失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    /**
     * 📺 播放下一集
     */
    private void playNextEpisode() {
        if (episodeList == null || episodeList.isEmpty()) {
            Toast.makeText(this, "暂无下一集", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 找到当前剧集的下一集
        for (int i = 0; i < episodeList.size(); i++) {
            if (episodeList.get(i).getEpisodeNumber() == episodeNumber) {
                if (i + 1 < episodeList.size()) {
                    playEpisode(episodeList.get(i + 1));
                } else {
                    Toast.makeText(this, "已经是最后一集", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
        
        Toast.makeText(this, "暂无下一集", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 📺 自动播放下一集（播放结束时调用）
     */
    private void playNextEpisodeAuto() {
        if (episodeList == null || episodeList.isEmpty()) {
            finish();
            return;
        }
        
        // 找到当前剧集的下一集
        for (int i = 0; i < episodeList.size(); i++) {
            if (episodeList.get(i).getEpisodeNumber() == episodeNumber) {
                if (i + 1 < episodeList.size()) {
                    Toast.makeText(this, "自动播放下一集...", Toast.LENGTH_SHORT).show();
                    playEpisode(episodeList.get(i + 1));
                } else {
                    Toast.makeText(this, "已播放完最后一集", Toast.LENGTH_SHORT).show();
                    finish();
                }
                return;
            }
        }
        
        finish();
    }
    
    private void showQualityMenu() {
        String[] qualityLabels = {"原画", "1080P", "720P", "480P"};
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("画质选择")
            .setItems(qualityLabels, (dialog, which) -> {
                Toast.makeText(this, "已选择: " + qualityLabels[which], Toast.LENGTH_SHORT).show();
            })
            .show();
    }
    
    private void showSubtitleMenu() {
        // 构建字幕选项列表
        java.util.List<String> options = new java.util.ArrayList<>();
        options.add("关闭字幕");
        
        if (subtitleStreams != null && !subtitleStreams.isEmpty()) {
            for (com.mynas.nastv.model.StreamListResponse.SubtitleStream sub : subtitleStreams) {
                String label = sub.getTitle();
                if (label == null || label.isEmpty()) {
                    label = sub.getLanguage();
                }
                if (label == null || label.isEmpty()) {
                    label = "字幕 " + (options.size());
                }
                // 标记字幕类型
                if (sub.isExternal()) {
                    label += " (外挂)";
                } else {
                    // 内嵌字幕在直连模式下不可用
                    label += " (内嵌" + (isDirectLinkMode ? "-不可用" : "") + ")";
                }
                options.add(label);
            }
        }
        
        String[] subtitleOptions = options.toArray(new String[0]);
        int checkedItem = currentSubtitleIndex + 1; // +1 因为第一个是"关闭字幕"
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("字幕设置")
            .setSingleChoiceItems(subtitleOptions, checkedItem, (dialog, which) -> {
                if (which == 0) {
                    // 关闭字幕
                    disableSubtitle();
                    Toast.makeText(this, "字幕已关闭", Toast.LENGTH_SHORT).show();
                } else {
                    int subtitleIndex = which - 1;
                    com.mynas.nastv.model.StreamListResponse.SubtitleStream sub = subtitleStreams.get(subtitleIndex);
                    
                    if (sub.isExternal()) {
                        // 外挂字幕：下载并加载
                        loadSubtitle(subtitleIndex);
                    } else {
                        // 内嵌字幕：直连模式下不支持
                        if (isDirectLinkMode) {
                            Toast.makeText(this, "直连模式不支持内嵌字幕，请使用转码模式", Toast.LENGTH_LONG).show();
                        } else {
                            // 非直连模式可以尝试轨道选择
                            enableInternalSubtitle(subtitleIndex);
                        }
                    }
                }
                dialog.dismiss();
            })
            .show();
    }
    
    /**
     * 📝 关闭字幕
     */
    private void disableSubtitle() {
        currentSubtitleIndex = -1;
        
        // 禁用字幕轨道
        if (exoPlayer != null) {
            try {
                androidx.media3.common.TrackSelectionParameters params = exoPlayer.getTrackSelectionParameters()
                    .buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                    .build();
                exoPlayer.setTrackSelectionParameters(params);
                Log.e(TAG, "📝 Subtitle track disabled");
            } catch (Exception e) {
                Log.e(TAG, "📝 Error disabling subtitle", e);
            }
        }
        
        Log.e(TAG, "📝 Subtitle disabled");
    }
    
    private boolean isDanmakuEnabled = true;
    
    private void toggleDanmaku() {
        isDanmakuEnabled = !isDanmakuEnabled;
        if (danmuController != null) {
            if (isDanmakuEnabled) {
                danmuController.startPlayback();
            } else {
                danmuController.pausePlayback();
            }
        }
        if (danmuContainer != null) {
            danmuContainer.setVisibility(isDanmakuEnabled ? View.VISIBLE : View.GONE);
        }
        updateDanmakuLabel();
        Toast.makeText(this, isDanmakuEnabled ? "弹幕已开启" : "弹幕已关闭", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * ⚙️ 显示设置对话框
     */
    private void showSettingsDialog() {
        String[] settingsItems = {
            "自动连播: " + (SharedPreferencesManager.isAutoPlayNext() ? "开" : "关"),
            "跳过片头/片尾",
            "画面比例: " + getAspectRatioLabel(SharedPreferencesManager.getAspectRatio()),
            "音频轨道"
        };
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("设置")
            .setItems(settingsItems, (dialog, which) -> {
                switch (which) {
                    case 0: // 自动连播
                        toggleAutoPlayNext();
                        break;
                    case 1: // 跳过片头/片尾
                        showSkipIntroOutroDialog();
                        break;
                    case 2: // 画面比例
                        showAspectRatioDialog();
                        break;
                    case 3: // 音频轨道
                        showAudioTrackDialog();
                        break;
                }
            })
            .show();
    }
    
    /**
     * ⚙️ 切换自动连播
     */
    private void toggleAutoPlayNext() {
        boolean current = SharedPreferencesManager.isAutoPlayNext();
        SharedPreferencesManager.setAutoPlayNext(!current);
        Toast.makeText(this, "自动连播: " + (!current ? "开" : "关"), Toast.LENGTH_SHORT).show();
    }
    
    /**
     * ⚙️ 显示跳过片头/片尾设置对话框
     */
    private void showSkipIntroOutroDialog() {
        String[] options = {
            "跳过片头: " + formatSkipTime(SharedPreferencesManager.getSkipIntro()),
            "跳过片尾: " + formatSkipTime(SharedPreferencesManager.getSkipOutro())
        };
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("跳过片头/片尾")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    showSkipTimeDialog(true);
                } else {
                    showSkipTimeDialog(false);
                }
            })
            .show();
    }
    
    /**
     * ⚙️ 显示跳过时间选择对话框
     */
    private void showSkipTimeDialog(boolean isIntro) {
        String[] timeOptions = {"未设置", "30秒", "60秒", "90秒", "120秒", "自定义"};
        int[] timeValues = {0, 30, 60, 90, 120, -1};
        
        int currentValue = isIntro ? SharedPreferencesManager.getSkipIntro() : SharedPreferencesManager.getSkipOutro();
        int checkedItem = 0;
        for (int i = 0; i < timeValues.length - 1; i++) {
            if (timeValues[i] == currentValue) {
                checkedItem = i;
                break;
            }
        }
        
        new android.app.AlertDialog.Builder(this)
            .setTitle(isIntro ? "跳过片头" : "跳过片尾")
            .setSingleChoiceItems(timeOptions, checkedItem, (dialog, which) -> {
                if (which == 5) {
                    // 自定义时间
                    showCustomSkipTimeDialog(isIntro);
                } else {
                    if (isIntro) {
                        SharedPreferencesManager.setSkipIntro(timeValues[which]);
                    } else {
                        SharedPreferencesManager.setSkipOutro(timeValues[which]);
                    }
                    Toast.makeText(this, (isIntro ? "跳过片头: " : "跳过片尾: ") + timeOptions[which], Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            })
            .show();
    }
    
    /**
     * ⚙️ 显示自定义跳过时间对话框
     */
    private void showCustomSkipTimeDialog(boolean isIntro) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("输入秒数");
        
        int currentValue = isIntro ? SharedPreferencesManager.getSkipIntro() : SharedPreferencesManager.getSkipOutro();
        if (currentValue > 0) {
            input.setText(String.valueOf(currentValue));
        }
        
        new android.app.AlertDialog.Builder(this)
            .setTitle(isIntro ? "自定义跳过片头时间" : "自定义跳过片尾时间")
            .setView(input)
            .setPositiveButton("确定", (dialog, which) -> {
                try {
                    int seconds = Integer.parseInt(input.getText().toString());
                    if (isIntro) {
                        SharedPreferencesManager.setSkipIntro(seconds);
                    } else {
                        SharedPreferencesManager.setSkipOutro(seconds);
                    }
                    Toast.makeText(this, "已设置为 " + seconds + " 秒", Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private String formatSkipTime(int seconds) {
        if (seconds <= 0) return "未设置";
        return seconds + "秒";
    }
    
    /**
     * ⚙️ 显示画面比例对话框
     */
    private void showAspectRatioDialog() {
        String[] ratioOptions = {"默认", "16:9", "4:3", "填充屏幕"};
        int currentRatio = SharedPreferencesManager.getAspectRatio();
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("画面比例")
            .setSingleChoiceItems(ratioOptions, currentRatio, (dialog, which) -> {
                SharedPreferencesManager.setAspectRatio(which);
                applyAspectRatio(which);
                Toast.makeText(this, "画面比例: " + ratioOptions[which], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            })
            .show();
    }
    
    private String getAspectRatioLabel(int ratio) {
        switch (ratio) {
            case 1: return "16:9";
            case 2: return "4:3";
            case 3: return "填充";
            default: return "默认";
        }
    }
    
    /**
     * ⚙️ 应用画面比例
     */
    private void applyAspectRatio(int ratio) {
        if (playerView == null) return;
        
        switch (ratio) {
            case 0: // 默认
                playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
                break;
            case 1: // 16:9
                playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
                break;
            case 2: // 4:3
                playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT);
                break;
            case 3: // 填充
                playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL);
                break;
        }
    }
    
    /**
     * ⚙️ 显示音频轨道对话框
     */
    private void showAudioTrackDialog() {
        if (exoPlayer == null) {
            Toast.makeText(this, "播放器未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 获取音频轨道列表
        java.util.List<String> audioTracks = new java.util.ArrayList<>();
        java.util.List<Integer> trackIndices = new java.util.ArrayList<>();
        int selectedIndex = -1;
        
        try {
            androidx.media3.common.Tracks tracks = exoPlayer.getCurrentTracks();
            int audioTrackCount = 0;
            
            for (int i = 0; i < tracks.getGroups().size(); i++) {
                androidx.media3.common.Tracks.Group group = tracks.getGroups().get(i);
                if (group.getType() == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                    for (int j = 0; j < group.length; j++) {
                        androidx.media3.common.Format format = group.getTrackFormat(j);
                        String label = format.label;
                        if (label == null || label.isEmpty()) {
                            label = format.language;
                        }
                        if (label == null || label.isEmpty()) {
                            label = "音轨 " + (audioTrackCount + 1);
                        }
                        
                        // 添加编码信息
                        if (format.sampleMimeType != null) {
                            if (format.sampleMimeType.contains("ac3")) {
                                label += " (AC3)";
                            } else if (format.sampleMimeType.contains("eac3")) {
                                label += " (EAC3)";
                            } else if (format.sampleMimeType.contains("aac")) {
                                label += " (AAC)";
                            }
                        }
                        
                        audioTracks.add(label);
                        trackIndices.add(audioTrackCount);
                        
                        if (group.isTrackSelected(j)) {
                            selectedIndex = audioTrackCount;
                        }
                        audioTrackCount++;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting audio tracks", e);
        }
        
        if (audioTracks.isEmpty()) {
            audioTracks.add("默认音频");
            trackIndices.add(0);
            selectedIndex = 0;
        }
        
        String[] options = audioTracks.toArray(new String[0]);
        final int checkedItem = selectedIndex >= 0 ? selectedIndex : 0;
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("音频轨道")
            .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                selectAudioTrack(which);
                Toast.makeText(this, "已选择: " + options[which], Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            })
            .show();
    }
    
    /**
     * ⚙️ 选择音频轨道
     */
    private void selectAudioTrack(int trackIndex) {
        if (exoPlayer == null) return;
        
        try {
            androidx.media3.common.Tracks tracks = exoPlayer.getCurrentTracks();
            int audioTrackCount = 0;
            
            for (int i = 0; i < tracks.getGroups().size(); i++) {
                androidx.media3.common.Tracks.Group group = tracks.getGroups().get(i);
                if (group.getType() == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                    for (int j = 0; j < group.length; j++) {
                        if (audioTrackCount == trackIndex) {
                            // 选择这个音轨
                            androidx.media3.common.Format format = group.getTrackFormat(j);
                            androidx.media3.common.TrackSelectionParameters params = exoPlayer.getTrackSelectionParameters()
                                .buildUpon()
                                .setPreferredAudioLanguage(format.language)
                                .build();
                            exoPlayer.setTrackSelectionParameters(params);
                            Log.d(TAG, "Selected audio track: " + format.language);
                            return;
                        }
                        audioTrackCount++;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error selecting audio track", e);
        }
    }
}
