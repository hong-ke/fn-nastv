package com.mynas.nastv.player;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.mynas.nastv.R;
import com.mynas.nastv.cache.OkHttpProxyCacheManager;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.ToastUtils;

import java.util.List;

/**
 * 播放器菜单控制器
 * 负责底部菜单、进度条、设置对话框的管理
 */
public class PlayerMenuController {
    private static final String TAG = "PlayerMenuController";

    private Context context;
    private View rootView;
    private boolean isMenuVisible = false;

    // 菜单组件
    private LinearLayout bottomMenuContainer;
    private LinearLayout topInfoContainer;
    private TextView menuNextEpisode, menuSpeed, menuEpisode, menuQuality, menuSubtitle, menuAudio, menuDanmaku, menuSettings;

    // 进度条组件
    private TextView progressCurrentTime, progressTotalTime, bufferInfoText;
    private SeekBar progressSeekbar;
    private ProgressBar bufferProgressbar;
    private boolean isSeekbarTracking = false;

    // 播放速度
    private float currentSpeed = 1.0f;
    private static final float[] SPEED_OPTIONS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private static final String[] SPEED_LABELS = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};

    // 弹幕状态
    private boolean isDanmakuEnabled = true;

    // 回调
    private MenuCallback callback;
    private PositionProvider positionProvider;

    // 进度更新
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;

    public interface MenuCallback {
        void onSpeedChanged(float speed);
        void onEpisodeSelected(EpisodeListResponse.Episode episode);
        void onNextEpisode();
        void onSubtitleMenuRequested();
        void onAudioMenuRequested();
        void onDanmakuToggled(boolean enabled);
        void onSeekTo(long position);
        void onSettingsRequested();
    }

    public interface PositionProvider {
        long getCurrentPosition();
        long getDuration();
    }

    public PlayerMenuController(Context context, View rootView) {
        this.context = context;
        this.rootView = rootView;
        initViews();
    }

    public void setCallback(MenuCallback callback) {
        this.callback = callback;
    }

    public void setPositionProvider(PositionProvider provider) {
        this.positionProvider = provider;
    }

    public boolean isMenuVisible() {
        return isMenuVisible;
    }

    public boolean isDanmakuEnabled() {
        return isDanmakuEnabled;
    }

    private void initViews() {
        bottomMenuContainer = rootView.findViewById(R.id.bottom_menu_container);
        topInfoContainer = rootView.findViewById(R.id.top_info_container);
        menuNextEpisode = rootView.findViewById(R.id.menu_next_episode);
        menuSpeed = rootView.findViewById(R.id.menu_speed);
        menuEpisode = rootView.findViewById(R.id.menu_episode);
        menuQuality = rootView.findViewById(R.id.menu_quality);
        menuSubtitle = rootView.findViewById(R.id.menu_subtitle);
        menuAudio = rootView.findViewById(R.id.menu_audio);
        menuDanmaku = rootView.findViewById(R.id.menu_danmaku);
        menuSettings = rootView.findViewById(R.id.menu_settings);

        progressCurrentTime = rootView.findViewById(R.id.progress_current_time);
        progressTotalTime = rootView.findViewById(R.id.progress_total_time);
        progressSeekbar = rootView.findViewById(R.id.progress_seekbar);
        bufferProgressbar = rootView.findViewById(R.id.buffer_progressbar);
        bufferInfoText = rootView.findViewById(R.id.buffer_info_text);

        setupClickListeners();
        setupSeekbarListener();
        updateSpeedLabel();
        updateDanmakuLabel();
    }

    private void setupClickListeners() {
        if (menuNextEpisode != null) {
            menuNextEpisode.setOnClickListener(v -> {
                if (callback != null) callback.onNextEpisode();
            });
        }
        if (menuSpeed != null) {
            menuSpeed.setOnClickListener(v -> showSpeedMenu());
        }
        if (menuEpisode != null) {
            menuEpisode.setOnClickListener(v -> {
                // 由外部处理剧集列表
            });
        }
        if (menuQuality != null) {
            menuQuality.setOnClickListener(v -> showQualityMenu());
        }
        if (menuSubtitle != null) {
            menuSubtitle.setOnClickListener(v -> {
                if (callback != null) callback.onSubtitleMenuRequested();
            });
        }
        if (menuAudio != null) {
            menuAudio.setOnClickListener(v -> {
                if (callback != null) callback.onAudioMenuRequested();
            });
        }
        if (menuDanmaku != null) {
            menuDanmaku.setOnClickListener(v -> toggleDanmaku());
        }
        if (menuSettings != null) {
            menuSettings.setOnClickListener(v -> {
                if (callback != null) callback.onSettingsRequested();
            });
        }
    }

    private void setupSeekbarListener() {
        if (progressSeekbar != null) {
            progressSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && positionProvider != null) {
                        long duration = positionProvider.getDuration();
                        if (duration > 0) {
                            long newPosition = (duration * progress) / 100;
                            progressCurrentTime.setText(formatTime(newPosition));
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isSeekbarTracking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    isSeekbarTracking = false;
                    if (positionProvider != null && callback != null) {
                        long duration = positionProvider.getDuration();
                        if (duration > 0) {
                            long newPosition = (duration * seekBar.getProgress()) / 100;
                            callback.onSeekTo(newPosition);
                        }
                    }
                }
            });
        }
    }

    public void showMenu() {
        updateProgressBar();
        if (topInfoContainer != null) {
            topInfoContainer.setVisibility(View.VISIBLE);
            topInfoContainer.bringToFront();
        }
        if (bottomMenuContainer != null) {
            bottomMenuContainer.setVisibility(View.VISIBLE);
            bottomMenuContainer.bringToFront();
            bottomMenuContainer.requestLayout();
        }
        if (menuSpeed != null) {
            menuSpeed.requestFocus();
        }
        isMenuVisible = true;
        startProgressUpdate();
    }

    public void hideMenu() {
        if (bottomMenuContainer != null) {
            bottomMenuContainer.setVisibility(View.GONE);
        }
        if (topInfoContainer != null) {
            topInfoContainer.setVisibility(View.GONE);
        }
        stopProgressUpdate();
        isMenuVisible = false;
    }

    public void toggleMenu() {
        if (isMenuVisible) {
            hideMenu();
        } else {
            showMenu();
        }
    }

    private void startProgressUpdate() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        progressRunnable = new Runnable() {
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
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdate() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
    }

    private void updateProgressBar() {
        if (positionProvider == null || progressSeekbar == null) return;

        long currentPosition = positionProvider.getCurrentPosition();
        long duration = positionProvider.getDuration();

        if (duration > 0) {
            int progress = (int) ((currentPosition * 100) / duration);
            progressSeekbar.setProgress(progress);
            progressCurrentTime.setText(formatTime(currentPosition));
            progressTotalTime.setText(formatTime(duration));

            // 缓存进度
            int bufferProgress = progress;
            int cachedChunks = 0;
            try {
                OkHttpProxyCacheManager cacheManager = OkHttpProxyCacheManager.instance();
                if (cacheManager != null) {
                    bufferProgress = cacheManager.getDownloadProgress();
                    cachedChunks = cacheManager.getCachedChunksCount();
                }
            } catch (Exception e) {
                // ignore
            }

            if (bufferProgressbar != null) {
                bufferProgressbar.setProgress(bufferProgress);
            }
            if (bufferInfoText != null) {
                if (cachedChunks > 0) {
                    int cachedMB = cachedChunks * 2;
                    bufferInfoText.setText("已缓存 " + cachedMB + "MB (" + bufferProgress + "%)");
                } else if (bufferProgress >= 99) {
                    bufferInfoText.setText("缓存完成");
                } else {
                    bufferInfoText.setText("");
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

    private void showSpeedMenu() {
        int currentIndex = 2;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            if (Math.abs(SPEED_OPTIONS[i] - currentSpeed) < 0.01f) {
                currentIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(context)
                .setTitle("播放速度")
                .setSingleChoiceItems(SPEED_LABELS, currentIndex, (dialog, which) -> {
                    currentSpeed = SPEED_OPTIONS[which];
                    if (callback != null) {
                        callback.onSpeedChanged(currentSpeed);
                    }
                    updateSpeedLabel();
                    ToastUtils.show(context, "播放速度: " + SPEED_LABELS[which]);
                    dialog.dismiss();
                })
                .show();
    }

    private void showQualityMenu() {
        String[] qualityLabels = {"原画", "1080P", "720P", "480P"};
        new AlertDialog.Builder(context)
                .setTitle("画质选择")
                .setItems(qualityLabels, (dialog, which) -> {
                    ToastUtils.show(context, "已选择: " + qualityLabels[which]);
                })
                .show();
    }

    private void toggleDanmaku() {
        isDanmakuEnabled = !isDanmakuEnabled;
        if (callback != null) {
            callback.onDanmakuToggled(isDanmakuEnabled);
        }
        updateDanmakuLabel();
        ToastUtils.show(context, isDanmakuEnabled ? "弹幕已开启" : "弹幕已关闭");
    }

    private void updateSpeedLabel() {
        if (menuSpeed != null) {
            int index = 2;
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

    public void setEpisodeClickListener(View.OnClickListener listener) {
        if (menuEpisode != null) {
            menuEpisode.setOnClickListener(listener);
        }
    }

    public float getCurrentSpeed() {
        return currentSpeed;
    }

    public void release() {
        stopProgressUpdate();
    }
}
