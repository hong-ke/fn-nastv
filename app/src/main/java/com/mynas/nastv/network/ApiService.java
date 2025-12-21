package com.mynas.nastv.network;

import com.mynas.nastv.model.BaseResponse;
import com.mynas.nastv.model.LoginRequest;
import com.mynas.nastv.model.LoginResponse;
import com.mynas.nastv.model.MediaDbListResponse;
import com.mynas.nastv.model.MediaDbInfoResponse;
import com.mynas.nastv.model.MediaItemListResponse;
import com.mynas.nastv.model.MediaLibraryItemsRequest;
import com.mynas.nastv.model.MediaDetailResponse;
import com.mynas.nastv.model.SeasonListResponse;
import com.mynas.nastv.model.PlayApiRequest;
import com.mynas.nastv.model.PlayApiResponse;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.model.PlayInfoRequest;
import com.mynas.nastv.model.PlayInfoResponse;
import com.mynas.nastv.model.PlayUrlRequest;
import com.mynas.nastv.model.PlayUrlResponse;
import com.mynas.nastv.model.PlayListResponse;
import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.model.ConfigResponse;
import com.mynas.nastv.model.QrCodeResponse;
import com.mynas.nastv.model.UserInfoResponse;
import com.mynas.nastv.model.SearchRequest;
import com.mynas.nastv.model.SearchResponse;
import com.mynas.nastv.model.WatchHistoryResponse;
import com.mynas.nastv.model.FavoriteRequest;
import java.util.List;
import com.mynas.nastv.model.PlayRecordRequest;
import com.mynas.nastv.model.DanmakuResponse;
import com.mynas.nastv.model.DanmakuRequest;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 🌐 API服务接口
 * 定义所有与后端通信的接口，复用Web项目的API
 */
public interface ApiService {
    
    // 🔐 登录相关接口
    @GET("/api/getFnUrl")
    Call<ResponseBody> getFnUrl();
    
    @PUT("/v/api/v1/logincode/generate")
    Call<QrCodeResponse> getQrCode();
    
    @GET("/v/api/v1/logincode/{code}")
    Call<LoginResponse> checkQrLogin(@Path("code") String code);
    
    @POST("/v/api/v1/login")
    Call<LoginResponse> login(@Header("authx") String signature, @Body LoginRequest request);
    
    // ⚙️ 系统配置接口 - 添加/v前缀
    @GET("/v/api/v1/sys/config")
    Call<ConfigResponse> getConfig(@Header("Authorization") String token, @Header("authx") String signature);
    
    // 📚 媒体库接口 - 使用相对路径，通过fnos-tv代理转发到飞牛服务器
    @GET("api/v1/mediadb/list")
    Call<MediaDbListResponse> getMediaDbList(@Header("Authorization") String token, @Header("authx") String signature);
    
    @GET("api/v1/mediadb/sum")
    Call<BaseResponse<Object>> getMediaDbSum(@Header("Authorization") String token, @Header("authx") String signature);
    
    @POST("api/v1/item/list")
    Call<MediaItemListResponse> getMediaDbInfos(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body Object requestData
    );
    
    // 🎬 播放信息接口 - 根据Web项目curl命令
    @POST("api/v1/play/info")  // 🚨 [修复] 移除重复的v/前缀，因为BASE_URL已经包含了/fnos/v/
    Call<PlayInfoResponse> getPlayInfo(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body PlayInfoRequest request
    );
    
    @GET("v/api/v1/stream/list/{episodeGuid}")
    Call<StreamListResponse> getStreamList(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("episodeGuid") String episodeGuid,
        @Query("before_play") int beforePlay
    );
    
    @POST("v/api/v1/play/play")
    Call<PlayUrlResponse> getPlayUrl(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body PlayUrlRequest request
    );
    
    // 👤 用户信息接口 - 飞牛服务器需要/v前缀
    @GET("v/api/v1/user/info")
    Call<BaseResponse<UserInfoResponse>> getUserInfo(
        @Header("Authorization") String token,
        @Header("authx") String signature
    );
    

    // 🎬 媒体详情接口 - 根据Web项目curl命令修正路径
    @GET("api/v1/item/{guid}")  // 🚨 [修复] 移除重复的v/前缀，因为BASE_URL已经包含了/fnos/v/
    Call<BaseResponse<MediaDetailResponse>> getItemDetail(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("guid") String guid
    );
    
    
    // 📺 季度和剧集接口 - 飞牛服务器需要/v前缀
    @GET("v/api/v1/season/list/{parentGuid}")
    Call<BaseResponse<SeasonListResponse>> getSeasonList(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("parentGuid") String parentGuid
    );
    
    @GET("v/api/v1/episode/list/{parentGuid}")
    Call<BaseResponse<EpisodeListResponse>> getEpisodeList(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("parentGuid") String parentGuid
    );
    
    // 👥 演员信息接口
    @GET("/api/v1/person/list/{itemGuid}")
    Call<BaseResponse<Object>> getPersonList(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("itemGuid") String itemGuid
    );
    
