package com.mynas.nastv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.PlayApiResponse;
import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.model.Danmu;
import com.mynas.nastv.ui.DanmuManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.widget.FrameLayout;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import android.animation.ObjectAnimator;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.Typeface;

import com.mynas.nastv.R;

/**
 * 🎬 视频播放器Activity - 基础版本
 * 功能：使用ExoPlayer进行视频播放，支持Android TV遥控器
 * 对应Web项目：VideoPlayer.vue
 */
public class VideoPlayerActivity extends AppCompatActivity {
    private static final String TAG = "VideoPlayerActivity";
    
    // UI组件
    private PlayerView playerView;
    private ImageView posterImageView;
    private TextView titleText;
    private TextView infoText;
    private View loadingLayout;
    private View errorLayout;
    private TextView errorText;
    private FrameLayout danmuContainer;
    
    // 播放器
    private ExoPlayer exoPlayer;
    
    // 🔄 重试相关变量 
    private String currentPlayUrl; // 存储当前实际播放的URL
    
    // 弹幕管理器
    private DanmuManager danmuManager;
    private Handler danmuUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable danmuUpdateRunnable;
    
    // 媒体信息
    private String mediaId;
    private String mediaTitle;
    private String mediaType;
    private String mediaYear;
    private String mediaGenre;
    private String posterUrl;
    
    // 播放相关信息
    private String mediaGuid;
    private String videoGuid;
    private String audioGuid;
    private String episodeGuid;  // 🎯 用于获取原画流媒体信息
    
    // MediaManager
    private MediaManager mediaManager;
    
    // 播放状态
    private boolean isPlayerReady = false;
    private boolean isPlayerError = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        
        Log.d(TAG, "🚀 VideoPlayerActivity启动");
        
        // 🔗 初始化数据
        initializeData();
        
        // 🔗 初始化视图
        initializeViews();
        
        // 🎬 初始化播放器
        initializePlayer();
        
