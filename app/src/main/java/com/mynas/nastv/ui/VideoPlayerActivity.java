package com.mynas.nastv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.mynas.nastv.R;
import com.mynas.nastv.feature.danmaku.api.IDanmuController;
import com.mynas.nastv.feature.danmaku.logic.DanmuControllerImpl;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.model.PlayStartInfo;
import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.player.Media3VideoPlayer;
import com.mynas.nastv.utils.FormatUtils;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.ToastUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Media3 视频播放器 Activity
 * 
 * 使用 androidx.media3 ExoPlayer 实现视频播放
 * 支持弹幕、字幕、快进快退等功能
 */
public class VideoPlayerActivity extends AppCompatActivity {
    
    private static final String TAG = "Media3PlayerActivity";
    
    // UI
    private Media3VideoPlayer playerView;
    private View loadingLayout;
    private View errorLayout;
    private TextView errorText;
    private FrameLayout danmuContainer;
    private TextView subtitleTextView;
    private View bufferingIndicator;  // LinearLayout in layout
    private LinearLayout topInfoContainer;
    private LinearLayout bottomMenuContainer;
    private TextView titleText;
    private TextView infoText;
    private LinearLayout seekOverlay;
    private TextView seekTimeText;
    private ImageView centerPlayIcon;  // 中央暂停图标
    
    // 底部菜单进度条
    private TextView progressCurrentTime;
    private TextView progressTotalTime;
    private android.widget.SeekBar progressSeekbar;
    private android.widget.ProgressBar bufferProgressbar;
    private android.widget.ProgressBar seekProgressBar;
    
    // 侧边面板
    private View sidePanel;
    private TextView sidePanelTitle;
    private LinearLayout sidePanelContent;
    private boolean isSidePanelVisible = false;
    private String currentPanelType = null; // "speed", "episode", "settings"
    
    // 剧集列表
    private List<EpisodeListResponse.Episode> episodeList = new ArrayList<>();
    
    // 版本列表（多文件）
    private List<StreamListResponse.FileStream> versionList = new ArrayList<>();
    private String itemGuid;  // 用于获取版本列表
    
    // 弹幕控制器
    private IDanmuController danmuController;
    
    // 数据
    private String videoUrl;
    private String mediaTitle;
    private String mediaGuid;
    private String episodeGuid;
    private String tvTitle;
    private int episodeNumber;
    private int seasonNumber;
    private String parentGuid;
    private long resumePositionMs = 0;
    
    // 状态
    private boolean isPlayerReady = false;
    private boolean isDanmakuEnabled = true;
    private float currentSpeed = 1.0f;
    
    // 倍速选项
    private static final float[] SPEED_VALUES = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private static final String[] SPEED_LABELS = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x"};
    
    // 快进快退
    private long seekAccumulatedTime = 0;
    private long seekBasePosition = -1;
    private Handler seekHandler = new Handler(Looper.getMainLooper());
    private Runnable seekRunnable;
    private static final long SEEK_DELAY = 500;
    private static final long SEEK_STEP = 10000; // 10秒
    
