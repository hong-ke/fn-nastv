package com.mynas.nastv.feature.danmaku.logic;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mynas.nastv.feature.danmaku.model.DanmakuEntity;
import com.mynas.nastv.model.DanmakuMapResponse;
import com.mynas.nastv.network.ApiClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 弹幕数据仓库 - 使用弹幕服务器 (http://192.168.3.20:13401)
 * 
 * 🎬 优化：
 * - 智能过滤弹幕，防止 OOM
 * - 优先丢弃重复弹幕
 * - 根据播放时长计算合理的弹幕密度
 * - 支持清理已使用的弹幕
 */
public class DanmuRepository {
    
    private static final String TAG = "DanmuRepository";
    
    // 🎬 弹幕密度限制：每分钟最多显示的弹幕数量
    private static final int MAX_DANMAKU_PER_MINUTE = 30;
    
    // 🎬 总弹幕数量限制（防止 OOM）
    private static final int MAX_TOTAL_DANMAKU = 3000;
    
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
                        
                        // 计算原始弹幕数量
                        int rawCount = 0;
                        for (List<DanmakuMapResponse.DanmakuItem> list : rawData.values()) {
                            if (list != null) rawCount += list.size();
                        }
                        final int finalRawCount = rawCount;
                        Log.d(TAG, "Danmaku API success, got " + rawCount + " raw danmaku items");
                        
                        // Process in background
                        backgroundExecutor.execute(() -> {
                            try {
                                Map<String, List<DanmakuEntity>> data = processDanmakuMap(rawData);
                                int totalCount = 0;
                                for (List<DanmakuEntity> list : data.values()) {
                                    totalCount += list.size();
                                }
                                Log.d(TAG, "Processed " + totalCount + " danmaku items into " + data.size() + " buckets (filtered from " + finalRawCount + ")");
                                mainHandler.post(() -> callback.onSuccess(data));
                            } catch (OutOfMemoryError e) {
                                Log.e(TAG, "OOM while processing danmaku, returning empty", e);
                                mainHandler.post(() -> callback.onSuccess(new HashMap<>()));
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing danmaku", e);
                                mainHandler.post(() -> callback.onError(e));
                            }
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
     * 🎬 优化：
     * 1. 先去重（相同文本的弹幕只保留一个）
     * 2. 根据时间密度过滤（每分钟最多 MAX_DANMAKU_PER_MINUTE 条）
     * 3. 总数限制（最多 MAX_TOTAL_DANMAKU 条）
     * 4. 按60秒分桶存储
     */
    private Map<String, List<DanmakuEntity>> processDanmakuMap(Map<String, List<DanmakuMapResponse.DanmakuItem>> rawData) {
        Map<String, List<DanmakuEntity>> result = new HashMap<>();
        
        if (rawData == null) return result;
        
        // 🎬 第一步：收集所有弹幕并去重
        List<DanmakuMapResponse.DanmakuItem> allItems = new ArrayList<>();
        Set<String> seenTexts = new HashSet<>();
        
        for (Map.Entry<String, List<DanmakuMapResponse.DanmakuItem>> entry : rawData.entrySet()) {
            List<DanmakuMapResponse.DanmakuItem> items = entry.getValue();
            if (items == null) continue;
            
            for (DanmakuMapResponse.DanmakuItem item : items) {
                if (item.text == null || item.text.trim().isEmpty()) continue;
                
                String normalizedText = item.text.trim().toLowerCase();
                // 🎬 去重：相同文本只保留第一个
                if (!seenTexts.contains(normalizedText)) {
                    seenTexts.add(normalizedText);
                    allItems.add(item);
                }
            }
        }
        
        Log.d(TAG, "去重后弹幕数量: " + allItems.size() + " (去除 " + (seenTexts.size() > allItems.size() ? 0 : seenTexts.size() - allItems.size()) + " 条重复)");
        
        // 🎬 第二步：按时间排序
        Collections.sort(allItems, new Comparator<DanmakuMapResponse.DanmakuItem>() {
            @Override
            public int compare(DanmakuMapResponse.DanmakuItem a, DanmakuMapResponse.DanmakuItem b) {
                return Double.compare(a.time, b.time);
            }
        });
        
        // 🎬 第三步：按时间密度过滤
        List<DanmakuMapResponse.DanmakuItem> filteredItems = filterByDensity(allItems);
        
        Log.d(TAG, "密度过滤后弹幕数量: " + filteredItems.size());
        
        // 🎬 第四步：总数限制
        if (filteredItems.size() > MAX_TOTAL_DANMAKU) {
            // 均匀采样
            List<DanmakuMapResponse.DanmakuItem> sampled = new ArrayList<>();
            float step = (float) filteredItems.size() / MAX_TOTAL_DANMAKU;
            for (int i = 0; i < MAX_TOTAL_DANMAKU; i++) {
                int index = Math.min((int) (i * step), filteredItems.size() - 1);
                sampled.add(filteredItems.get(index));
            }
            filteredItems = sampled;
            Log.d(TAG, "总数限制后弹幕数量: " + filteredItems.size());
        }
        
        // 🎬 第五步：按60秒分桶
        for (DanmakuMapResponse.DanmakuItem item : filteredItems) {
            long timeMs = (long) (item.time * 1000);
            long bucketId = timeMs / 60000;
            long bucketStart = bucketId * 60000;
            long bucketEnd = bucketStart + 60000;
            String bucketKey = bucketStart + "-" + bucketEnd;
            
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
        
        Log.d(TAG, "弹幕分桶完成，共 " + result.size() + " 个时间桶");
        
        // 清理临时数据帮助 GC
        allItems.clear();
        seenTexts.clear();
        filteredItems.clear();
        
        return result;
    }
    
    /**
     * 🎬 按时间密度过滤弹幕
     * 每分钟最多保留 MAX_DANMAKU_PER_MINUTE 条弹幕
     */
    private List<DanmakuMapResponse.DanmakuItem> filterByDensity(List<DanmakuMapResponse.DanmakuItem> items) {
        if (items.isEmpty()) return items;
        
        List<DanmakuMapResponse.DanmakuItem> result = new ArrayList<>();
        
        // 按分钟分组
        Map<Long, List<DanmakuMapResponse.DanmakuItem>> minuteGroups = new HashMap<>();
        for (DanmakuMapResponse.DanmakuItem item : items) {
            long minute = (long) (item.time / 60);
            List<DanmakuMapResponse.DanmakuItem> group = minuteGroups.get(minute);
            if (group == null) {
                group = new ArrayList<>();
                minuteGroups.put(minute, group);
            }
            group.add(item);
        }
        
        // 每分钟最多保留 MAX_DANMAKU_PER_MINUTE 条
        for (Map.Entry<Long, List<DanmakuMapResponse.DanmakuItem>> entry : minuteGroups.entrySet()) {
            List<DanmakuMapResponse.DanmakuItem> group = entry.getValue();
            
            if (group.size() <= MAX_DANMAKU_PER_MINUTE) {
                result.addAll(group);
            } else {
                // 均匀采样
                float step = (float) group.size() / MAX_DANMAKU_PER_MINUTE;
                for (int i = 0; i < MAX_DANMAKU_PER_MINUTE; i++) {
                    int index = Math.min((int) (i * step), group.size() - 1);
                    result.add(group.get(index));
                }
            }
        }
        
        // 重新按时间排序
        Collections.sort(result, new Comparator<DanmakuMapResponse.DanmakuItem>() {
            @Override
            public int compare(DanmakuMapResponse.DanmakuItem a, DanmakuMapResponse.DanmakuItem b) {
                return Double.compare(a.time, b.time);
            }
        });
        
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
