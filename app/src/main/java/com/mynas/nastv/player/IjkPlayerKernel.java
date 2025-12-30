package com.mynas.nastv.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.mynas.nastv.cache.OkHttpProxyCacheManager;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.builder.GSYVideoOptionBuilder;
import com.shuyu.gsyvideoplayer.listener.GSYVideoProgressListener;
import com.shuyu.gsyvideoplayer.listener.VideoAllCallBack;
import com.shuyu.gsyvideoplayer.utils.GSYVideoType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo;

/**
 * IJKPlayer 内核
 * 封装 GSYVideoPlayer + IJKPlayer 的所有操作
 * 提供统一的播放器接口
 */
public class IjkPlayerKernel implements PlayerKernel {
    private static final String TAG = "IjkPlayerKernel";
    
    private Context context;
    private com.mynas.nastv.player.NoLoadingGSYVideoPlayer playerView;
    private IjkMediaPlayer currentIjkPlayer;
    
    // 回调接口
    private PlayerKernel.PlayerCallback playerCallback;
    private PlayerKernel.SubtitleCallback subtitleCallback;
    
    // 状态
    private boolean isPrepared = false;
    private int videoWidth = 0;
    private int videoHeight = 0;
    
    // 解码器配置
    private boolean forceUseSoftwareDecoder = false;
    private PlayerSettingsHelper settingsHelper;
    
    public IjkPlayerKernel(Context context, com.mynas.nastv.player.NoLoadingGSYVideoPlayer playerView) {
        this.context = context.getApplicationContext();
        this.playerView = playerView;
        this.settingsHelper = new PlayerSettingsHelper(context);
    }
    