    // 🔍 搜索接口
    @POST("/api/v1/search")
    Call<BaseResponse<SearchResponse>> search(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body SearchRequest request
    );
    
    @GET("/api/v1/search/suggestions")
    Call<BaseResponse<Object>> getSearchSuggestions(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Query("keyword") String keyword
    );
    
    // 📖 观看记录接口
    @GET("/api/v1/user/watchhistory")
    Call<BaseResponse<WatchHistoryResponse>> getWatchHistory(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Query("page") int page,
        @Query("limit") int limit
    );
    
    @POST("/api/v1/play/record")
    Call<BaseResponse<Object>> sendPlayRecord(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body PlayRecordRequest request
    );
    
    // ⭐ 收藏接口
    @GET("/api/v1/user/favorites")
    Call<BaseResponse<Object>> getFavorites(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Query("page") int page,
        @Query("limit") int limit
    );
    
    @POST("/api/v1/user/favorite")
    Call<BaseResponse<Object>> addToFavorites(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body FavoriteRequest request
    );
    
    @DELETE("/api/v1/user/favorite/{itemGuid}")
    Call<BaseResponse<Object>> removeFromFavorites(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("itemGuid") String itemGuid
    );
    
    // 📊 继续观看列表接口 - BASE_URL已包含/fnos/v/，所以不需要额外的/v前缀
    @GET("api/v1/play/list")
    Call<PlayListResponse> getPlayList(
        @Header("Authorization") String token,
        @Header("authx") String signature
    );
    
    // 📚 获取媒体库内容接口 - POST方式，传递筛选参数
    @POST("api/v1/item/list")
    Call<MediaItemListResponse> getMediaLibraryItems(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body MediaLibraryItemsRequest request
    );
    
    // 🌐 弹幕接口
    @GET("/api/v1/danmaku/{episodeGuid}")
    Call<BaseResponse<DanmakuResponse>> getDanmaku(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("episodeGuid") String episodeGuid
    );
    
    @POST("/api/v1/danmaku")
    Call<BaseResponse<Object>> sendDanmaku(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body DanmakuRequest request
    );
    
    // 📱 图片接口
    @GET("/api/v1/sys/img/{imageId}")
    Call<Object> getImage(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("imageId") String imageId
    );
    
    // ⚙️ 设置接口
    @GET("/api/v1/user/settings")
    Call<BaseResponse<Object>> getUserSettings(
        @Header("Authorization") String token,
        @Header("authx") String signature
    );
    
    @PUT("/api/v1/user/settings")
    Call<BaseResponse<Object>> updateUserSettings(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body Object settings
    );
    
    // 📊 统计接口
    @GET("/api/v1/stats/dashboard")
    Call<BaseResponse<Object>> getDashboardStats(
        @Header("Authorization") String token,
        @Header("authx") String signature
    );
    
    // 🔄 同步接口
    @POST("/api/v1/sync/progress")
    Call<BaseResponse<Object>> syncProgress(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body Object progressData
    );
    
    @GET("/api/v1/sync/progress")
    Call<BaseResponse<Object>> getProgressSync(
        @Header("Authorization") String token,
        @Header("authx") String signature
    );
    
    // 🎬 获取剧集列表（用于获取真实剧集GUID）
    // 🎬 获取剧集列表（用于播放）
    // 🚨 [修复] API返回格式是直接的剧集数组: {"data": [...]}
    @GET("api/v1/episode/list/{seasonGuid}")
    Call<BaseResponse<List<EpisodeListResponse.Episode>>> getEpisodeListForPlay(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("seasonGuid") String seasonGuid
    );
    
    // 🎬 获取季列表（用于获取季GUID）
    @GET("api/v1/season/list/{mediaGuid}")
    Call<BaseResponse<SeasonListResponse>> getSeasonListForGuid(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("mediaGuid") String mediaGuid
    );
    
    /**
     * 🎬 获取流媒体列表（获取真正的媒体流GUID）
     * GET /fnos/v/api/v1/stream/list/{episodeGuid}?before_play=1
     */
    @GET("api/v1/stream/list/{episodeGuid}?before_play=1")
    Call<BaseResponse<StreamListResponse.StreamData>> getStreamList(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("episodeGuid") String episodeGuid
    );

    /**
     * 🎬 调用播放API激活播放会话
     * POST /fnos/v/api/v1/play/play
     */
    @POST("api/v1/play/play")
    Call<BaseResponse<PlayApiResponse.PlaySessionData>> startPlaySession(@Body PlayApiRequest request);
    
    /**
     * 🎬 获取弹幕数据
     * 路径: /danmu/get（从服务器根路径开始，避免与fnos/v/路径冲突）
     */
    @GET("danmu/get")  // 修复路径：直接使用 danmu/get，通过 BaseURL 配置来避免路径冲突
    Call<ResponseBody> getDanmu(
        @Query("douban_id") String doubanId,
        @Query("episode_number") int episodeNumber,
        @Query("episode_title") String episodeTitle,
        @Query("title") String title,
        @Query("season_number") int seasonNumber,
        @Query("season") boolean season,
        @Query("guid") String guid,
        @Query("parent_guid") String parentGuid
    );
}

