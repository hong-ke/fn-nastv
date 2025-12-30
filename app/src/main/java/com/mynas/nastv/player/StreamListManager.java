package com.mynas.nastv.player;

import android.content.Context;
import android.util.Log;

import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.SignatureUtils;

import java.util.List;

/**
 * 流列表管理器
 * 负责加载音频流和字幕流列表
 */
public class StreamListManager {
    private static final String TAG = "StreamListManager";
    
    private Context context;
    private StreamListCallback callback;
    
    public interface StreamListCallback {
        void onStreamListLoaded(StreamListResponse.StreamData data);
        void onError(String error);
    }
    
    public StreamListManager(Context context) {
        this.context = context;
    }
    
    public void setCallback(StreamListCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 加载流列表（音频流和字幕流）
     */
    public void loadStreamList(String itemGuid) {
        if (itemGuid == null || itemGuid.isEmpty()) {
            Log.e(TAG, "No item guid for stream loading");
            return;
        }
        
        Log.d(TAG, "Loading stream list for item: " + itemGuid);
        
        new Thread(() -> {
            try {
                String token = SharedPreferencesManager.getAuthToken();
                String signature = SignatureUtils.generateSignature(
                        "GET", "/v/api/v1/stream/list/" + itemGuid, "", null);
                
                Log.d(TAG, "Calling getStreamList API...");
                
                retrofit2.Response<StreamListResponse> response =
                        ApiClient.getApiService()
                                .getStreamList(token, signature, itemGuid)
                                .execute();
                
                Log.d(TAG, "getStreamList response: " + response.code());
                
                if (response.isSuccessful() && response.body() != null) {
                    StreamListResponse.StreamData data = response.body().getData();
                    
                    if (data != null) {
                        // 打印音频流信息
                        if (data.getAudioStreams() != null) {
                            Log.d(TAG, "Found " + data.getAudioStreams().size() + " audio streams");
                            for (int i = 0; i < data.getAudioStreams().size(); i++) {
                                StreamListResponse.AudioStream audio = data.getAudioStreams().get(i);
                                Log.d(TAG, "Audio " + i + ": " + audio.getAudioType() + " (" + 
                                        audio.getCodecName() + ") " + audio.getChannels() + "ch, index=" + audio.getIndex());
                            }
                        }
                        
                        // 打印字幕流信息
                        if (data.getSubtitleStreams() != null) {
                            Log.d(TAG, "Found " + data.getSubtitleStreams().size() + " subtitle streams");
                            for (int i = 0; i < data.getSubtitleStreams().size(); i++) {
                                StreamListResponse.SubtitleStream sub = data.getSubtitleStreams().get(i);
                                Log.d(TAG, "Subtitle " + i + ": " + sub.getTitle() + " (" + sub.getLanguage() + 
                                        ") external=" + sub.isExternal() + " guid=" + sub.getGuid());
                            }
                        }
                        
                        if (callback != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                callback.onStreamListLoaded(data);
                            });
                        }
                    } else {
                        String error = "Stream data is null";
                        Log.e(TAG, error);
                        if (callback != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                callback.onError(error);
                            });
                        }
                    }
                } else {
                    String error = "Failed to load stream list: " + response.code();
                    Log.e(TAG, error);
                    if (response.errorBody() != null) {
                        try {
                            Log.e(TAG, "Error body: " + response.errorBody().string());
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    if (callback != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            callback.onError(error);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading stream list", e);
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        callback.onError("加载流列表失败: " + e.getMessage());
                    });
                }
            }
        }).start();
    }
}

