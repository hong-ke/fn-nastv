package com.mynas.nastv.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.mynas.nastv.model.BaseResponse;
import com.mynas.nastv.model.MediaDbListResponse;
import com.mynas.nastv.model.MediaDetailResponse;
import com.mynas.nastv.model.MediaItem;
import com.mynas.nastv.model.MediaItemListResponse;
import com.mynas.nastv.model.PlayInfoRequest;
import com.mynas.nastv.model.PlayInfoResponse;
import com.mynas.nastv.model.PlayListResponse;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.model.SeasonListResponse;
import com.mynas.nastv.model.WatchHistoryResponse;
import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.network.ApiService;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.SignatureUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 📚 Media Manager
 * Responsible for fetching media data, matching the logic of fntv-electron.
 */
public class MediaManager {
    private static final String TAG = "MediaManager";
    
    private Context context;
    
    // Cache
    private List<MediaDbItem> mediaDbList = new ArrayList<>();
    private Map<String, List<MediaItem>> mediaDbInfos = new HashMap<>();
    
    public MediaManager(Context context) {
        this.context = context;
        SharedPreferencesManager.initialize(context);
    }
    
    /**
     * 📚 Get Media DB List
     * Matches web: GetMediaDbList
     */
    public void getMediaDbList(MediaCallback<List<MediaDbItem>> callback) {
        Log.d(TAG, "🔍 [MediaManager] Getting media DB list...");
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("User not logged in");
            return;
        }
        
