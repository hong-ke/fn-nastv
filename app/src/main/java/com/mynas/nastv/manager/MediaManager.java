package com.mynas.nastv.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.ResponseBody;

import com.mynas.nastv.model.BaseResponse;
import com.mynas.nastv.model.MediaDbListResponse;
import com.mynas.nastv.model.MediaDetailResponse;
import com.mynas.nastv.model.MediaItem;
import com.mynas.nastv.model.MediaItemListResponse;
import com.mynas.nastv.model.MediaLibraryItemsRequest;
import com.mynas.nastv.model.PlayInfoRequest;
import com.mynas.nastv.model.PlayInfoResponse;
import com.mynas.nastv.model.PlayApiRequest;
import com.mynas.nastv.model.PlayApiResponse;
import com.mynas.nastv.model.PlayListResponse;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.model.DanmuResponse;
import com.mynas.nastv.model.Danmu;
import com.mynas.nastv.model.PlayRequest;
import com.mynas.nastv.model.PlayResponse;
import com.mynas.nastv.model.SeasonListResponse;
import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.network.ApiService;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.SignatureUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 📚 媒体数据管理器
 * 参考Web项目实现，负责媒体库数据的获取和管理
 * 对应Web项目：App.vue中的GetMediaDbList、GetMediaDbInfos等方法
 */
public class MediaManager {
    private static final String TAG = "MediaManager";
    
    private Context context;
    
    // 媒体库数据缓存
    private List<MediaDbItem> mediaDbList = new ArrayList<>();
    private Map<String, List<MediaItem>> mediaDbInfos = new HashMap<>();
    
    public MediaManager(Context context) {
        this.context = context;
        // 确保SharedPreferencesManager已初始化
        SharedPreferencesManager.initialize(context);
    }
    
    /**
     * 📚 获取媒体库列表
     * 对应Web项目：GetMediaDbList()
     */
    public void getMediaDbList(MediaCallback<List<MediaDbItem>> callback) {
        Log.d(TAG, "🔍 [MediaManager] 开始获取媒体库列表...");
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("用户未登录");
            return;
        }
        
