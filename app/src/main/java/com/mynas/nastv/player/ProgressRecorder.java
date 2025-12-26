package com.mynas.nastv.player;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mynas.nastv.manager.MediaManager;
import com.mynas.nastv.model.PlayRecordRequest;
import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.network.ApiService;
import com.mynas.nastv.model.BaseResponse;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.SignatureUtils;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ProgressRecorder - 播放进度记录器
 * 定时记录播放进度到服务器，与 Web 端行为一致
 * 
 * 功能:
 * - 每 10 秒自动记录播放进度
 * - 暂停/退出时立即保存
 * - 支持手动触发保存
 */
public class ProgressRecorder {
    private static final String TAG = "ProgressRecorder";
    private static final long RECORD_INTERVAL_MS = 10000; // 10秒
    
    private final Handler handler;
    private final Runnable recordRunnable;
    
    private String itemGuid;
    private String mediaGuid;
    private String videoGuid;
    private String audioGuid;
    private String subtitleGuid;
    private String resolution;    // 分辨率
    private long bitrate;         // 码率
    private long currentPosition; // 当前位置（秒）
    private long duration;        // 总时长（秒）
    
    private boolean isRecording = false;
    
    public ProgressRecorder() {
        this.handler = new Handler(Looper.getMainLooper());
        this.recordRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    sendProgressRecord();
                    handler.postDelayed(this, RECORD_INTERVAL_MS);
                }
            }
        };
    }
    
    /**
     * 开始记录播放进度
     * @param itemGuid 媒体项 GUID (episode/movie)
     * @param mediaGuid 媒体文件 GUID
     */
    public void startRecording(String itemGuid, String mediaGuid) {
        Log.d(TAG, "Start recording: itemGuid=" + itemGuid + ", mediaGuid=" + mediaGuid);
        
        this.itemGuid = itemGuid;
        this.mediaGuid = mediaGuid;
        this.isRecording = true;
        
        // 启动定时记录
        handler.removeCallbacks(recordRunnable);
        handler.postDelayed(recordRunnable, RECORD_INTERVAL_MS);
    }
    
    /**
     * 设置流 GUID（可选，用于记录当前选择的视频/音频/字幕轨道）
     */
    public void setStreamGuids(String videoGuid, String audioGuid, String subtitleGuid) {
        this.videoGuid = videoGuid;
        this.audioGuid = audioGuid;
        this.subtitleGuid = subtitleGuid;
    }
    
    /**
     * 设置视频信息（分辨率和码率）
     */
    public void setVideoInfo(String resolution, long bitrate) {
        this.resolution = resolution;
        this.bitrate = bitrate;
    }
    
    /**
     * 更新播放进度
     * @param position 当前位置（秒）
     * @param duration 总时长（秒）
     */
    public void updateProgress(long position, long duration) {
        this.currentPosition = position;
        this.duration = duration;
    }
    
    /**
     * 立即保存进度（用于暂停/退出时）
     */
    public void saveImmediately() {
        if (isRecording && itemGuid != null) {
            Log.d(TAG, "Save immediately: position=" + currentPosition);
            sendProgressRecord();
        }
    }
    
    /**
     * 停止记录
     */
    public void stopRecording() {
        Log.d(TAG, "Stop recording");
        
        // 停止前保存一次
        if (isRecording) {
            saveImmediately();
        }
        
        isRecording = false;
        handler.removeCallbacks(recordRunnable);
        
        // 清理状态
        itemGuid = null;
        mediaGuid = null;
        videoGuid = null;
        audioGuid = null;
        subtitleGuid = null;
        currentPosition = 0;
        duration = 0;
    }
    
    /**
     * 发送播放进度记录到服务器
     */
    private void sendProgressRecord() {
        if (itemGuid == null || mediaGuid == null) {
            Log.w(TAG, "Cannot send record: missing itemGuid or mediaGuid");
            return;
        }
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            Log.w(TAG, "Cannot send record: not logged in");
            return;
        }
        
        try {
            // 构建请求
            PlayRecordRequest request = new PlayRecordRequest();
            request.setItemGuid(itemGuid);
            request.setMediaGuid(mediaGuid);
            request.setVideoGuid(videoGuid);
            request.setAudioGuid(audioGuid);
            request.setSubtitleGuid(subtitleGuid);
            request.setResolution(resolution != null ? resolution : "");
            request.setBitrate(bitrate);
            request.setTs(currentPosition);
            request.setDuration(duration);
            
            // 生成签名
            String method = "POST";
            String url = "/v/api/v1/play/record";
            Gson gson = new Gson();
            String data = gson.toJson(request);
            Map<String, String> params = new HashMap<>();
            
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            // 发送请求
            ApiService apiService = ApiClient.getApiService();
            Call<BaseResponse<Object>> call = apiService.sendPlayRecord(authToken, authx, request);
            
            call.enqueue(new Callback<BaseResponse<Object>>() {
                @Override
                public void onResponse(Call<BaseResponse<Object>> call, Response<BaseResponse<Object>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        BaseResponse<Object> res = response.body();
                        if (res.getCode() == 0) {
                            Log.d(TAG, "Progress recorded: " + currentPosition + "s / " + duration + "s");
                        } else {
                            Log.w(TAG, "Record failed: " + res.getMessage());
                        }
                    } else {
                        Log.w(TAG, "Record request failed: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(Call<BaseResponse<Object>> call, Throwable t) {
                    Log.e(TAG, "Record error: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Record exception: " + e.getMessage());
        }
    }
    
    /**
     * 检查是否正在记录
     */
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * 获取当前记录的 itemGuid
     */
    public String getItemGuid() {
        return itemGuid;
    }
    
    /**
     * 获取当前记录的 mediaGuid
     */
    public String getMediaGuid() {
        return mediaGuid;
    }
}