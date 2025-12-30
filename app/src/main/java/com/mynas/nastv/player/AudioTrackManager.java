package com.mynas.nastv.player;

import android.content.Context;
import android.util.Log;

import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.SignatureUtils;
import com.mynas.nastv.utils.ToastUtils;

import java.util.List;

/**
 * 音频轨道管理器
 * 负责音频轨道的加载、选择和切换
 */
public class AudioTrackManager {
    private static final String TAG = "AudioTrackManager";
    
    private Context context;
    private List<StreamListResponse.AudioStream> audioStreams;
    private int currentAudioIndex = -1;
    private int preferredAudioIndex = -1;  // 用户选择的音频轨道索引
    private AudioTrackCallback callback;
    
    public interface AudioTrackCallback {
        void onAudioTracksLoaded(List<StreamListResponse.AudioStream> streams);
        void onAudioTrackChanged(int index);
        void onError(String error);
    }
    
    public AudioTrackManager(Context context) {
        this.context = context;
    }
    
    public void setCallback(AudioTrackCallback callback) {
        this.callback = callback;
    }
    
    public List<StreamListResponse.AudioStream> getAudioStreams() {
        return audioStreams;
    }
    
    public int getCurrentAudioIndex() {
        return currentAudioIndex;
    }
    
    public void setCurrentAudioIndex(int index) {
        this.currentAudioIndex = index;
    }
    
    public int getPreferredAudioIndex() {
        return preferredAudioIndex;
    }
    
    /**
     * 加载音频流列表
     */
    public void loadAudioStreams(String itemGuid) {
        if (itemGuid == null || itemGuid.isEmpty()) {
            Log.e(TAG, "No item guid for audio stream loading");
            return;
        }
        
        Log.d(TAG, "Loading audio streams for item: " + itemGuid);
        
        new Thread(() -> {
            try {
                String token = SharedPreferencesManager.getAuthToken();
                String signature = SignatureUtils.generateSignature(
                        "GET", "/v/api/v1/stream/list/" + itemGuid, "", null);
                
                retrofit2.Response<StreamListResponse> response =
                        ApiClient.getApiService()
                                .getStreamList(token, signature, itemGuid)
                                .execute();
                
                if (response.isSuccessful() && response.body() != null) {
                    StreamListResponse.StreamData data = response.body().getData();
                    
                    if (data != null && data.getAudioStreams() != null) {
                        audioStreams = data.getAudioStreams();
                        Log.d(TAG, "Found " + audioStreams.size() + " audio streams");
                        
                        // 查找 AAC 格式的音频轨道
                        for (int i = 0; i < audioStreams.size(); i++) {
                            StreamListResponse.AudioStream audio = audioStreams.get(i);
                            String codec = audio.getCodecName();
                            if (codec != null && codec.toLowerCase().equals("aac")) {
                                preferredAudioIndex = audio.getIndex();
                                Log.d(TAG, "Preferred AAC audio track index: " + preferredAudioIndex);
                                break;
                            }
                        }
                        
                        if (callback != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                callback.onAudioTracksLoaded(audioStreams);
                            });
                        }
                    }
                } else {
                    String error = "Failed to load audio streams: " + response.code();
                    Log.e(TAG, error);
                    if (callback != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            callback.onError(error);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading audio streams", e);
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onError("加载音频流失败: " + e.getMessage());
                    });
                }
            }
        }).start();
    }
    
    /**
     * 自动选择可用的音频轨道
     */
    public void autoSelectAudioTrack(PlayerKernel playerKernel) {
        if (audioStreams == null || audioStreams.isEmpty()) {
            return;
        }
        
        if (playerKernel instanceof IjkPlayerKernel) {
            IjkPlayerKernel ijkKernel = (IjkPlayerKernel) playerKernel;
            
            // 延迟执行，等待播放器解析完轨道信息
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                List<PlayerKernel.TrackInfo> tracks = ijkKernel.getAudioTracks();
                if (tracks.isEmpty()) {
                    return;
                }
                
                // 如果有首选音频轨道（从 API 获取的 AAC 轨道索引），直接使用
                if (preferredAudioIndex >= 0) {
                    for (int i = 0; i < tracks.size(); i++) {
                        PlayerKernel.TrackInfo track = tracks.get(i);
                        if (track.codec != null && track.codec.toLowerCase().contains("aac")) {
                            ijkKernel.selectAudioTrack(i);
                            currentAudioIndex = i;
                            ToastUtils.show(context, "已自动切换到 AAC 音频");
                            return;
                        }
                    }
                }
                
                // 如果没有首选轨道，优先选择 AAC 轨道
                for (int i = 0; i < tracks.size(); i++) {
                    PlayerKernel.TrackInfo track = tracks.get(i);
                    if (track.codec != null && track.codec.toLowerCase().contains("aac")) {
                        ijkKernel.selectAudioTrack(i);
                        currentAudioIndex = i;
                        ToastUtils.show(context, "已自动切换到 AAC 音频");
                        return;
                    }
                }
            }, 1000);
        }
    }
    
    /**
     * 选择音频轨道
     */
    public void selectAudioTrack(int index, PlayerKernel playerKernel) {
        if (audioStreams == null || index < 0 || index >= audioStreams.size()) {
            return;
        }
        
        if (playerKernel instanceof IjkPlayerKernel) {
            IjkPlayerKernel ijkKernel = (IjkPlayerKernel) playerKernel;
            ijkKernel.selectAudioTrack(index);
            currentAudioIndex = index;
            
            StreamListResponse.AudioStream audio = audioStreams.get(index);
            String msg = "已切换到: " + (audio.getTitle() != null ? audio.getTitle() : "音频 " + (index + 1));
            if (audio.getAudioType() != null) {
                msg += " (" + audio.getAudioType().toUpperCase() + ")";
            }
            ToastUtils.show(context, msg);
            
            if (callback != null) {
                callback.onAudioTrackChanged(index);
            }
        }
    }
    
    /**
     * 重置状态
     */
    public void reset() {
        audioStreams = null;
        currentAudioIndex = -1;
        preferredAudioIndex = -1;
    }
}

