package com.mynas.nastv.feature.danmaku.logic;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;
import com.mynas.nastv.model.DanmakuMapResponse;
import com.mynas.nastv.network.ApiClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 弹幕数据仓库 - 使用弹幕服务器 (http://192.168.3.20:13401)
 */
public class DanmuRepository {
    
    private static final String TAG = "DanmuRepository";
    
    private final Handler mainHandler;
    private final Executor backgroundExecutor;
    
    public DanmuRepository() {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.backgroundExecutor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * Fetch Danmaku using title, season, episode (New API)
     * 使用弹幕服务器 (http://192.168.3.20:13401)
     * 
     * API: GET /danmu/get?title=xxx&season_number=1&episode_number=1
     * 响应格式: { "1": [...] } - key 是集数，value 是该集所有弹幕
     * 
     * 注意：不传 guid/parent_guid，让服务器直接用 title 搜索第三方弹幕源
     * 如果传入 guid 但数据库没有记录，服务器会返回空数据
     */
    public void fetchDanmaku(String title, int episode, int season, String guid, String parentGuid, RepositoryCallback callback) {
        Log.d(TAG, "Fetching danmaku for title=" + title + ", s" + season + "e" + episode);
        
        if (title == null || title.isEmpty()) {
            Log.w(TAG, "title is empty, skipping danmaku fetch");
            notifyError(callback, new IllegalArgumentException("title is empty"));
            return;
        }

        try {
            // 使用弹幕专用 API 服务 (独立的弹幕服务器)
            // 不传 guid/parent_guid，直接用 title 搜索弹幕
            Call<Map<String, List<DanmakuMapResponse.DanmakuItem>>> call = 
                ApiClient.getDanmuApiService().getDanmakuMap(title, season, episode);
            
            call.enqueue(new Callback<Map<String, List<DanmakuMapResponse.DanmakuItem>>>() {
                @Override
                public void onResponse(@NonNull Call<Map<String, List<DanmakuMapResponse.DanmakuItem>>> call, 
                                       @NonNull Response<Map<String, List<DanmakuMapResponse.DanmakuItem>>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Map<String, List<DanmakuMapResponse.DanmakuItem>> rawData = response.body();
                        Log.d(TAG, "Danmaku API success, got " + rawData.size() + " buckets");
                        
                        // Process in background
                        backgroundExecutor.execute(() -> {
                            Map<String, List<DanmakuEntity>> data = processDanmakuMap(rawData);
                            int totalCount = 0;
                            for (List<DanmakuEntity> list : data.values()) {
                                totalCount += list.size();
                            }
                            Log.d(TAG, "Processed " + totalCount + " danmaku items into " + data.size() + " buckets");
                            mainHandler.post(() -> callback.onSuccess(data));
                        });
                    } else {
                        String errorBody = "";
                        try {
                            if (response.errorBody() != null) {
                                errorBody = response.errorBody().string();
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                        Log.e(TAG, "Danmaku API failed: " + response.code() + " - " + errorBody);
                        notifyError(callback, new Exception("Request failed: " + response.code()));
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Map<String, List<DanmakuMapResponse.DanmakuItem>>> call, @NonNull Throwable t) {
                    Log.e(TAG, "Danmaku API error", t);
                    notifyError(callback, new Exception(t.getMessage()));
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Danmaku fetch exception", e);
            notifyError(callback, e);
        }
    }

    /**
     * Legacy fetch method using doubanId (kept for compatibility)
     */
    public void fetchDanmaku(String doubanId, int episode, int season, RepositoryCallback callback) {
        Log.w(TAG, "Legacy fetchDanmaku called with doubanId=" + doubanId + " - not supported, use title-based API");
        notifyError(callback, new UnsupportedOperationException("Using doubanId not supported, use title-based API"));
    }

    /**
     * Legacy fetch method (kept for compatibility)
     */
    public void fetchDanmaku(String videoId, String episodeId, RepositoryCallback callback) {
        Log.w(TAG, "Legacy fetchDanmaku called with videoId=" + videoId + " - not supported");
        notifyError(callback, new UnsupportedOperationException("Using legacy ID not supported in new API"));
    }
    
    /**
     * 处理弹幕 Map 响应
     * 
     * API返回格式: { "1": [弹幕列表] } - key是集数（电影为"1"），value是该集所有弹幕
     * 每条弹幕有 time 字段（秒），表示弹幕出现的时间点
     * 
     * 输出: { "0-60000": [弹幕列表], "60000-120000": [弹幕列表] } - 按60秒分桶
     * 根据每条弹幕的 time 字段分配到对应的时间桶
     */
    private Map<String, List<DanmakuEntity>> processDanmakuMap(Map<String, List<DanmakuMapResponse.DanmakuItem>> rawData) {
        Map<String, List<DanmakuEntity>> result = new HashMap<>();
        
        if (rawData == null) return result;
        
        // 遍历所有集数的弹幕（电影只有"1"，电视剧可能有多集）
        for (Map.Entry<String, List<DanmakuMapResponse.DanmakuItem>> entry : rawData.entrySet()) {
            List<DanmakuMapResponse.DanmakuItem> items = entry.getValue();
            
            if (items == null) continue;
            
            // 根据每条弹幕的 time 字段分配到对应的60秒时间桶
            for (DanmakuMapResponse.DanmakuItem item : items) {
                // 计算弹幕所属的时间桶（60秒为一个桶）
                long timeMs = (long) (item.time * 1000); // 秒转毫秒
                long bucketId = timeMs / 60000;
                long bucketStart = bucketId * 60000;
                long bucketEnd = bucketStart + 60000;
                String bucketKey = bucketStart + "-" + bucketEnd;
                
                // 获取或创建该时间桶的弹幕列表
                List<DanmakuEntity> entityList = result.get(bucketKey);
                if (entityList == null) {
                    entityList = new ArrayList<>();
                    result.put(bucketKey, entityList);
                }
                
                // 创建弹幕实体
                DanmakuEntity entity = new DanmakuEntity();
                entity.time = timeMs;
                entity.text = sanitizeDanmakuText(item.text);
                entity.color = item.color != null ? item.color : "#FFFFFF";
                entity.mode = item.mode;
                entityList.add(entity);
            }
        }
        
        Log.d(TAG, "弹幕分桶完成，共 " + result.size() + " 个时间桶");
        return result;
    }
    
    public static String sanitizeDanmakuText(String input) {
        if (input == null) return "";
        return input.replace("\n", " ").replace("\r", " ").trim();
    }
    
    private void notifyError(RepositoryCallback callback, Exception e) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(e));
        }
    }
    
    public interface RepositoryCallback {
        void onSuccess(Map<String, List<DanmakuEntity>> data);
        void onError(Exception e);
    }
}
