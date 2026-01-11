package com.mynas.nastv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.mynas.nastv.R;
import com.mynas.nastv.feature.danmaku.api.IDanmuController;
import com.mynas.nastv.feature.danmaku.logic.DanmuControllerImpl;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.PlayStartInfo;
import com.mynas.nastv.player.Media3VideoPlayer;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.ToastUtils;

import java.io.File;
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
    
    // 底部菜单进度条
    private TextView progressCurrentTime;
    private TextView progressTotalTime;
    private android.widget.SeekBar progressSeekbar;
    private android.widget.ProgressBar bufferProgressbar;
    private android.widget.ProgressBar seekProgressBar;
    
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
        
        // 底部菜单进度条
        progressCurrentTime = findViewById(R.id.progress_current_time);
        progressTotalTime = findViewById(R.id.progress_total_time);
        progressSeekbar = findViewById(R.id.progress_seekbar);
        bufferProgressbar = findViewById(R.id.buffer_progressbar);
        
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
            menuNextEpisode.setOnClickListener(v -> {
                ToastUtils.show(this, "下一集功能待实现");
            });
        }
        
        // 倍速
        View menuSpeed = findViewById(R.id.menu_speed);
        if (menuSpeed != null) {
            menuSpeed.setOnClickListener(v -> showSpeedDialog());
        }
        
        // 选集
        View menuEpisode = findViewById(R.id.menu_episode);
        if (menuEpisode != null) {
            menuEpisode.setOnClickListener(v -> {
                ToastUtils.show(this, "选集功能待实现");
            });
        }
        
        // 画质
        View menuQuality = findViewById(R.id.menu_quality);
        if (menuQuality != null) {
            menuQuality.setOnClickListener(v -> {
                ToastUtils.show(this, "画质功能待实现");
            });
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
            menuAudio.setOnClickListener(v -> {
                ToastUtils.show(this, "音频功能待实现");
            });
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
    }
    
    private void showSpeedDialog() {
        String[] speeds = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};
        float[] speedValues = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        
        new android.app.AlertDialog.Builder(this)
            .setTitle("播放速度")
            .setItems(speeds, (dialog, which) -> {
                playerView.setSpeed(speedValues[which]);
                ToastUtils.show(this, "播放速度: " + speeds[which]);
            })
            .show();
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
            Log.d(TAG, "加载弹幕: " + tvTitle + " S" + seasonNumber + "E" + episodeNumber);
            danmuController.loadDanmaku(tvTitle, episodeNumber, seasonNumber, episodeGuid, parentGuid);
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
            ToastUtils.show(this, "已暂停");
        } else {
            playerView.start();
            if (danmuController != null) danmuController.startPlayback();
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