        try {
            // 生成签名 - 使用与Web项目相同的路径格式
            String method = "GET";
            String url = "/v/api/v1/mediadb/list";  // Web项目格式：添加/v前缀
            String data = "";
            Map<String, String> params = new HashMap<>();
            
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            
            // fnos-tv后端期望原始token，不要Bearer前缀（与Web项目一致）
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            Log.d(TAG, "🔍 [API调试] 准备调用媒体库API");
            Log.d(TAG, "🔍 [API调试] 原始Token: " + (token != null ? token.substring(0, Math.min(30, token.length())) + "..." : "null"));
            Log.d(TAG, "🔍 [API调试] 处理后Token: " + (authToken != null ? authToken.substring(0, Math.min(30, authToken.length())) + "..." : "null"));
            Log.d(TAG, "🔍 [API调试] Authx: " + (authx != null ? authx.substring(0, Math.min(30, authx.length())) + "..." : "null"));
            
            // 调用API - 使用fnos-tv代理服务器，nginx会转发到飞牛服务器  
            ApiService apiService = ApiClient.getApiService(); // 使用代理服务器
            if (apiService == null) {
                callback.onError("API服务未初始化");
                return;
            }
            
            Log.d(TAG, "🔍 [API调试] 使用fnos-tv代理调用媒体库API");
            Log.d(TAG, "🔍 [API调试] 代理URL: " + SharedPreferencesManager.getApiBaseUrl() + "api/v1/mediadb/list");
            Log.d(TAG, "🔍 [API调试] nginx转发到: ${FNOS_URL}/v/api/v1/mediadb/list");
            Call<MediaDbListResponse> call = apiService.getMediaDbList(authToken, authx);
            call.enqueue(new Callback<MediaDbListResponse>() {
                @Override
                public void onResponse(@NonNull Call<MediaDbListResponse> call, @NonNull Response<MediaDbListResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        MediaDbListResponse dbResponse = response.body();
                        if (dbResponse.getCode() == 0) {
                            // 转换为内部数据格式
                            mediaDbList = convertToMediaDbItems(dbResponse.getData());
                            Log.d(TAG, "✅ 媒体库列表获取成功，共 " + mediaDbList.size() + " 个媒体库");
                            callback.onSuccess(mediaDbList);
                        } else {
                            Log.e(TAG, "❌ 媒体库列表API返回错误: " + dbResponse.getMessage());
                            callback.onError("API错误: " + dbResponse.getMessage());
                        }
                    } else {
                        Log.e(TAG, "❌ 媒体库列表请求失败: " + response.message());
                        callback.onError("请求失败: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<MediaDbListResponse> call, @NonNull Throwable t) {
                    Log.e(TAG, "❌ 媒体库列表网络请求失败", t);
                    callback.onError("网络请求失败: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 媒体库列表请求异常", e);
            callback.onError("请求异常: " + e.getMessage());
        }
    }
    
    /**
     * 📖 获取指定媒体库的详细信息
     * 对应Web项目：GetMediaDbInfos()
     */
    public void getMediaDbInfos(String guid, MediaCallback<List<MediaItem>> callback) {
        Log.d(TAG, "🔍 [MediaManager] 获取媒体库详情: " + guid);
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("用户未登录");
            return;
        }
        
        try {
            // 构建请求数据
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("ancestor_guid", guid);
            requestData.put("tags", new HashMap<>());
            requestData.put("types", new ArrayList<>());
            requestData.put("page", 1);
            requestData.put("limit", 100);
            requestData.put("sort", "sort_name");
            requestData.put("order", "ASC");
            
            // 生成签名 - 使用与Web项目相同的路径格式
            String method = "POST";
            String url = "/v/api/v1/item/list";  // Web项目格式：添加/v前缀
            // 序列化requestData为JSON字符串用于签名
            Gson gson = new Gson();
            String data = gson.toJson(requestData);
            Map<String, String> params = new HashMap<>();
            
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            
            // fnos-tv后端期望原始token，不要Bearer前缀（与Web项目一致）
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            Log.d(TAG, "🔍 [API调试] 准备调用媒体库详情API");
            Log.d(TAG, "🔍 [API调试] GUID: " + guid);
            Log.d(TAG, "🔍 [API调试] 处理后Token: " + (authToken != null ? authToken.substring(0, Math.min(30, authToken.length())) + "..." : "null"));
            Log.d(TAG, "🔍 [API调试] Authx: " + (authx != null ? authx.substring(0, Math.min(30, authx.length())) + "..." : "null"));
            
            // 调用API - 使用fnos-tv代理服务器，nginx会转发到飞牛服务器
            ApiService apiService = ApiClient.getApiService(); // 使用代理服务器
            
            Log.d(TAG, "🔍 [API调试] 使用fnos-tv代理调用媒体库详情API");
            Call<MediaItemListResponse> call = apiService.getMediaDbInfos(authToken, authx, requestData);
            call.enqueue(new Callback<MediaItemListResponse>() {
                @Override
                public void onResponse(@NonNull Call<MediaItemListResponse> call, @NonNull Response<MediaItemListResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        MediaItemListResponse itemResponse = response.body();
                        if (itemResponse.getCode() == 0) {
                            // 解析并转换数据
                            List<MediaItem> mediaItems = convertToMediaItems(itemResponse.getData());
                            mediaDbInfos.put(guid, mediaItems);
                            Log.d(TAG, "✅ 媒体库详情获取成功，共 " + mediaItems.size() + " 个项目");
                            callback.onSuccess(mediaItems);
                        } else {
                            Log.e(TAG, "❌ 媒体库详情API返回错误: " + itemResponse.getMessage());
                            callback.onError("API错误: " + itemResponse.getMessage());
                        }
                    } else {
                        Log.e(TAG, "❌ 媒体库详情请求失败: " + response.message());
                        callback.onError("请求失败: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<MediaItemListResponse> call, @NonNull Throwable t) {
                    Log.e(TAG, "❌ 媒体库详情网络请求失败", t);
                    callback.onError("网络请求失败: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 媒体库详情请求异常", e);
            callback.onError("请求异常: " + e.getMessage());
        }
    }
    
    /**
     * 📊 获取媒体库统计数据（对应Web项目：GetMediaDbSum）
     */
    public void getMediaDbSum(MediaCallback<Map<String, Integer>> callback) {
        Log.d(TAG, "📊 [MediaManager] 开始获取媒体库统计数据...");
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("用户未登录");
            return;
        }
        
        try {
            // 生成签名 - 使用与Web项目相同的路径格式
            String method = "GET";
            String url = "/v/api/v1/mediadb/sum";  // Web项目格式：添加/v前缀
            String data = "";
            Map<String, String> params = new HashMap<>();
            
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            
            // fnos-tv后端期望原始token，不要Bearer前缀（与Web项目一致）
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            Log.d(TAG, "📊 [API调试] 准备调用媒体库统计API");
            Log.d(TAG, "📊 [API调试] 处理后Token: " + (authToken != null ? authToken.substring(0, Math.min(30, authToken.length())) + "..." : "null"));
            Log.d(TAG, "📊 [API调试] Authx: " + (authx != null ? authx.substring(0, Math.min(30, authx.length())) + "..." : "null"));
            
            // 调用API - 使用fnos-tv代理服务器，nginx会转发到飞牛服务器  
            ApiService apiService = ApiClient.getApiService(); // 使用代理服务器
            if (apiService == null) {
                callback.onError("API服务未初始化");
                return;
            }
            
            Log.d(TAG, "📊 [API调试] 使用fnos-tv代理调用媒体库统计API");
            Call<BaseResponse<Object>> call = apiService.getMediaDbSum(authToken, authx);
            Log.d(TAG, "📊 [API调试] 请求URL: " + call.request().url());
            call.enqueue(new Callback<BaseResponse<Object>>() {
                @Override
                public void onResponse(@NonNull Call<BaseResponse<Object>> call, @NonNull Response<BaseResponse<Object>> response) {
                    Log.d(TAG, "📊 [API响应] HTTP状态码: " + response.code());
                    Log.d(TAG, "📊 [API响应] 响应成功: " + response.isSuccessful());
                    
                    if (response.isSuccessful() && response.body() != null) {
                        BaseResponse<Object> sumResponse = response.body();
                        Log.d(TAG, "📊 [API响应] 响应体不为空: " + (sumResponse != null));
                        Log.d(TAG, "📊 [API响应] 响应代码: " + sumResponse.getCode());
                        Log.d(TAG, "📊 [API响应] 响应消息: " + sumResponse.getMessage());
                        Log.d(TAG, "📊 [API响应] 响应数据类型: " + (sumResponse.getData() != null ? sumResponse.getData().getClass().getSimpleName() : "null"));
                        Log.d(TAG, "📊 [API响应] 原始数据: " + sumResponse.getData());
                        
                        if (sumResponse.getCode() == 0) {
                            // 解析统计数据
                            Map<String, Integer> sumData = parseMediaDbSumData(sumResponse.getData());
                            Log.d(TAG, "✅ 媒体库统计获取成功: " + sumData);
                            callback.onSuccess(sumData);
                        } else {
                            Log.e(TAG, "❌ 媒体库统计API返回错误码: " + sumResponse.getCode() + ", 消息: " + sumResponse.getMessage());
                            callback.onError("API错误: " + sumResponse.getMessage());
                        }
                    } else {
                        Log.e(TAG, "❌ 媒体库统计请求失败: HTTP " + response.code() + " - " + response.message());
                        try {
                            String errorBody = response.errorBody() != null ? response.errorBody().string() : "无错误体";
                            Log.e(TAG, "❌ 错误响应体: " + errorBody);
                        } catch (Exception e) {
                            Log.e(TAG, "❌ 无法读取错误响应体", e);
                        }
                        callback.onError("请求失败: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<BaseResponse<Object>> call, @NonNull Throwable t) {
                    Log.e(TAG, "❌ 媒体库统计网络请求失败", t);
                    callback.onError("网络请求失败: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 媒体库统计请求异常", e);
            callback.onError("请求异常: " + e.getMessage());
        }
    }
    
    /**
     * 📊 解析媒体库统计数据
     */
    private Map<String, Integer> parseMediaDbSumData(Object data) {
        Map<String, Integer> result = new HashMap<>();
        try {
            Log.d(TAG, "📊 [解析调试] 开始解析统计数据");
            Log.d(TAG, "📊 [解析调试] 数据类型: " + (data != null ? data.getClass().getSimpleName() : "null"));
            Log.d(TAG, "📊 [解析调试] 数据内容: " + data);
            
            if (data == null) {
                Log.w(TAG, "📊 [解析调试] 数据为null");
                return result;
            }
            
            if (data instanceof Map) {
                Map<?, ?> dataMap = (Map<?, ?>) data;
                Log.d(TAG, "📊 [解析调试] Map数据，条目数: " + dataMap.size());
                Log.d(TAG, "📊 [解析调试] Map键集合: " + dataMap.keySet());
                
                for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
                    String key = entry.getKey().toString();
                    Object value = entry.getValue();
                    Log.d(TAG, "📊 [解析调试] 处理键值对: " + key + " = " + value + " (类型: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
                    
                    if (value instanceof Number) {
                        int count = ((Number) value).intValue();
                        result.put(key, count);
                        Log.d(TAG, "📊 [解析调试] Number类型解析成功: " + key + " = " + count);
                    } else if (value instanceof String) {
                        try {
                            int count = Integer.parseInt((String) value);
                            result.put(key, count);
                            Log.d(TAG, "📊 [解析调试] String类型解析成功: " + key + " = " + count);
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "⚠️ [解析调试] 无法解析字符串为数字: " + key + "=" + value, e);
                            result.put(key, 0);
                        }
                    } else {
                        Log.w(TAG, "⚠️ [解析调试] 不支持的值类型: " + key + "=" + value + " (类型: " + (value != null ? value.getClass().getSimpleName() : "null") + ")");
                        result.put(key, 0);
                    }
                }
            } else {
                Log.w(TAG, "⚠️ [解析调试] 数据不是Map类型: " + data.getClass().getSimpleName());
            }
            
            Log.d(TAG, "📊 [解析调试] 最终解析结果: " + result);
            Log.d(TAG, "📊 [解析调试] 解析的条目数: " + result.size());
        } catch (Exception e) {
            Log.e(TAG, "❌ [解析调试] 解析媒体库统计数据异常", e);
        }
        return result;
    }
    
    /**
     * 🎬 获取媒体详情
     */
    public void getItemDetail(String itemGuid, MediaCallback<MediaDetailResponse> callback) {
        Log.d(TAG, "🎬 [MediaManager] 获取媒体详情: " + itemGuid);
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("用户未登录");
            return;
        }
        
        try {
            // 生成签名
            String method = "GET";
            String url = "/v/api/v1/item/" + itemGuid;  // 🚨 [注意] 签名路径保持原样，与Web项目一致
            String data = "";
            Map<String, String> params = new HashMap<>();
            
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            Log.d(TAG, "🎬 [API调试] 调用媒体详情API: " + itemGuid);
            
            ApiService apiService = ApiClient.getApiService();
            Call<BaseResponse<MediaDetailResponse>> call = apiService.getItemDetail(authToken, authx, itemGuid);
            call.enqueue(new Callback<BaseResponse<MediaDetailResponse>>() {
                @Override
                public void onResponse(@NonNull Call<BaseResponse<MediaDetailResponse>> call, @NonNull Response<BaseResponse<MediaDetailResponse>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        BaseResponse<MediaDetailResponse> detailResponse = response.body();
                        if (detailResponse.getCode() == 0) {
                            Log.d(TAG, "✅ 媒体详情获取成功");
                            callback.onSuccess(detailResponse.getData());
                        } else {
                            Log.e(TAG, "❌ 媒体详情API返回错误: " + detailResponse.getMessage());
                            callback.onError("API错误: " + detailResponse.getMessage());
                        }
                    } else {
                        Log.e(TAG, "❌ 媒体详情请求失败: " + response.message());
                        callback.onError("请求失败: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<BaseResponse<MediaDetailResponse>> call, @NonNull Throwable t) {
                    Log.e(TAG, "❌ 媒体详情网络请求失败", t);
                    callback.onError("网络请求失败: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 媒体详情请求异常", e);
            callback.onError("请求异常: " + e.getMessage());
        }
    }
    
    /**
     * 🎬 获取播放信息
     */
    public void getPlayInfo(String itemGuid, MediaCallback<PlayInfoResponse> callback) {
        Log.d(TAG, "🎬 [MediaManager] 获取播放信息: " + itemGuid);
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("用户未登录");
            return;
        }
        
        try {
            // 构建请求数据
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("item_guid", itemGuid);
            
            // 生成签名
            String method = "POST";
            String url = "/v/api/v1/play/info";  // 🚨 [注意] 签名路径保持原样，与Web项目一致
            Gson gson = new Gson();
            String data = gson.toJson(requestData);
            Map<String, String> params = new HashMap<>();
            
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            Log.d(TAG, "🎬 [API调试] 调用播放信息API: " + itemGuid);
            
            ApiService apiService = ApiClient.getApiService();
            PlayInfoRequest request = new PlayInfoRequest(itemGuid);
            Call<PlayInfoResponse> call = apiService.getPlayInfo(authToken, authx, request);
            call.enqueue(new Callback<PlayInfoResponse>() {
                @Override
                public void onResponse(@NonNull Call<PlayInfoResponse> call, @NonNull Response<PlayInfoResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        PlayInfoResponse playInfo = response.body();
                        if (playInfo.getCode() == 0) {
                            Log.d(TAG, "✅ 播放信息获取成功");
                            callback.onSuccess(playInfo);
                        } else {
                            Log.e(TAG, "❌ 播放信息API返回错误: " + playInfo.getMessage());
                            callback.onError("API错误: " + playInfo.getMessage());
                        }
                    } else {
                        Log.e(TAG, "❌ 播放信息请求失败: " + response.message());
                        callback.onError("请求失败: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<PlayInfoResponse> call, @NonNull Throwable t) {
                    Log.e(TAG, "❌ 播放信息网络请求失败", t);
                    callback.onError("网络请求失败: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 播放信息请求异常", e);
            callback.onError("请求异常: " + e.getMessage());
        }
    }
    
    /**
     * 🔄 获取继续观看列表
     */
    public void getPlayList(MediaCallback<List<MediaItem>> callback) {
        Log.d(TAG, "🔄 开始获取继续观看列表");
        
        try {
            // 获取认证token
            String token = SharedPreferencesManager.getAuthToken();
            if (token == null || token.isEmpty()) {
                Log.e(TAG, "❌ 未找到认证token，请先登录");
                callback.onError("未登录");
                return;
            }
            
            // 移除Bearer前缀
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            // 生成签名 - 签名路径需要包含/v前缀（与Web项目保持一致）
            String path = "/v/api/v1/play/list";
            Map<String, String> params = new HashMap<>();
            String authx = SignatureUtils.generateSignature("GET", path, "", params);
            
            Log.d(TAG, "🔍 [API调试] 调用继续观看API");
            Log.d(TAG, "🔑 [API调试] Token: " + authToken.substring(0, Math.min(20, authToken.length())) + "...");
            Log.d(TAG, "🔐 [API调试] Authx: " + authx);
            
            // 调用API - 使用fnos-tv代理服务器
            ApiService apiService = ApiClient.getApiService();
            Call<PlayListResponse> call = apiService.getPlayList(authToken, authx);
            call.enqueue(new Callback<PlayListResponse>() {
                @Override
                public void onResponse(@NonNull Call<PlayListResponse> call, @NonNull Response<PlayListResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        PlayListResponse playListResponse = response.body();
                        if (playListResponse.getCode() == 0) {
                            // 转换为MediaItem列表
                            List<MediaItem> mediaItems = convertPlayListToMediaItems(playListResponse.getData());
                            Log.d(TAG, "✅ 继续观看列表获取成功，共 " + mediaItems.size() + " 个项目");
                            callback.onSuccess(mediaItems);
                        } else {
                            Log.e(TAG, "❌ 继续观看API返回错误: " + playListResponse.getMsg());
                            callback.onError("API错误: " + playListResponse.getMsg());
                        }
                    } else {
                        Log.e(TAG, "❌ 继续观看请求失败: " + response.message());
                        callback.onError("请求失败: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<PlayListResponse> call, @NonNull Throwable t) {
                    Log.e(TAG, "❌ 继续观看网络请求失败", t);
                    callback.onError("网络请求失败: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ 获取继续观看列表异常", e);
            callback.onError("获取数据异常: " + e.getMessage());
        }
    }
    
    /**
     * 📚 获取指定媒体库的内容列表
     * @param libraryGuid 媒体库GUID
     * @param pageSize 获取数量
     * @param callback 回调接口
     */
    public void getMediaLibraryItems(String libraryGuid, int pageSize, MediaCallback<List<MediaItem>> callback) {
        Log.d(TAG, "📚 开始获取媒体库内容: " + libraryGuid + ", 数量: " + pageSize);
        
        try {
            String token = SharedPreferencesManager.getAuthToken();
            if (token == null || token.isEmpty()) {
                Log.e(TAG, "❌ 获取媒体库内容失败：用户未登录");
                callback.onError("用户未登录");
                return;
            }
            
            // 移除Bearer前缀
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            // 创建请求参数
            MediaLibraryItemsRequest request = new MediaLibraryItemsRequest(libraryGuid, pageSize);
            
            // 生成签名 - 需要包含请求体数据，使用与Web项目相同的路径格式
            String path = "/v/api/v1/item/list";  // 添加/v前缀，与getMediaDbInfos()保持一致
            String requestBody = new Gson().toJson(request);
            Map<String, String> params = new HashMap<>();
            String authx = SignatureUtils.generateSignature("POST", path, requestBody, params);
            
            Log.d(TAG, "📚 [API调试] 调用媒体库内容API");
            Log.d(TAG, "📚 [API调试] 媒体库GUID: " + libraryGuid);
            Log.d(TAG, "📚 [API调试] 请求体: " + requestBody);
            Log.d(TAG, "📚 [API调试] Authx: " + authx);
            
            // 调用API
            ApiService apiService = ApiClient.getApiService();
            Call<MediaItemListResponse> call = apiService.getMediaLibraryItems(authToken, authx, request);
            
            call.enqueue(new Callback<MediaItemListResponse>() {
                @Override
                public void onResponse(Call<MediaItemListResponse> call, Response<MediaItemListResponse> response) {
                    Log.d(TAG, "📚 [API响应] 状态码: " + response.code());
                    Log.d(TAG, "📚 [API响应] URL: " + call.request().url().toString());
                    
                    if (response.isSuccessful() && response.body() != null) {
                        MediaItemListResponse apiResponse = response.body();
                        Log.d(TAG, "📚 [API响应] 响应代码: " + apiResponse.getCode());
                        Log.d(TAG, "📚 [API响应] 响应消息: " + apiResponse.getMessage());
                        
                        if (apiResponse.getCode() == 0) {
                            // 解析成功
                            List<MediaItem> mediaItems = convertToMediaItems(apiResponse.getData());
                            Log.d(TAG, "✅ 媒体库内容获取成功，共 " + mediaItems.size() + " 个项目");
                            callback.onSuccess(mediaItems);
                        } else {
                            Log.e(TAG, "❌ 媒体库内容API返回错误: " + apiResponse.getMessage());
                            callback.onError("API错误: " + apiResponse.getMessage());
                        }
                    } else {
                        Log.e(TAG, "❌ 媒体库内容请求失败: " + response.code());
                        try {
                            String errorBody = response.errorBody() != null ? response.errorBody().string() : "无错误信息";
                            Log.e(TAG, "❌ 错误响应体: " + errorBody);
                        } catch (Exception e) {
                            Log.e(TAG, "❌ 无法读取错误响应体", e);
                        }
                        callback.onError("网络请求失败: " + response.code());
                    }
                }
                
                @Override
                public void onFailure(Call<MediaItemListResponse> call, Throwable t) {
                    Log.e(TAG, "❌ 媒体库内容网络请求失败", t);
                    Log.e(TAG, "❌ 请求URL: " + call.request().url().toString());
                    Log.e(TAG, "❌ 请求方法: " + call.request().method());
                    Log.e(TAG, "❌ 错误详情: " + t.getClass().getSimpleName() + " - " + t.getMessage());
                    callback.onError("网络请求失败: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 获取媒体库内容异常", e);
            callback.onError("获取失败: " + e.getMessage());
        }
    }
    
    /**
     * 🔄 转换继续观看列表为MediaItem列表
     */
    private List<MediaItem> convertPlayListToMediaItems(List<PlayListResponse.PlayListItem> playList) {
        List<MediaItem> result = new ArrayList<>();
        
        if (playList == null || playList.isEmpty()) {
            Log.d(TAG, "📊 继续观看列表为空");
            return result;
        }
        
        Log.d(TAG, "🔄 解析继续观看项目，共 " + playList.size() + " 个");
        
        for (PlayListResponse.PlayListItem item : playList) {
            MediaItem mediaItem = new MediaItem();
            
            // 基础信息
            mediaItem.setId(item.getGuid());
            
            // 标题处理
            String displayTitle = item.getTvTitle();
            if (displayTitle == null || displayTitle.trim().isEmpty()) {
                displayTitle = item.getTitle();
            }
            if (displayTitle == null || displayTitle.trim().isEmpty()) {
                displayTitle = "未知标题";
            }
            mediaItem.setTitle(displayTitle);
            
            // 副标题：包含集数信息
            String subtitle = "";
            if (item.getSeasonNumber() > 0 && item.getEpisodeNumber() > 0) {
                subtitle = "S" + item.getSeasonNumber() + "E" + item.getEpisodeNumber();
                if (item.getTitle() != null && !item.getTitle().isEmpty()) {
                    subtitle += " · " + item.getTitle();
                }
            } else if (item.getType() != null) {
                subtitle = item.getType();
            }
            mediaItem.setSubtitle(subtitle);
            
            // 海报处理 - 使用正确的图片服务API
            String posterUrl = item.getPoster();
            if (posterUrl != null && !posterUrl.isEmpty()) {
                // 记录原始URL以便调试
                android.util.Log.d(TAG, "🖼️ [调试] 原始海报路径: " + posterUrl);
                
                if (!posterUrl.startsWith("http")) {
                    // 构建正确的图片服务URL
                    posterUrl = SharedPreferencesManager.getImageServiceUrl() + posterUrl + "?w=200";
                    android.util.Log.d(TAG, "🖼️ [调试] 构建的海报URL: " + posterUrl);
                }
            }
            mediaItem.setPosterUrl(posterUrl);
            
            // 类型
            mediaItem.setType(item.getType());
            
            // 观看进度 (基于ts和duration计算百分比)
            float watchProgress = item.getWatchProgress();
            mediaItem.setWatchedProgress(watchProgress);
            
            // 时长
            if (item.getDuration() > 0) {
                mediaItem.setDuration((int) (item.getDuration() / 60)); // 转换为分钟
            }
            
            result.add(mediaItem);
            
            Log.v(TAG, "✨ 解析继续观看: " + displayTitle + " (进度: " + String.format("%.1f", watchProgress) + "%)");
        }
        
        Log.d(TAG, "✅ 成功解析 " + result.size() + " 个继续观看项目");
        return result;
    }
    
    /**
     * 🔄 转换MediaDbListResponse数据为内部格式
     */
    private List<MediaDbItem> convertToMediaDbItems(List<MediaDbListResponse.MediaDb> data) {
        List<MediaDbItem> result = new ArrayList<>();
        
        if (data != null && !data.isEmpty()) {
            Log.d(TAG, "🔄 解析媒体库列表数据，共 " + data.size() + " 个");
            for (MediaDbListResponse.MediaDb mediaDb : data) {
                MediaDbItem item = new MediaDbItem(
                    mediaDb.getGuid(),
                    mediaDb.getName(),
                    mediaDb.getType()
                );
                result.add(item);
                Log.d(TAG, "📚 解析媒体库: " + mediaDb.getName() + " (" + mediaDb.getType() + ")");
            }
        } else {
            Log.w(TAG, "⚠️ 媒体库列表数据为空，使用模拟数据");
            // 模拟数据作为fallback
            result.add(new MediaDbItem("movie_db", "电影库", "movie"));
            result.add(new MediaDbItem("tv_db", "电视剧库", "tv"));
            result.add(new MediaDbItem("anime_db", "动漫库", "anime"));
        }
        
        return result;
    }
    
    /**
     * 🔄 转换API响应数据为MediaItem列表
     */
    private List<MediaItem> convertToMediaItems(MediaItemListResponse.MediaItemData data) {
        List<MediaItem> result = new ArrayList<>();
        
        Log.d(TAG, "🔄 解析媒体项目数据...");
        
        if (data != null && data.getList() != null && !data.getList().isEmpty()) {
            Log.d(TAG, "📊 API返回 " + data.getTotal() + " 个项目，实际列表 " + data.getList().size() + " 个");
            
            for (MediaItemListResponse.MediaItemInfo item : data.getList()) {
                MediaItem mediaItem = new MediaItem();
                
                // 基础信息
                mediaItem.setId(item.getGuid());
                
                // 标题处理：优先使用tv_title，如果为空则使用title
                String displayTitle = item.getTvTitle();
                if (displayTitle == null || displayTitle.trim().isEmpty()) {
                    displayTitle = item.getTitle();
                }
                if (displayTitle == null || displayTitle.trim().isEmpty()) {
                    displayTitle = "未知标题";
                }
                mediaItem.setTitle(displayTitle);
                
                // 副标题：包含集数信息和播出日期
                String subtitle = "";
                if (item.getSeasonNumber() > 0 && item.getEpisodeNumber() > 0) {
                    subtitle = "S" + item.getSeasonNumber() + "E" + item.getEpisodeNumber();
                    if (item.getParentTitle() != null && !item.getParentTitle().isEmpty()) {
                        subtitle += " · " + item.getParentTitle();
                    }
                } else if (item.getAirDate() != null && !item.getAirDate().isEmpty()) {
                    subtitle = item.getAirDate();
                }
                if (item.getRuntime() > 0) {
                    subtitle += (subtitle.isEmpty() ? "" : " · ") + item.getRuntime() + "分钟";
                }
                mediaItem.setSubtitle(subtitle);
                
                // 类型和分类
                mediaItem.setType(item.getType());
                
                // 海报URL处理
                if (item.getPoster() != null && !item.getPoster().isEmpty()) {
                    // 如果是相对路径，需要拼接完整URL
                    String posterUrl = item.getPoster();
                    android.util.Log.d(TAG, "🖼️ [调试] MediaList原始海报路径: " + posterUrl);
                    
                    if (!posterUrl.startsWith("http")) {
                        // 构建正确的图片服务URL
                        posterUrl = SharedPreferencesManager.getImageServiceUrl() + posterUrl + "?w=200";
                        android.util.Log.d(TAG, "🖼️ [调试] MediaList构建的海报URL: " + posterUrl);
                    }
                    mediaItem.setPosterUrl(posterUrl);
                }
                
                // 评分
                try {
                    if (item.getVoteAverage() != null && !item.getVoteAverage().isEmpty()) {
                        mediaItem.setRating(Float.parseFloat(item.getVoteAverage()));
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "⚠️ 评分解析失败: " + item.getVoteAverage());
                }
                
                // 观看状态
                mediaItem.setFavorite(item.getIsFavorite() == 1);
                mediaItem.setWatchedProgress(item.getWatched() > 0 ? 100 : 0);
                mediaItem.setLastWatchedTime(item.getWatchedTs());
                
                // 时长
                if (item.getDuration() > 0) {
                    mediaItem.setDuration(item.getDuration() / 60); // 转换为分钟
                }
                
                // 剧集信息
                if (item.getNumberOfEpisodes() > 0) {
                    mediaItem.setTotalEpisodes(item.getNumberOfEpisodes());
                }
                
                result.add(mediaItem);
                
                Log.v(TAG, "✨ 解析媒体项目: " + displayTitle + " (" + item.getType() + ")");
            }
            
            Log.d(TAG, "✅ 成功解析 " + result.size() + " 个媒体项目");
        } else {
            Log.w(TAG, "⚠️ API返回的媒体项目数据为空");
        }
        
        return result;
    }
    
    /**
     * 📚 媒体库项目数据类
     */
    public static class MediaDbItem {
        private String guid;
        private String name;
        private String category;
        private int itemCount = 0; // 媒体库包含的项目数量
        
        public MediaDbItem(String guid, String name, String category) {
            this.guid = guid;
            this.name = name;
            this.category = category;
        }
        
        public MediaDbItem(String guid, String name, String category, int itemCount) {
            this.guid = guid;
            this.name = name;
            this.category = category;
            this.itemCount = itemCount;
        }
        
        // Getters and Setters
        public String getGuid() { return guid; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public int getItemCount() { return itemCount; }
        public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    }
    
    /**
     * 🎬 获取剧集列表
     */
    public void getEpisodeList(String seasonGuid, MediaCallback<List<EpisodeListResponse.Episode>> callback) {
        Log.d(TAG, "🎬 [MediaManager] 获取剧集列表: " + seasonGuid);
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("用户未登录");
            return;
        }
        
        try {
            String method = "GET";
            String url = "/v/api/v1/episode/list/" + seasonGuid;
            String data = "";
            Map<String, String> params = new HashMap<>();
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            Log.d(TAG, "🎬 [API调试] 调用剧集列表API: " + seasonGuid);
            
            ApiService apiService = ApiClient.getApiService();
            Call<BaseResponse<List<EpisodeListResponse.Episode>>> call = apiService.getEpisodeListForPlay(authToken, authx, seasonGuid);
            
            call.enqueue(new Callback<BaseResponse<List<EpisodeListResponse.Episode>>>() {
                @Override
                public void onResponse(@NonNull Call<BaseResponse<List<EpisodeListResponse.Episode>>> call, @NonNull Response<BaseResponse<List<EpisodeListResponse.Episode>>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        BaseResponse<List<EpisodeListResponse.Episode>> baseResponse = response.body();
                        if (baseResponse.getCode() == 0) {
                            List<EpisodeListResponse.Episode> episodes = baseResponse.getData();
                            Log.d(TAG, "✅ 剧集列表获取成功，共 " + (episodes != null ? episodes.size() : 0) + " 集");
                            callback.onSuccess(episodes);
                        } else {
                            Log.e(TAG, "❌ 剧集列表API返回错误: " + baseResponse.getMessage());
                            callback.onError("API错误: " + baseResponse.getMessage());
                        }
                    } else {
                        Log.e(TAG, "❌ 剧集列表请求失败: " + response.message());
                        callback.onError("请求失败: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<BaseResponse<List<EpisodeListResponse.Episode>>> call, @NonNull Throwable t) {
                    Log.e(TAG, "❌ 剧集列表网络请求失败", t);
                    callback.onError("网络请求失败: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 剧集列表请求异常", e);
            callback.onError("请求异常: " + e.getMessage());
        }
    }
    
    /**
     * 🎬 根据季GUID和剧集编号开始播放
     */
    public void startPlayEpisodeByNumber(String seasonGuid, int episodeNumber, MediaCallback<String> callback) {
        Log.d(TAG, "🎬 [MediaManager] 开始播放: 季GUID=" + seasonGuid + ", 第" + episodeNumber + "集");
        
        // 步骤1: 获取剧集列表，找到对应剧集的GUID
        getEpisodeList(seasonGuid, new MediaCallback<List<EpisodeListResponse.Episode>>() {
            @Override
            public void onSuccess(List<EpisodeListResponse.Episode> episodes) {
                if (episodes == null || episodes.isEmpty()) {
                    Log.w(TAG, "⚠️ 剧集列表为空，尝试直接播放");
                    // 如果没有找到剧集列表，尝试直接使用季GUID播放
                    startPlayEpisodeWithRealGuid(seasonGuid, callback);
                    return;
                }
                
                // 查找对应编号的剧集
                EpisodeListResponse.Episode targetEpisode = null;
                for (EpisodeListResponse.Episode episode : episodes) {
                    if (episode.getEpisodeNumber() == episodeNumber) {
                        targetEpisode = episode;
                        break;
                    }
                }
                
                if (targetEpisode == null) {
                    Log.w(TAG, "⚠️ 没有找到第" + episodeNumber + "集，尝试直接播放");
                    // 如果没有找到对应剧集，尝试直接播放
                    startPlayEpisodeWithRealGuid(seasonGuid, callback);
                    return;
                }
                
                Log.d(TAG, "✅ 找到第" + episodeNumber + "集: " + targetEpisode.getGuid());
                
                // 步骤2: 使用真实的剧集GUID获取播放信息
                startPlayEpisodeWithRealGuid(targetEpisode.getGuid(), callback);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 获取剧集列表失败，尝试直接播放: " + error);
                // 如果获取剧集列表失败，尝试直接使用传入的GUID播放
                startPlayEpisodeWithRealGuid(seasonGuid, callback);
            }
        });
    }
    
    /**
     * 🎬 使用真实GUID开始播放（完整流程）
     */
    private void startPlayEpisodeWithRealGuid(String episodeGuid, MediaCallback<String> callback) {
        Log.d(TAG, "🎬 [MediaManager] 使用真实GUID播放: " + episodeGuid);
        
        // 调用播放信息API获取真实播放数据
        getPlayInfo(episodeGuid, new MediaCallback<PlayInfoResponse>() {
            @Override
            public void onSuccess(PlayInfoResponse playInfoResponse) {
                Log.d(TAG, "✅ 播放信息获取成功");
                
                // 从PlayInfoResponse中提取播放链接
                if (playInfoResponse != null && playInfoResponse.getData() != null) {
                    Object playData = playInfoResponse.getData();
                    // 🚨 临时简化：直接从getData()对象中提取播放信息
                    // 实际应该解析完整的播放数据结构
                    String playLink = extractPlayLinkFromData(playData);
                    
                    if (playLink != null && !playLink.isEmpty()) {
                        // 构建完整的播放URL
                        String fullPlayUrl;
                        if (playLink.startsWith("http")) {
                            fullPlayUrl = playLink;
                        } else {
                            fullPlayUrl = SharedPreferencesManager.getPlayServiceUrl() + playLink;
                        }
                        
                        Log.d(TAG, "✅ 真实播放URL: " + fullPlayUrl);
                        callback.onSuccess(fullPlayUrl);
                        return;
                    }
                }
                
                // 如果没有获取到播放链接，提供错误信息
                callback.onError("播放信息中没有找到播放链接");
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ 获取播放信息失败: " + error);
                callback.onError("获取播放信息失败: " + error);
            }
        });
    }
    
    /**
     * 🎬 从播放数据中提取播放链接
     */
    private String extractPlayLinkFromData(Object data) {
        if (data != null) {
            Log.d(TAG, "📊 播放数据类型: " + data.getClass().getSimpleName());
            Log.d(TAG, "📊 播放数据内容: " + data.toString());
            
            // ✅ [修复] 从PlayInfoData中提取播放链接
            if (data instanceof PlayInfoResponse.PlayInfoData) {
                PlayInfoResponse.PlayInfoData playData = (PlayInfoResponse.PlayInfoData) data;
                Log.d(TAG, "📊 PlayInfoData对象: " + playData.toString());
                
                // 首先尝试直接的play_link字段
                String playLink = playData.getPlayLink();
                if (playLink != null && !playLink.isEmpty()) {
                    Log.d(TAG, "✅ 从PlayInfoData中提取到直接播放链接: " + playLink);
                    return playLink;
                }
                
                // 🎯 [核心修复] 从media_guid构建播放链接
                String mediaGuid = playData.getMediaGuid();
                if (mediaGuid != null && !mediaGuid.isEmpty()) {
                    String constructedPlayLink = "/v/media/" + mediaGuid + "/preset.m3u8";
                    Log.d(TAG, "✅ 从PlayInfoData的media_guid构建播放链接: " + constructedPlayLink);
                    return constructedPlayLink;
                }
                
                Log.w(TAG, "⚠️ PlayInfoData中没有media_guid和play_link: mediaGuid=" + mediaGuid + ", playLink=" + playLink);
                return null;
            }
            
            // 如果是其他类型，尝试解析JSON字符串
            String dataStr = data.toString();
            if (dataStr.contains("play_link")) {
                try {
                    int startIndex = dataStr.indexOf("\"play_link\":\"");
                    if (startIndex != -1) {
                        startIndex += 13; // 跳过 "play_link":"
                        int endIndex = dataStr.indexOf("\"", startIndex);
                        if (endIndex != -1) {
                            String playLink = dataStr.substring(startIndex, endIndex);
                            Log.d(TAG, "✅ 从JSON字符串中提取到播放链接: " + playLink);
                            return playLink;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ 解析播放链接失败", e);
                }
            }
            
            // 🚨 [临时解决方案] 从media_guid构建播放URL (基于web项目逻辑)
            if (dataStr.contains("media_guid")) {
                try {
                    int startIndex = dataStr.indexOf("\"media_guid\":\"");
                    if (startIndex != -1) {
                        startIndex += 14; // "media_guid":"的长度
                        int endIndex = dataStr.indexOf("\"", startIndex);
                        if (endIndex != -1) {
                            String mediaGuid = dataStr.substring(startIndex, endIndex);
                            String playUrl = "/v/media/" + mediaGuid + "/preset.m3u8";
                            Log.d(TAG, "✅ 从toString中的media_guid构建播放链接: " + playUrl);
                            return playUrl;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ 从media_guid构建播放链接失败", e);
                }
            }
            
            Log.w(TAG, "⚠️ 无法从播放数据中提取播放链接");
        }
        return null;
    }
    
    /**
     * 🎬 开始播放剧集（兼容旧接口）
     */
    public void startPlayEpisode(String episodeGuid, MediaCallback<String> callback) {
        Log.d(TAG, "🎬 [MediaManager] 兼容接口播放: " + episodeGuid);
        // 直接使用真实GUID播放
        startPlayEpisodeWithRealGuid(episodeGuid, callback);
    }
    
    /**
     * 🎬 获取流媒体列表（获取真正的媒体流GUID）
     */
    public void getStreamList(String episodeGuid, MediaCallback<StreamListResponse.StreamData> callback) {
        Log.d(TAG, "🎬 [流媒体列表] 开始获取流媒体列表: " + episodeGuid);
        
        try {
            Call<BaseResponse<StreamListResponse.StreamData>> call = ApiClient.getApiService().getStreamList(
                SharedPreferencesManager.getAuthToken(),
                null, // 签名将由拦截器自动添加
                episodeGuid
            );
            
            call.enqueue(new retrofit2.Callback<BaseResponse<StreamListResponse.StreamData>>() {
                @Override
                public void onResponse(Call<BaseResponse<StreamListResponse.StreamData>> call,
                                     retrofit2.Response<BaseResponse<StreamListResponse.StreamData>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        BaseResponse<StreamListResponse.StreamData> baseResponse = response.body();
                        Log.d(TAG, "🔍 [调试] API响应码: " + baseResponse.getCode());
                        Log.d(TAG, "🔍 [调试] API响应消息: " + baseResponse.getMessage());
                        Log.d(TAG, "🔍 [调试] API响应数据是否为null: " + (baseResponse.getData() == null));
                        
                        if (baseResponse.isSuccess() && baseResponse.getData() != null) {
                            StreamListResponse.StreamData streamData = baseResponse.getData();
                            Log.d(TAG, "✅ [流媒体列表] 获取成功");
                            
                            // 🔍 详细调试信息
                            Log.d(TAG, "🔍 [调试] videoStreams是否为null: " + (streamData.getVideoStreams() == null));
                            Log.d(TAG, "🔍 [调试] audioStreams是否为null: " + (streamData.getAudioStreams() == null));
                            Log.d(TAG, "🔍 [调试] files是否为null: " + (streamData.getFiles() == null));
                            
                            Log.d(TAG, "📊 视频流数量: " + (streamData.getVideoStreams() != null ? streamData.getVideoStreams().size() : 0));
                            Log.d(TAG, "📊 音频流数量: " + (streamData.getAudioStreams() != null ? streamData.getAudioStreams().size() : 0));
                            Log.d(TAG, "📊 文件数量: " + (streamData.getFiles() != null ? streamData.getFiles().size() : 0));
                            
                            // 输出第一个视频流和音频流的GUID
                            if (streamData.getVideoStreams() != null && !streamData.getVideoStreams().isEmpty()) {
                                Log.d(TAG, "🎬 第一个视频流GUID: " + streamData.getVideoStreams().get(0).getGuid());
                            }
                            if (streamData.getAudioStreams() != null && !streamData.getAudioStreams().isEmpty()) {
                                Log.d(TAG, "🎵 第一个音频流GUID: " + streamData.getAudioStreams().get(0).getGuid());
                            }
                            
                            callback.onSuccess(streamData);
                        } else {
                            Log.e(TAG, "❌ [流媒体列表] 响应数据错误: " + baseResponse.getMessage());
                            callback.onError("获取流媒体列表失败: " + baseResponse.getMessage());
                        }
                    } else {
                        Log.e(TAG, "❌ [流媒体列表] 请求失败: " + response.code());
                        callback.onError("获取流媒体列表失败: HTTP " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<BaseResponse<StreamListResponse.StreamData>> call, Throwable t) {
                    Log.e(TAG, "❌ [流媒体列表] 网络请求失败", t);
                    callback.onError("网络错误: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ [流媒体列表] 请求创建失败", e);
            callback.onError("请求创建失败: " + e.getMessage());
        }
    }

    /**
     * 🎬 调用播放API激活播放会话（完全复制web项目逻辑）
     */
    public void callPlayApiWithStreamData(String mediaGuid, String videoGuid, String audioGuid, 
                                        StreamListResponse.StreamData streamData, PlaySessionCallback callback) {
        Log.d(TAG, "🎬 [播放API] 开始调用play API（完全复制web逻辑）");
        Log.d(TAG, "📊 参数: media_guid=" + mediaGuid + ", video_guid=" + videoGuid + ", audio_guid=" + audioGuid);
        
        try {
            // 🔧 完全复制web项目逻辑：直接使用第一个视频流，不做智能选择
            if (streamData.getVideoStreams() == null || streamData.getVideoStreams().isEmpty()) {
                Log.e(TAG, "❌ 视频流列表为空");
                callback.onPlaySessionError("视频流列表为空");
                return;
            }
            
            // 🔧 web逻辑：直接使用第一个视频流，但Android需要检查HEVC兼容性
            StreamListResponse.VideoStream preferredVideoStream = streamData.getVideoStreams().get(0);
            
            // 🔧 Android特殊处理：检查第一个流是否为不兼容的HEVC
            if (isIncompatibleHEVC(preferredVideoStream)) {
                Log.w(TAG, "⚠️ [Android兼容性] 检测到不兼容的HEVC流 - codec:" + preferredVideoStream.getCodec() + 
                      ", profile:" + preferredVideoStream.getProfile() + ", bitDepth:" + preferredVideoStream.getBitDepth());
                
                // 如果有多个视频流，尝试寻找8bit版本
                if (streamData.getVideoStreams().size() > 1) {
                    StreamListResponse.VideoStream compatibleStream = findCompatibleStream(streamData.getVideoStreams());
                    if (compatibleStream != null) {
                        preferredVideoStream = compatibleStream;
                        Log.d(TAG, "✅ [Android兼容性] 找到兼容流: " + compatibleStream.getGuid() + 
                              ", Profile: " + compatibleStream.getProfile() + ", BitDepth: " + compatibleStream.getBitDepth());
                    } else {
                        Log.w(TAG, "⚠️ [Android兼容性] 未找到兼容流，将使用软件解码尝试");
                    }
                } else {
                    Log.w(TAG, "⚠️ [Android兼容性] 只有一个视频流且不兼容，将使用软件解码尝试");
                }
            } else {
                Log.d(TAG, "✅ [Android兼容性] 视频流兼容 - codec:" + preferredVideoStream.getCodec() + 
                      ", profile:" + preferredVideoStream.getProfile() + ", bitDepth:" + preferredVideoStream.getBitDepth());
            }
            
            String preferredEncoder = preferredVideoStream.getCodec() != null ? preferredVideoStream.getCodec() : "unknown";
            
            Log.d(TAG, "🔧 [复制web逻辑] 使用第一个视频流，不进行智能选择");
            Log.d(TAG, "🔧 [复制web逻辑] 原始视频流信息: codec=" + preferredVideoStream.getCodec() + 
                  ", guid=" + preferredVideoStream.getGuid() + ", bps=" + (preferredVideoStream.getBitrate()/1000000.0) + "Mbps" +
                  ", streams_count=" + streamData.getVideoStreams().size());
            
            // 🔧 web逻辑：按照特定规则选择media_guid  
            String localMediaGuid = selectCorrectMediaGuid(streamData);
            
            // 🔧 web逻辑：使用原始分辨率和码率
            String originalResolution = "1080";  // 默认1080p
            long originalBitrate = preferredVideoStream.getBitrate();
            
            // 🔧 计算分辨率（优先使用resolution_type）
            if (preferredVideoStream.getResolution() != null && !preferredVideoStream.getResolution().isEmpty()) {
                originalResolution = preferredVideoStream.getResolution();
                Log.d(TAG, "🔧 [复制web逻辑] 检测到分辨率类型: " + originalResolution + 
                      " (" + preferredVideoStream.getWidth() + "x" + preferredVideoStream.getHeight() + ")");
            } else if (preferredVideoStream.getWidth() > 0 && preferredVideoStream.getHeight() > 0) {
                originalResolution = preferredVideoStream.getHeight() + "p";
                Log.d(TAG, "🔧 [复制web逻辑] 根据尺寸计算分辨率: " + originalResolution + 
                      " (" + preferredVideoStream.getWidth() + "x" + preferredVideoStream.getHeight() + ")");
            }
            
            Log.d(TAG, "🔧 【完全复制web】使用参数: encoder=" + preferredEncoder + ", resolution=" + originalResolution + 
                  ", bitrate=" + (originalBitrate / 1000000.0) + "Mbps");
            Log.d(TAG, "🔧 【完全复制web】media_guid: " + localMediaGuid + " -> video_guid: " + preferredVideoStream.getGuid());
            
            // 🔧 web逻辑：直接构造请求参数
            PlayApiRequest request = new PlayApiRequest(localMediaGuid, preferredVideoStream.getGuid(), audioGuid, 
                                                       preferredEncoder, originalResolution, originalBitrate);
            Log.d(TAG, "📊 请求数据（完全复制web）: " + request.toString());
            
            Call<BaseResponse<PlayApiResponse.PlaySessionData>> call = ApiClient.getPlayApiService().startPlaySession(request);
            call.enqueue(new retrofit2.Callback<BaseResponse<PlayApiResponse.PlaySessionData>>() {
                @Override
                public void onResponse(Call<BaseResponse<PlayApiResponse.PlaySessionData>> call, 
                                     retrofit2.Response<BaseResponse<PlayApiResponse.PlaySessionData>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        BaseResponse<PlayApiResponse.PlaySessionData> baseResponse = response.body();
                        if (baseResponse.isSuccess() && baseResponse.getData() != null) {
                            PlayApiResponse.PlaySessionData sessionData = baseResponse.getData();
                            Log.d(TAG, "✅ [播放API] 播放会话激活成功（原画）: " + sessionData.toString());
                            
                            // 🎯 使用API返回的play_link构建播放URL
                            String realPlayUrl;
                            if (sessionData.getPlay_link() != null && !sessionData.getPlay_link().isEmpty()) {
                                realPlayUrl = SharedPreferencesManager.getPlayServiceUrl() + sessionData.getPlay_link();
                                Log.d(TAG, "🎬 使用play_link构建URL（原画）: " + realPlayUrl);
                            } else {
                                // 回退到使用media_guid
                                realPlayUrl = SharedPreferencesManager.getPlayServiceUrl() + 
                                            "/v/media/" + sessionData.getMedia_guid() + "/preset.m3u8";
                                Log.d(TAG, "🎬 回退使用media_guid构建URL（原画）: " + realPlayUrl);
                            }
                            
                            callback.onPlaySessionSuccess(realPlayUrl, sessionData);
                        } else {
                            Log.e(TAG, "❌ [播放API] 响应数据错误（原画）: " + baseResponse.getMessage());
                            callback.onPlaySessionError("播放会话激活失败: " + baseResponse.getMessage());
                        }
                    } else {
                        Log.e(TAG, "❌ [播放API] 请求失败（原画）: " + response.code());
                        callback.onPlaySessionError("播放会话激活失败: HTTP " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<BaseResponse<PlayApiResponse.PlaySessionData>> call, Throwable t) {
                    Log.e(TAG, "❌ [播放API] 网络请求失败（完全复制web）", t);
                    callback.onPlaySessionError("网络错误: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ [播放API] 请求创建失败（完全复制web）", e);
            callback.onPlaySessionError("请求创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 🔧 按照web项目逻辑选择正确的media_guid (保留此方法，因为web和Android都需要)
     * 复制web项目的files选择逻辑
     */
    private String selectCorrectMediaGuid(StreamListResponse.StreamData streamData) {
        if (streamData.getFiles() == null || streamData.getFiles().isEmpty()) {
            Log.w(TAG, "⚠️ [媒体GUID选择] files列表为空，返回null");
            return null;
        }
        
        Log.d(TAG, "🔍 [媒体GUID选择] 开始分析 " + streamData.getFiles().size() + " 个文件");
        
        // 🔧 复制web项目的正则表达式逻辑: /\d+-\d+-\S+/
        String regex = "\\d+-\\d+-\\S+";
        
        // 首先查找不匹配正则表达式的文件
        for (int i = 0; i < streamData.getFiles().size(); i++) {
            StreamListResponse.FileStream file = streamData.getFiles().get(i);
            String path = file.getPath() != null ? file.getPath() : "";
            
            Log.d(TAG, "🔍 [媒体GUID选择] 文件" + (i+1) + ": path=" + path + ", guid=" + file.getGuid());
            
            if (!path.matches(regex)) {
                Log.d(TAG, "✅ [媒体GUID选择] 找到不匹配正则的文件: " + file.getGuid() + " (path: " + path + ")");
                return file.getGuid();
            } else {
                Log.d(TAG, "🔍 [媒体GUID选择] 文件匹配正则，跳过: " + path);
            }
        }
        
        // 如果没有找到不匹配的，使用第一个文件
        StreamListResponse.FileStream firstFile = streamData.getFiles().get(0);
        Log.d(TAG, "🔧 [媒体GUID选择] 未找到不匹配正则的文件，使用第一个: " + firstFile.getGuid() + " (path: " + firstFile.getPath() + ")");
        return firstFile.getGuid();
    }
    
    /**
     * 🔧 检查视频流是否为不兼容的HEVC格式
     */
    private boolean isIncompatibleHEVC(StreamListResponse.VideoStream stream) {
        String codec = stream.getCodec() != null ? stream.getCodec().toLowerCase() : "";
        String profile = stream.getProfile() != null ? stream.getProfile() : "";  
        int bitDepth = stream.getBitDepth();
        
        // 只有10bit HEVC才不兼容
        boolean isHEVC = codec.contains("hevc") || codec.contains("h265");
        boolean is10bit = bitDepth > 8 || profile.toLowerCase().contains("10");
        
        return isHEVC && is10bit;
    }
    
    /**
     * 🔧 寻找Android兼容的视频流（8bit优先）
     */
    private StreamListResponse.VideoStream findCompatibleStream(List<StreamListResponse.VideoStream> videoStreams) {
        for (StreamListResponse.VideoStream stream : videoStreams) {
            if (!isIncompatibleHEVC(stream)) {
                return stream; // 找到第一个兼容的流
            }
        }
        return null; // 没有找到兼容的流
    }

    /**
     * 🎬 调用播放API激活播放会话（兼容方法）
     */
    public void callPlayApi(String mediaGuid, String videoGuid, String audioGuid, PlaySessionCallback callback) {
        Log.d(TAG, "🎬 [播放API] 开始调用play API");
        Log.d(TAG, "📊 参数: media_guid=" + mediaGuid + ", video_guid=" + videoGuid + ", audio_guid=" + audioGuid);
        
        try {
            PlayApiRequest request = new PlayApiRequest(mediaGuid, videoGuid, audioGuid);
            Log.d(TAG, "📊 请求数据: " + request.toString());
            
            Call<BaseResponse<PlayApiResponse.PlaySessionData>> call = ApiClient.getPlayApiService().startPlaySession(request);
            call.enqueue(new retrofit2.Callback<BaseResponse<PlayApiResponse.PlaySessionData>>() {
                @Override
                public void onResponse(Call<BaseResponse<PlayApiResponse.PlaySessionData>> call, 
                                     retrofit2.Response<BaseResponse<PlayApiResponse.PlaySessionData>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        BaseResponse<PlayApiResponse.PlaySessionData> baseResponse = response.body();
                        if (baseResponse.isSuccess() && baseResponse.getData() != null) {
                            PlayApiResponse.PlaySessionData sessionData = baseResponse.getData();
                            Log.d(TAG, "✅ [播放API] 播放会话激活成功: " + sessionData.toString());
                            
                            // 🎯 使用API返回的play_link构建播放URL
                            String realPlayUrl;
                            if (sessionData.getPlay_link() != null && !sessionData.getPlay_link().isEmpty()) {
                                realPlayUrl = SharedPreferencesManager.getPlayServiceUrl() + sessionData.getPlay_link();
                                Log.d(TAG, "🎬 使用play_link构建URL: " + realPlayUrl);
                            } else {
                                // 回退到使用media_guid
                                realPlayUrl = SharedPreferencesManager.getPlayServiceUrl() + 
                                            "/v/media/" + sessionData.getMedia_guid() + "/preset.m3u8";
                                Log.d(TAG, "🎬 回退使用media_guid构建URL: " + realPlayUrl);
                            }
                            
                            callback.onPlaySessionSuccess(realPlayUrl, sessionData);
                        } else {
                            Log.e(TAG, "❌ [播放API] 响应数据错误: " + baseResponse.getMessage());
                            callback.onPlaySessionError("播放会话激活失败: " + baseResponse.getMessage());
                        }
                    } else {
                        Log.e(TAG, "❌ [播放API] 请求失败: " + response.code());
                        callback.onPlaySessionError("播放会话激活失败: HTTP " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<BaseResponse<PlayApiResponse.PlaySessionData>> call, Throwable t) {
                    Log.e(TAG, "❌ [播放API] 网络请求失败", t);
                    callback.onPlaySessionError("网络错误: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ [播放API] 请求创建失败", e);
            callback.onPlaySessionError("请求创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 🎬 获取弹幕数据
     */
    public void getDanmu(String doubanId, int episodeNumber, String episodeTitle, String title,
                        int seasonNumber, String episodeGuid, String parentGuid, 
                        MediaCallback<List<Danmu>> callback) {
        Log.d(TAG, "🎬 [弹幕] 开始获取弹幕数据");
        Log.d(TAG, "📊 参数: 豆瓣ID=" + doubanId + ", 集数=" + episodeNumber + ", 标题=" + title);
        
        try {
            Call<ResponseBody> call = ApiClient.getDanmuApiService().getDanmu(
                doubanId, episodeNumber, episodeTitle, title, 
                seasonNumber, true, episodeGuid, parentGuid
            );
            
            call.enqueue(new retrofit2.Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String jsonString = response.body().string();
//                            Log.d(TAG, "🎬 [弹幕] API响应: " + jsonString);
                            
                            // 解析JSON：{ "集数": [ {弹幕对象} ] }
                            Gson gson = new Gson();
                            TypeToken<Map<String, List<Danmu>>> typeToken = new TypeToken<Map<String, List<Danmu>>>() {};
                            Map<String, List<Danmu>> danmuMap = gson.fromJson(jsonString, typeToken.getType());
                            
                            if (danmuMap != null && !danmuMap.isEmpty()) {
                                // 获取对应集数的弹幕
                                String episodeKey = String.valueOf(episodeNumber);
                                List<Danmu> danmuList = danmuMap.get(episodeKey);
                                
                                if (danmuList != null && !danmuList.isEmpty()) {
                                    Log.d(TAG, "✅ [弹幕] 获取成功: 第" + episodeNumber + "集共" + danmuList.size() + "条弹幕");
                                    callback.onSuccess(danmuList);
                                } else {
                                    Log.w(TAG, "⚠️ [弹幕] 第" + episodeNumber + "集暂无弹幕数据");
                                    callback.onSuccess(new ArrayList<>()); // 返回空列表
                                }
                            } else {
                                Log.w(TAG, "⚠️ [弹幕] 弹幕数据为空");
                                callback.onSuccess(new ArrayList<>());
                            }
                            
                        } catch (Exception e) {
                            Log.e(TAG, "❌ [弹幕] JSON解析失败", e);
                            callback.onError("弹幕数据解析失败: " + e.getMessage());
                        }
                    } else {
                        Log.e(TAG, "❌ [弹幕] 请求失败: " + response.code());
                        callback.onError("获取弹幕失败: HTTP " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.e(TAG, "❌ [弹幕] 网络请求失败", t);
                    callback.onError("网络错误: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ [弹幕] 请求创建失败", e);
            callback.onError("请求创建失败: " + e.getMessage());
        }
    }

    /**
     * 播放会话回调接口
     */
    public interface PlaySessionCallback {
        void onPlaySessionSuccess(String playUrl, PlayApiResponse.PlaySessionData sessionData);
        void onPlaySessionError(String errorMessage);
    }

    /**
     * 📲 媒体数据回调接口
     */
    public interface MediaCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }
}

