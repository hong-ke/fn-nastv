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

import androidx.appcompat.app.AppCompatActivity;

import com.shuyu.gsyvideoplayer.builder.GSYVideoOptionBuilder;
import com.shuyu.gsyvideoplayer.listener.GSYVideoProgressListener;
import com.shuyu.gsyvideoplayer.listener.VideoAllCallBack;
import com.shuyu.gsyvideoplayer.utils.GSYVideoType;

import com.mynas.nastv.R;
import com.mynas.nastv.feature.danmaku.api.IDanmuController;
import com.mynas.nastv.feature.danmaku.logic.DanmuControllerImpl;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.model.PlayStartInfo;
import com.mynas.nastv.player.AudioTrackManager;
import com.mynas.nastv.player.EpisodeController;
import com.mynas.nastv.player.IjkPlayerKernel;
import com.mynas.nastv.player.PlayerKernel;
import com.mynas.nastv.player.PlayerMenuController;
import com.mynas.nastv.player.PlayerSettingsHelper;
import com.mynas.nastv.player.ProgressRecorder;
import com.mynas.nastv.player.StreamListManager;
import com.mynas.nastv.player.SubtitleManager;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.ToastUtils;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * VideoPlayerActivity
 * 视频播放器 Activity
 * 
 * 重构说明：
 * - SubtitleManager: 字幕管理（加载、解析、显示）
 * - PlayerMenuController: 菜单控制（底部菜单、进度条）
 * - PlayerSettingsHelper: 播放器设置（解码器、画面比例等）
 * - EpisodeController: 剧集管理（选集、下一集）
 */
public class VideoPlayerActivity extends AppCompatActivity {
    private static final String TAG = "VideoPlayerActivity";

    // UI
    private com.mynas.nastv.player.NoLoadingGSYVideoPlayer playerView;
    private ImageView posterImageView;
    private LinearLayout topInfoContainer;
    private TextView titleText;
    private TextView infoText;
    private View loadingLayout;
    private View errorLayout;
    private TextView errorText;
    private FrameLayout danmuContainer;
    private TextView subtitleTextView;

    private IDanmuController danmuController;

    // 辅助类
    private SubtitleManager subtitleManager;
    private PlayerMenuController menuController;
    private PlayerSettingsHelper settingsHelper;
    private EpisodeController episodeController;
    private AudioTrackManager audioTrackManager;
    private StreamListManager streamListManager;
    
    // 播放器内核
    private PlayerKernel currentPlayerKernel;
    private IjkPlayerKernel ijkPlayerKernel;

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
    private String parentGuid;
    private String tvTitle;
    private String seasonGuid;

    // 恢复播放位置
    private long resumePositionSeconds = 0;

    // 跳过片头标志
    private boolean hasSkippedIntro = false;

    // 字幕相关（由 SubtitleManager 管理，保留兼容变量）
    private List<com.mynas.nastv.model.StreamListResponse.SubtitleStream> subtitleStreams;
    private int currentSubtitleIndex = -1;
    private String currentVideoUrl;
    private boolean isDirectLinkMode = false;

    // 音频流相关（由 AudioTrackManager 管理，保留兼容变量）
    private List<com.mynas.nastv.model.StreamListResponse.AudioStream> audioStreams;
    private int currentAudioIndex = -1;

    // 快进/快退相关
    private long seekAccumulatedTime = 0;  // 累积的快进/快退时间（毫秒）
    private long seekBasePosition = -1;    // 开始快进/快退时的基准位置
    private Handler seekHandler = new Handler(Looper.getMainLooper());
    private Runnable seekRunnable;
    private static final long SEEK_DELAY = 500;  // 松开按键后延迟执行seek的时间

    // Manager
    private MediaManager mediaManager;
    private ProgressRecorder progressRecorder;

    private boolean isPlayerReady = false;

    // 解码器（由 PlayerSettingsHelper 管理，保留兼容变量）
    private boolean forceUseSoftwareDecoder = false;

    // ExoPlayer 内核（用于内嵌字幕）
    private com.mynas.nastv.player.ExoPlayerKernel exoPlayerKernel;
    private boolean useExoPlayerForSubtitle = false;
    private android.view.TextureView exoTextureView;

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

        // 恢复播放位置（秒）
        resumePositionSeconds = intent.getLongExtra("resume_position", 0);
        if (resumePositionSeconds <= 0) {
            resumePositionSeconds = intent.getLongExtra("ts", 0);
        }

        // 电影弹幕修复：电影没有季/集概念，但弹幕API需要season=1, episode=1
        // 参考Web端请求：电影使用 season_number=1, episode_number=1
        if (episodeNumber <= 0) episodeNumber = 1;
        if (seasonNumber <= 0) seasonNumber = 1;

        // 如果没有parentGuid，电影使用自身guid作为parent_guid
        if (parentGuid == null || parentGuid.isEmpty()) {
            parentGuid = episodeGuid != null ? episodeGuid : mediaGuid;
        }

        if (mediaTitle == null) mediaTitle = "Unknown";

        mediaManager = new MediaManager(this);

        // 初始化播放进度记录器
        progressRecorder = new ProgressRecorder();
        
        // 初始化音频轨道管理器
        audioTrackManager = new AudioTrackManager(this);
        audioTrackManager.setCallback(new AudioTrackManager.AudioTrackCallback() {
            @Override
            public void onAudioTracksLoaded(List<com.mynas.nastv.model.StreamListResponse.AudioStream> streams) {
                audioStreams = streams;
                // 自动选择音频轨道
                if (currentPlayerKernel != null) {
                    audioTrackManager.autoSelectAudioTrack(currentPlayerKernel);
                }
            }
            
            @Override
            public void onAudioTrackChanged(int index) {
                currentAudioIndex = index;
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Audio track error: " + error);
            }
        });
        
        // 初始化流列表管理器
        streamListManager = new StreamListManager(this);
        streamListManager.setCallback(new StreamListManager.StreamListCallback() {
            @Override
            public void onStreamListLoaded(com.mynas.nastv.model.StreamListResponse.StreamData data) {
                // 处理音频流
                if (data != null && data.getAudioStreams() != null) {
                    audioTrackManager.loadAudioStreams(episodeGuid != null ? episodeGuid : mediaGuid);
                }
                
                // 处理字幕流
                if (data != null && data.getSubtitleStreams() != null) {
                    subtitleManager.setSubtitleStreams(data.getSubtitleStreams());
                    handleSubtitleStreams(data.getSubtitleStreams());
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Stream list error: " + error);
            }
        });

        // 初始化剧集控制器
        episodeController = new EpisodeController(this);
        episodeController.setSeasonGuid(seasonGuid);
        episodeController.setCurrentEpisodeNumber(episodeNumber);
        episodeController.setCallback(new EpisodeController.EpisodeCallback() {
            @Override
            public void onEpisodeLoaded(PlayStartInfo playInfo, EpisodeListResponse.Episode episode) {
                handleEpisodeLoaded(playInfo, episode);
            }
            @Override
            public void onEpisodeLoadError(String error) {
                ToastUtils.show(VideoPlayerActivity.this, "加载失败: " + error);
            }
            @Override
            public void onEpisodeListLoaded(List<EpisodeListResponse.Episode> episodes) {
                Log.d(TAG, "Episode list loaded: " + episodes.size());
            }
            @Override
            public void onNoMoreEpisodes() {
                ToastUtils.show(VideoPlayerActivity.this, "暂无下一集");
            }
            @Override
            public void onLastEpisodeFinished() {
                finish();
            }
        });

        // 加载剧集列表
        if (seasonGuid != null && !seasonGuid.isEmpty()) {
            episodeController.loadEpisodeList();
        }

        Log.d(TAG, "Data Initialized: " + mediaTitle + ", URL: " + videoUrl);
        Log.d(TAG, "Danmaku Params: title=" + tvTitle + ", s" + seasonNumber + "e" + episodeNumber + ", guid=" + episodeGuid + ", parentGuid=" + parentGuid);
    }