    // 进度更新
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    
    // Manager
    private MediaManager mediaManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        
        initData();
        initViews();
        initPlayer();
        startPlayback();
    }
    
    private void initData() {
        Intent intent = getIntent();
        videoUrl = intent.getStringExtra("video_url");
        mediaTitle = intent.getStringExtra("video_title");
        if (mediaTitle == null) mediaTitle = intent.getStringExtra("media_title");
        if (mediaTitle == null) mediaTitle = "未知视频";
        
        tvTitle = intent.getStringExtra("tv_title");
        if (tvTitle == null) tvTitle = mediaTitle;
        
        mediaGuid = intent.getStringExtra("media_guid");
        episodeGuid = intent.getStringExtra("episode_guid");
        parentGuid = intent.getStringExtra("season_guid");
        if (parentGuid == null) parentGuid = intent.getStringExtra("parent_guid");
        
        // itemGuid 用于获取版本列表
        itemGuid = intent.getStringExtra("item_guid");
        if (itemGuid == null) itemGuid = episodeGuid;
        
        episodeNumber = intent.getIntExtra("episode_number", 1);
        seasonNumber = intent.getIntExtra("season_number", 1);
        
        // resume_position 和 ts 值已经是毫秒，不需要再乘以 1000
        resumePositionMs = intent.getLongExtra("resume_position", 0);
        if (resumePositionMs <= 0) {
            resumePositionMs = intent.getLongExtra("ts", 0);
        }
        
        mediaManager = new MediaManager(this);
        
        Log.d(TAG, "播放: " + mediaTitle + ", URL: " + (videoUrl != null ? videoUrl.substring(0, Math.min(80, videoUrl.length())) : "null"));
    }
    
    private void initViews() {
        playerView = findViewById(R.id.player_view);
        loadingLayout = findViewById(R.id.loading_layout);
        errorLayout = findViewById(R.id.error_layout);
        errorText = findViewById(R.id.error_text);
        danmuContainer = findViewById(R.id.danmu_container);
        subtitleTextView = findViewById(R.id.subtitle_text_view);
        bufferingIndicator = findViewById(R.id.buffering_indicator);
        topInfoContainer = findViewById(R.id.top_info_container);
        bottomMenuContainer = findViewById(R.id.bottom_menu_container);
        titleText = findViewById(R.id.title_text);
        infoText = findViewById(R.id.info_text);
        seekOverlay = findViewById(R.id.seek_progress_overlay);
        seekTimeText = findViewById(R.id.seek_time_text);
        seekProgressBar = findViewById(R.id.seek_progress_bar);
        centerPlayIcon = findViewById(R.id.center_play_icon);
        
        // 底部菜单进度条
        progressCurrentTime = findViewById(R.id.progress_current_time);
        progressTotalTime = findViewById(R.id.progress_total_time);
        progressSeekbar = findViewById(R.id.progress_seekbar);
        bufferProgressbar = findViewById(R.id.buffer_progressbar);
        
        // 侧边面板
        sidePanel = findViewById(R.id.side_panel);
        if (sidePanel != null) {
            sidePanelTitle = sidePanel.findViewById(R.id.panel_title);
            sidePanelContent = sidePanel.findViewById(R.id.panel_content);
            // 点击背景关闭面板
            sidePanel.setOnClickListener(v -> hideSidePanel());
            // 阻止点击面板容器时关闭
            View panelContainer = sidePanel.findViewById(R.id.panel_container);
            if (panelContainer != null) {
                panelContainer.setOnClickListener(v -> {});
            }
        }
        
        // 设置标题
        if (titleText != null) {
            titleText.setText(mediaTitle);
        }
        
        // 初始化弹幕
        if (danmuContainer != null) {
            danmuController = new DanmuControllerImpl();
            danmuController.initialize(this, danmuContainer);
            Log.d(TAG, "弹幕控制器已初始化");
        }
        
        // 点击切换菜单显示
        playerView.setOnClickListener(v -> toggleMenu());
        
        // 设置菜单按钮点击监听
        setupMenuButtons();
    }
    
    private void setupMenuButtons() {
        // 下一集
        View menuNextEpisode = findViewById(R.id.menu_next_episode);
        if (menuNextEpisode != null) {
            menuNextEpisode.setOnClickListener(v -> playNextEpisode());
        }
        
        // 倍速
        View menuSpeed = findViewById(R.id.menu_speed);
        if (menuSpeed != null) {
            menuSpeed.setOnClickListener(v -> showSpeedPanel());
        }
        
        // 选集
        View menuEpisode = findViewById(R.id.menu_episode);
        if (menuEpisode != null) {
            menuEpisode.setOnClickListener(v -> showEpisodePanel());
        }
        
        // 版本（多文件选择）
        View menuQuality = findViewById(R.id.menu_quality);
        if (menuQuality != null) {
            menuQuality.setOnClickListener(v -> showVersionPanel());
        }
        
        // 字幕
        View menuSubtitle = findViewById(R.id.menu_subtitle);
        if (menuSubtitle != null) {
            menuSubtitle.setOnClickListener(v -> {
                ToastUtils.show(this, "字幕功能待实现");
            });
        }
        
        // 音频
        View menuAudio = findViewById(R.id.menu_audio);
        if (menuAudio != null) {
            menuAudio.setOnClickListener(v -> showAudioPanel());
        }
        
        // 弹幕
        View menuDanmaku = findViewById(R.id.menu_danmaku);
        if (menuDanmaku != null) {
            menuDanmaku.setOnClickListener(v -> toggleDanmaku());
        }
        
        // 设置
        View menuSettings = findViewById(R.id.menu_settings);
        if (menuSettings != null) {
            menuSettings.setOnClickListener(v -> {
                ToastUtils.show(this, "设置功能待实现");
            });
        }
        
        // 加载剧集列表
        loadEpisodeList();
        
        // 加载版本列表（多文件）
        loadVersionList();
    }
    
    // ==================== 侧边面板 ====================
    
    private void showSpeedPanel() {
        if (sidePanelContent == null) return;
        
        toggleMenu(); // 隐藏底部菜单
        sidePanelContent.removeAllViews();
        sidePanelTitle.setText("倍速");
        
        LayoutInflater inflater = LayoutInflater.from(this);
        View firstFocusable = null;
        
        for (int i = 0; i < SPEED_VALUES.length; i++) {
            final float speed = SPEED_VALUES[i];
            final String label = SPEED_LABELS[i];
            
            View itemView = inflater.inflate(R.layout.item_panel_option, sidePanelContent, false);
            TextView titleView = itemView.findViewById(R.id.option_title);
            TextView valueView = itemView.findViewById(R.id.option_value);
            
            titleView.setText(label);
            
            // 当前选中的倍速显示勾选
            if (Math.abs(speed - currentSpeed) < 0.01f) {
                valueView.setText("✓");
                valueView.setTextColor(0xFF23ADE5);
                itemView.setSelected(true);
            } else {
                valueView.setText("");
            }
            
            itemView.setOnClickListener(v -> {
                currentSpeed = speed;
                playerView.setSpeed(speed);
                ToastUtils.show(this, "播放速度: " + label);
                hideSidePanel();
            });
            
            sidePanelContent.addView(itemView);
            if (firstFocusable == null) firstFocusable = itemView;
        }
        
        showSidePanel("speed", firstFocusable);
    }
    
    private void showAudioPanel() {
        if (sidePanelContent == null || playerView == null) return;
        
        List<Media3VideoPlayer.AudioTrackInfo> audioTracks = playerView.getAudioTracks();
        
        if (audioTracks.isEmpty()) {
            ToastUtils.show(this, "暂无可用音轨");
            return;
        }
        
        toggleMenu(); // 隐藏底部菜单
        sidePanelContent.removeAllViews();
        sidePanelTitle.setText("音频 (" + audioTracks.size() + "条音轨)");
        
        LayoutInflater inflater = LayoutInflater.from(this);
        View firstFocusable = null;
        View selectedView = null;
        
        for (Media3VideoPlayer.AudioTrackInfo track : audioTracks) {
            View itemView = inflater.inflate(R.layout.item_panel_option, sidePanelContent, false);
            TextView titleView = itemView.findViewById(R.id.option_title);
            TextView valueView = itemView.findViewById(R.id.option_value);
            
            titleView.setText(track.getDisplayName());
            
            // 不支持的格式显示灰色
            if (!track.isSupported) {
                titleView.setTextColor(0x80FFFFFF);  // 半透明白色
            }
            
            // 当前选中的音轨显示勾选
            if (track.isSelected) {
                valueView.setText("✓");
                valueView.setTextColor(0xFF23ADE5);
                itemView.setSelected(true);
                selectedView = itemView;
            } else {
                valueView.setText("");
            }
            
            final int groupIndex = track.groupIndex;
            final int trackIndex = track.trackIndex;
            final String displayName = track.getDisplayName();
            final boolean isSupported = track.isSupported;
            
            itemView.setOnClickListener(v -> {
                if (!isSupported) {
                    ToastUtils.show(this, "该音轨格式不支持: " + displayName);
                    return;
                }
                playerView.selectAudioTrack(groupIndex, trackIndex);
                ToastUtils.show(this, "已切换音轨: " + displayName);
                hideSidePanel();
            });
            
            sidePanelContent.addView(itemView);
            if (firstFocusable == null) firstFocusable = itemView;
        }
        
        // 优先让当前选中的音轨获取焦点
        showSidePanel("audio", selectedView != null ? selectedView : firstFocusable);
    }
    
    private void showVersionPanel() {
        if (sidePanelContent == null) return;
        
        if (versionList.isEmpty()) {
            ToastUtils.show(this, "暂无其他版本");
            return;
        }
        
        // 只有一个版本时也提示
        if (versionList.size() == 1) {
            ToastUtils.show(this, "只有一个版本");
            return;
        }
        
        toggleMenu(); // 隐藏底部菜单
        sidePanelContent.removeAllViews();
        sidePanelTitle.setText("版本 (" + versionList.size() + "个文件)");
        
        LayoutInflater inflater = LayoutInflater.from(this);
        View firstFocusable = null;
        View selectedView = null;
        
        for (int i = 0; i < versionList.size(); i++) {
            StreamListResponse.FileStream file = versionList.get(i);
            View itemView = inflater.inflate(R.layout.item_panel_option, sidePanelContent, false);
            TextView titleView = itemView.findViewById(R.id.option_title);
            TextView valueView = itemView.findViewById(R.id.option_value);
            
            // 显示文件名和大小
            String displayName = file.getDisplayName();
            String sizeStr = FormatUtils.formatFileSize(file.getSize());
            titleView.setText(displayName);
            valueView.setText(sizeStr);
            
            // 当前播放的版本高亮
            boolean isCurrent = file.getGuid().equals(mediaGuid);
            if (isCurrent) {
                titleView.setTextColor(0xFF23ADE5);
                valueView.setTextColor(0xFF23ADE5);
                itemView.setSelected(true);
                selectedView = itemView;
            }
            
            final String fileGuid = file.getGuid();
            final String fileName = displayName;
            
            itemView.setOnClickListener(v -> {
                if (!fileGuid.equals(mediaGuid)) {
                    playVersion(fileGuid, fileName);
                }
                hideSidePanel();
            });
            
            sidePanelContent.addView(itemView);
            if (firstFocusable == null) firstFocusable = itemView;
        }
        
        // 优先让当前版本获取焦点
        showSidePanel("version", selectedView != null ? selectedView : firstFocusable);
    }
    
    private void playVersion(String newMediaGuid, String fileName) {
        ToastUtils.show(this, "正在切换版本...");
        
        // 停止当前播放
        if (playerView != null) {
            playerView.pause();
        }
        if (danmuController != null) {
            danmuController.pausePlayback();
        }
        
        // 更新状态
        mediaGuid = newMediaGuid;
        resumePositionMs = 0;
        
        // 构建新的播放URL
        String baseUrl = SharedPreferencesManager.getServerBaseUrl();
        videoUrl = baseUrl + mediaManager.getPlayUrl(newMediaGuid);
        
        Log.d(TAG, "切换版本: " + fileName + ", URL: " + videoUrl);
        
        // 重新开始播放
        isPlayerReady = false;
        startPlayback();
    }
    
    private void showEpisodePanel() {
        if (sidePanelContent == null) return;
        
        if (episodeList.isEmpty()) {
            ToastUtils.show(this, "暂无剧集信息");
            return;
        }
        
        toggleMenu(); // 隐藏底部菜单
        sidePanelContent.removeAllViews();
        sidePanelTitle.setText("选集 (" + episodeList.size() + "集)");
        
        LayoutInflater inflater = LayoutInflater.from(this);
        View firstFocusable = null;
        View currentEpisodeView = null;
        String baseUrl = SharedPreferencesManager.getServerBaseUrl();
        
        for (EpisodeListResponse.Episode episode : episodeList) {
            View itemView = inflater.inflate(R.layout.item_episode_grid, sidePanelContent, false);
            ImageView thumbView = itemView.findViewById(R.id.episode_thumb);
            ImageView iconView = itemView.findViewById(R.id.episode_icon);
            ImageView checkView = itemView.findViewById(R.id.episode_check);
            TextView numberView = itemView.findViewById(R.id.episode_number);
            TextView titleView = itemView.findViewById(R.id.episode_title);
            
            int epNum = episode.getEpisodeNumber();
            numberView.setText("第" + epNum + "集");
            
            // 设置标题
            String epTitle = episode.getTitle();
            if (epTitle != null && !epTitle.isEmpty()) {
                titleView.setText(epTitle);
                titleView.setVisibility(View.VISIBLE);
            } else {
                titleView.setVisibility(View.GONE);
            }
            
            // 加载缩略图
            String stillPath = episode.getStillPath();
            if (stillPath != null && !stillPath.isEmpty()) {
                String thumbUrl = baseUrl + "/v/api/v1/sys/img" + stillPath + "?w=200";
                Glide.with(this).load(thumbUrl).into(thumbView);
            }
            
            // 当前集高亮
            if (epNum == episodeNumber) {
                itemView.setSelected(true);
                iconView.setVisibility(View.VISIBLE);
                checkView.setVisibility(View.VISIBLE);
                currentEpisodeView = itemView;
            } else {
                iconView.setVisibility(View.GONE);
                checkView.setVisibility(View.GONE);
            }
            
            final String epGuid = episode.getGuid();
            final int targetEpNum = epNum;
            itemView.setOnClickListener(v -> {
                if (targetEpNum != episodeNumber) {
                    playEpisode(epGuid, targetEpNum);
                }
                hideSidePanel();
            });
            
            sidePanelContent.addView(itemView);
            if (firstFocusable == null) firstFocusable = itemView;
        }
        
        // 优先让当前集获取焦点
        showSidePanel("episode", currentEpisodeView != null ? currentEpisodeView : firstFocusable);
    }
    
    private void showSidePanel(String type, View firstFocusable) {
        currentPanelType = type;
        isSidePanelVisible = true;
        if (sidePanel != null) {
            sidePanel.setVisibility(View.VISIBLE);
            if (firstFocusable != null) {
                firstFocusable.requestFocus();
            }
        }
    }
    
    private void hideSidePanel() {
        isSidePanelVisible = false;
        currentPanelType = null;
        if (sidePanel != null) {
            sidePanel.setVisibility(View.GONE);
        }
    }
    
    private void loadEpisodeList() {
        if (parentGuid == null || parentGuid.isEmpty()) {
            Log.d(TAG, "No parent guid, skip loading episode list");
            return;
        }
        
        mediaManager.getEpisodeList(parentGuid, new MediaManager.MediaCallback<List<EpisodeListResponse.Episode>>() {
            @Override
            public void onSuccess(List<EpisodeListResponse.Episode> episodes) {
                if (episodes != null && !episodes.isEmpty()) {
                    episodeList.clear();
                    episodeList.addAll(episodes);
                    Log.d(TAG, "Loaded " + episodes.size() + " episodes");
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load episodes: " + error);
            }
        });
    }
    
    private void loadVersionList() {
        if (itemGuid == null || itemGuid.isEmpty()) {
            Log.d(TAG, "No item guid, skip loading version list");
            return;
        }
        
        mediaManager.getStreamList(itemGuid, new MediaManager.MediaCallback<StreamListResponse>() {
            @Override
            public void onSuccess(StreamListResponse response) {
                if (response != null && response.getData() != null) {
                    List<StreamListResponse.FileStream> files = response.getData().getFiles();
                    if (files != null && !files.isEmpty()) {
                        versionList.clear();
                        versionList.addAll(files);
                        Log.d(TAG, "Loaded " + files.size() + " versions");
                    }
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load versions: " + error);
            }
        });
    }
    
    private void playNextEpisode() {
        if (episodeList.isEmpty()) {
            ToastUtils.show(this, "暂无下一集");
            return;
        }
        
        // 找到下一集
        for (int i = 0; i < episodeList.size(); i++) {
            if (episodeList.get(i).getEpisodeNumber() == episodeNumber) {
                if (i + 1 < episodeList.size()) {
                    EpisodeListResponse.Episode nextEp = episodeList.get(i + 1);
                    playEpisode(nextEp.getGuid(), nextEp.getEpisodeNumber());
                    return;
                }
            }
        }
        
        ToastUtils.show(this, "已是最后一集");
    }
    
    private void playEpisode(String epGuid, int epNum) {
        ToastUtils.show(this, "正在加载第" + epNum + "集...");
        
        // 停止当前播放
        if (playerView != null) {
            playerView.pause();
        }
        if (danmuController != null) {
            danmuController.pausePlayback();
        }
        
        // 获取新剧集的播放信息
        mediaManager.getPlayInfo(epGuid, new MediaManager.MediaCallback<com.mynas.nastv.model.PlayInfoResponse>() {
            @Override
            public void onSuccess(com.mynas.nastv.model.PlayInfoResponse data) {
                if (data.getData() != null) {
                    String mediaGuidNew = data.getData().getMediaGuid();
                    if (mediaGuidNew != null && !mediaGuidNew.isEmpty()) {
                        runOnUiThread(() -> {
                            // 更新状态
                            episodeGuid = epGuid;
                            episodeNumber = epNum;
                            mediaGuid = mediaGuidNew;
                            resumePositionMs = 0;
                            
                            // 更新标题
                            String newTitle = tvTitle + " 第" + epNum + "集";
                            mediaTitle = newTitle;
                            if (titleText != null) {
                                titleText.setText(newTitle);
                            }
                            
                            // 构建新的播放URL
                            String baseUrl = SharedPreferencesManager.getServerBaseUrl();
                            videoUrl = baseUrl + mediaManager.getPlayUrl(mediaGuidNew);
                            
                            // 重新开始播放
                            isPlayerReady = false;
                            startPlayback();
                        });
                    } else {
                        runOnUiThread(() -> ToastUtils.show(VideoPlayerActivity.this, "获取播放地址失败"));
                    }
                }
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> ToastUtils.show(VideoPlayerActivity.this, "加载失败: " + error));
            }
        });
    }
    
    private void toggleDanmaku() {
        isDanmakuEnabled = !isDanmakuEnabled;
        if (danmuController != null) {
            if (isDanmakuEnabled) {
                danmuController.startPlayback();
                ToastUtils.show(this, "弹幕已开启");
            } else {
                danmuController.pausePlayback();
                ToastUtils.show(this, "弹幕已关闭");
            }
        }
    }

    private void initPlayer() {
        // 创建请求头
        Map<String, String> headers = createHeaders();
        
        // 设置渲染模式（SurfaceView 支持 HDR）
        playerView.setRenderMode(Media3VideoPlayer.RenderMode.SURFACE_VIEW);
        
        // 初始化播放器
        playerView.setup(headers);
        
        // 应用画面比例设置
        int aspectRatio = SharedPreferencesManager.getAspectRatio();
        playerView.setVideoScalingMode(aspectRatio == 3 ? 1 : 0); // 填充模式使用裁剪
        
        // 强制最高画质
        playerView.forceHighestQuality();
        
        // 设置回调
        playerView.setPlayerCallback(new Media3VideoPlayer.PlayerCallback() {
            @Override
            public void onPrepared() {
                Log.d(TAG, "播放器准备完成");
                runOnUiThread(() -> {
                    isPlayerReady = true;
                    hideLoading();
                    
                    // 恢复播放位置
                    if (resumePositionMs > 0) {
                        playerView.seekTo(resumePositionMs);
                        Log.d(TAG, "恢复播放位置: " + (resumePositionMs / 1000) + "s");
                    }
                    
                    // 启动弹幕
                    startDanmaku();
                    
                    // 启动进度更新
                    startProgressUpdate();
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "播放错误: " + error);
                runOnUiThread(() -> showError(error));
            }
            
            @Override
            public void onCompletion() {
                Log.d(TAG, "播放完成");
                runOnUiThread(() -> {
                    stopProgressUpdate();
                    finish();
                });
            }
            
            @Override
            public void onBuffering(boolean isBuffering) {
                runOnUiThread(() -> {
                    if (bufferingIndicator != null) {
                        bufferingIndicator.setVisibility(isBuffering && isPlayerReady ? View.VISIBLE : View.GONE);
                    }
                });
            }
            
            @Override
            public void onVideoSizeChanged(int width, int height) {
                Log.d(TAG, "视频尺寸: " + width + "x" + height);
            }
        });
        
        // 设置字幕回调
        playerView.setSubtitleCallback(cues -> {
            runOnUiThread(() -> {
                if (subtitleTextView != null) {
                    if (cues != null && !cues.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (androidx.media3.common.text.Cue cue : cues) {
                            if (cue.text != null) {
                                if (sb.length() > 0) sb.append("\n");
                                sb.append(cue.text);
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
    }
    
    private void startPlayback() {
        if (videoUrl == null || videoUrl.isEmpty()) {
            showError("视频地址为空");
            return;
        }
        
        showLoading();
        
        // 获取缓存目录
        File cacheDir = new File(getCacheDir(), "video_cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        // 使用缓存播放
        playerView.playWithCache(videoUrl, cacheDir);
    }
    
    private Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        String token = SharedPreferencesManager.getAuthToken();
        if (token != null && !token.isEmpty()) {
            // 服务器需要 Authorization header，不是 X-Emby-Token
            // 移除 Bearer 前缀（如果存在）
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            headers.put("Authorization", authToken);
            // 同时添加 Cookie（与 Web 端一致）
            headers.put("Cookie", "Trim-MC-token=" + authToken);
        }
        return headers;
    }
    
    // ==================== 弹幕 ====================
    
    private void startDanmaku() {
        if (danmuController != null && tvTitle != null) {
            // 电影时 season/episode 可能为 0，弹幕接口需要传 1
            int season = seasonNumber > 0 ? seasonNumber : 1;
            int episode = episodeNumber > 0 ? episodeNumber : 1;
            Log.d(TAG, "加载弹幕: " + tvTitle + " S" + season + "E" + episode + " (原始: S" + seasonNumber + "E" + episodeNumber + ")");
            danmuController.loadDanmaku(tvTitle, episode, season, episodeGuid, parentGuid);
            danmuController.startPlayback();
        }
    }
    
    private void updateDanmakuPosition() {
        if (danmuController != null && isPlayerReady) {
            long position = playerView.getCurrentPosition();
            danmuController.updatePlaybackPosition(position);
        }
    }
    
    // ==================== 进度更新 ====================
    
    private void startProgressUpdate() {
        stopProgressUpdate();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                updateDanmakuPosition();
                updateProgressUI();
                // 优化：降低 UI 更新频率到 250ms，减少 UI 线程负担
                progressHandler.postDelayed(this, 250);
            }
        };
        progressHandler.post(progressRunnable);
    }
    
    private long lastDisplayedPosition = -1;
    private int lastDisplayedProgress = -1;
    
    private void updateProgressUI() {
        if (!isPlayerReady || playerView == null) return;
        
        long currentPosition = playerView.getCurrentPosition();
        long duration = playerView.getDuration();
        
        if (duration > 0) {
            int progress = (int) (currentPosition * 100 / duration);
            
            // 优化：只在进度变化时更新 UI
            if (progress != lastDisplayedProgress) {
                lastDisplayedProgress = progress;
                if (progressSeekbar != null) {
                    progressSeekbar.setProgress(progress);
                }
            }
            
            // 优化：只在秒数变化时更新时间显示
            long currentSeconds = currentPosition / 1000;
            long lastSeconds = lastDisplayedPosition / 1000;
            if (currentSeconds != lastSeconds || lastDisplayedPosition < 0) {
                lastDisplayedPosition = currentPosition;
                if (progressCurrentTime != null) {
                    progressCurrentTime.setText(formatTime(currentPosition));
                }
                if (progressTotalTime != null) {
                    progressTotalTime.setText(formatTime(duration));
                }
            }
            
            // 更新缓冲进度
            if (bufferProgressbar != null) {
                int bufferedPercentage = playerView.getBufferedPercentage();
                bufferProgressbar.setProgress(bufferedPercentage);
            }
        }
    }
    
    private void stopProgressUpdate() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }
    
    // ==================== UI 状态 ====================
    
    private void showLoading() {
        if (loadingLayout != null) loadingLayout.setVisibility(View.VISIBLE);
        if (errorLayout != null) errorLayout.setVisibility(View.GONE);
    }
    
    private void hideLoading() {
        if (loadingLayout != null) loadingLayout.setVisibility(View.GONE);
    }
    
    private void showError(String message) {
        if (loadingLayout != null) loadingLayout.setVisibility(View.GONE);
        if (errorLayout != null) errorLayout.setVisibility(View.VISIBLE);
        if (errorText != null) errorText.setText(message);
    }
    
    private void toggleMenu() {
        boolean isVisible = (topInfoContainer != null && topInfoContainer.getVisibility() == View.VISIBLE) ||
                           (bottomMenuContainer != null && bottomMenuContainer.getVisibility() == View.VISIBLE);
        
        int newVisibility = isVisible ? View.GONE : View.VISIBLE;
        
        if (topInfoContainer != null) {
            topInfoContainer.setVisibility(newVisibility);
        }
        if (bottomMenuContainer != null) {
            bottomMenuContainer.setVisibility(newVisibility);
            // 菜单显示时，让第一个按钮获取焦点
            if (newVisibility == View.VISIBLE) {
                View firstButton = bottomMenuContainer.findViewById(R.id.menu_next_episode);
                if (firstButton != null) {
                    firstButton.requestFocus();
                }
            }
        }
    }
    
    // ==================== 按键处理 ====================
    
    private boolean isMenuVisible() {
        return bottomMenuContainer != null && bottomMenuContainer.getVisibility() == View.VISIBLE;
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 侧边面板显示时的按键处理
        if (isSidePanelVisible) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    hideSidePanel();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    // 右键关闭面板
                    hideSidePanel();
                    return true;
                default:
                    // 其他按键让系统处理焦点
                    return super.onKeyDown(keyCode, event);
            }
        }
        
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                // 菜单显示时，让系统处理按钮点击
                if (isMenuVisible()) {
                    return super.onKeyDown(keyCode, event);
                }
                // 暂停/播放
                togglePlayPause();
                return true;
                
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // 菜单显示时，让系统处理焦点切换
                if (isMenuVisible()) {
                    return super.onKeyDown(keyCode, event);
                }
                // 快进/快退
                startSeek(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -SEEK_STEP : SEEK_STEP);
                return true;
                
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
                // 菜单显示时，上键隐藏菜单
                if (isMenuVisible() && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    toggleMenu();
                    return true;
                }
                // 菜单未显示时，上下键弹出菜单
                if (!isMenuVisible()) {
                    toggleMenu();
                    return true;
                }
                return super.onKeyDown(keyCode, event);
                
            case KeyEvent.KEYCODE_BACK:
                // 菜单显示时，返回键先隐藏菜单
                if (isMenuVisible()) {
                    toggleMenu();
                    return true;
                }
                onBackPressed();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            // 菜单显示时不处理
            if (isMenuVisible()) {
                return super.onKeyUp(keyCode, event);
            }
            executeSeek();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }
    
    private void togglePlayPause() {
        if (playerView.isPlaying()) {
            playerView.pause();
            if (danmuController != null) danmuController.pausePlayback();
            // 显示暂停图标
            if (centerPlayIcon != null) {
                centerPlayIcon.setImageResource(R.drawable.ic_pause);
                centerPlayIcon.setVisibility(View.VISIBLE);
            }
        } else {
            playerView.start();
            if (danmuController != null) danmuController.startPlayback();
            // 隐藏暂停图标
            if (centerPlayIcon != null) {
                centerPlayIcon.setVisibility(View.GONE);
            }
        }
    }
    
    private void startSeek(long delta) {
        if (!isPlayerReady) return;
        
        if (seekBasePosition < 0) {
            seekBasePosition = playerView.getCurrentPosition();
            seekAccumulatedTime = 0;
        }
        
        seekAccumulatedTime += delta;
        
        long targetPosition = seekBasePosition + seekAccumulatedTime;
        long duration = playerView.getDuration();
        targetPosition = Math.max(0, Math.min(targetPosition, duration));
        
        // 显示 seek 提示
        if (seekOverlay != null && seekTimeText != null) {
            seekOverlay.setVisibility(View.VISIBLE);
            String timeStr = formatTime(targetPosition) + " / " + formatTime(duration);
            seekTimeText.setText(timeStr);
            
            // 更新快进进度条
            if (seekProgressBar != null && duration > 0) {
                int progress = (int) (targetPosition * 100 / duration);
                seekProgressBar.setProgress(progress);
            }
        }
        
        // 取消之前的延迟执行
        if (seekRunnable != null) {
            seekHandler.removeCallbacks(seekRunnable);
        }
    }
    
    private void executeSeek() {
        if (seekBasePosition < 0) return;
        
        long targetPosition = seekBasePosition + seekAccumulatedTime;
        long duration = playerView.getDuration();
        targetPosition = Math.max(0, Math.min(targetPosition, duration));
        
        final long finalPosition = targetPosition;
        
        seekRunnable = () -> {
            playerView.seekTo(finalPosition);
            if (danmuController != null) {
                danmuController.seekTo(finalPosition);
            }
            
            // 隐藏 seek 提示
            if (seekOverlay != null) {
                seekOverlay.setVisibility(View.GONE);
            }
            
            seekBasePosition = -1;
            seekAccumulatedTime = 0;
        };
        
        seekHandler.postDelayed(seekRunnable, SEEK_DELAY);
    }
    
    private String formatTime(long timeMs) {
        long seconds = timeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
    
    // ==================== 生命周期 ====================
    
    @Override
    protected void onPause() {
        super.onPause();
        if (playerView != null) {
            playerView.pause();
        }
        if (danmuController != null) {
            danmuController.pausePlayback();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (playerView != null && isPlayerReady) {
            playerView.start();
        }
        if (danmuController != null && isPlayerReady) {
            danmuController.startPlayback();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopProgressUpdate();
        
        if (seekRunnable != null) {
            seekHandler.removeCallbacks(seekRunnable);
        }
        
        if (danmuController != null) {
            danmuController.destroy();
            danmuController = null;
        }
        
        if (playerView != null) {
            playerView.release();
        }
    }
}
