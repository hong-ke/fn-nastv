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

import com.shuyu.gsyvideoplayer.builder.GSYVideoOptionBuilder;
import com.shuyu.gsyvideoplayer.listener.GSYVideoProgressListener;
import com.shuyu.gsyvideoplayer.listener.VideoAllCallBack;
import com.shuyu.gsyvideoplayer.video.StandardGSYVideoPlayer;
import com.shuyu.gsyvideoplayer.utils.GSYVideoType;

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
    private StandardGSYVideoPlayer playerView;
    // subtitleView 已移除 - GSYVideoPlayer + IJKPlayer 不支持字幕
    private ImageView posterImageView;
    private LinearLayout topInfoContainer;
    private TextView titleText;
    private TextView infoText;
    private View loadingLayout;
    private View errorLayout;
    private TextView errorText;
    private FrameLayout danmuContainer;
    
    // ExoPlayer 已移除 - 使用 GSYVideoPlayer + IJKPlayer
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
    
    // 🚀 缓存由 GSYVideoPlayer + OkHttpProxyCacheManager 处理
    
    // Manager
    private MediaManager mediaManager;
    
    // 🎬 播放进度记录器
    private ProgressRecorder progressRecorder;
    
    private boolean isPlayerReady = false;
    
    // 🔧 解码器自动切换：本次会话是否强制使用软解（硬解崩溃后自动切换）
    private boolean forceUseSoftwareDecoder = false;
    private int decoderRetryCount = 0;
    private static final int MAX_DECODER_RETRY = 1; // 最多重试1次（切换到软解）
    private tv.danmaku.ijk.media.player.IjkMediaPlayer currentIjkPlayer = null;
    
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
        // subtitleView 已移除 - GSYVideoPlayer + IJKPlayer 不支持字幕
        posterImageView = findViewById(R.id.poster_image);
        topInfoContainer = findViewById(R.id.top_info_container);
        titleText = findViewById(R.id.title_text);
        infoText = findViewById(R.id.info_text);
        loadingLayout = findViewById(R.id.loading_layout);
        errorLayout = findViewById(R.id.error_layout);
        errorText = findViewById(R.id.error_text);
        danmuContainer = findViewById(R.id.danmu_container);
        
        // 🔧 关键修复：立即隐藏海报背景，避免显示灰色山景默认图
        if (posterImageView != null) {
            posterImageView.setVisibility(View.GONE);
        }
        
        // 更新标题显示
        updateTitleDisplay();
        
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
    
    // 🔧 记录是否已显示软解提示（避免重复提示）
    private boolean hasShownSoftwareDecoderToast = false;
    
    private void initializePlayer() {
        try {
            // 🎬 初始化 GSYVideoPlayer
            Log.d(TAG, "🎬 Initializing GSYVideoPlayer");
            
            // 🔧 关键修复：显式设置使用 IJKPlayer 内核（ExoPlayer 遇到 HTTP 416 错误）
            // ExoPlayer 自动发送 Range 请求，与服务器 URL 中的 range 参数冲突
            // IJKPlayer 内核可以正确处理这种情况
            // 注意：必须显式设置，否则 GSYVideoPlayer 可能会根据视频格式或配置自动选择播放器
            // GSYVideoPlayer 默认使用 IJKPlayer，但为了明确，我们不设置
            // 如果需要使用 ExoPlayer，需要调用：
            // PlayerFactory.setPlayManager(Exo2PlayerManager.class);
            Log.d(TAG, "🎬 Using default IJKPlayer kernel (ExoPlayer has HTTP 416 Range request conflict)");
            
            // 🔧 设置视频渲染类型为 TEXTURE（TextureView）
            // TextureView 的 Surface 创建更可靠，不会出现 NULL native_window 问题
            // 虽然性能略低于 SurfaceView，但兼容性更好
            GSYVideoType.setRenderType(GSYVideoType.TEXTURE);
            Log.d(TAG, "🎬 Set render type to TEXTURE (more reliable than SURFACE for IJKPlayer)");
            
            // 🔧 设置屏幕缩放类型为默认（保持宽高比，不拉伸）
            // SCREEN_TYPE_DEFAULT = 0: 默认比例
            // SCREEN_TYPE_16_9 = 1: 16:9
            // SCREEN_TYPE_4_3 = 2: 4:3
            // SCREEN_TYPE_FULL = 3: 全屏拉伸
            // SCREEN_TYPE_MATCH_FULL = 4: 全屏裁剪
            // SCREEN_MATCH_FULL = -4: 全屏裁剪（负值）
            GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_DEFAULT);
            Log.d(TAG, "🎬 Set screen type to DEFAULT (keep aspect ratio)");
            
            // 🔧 配置解码器：根据用户设置和自动降级逻辑
            configureDecoder();
            
            // 🔧 设置播放器初始化成功监听器，用于检测实际使用的解码器
            com.shuyu.gsyvideoplayer.GSYVideoManager.instance().setPlayerInitSuccessListener((player, model) -> {
                Log.i(TAG, "🎬 播放器初始化成功，类型: " + player.getClass().getSimpleName());
                
                // 保存播放器引用，用于后续检测
                if (player instanceof tv.danmaku.ijk.media.player.IjkMediaPlayer) {
                    currentIjkPlayer = (tv.danmaku.ijk.media.player.IjkMediaPlayer) player;
                }
            });
            
            // 配置播放器选项
            GSYVideoOptionBuilder gsyVideoOptionBuilder = new GSYVideoOptionBuilder();
            gsyVideoOptionBuilder
                .setIsTouchWiget(false) // 禁用触摸控制，使用自定义菜单
                .setRotateViewAuto(false) // 禁用自动旋转
                .setLockLand(false) // 不锁定横屏
                .setShowFullAnimation(false) // 禁用全屏动画
                .setNeedLockFull(true) // 需要锁定全屏
                .setNeedShowWifiTip(false) // 🔧 禁用WiFi提示
                .setDismissControlTime(0) // 🔧 立即隐藏内置控制栏
                .setHideKey(true) // 🔧 隐藏返回键
                .setCacheWithPlay(false) // 默认不使用内置缓存（在 playMedia 中根据 URL 动态配置）
                .setVideoTitle(mediaTitle != null ? mediaTitle : "视频")
                .setVideoAllCallBack(new VideoAllCallBack() {
                    @Override
                    public void onStartPrepared(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onStartPrepared");
                        // 🔧 隐藏内置控制栏
                        if (playerView != null) {
                            playerView.getBackButton().setVisibility(View.GONE);
                            playerView.getFullscreenButton().setVisibility(View.GONE);
                            playerView.getStartButton().setVisibility(View.GONE);
                        }
                    }
                    
                    @Override
                    public void onPrepared(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onPrepared - 播放器已准备好");
                        isPlayerReady = true;
                        showPlayer();
                        hideBufferingIndicator();
                        
                        // 🔧 确保播放器视图可见
                        if (playerView != null) {
                            playerView.setVisibility(View.VISIBLE);
                            // 🔧 不再调用 bringToFront()，避免遮挡弹幕
                            // playerView.bringToFront();
                            // 🔧 再次隐藏内置控制栏（确保）
                            playerView.getBackButton().setVisibility(View.GONE);
                            playerView.getFullscreenButton().setVisibility(View.GONE);
                            playerView.getStartButton().setVisibility(View.GONE);
                        }
                        
                        // 🔧 弹幕容器不需要 bringToFront，它在布局中已经在播放器之后
                        // 通过 XML 布局顺序控制层级，不使用 bringToFront 避免遮挡播放器
                        if (danmuContainer != null) {
                            danmuContainer.setVisibility(View.VISIBLE);
                            Log.d(TAG, "🎬 弹幕容器已设置可见");
                        }
                        
                        // 🎬 启动弹幕播放和位置更新
                        if (danmuController != null) {
                            danmuController.startPlayback();
                            startPositionUpdate();
                            Log.d(TAG, "🎬 弹幕播放已启动");
                        }
                        
                        // 🎬 启动播放进度记录
                        if (progressRecorder != null && !progressRecorder.isRecording()) {
                            String itemGuid = episodeGuid != null ? episodeGuid : mediaGuid;
                            progressRecorder.startRecording(itemGuid, mediaGuid);
                            progressRecorder.setStreamGuids(videoGuid, audioGuid, null);
                        }
                        
                        // 注意：不需要再次调用 startPlayLogic()
                        // startPlayLogic() 已经在 playMedia() 中调用，会触发 onPrepared 回调
                        // 此时播放器已经准备好，会自动开始播放
                        
                        // 🎬 恢复播放位置（延迟执行，确保播放器已准备好）
                        if (resumePositionSeconds > 0) {
                            long resumePositionMs = resumePositionSeconds * 1000;
                            Log.d(TAG, "🎬 Resuming playback at position: " + resumePositionSeconds + "s");
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (playerView != null) {
                                    playerView.seekTo(resumePositionMs);
                                }
                            }, 500);
                            resumePositionSeconds = 0;
                        } else {
                            // 🎬 跳过片头功能
                            int skipIntro = SharedPreferencesManager.getSkipIntro();
                            if (skipIntro > 0 && !hasSkippedIntro) {
                                Log.d(TAG, "🎬 Skipping intro: " + skipIntro + "s");
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    if (playerView != null) {
                                        playerView.seekTo(skipIntro * 1000L);
                                    }
                                }, 500);
                                hasSkippedIntro = true;
                            }
                        }
                        
                        // 🔧 延迟检测解码器类型（等待视频开始解码）
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (currentIjkPlayer != null) {
                                checkDecoderAndShowToast(currentIjkPlayer);
                            }
                        }, 1000);
                    }
                    
                    @Override
                    public void onClickStartError(String url, Object... objects) {
                        Log.e(TAG, "🎬 GSYVideoPlayer onClickStartError");
                    }
                    
                    @Override
                    public void onClickStop(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onClickStop");
                    }
                    
                    @Override
                    public void onClickStopFullscreen(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onClickStopFullscreen");
                    }
                    
                    @Override
                    public void onClickResume(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onClickResume");
                        if (danmuController != null) {
                            danmuController.startPlayback();
                            startPositionUpdate();
                        }
                        if (progressRecorder != null && !progressRecorder.isRecording()) {
                            String itemGuid = episodeGuid != null ? episodeGuid : mediaGuid;
                            progressRecorder.startRecording(itemGuid, mediaGuid);
                            progressRecorder.setStreamGuids(videoGuid, audioGuid, null);
                        }
                    }
                    
                    @Override
                    public void onClickResumeFullscreen(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onClickResumeFullscreen");
                    }
                    
                    // onClickPause 和 onClickPauseFullscreen 在新版本中可能不存在或签名不同
                    // 使用 onClickResume 和 onClickPause 的相反逻辑来处理
                    
                    @Override
                    public void onClickSeekbar(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onClickSeekbar");
                    }
                    
                    @Override
                    public void onClickSeekbarFullscreen(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onClickSeekbarFullscreen");
                    }
                    
                    @Override
                    public void onClickStartThumb(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onClickStartThumb");
                    }
                    
                    @Override
                    public void onClickBlank(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onClickBlank - 切换菜单显示, isMenuVisible=" + isMenuVisible);
                        // 🔧 修复：在 GSYVideoPlayer 的点击回调中切换菜单显示
                        runOnUiThread(() -> {
                            Log.d(TAG, "🎬 onClickBlank runOnUiThread - isMenuVisible=" + isMenuVisible);
                            if (isMenuVisible) {
                                Log.d(TAG, "🎬 调用 hideSettingsMenu()");
                                hideSettingsMenu();
                            } else {
                                Log.d(TAG, "🎬 调用 showSettingsMenu()");
                                showSettingsMenu();
                            }
                        });
                    }
                    
                    @Override
                    public void onAutoComplete(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onAutoComplete");
                        // 🎬 自动连播：播放结束时自动播放下一集
                        if (SharedPreferencesManager.isAutoPlayNext() && episodeList != null && !episodeList.isEmpty()) {
                            playNextEpisodeAuto();
                        } else {
                            finish();
                        }
                    }
                    
                    @Override
                    public void onEnterFullscreen(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onEnterFullscreen");
                    }
                    
                    @Override
                    public void onQuitFullscreen(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onQuitFullscreen");
                    }
                    
                    @Override
                    public void onQuitSmallWidget(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onQuitSmallWidget");
                    }
                    
                    @Override
                    public void onEnterSmallWidget(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onEnterSmallWidget");
                    }
                    
                    @Override
                    public void onTouchScreenSeekVolume(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onTouchScreenSeekVolume");
                    }
                    
                    @Override
                    public void onTouchScreenSeekPosition(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onTouchScreenSeekPosition");
                    }
                    
                    @Override
                    public void onTouchScreenSeekLight(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onTouchScreenSeekLight");
                    }
                    
                    @Override
                    public void onPlayError(String url, Object... objects) {
                        String errorMsg = objects.length > 0 ? objects[0].toString() : "未知错误";
                        Log.e(TAG, "🎬 GSYVideoPlayer onPlayError: " + errorMsg);
                        
                        // 🔧 解码器自动降级：如果使用硬解失败，自动切换到软解重试
                        if (!forceUseSoftwareDecoder && !SharedPreferencesManager.useSoftwareDecoder() && decoderRetryCount < MAX_DECODER_RETRY) {
                            decoderRetryCount++;
                            forceUseSoftwareDecoder = true;
                            Log.w(TAG, "🎬 硬解失败，自动切换到软解重试 (retry=" + decoderRetryCount + ")");
                            runOnUiThread(() -> {
                                Toast.makeText(VideoPlayerActivity.this, "硬解失败，自动切换软解", Toast.LENGTH_SHORT).show();
                                // 重新配置解码器并播放
                                configureDecoder();
                                if (currentVideoUrl != null) {
                                    playMedia(currentVideoUrl);
                                }
                            });
                            return;
                        }
                        
                        showError("播放错误: " + errorMsg);
                    }
                    
                    @Override
                    public void onClickBlankFullscreen(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onClickBlankFullscreen - 切换菜单显示");
                        // 🔧 修复：全屏模式下也切换菜单显示
                        if (isMenuVisible) {
                            hideSettingsMenu();
                        } else {
                            showSettingsMenu();
                        }
                    }
                    
                    @Override
                    public void onComplete(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onComplete");
                    }
                    
                    @Override
                    public void onClickStartIcon(String url, Object... objects) {
                        Log.d(TAG, "🎬 GSYVideoPlayer onClickStartIcon");
                    }
                })
                .setGSYVideoProgressListener(new GSYVideoProgressListener() {
                    @Override
                    public void onProgress(long progress, long secProgress, long currentPosition, long totalDuration) {
                        // 更新进度记录
                        if (progressRecorder != null && progressRecorder.isRecording() && totalDuration > 0) {
                            progressRecorder.updateProgress(currentPosition / 1000, totalDuration / 1000);
                        }
                    }
                });
            
            // 应用配置到播放器
            gsyVideoOptionBuilder.build(playerView);
            
            // �  字幕功能已禁用 - GSYVideoPlayer + IJKPlayer 不支持外挂字幕
            // subtitleView 配置代码已移除
            
        } catch (Exception e) {
            Log.e(TAG, "GSYVideoPlayer Init Failed", e);
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
        Log.e(TAG, "playMedia called with URL: " + url);
        Log.e(TAG, "Danmaku params for playback: title=" + tvTitle + ", s" + seasonNumber + "e" + episodeNumber + ", guid=" + episodeGuid);
        showLoading("Loading...");
        
        // 🔧 关键修复：保持 URL 原样，不修改
        // 签名验证基于原始 URL，如果修改 URL 会导致鉴权失败（HTTP 416）
        // ExoPlayer 会自动处理 Range 请求，不需要手动修改 URL
        String playUrl = url;
        
        // 保存当前视频URL
        currentVideoUrl = url;
        
        try {
            // 🚀 为直连 URL 启用缓存和多线程加速
            // 判断是否为直连 URL（包含 direct_link_quality_index 或外部云存储 URL）
            boolean isDirectLink = url.contains("direct_link_quality_index") ||
                (url.startsWith("https://") && !url.contains("192.168.") && !url.contains("localhost"));
            
            // 🚀 为直连 URL 启用缓存
            java.io.File cacheDir = null;
            if (isDirectLink) {
                // 🚀 直连 URL：启用缓存并设置缓存路径
                cacheDir = new java.io.File(getCacheDir(), "gsy_video_cache");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
                Log.d(TAG, "🚀 Direct link detected, enabling cache at: " + cacheDir.getAbsolutePath());
                Log.d(TAG, "🚀 Cache directory exists: " + cacheDir.exists() + ", writable: " + cacheDir.canWrite());
            }
            
            // 🎬 使用 GSYVideoPlayer 播放
            // 🔧 关键：使用原始 URL 生成请求头（包含正确的签名）
            Map<String, String> headers = createHeadersForUrl(url);
            
            // 设置播放器标题
            String videoTitle = mediaTitle != null ? mediaTitle : "视频";
            
            // 🔧 关键：如果启用缓存，需要在 setUp 时传递缓存路径
            // GSYVideoPlayer 的 setUp 方法签名：setUp(String url, boolean cacheWithPlay, File cachePath, Map<String, String> mapHeadData, String title)
            // ⚠️ 注意：GSYVideoPlayer 的 HttpProxyCacheServer 可能不会使用 setUp 中传递的 headers
            // 需要在 setUp 之后再次调用 setMapHeadData 来确保 headers 被正确设置
            if (isDirectLink && cacheDir != null) {
                Log.d(TAG, "🚀 Setting up with cache: cacheWithPlay=true, cachePath=" + cacheDir.getAbsolutePath());
                Log.d(TAG, "🚀 Headers to be set: " + (headers != null ? headers.keySet() : "null"));
                
                // 🔑 关键修复：在 setUp 之前设置 OkHttpProxyCacheManager 的 headers
                // OkHttpProxyCacheManager 使用 OkHttp 替代 HttpURLConnection，能正确传递认证头
                com.mynas.nastv.cache.OkHttpProxyCacheManager.setCurrentHeaders(headers);
                Log.d(TAG, "🔑 OkHttpProxyCacheManager headers set before setUp");
                
                playerView.setUp(playUrl, true, cacheDir, headers, videoTitle);
                
                // 🔧 关键修复：HttpProxyCacheServer 可能不会使用 setUp 中的 headers
                // 需要在 setUp 之后再次设置 headers，确保缓存代理服务器能使用正确的认证头
                if (headers != null && !headers.isEmpty()) {
                    playerView.setMapHeadData(headers);
                    Log.d(TAG, "🚀 Headers set again via setMapHeadData for cache proxy: " + headers.keySet());
                }
            } else {
                Log.d(TAG, "🎬 Setting up without cache: cacheWithPlay=false");
                playerView.setUp(playUrl, false, null, headers, videoTitle);
                
                // 非缓存模式也需要设置 headers
                if (headers != null && !headers.isEmpty()) {
                    playerView.setMapHeadData(headers);
                    Log.d(TAG, "🎬 Headers set via setMapHeadData: " + headers.keySet());
                }
            }
            
            // 🔧 调试：在播放前记录 URL，用于后续分析播放器选择
            Log.d(TAG, "🎬 Setting up video: URL=" + playUrl.substring(0, Math.min(100, playUrl.length())) + "...");
            
            // 开始播放
            playerView.startPlayLogic();
            Log.d(TAG, "🎬 startPlayLogic() called - will trigger onPrepared callback");
            
            // Load Danmaku - 使用 title + season + episode + guid 获取弹幕
            if (danmuController != null) {
                if (tvTitle != null && !tvTitle.isEmpty()) {
                    Log.e(TAG, "Loading danmaku with title=" + tvTitle + ", s" + seasonNumber + "e" + episodeNumber);
                    danmuController.loadDanmaku(tvTitle, episodeNumber, seasonNumber, episodeGuid, parentGuid);
                } else {
                    Log.w(TAG, "No valid title for danmaku, skipping. title=" + tvTitle);
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
     * 创建请求头
     */
    private Map<String, String> createHeadersForUrl(String url) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Accept", "*/*");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Accept-Encoding", "identity");
        headers.put("Connection", "keep-alive");
        
        // 🔧 关键修复：对于包含 /range/ 的 URL，确保使用原始 URL 生成签名
        // 签名验证基于完整的 URL 路径，包括 /range/ 部分
        if (url.contains("/range/")) {
            Log.d(TAG, "🔧 URL contains /range/ path, will use original URL for signature generation");
        }
        
        // 判断是否为直连URL
        boolean isExternalDirectLink = url.startsWith("https://") && !url.contains("192.168.") && !url.contains("localhost");
        boolean isProxyDirectLink = url.contains("direct_link_quality_index");
        
        if (isProxyDirectLink) {
            String token = SharedPreferencesManager.getAuthToken();
            if (token != null && !token.isEmpty()) {
                String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
                headers.put("Cookie", "Trim-MC-token=" + authToken);
                headers.put("Authorization", authToken);
                
                // 🔍 详细日志：打印认证信息（隐藏敏感内容）
                Log.d(TAG, "🔍 [CURL TEST] Token length: " + authToken.length() + ", first 10 chars: " + 
                      (authToken.length() > 10 ? authToken.substring(0, 10) + "..." : authToken));
                
                try {
                    String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature("GET", url, "", null);
                    if (signature != null) {
                        headers.put("authx", signature);
                        // 🔍 详细日志：打印签名信息
                        Log.d(TAG, "🔍 [CURL TEST] Signature length: " + signature.length() + ", first 10 chars: " + 
                              (signature.length() > 10 ? signature.substring(0, 10) + "..." : signature));
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Sign failed", e);
                }
            }
        } else if (isExternalDirectLink) {
            String referer = "https://pan.quark.cn/";
            try {
                java.net.URL parsedUrl = new java.net.URL(url);
                referer = parsedUrl.getProtocol() + "://" + parsedUrl.getHost() + "/";
            } catch (Exception e) {
                Log.w(TAG, "Parse URL failed", e);
            }
            headers.put("Referer", referer);
            headers.put("Origin", referer.substring(0, referer.length() - 1));
            headers.put("Sec-Fetch-Dest", "video");
            headers.put("Sec-Fetch-Mode", "cors");
            headers.put("Sec-Fetch-Site", "cross-site");
        }
        
        // 🔍 详细日志：打印所有 headers（用于 curl 测试）
        Log.d(TAG, "🔍 [CURL TEST] ===== Headers for URL =====");
        Log.d(TAG, "🔍 [CURL TEST] URL: " + url);
        Log.d(TAG, "🔍 [CURL TEST] curl -v -X GET \\");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            // 隐藏敏感信息的部分内容
            if (key.equals("Cookie") || key.equals("Authorization") || key.equals("authx")) {
                String maskedValue = value.length() > 20 ? value.substring(0, 20) + "..." : value;
                Log.d(TAG, "🔍 [CURL TEST]   -H \"" + key + ": " + maskedValue + "\" \\");
            } else {
                Log.d(TAG, "🔍 [CURL TEST]   -H \"" + key + ": " + value + "\" \\");
            }
        }
        Log.d(TAG, "🔍 [CURL TEST]   \"" + url + "\"");
        Log.d(TAG, "🔍 [CURL TEST] =============================");
        
        return headers;
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
     * 注意：GSYVideoPlayer + IJKPlayer 不支持外挂字幕，此方法仅显示提示
     */
    private void addSubtitleToPlayer(java.io.File subtitleFile, 
            com.mynas.nastv.model.StreamListResponse.SubtitleStream subtitle,
            String format, int subtitleIndex) {
        
        Log.e(TAG, "📝 GSYVideoPlayer + IJKPlayer 不支持外挂字幕");
        Toast.makeText(this, "当前播放器不支持外挂字幕", Toast.LENGTH_SHORT).show();
    }
    
    // enableSubtitleTrack, logCurrentTracks 已移除 - ExoPlayer 相关代码
    
    // 📝 createDirectLinkMediaSource 已移除 - GSYVideoPlayer 使用 OkHttpProxyCacheManager 处理缓存
    // 📝 getMimeTypeForSubtitle 已移除 - ExoPlayer 相关代码
    // 📝 createCustomExtractorsFactory 已移除 - ExoPlayer 相关代码
    
    /**
     * 📝 启用内嵌字幕（通过轨道选择）
     * 注意：GSYVideoPlayer + IJKPlayer 不支持内嵌字幕选择，此方法仅显示提示
     */
    private void enableInternalSubtitle(int index) {
        Log.e(TAG, "📝 GSYVideoPlayer + IJKPlayer 不支持内嵌字幕选择");
        Toast.makeText(this, "当前播放器不支持内嵌字幕", Toast.LENGTH_SHORT).show();
    }
    
    // selectSubtitleTrack 已移除 - ExoPlayer 相关代码
    // createMediaItemWithHeaders 已移除 - GSYVideoPlayer 使用 OkHttpProxyCacheManager 处理缓存和认证
    
    /**
     * 🔧 配置解码器：根据用户设置和自动降级逻辑
     */
    private void configureDecoder() {
        boolean useSoftware = SharedPreferencesManager.useSoftwareDecoder() || forceUseSoftwareDecoder;
        
        if (useSoftware) {
            // 软解模式
            GSYVideoType.disableMediaCodec();
            Log.i(TAG, "🎬 解码器配置: 软解模式");
        } else {
            // 硬解模式
            GSYVideoType.enableMediaCodec();
            GSYVideoType.enableMediaCodecTexture();
            Log.i(TAG, "🎬 解码器配置: 硬解模式");
        }
        
        // 配置 IJKPlayer 高级选项
        try {
            com.shuyu.gsyvideoplayer.GSYVideoManager.instance().setOptionModelList(getIjkOptions(useSoftware));
            Log.i(TAG, "🎬 IJKPlayer 选项已配置");
        } catch (Exception e) {
            Log.e(TAG, "🎬 配置 IJKPlayer 选项失败", e);
        }
    }
    
    /**
     * 🔧 检查解码器并显示提示
     */
    private void checkDecoderAndShowToast(tv.danmaku.ijk.media.player.IjkMediaPlayer ijkPlayer) {
        if (hasShownSoftwareDecoderToast) return;
        
        boolean configuredHardware = !SharedPreferencesManager.useSoftwareDecoder() && !forceUseSoftwareDecoder;
        
        if (!configuredHardware) {
            Log.i(TAG, "🎬 已配置软解，无需检测");
            return;
        }
        
        // 检查设备是否支持 HEVC 硬解
        // 通过 MediaCodecList 检查是否有 HEVC 硬件解码器
        try {
            android.media.MediaCodecList codecList = new android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS);
            boolean hasHevcHardwareDecoder = false;
            
            for (android.media.MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
                if (codecInfo.isEncoder()) continue;
                
                String[] types = codecInfo.getSupportedTypes();
                for (String type : types) {
                    if (type.equalsIgnoreCase("video/hevc")) {
                        // 检查是否是硬件解码器（不是 OMX.google 开头的）
                        String name = codecInfo.getName();
                        if (!name.startsWith("OMX.google.")) {
                            hasHevcHardwareDecoder = true;
                            Log.i(TAG, "🎬 找到 HEVC 硬件解码器: " + name);
                            break;
                        }
                    }
                }
                if (hasHevcHardwareDecoder) break;
            }
            
            if (!hasHevcHardwareDecoder) {
                // 设备没有 HEVC 硬件解码器，显示提示
                hasShownSoftwareDecoderToast = true;
                forceUseSoftwareDecoder = true;
                runOnUiThread(() -> {
                    Toast.makeText(VideoPlayerActivity.this, "硬解不支持，已自动切换软解", Toast.LENGTH_SHORT).show();
                });
                Log.i(TAG, "🎬 设备无 HEVC 硬件解码器，已自动切换到软解");
            } else {
                Log.i(TAG, "🎬 设备支持 HEVC 硬解");
            }
        } catch (Exception e) {
            Log.w(TAG, "🎬 检测解码器失败: " + e.getMessage());
        }
    }
    
    /**
     * 🔧 检查并显示解码器切换提示（备用方案）
     */
    private void checkAndShowDecoderToast() {
        if (hasShownSoftwareDecoderToast) return;
        
        boolean configuredHardware = !SharedPreferencesManager.useSoftwareDecoder() && !forceUseSoftwareDecoder;
        boolean mediaCodecEnabled = GSYVideoType.isMediaCodec();
        
        Log.i(TAG, "🎬 检测解码器状态: configuredHardware=" + configuredHardware + ", mediaCodecEnabled=" + mediaCodecEnabled);
    }
    
    /**
     * 🔧 获取 IJKPlayer 配置选项
     */
    private java.util.List<com.shuyu.gsyvideoplayer.model.VideoOptionModel> getIjkOptions(boolean useSoftware) {
        java.util.List<com.shuyu.gsyvideoplayer.model.VideoOptionModel> options = new java.util.ArrayList<>();
        
        // 播放器选项
        int playerCategory = tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER;
        int formatCategory = tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT;
        
        if (!useSoftware) {
            // 硬解模式配置
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec", 1));
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec-auto-rotate", 1));
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec-handle-resolution-change", 1));
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec-hevc", 1));
            Log.i(TAG, "🎬 IJKPlayer: 启用硬解 + HEVC 硬解");
        } else {
            // 软解模式配置
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec", 0));
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec-hevc", 0));
            Log.i(TAG, "🎬 IJKPlayer: 使用软解");
        }
        
        // 通用优化选项
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "framedrop", 1));
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "enable-accurate-seek", 1));
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "max-buffer-size", 15 * 1024 * 1024));
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "min-frames", 50));
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "start-on-prepared", 1));
        
        // 格式选项
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(formatCategory, "probesize", 10 * 1024 * 1024));
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(formatCategory, "analyzeduration", 5 * 1000 * 1000));
        
        return options;
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
            if (playerView != null) {
                // GSYVideoPlayer 获取播放位置的方法
                long currentPosition = 0;
                long duration = 0;
                try {
                    // GSYVideoPlayer API - 使用 getCurrentState() 检查状态
                    int state = playerView.getCurrentState();
                    // STATE_PLAYING = 2
                    if (state == 2) {
                        currentPosition = playerView.getCurrentPositionWhenPlaying();
                        duration = playerView.getDuration();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error getting position from GSYVideoPlayer", e);
                }
                
                if (currentPosition > 0 && duration > 0) {
                    // 更新弹幕位置
                    if (danmuController != null) {
                        danmuController.updatePlaybackPosition(currentPosition);
                        // 🔧 调试日志：每 5 秒打印一次弹幕位置更新
                        if (currentPosition % 5000 < 150) {
                            Log.d(TAG, "🎬 弹幕位置更新: " + (currentPosition/1000) + "s");
                        }
                    }
                    
                    // 🎬 更新播放进度记录器
                    if (progressRecorder != null) {
                        // 转换为秒
                        progressRecorder.updateProgress(currentPosition / 1000, duration / 1000);
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
    
    // 🚀 缓存由 GSYVideoPlayer + OkHttpProxyCacheManager 自动管理，无需手动停止
    // extractCacheKeyFromUrl 已移除 - ExoPlayer 相关代码
    
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
        
        if (playerView != null) {
            playerView.release();
            playerView = null;
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
             if (playerView != null) {
                 try {
                     // GSYVideoPlayer 使用 getCurrentState() 检查状态
                     int state = playerView.getCurrentState();
                     // GSYVideoPlayer 状态常量：STATE_PLAYING = 2, STATE_PAUSE = 1
                     if (state == 2) { // STATE_PLAYING
                         playerView.onVideoPause();
                         showCenterIcon(false); // 显示暂停图标
                     } else {
                         playerView.onVideoResume();
                         showCenterIcon(true); // 显示播放图标
                     }
                 } catch (Exception e) {
                     Log.w(TAG, "Error checking playing state", e);
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
            if (playerView != null) {
                try {
                    long currentPosition = playerView.getCurrentPositionWhenPlaying();
                    long duration = playerView.getDuration();
                    long newPosition = Math.max(0, currentPosition - 10000);
                    playerView.seekTo(newPosition);
                    showSeekProgressOverlay(newPosition, false);
                } catch (Exception e) {
                    Log.w(TAG, "Error seeking backward", e);
                }
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !isMenuVisible) {
            // 右键快进10秒（菜单不可见时）
            if (playerView != null) {
                try {
                    long currentPosition = playerView.getCurrentPositionWhenPlaying();
                    long duration = playerView.getDuration();
                    long newPosition = Math.min(duration, currentPosition + 10000);
                    playerView.seekTo(newPosition);
                    showSeekProgressOverlay(newPosition, true);
                } catch (Exception e) {
                    Log.w(TAG, "Error seeking forward", e);
                }
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
        Log.d(TAG, "🎬 showSettingsMenu() 被调用");
        if (bottomMenuContainer == null) {
            Log.d(TAG, "🎬 bottomMenuContainer 为 null，初始化视图");
            bottomMenuContainer = findViewById(R.id.bottom_menu_container);
            Log.d(TAG, "🎬 bottomMenuContainer = " + bottomMenuContainer);
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
                        if (fromUser && playerView != null) {
                            long duration = playerView.getDuration();
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
                        if (playerView != null) {
                            long duration = playerView.getDuration();
                            long newPosition = (duration * seekBar.getProgress()) / 100;
                            playerView.seekTo(newPosition);
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
        
        // 显示顶部信息
        if (topInfoContainer != null) {
            topInfoContainer.setVisibility(View.VISIBLE);
            topInfoContainer.bringToFront();
            topInfoContainer.requestLayout();
            Log.d(TAG, "🎬 topInfoContainer 设置为 VISIBLE");
        }
        
        if (bottomMenuContainer != null) {
            bottomMenuContainer.setVisibility(View.VISIBLE);
            bottomMenuContainer.bringToFront();
            // 🔧 强制请求布局，确保视图被正确测量
            bottomMenuContainer.requestLayout();
            bottomMenuContainer.invalidate();
            Log.d(TAG, "🎬 bottomMenuContainer 设置为 VISIBLE, visibility=" + bottomMenuContainer.getVisibility() + 
                       ", width=" + bottomMenuContainer.getWidth() + ", height=" + bottomMenuContainer.getHeight());
        }
        if (menuSpeed != null) {
            menuSpeed.requestFocus();
        }
        isMenuVisible = true;
        Log.d(TAG, "🎬 菜单已显示, isMenuVisible=" + isMenuVisible);
        
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
        if (playerView != null && progressSeekbar != null) {
            try {
                long currentPosition = playerView.getCurrentPositionWhenPlaying();
                long duration = playerView.getDuration();
                
                if (duration > 0) {
                    // 播放进度
                    int progress = (int) ((currentPosition * 100) / duration);
                    progressSeekbar.setProgress(progress);
                    progressCurrentTime.setText(formatTime(currentPosition));
                    progressTotalTime.setText(formatTime(duration));
                    
                    // 🔑 从 OkHttpProxyCacheManager 获取真实缓存进度
                    int bufferProgress = progress; // 默认等于播放进度
                    int cachedChunks = 0;
                    int currentChunk = 0;
                    
                    try {
                        com.mynas.nastv.cache.OkHttpProxyCacheManager cacheManager = 
                            com.mynas.nastv.cache.OkHttpProxyCacheManager.instance();
                        if (cacheManager != null) {
                            bufferProgress = cacheManager.getDownloadProgress();
                            cachedChunks = cacheManager.getCachedChunksCount();
                            currentChunk = cacheManager.getCurrentPlaybackChunk();
                        }
                    } catch (Exception e) {
                        // 忽略缓存管理器错误
                    }
                    
                    // 更新缓存进度条
                    if (bufferProgressbar != null) {
                        bufferProgressbar.setProgress(bufferProgress);
                    }
                    
                    // 缓存信息文本
                    if (bufferInfoText != null) {
                        if (cachedChunks > 0) {
                            // 显示缓存块数和进度
                            int cachedMB = cachedChunks * 2; // 每块 2MB
                            bufferInfoText.setText("已缓存 " + cachedMB + "MB (" + bufferProgress + "%)");
                        } else if (bufferProgress >= 99) {
                            bufferInfoText.setText("缓存完成");
                        } else {
                            bufferInfoText.setText("");
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Error updating progress bar", e);
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
        if (playerView == null) return;
        
        long duration = playerView.getDuration();
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
        Log.d(TAG, "🎬 hideSettingsMenu() 被调用");
        if (bottomMenuContainer != null) {
            bottomMenuContainer.setVisibility(View.GONE);
            Log.d(TAG, "🎬 bottomMenuContainer 设置为 GONE");
        }
        // 隐藏顶部信息
        if (topInfoContainer != null) {
            topInfoContainer.setVisibility(View.GONE);
        }
        // 停止进度更新
        stopProgressUpdate();
        isMenuVisible = false;
        Log.d(TAG, "🎬 菜单已隐藏, isMenuVisible=" + isMenuVisible);
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
    
    /**
     * 更新左上角标题显示
     * 电影：显示电影标题
     * 电视剧：显示《电视剧名》 第x季 第y集 + 集标题
     */
    private void updateTitleDisplay() {
        Log.d(TAG, "updateTitleDisplay called: titleText=" + (titleText != null) + ", infoText=" + (infoText != null));
        Log.d(TAG, "updateTitleDisplay data: tvTitle=" + tvTitle + ", seasonNumber=" + seasonNumber + ", episodeNumber=" + episodeNumber + ", mediaTitle=" + mediaTitle + ", seasonGuid=" + seasonGuid);
        
        if (titleText == null || infoText == null) return;
        
        // 判断是否为电视剧（有季/集信息）
        boolean isTvShow = seasonGuid != null && !seasonGuid.isEmpty() && episodeNumber > 0;
        Log.d(TAG, "updateTitleDisplay isTvShow=" + isTvShow);
        
        if (isTvShow && tvTitle != null && !tvTitle.isEmpty()) {
            // 电视剧：显示《电视剧名》 第x季 第y集
            String mainTitle = "《" + tvTitle + "》 第" + seasonNumber + "季 第" + episodeNumber + "集";
            titleText.setText(mainTitle);
            Log.d(TAG, "updateTitleDisplay TV: " + mainTitle);
            // 集标题
            if (mediaTitle != null && !mediaTitle.isEmpty() && !mediaTitle.equals(tvTitle)) {
                infoText.setText(mediaTitle);
                infoText.setVisibility(View.VISIBLE);
            } else {
                infoText.setVisibility(View.GONE);
            }
        } else {
            // 电影：只显示电影标题
            titleText.setText(mediaTitle != null ? mediaTitle : "未知标题");
            Log.d(TAG, "updateTitleDisplay Movie: " + mediaTitle);
            infoText.setVisibility(View.GONE);
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
                if (playerView != null) {
                    playerView.setSpeed(currentSpeed);
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
     * 
     * 🔧 完全重新初始化策略：
     * - 释放所有资源（ExoPlayer、缓存、预缓存服务）
     * - 清空共享缓存（避免旧数据干扰）
     * - 重新创建 ExoPlayer 和缓存工厂
     * - 就像首次进入一样，完全干净的状态
     */
    private void playEpisode(com.mynas.nastv.model.EpisodeListResponse.Episode episode) {
        Log.e(TAG, "🚀🚀🚀 playEpisode called for episode " + episode.getEpisodeNumber());
        Toast.makeText(this, "正在加载第" + episode.getEpisodeNumber() + "集...", Toast.LENGTH_SHORT).show();
        
        mediaManager.startPlayWithInfo(episode.getGuid(), new MediaManager.MediaCallback<com.mynas.nastv.model.PlayStartInfo>() {
            @Override
            public void onSuccess(com.mynas.nastv.model.PlayStartInfo playInfo) {
                runOnUiThread(() -> {
                    Log.e(TAG, "🔄 Starting FULL REINITIALIZATION for episode switch");
                    
                    // 更新当前剧集信息
                    episodeNumber = episode.getEpisodeNumber();
                    episodeGuid = episode.getGuid();
                    videoGuid = playInfo.getVideoGuid();
                    audioGuid = playInfo.getAudioGuid();
                    mediaGuid = playInfo.getMediaGuid();
                    
                    // 更新标题
                    String newTitle = episode.getTitle() != null ? episode.getTitle() : "第" + episode.getEpisodeNumber() + "集";
                    mediaTitle = newTitle;
                    updateTitleDisplay();
                    
                    // 重置恢复位置
                    resumePositionSeconds = playInfo.getResumePositionSeconds();
                    
                    // 🔧 步骤1：停止 GSYVideoPlayer
                    Log.e(TAG, "🔄 Step 1: Stopping GSYVideoPlayer");
                    if (playerView != null) {
                        playerView.release();
                        isPlayerReady = false;
                    }
                    
                    // 🔧 步骤2：清空弹幕缓存
                    Log.e(TAG, "🔄 Step 2: Clearing danmaku cache");
                    if (danmuController != null) {
                        danmuController.clearDanmaku();
                    }
                    
                    // 🔧 步骤3：重置播放器状态
                    Log.e(TAG, "🔄 Step 3: Resetting player state");
                    hasSkippedIntro = false;
                    currentSubtitleIndex = -1;
                    subtitleStreams = null;
                    
                    // 🔧 步骤4：重新初始化播放器
                    Log.e(TAG, "🔄 Step 4: Reinitializing player");
                    initializePlayer();
                    
                    // 🔧 步骤5：显示加载界面并播放新视频
                    Log.e(TAG, "🔄 Step 5: Playing new video");
                    showLoading("加载中...");
                    videoUrl = playInfo.getPlayUrl();
                    playMedia(videoUrl);
                    
                    hideSettingsMenu();
                    
                    Log.e(TAG, "🔄 Episode switch completed");
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
        
        // GSYVideoPlayer + IJKPlayer 不支持字幕轨道控制
        Log.e(TAG, "📝 Subtitle disabled (GSYVideoPlayer + IJKPlayer 不支持字幕轨道控制)");
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
        // 🔧 解码器显示：考虑自动切换的情况
        boolean actualUseSoftware = SharedPreferencesManager.useSoftwareDecoder() || forceUseSoftwareDecoder;
        String decoderLabel = actualUseSoftware ? "软解" : "硬解";
        if (forceUseSoftwareDecoder && !SharedPreferencesManager.useSoftwareDecoder()) {
            decoderLabel = "软解(自动)"; // 标记是自动切换的
        }
        
        String[] settingsItems = {
            "自动连播: " + (SharedPreferencesManager.isAutoPlayNext() ? "开" : "关"),
            "跳过片头/片尾",
            "画面比例: " + getAspectRatioLabel(SharedPreferencesManager.getAspectRatio()),
            "解码器: " + decoderLabel,
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
                    case 3: // 解码器
                        showDecoderDialog();
                        break;
                    case 4: // 音频轨道
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
     * ⚙️ 显示解码器选择对话框
     */
    private void showDecoderDialog() {
        String[] decoderOptions = {"硬解 (推荐)", "软解 (兼容性更好)"};
        int currentDecoder = SharedPreferencesManager.getDecoderType();
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("解码器")
            .setSingleChoiceItems(decoderOptions, currentDecoder, (dialog, which) -> {
                SharedPreferencesManager.setDecoderType(which);
                String msg = which == 0 ? "已切换到硬解，重新播放生效" : "已切换到软解，重新播放生效";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                dialog.dismiss();
                
                // 提示用户重新播放
                new android.app.AlertDialog.Builder(this)
                    .setTitle("解码器已更改")
                    .setMessage("需要重新加载视频才能生效，是否立即重新加载？")
                    .setPositiveButton("重新加载", (d, w) -> reloadVideo())
                    .setNegativeButton("稍后", null)
                    .show();
            })
            .show();
    }
    
    /**
     * 🔄 重新加载视频（用于切换解码器后）
     */
    private void reloadVideo() {
        if (playerView != null && currentVideoUrl != null) {
            // 保存当前播放位置
            long currentPosition = 0;
            try {
                currentPosition = playerView.getCurrentPositionWhenPlaying();
            } catch (Exception e) {
                Log.w(TAG, "获取当前播放位置失败", e);
            }
            
            // 重置解码器降级标志（用户手动切换时）
            forceUseSoftwareDecoder = false;
            decoderRetryCount = 0;
            
            // 停止当前播放
            playerView.release();
            
            // 重新配置解码器
            configureDecoder();
            
            // 设置恢复位置
            resumePositionSeconds = currentPosition / 1000;
            
            // 重新播放
            playMedia(currentVideoUrl);
            
            Toast.makeText(this, "正在重新加载...", Toast.LENGTH_SHORT).show();
        }
    }
    
    // shouldSwitchToSoftwareDecoder 已移除 - ExoPlayer 相关代码
    
    /**
     * 🔧 使用软解重新加载视频
     */
    private void reloadVideoWithSoftwareDecoder() {
        if (currentVideoUrl == null) {
            showError("无法重新加载：视频URL为空");
            return;
        }
        
        Log.d(TAG, "🔧 Reloading video with software decoder...");
        
        // 停止当前播放
        if (playerView != null) {
            playerView.release();
        }
        
        // 重置播放器状态
        isPlayerReady = false;
        
        // 重新初始化播放器（会使用 forceUseSoftwareDecoder 标志）
        initializePlayer();
        
        // 重新播放
        playMedia(currentVideoUrl);
    }
    
    /**
     * ⚙️ 应用画面比例
     */
    private void applyAspectRatio(int ratio) {
        if (playerView == null) return;
        
        // GSYVideoPlayer 使用不同的缩放模式
        switch (ratio) {
            case 0: // 默认
                playerView.setShowFullAnimation(false);
                break;
            case 1: // 16:9
                playerView.setShowFullAnimation(false);
                break;
            case 2: // 4:3
                playerView.setShowFullAnimation(false);
                break;
            case 3: // 填充
                playerView.setShowFullAnimation(false);
                break;
        }
    }
    
    /**
     * ⚙️ 显示音频轨道对话框
     * 注意：GSYVideoPlayer + IJKPlayer 不支持音频轨道选择
     */
    private void showAudioTrackDialog() {
        Toast.makeText(this, "当前播放器不支持音频轨道选择", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * ⚙️ 选择音频轨道
     * 注意：GSYVideoPlayer + IJKPlayer 不支持音频轨道选择
     */
    private void selectAudioTrack(int trackIndex) {
        Log.d(TAG, "GSYVideoPlayer + IJKPlayer 不支持音频轨道选择");
    }
}