        // 📊 加载媒体内容
        loadMediaContent();
    }
    
    // 播放URL
    private String videoUrl;

    /**
     * 📊 初始化数据
     */
    private void initializeData() {
        Intent intent = getIntent();
        videoUrl = intent.getStringExtra("video_url");
        mediaId = intent.getStringExtra("media_id");
        mediaTitle = intent.getStringExtra("media_title");
        mediaType = intent.getStringExtra("media_type");
        mediaYear = intent.getStringExtra("media_year");
        mediaGenre = intent.getStringExtra("media_genre");
        posterUrl = intent.getStringExtra("poster_url");
        
        // 播放相关GUID
        mediaGuid = intent.getStringExtra("media_guid");
        videoGuid = intent.getStringExtra("video_guid");
        audioGuid = intent.getStringExtra("audio_guid");
        
        // 🎯 episodeGuid用于获取原画流媒体信息
        this.episodeGuid = intent.getStringExtra("episode_guid");
        
        Log.d(TAG, "🎬 接收到episodeGuid: " + this.episodeGuid + "（用于原画播放）");
        
        // 兼容不同的参数名
        if (videoUrl == null) videoUrl = intent.getStringExtra("video_title"); // 有时候title字段可能包含URL
        if (mediaTitle == null) mediaTitle = intent.getStringExtra("video_title");
        
        if (mediaId == null) mediaId = "unknown";
        if (mediaTitle == null) mediaTitle = "未知标题";
        if (mediaType == null) mediaType = "unknown";
        if (mediaYear == null) mediaYear = "未知年份";
        if (mediaGenre == null) mediaGenre = "未知类型";
        
        // 初始化MediaManager
        mediaManager = new MediaManager(this);
        
        Log.d(TAG, "📊 媒体信息 - ID: " + mediaId + ", 标题: " + mediaTitle + ", 类型: " + mediaType);
        Log.d(TAG, "🎬 播放URL: " + videoUrl);
        Log.d(TAG, "🎬 媒体GUID: " + mediaGuid + ", 视频GUID: " + videoGuid);
    }
    
    /**
     * 🔗 初始化视图
     */
    private void initializeViews() {
        Log.d(TAG, "📱 初始化视图组件");
        
        // 绑定UI组件
        playerView = findViewById(R.id.player_view);
        posterImageView = findViewById(R.id.poster_image);
        titleText = findViewById(R.id.title_text);
        infoText = findViewById(R.id.info_text);
        loadingLayout = findViewById(R.id.loading_layout);
        errorLayout = findViewById(R.id.error_layout);
        errorText = findViewById(R.id.error_text);
        danmuContainer = findViewById(R.id.danmu_container);
        
        // 🎬 初始化弹幕管理器
        if (danmuContainer != null) {
            danmuManager = new DanmuManager(this, danmuContainer);
            Log.d(TAG, "✅ 弹幕管理器初始化完成");
        } else {
            Log.e(TAG, "❌ 弹幕容器未找到");
        }
        
        // 设置媒体信息
        titleText.setText(mediaTitle);
        infoText.setText(mediaYear + " · " + mediaGenre);
        
        // 初始状态：显示加载中
        showLoading("正在准备播放器...");
        
        Log.d(TAG, "✅ 视图组件初始化完成");
    }
    
    /**
     * 🎬 初始化播放器
     */
    private void initializePlayer() {
        Log.d(TAG, "🎬 初始化ExoPlayer");
        
        try {
            // 创建ExoPlayer实例
            exoPlayer = new ExoPlayer.Builder(this).build();
            
            // 绑定到PlayerView
            playerView.setPlayer(exoPlayer);
            
            // 设置播放器监听器
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    handlePlaybackStateChange(playbackState);
                }
                
                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    handlePlayerError(error);
                }
                
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    Log.d(TAG, "🎬 播放状态变化: " + (isPlaying ? "播放中" : "暂停"));
                    
                    // 🎬 根据播放状态启动或停止弹幕位置更新
                    if (isPlaying) {
                        startDanmuPositionUpdate();
                    } else {
                        stopDanmuPositionUpdate();
                    }
                }
            });
            
            // 设置播放器控制器
            playerView.setUseController(true);
            playerView.setControllerAutoShow(true);
            playerView.setControllerHideOnTouch(true);
            
            Log.d(TAG, "✅ ExoPlayer初始化完成");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ ExoPlayer初始化失败", e);
            showError("播放器初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 📊 加载媒体内容
     */
    private void loadMediaContent() {
        Log.d(TAG, "📊 开始加载媒体内容: " + mediaTitle);
        
        // 🎯 优先使用传递过来的真实播放URL
        if (videoUrl != null && !videoUrl.isEmpty()) {
            Log.d(TAG, "✅ 使用传递的播放URL: " + videoUrl);
            playMedia(videoUrl);
        } else {
            // 🚨 如果没有传递URL，使用测试视频（开发用）
            Log.w(TAG, "⚠️ 未接收到播放URL，使用测试视频");
            String testVideoUrl = getTestVideoUrl();
            if (testVideoUrl != null) {
                playMedia(testVideoUrl);
            } else {
                showError("暂无可用的播放源");
            }
        }
    }
    
    /**
     * 🎬 播放媒体
     */
    private void playMedia(String videoUrl) {
        Log.d(TAG, "🎬 开始播放媒体: " + videoUrl);
        
        try {
            showLoading("正在加载视频...");
            
            // 🚨 实现完整的播放流程：先调用play API，再播放视频
            if (mediaGuid != null && videoGuid != null) {
                Log.d(TAG, "🎬 开始完整播放流程：先激活播放会话");
                startPlaySession();
            } else {
                Log.w(TAG, "⚠️ 缺少播放GUID信息，使用简单播放");
                playVideoDirectly(videoUrl);
            }
            
            Log.d(TAG, "✅ 媒体播放开始");
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 媒体播放失败", e);
            showError("播放失败: " + e.getMessage());
        }
    }
    
    /**
     * 🔄 处理播放状态变化
     */
    private void handlePlaybackStateChange(int playbackState) {
        switch (playbackState) {
            case Player.STATE_IDLE:
                Log.d(TAG, "🔄 播放器状态: IDLE");
                break;
            case Player.STATE_BUFFERING:
                Log.d(TAG, "🔄 播放器状态: BUFFERING");
                showLoading("缓冲中...");
                break;
            case Player.STATE_READY:
                Log.d(TAG, "🔄 播放器状态: READY");
                isPlayerReady = true;
                showPlayer();
                break;
            case Player.STATE_ENDED:
                Log.d(TAG, "🔄 播放器状态: ENDED");
                Toast.makeText(this, "播放完成", Toast.LENGTH_SHORT).show();
                finish();
                break;
        }
    }
    
    // 🔄 重试相关变量
    private int playbackRetryCount = 0;
    private static final int MAX_RETRY_COUNT = 2;
    
    /**
     * ❌ 处理播放器错误
     */
    private void handlePlayerError(androidx.media3.common.PlaybackException error) {
        Log.e(TAG, "❌ 播放器错误", error);
        
        // 🔄 检查是否为HTTP 410错误并尝试重试
        if (shouldRetryPlayback(error) && playbackRetryCount < MAX_RETRY_COUNT) {
            playbackRetryCount++;
            Log.w(TAG, "🔄 检测到HTTP 410错误，尝试重试播放 (第" + playbackRetryCount + "次)");
            
            // 延迟2秒后重试
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d(TAG, "🔄 开始重试播放流程");
                retryPlayback();
            }, 2000);
            
            showLoading("播放链接已过期，正在重新获取... (第" + playbackRetryCount + "次重试)");
            return;
        }
        
        isPlayerError = true;
        playbackRetryCount = 0; // 重置重试计数
        
        // 🔧 更详细的错误信息 - 修复优先级，先检查可重试错误
        String errorMessage = "播放错误: " + error.getMessage();
        if (error.getMessage() != null && error.getMessage().contains("Response code: 410")) {
            errorMessage = "播放链接已失效，请重新选择视频";
        } else if (error.getMessage() != null && error.getMessage().contains("NO_EXCEEDS_CAPABILITIES")) {
            errorMessage = "设备不支持该视频格式，建议使用其他播放器";
        } else if (error.getCause() instanceof IOException) {
            errorMessage = "网络连接错误，请检查网络设置";
        }
        
        showError(errorMessage);
    }
    
    /**
     * 🔄 判断是否应该重试播放
     */
    private boolean shouldRetryPlayback(androidx.media3.common.PlaybackException error) {
        if (error == null) return false;
        
        // 🔧 增强检测：检查错误消息和异常原因
        String errorMsg = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
        
        // 检查主要错误消息中的关键词
        boolean hasRetryableError = errorMsg.contains("response code: 410") || 
                                   errorMsg.contains("http 410") ||
                                   errorMsg.contains("gone") ||
                                   errorMsg.contains("not found");
        
        // 🔧 检查异常堆栈中的HTTP 410错误
        if (!hasRetryableError && error.getCause() != null) {
            Throwable cause = error.getCause();
            String causeMsg = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
            hasRetryableError = causeMsg.contains("response code: 410") ||
                               causeMsg.contains("invalidresponsecodexception");
        }
        
        Log.d(TAG, "🔄 重试检测: errorMsg='" + errorMsg + "', hasRetryableError=" + hasRetryableError);
        return hasRetryableError;
    }
    
    /**
     * 🔄 重试播放
     */
    private void retryPlayback() {
        Log.d(TAG, "🔄 开始重试播放流程");
        
        // 重置播放错误状态
        isPlayerError = false;
        
        // 🔧 简单的重试策略：重置播放器并使用原始URL重新播放
        Log.d(TAG, "🔄 重置播放器状态并重试");
        showLoading("重试播放中...");
        
        // 延迟1秒后开始重试
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (videoUrl != null && !videoUrl.isEmpty()) {
                    Log.d(TAG, "🔄 使用原始URL重试: " + videoUrl);
                    
                    // 🔧 完全重置播放器
                    if (exoPlayer != null) {
                        exoPlayer.stop();
                        exoPlayer.clearMediaItems();
                        
                        // 🔧 重新设置播放源并开始播放 - 添加认证头
                        MediaItem mediaItem = createMediaItemWithHeaders(videoUrl);
                        if (mediaItem != null) {
                            exoPlayer.setMediaItem(mediaItem);
                        }
                        // 如果mediaItem为null，说明已经通过setMediaSource设置了
                        exoPlayer.prepare();
                        exoPlayer.play();
                        
                        Log.d(TAG, "✅ 重试播放已开始");
                    }
                } else {
                    Log.e(TAG, "❌ 重试失败：没有可用的播放URL");
                    showError("重试失败：没有播放地址");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ 重试流程异常", e);
                showError("重试失败: " + e.getMessage());
            }
        }, 1000);
    }
    
    /**
     * 🔄 使用新的流媒体数据重试播放
     */
    private void retryWithNewStreamData(StreamListResponse.StreamData streamData) {
        // 此方法暂时不使用，保留作为未来扩展
        Log.d(TAG, "🔄 retryWithNewStreamData method (reserved for future use)");
    }
    
    /**
     * 🔄 显示加载状态
     */
    private void showLoading(String message) {
        runOnUiThread(() -> {
            loadingLayout.setVisibility(View.VISIBLE);
            playerView.setVisibility(View.GONE);
            errorLayout.setVisibility(View.GONE);
            posterImageView.setVisibility(View.VISIBLE);
            
            TextView loadingText = loadingLayout.findViewById(R.id.loading_text);
            if (loadingText != null) {
                loadingText.setText(message);
            }
            
            Log.d(TAG, "🔄 显示加载状态: " + message);
        });
    }
    
    /**
     * 🎬 显示播放器
     */
    private void showPlayer() {
        runOnUiThread(() -> {
            loadingLayout.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
            errorLayout.setVisibility(View.GONE);
            posterImageView.setVisibility(View.GONE);
            
            Log.d(TAG, "🎬 显示播放器界面");
        });
    }
    
    /**
     * ❌ 显示错误状态
     */
    private void showError(String message) {
        runOnUiThread(() -> {
            loadingLayout.setVisibility(View.GONE);
            playerView.setVisibility(View.GONE);
            errorLayout.setVisibility(View.VISIBLE);
            posterImageView.setVisibility(View.VISIBLE);
            
            errorText.setText(message);
            
            Log.e(TAG, "❌ 显示错误状态: " + message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }
    
    /**
     * 🎬 开始播放会话（完整流程 - 原画模式）
     */
    private void startPlaySession() {
        Log.d(TAG, "🎬 步骤1：获取流媒体信息以实现原画播放");
        showLoading("正在准备原画播放...");
        
        // 🎯 使用从Intent传递的真实GUID（每个剧集都不同）
        Log.d(TAG, "🎬 使用剧集专属GUID: media=" + mediaGuid + ", video=" + videoGuid + ", audio=" + audioGuid);
        Log.d(TAG, "🎬 使用episodeGuid获取原画信息: " + episodeGuid);
        
        // 🚨 检查GUID是否有效
        if (mediaGuid == null || videoGuid == null || episodeGuid == null) {
            Log.e(TAG, "❌ GUID信息不完整，无法调用原画播放API");
            Log.w(TAG, "🔄 回退到兼容播放模式");
            fallbackToCompatiblePlay();
            return;
        }
        
        if (mediaGuid.equals(videoGuid) && videoGuid.equals(audioGuid)) {
            Log.w(TAG, "⚠️ 检测到相同的GUID，这可能导致play API失败");
            Log.w(TAG, "🔄 回退到简单播放模式");
            playVideoDirectly(videoUrl);
            return;
        }
        
        // 🎯 第1步：获取流媒体列表以获取原画信息
        mediaManager.getStreamList(episodeGuid, new MediaManager.MediaCallback<StreamListResponse.StreamData>() {
            @Override
            public void onSuccess(StreamListResponse.StreamData streamData) {
                Log.d(TAG, "✅ [原画播放] 流媒体信息获取成功");
                Log.d(TAG, "🎬 步骤2：调用原画播放API激活会话");
                showLoading("正在激活原画播放会话...");
                
                // 🎯 第2步：使用流媒体信息调用原画播放API
                mediaManager.callPlayApiWithStreamData(mediaGuid, videoGuid, audioGuid, streamData, new MediaManager.PlaySessionCallback() {
                    @Override
                    public void onPlaySessionSuccess(String playUrl, PlayApiResponse.PlaySessionData sessionData) {
                        Log.d(TAG, "✅ [原画播放] 会话激活成功，开始播放: " + playUrl);
                        runOnUiThread(() -> {
                            // 使用从原画play API获得的真实播放URL
                            playVideoDirectly(playUrl);
                        });
                    }

                    @Override
                    public void onPlaySessionError(String errorMessage) {
                        Log.e(TAG, "❌ [原画播放] 会话激活失败: " + errorMessage);
                        Log.w(TAG, "🔄 回退到兼容播放模式");
                        runOnUiThread(() -> {
                            // 如果原画play API失败，回退到兼容播放
                            fallbackToCompatiblePlay();
                        });
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ [原画播放] 流媒体信息获取失败: " + error);
                Log.w(TAG, "🔄 回退到兼容播放模式");
                runOnUiThread(() -> {
                    fallbackToCompatiblePlay();
                });
            }
        });
    }
    
    /**
     * 🔄 回退到兼容播放模式
     */
    private void fallbackToCompatiblePlay() {
        if (mediaGuid != null && videoGuid != null && audioGuid != null) {
            Log.d(TAG, "🔄 使用兼容播放API");
            showLoading("正在准备兼容播放...");
            
            // 使用原有的播放API（720p）
            mediaManager.callPlayApi(mediaGuid, videoGuid, audioGuid, new MediaManager.PlaySessionCallback() {
                @Override
                public void onPlaySessionSuccess(String playUrl, PlayApiResponse.PlaySessionData sessionData) {
                    Log.d(TAG, "✅ [兼容播放] 会话激活成功，开始播放: " + playUrl);
                    runOnUiThread(() -> {
                        playVideoDirectly(playUrl);
                    });
                }

                @Override
                public void onPlaySessionError(String errorMessage) {
                    Log.e(TAG, "❌ [兼容播放] 会话激活失败: " + errorMessage);
                    runOnUiThread(() -> {
                        // 最终回退：直接播放
                        Log.w(TAG, "🔄 最终回退到简单播放模式");
                        playVideoDirectly(videoUrl);
                    });
                }
            });
        } else {
            // 最终回退：直接播放
            Log.w(TAG, "🔄 最终回退到简单播放模式");
            playVideoDirectly(videoUrl);
        }
    }
    
    /**
     * 🎬 直接播放视频（简化版本）
     */
    private void playVideoDirectly(String url) {
        Log.d(TAG, "🎬 步骤2：直接播放视频: " + url);
        
        try {
            // 创建媒体项目和播放 - 添加认证头
            MediaItem mediaItem = createMediaItemWithHeaders(url);
            if (mediaItem != null) {
                exoPlayer.setMediaItem(mediaItem);
            }
            // 如果mediaItem为null，说明已经通过setMediaSource设置了
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);
            
            Log.d(TAG, "✅ 直接播放开始");
            
            // 🎬 播放开始后，异步加载弹幕数据
            loadDanmuData();
            
            // 🧪 创建多个测试弹幕（调试用）
            createTestDanmuSequence();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 直接播放失败", e);
            showError("播放失败: " + e.getMessage());
        }
    }
    
    /**
     * 🎬 加载弹幕数据
     */
    private void loadDanmuData() {
        if (danmuManager == null) {
            Log.w(TAG, "⚠️ 弹幕管理器未初始化，跳过弹幕加载");
            return;
        }
        
        Log.d(TAG, "🎬 开始加载弹幕数据");
        
        // 🚧 TODO: 从Intent或MediaDetailActivity获取完整的弹幕参数
        // 现在使用示例参数进行测试
        String doubanId = "36449461";  // 示例豆瓣ID
        int episodeNumber = 1;         // 示例集数
        String episodeTitle = "测试集"; // 示例集标题
        String title = mediaTitle != null ? mediaTitle : "测试剧集";
        int seasonNumber = 1;          // 示例季数
        String parentGuid = mediaGuid; // 使用media GUID作为parent GUID
        
        Log.d(TAG, "📊 弹幕参数: 豆瓣ID=" + doubanId + ", 集数=" + episodeNumber + ", 标题=" + title);
        
        mediaManager.getDanmu(doubanId, episodeNumber, episodeTitle, title,
                            seasonNumber, episodeGuid != null ? episodeGuid : mediaGuid, 
                            parentGuid, new MediaManager.MediaCallback<List<Danmu>>() {
            @Override
            public void onSuccess(List<Danmu> danmuList) {
                Log.d(TAG, "✅ 弹幕数据加载成功: " + danmuList.size() + "条");
                
                runOnUiThread(() -> {
                    if (danmuManager != null && danmuList != null && !danmuList.isEmpty()) {
                        danmuManager.loadDanmuList(danmuList);
                        Log.d(TAG, "🎬 弹幕数据已加载到管理器");
                    } else {
                        Log.w(TAG, "⚠️ 弹幕数据为空或管理器已释放");
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 弹幕数据加载失败: " + error);
                // 弹幕加载失败不影响视频播放，只记录日志
            }
        });
    }
    
    /**
     * 🧪 创建测试弹幕序列
     */
    private void createTestDanmuSequence() {
        if (danmuManager == null) {
            Log.e(TAG, "❌ 弹幕管理器为null，无法创建测试弹幕");
            return;
        }
        
        Log.d(TAG, "🧪 开始创建测试弹幕序列");
        
        // 创建多条测试弹幕数据
        List<Danmu> testDanmuList = new ArrayList<>();
        
        // 立即显示的弹幕
        testDanmuList.add(new Danmu("🧪立即显示测试弹幕🧪", 0, "#FF0000", Danmu.Mode.SCROLL));
        testDanmuList.add(new Danmu("🎨第二条红色弹幕🎨", 2, "#FF0000", Danmu.Mode.SCROLL)); 
        testDanmuList.add(new Danmu("🎯第三条蓝色弹幕🎯", 4, "#0000FF", Danmu.Mode.SCROLL));
        testDanmuList.add(new Danmu("💚第四条绿色弹幕💚", 6, "#00FF00", Danmu.Mode.SCROLL));
        testDanmuList.add(new Danmu("💛第五条黄色弹幕💛", 8, "#FFFF00", Danmu.Mode.SCROLL));
        testDanmuList.add(new Danmu("🌈第六条紫色弹幕🌈", 10, "#FF00FF", Danmu.Mode.SCROLL));
        testDanmuList.add(new Danmu("⭐第七条橙色弹幕⭐", 12, "#FF8000", Danmu.Mode.SCROLL));
        testDanmuList.add(new Danmu("🎵第八条白色弹幕🎵", 14, "#FFFFFF", Danmu.Mode.SCROLL));
        
        // 加载测试弹幕
        danmuManager.loadDanmuList(testDanmuList);
        Log.d(TAG, "✅ 测试弹幕数据已加载: " + testDanmuList.size() + "条");
        
        // 立即显示第一条测试弹幕
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (danmuManager != null) {
                Log.d(TAG, "🧪 1秒后强制显示测试弹幕");
                danmuManager.showTestDanmu();
            }
        }, 1000);
        
        // 定期显示更多测试弹幕
        for (int i = 0; i < 5; i++) {
            final int index = i;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (danmuManager != null) {
                    String testText = "🚀连续测试弹幕#" + (index + 1) + "🚀";
                    Danmu testDanmu = new Danmu(testText, 0, "#FF" + String.format("%02X", (index * 50) % 256) + "00", Danmu.Mode.SCROLL);
                    Log.d(TAG, "🧪 显示连续测试弹幕: " + testText);
                    showSingleTestDanmu(testDanmu, index);
                }
            }, 2000 + index * 1000); // 每秒显示一条
        }
    }
    
    /**
     * 🧪 显示单个测试弹幕
     */
    private void showSingleTestDanmu(Danmu danmu, int track) {
        if (danmuContainer == null || danmuManager == null) {
            return;
        }
        
        Log.d(TAG, "🧪 强制显示单个测试弹幕: " + danmu.getText() + ", 通道: " + track);
        
        // 创建弹幕视图
        TextView testView = new TextView(this);
        testView.setText(danmu.getText());
        testView.setTextColor(Color.parseColor(danmu.getColor()));
        testView.setTextSize(24); // 更大的字体
        testView.setTypeface(Typeface.DEFAULT_BOLD);
        testView.setSingleLine(true);
        
        // 非常明显的背景
        testView.setBackgroundColor(Color.argb(150, 0, 0, 0)); // 半透明黑色背景
        testView.setPadding(16, 8, 16, 8);
        
        // 设置位置 - 确保在屏幕内
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        
        int topMargin = 100 + track * 80; // 每条弹幕间隔80px
        params.setMargins(danmuContainer.getWidth() - 50, topMargin, 0, 0); // 从右边开始
        params.gravity = Gravity.TOP | Gravity.START;
        
        testView.setLayoutParams(params);
        
        // 添加到容器
        danmuContainer.addView(testView);
        Log.d(TAG, "✅ 测试弹幕已添加，当前子视图数: " + danmuContainer.getChildCount());
        
        // 简单的移动动画
        ObjectAnimator animator = ObjectAnimator.ofFloat(
            testView, "translationX", 0, -danmuContainer.getWidth() - 200);
        animator.setDuration(5000); // 5秒滚动
        animator.start();
        
        Log.d(TAG, "🎭 测试弹幕动画已开始: " + danmu.getText());
    }
    
    /**
     * 🎭 获取测试视频URL
     */
    private String getTestVideoUrl() {
        // 返回一些公开的测试视频URL
        return "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
    }
    
    /**
     * 🎮 处理遥控器按键事件
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (exoPlayer != null) {
                    if (exoPlayer.isPlaying()) {
                        exoPlayer.pause();
                        Toast.makeText(this, "暂停", Toast.LENGTH_SHORT).show();
                    } else {
                        exoPlayer.play();
                        Toast.makeText(this, "播放", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                Log.d(TAG, "⬅️ 用户按下返回键");
                finish();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    /**
     * 🎬 启动弹幕位置更新定时器
     */
    private void startDanmuPositionUpdate() {
        if (danmuManager == null || exoPlayer == null) {
            return;
        }
        
        // 停止之前的定时器
        stopDanmuPositionUpdate();
        
        // 创建新的定时任务
        danmuUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null && danmuManager != null && isPlayerReady) {
                    try {
                        long currentPosition = exoPlayer.getCurrentPosition();
                        danmuManager.updatePosition(currentPosition);
                        
                        // 每500毫秒更新一次弹幕位置
                        danmuUpdateHandler.postDelayed(this, 500);
                    } catch (Exception e) {
                        Log.e(TAG, "❌ 弹幕位置更新失败", e);
                    }
                }
            }
        };
        
        // 启动定时器
        danmuUpdateHandler.post(danmuUpdateRunnable);
        Log.d(TAG, "✅ 弹幕位置更新定时器已启动");
    }
    
    /**
     * 🎬 停止弹幕位置更新定时器
     */
    private void stopDanmuPositionUpdate() {
        if (danmuUpdateRunnable != null) {
            danmuUpdateHandler.removeCallbacks(danmuUpdateRunnable);
            danmuUpdateRunnable = null;
            Log.d(TAG, "🛑 弹幕位置更新定时器已停止");
        }
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "📱 Activity onStart");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "📱 Activity onResume");
        
        if (exoPlayer != null && isPlayerReady && !isPlayerError) {
            exoPlayer.setPlayWhenReady(true);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "📱 Activity onPause");
        
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "📱 Activity onStop");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "📱 Activity onDestroy");
        
        // 释放播放器资源
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        
        // 🎬 清理弹幕相关资源
        stopDanmuPositionUpdate();
        if (danmuManager != null) {
            danmuManager.reset();
            danmuManager = null;
        }
    }
    
    /**
     * 🔐 创建带认证头的MediaItem
     */
    private MediaItem createMediaItemWithHeaders(String url) {
        try {
            // 获取认证token - 使用静态方法
            String token = SharedPreferencesManager.getAuthToken();
            
            Log.d(TAG, "🔐 为ExoPlayer添加认证头");
            
            if (token != null && !token.isEmpty()) {
                Log.d(TAG, "✅ 找到认证token，长度: " + token.length());
                
                // 🔧 创建自定义DataSource工厂，使用完整认证（Cookie + authx签名）
                Map<String, String> headers = new HashMap<>();
                headers.put("Accept", "*/*");
                headers.put("Accept-Language", "zh-CN,zh;q=0.9");
                headers.put("Connection", "keep-alive");
                headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36");
                headers.put("Sec-Fetch-Dest", "empty");
                headers.put("Sec-Fetch-Mode", "cors");
                headers.put("Sec-Fetch-Site", "same-origin");
                
                // 🔑 关键修复：使用Cookie认证
                headers.put("Cookie", "authorization=" + token);
                
                // 🔑 关键修复：添加authx签名（视频播放也需要签名）
                try {
                    // 构造一个假的GET请求来生成签名
                    okhttp3.Request fakeRequest = new okhttp3.Request.Builder().url(url).get().build();
                    String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature(fakeRequest);
                    if (signature != null) {
                        headers.put("authx", signature);
                        Log.d(TAG, "🔑 为视频播放添加authx签名: " + signature.substring(0, Math.min(signature.length(), 20)) + "...");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "⚠️ 视频播放签名生成失败，继续无签名播放", e);
                }
                
                Log.d(TAG, "🍪 使用完整认证: Cookie+authx签名");
                
                // 创建带Cookie认证的HttpDataSource工厂
                DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(headers)
                    .setUserAgent("Mozilla/5.0 (Linux; Android 11; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
                    .setConnectTimeoutMs(10000)
                    .setReadTimeoutMs(10000);
                
                // 创建MediaSource而不是直接使用MediaItem
                MediaSource mediaSource = new ProgressiveMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url));
                
                // 清空现有媒体项并设置MediaSource
                exoPlayer.setMediaSource(mediaSource);
                
                Log.d(TAG, "✅ 创建带完整认证的MediaSource成功");
                return null; // 返回null因为我们直接设置了MediaSource
            } else {
                Log.w(TAG, "⚠️ 未找到认证token，使用无认证头的MediaItem");
                return MediaItem.fromUri(url);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ 创建认证MediaItem失败，回退到普通MediaItem", e);
            return MediaItem.fromUri(url);
        }
    }
}