    @Override
    public void init(Map<String, String> headers) {
        Log.d(TAG, "初始化 IJKPlayer 内核");
        
        // 设置视频渲染类型为 TEXTURE
        GSYVideoType.setRenderType(GSYVideoType.TEXTURE);
        GSYVideoType.setShowType(GSYVideoType.SCREEN_TYPE_DEFAULT);
        
        // 配置解码器
        configureDecoder();
        
        // 设置播放器初始化成功监听器
        GSYVideoManager.instance().setPlayerInitSuccessListener((player, model) -> {
            Log.d(TAG, "播放器初始化成功，类型: " + player.getClass().getSimpleName());
            
            if (player instanceof IjkMediaPlayer) {
                currentIjkPlayer = (IjkMediaPlayer) player;
                
                // 设置字幕监听器
                Log.d(TAG, "设置 IJKPlayer OnTimedTextListener");
                currentIjkPlayer.setOnTimedTextListener((mp, text) -> {
                    if (text != null && text.getText() != null) {
                        String subtitleText = text.getText().toString();
                        if (!subtitleText.isEmpty() && subtitleCallback != null) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                subtitleCallback.onTimedText(subtitleText);
                            });
                        }
                    }
                });
            }
        });
        
        // 配置播放器选项
        GSYVideoOptionBuilder gsyVideoOptionBuilder = new GSYVideoOptionBuilder();
        gsyVideoOptionBuilder
                .setIsTouchWiget(false)
                .setRotateViewAuto(false)
                .setLockLand(false)
                .setShowFullAnimation(false)
                .setNeedLockFull(true)
                .setNeedShowWifiTip(false)
                .setDismissControlTime(0)
                .setHideKey(true)
                .setCacheWithPlay(false)
                .setVideoAllCallBack(new VideoAllCallBack() {
                    @Override
                    public void onPrepared(String url, Object... objects) {
                        Log.d(TAG, "IJKPlayer onPrepared");
                        isPrepared = true;
                        if (playerCallback != null) {
                            playerCallback.onPrepared();
                        }
                    }
                    
                    @Override
                    public void onPlayError(String url, Object... objects) {
                        String errorMsg = objects.length > 0 ? objects[0].toString() : "未知错误";
                        Log.e(TAG, "IJKPlayer onPlayError: " + errorMsg);
                        if (playerCallback != null) {
                            playerCallback.onError(errorMsg);
                        }
                    }
                    
                    @Override
                    public void onAutoComplete(String url, Object... objects) {
                        Log.d(TAG, "IJKPlayer onAutoComplete");
                        if (playerCallback != null) {
                            playerCallback.onCompletion();
                        }
                    }
                    
                    @Override
                    public void onStartPrepared(String url, Object... objects) {
                        // 隐藏内置控制栏
                        if (playerView != null) {
                            playerView.getBackButton().setVisibility(View.GONE);
                            playerView.getFullscreenButton().setVisibility(View.GONE);
                            playerView.getStartButton().setVisibility(View.GONE);
                        }
                    }
                    
                    @Override
                    public void onClickResume(String url, Object... objects) {
                        // 恢复播放
                    }
                    
                    @Override
                    public void onClickBlank(String url, Object... objects) {
                        // 点击空白区域
                    }
                    
                    // 其他回调方法留空
                    @Override public void onClickStartError(String url, Object... objects) {}
                    @Override public void onClickStop(String url, Object... objects) {}
                    @Override public void onClickStopFullscreen(String url, Object... objects) {}
                    @Override public void onClickResumeFullscreen(String url, Object... objects) {}
                    @Override public void onClickSeekbar(String url, Object... objects) {}
                    @Override public void onClickSeekbarFullscreen(String url, Object... objects) {}
                    @Override public void onClickStartThumb(String url, Object... objects) {}
                    @Override public void onEnterFullscreen(String url, Object... objects) {}
                    @Override public void onQuitFullscreen(String url, Object... objects) {}
                    @Override public void onQuitSmallWidget(String url, Object... objects) {}
                    @Override public void onEnterSmallWidget(String url, Object... objects) {}
                    @Override public void onTouchScreenSeekVolume(String url, Object... objects) {}
                    @Override public void onTouchScreenSeekPosition(String url, Object... objects) {}
                    @Override public void onTouchScreenSeekLight(String url, Object... objects) {}
                    @Override public void onClickBlankFullscreen(String url, Object... objects) {}
                    @Override public void onComplete(String url, Object... objects) {}
                    @Override public void onClickStartIcon(String url, Object... objects) {}
                })
                .setGSYVideoProgressListener(new GSYVideoProgressListener() {
                    @Override
                    public void onProgress(long progress, long secProgress, long currentPosition, long totalDuration) {
                        // 进度更新（由外部处理）
                    }
                })
                .setGSYStateUiListener(state -> {
                    // CURRENT_STATE_PLAYING_BUFFERING_START = 3
                    if (state == 3) {
                        if (playerCallback != null) {
                            playerCallback.onBuffering(true);
                        }
                    } else if (state == 2) {
                        // CURRENT_STATE_PLAYING = 2
                        if (playerCallback != null) {
                            playerCallback.onBuffering(false);
                        }
                    }
                });
        
        gsyVideoOptionBuilder.build(playerView);
    }
    
    @Override
    public void setSurface(android.view.Surface surface) {
        // IJKPlayer 通过 GSYVideoPlayer 管理 Surface
    }
    
    @Override
    public void setTextureView(android.view.TextureView textureView) {
        // IJKPlayer 通过 GSYVideoPlayer 管理 TextureView
    }
    
    @Override
    public void play(String url) {
        if (playerView == null) {
            Log.e(TAG, "PlayerView 未初始化");
            return;
        }
        
        Log.d(TAG, "播放视频: " + url.substring(0, Math.min(80, url.length())));
        
        Map<String, String> headers = new HashMap<>();
        playerView.setUp(url, false, null, headers, "视频");
        
        if (headers != null && !headers.isEmpty()) {
            playerView.setMapHeadData(headers);
        }
        
        playerView.startPlayLogic();
    }
    
    @Override
    public void playWithProxyCache(String originUrl, Map<String, String> headers, File cacheDir) {
        if (playerView == null) {
            Log.e(TAG, "PlayerView 未初始化");
            return;
        }
        
        Log.d(TAG, "使用代理缓存播放: " + originUrl.substring(0, Math.min(80, originUrl.length())));
        
        // 设置 OkHttpProxyCacheManager 的 headers
        OkHttpProxyCacheManager.setCurrentHeaders(headers);
        
        if (cacheDir != null) {
            playerView.setUp(originUrl, true, cacheDir, headers, "视频");
        } else {
            playerView.setUp(originUrl, false, null, headers, "视频");
        }
        
        if (headers != null && !headers.isEmpty()) {
            playerView.setMapHeadData(headers);
        }
        
        playerView.startPlayLogic();
    }
    
    @Override
    public void start() {
        if (playerView != null) {
            playerView.onVideoResume();
        }
    }
    
    @Override
    public void pause() {
        if (playerView != null) {
            playerView.onVideoPause();
        }
    }
    
    @Override
    public void stop() {
        if (playerView != null) {
            playerView.release();
        }
    }
    
    @Override
    public void seekTo(long positionMs) {
        if (playerView != null) {
            playerView.seekTo(positionMs);
        }
    }
    
    @Override
    public long getCurrentPosition() {
        if (playerView != null) {
            try {
                int state = playerView.getCurrentState();
                if (state == 2) { // STATE_PLAYING
                    return playerView.getCurrentPositionWhenPlaying();
                }
            } catch (Exception e) {
                Log.w(TAG, "获取播放位置失败", e);
            }
        }
        return 0;
    }
    
    @Override
    public long getDuration() {
        if (playerView != null) {
            return playerView.getDuration();
        }
        return 0;
    }
    
    @Override
    public boolean isPlaying() {
        if (playerView != null) {
            try {
                int state = playerView.getCurrentState();
                return state == 2; // STATE_PLAYING
            } catch (Exception e) {
                Log.w(TAG, "检查播放状态失败", e);
            }
        }
        return false;
    }
    
    @Override
    public void setSpeed(float speed) {
        if (playerView != null && speed > 0) {
            playerView.setSpeed(speed);
        }
    }
    
    @Override
    public void setVolume(float volume) {
        // IJKPlayer 通过 GSYVideoPlayer 管理音量
        // 如果需要，可以在这里实现
    }
    
    @Override
    public int getVideoWidth() {
        return videoWidth;
    }
    
    @Override
    public int getVideoHeight() {
        return videoHeight;
    }
    
    @Override
    public View getPlayerView() {
        return playerView;
    }
    
    @Override
    public void setPlayerCallback(PlayerKernel.PlayerCallback callback) {
        this.playerCallback = callback;
    }
    
    @Override
    public void setSubtitleCallback(PlayerKernel.SubtitleCallback callback) {
        this.subtitleCallback = callback;
    }
    
    @Override
    public void selectAudioTrack(int trackIndex) {
        try {
            com.shuyu.gsyvideoplayer.player.IPlayerManager playerManager =
                    GSYVideoManager.instance().getPlayer();
            
            if (playerManager instanceof com.shuyu.gsyvideoplayer.player.IjkPlayerManager) {
                com.shuyu.gsyvideoplayer.player.IjkPlayerManager ijkManager =
                        (com.shuyu.gsyvideoplayer.player.IjkPlayerManager) playerManager;
                
                IjkTrackInfo[] trackInfos = ijkManager.getTrackInfo();
                if (trackInfos == null || trackInfos.length == 0) {
                    return;
                }
                
                // 查找音频轨道
                List<Integer> audioTrackIndices = new ArrayList<>();
                for (int i = 0; i < trackInfos.length; i++) {
                    if (trackInfos[i].getTrackType() == 2) { // MEDIA_TRACK_TYPE_AUDIO = 2
                        audioTrackIndices.add(i);
                    }
                }
                
                if (trackIndex >= 0 && trackIndex < audioTrackIndices.size()) {
                    int actualIndex = audioTrackIndices.get(trackIndex);
                    ijkManager.selectTrack(actualIndex);
                    Log.d(TAG, "选择音频轨道: " + actualIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "选择音频轨道失败", e);
        }
    }
    
    @Override
    public void selectSubtitleTrack(int trackIndex) {
        try {
            com.shuyu.gsyvideoplayer.player.IPlayerManager playerManager =
                    GSYVideoManager.instance().getPlayer();
            
            if (playerManager instanceof com.shuyu.gsyvideoplayer.player.IjkPlayerManager) {
                com.shuyu.gsyvideoplayer.player.IjkPlayerManager ijkManager =
                        (com.shuyu.gsyvideoplayer.player.IjkPlayerManager) playerManager;
                
                IjkTrackInfo[] trackInfos = ijkManager.getTrackInfo();
                if (trackInfos == null || trackInfos.length == 0) {
                    return;
                }
                
                // 查找字幕轨道
                int textTrackCount = 0;
                for (int i = 0; i < trackInfos.length; i++) {
                    int trackType = trackInfos[i].getTrackType();
                    if (trackType == 3 || trackType == 4) { // TIMEDTEXT or SUBTITLE
                        if (textTrackCount == trackIndex) {
                            ijkManager.selectTrack(i);
                            Log.d(TAG, "选择字幕轨道: " + i);
                            return;
                        }
                        textTrackCount++;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "选择字幕轨道失败", e);
        }
    }
    
    @Override
    public List<TrackInfo> getAudioTracks() {
        List<TrackInfo> tracks = new ArrayList<>();
        try {
            com.shuyu.gsyvideoplayer.player.IPlayerManager playerManager =
                    GSYVideoManager.instance().getPlayer();
            
            if (playerManager instanceof com.shuyu.gsyvideoplayer.player.IjkPlayerManager) {
                com.shuyu.gsyvideoplayer.player.IjkPlayerManager ijkManager =
                        (com.shuyu.gsyvideoplayer.player.IjkPlayerManager) playerManager;
                
                IjkTrackInfo[] trackInfos = ijkManager.getTrackInfo();
                if (trackInfos == null) return tracks;
                
                int audioIndex = 0;
                for (int i = 0; i < trackInfos.length; i++) {
                    if (trackInfos[i].getTrackType() == 2) { // MEDIA_TRACK_TYPE_AUDIO = 2
                        tv.danmaku.ijk.media.player.misc.IMediaFormat format = trackInfos[i].getFormat();
                        String formatStr = format != null ? format.toString() : "";
                        tracks.add(new TrackInfo(audioIndex, trackInfos[i].getLanguage(), 
                                null, formatStr));
                        audioIndex++;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取音频轨道失败", e);
        }
        return tracks;
    }
    
    @Override
    public List<TrackInfo> getSubtitleTracks() {
        List<TrackInfo> tracks = new ArrayList<>();
        try {
            com.shuyu.gsyvideoplayer.player.IPlayerManager playerManager =
                    GSYVideoManager.instance().getPlayer();
            
            if (playerManager instanceof com.shuyu.gsyvideoplayer.player.IjkPlayerManager) {
                com.shuyu.gsyvideoplayer.player.IjkPlayerManager ijkManager =
                        (com.shuyu.gsyvideoplayer.player.IjkPlayerManager) playerManager;
                
                IjkTrackInfo[] trackInfos = ijkManager.getTrackInfo();
                if (trackInfos == null) return tracks;
                
                int subtitleIndex = 0;
                for (int i = 0; i < trackInfos.length; i++) {
                    int trackType = trackInfos[i].getTrackType();
                    if (trackType == 3 || trackType == 4) { // TIMEDTEXT or SUBTITLE
                        tracks.add(new TrackInfo(subtitleIndex, trackInfos[i].getLanguage(), 
                                null, null));
                        subtitleIndex++;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取字幕轨道失败", e);
        }
        return tracks;
    }
    
    @Override
    public void release() {
        Log.d(TAG, "释放 IJKPlayer 内核");
        if (playerView != null) {
            playerView.release();
        }
        currentIjkPlayer = null;
        isPrepared = false;
    }
    
    @Override
    public void reset() {
        if (playerView != null) {
            playerView.release();
        }
        isPrepared = false;
    }
    
    /**
     * 配置解码器
     */
    private void configureDecoder() {
        if (settingsHelper != null) {
            settingsHelper.setForceUseSoftwareDecoder(forceUseSoftwareDecoder);
            settingsHelper.configureDecoder();
        }
    }
    
    /**
     * 设置强制使用软解
     */
    public void setForceUseSoftwareDecoder(boolean force) {
        this.forceUseSoftwareDecoder = force;
        configureDecoder();
    }
    
    /**
     * 获取 IJKPlayer 实例（用于特殊操作）
     */
    public IjkMediaPlayer getIjkPlayer() {
        return currentIjkPlayer;
    }
}