        try {
            // Generate signature
            String method = "GET";
            String url = "/v/api/v1/mediadb/list";
            String data = "";
            Map<String, String> params = new HashMap<>();
            
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            ApiService apiService = ApiClient.getApiService();
            Call<MediaDbListResponse> call = apiService.getMediaDbList(authToken, authx);
            
            call.enqueue(new Callback<MediaDbListResponse>() {
                @Override
                public void onResponse(@NonNull Call<MediaDbListResponse> call, @NonNull Response<MediaDbListResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        MediaDbListResponse dbResponse = response.body();
                        if (dbResponse.getCode() == 0) {
                            mediaDbList = convertToMediaDbItems(dbResponse.getData());
                            Log.d(TAG, "✅ Media DB list success: " + mediaDbList.size());
                            callback.onSuccess(mediaDbList);
                        } else {
                            callback.onError("API Error: " + dbResponse.getMessage());
                        }
                    } else {
                        callback.onError("Request failed: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<MediaDbListResponse> call, @NonNull Throwable t) {
                    callback.onError("Network error: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            callback.onError("Exception: " + e.getMessage());
        }
    }
    
    /**
     * 📖 Get Item List (Folder content)
     * Matches web: getItemList
     */
    public void getMediaDbInfos(String guid, MediaCallback<List<MediaItem>> callback) {
        getMediaLibraryItems(guid, 100, callback);
    }

    /**
     * 📖 Get Library Items with Limit
     * 使用与 Web 端一致的请求参数
     */
    public void getMediaLibraryItems(String guid, int limit, MediaCallback<List<MediaItem>> callback) {
        Log.d(TAG, "🔍 [MediaManager] Getting item list for: " + guid + ", limit: " + limit);
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("User not logged in");
            return;
        }
        
        try {
            // 使用与 Web 端一致的请求参数
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("ancestor_guid", guid);  // Web 端使用 ancestor_guid
            
            // tags 过滤
            Map<String, Object> tags = new HashMap<>();
            tags.put("type", new String[]{"Movie", "TV", "Directory", "Video"});
            requestData.put("tags", tags);
            
            requestData.put("exclude_grouped_video", 1);
            requestData.put("sort_type", "DESC");
            requestData.put("sort_column", "create_time");
            requestData.put("page_size", limit);  // Web 端使用 page_size
            
            // 🔑 关键：Web端在POST请求时会添加nonce字段用于防重放
            String nonce = String.format("%06d", (int)(Math.random() * 900000) + 100000);
            requestData.put("nonce", nonce);
            
            String method = "POST";
            String url = "/v/api/v1/item/list";
            Gson gson = new Gson();
            String data = gson.toJson(requestData);
            Map<String, String> params = new HashMap<>();
            
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            ApiService apiService = ApiClient.getApiService();
            Call<MediaItemListResponse> call = apiService.getItemList(authToken, authx, requestData);
            
            call.enqueue(new Callback<MediaItemListResponse>() {
                @Override
                public void onResponse(@NonNull Call<MediaItemListResponse> call, @NonNull Response<MediaItemListResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        MediaItemListResponse itemResponse = response.body();
                        if (itemResponse.getCode() == 0) {
                            List<MediaItem> mediaItems = convertToMediaItems(itemResponse.getData());
                            mediaDbInfos.put(guid, mediaItems);
                            callback.onSuccess(mediaItems);
                        } else {
                            callback.onError(itemResponse.getMessage());
                        }
                    } else {
                        callback.onError("Failed: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<MediaItemListResponse> call, @NonNull Throwable t) {
                    callback.onError(t.getMessage());
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    /**
     * 📖 Get Media DB Sum (Counts)
     */
    public void getMediaDbSum(MediaCallback<Map<String, Integer>> callback) {
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) return;

        try {
            String method = "GET";
            String url = "/v/api/v1/mediadb/sum";
            String authx = SignatureUtils.generateSignature(method, url, "", new HashMap<>());
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;

            ApiClient.getApiService().getMediaDbSum(authToken, authx).enqueue(new Callback<BaseResponse<Map<String, Integer>>>() {
                @Override
                public void onResponse(Call<BaseResponse<Map<String, Integer>>> call, Response<BaseResponse<Map<String, Integer>>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getCode() == 0) {
                        callback.onSuccess(response.body().getData());
                    } else {
                        callback.onError("Summary failed");
                    }
                }
                @Override
                public void onFailure(Call<BaseResponse<Map<String, Integer>>> call, Throwable t) {
                    callback.onError(t.getMessage());
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    /**
     * 🕒 Get Watch History (Play List)
     * 使用 Web 端的 /v/api/v1/play/list 接口
     */
    public void getPlayList(MediaCallback<List<MediaItem>> callback) {
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("User not logged in");
            return;
        }

        try {
            String method = "GET";
            String url = "/v/api/v1/play/list";
            
            String authx = SignatureUtils.generateSignature(method, url, "", new HashMap<>());
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;

            ApiClient.getApiService().getPlayList(authToken, authx).enqueue(new Callback<BaseResponse<List<PlayListResponse.PlayListItem>>>() {
                @Override
                public void onResponse(Call<BaseResponse<List<PlayListResponse.PlayListItem>>> call, Response<BaseResponse<List<PlayListResponse.PlayListItem>>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getCode() == 0) {
                        List<PlayListResponse.PlayListItem> playList = response.body().getData();
                        List<MediaItem> items = new ArrayList<>();
                        if (playList != null) {
                            for (PlayListResponse.PlayListItem pItem : playList) {
                                MediaItem mi = new MediaItem();
                                mi.setId(pItem.getGuid());
                                // 使用 tv_title 或 title
                                String displayTitle = pItem.getTvTitle();
                                if (displayTitle == null || displayTitle.isEmpty()) {
                                    displayTitle = pItem.getTitle();
                                }
                                mi.setTitle(displayTitle);
                                
                                // 构建海报 URL
                                String poster = pItem.getPoster();
                                if (poster != null && !poster.startsWith("http")) {
                                    poster = SharedPreferencesManager.getServerBaseUrl() + "/v/api/v1/sys/img" + poster + "?w=400";
                                }
                                mi.setPosterUrl(poster);
                                mi.setType(pItem.getType());
                                
                                // 设置观看进度
                                mi.setWatchedTs(pItem.getTs());
                                mi.setDuration(pItem.getDuration());
                                
                                // 🔗 设置关联信息 (用于继续观看导航)
                                mi.setParentGuid(pItem.getParentGuid());
                                mi.setAncestorGuid(pItem.getAncestorGuid());
                                mi.setMediaGuid(pItem.getMediaGuid());
                                
                                // 🎬 设置弹幕相关信息
                                mi.setDoubanId(pItem.getDoubanId());
                                mi.setSeasonNumber(pItem.getSeasonNumber());
                                mi.setEpisodeNumber(pItem.getEpisodeNumber());
                                mi.setTvTitle(pItem.getTvTitle()); // 电视剧标题用于弹幕搜索
                                
                                Log.d(TAG, "🎬 PlayList item: " + displayTitle + ", tvTitle=" + pItem.getTvTitle() + ", s" + pItem.getSeasonNumber() + "e" + pItem.getEpisodeNumber());
                                
                                items.add(mi);
                            }
                        }
                        callback.onSuccess(items);
                    } else {
                        // 如果 play/list 失败，尝试使用 watchhistory
                        Log.w(TAG, "play/list failed, trying watchhistory...");
                        getWatchHistoryFallback(callback);
                    }
                }
                @Override
                public void onFailure(Call<BaseResponse<List<PlayListResponse.PlayListItem>>> call, Throwable t) {
                    Log.e(TAG, "play/list error: " + t.getMessage());
                    getWatchHistoryFallback(callback);
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }
    
    /**
     * 🕒 Fallback: Get Watch History
     */
    private void getWatchHistoryFallback(MediaCallback<List<MediaItem>> callback) {
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) return;

        try {
            String method = "GET";
            String url = "/v/api/v1/user/watchhistory";
            Map<String, String> queryParams = new HashMap<>();
            queryParams.put("page", "1");
            queryParams.put("limit", "20");
            
            String authx = SignatureUtils.generateSignature(method, url, "", queryParams);
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;

            ApiClient.getApiService().getWatchHistory(authToken, authx, 1, 20).enqueue(new Callback<BaseResponse<WatchHistoryResponse>>() {
                @Override
                public void onResponse(Call<BaseResponse<WatchHistoryResponse>> call, Response<BaseResponse<WatchHistoryResponse>> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().getCode() == 0) {
                        WatchHistoryResponse history = response.body().getData();
                        List<MediaItem> items = new ArrayList<>();
                        if (history != null && history.getHistory() != null) {
                            for (WatchHistoryResponse.WatchHistoryItem hItem : history.getHistory()) {
                                MediaItem mi = new MediaItem();
                                mi.setId(hItem.getItemGuid());
                                mi.setTitle(hItem.getItemTitle());
                                String poster = hItem.getPosterUrl();
                                if (poster != null && !poster.startsWith("http")) {
                                    poster = SharedPreferencesManager.getServerBaseUrl() + "/v/api/v1/sys/img" + poster + "?w=400";
                                }
                                mi.setPosterUrl(poster);
                                mi.setType(hItem.getItemType());
                                items.add(mi);
                            }
                        }
                        callback.onSuccess(items);
                    } else {
                        callback.onError("History failed");
                    }
                }
                @Override
                public void onFailure(Call<BaseResponse<WatchHistoryResponse>> call, Throwable t) {
                    callback.onError(t.getMessage());
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    /**
     * 🎬 Get Item Detail
     * 使用 Web 端的 GET /v/api/v1/item/{guid} 接口
     */
    public void getItemDetail(String guid, MediaCallback<MediaDetailResponse> callback) {
        Log.d(TAG, "🎬 [MediaManager] Getting item detail: " + guid);
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("User not logged in");
            return;
        }
        
        try {
            String method = "GET";
            String url = "/v/api/v1/item/" + guid;
            String data = "";
            Map<String, String> params = new HashMap<>();
            
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            ApiService apiService = ApiClient.getApiService();
            Call<BaseResponse<MediaDetailResponse>> call = apiService.getItemDetail(authToken, authx, guid);
            
            call.enqueue(new Callback<BaseResponse<MediaDetailResponse>>() {
                @Override
                public void onResponse(@NonNull Call<BaseResponse<MediaDetailResponse>> call, @NonNull Response<BaseResponse<MediaDetailResponse>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        BaseResponse<MediaDetailResponse> res = response.body();
                        if (res.getCode() == 0 && res.getData() != null) {
                            Log.d(TAG, "✅ Item detail success: " + res.getData().getTitle());
                            callback.onSuccess(res.getData());
                        } else {
                            // Fallback to play/info
                            Log.w(TAG, "Item detail failed, trying play/info...");
                            getItemDetailViaPlayInfo(guid, callback);
                        }
                    } else {
                        getItemDetailViaPlayInfo(guid, callback);
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<BaseResponse<MediaDetailResponse>> call, @NonNull Throwable t) {
                    Log.e(TAG, "Item detail error: " + t.getMessage());
                    getItemDetailViaPlayInfo(guid, callback);
                }
            });
            
        } catch (Exception e) {
            callback.onError("Exception: " + e.getMessage());
        }
    }
    
    /**
     * 🎬 Get Item Detail via Play Info (Fallback)
     */
    private void getItemDetailViaPlayInfo(String guid, MediaCallback<MediaDetailResponse> callback) {
        getPlayInfo(guid, new MediaCallback<PlayInfoResponse>() {
            @Override
            public void onSuccess(PlayInfoResponse data) {
                if (data.getData() != null && data.getData().getItem() != null) {
                    // Convert Map to MediaDetailResponse
                    Gson gson = new Gson();
                    String json = gson.toJson(data.getData().getItem());
                    MediaDetailResponse detail = gson.fromJson(json, MediaDetailResponse.class);
                    callback.onSuccess(detail);
                } else {
                    callback.onError("Detail not found");
                }
            }
            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    /**
     * 📺 Get Season List
     * 使用 Web 端的 GET /v/api/v1/season/list/{tv_guid} 接口
     */
    public void getSeasonList(String tvGuid, MediaCallback<List<SeasonListResponse.Season>> callback) {
        Log.d(TAG, "📺 [MediaManager] Getting season list: " + tvGuid);
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("User not logged in");
            return;
        }
        
        try {
            String method = "GET";
            String url = "/v/api/v1/season/list/" + tvGuid;
            String data = "";
            Map<String, String> params = new HashMap<>();
            
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            ApiService apiService = ApiClient.getApiService();
            Call<SeasonListResponse> call = apiService.getSeasonList(authToken, authx, tvGuid);
            
            call.enqueue(new Callback<SeasonListResponse>() {
                @Override
                public void onResponse(@NonNull Call<SeasonListResponse> call, @NonNull Response<SeasonListResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        SeasonListResponse res = response.body();
                        if (res.getCode() == 0 && res.getData() != null) {
                            Log.d(TAG, "✅ Season list success: " + res.getData().size() + " seasons");
                            callback.onSuccess(res.getData());
                        } else {
                            callback.onError(res.getMessage() != null ? res.getMessage() : "Failed to get seasons");
                        }
                    } else {
                        callback.onError("Request failed: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<SeasonListResponse> call, @NonNull Throwable t) {
                    callback.onError("Network error: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            callback.onError("Exception: " + e.getMessage());
        }
    }

    /**
     * 🎬 Get Play Info
     * Matches web: getPlayInfo
     */
    public void getPlayInfo(String itemGuid, MediaCallback<PlayInfoResponse> callback) {
        Log.d(TAG, "🎬 [MediaManager] Getting play info: " + itemGuid);
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("User not logged in");
            return;
        }
        
        try {
            // 构建请求数据 - 与 Web 端一致
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("item_guid", itemGuid);
            
            // 🔑 关键：Web端在POST请求时会添加nonce字段用于防重放
            String nonce = String.format("%06d", (int)(Math.random() * 900000) + 100000);
            requestData.put("nonce", nonce);
            
            String method = "POST";
            String url = "/v/api/v1/play/info";
            Gson gson = new Gson();
            String data = gson.toJson(requestData);
            Map<String, String> params = new HashMap<>();
            
            String authx = SignatureUtils.generateSignature(method, url, data, params);
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            ApiService apiService = ApiClient.getApiService();
            // 使用 Map 发送请求，确保签名和请求体一致
            Call<PlayInfoResponse> call = apiService.getPlayInfoMap(authToken, authx, requestData);
            
            call.enqueue(new Callback<PlayInfoResponse>() {
                @Override
                public void onResponse(@NonNull Call<PlayInfoResponse> call, @NonNull Response<PlayInfoResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        PlayInfoResponse playInfo = response.body();
                        Log.d(TAG, "🎬 Play info response code: " + playInfo.getCode() + ", msg: " + playInfo.getMessage());
                        if (playInfo.getCode() == 0 && playInfo.getData() != null) {
                            Log.d(TAG, "✅ Play info success, media_guid: " + playInfo.getData().getMediaGuid());
                            callback.onSuccess(playInfo);
                        } else {
                            String errorMsg = playInfo.getMessage() != null ? playInfo.getMessage() : "Unknown error";
                            Log.e(TAG, "❌ Play info API error: " + errorMsg);
                            callback.onError("API Error: " + errorMsg);
                        }
                    } else {
                        String errorBody = "";
                        try {
                            if (response.errorBody() != null) {
                                errorBody = response.errorBody().string();
                            }
                        } catch (Exception e) {
                            errorBody = "Unable to read error body";
                        }
                        Log.e(TAG, "❌ Play info request failed: " + response.code() + " - " + errorBody);
                        callback.onError("Request failed: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<PlayInfoResponse> call, @NonNull Throwable t) {
                    Log.e(TAG, "❌ Play info network error: " + t.getMessage());
                    callback.onError("Network error: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Play info exception: " + e.getMessage());
            callback.onError("Exception: " + e.getMessage());
        }
    }
    
    /**
     * 🎬 Get Play URL
     * Uses media_guid to construct the direct range URL, matching Web's format.
     * Web端使用: /v/api/v1/media/range/{mediaGuid}?direct_link_quality_index=0
     */
    public String getPlayUrl(String mediaGuid) {
        return "/v/api/v1/media/range/" + mediaGuid + "?direct_link_quality_index=0";
    }

    /**
     * 🎬 Start Playback logic (replaces startPlayEpisode)
     * 直接使用 Web 端的 URL 格式播放，不依赖 stream API
     */
    public void startPlay(String itemGuid, MediaCallback<String> callback) {
        Log.d(TAG, "🎬 startPlay: " + itemGuid);
        
        getPlayInfo(itemGuid, new MediaCallback<PlayInfoResponse>() {
            @Override
            public void onSuccess(PlayInfoResponse playInfoResponse) {
                if (playInfoResponse != null && playInfoResponse.getData() != null) {
                    PlayInfoResponse.PlayInfoData data = playInfoResponse.getData();
                    String mediaGuid = data.getMediaGuid();
                    
                    Log.d(TAG, "🎬 PlayInfo成功: mediaGuid=" + mediaGuid + ", type=" + data.getType());
                    
                    if (mediaGuid != null && !mediaGuid.isEmpty()) {
                        // 🔥 直接使用 Web 端的 URL 格式
                        // Web端使用: http://server/v/api/v1/media/range/{mediaGuid}?direct_link_quality_index=0
                        String baseUrl = SharedPreferencesManager.getServerBaseUrl();
                        String playUrl = baseUrl + "/v/api/v1/media/range/" + mediaGuid + "?direct_link_quality_index=0";
                        Log.d(TAG, "🎬 使用媒体URL: " + playUrl);
                        callback.onSuccess(playUrl);
                    } else {
                        callback.onError("No media_guid found in play info");
                    }
                } else {
                    callback.onError("Invalid play info response");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
    
    /**
     * 🎬 获取直连URL（优先原画）
     * 调用 POST /v/api/v1/stream 获取 direct_link_qualities
     * 
     * Web端请求格式:
     * {
     *   "media_guid": "xxx",
     *   "ip": "xxx",  // 可以是GUID或空字符串
     *   "header": {"User-Agent": ["xxx"]},
     *   "level": 1
     * }
     */
    public void getDirectLinkUrl(String mediaGuid, MediaCallback<String> callback) {
        Log.d(TAG, "🎬 获取直连URL: " + mediaGuid);
        
        String token = SharedPreferencesManager.getAuthToken();
        if (token == null || token.isEmpty()) {
            callback.onError("User not logged in");
            return;
        }
        
        try {
            // 构建请求数据 - 与 Web 端完全一致的格式和字段顺序
            java.util.LinkedHashMap<String, Object> requestData = new java.util.LinkedHashMap<>();
            
            // 字段顺序与 Web 端一致
            requestData.put("media_guid", mediaGuid);
            requestData.put("ip", "");  // Web端使用GUID，但空字符串也可以
            
            // header 字段
            java.util.LinkedHashMap<String, Object> header = new java.util.LinkedHashMap<>();
            header.put("User-Agent", java.util.Arrays.asList("NasTV-Android/1.0"));
            requestData.put("header", header);
            
            requestData.put("level", 1);
            
            // 🔑 注意：nonce 只在 authx 签名中使用，不放在请求体中
            // 但签名计算时需要包含 nonce
            String nonce = String.format("%06d", (int)(Math.random() * 900000) + 100000);
            
            String method = "POST";
            String url = "/v/api/v1/stream";
            Gson gson = new Gson();
            
            // 计算签名时，需要在 data 中包含 nonce（与 Web 端一致）
            java.util.LinkedHashMap<String, Object> signData = new java.util.LinkedHashMap<>(requestData);
            signData.put("nonce", nonce);
            String dataForSign = gson.toJson(signData);
            
            // 实际发送的请求体不包含 nonce
            String data = gson.toJson(requestData);
            
            Log.d(TAG, "🎬 Stream请求数据: " + data);
            Log.d(TAG, "🎬 Stream签名数据: " + dataForSign);
            
            String authx = SignatureUtils.generateSignature(method, url, dataForSign, new HashMap<>());
            String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            
            Log.d(TAG, "🎬 Stream请求 authx: " + authx);
            
            ApiService apiService = ApiClient.getApiService();
            Call<ResponseBody> call = apiService.getStream(authToken, authx, requestData);
            
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String responseBody = response.body().string();
                            Log.d(TAG, "🎬 Stream响应长度: " + responseBody.length());
                            Log.d(TAG, "🎬 Stream响应: " + responseBody.substring(0, Math.min(1000, responseBody.length())));
                            
                            // 解析响应
                            com.mynas.nastv.model.StreamResponse streamResponse = 
                                gson.fromJson(responseBody, com.mynas.nastv.model.StreamResponse.class);
                            
                            if (streamResponse != null && streamResponse.getCode() == 0 && streamResponse.getData() != null) {
                                String directUrl = streamResponse.getData().getOriginalQualityUrl();
                                if (directUrl != null && !directUrl.isEmpty()) {
                                    Log.d(TAG, "✅ 获取到直连URL（原画）: " + directUrl.substring(0, Math.min(100, directUrl.length())));
                                    callback.onSuccess(directUrl);
                                } else {
                                    Log.w(TAG, "⚠️ 没有直连URL，尝试使用qualities");
                                    callback.onError("No direct link available");
                                }
                            } else {
                                String msg = streamResponse != null ? streamResponse.getMessage() : "Unknown error";
                                int code = streamResponse != null ? streamResponse.getCode() : -999;
                                Log.e(TAG, "❌ Stream API error: code=" + code + ", msg=" + msg);
                                callback.onError("Stream API error: " + msg);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ 解析Stream响应失败: " + e.getMessage(), e);
                            callback.onError("Parse error: " + e.getMessage());
                        }
                    } else {
                        String errorBody = "";
                        try {
                            if (response.errorBody() != null) {
                                errorBody = response.errorBody().string();
                            }
                        } catch (Exception e) {
                            errorBody = "Unable to read error body";
                        }
                        Log.e(TAG, "❌ Stream请求失败: " + response.code() + " - " + errorBody);
                        callback.onError("Stream request failed: " + response.message());
                    }
                }
                
                @Override
                public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                    Log.e(TAG, "❌ Stream请求失败: " + t.getMessage());
                    callback.onError("Network error: " + t.getMessage());
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "❌ getDirectLinkUrl异常: " + e.getMessage());
            callback.onError("Exception: " + e.getMessage());
        }
    }

    /**
     * 🎬 Get Episode List
     */
    public void getEpisodeList(String seasonGuid, MediaCallback<List<EpisodeListResponse.Episode>> callback) {
         Log.d(TAG, "🎬 Getting episode list: " + seasonGuid);
         
         String token = SharedPreferencesManager.getAuthToken();
         // ... auth checks ...
         
         try {
             String method = "GET";
             String url = "/v/api/v1/episode/list/" + seasonGuid;
             String data = "";
             Map<String, String> params = new HashMap<>();
             
             String authx = SignatureUtils.generateSignature(method, url, data, params);
             String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
             
             ApiService apiService = ApiClient.getApiService();
             Call<BaseResponse<List<EpisodeListResponse.Episode>>> call = apiService.getEpisodeList(authToken, authx, seasonGuid);
             
             call.enqueue(new Callback<BaseResponse<List<EpisodeListResponse.Episode>>>() {
                 @Override
                 public void onResponse(Call<BaseResponse<List<EpisodeListResponse.Episode>>> call, Response<BaseResponse<List<EpisodeListResponse.Episode>>> response) {
                     if (response.isSuccessful() && response.body() != null) {
                         BaseResponse<List<EpisodeListResponse.Episode>> res = response.body();
                         if (res.getCode() == 0) {
                             callback.onSuccess(res.getData());
                         } else {
                             callback.onError(res.getMessage());
                         }
                     } else {
                         callback.onError("Request failed");
                     }
                 }

                 @Override
                 public void onFailure(Call<BaseResponse<List<EpisodeListResponse.Episode>>> call, Throwable t) {
                     callback.onError(t.getMessage());
                 }
             });
         } catch (Exception e) {
             callback.onError(e.getMessage());
         }
    }

    // Helper conversion methods...
    private List<MediaDbItem> convertToMediaDbItems(List<MediaDbListResponse.MediaDb> data) {
        List<MediaDbItem> result = new ArrayList<>();
        if (data != null) {
            for (MediaDbListResponse.MediaDb db : data) {
                result.add(new MediaDbItem(db.getGuid(), db.getName(), db.getType()));
            }
        }
        return result;
    }
    
    private List<MediaItem> convertToMediaItems(MediaItemListResponse.MediaItemData data) {
        List<MediaItem> result = new ArrayList<>();
        if (data != null && data.getList() != null) {
             for (MediaItemListResponse.MediaItemInfo item : data.getList()) {
                 MediaItem mediaItem = new MediaItem();
                 mediaItem.setId(item.getGuid());
                 mediaItem.setTitle(item.getTvTitle() != null && !item.getTvTitle().isEmpty() ? item.getTvTitle() : item.getTitle());
                 
                 // 构建海报 URL - 与 Web 端一致
                 String poster = item.getPoster();
                 if (poster != null && !poster.startsWith("http")) {
                     poster = SharedPreferencesManager.getServerBaseUrl() + "/v/api/v1/sys/img" + poster + "?w=400";
                 }
                 mediaItem.setPosterUrl(poster);
                 mediaItem.setType(item.getType());
                 
                 // 设置时长
                 if (item.getDuration() > 0) {
                     mediaItem.setDuration(item.getDuration());
                 } else if (item.getRuntime() > 0) {
                     mediaItem.setDuration(item.getRuntime() * 60L); // runtime 是分钟，转换为秒
                 }
                 
                 result.add(mediaItem);
             }
        }
        return result;
    }

    // Inner classes
    public static class MediaDbItem {
        private String guid;
        private String name;
        private String category;
        private int itemCount;
        public MediaDbItem(String guid, String name, String category) {
            this.guid = guid;
            this.name = name;
            this.category = category;
        }
        public String getGuid() { return guid; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public int getItemCount() { return itemCount; }
        public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    }
    
    public interface MediaCallback<T> {
        void onSuccess(T data);
        void onError(String error);
    }
}