    /**
     * 处理剧集加载完成
     */
    private void handleEpisodeLoaded(PlayStartInfo playInfo, EpisodeListResponse.Episode episode) {
        runOnUiThread(() -> {
            Log.d(TAG, "Episode loaded: " + episode.getEpisodeNumber());

            // 记录当前是否使用 ExoPlayer
            final boolean wasUsingExoPlayer = useExoPlayerForSubtitle;

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

            // 释放 ExoPlayer
            if (wasUsingExoPlayer) {
                releaseExoPlayerKernel();
                useExoPlayerForSubtitle = false;
                if (exoTextureView != null) {
                    exoTextureView.setVisibility(View.GONE);
                }
            }

            // 停止 GSYVideoPlayer
            if (playerView != null) {
                playerView.release();
                isPlayerReady = false;
            }

            // 清空弹幕缓存
            if (danmuController != null) {
                danmuController.clearDanmaku();
            }

            // 重置状态
            hasSkippedIntro = false;
            currentSubtitleIndex = -1;
            subtitleStreams = null;

            // 重新初始化播放器
            if (playerView != null) {
                playerView.setVisibility(View.VISIBLE);
            }
            initializePlayer();

            // 播放新视频
            showLoading("加载中...");
            videoUrl = playInfo.getPlayUrl();
            playMedia(videoUrl);

            hideSettingsMenu();
        });
    }

    private void initializeViews() {
        playerView = findViewById(R.id.player_view);
        posterImageView = findViewById(R.id.poster_image);
        topInfoContainer = findViewById(R.id.top_info_container);
        titleText = findViewById(R.id.title_text);
        infoText = findViewById(R.id.info_text);
        loadingLayout = findViewById(R.id.loading_layout);
        errorLayout = findViewById(R.id.error_layout);
        errorText = findViewById(R.id.error_text);
        danmuContainer = findViewById(R.id.danmu_container);
        subtitleTextView = findViewById(R.id.subtitle_text_view);

        // 为 GSYVideoPlayer 应用饱和度增强滤镜
        if (playerView != null) {
            applySaturationFilter(playerView, 1.20f); // 增加20%饱和度，改善颜色偏白和饱和度不足问题
        }

        // 初始化辅助类
        initializeHelpers();

        // 关键修复：立即隐藏海报背景，避免显示灰色山景默认图
        if (posterImageView != null) {
            posterImageView.setVisibility(View.GONE);
        }

        // 更新标题显示
        updateTitleDisplay();

        // 添加点击屏幕呼出/隐藏菜单
        playerView.setOnClickListener(v -> {
            if (menuController != null && menuController.isMenuVisible()) {
                menuController.hideMenu();
            } else if (menuController != null) {
                menuController.showMenu();
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

    /**
     * 初始化辅助类
     */
    private void initializeHelpers() {
        // 字幕管理器
        subtitleManager = new SubtitleManager(this, subtitleTextView);
        subtitleManager.setPositionProvider(() -> {
            if (currentPlayerKernel != null) {
                return currentPlayerKernel.getCurrentPosition();
            }
            return 0;
        });
        subtitleManager.setCallback(new SubtitleManager.SubtitleCallback() {
            @Override
            public void onSubtitleLoaded(String title) {
                ToastUtils.show(VideoPlayerActivity.this, "字幕已加载: " + title);
            }
            @Override
            public void onSubtitleError(String error) {
                ToastUtils.show(VideoPlayerActivity.this, error);
            }
            @Override
            public void onInternalSubtitleDetected() {
                switchToExoPlayerForInternalSubtitle();
            }
        });

        // 菜单控制器
        menuController = new PlayerMenuController(this, getWindow().getDecorView());
        menuController.setPositionProvider(new PlayerMenuController.PositionProvider() {
            @Override
            public long getCurrentPosition() {
                if (currentPlayerKernel != null) {
                    return currentPlayerKernel.getCurrentPosition();
                }
                return 0;
            }
            @Override
            public long getDuration() {
                if (currentPlayerKernel != null) {
                    return currentPlayerKernel.getDuration();
                }
                return 0;
            }
        });
        menuController.setCallback(new PlayerMenuController.MenuCallback() {
            @Override
            public void onSpeedChanged(float speed) {
                if (currentPlayerKernel != null) {
                    currentPlayerKernel.setSpeed(speed);
                }
            }
            @Override
            public void onEpisodeSelected(EpisodeListResponse.Episode episode) {
                if (episodeController != null) {
                    episodeController.playEpisode(episode);
                }
            }
            @Override
            public void onNextEpisode() {
                if (episodeController != null) {
                    episodeController.playNextEpisode();
                }
            }
            @Override
            public void onSubtitleMenuRequested() {
                showSubtitleMenu();
            }
            @Override
            public void onAudioMenuRequested() {
                showAudioMenu();
            }
            @Override
            public void onDanmakuToggled(boolean enabled) {
                if (danmuController != null) {
                    if (enabled) {
                        danmuController.startPlayback();
                    } else {
                        danmuController.pausePlayback();
                    }
                }
                if (danmuContainer != null) {
                    danmuContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);
                }
            }
            @Override
            public void onSeekTo(long position) {
                if (currentPlayerKernel != null) {
                    currentPlayerKernel.seekTo(position);
                }
            }
            @Override
            public void onSettingsRequested() {
                if (settingsHelper != null) {
                    settingsHelper.showSettingsDialog();
                }
            }
        });
        menuController.setEpisodeClickListener(v -> {
            if (episodeController != null) {
                episodeController.showEpisodeMenu();
            }
        });

        // 设置辅助类
        settingsHelper = new PlayerSettingsHelper(this);
        settingsHelper.setCallback(new PlayerSettingsHelper.SettingsCallback() {
            @Override
            public void onDecoderChanged(boolean useSoftware) {
                forceUseSoftwareDecoder = useSoftware;
            }
            @Override
            public void onAspectRatioChanged(int ratio) {
                applyAspectRatio(ratio);
            }
            @Override
            public void onReloadVideoRequested() {
                reloadVideo();
            }
        });
    }

    // 记录是否已显示软解提示（避免重复提示）
    private boolean hasShownSoftwareDecoderToast = false;
    
    /**
     * 处理字幕流列表
     */
    private void handleSubtitleStreams(List<com.mynas.nastv.model.StreamListResponse.SubtitleStream> streams) {
        if (streams == null || streams.isEmpty()) {
            return;
        }
        
        // 查找外挂字幕和内嵌字幕
        int firstExternalIndex = -1;
        int firstInternalIndex = -1;
        String internalSubtitleFormat = null;
        
        for (int i = 0; i < streams.size(); i++) {
            com.mynas.nastv.model.StreamListResponse.SubtitleStream sub = streams.get(i);
            if (sub.isExternal() && firstExternalIndex == -1) {
                firstExternalIndex = i;
            }
            if (!sub.isExternal() && firstInternalIndex == -1) {
                firstInternalIndex = i;
                internalSubtitleFormat = sub.getFormat();
                if (internalSubtitleFormat == null || internalSubtitleFormat.isEmpty()) {
                    internalSubtitleFormat = sub.getCodecName();
                }
            }
        }
        
        // 优先使用外挂字幕
        if (firstExternalIndex >= 0) {
            subtitleManager.loadSubtitle(firstExternalIndex);
        } else if (firstInternalIndex >= 0) {
            // 只有内嵌字幕，根据格式决定
            final String format = internalSubtitleFormat != null ? internalSubtitleFormat.toLowerCase() : "";
            boolean ijkSupportsFormat = format.equals("ass") || format.equals("ssa");
            
            if (ijkSupportsFormat) {
                // ASS/SSA 格式，IJKPlayer 可以处理
                Log.d(TAG, "Internal subtitle format supported by IJKPlayer: " + format);
            } else {
                // subrip/srt 等格式，切换到 ExoPlayer
                Log.d(TAG, "Internal subtitle format NOT supported by IJKPlayer: " + format + ", switching to ExoPlayer");
                switchToExoPlayerForInternalSubtitle();
            }
        }
    }

    private void initializePlayer() {
        try {
            // 初始化 IJKPlayer 内核
            Log.d(TAG, "Initializing IJKPlayer kernel");
            
            if (ijkPlayerKernel == null) {
                ijkPlayerKernel = new IjkPlayerKernel(this, playerView);
                ijkPlayerKernel.setPlayerCallback(new PlayerKernel.PlayerCallback() {
                    @Override
                    public void onPrepared() {
                        Log.d(TAG, "IJKPlayer onPrepared");
                        isPlayerReady = true;
                        showPlayer();
                        hideBufferingIndicator();
                        
                        if (playerView != null) {
                            playerView.setVisibility(View.VISIBLE);
                            playerView.getBackButton().setVisibility(View.GONE);
                            playerView.getFullscreenButton().setVisibility(View.GONE);
                            playerView.getStartButton().setVisibility(View.GONE);
                        }
                        
                        // 启动弹幕
                        if (danmuController != null) {
                            danmuController.startPlayback();
                            startPositionUpdate();
                        }
                        
                        // 启动播放进度记录
                        if (progressRecorder != null && !progressRecorder.isRecording()) {
                            String itemGuid = episodeGuid != null ? episodeGuid : mediaGuid;
                            progressRecorder.startRecording(itemGuid, mediaGuid);
                            progressRecorder.setStreamGuids(videoGuid, audioGuid, null);
                        }
                        
                        // 恢复播放位置
                        if (resumePositionSeconds > 0) {
                            long resumePositionMs = resumePositionSeconds * 1000;
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (ijkPlayerKernel != null) {
                                    ijkPlayerKernel.seekTo(resumePositionMs);
                                }
                            }, 500);
                            resumePositionSeconds = 0;
                        } else {
                            // 跳过片头
                            int skipIntro = SharedPreferencesManager.getSkipIntro();
                            if (skipIntro > 0 && !hasSkippedIntro) {
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    if (ijkPlayerKernel != null) {
                                        ijkPlayerKernel.seekTo(skipIntro * 1000L);
                                    }
                                }, 500);
                                hasSkippedIntro = true;
                            }
                        }
                        
                        // 延迟自动选择音频轨道
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (ijkPlayerKernel != null && audioTrackManager != null) {
                                audioTrackManager.autoSelectAudioTrack(ijkPlayerKernel);
                            }
                        }, 1000);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "IJKPlayer error: " + error);
                        // 解码器自动降级
                        if (!forceUseSoftwareDecoder && !SharedPreferencesManager.useSoftwareDecoder()) {
                            forceUseSoftwareDecoder = true;
                            Log.w(TAG, "硬解失败，自动切换到软解");
                            runOnUiThread(() -> {
                                ToastUtils.show(VideoPlayerActivity.this, "硬解失败，自动切换软解");
                                if (ijkPlayerKernel != null) {
                                    ijkPlayerKernel.setForceUseSoftwareDecoder(true);
                                }
                                if (currentVideoUrl != null) {
                                    playMedia(currentVideoUrl);
                                }
                            });
                            return;
                        }
                        showError("播放错误: " + error);
                    }
                    
                    @Override
                    public void onCompletion() {
                        Log.d(TAG, "IJKPlayer onCompletion");
                        if (SharedPreferencesManager.isAutoPlayNext() && episodeController != null && episodeController.hasEpisodeList()) {
                            episodeController.playNextEpisodeAuto();
                        } else {
                            finish();
                        }
                    }
                    
                    @Override
                    public void onBuffering(boolean isBuffering) {
                        if (isBuffering && isPlayerReady) {
                            showBufferingIndicator();
                        } else {
                            hideBufferingIndicator();
                        }
                    }
                    
                    @Override
                    public void onVideoSizeChanged(int width, int height) {
                        Log.d(TAG, "IJKPlayer video size: " + width + "x" + height);
                    }
                });
                
                ijkPlayerKernel.setSubtitleCallback(new PlayerKernel.SubtitleCallback() {
                    @Override
                    public void onSubtitleChanged(List<androidx.media3.common.text.Cue> cues) {
                        // IJKPlayer 不使用这个回调
                    }
                    
                    @Override
                    public void onTimedText(String text) {
                        runOnUiThread(() -> {
                            if (subtitleTextView != null) {
                                if (text != null && !text.isEmpty()) {
                                    subtitleTextView.setText(text);
                                    subtitleTextView.setVisibility(View.VISIBLE);
                                } else {
                                    subtitleTextView.setVisibility(View.GONE);
                                }
                            }
                        });
                    }
                });
            }
            
            currentPlayerKernel = ijkPlayerKernel;
            
            // 初始化播放器
            Map<String, String> headers = new HashMap<>();
            ijkPlayerKernel.init(headers);
            
            // 隐藏 GSYVideoPlayer 内置的 loading 视图
            hideGSYBuiltInLoading();

        } catch (Exception e) {
            Log.e(TAG, "GSYVideoPlayer Init Failed", e);
            showError("Player Init Failed");
        }
    }

    /**
     * 隐藏 GSYVideoPlayer 内置的 loading 视图
     * 遍历所有可能的 loading 相关视图并隐藏
     */
    private void hideGSYBuiltInLoading() {
        if (playerView == null) return;

        try {
            // 尝试通过 ID 隐藏
            int[] loadingIds = {
                    com.shuyu.gsyvideoplayer.R.id.loading,
                    com.shuyu.gsyvideoplayer.R.id.thumb,
                    com.shuyu.gsyvideoplayer.R.id.start
            };

            for (int id : loadingIds) {
                try {
                    View view = playerView.findViewById(id);
                    if (view != null) {
                        view.setVisibility(View.GONE);
                        Log.d(TAG, "隐藏 GSYVideoPlayer 视图 ID: " + id);
                    }
                } catch (Exception e) {
                    // 忽略
                }
            }

            // 遍历子视图，隐藏所有 ProgressBar 和可能的 loading ImageView
            hideLoadingViewsRecursively(playerView);

        } catch (Exception e) {
            Log.w(TAG, "隐藏 GSYVideoPlayer loading 失败", e);
        }
    }

    /**
     * 递归隐藏所有 loading 相关视图（ProgressBar 和 ImageView）
     */
    private void hideLoadingViewsRecursively(android.view.ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            // 隐藏 ProgressBar
            if (child instanceof android.widget.ProgressBar) {
                // 排除我们自定义的进度条
                if (child.getId() != R.id.buffering_indicator &&
                        child.getId() != R.id.seek_progress_bar &&
                        child.getId() != R.id.buffer_progressbar &&
                        child.getId() != R.id.progress_seekbar) {
                    child.setVisibility(View.GONE);
                    Log.d(TAG, "隐藏 ProgressBar: " + child);
                }
            }
            // 隐藏 GSYVideoPlayer 的 loading ImageView（通常在播放器中心）
            else if (child instanceof ImageView) {
                // 排除我们自定义的 ImageView
                if (child.getId() != R.id.poster_image &&
                        child.getId() != R.id.center_play_icon) {
                    // 检查是否在播放器中心区域（可能是 loading 图标）
                    child.setVisibility(View.GONE);
                    Log.d(TAG, "隐藏 ImageView: " + child + " id=" + child.getId());
                }
            }
            // 递归处理子 ViewGroup
            else if (child instanceof android.view.ViewGroup) {
                hideLoadingViewsRecursively((android.view.ViewGroup) child);
            }
        }
    }

    // 缓冲指示器 - 播放中卡顿时显示
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
                ToastUtils.show(this, "缓冲中...");
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

        // 保持 URL 原样，不修改
        String playUrl = url;

        // 保存当前视频URL
        currentVideoUrl = url;

        try {
            // 为直连 URL 启用缓存和多线程加速
            // 判断是否为直连 URL（包含 direct_link_quality_index 或外部云存储 URL）
            boolean isDirectLink = url.contains("direct_link_quality_index") ||
                    (url.startsWith("https://") && !url.contains("192.168.") && !url.contains("localhost"));

            // 为直连 URL 启用缓存
            java.io.File cacheDir = null;
            if (isDirectLink) {
                // 直连 URL：启用缓存并设置缓存路径
                cacheDir = new java.io.File(getCacheDir(), "gsy_video_cache");
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
                Log.d(TAG, "Direct link detected, enabling cache at: " + cacheDir.getAbsolutePath());
                Log.d(TAG, "Cache directory exists: " + cacheDir.exists() + ", writable: " + cacheDir.canWrite());
            }

            // 使用播放器内核播放
            Map<String, String> headers = createHeadersForUrl(url);
            
            if (currentPlayerKernel == null) {
                Log.e(TAG, "Player kernel not initialized");
                showError("播放器未初始化");
                return;
            }
            
            if (isDirectLink && cacheDir != null) {
                // 使用代理缓存播放
                currentPlayerKernel.playWithProxyCache(playUrl, headers, cacheDir);
            } else {
                // 直接播放
                currentPlayerKernel.play(playUrl);
            }
            
            Log.d(TAG, "Playing video: URL=" + playUrl.substring(0, Math.min(100, playUrl.length())) + "...");

            // Load Danmaku - 使用 title + season + episode + guid 获取弹幕
            if (danmuController != null) {
                if (tvTitle != null && !tvTitle.isEmpty()) {
                    Log.e(TAG, "Loading danmaku with title=" + tvTitle + ", s" + seasonNumber + "e" + episodeNumber);
                    danmuController.loadDanmaku(tvTitle, episodeNumber, seasonNumber, episodeGuid, parentGuid);
                } else {
                    Log.w(TAG, "No valid title for danmaku, skipping. title=" + tvTitle);
                }
            }

            // 加载流列表（音频流和字幕流）
            String itemGuid = episodeGuid != null ? episodeGuid : mediaGuid;
            if (itemGuid != null && !itemGuid.isEmpty()) {
                streamListManager.loadStreamList(itemGuid);
            }

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

        // 关键修复：对于包含 /range/ 的 URL，确保使用原始 URL 生成签名
        // 签名验证基于完整的 URL 路径，包括 /range/ 部分
        if (url.contains("/range/")) {
            Log.d(TAG, "URL contains /range/ path, will use original URL for signature generation");
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

                // 详细日志：打印认证信息（隐藏敏感内容）
                Log.d(TAG, "[CURL TEST] Token length: " + authToken.length() + ", first 10 chars: " +
                        (authToken.length() > 10 ? authToken.substring(0, 10) + "..." : authToken));

                try {
                    String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature("GET", url, "", null);
                    if (signature != null) {
                        headers.put("authx", signature);
                        // 详细日志：打印签名信息
                        Log.d(TAG, "[CURL TEST] Signature length: " + signature.length() + ", first 10 chars: " +
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

        // 详细日志：打印所有 headers（用于 curl 测试）
        Log.d(TAG, "[CURL TEST] ===== Headers for URL =====");
        Log.d(TAG, "[CURL TEST] URL: " + url);
        Log.d(TAG, "[CURL TEST] curl -v -X GET \\");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            // 隐藏敏感信息的部分内容
            if (key.equals("Cookie") || key.equals("Authorization") || key.equals("authx")) {
                String maskedValue = value.length() > 20 ? value.substring(0, 20) + "..." : value;
                Log.d(TAG, "[CURL TEST]   -H \"" + key + ": " + maskedValue + "\" \\");
            } else {
                Log.d(TAG, "[CURL TEST]   -H \"" + key + ": " + value + "\" \\");
            }
        }
        Log.d(TAG, "[CURL TEST]   \"" + url + "\"");
        Log.d(TAG, "[CURL TEST] =============================");

        return headers;
    }


    /**
     * 加载指定字幕（委托给 SubtitleManager）
     */
    private void loadSubtitle(int index) {
        if (subtitleManager != null) {
            subtitleManager.loadSubtitle(index);
        }
    }

    /**
     * 启用内嵌字幕（委托给 SubtitleManager）
     */
    private void enableInternalSubtitle(int index) {
        if (subtitleManager != null) {
            subtitleManager.loadSubtitle(index);
        }
    }

    /**
     * 切换到 ExoPlayer 内核播放内嵌字幕
     * 当检测到只有内嵌字幕时调用此方法
     */
    private void switchToExoPlayerForInternalSubtitle() {
        Log.d(TAG, "切换到 ExoPlayer 内核以支持内嵌字幕");

        if (currentVideoUrl == null || currentVideoUrl.isEmpty()) {
            Log.e(TAG, "无法切换：视频 URL 为空");
            return;
        }

        // 保存当前播放位置（优先使用 GSYVideoPlayer 的位置，否则使用历史恢复位置）
        long currentPosition = 0;
        if (playerView != null) {
            currentPosition = playerView.getCurrentPositionWhenPlaying();
        }
        // 如果 GSYVideoPlayer 还没开始播放，使用历史恢复位置
        if (currentPosition <= 0 && resumePositionSeconds > 0) {
            currentPosition = resumePositionSeconds * 1000;
            Log.d(TAG, "使用历史恢复位置: " + resumePositionSeconds + "s");
        }
        final long savedPosition = currentPosition;

        // 停止 GSYVideoPlayer
        if (playerView != null) {
            playerView.release();
            playerView.setVisibility(View.GONE);
        }

        // 标记使用 ExoPlayer
        useExoPlayerForSubtitle = true;

        // 创建 ExoPlayer 的 TextureView
        if (exoTextureView == null) {
            exoTextureView = new android.view.TextureView(this);
            
            // 应用饱和度增强滤镜 - 增加色彩饱和度
            applySaturationFilter(exoTextureView, 1.20f); // 1.20 = 增加20%饱和度，改善颜色偏白和饱和度不足问题
            
            // 添加到根布局（在 playerView 的位置）
            android.view.ViewGroup rootView = (android.view.ViewGroup) findViewById(android.R.id.content);
            if (rootView != null && rootView.getChildCount() > 0) {
                android.view.ViewGroup mainLayout = (android.view.ViewGroup) rootView.getChildAt(0);
                // 在 playerView 之后添加（索引 1，因为 posterImageView 是索引 0）
                android.widget.RelativeLayout.LayoutParams params = new android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                        android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
                );
                mainLayout.addView(exoTextureView, 1, params);
                Log.d(TAG, "ExoPlayer TextureView 已添加到主布局（已应用饱和度增强）");
            }
        }
        exoTextureView.setVisibility(View.VISIBLE);

        // 初始化 ExoPlayer 内核
        exoPlayerKernel = new com.mynas.nastv.player.ExoPlayerKernel(this);

        // 设置字幕回调
        exoPlayerKernel.setSubtitleCallback(cues -> {
            runOnUiThread(() -> {
                if (subtitleTextView != null) {
                    if (cues != null && !cues.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (androidx.media3.common.text.Cue cue : cues) {
                            if (cue.text != null) {
                                String cueText = cue.text.toString();
                                // 过滤 ASS 绘图命令 (drawing commands)
                                // 绘图命令格式: m x y b x1 y1 x2 y2... (move 和 bezier curve)
                                if (isAssDrawingCommand(cueText)) {
                                    continue; // 跳过绘图命令
                                }
                                if (sb.length() > 0) sb.append("\n");
                                sb.append(cueText);
                            }
                        }
                        String text = sb.toString();
                        if (!text.isEmpty()) {
                            subtitleTextView.setText(text);
                            subtitleTextView.setVisibility(View.VISIBLE);
                        } else {
                            subtitleTextView.setVisibility(View.GONE);
                        }
                    } else {
                        subtitleTextView.setVisibility(View.GONE);
                    }
                }
            });
        });

        // 设置播放器回调
        exoPlayerKernel.setPlayerCallback(new com.mynas.nastv.player.ExoPlayerKernel.PlayerCallback() {
            @Override
            public void onPrepared() {
                Log.d(TAG, "ExoPlayer 准备完成");
                runOnUiThread(() -> {
                    isPlayerReady = true;
                    showPlayer();
                    hideBufferingIndicator();

                    // 恢复播放位置
                    if (savedPosition > 0) {
                        exoPlayerKernel.seekTo(savedPosition);
                        Log.d(TAG, "恢复播放位置: " + (savedPosition / 1000) + "s");
                    }

                    // 启动弹幕
                    if (danmuController != null) {
                        danmuController.startPlayback();
                        startPositionUpdateForExo();
                    }

                    ToastUtils.show(VideoPlayerActivity.this, "已切换到 ExoPlayer（支持内嵌字幕）");
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "ExoPlayer 错误: " + error);
                runOnUiThread(() -> showError("播放错误: " + error));
            }

            @Override
            public void onCompletion() {
                Log.d(TAG, "ExoPlayer 播放完成");
                runOnUiThread(() -> {
                    if (SharedPreferencesManager.isAutoPlayNext() && episodeController != null && episodeController.hasEpisodeList()) {
                        episodeController.playNextEpisodeAuto();
                    } else {
                        finish();
                    }
                });
            }

            @Override
            public void onBuffering(boolean isBuffering) {
                runOnUiThread(() -> {
                    if (isBuffering && isPlayerReady) {
                        showBufferingIndicator();
                    } else {
                        hideBufferingIndicator();
                    }
                });
            }

            @Override
            public void onVideoSizeChanged(int width, int height) {
                Log.d(TAG, "ExoPlayer 视频尺寸: " + width + "x" + height);
                // 根据视频尺寸调整 TextureView 的宽高比
                runOnUiThread(() -> adjustTextureViewAspectRatio(width, height));
            }
        });

        // 创建请求头
        Map<String, String> headers = createHeadersForUrl(currentVideoUrl);

        // 获取缓存目录
        File cacheDir = new File(getCacheDir(), "okhttp_video_cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        // 初始化并播放
        exoPlayerKernel.init(headers);

        // 设置 TextureView 的 SurfaceTextureListener
        exoTextureView.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "ExoPlayer Surface 可用");
                android.view.Surface videoSurface = new android.view.Surface(surface);
                exoPlayerKernel.setSurface(videoSurface);
                // 使用代理缓存播放（与 IJKPlayer 相同的缓存机制）
                exoPlayerKernel.playWithProxyCache(currentVideoUrl, headers, cacheDir);
            }

            @Override
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture surface) {
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture surface) {
            }
        });

        // 如果 Surface 已经可用，直接播放
        if (exoTextureView.isAvailable()) {
            android.view.Surface videoSurface = new android.view.Surface(exoTextureView.getSurfaceTexture());
            exoPlayerKernel.setSurface(videoSurface);
            // 使用代理缓存播放（与 IJKPlayer 相同的缓存机制）
            exoPlayerKernel.playWithProxyCache(currentVideoUrl, headers, cacheDir);
        }

        Log.d(TAG, "ExoPlayer 内核切换完成");
    }

    /**
     * ExoPlayer 的位置更新（用于弹幕同步）
     */
    private void startPositionUpdateForExo() {
        positionHandler.post(new Runnable() {
            @Override
            public void run() {
                if (exoPlayerKernel != null && useExoPlayerForSubtitle) {
                    long currentPosition = exoPlayerKernel.getCurrentPosition();
                    long duration = exoPlayerKernel.getDuration();

                    // 更新弹幕位置
                    if (danmuController != null) {
                        danmuController.updatePlaybackPosition(currentPosition);
                    }

                    // 更新播放进度记录器
                    if (progressRecorder != null && duration > 0) {
                        progressRecorder.updateProgress(currentPosition / 1000, duration / 1000);
                    }

                    positionHandler.postDelayed(this, 100);
                }
            }
        });
    }

    /**
     * 释放 ExoPlayer 内核
     */
    private void releaseExoPlayerKernel() {
        if (exoPlayerKernel != null) {
            exoPlayerKernel.release();
            exoPlayerKernel = null;
        }
        if (exoTextureView != null) {
            exoTextureView.setVisibility(View.GONE);
        }
        useExoPlayerForSubtitle = false;
    }


    /**
     * 检查并显示解码器切换提示（备用方案）
     */
    private void checkAndShowDecoderToast() {
        if (hasShownSoftwareDecoderToast) return;

        boolean configuredHardware = !SharedPreferencesManager.useSoftwareDecoder() && !forceUseSoftwareDecoder;
        boolean mediaCodecEnabled = GSYVideoType.isMediaCodec();

        Log.d(TAG, "检测解码器状态: configuredHardware=" + configuredHardware + ", mediaCodecEnabled=" + mediaCodecEnabled);
    }

    /**
     * 获取 IJKPlayer 配置选项
     */
    private java.util.List<com.shuyu.gsyvideoplayer.model.VideoOptionModel> getIjkOptions(boolean useSoftware) {
        java.util.List<com.shuyu.gsyvideoplayer.model.VideoOptionModel> options = new java.util.ArrayList<>();

        // 播放器选项
        int playerCategory = tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_PLAYER;
        int formatCategory = tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_FORMAT;
        int codecCategory = tv.danmaku.ijk.media.player.IjkMediaPlayer.OPT_CATEGORY_CODEC;

        if (!useSoftware) {
            // 硬解模式配置
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec", 1));
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec-auto-rotate", 1));
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec-handle-resolution-change", 1));
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec-hevc", 1));
            Log.d(TAG, "IJKPlayer: 启用硬解 + HEVC 硬解");
        } else {
            // 软解模式配置
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec", 0));
            options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "mediacodec-hevc", 0));
            Log.d(TAG, "IJKPlayer: 使用软解");
        }

        // 字幕选项 - 启用内嵌字幕解码
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "subtitle", 1));
        Log.d(TAG, "IJKPlayer: 启用字幕解码");

        // ==================== 画质优化选项 ====================
        
        // 1. 禁用环路滤波跳过 - 提高画质（0=不跳过，48=跳过非关键帧，默认48会降低画质）
        // skip_loop_filter: 0=AVDISCARD_NONE(不跳过), 8=AVDISCARD_DEFAULT, 48=AVDISCARD_NONREF
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(codecCategory, "skip_loop_filter", 0));
        
        // 2. 禁用帧跳过 - 保证画面完整性，减少颗粒感
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(codecCategory, "skip_frame", 0));
        
        // 3. 优化像素格式 - 使用高质量像素格式提升清晰度
        // 硬解模式下，使用最佳像素格式以获得更好的画质
        if (!useSoftware) {
            // 硬解模式：使用硬件解码器的最佳像素格式
            // 不强制指定 overlay-format，让系统选择最佳格式
            // 但可以通过其他参数优化颜色空间转换
        } else {
            // 软解模式：使用高质量像素格式
            // 尝试使用 RGB565 或更高精度格式
        }
        
        // 4. 增加视频缓冲帧数 - 减少丢帧，提高清晰度和流畅度
        // 从 6 增加到 10，提供更多缓冲帧以减少颗粒感
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "video-pictq-size", 10));
        
        // 5. 优化帧率控制 - 减少帧率波动导致的颗粒感
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "fps", 0)); // 0=不限制帧率，使用原始帧率
        
        // ==================== 通用优化选项 ====================
        // 帧丢弃策略：智能丢弃帧，在保持流畅度的同时保证画质
        // framedrop=1 表示在必要时丢弃帧以保持流畅，但不会过度丢弃导致画质下降
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "framedrop", 1));
        
        // 精确跳转
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "enable-accurate-seek", 1));
        
        // 增加缓冲区大小 - 提供更多缓冲以减少卡顿和颗粒感
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "max-buffer-size", 20 * 1024 * 1024)); // 从15MB增加到20MB
        
        // 最小帧数 - 增加预缓冲帧数
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "min-frames", 60)); // 从50增加到60
        
        // 准备完成后自动开始播放
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "start-on-prepared", 1));
        
        // 增加 packet 缓冲 - 减少卡顿
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "packet-buffering", 1));
        
        // 增加最大缓存时长 - 提供更长的缓冲时间
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "max_cached_duration", 5000)); // 从3000增加到5000ms

        // 格式选项 - 增加探测和分析时间以获得更好的画质
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(formatCategory, "probesize", 20 * 1024 * 1024)); // 从10MB增加到20MB
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(formatCategory, "analyzeduration", 10 * 1000 * 1000)); // 从5秒增加到10秒
        
        // 刷新数据包 - 减少延迟
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(formatCategory, "flush_packets", 1));
        
        // 额外优化：禁用低延迟模式以获得更好的画质
        options.add(new com.shuyu.gsyvideoplayer.model.VideoOptionModel(playerCategory, "low_delay", 0));

        Log.d(TAG, "IJKPlayer: 已应用画质优化配置");
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
            // 根据使用的播放器显示对应的视图
            if (useExoPlayerForSubtitle && exoTextureView != null) {
                exoTextureView.setVisibility(View.VISIBLE);
                if (playerView != null) {
                    playerView.setVisibility(View.GONE);
                }
            } else {
                playerView.setVisibility(View.VISIBLE);
            }
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
            if (currentPlayerKernel != null) {
                long currentPosition = currentPlayerKernel.getCurrentPosition();
                long duration = currentPlayerKernel.getDuration();

                if (currentPosition > 0 && duration > 0) {
                    // 更新弹幕位置
                    if (danmuController != null) {
                        danmuController.updatePlaybackPosition(currentPosition);
                        // 调试日志：每 5 秒打印一次弹幕位置更新
                        if (currentPosition % 5000 < 150) {
                            Log.d(TAG, "弹幕位置更新: " + (currentPosition / 1000) + "s");
                        }
                    }

                    // 更新播放进度记录器
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

    /**
     * 立即释放播放器并退出
     * 解决按返回键时播放器没有立即销毁的问题
     */
    private void releasePlayerAndFinish() {
        Log.d(TAG, "releasePlayerAndFinish - 立即释放播放器");

        // 停止位置更新
        stopPositionUpdate();

        // 停止播放进度记录
        if (progressRecorder != null) {
            progressRecorder.stopRecording();
        }

        // 清理图标隐藏任务
        if (hideIconRunnable != null) {
            iconHandler.removeCallbacks(hideIconRunnable);
        }

        // 释放播放器内核
        if (currentPlayerKernel != null) {
            currentPlayerKernel.release();
            currentPlayerKernel = null;
        }
        ijkPlayerKernel = null;
        releaseExoPlayerKernel();

        // 立即释放播放器视图
        if (playerView != null) {
            playerView.release();
        }

        // 强制释放缓存管理器（清理缓存状态）
        try {
            com.mynas.nastv.cache.OkHttpProxyCacheManager.instance().forceRelease();
            Log.d(TAG, "OkHttpProxyCacheManager 已强制释放");
        } catch (Exception e) {
            Log.w(TAG, "释放缓存管理器失败", e);
        }

        // 销毁弹幕
        if (danmuController != null) {
            danmuController.destroy();
        }

        // 退出 Activity
        finish();
    }

    // 缓存由 GSYVideoPlayer + OkHttpProxyCacheManager 自动管理

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPositionUpdate();

        // 释放辅助类
        if (subtitleManager != null) {
            subtitleManager.release();
            subtitleManager = null;
        }
        if (menuController != null) {
            menuController.release();
            menuController = null;
        }

        // 停止播放进度记录
        if (progressRecorder != null) {
            progressRecorder.stopRecording();
            progressRecorder = null;
        }

        // 清理图标隐藏任务
        if (hideIconRunnable != null) {
            iconHandler.removeCallbacks(hideIconRunnable);
        }

        // 释放 ExoPlayer 内核
        releaseExoPlayerKernel();

        if (playerView != null) {
            playerView.release();
            playerView = null;
        }

        // 强制释放缓存管理器
        try {
            com.mynas.nastv.cache.OkHttpProxyCacheManager.instance().forceRelease();
        } catch (Exception e) {
            Log.w(TAG, "释放缓存管理器失败", e);
        }

        if (danmuController != null) {
            danmuController.destroy();
            danmuController = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean menuVisible = menuController != null && menuController.isMenuVisible();
        
        // 返回键处理
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (menuVisible) {
                // 如果菜单可见，按返回键隐藏菜单
                menuController.hideMenu();
                return true;
            } else {
                // 菜单不可见时，立即销毁播放器并退出
                releasePlayerAndFinish();
                return true;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            // 处理播放/暂停
            if (currentPlayerKernel != null) {
                if (currentPlayerKernel.isPlaying()) {
                    currentPlayerKernel.pause();
                    showCenterIcon(false);
                    if (danmuController != null) {
                        danmuController.pausePlayback();
                    }
                } else {
                    currentPlayerKernel.start();
                    showCenterIcon(true);
                    if (danmuController != null) {
                        danmuController.startPlayback();
                    }
                }
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            // 按下键呼出/隐藏设置菜单和进度条
            if (menuController != null) {
                menuController.toggleMenu();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && menuVisible) {
            // 菜单可见时，按上键隐藏菜单
            if (menuController != null) {
                menuController.hideMenu();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !menuVisible) {
            // 左键快退10秒（菜单不可见时）- 累积模式
            accumulateSeek(-10000);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !menuVisible) {
            // 右键快进10秒（菜单不可见时）- 累积模式
            accumulateSeek(10000);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 累积快进/快退时间，延迟执行实际seek
     */
    private void accumulateSeek(long deltaMs) {
        // 取消之前的延迟seek
        if (seekRunnable != null) {
            seekHandler.removeCallbacks(seekRunnable);
        }

        // 如果是新的seek序列，记录基准位置
        if (seekBasePosition < 0) {
            if (currentPlayerKernel != null) {
                seekBasePosition = currentPlayerKernel.getCurrentPosition();
            }
            seekAccumulatedTime = 0;
        }

        // 累积时间
        seekAccumulatedTime += deltaMs;

        // 计算目标位置并显示进度
        long duration = 0;
        if (currentPlayerKernel != null) {
            duration = currentPlayerKernel.getDuration();
        }

        long targetPosition = seekBasePosition + seekAccumulatedTime;
        targetPosition = Math.max(0, Math.min(duration, targetPosition));

        // 显示进度覆盖层（不启动隐藏计时器，保持常亮）
        showSeekProgressOverlay(targetPosition, deltaMs > 0, false);

        // 延迟执行实际seek
        final long finalTargetPosition = targetPosition;
        final boolean isForward = deltaMs > 0;
        seekRunnable = () -> {
            executeSeek(finalTargetPosition, isForward);
            // 重置状态
            seekBasePosition = -1;
            seekAccumulatedTime = 0;
        };
        seekHandler.postDelayed(seekRunnable, SEEK_DELAY);
    }

    /**
     * 执行实际的seek操作
     */
    private void executeSeek(long position, boolean isForward) {
        Log.d(TAG, "执行seek: position=" + position);
        if (currentPlayerKernel != null) {
            currentPlayerKernel.seekTo(position);
        }
        // seek执行后，启动隐藏计时器
        showSeekProgressOverlay(position, isForward, true);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        // 点击屏幕呼出/隐藏菜单
        if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
            if (menuController != null) {
                menuController.toggleMenu();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    // UI - 中央播放/暂停图标
    private ImageView centerPlayIcon;
    private Handler iconHandler = new Handler(Looper.getMainLooper());
    private Runnable hideIconRunnable;

    // 当前播放速度（由 menuController 管理，保留兼容变量）
    private float currentSpeed = 1.0f;
    private static final float[] SPEED_OPTIONS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private static final String[] SPEED_LABELS = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};

    /**
     * 显示设置菜单（委托给 menuController）
     */
    private void showSettingsMenu() {
        if (menuController != null) {
            menuController.showMenu();
        }
    }

    /**
     * 隐藏设置菜单（委托给 menuController）
     */
    private void hideSettingsMenu() {
        if (menuController != null) {
            menuController.hideMenu();
        }
    }

    /**
     * 格式化时间
     */
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

    /**
     * 更新速度标签（委托给 menuController）
     */
    private void updateSpeedLabel() {
        // 由 menuController 管理
    }

    /**
     * 更新弹幕标签（委托给 menuController）
     */
    private void updateDanmakuLabel() {
        // 由 menuController 管理
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

    // 快进/快退进度条相关
    private View seekProgressOverlay;
    private TextView seekTimeText;
    private android.widget.ProgressBar seekProgressBar;
    private Handler seekOverlayHandler = new Handler(Looper.getMainLooper());
    private Runnable hideSeekOverlayRunnable;

    /**
     * 显示快进/快退进度条
     * @param newPosition 目标位置
     * @param isForward 是否快进
     */
    private void showSeekProgressOverlay(long newPosition, boolean isForward) {
        showSeekProgressOverlay(newPosition, isForward, false);
    }

    /**
     * 显示快进/快退进度条
     * @param newPosition 目标位置
     * @param isForward 是否快进
     * @param startHideTimer 是否启动隐藏计时器（累积seek期间不启动）
     */
    private void showSeekProgressOverlay(long newPosition, boolean isForward, boolean startHideTimer) {
        if (currentPlayerKernel == null) return;

        long duration = currentPlayerKernel.getDuration();
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
            // 取消之前的隐藏任务
            if (hideSeekOverlayRunnable != null) {
                seekOverlayHandler.removeCallbacks(hideSeekOverlayRunnable);
            }

            // 取消淡出动画
            seekProgressOverlay.animate().cancel();
            
            // 检查是否需要淡入动画（只在首次显示时）
            boolean needFadeIn = seekProgressOverlay.getVisibility() != View.VISIBLE || seekProgressOverlay.getAlpha() < 1f;
            
            // 确保完全可见
            seekProgressOverlay.setAlpha(1f);
            seekProgressOverlay.setVisibility(View.VISIBLE);

            // 设置时间文本
            String timeText = (isForward ? "" : "") + formatTime(newPosition) + " / " + formatTime(duration);
            seekTimeText.setText(timeText);

            // 设置进度条（直接设置，不用动画，避免闪烁）
            int progress = (int) ((newPosition * 100) / duration);
            seekProgressBar.setProgress(progress);

            // 只有在startHideTimer为true时才启动隐藏计时器
            if (startHideTimer) {
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
            }
        }
    }

    /**
     * 动态创建快进/快退进度条视图
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
                    ToastUtils.show(this, "播放速度: " + SPEED_LABELS[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void showQualityMenu() {
        String[] qualityLabels = {"原画", "1080P", "720P", "480P"};

        new android.app.AlertDialog.Builder(this)
                .setTitle("画质选择")
                .setItems(qualityLabels, (dialog, which) -> {
                    ToastUtils.show(this, "已选择: " + qualityLabels[which]);
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
                        ToastUtils.show(this, "字幕已关闭");
                    } else {
                        int subtitleIndex = which - 1;
                        com.mynas.nastv.model.StreamListResponse.SubtitleStream sub = subtitleStreams.get(subtitleIndex);

                        if (sub.isExternal()) {
                            // 外挂字幕：下载并加载
                            loadSubtitle(subtitleIndex);
                        } else {
                            // 内嵌字幕：直连模式下不支持
                            if (isDirectLinkMode) {
                                ToastUtils.show(this, "直连模式不支持内嵌字幕，请使用转码模式");
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
     * 关闭字幕
     */
    private void disableSubtitle() {
        currentSubtitleIndex = -1;
        if (subtitleManager != null) {
            subtitleManager.disableSubtitle();
        }
    }

    /**
     * 显示音频选择菜单
     */
    private void showAudioMenu() {
        if (audioTrackManager == null || audioStreams == null || audioStreams.isEmpty()) {
            ToastUtils.show(this, "没有可用的音频轨道");
            return;
        }

        // 构建音频选项列表
        java.util.List<String> options = new java.util.ArrayList<>();
        for (int i = 0; i < audioStreams.size(); i++) {
            com.mynas.nastv.model.StreamListResponse.AudioStream audio = audioStreams.get(i);
            StringBuilder label = new StringBuilder();
            
            String title = audio.getTitle();
            String language = audio.getLanguage();
            if (title != null && !title.isEmpty()) {
                label.append(title);
            } else if (language != null && !language.isEmpty()) {
                label.append(language);
            } else {
                label.append("音频 ").append(i + 1);
            }
            
            String audioType = audio.getAudioType();
            if (audioType != null && !audioType.isEmpty()) {
                label.append(" (").append(audioType.toUpperCase()).append(")");
                String lowerType = audioType.toLowerCase();
                if (lowerType.contains("dts") || lowerType.contains("eac3") || lowerType.contains("truehd")) {
                    label.append(" ⚠️");
                }
            }
            
            options.add(label.toString());
        }

        String[] audioOptions = options.toArray(new String[0]);
        int checkedItem = currentAudioIndex >= 0 ? currentAudioIndex : 0;

        new android.app.AlertDialog.Builder(this)
                .setTitle("音频选择")
                .setSingleChoiceItems(audioOptions, checkedItem, (dialog, which) -> {
                    selectAudioTrack(which);
                    dialog.dismiss();
                })
                .show();
    }

    /**
     * 选择音频轨道（委托给 AudioTrackManager）
     */
    private void selectAudioTrack(int index) {
        if (audioTrackManager != null && currentPlayerKernel != null) {
            audioTrackManager.selectAudioTrack(index, currentPlayerKernel);
        }
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
        ToastUtils.show(this, isDanmakuEnabled ? "弹幕已开启" : "弹幕已关闭");
    }

    /**
     * 显示设置对话框
     */
    private void showSettingsDialog() {
        // 解码器显示：考虑自动切换的情况
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
     * 切换自动连播
     */
    private void toggleAutoPlayNext() {
        boolean current = SharedPreferencesManager.isAutoPlayNext();
        SharedPreferencesManager.setAutoPlayNext(!current);
        ToastUtils.show(this, "自动连播: " + (!current ? "开" : "关"));
    }

    /**
     * 显示跳过片头/片尾设置对话框
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
     * 显示跳过时间选择对话框
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
                        ToastUtils.show(this, (isIntro ? "跳过片头: " : "跳过片尾: ") + timeOptions[which]);
                    }
                    dialog.dismiss();
                })
                .show();
    }

    /**
     * 显示自定义跳过时间对话框
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
                        ToastUtils.show(this, "已设置为 " + seconds + " 秒");
                    } catch (NumberFormatException e) {
                        ToastUtils.show(this, "请输入有效数字");
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
     * 显示画面比例对话框
     */
    private void showAspectRatioDialog() {
        String[] ratioOptions = {"默认", "16:9", "4:3", "填充屏幕"};
        int currentRatio = SharedPreferencesManager.getAspectRatio();

        new android.app.AlertDialog.Builder(this)
                .setTitle("画面比例")
                .setSingleChoiceItems(ratioOptions, currentRatio, (dialog, which) -> {
                    SharedPreferencesManager.setAspectRatio(which);
                    applyAspectRatio(which);
                    ToastUtils.show(this, "画面比例: " + ratioOptions[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private String getAspectRatioLabel(int ratio) {
        switch (ratio) {
            case 1:
                return "16:9";
            case 2:
                return "4:3";
            case 3:
                return "填充";
            default:
                return "默认";
        }
    }

    /**
     * 显示解码器选择对话框
     */
    private void showDecoderDialog() {
        String[] decoderOptions = {"硬解 (推荐)", "软解 (兼容性更好)"};
        int currentDecoder = SharedPreferencesManager.getDecoderType();

        new android.app.AlertDialog.Builder(this)
                .setTitle("解码器")
                .setSingleChoiceItems(decoderOptions, currentDecoder, (dialog, which) -> {
                    SharedPreferencesManager.setDecoderType(which);
                    String msg = which == 0 ? "已切换到硬解，重新播放生效" : "已切换到软解，重新播放生效";
                    ToastUtils.show(this, msg);
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
     * 重新加载视频（用于切换解码器后）
     */
    private void reloadVideo() {
        if (currentPlayerKernel != null && currentVideoUrl != null) {
            // 保存当前播放位置
            long currentPosition = currentPlayerKernel.getCurrentPosition();

            // 重置解码器降级标志（用户手动切换时）
            forceUseSoftwareDecoder = false;
            if (ijkPlayerKernel != null) {
                ijkPlayerKernel.setForceUseSoftwareDecoder(false);
            }

            // 停止当前播放
            if (currentPlayerKernel != null) {
                currentPlayerKernel.release();
            }

            // 重新初始化播放器
            initializePlayer();

            // 设置恢复位置
            resumePositionSeconds = currentPosition / 1000;

            // 重新播放
            playMedia(currentVideoUrl);

            ToastUtils.show(this, "正在重新加载...");
        }
    }

    /**
     * 应用画面比例（委托给 PlayerSettingsHelper）
     */
    private void applyAspectRatio(int ratio) {
        // 画面比例设置由 PlayerSettingsHelper 处理
        // 如果需要，可以在这里添加额外的逻辑
    }

    /**
     * 显示音频轨道对话框
     * 注意：GSYVideoPlayer + IJKPlayer 不支持音频轨道选择
     */
    private void showAudioTrackDialog() {
        showAudioMenu();
    }

    /**
     * 检查是否是 ASS 绘图命令
     * ASS 绘图命令格式: m x y b x1 y1 x2 y2... (move 和 bezier curve)
     * 这些命令用于绘制 logo 或图形，不应该作为文本显示
     */
    private boolean isAssDrawingCommand(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // 去除首尾空格
        text = text.trim();
        // 绘图命令通常以 "m" 开头，后面跟数字
        // 例如: "m 2.39 33.02 b 2.6 33.37..."
        if (text.startsWith("m ") || text.startsWith("m\t")) {
            // 检查是否主要由数字、空格、小数点和绘图命令字母(m, b, l, c, s, p, n)组成
            String cleaned = text.replaceAll("[mblcspn\\s\\d\\.]", "");
            // 如果清理后几乎没有其他字符，说明是绘图命令
            if (cleaned.length() < text.length() * 0.1) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为 View 应用饱和度增强滤镜
     * 优化方案：
     * 1. 提高饱和度以改善颜色偏白和饱和度不足问题
     * 2. 增强对比度以提升画面层次感
     * 3. 微调亮度以改善偏白问题
     * 
     * @param view 目标 View
     * @param saturation 饱和度值 (0=灰度, 1=正常, >1=增强饱和度)
     */
    private void applySaturationFilter(View view, float saturation) {
        if (view == null) return;
        
        // 创建饱和度矩阵
        android.graphics.ColorMatrix colorMatrix = new android.graphics.ColorMatrix();
        colorMatrix.setSaturation(saturation);
        
        // 增强对比度 - 从1.05增加到1.1，提升画面层次感和清晰度
        float contrast = 1.1f; // 增加10%对比度
        // 微调亮度 - 降低2%亮度以改善颜色偏白问题
        float brightness = -5f; // 降低约2%亮度（-5/255 ≈ -2%）
        float scale = contrast;
        float translate = (-.5f * scale + .5f) * 255f + brightness;
        android.graphics.ColorMatrix contrastMatrix = new android.graphics.ColorMatrix(new float[] {
            scale, 0, 0, 0, translate,
            0, scale, 0, 0, translate,
            0, 0, scale, 0, translate,
            0, 0, 0, 1, 0
        });
        colorMatrix.postConcat(contrastMatrix);
        
        // 应用滤镜
        android.graphics.ColorMatrixColorFilter filter = new android.graphics.ColorMatrixColorFilter(colorMatrix);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColorFilter(filter);
        view.setLayerPaint(paint);
        
        Log.d(TAG, "已应用饱和度滤镜: saturation=" + saturation + ", contrast=" + contrast + ", brightness=" + brightness);
    }

    /**
     * 调整 ExoPlayer TextureView 的宽高比以匹配视频
     * 保持视频原始比例，避免拉伸导致画面模糊
     */
    private void adjustTextureViewAspectRatio(int videoWidth, int videoHeight) {
        if (exoTextureView == null || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }

        // 获取父容器的尺寸
        android.view.ViewGroup parent = (android.view.ViewGroup) exoTextureView.getParent();
        if (parent == null) {
            return;
        }

        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        if (parentWidth <= 0 || parentHeight <= 0) {
            return;
        }

        float videoAspect = (float) videoWidth / videoHeight;
        float parentAspect = (float) parentWidth / parentHeight;

        int newWidth, newHeight;

        if (videoAspect > parentAspect) {
            // 视频更宽，以宽度为准
            newWidth = parentWidth;
            newHeight = (int) (parentWidth / videoAspect);
        } else {
            // 视频更高，以高度为准
            newHeight = parentHeight;
            newWidth = (int) (parentHeight * videoAspect);
        }

        // 居中显示
        int leftMargin = (parentWidth - newWidth) / 2;
        int topMargin = (parentHeight - newHeight) / 2;

        android.widget.RelativeLayout.LayoutParams params = new android.widget.RelativeLayout.LayoutParams(newWidth, newHeight);
        params.leftMargin = leftMargin;
        params.topMargin = topMargin;
        exoTextureView.setLayoutParams(params);

        Log.d(TAG, "调整 TextureView 尺寸: " + newWidth + "x" + newHeight + 
              " (视频: " + videoWidth + "x" + videoHeight + ", 容器: " + parentWidth + "x" + parentHeight + ")");
    }
}
