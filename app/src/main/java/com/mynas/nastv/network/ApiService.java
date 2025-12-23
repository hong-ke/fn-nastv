package com.mynas.nastv.network;

import com.mynas.nastv.model.BaseResponse;
import com.mynas.nastv.model.LoginRequest;
import com.mynas.nastv.model.LoginResponse;
import com.mynas.nastv.model.MediaDbListResponse;
import com.mynas.nastv.model.MediaItemListResponse;
import com.mynas.nastv.model.MediaDetailResponse;
import com.mynas.nastv.model.SeasonListResponse;
import com.mynas.nastv.model.EpisodeListResponse;
import com.mynas.nastv.model.PlayInfoRequest;
import com.mynas.nastv.model.PlayInfoResponse;
import com.mynas.nastv.model.PlayUrlResponse;
import com.mynas.nastv.model.StreamListResponse;
import com.mynas.nastv.model.ConfigResponse;
import com.mynas.nastv.model.UserInfoResponse;
import com.mynas.nastv.model.SearchRequest;
import com.mynas.nastv.model.SearchResponse;
import com.mynas.nastv.model.WatchHistoryResponse;
import com.mynas.nastv.model.FavoriteRequest;
import com.mynas.nastv.model.PlayRecordRequest;
import com.mynas.nastv.model.PlayListResponse;

import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * 🌐 API Service Interface
 * Redesigned to match fntv-electron and new web interface (v/api/v1).
 */
public interface ApiService {
    
    // 🔐 Login
    @GET("/api/getFnUrl")
    Call<ResponseBody> getFnUrl();
    
    @POST("/v/api/v1/login")
    Call<LoginResponse> login(@Header("authx") String signature, @Body LoginRequest request);

    @GET("/api/v1/logincode/tv")
    Call<com.mynas.nastv.model.QrCodeResponse> getQrCode();

    @GET("/api/v1/logincode/check")
    Call<com.mynas.nastv.model.LoginResponse> checkQrLogin(@Query("code") String code);
    
    // ⚙️ Config
    @GET("/v/api/v1/sys/config")
    Call<ConfigResponse> getConfig(@Header("Authorization") String token, @Header("authx") String signature);
    
    // 📚 Media Library List (Root)
    @GET("/v/api/v1/mediadb/list")
    Call<MediaDbListResponse> getMediaDbList(@Header("Authorization") String token, @Header("authx") String signature);

    @GET("/v/api/v1/mediadb/sum")
    Call<BaseResponse<Map<String, Integer>>> getMediaDbSum(@Header("Authorization") String token, @Header("authx") String signature);
    
    // 📚 Media Content List / Folder Content
    @POST("/v/api/v1/item/list")
    Call<MediaItemListResponse> getItemList(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body Object requestData
    );

    // 🎬 Get Item Detail (TV/Season/Episode/Movie)
    // Web端使用: GET /v/api/v1/item/{guid}
    @GET("/v/api/v1/item/{guid}")
    Call<BaseResponse<MediaDetailResponse>> getItemDetail(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("guid") String guid
    );

    // 📺 Season List (for TV shows)
    // Web端使用: GET /v/api/v1/season/list/{tv_guid}
    @GET("/v/api/v1/season/list/{tvGuid}")
    Call<SeasonListResponse> getSeasonList(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("tvGuid") String tvGuid
    );

    // 🎬 Play Info (Includes Item Details)
    @POST("/v/api/v1/play/info")
    Call<PlayInfoResponse> getPlayInfo(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body PlayInfoRequest request
    );
    
    // 🎬 Play Info (使用 Map 作为请求体，确保签名一致)
    @POST("/v/api/v1/play/info")
    Call<PlayInfoResponse> getPlayInfoMap(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body Map<String, Object> requestData
    );

    // 📺 Episode List
    @GET("/v/api/v1/episode/list/{parentGuid}")
    Call<BaseResponse<List<EpisodeListResponse.Episode>>> getEpisodeList(
        @Header("Authorization") String token, 
        @Header("authx") String signature, 
        @Path("parentGuid") String parentGuid
    );

    // 🎬 Stream Info (for playback quality selection)
    @POST("/v/api/v1/stream")
    Call<ResponseBody> getStream(
        @Header("Authorization") String token, 
        @Header("authx") String signature, 
        @Body Map<String, Object> requestData
    );

    // 🎬 Stream List (for item streams info)
    // Web端使用: GET /v/api/v1/stream/list/{item_guid}
    @GET("/v/api/v1/stream/list/{itemGuid}")
    Call<StreamListResponse> getStreamList(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("itemGuid") String itemGuid
    );
    
    // 🎬 Direct Media URL
    @GET("/v/api/v1/media/range/{mediaGuid}")
    Call<ResponseBody> getMediaRange(@Header("Authorization") String token, @Path("mediaGuid") String mediaGuid);

    // 👤 User Info
    @GET("/v/api/v1/user/info")
    Call<BaseResponse<UserInfoResponse>> getUserInfo(
        @Header("Authorization") String token,
        @Header("authx") String signature
    );

    // 👥 Person List
    @GET("/v/api/v1/person/list/{itemGuid}")
    Call<BaseResponse<Object>> getPersonList(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("itemGuid") String itemGuid
    );
    
    // 🔍 Search
    @POST("/v/api/v1/search")
    Call<BaseResponse<SearchResponse>> search(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body SearchRequest request
    );
    
    // 📖 Watch History
    @GET("/v/api/v1/user/watchhistory")
    Call<BaseResponse<WatchHistoryResponse>> getWatchHistory(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Query("page") int page,
        @Query("limit") int limit
    );
    
    // 📖 Play List (继续观看 - Web 端使用此接口)
    @GET("/v/api/v1/play/list")
    Call<BaseResponse<java.util.List<PlayListResponse.PlayListItem>>> getPlayList(
        @Header("Authorization") String token,
        @Header("authx") String signature
    );
    
    @POST("/v/api/v1/play/record")
    Call<BaseResponse<Object>> sendPlayRecord(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body PlayRecordRequest request
    );

    // ⭐ Favorites
    @GET("/v/api/v1/user/favorites")
    Call<BaseResponse<Object>> getFavorites(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Query("page") int page,
        @Query("limit") int limit
    );
    
    @POST("/v/api/v1/user/favorite")
    Call<BaseResponse<Object>> addToFavorites(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Body FavoriteRequest request
    );
    
    @DELETE("/v/api/v1/user/favorite/{itemGuid}")
    Call<BaseResponse<Object>> removeFromFavorites(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("itemGuid") String itemGuid
    );

    // 🎬 Danmaku (旧接口 - 保留兼容)
    @GET("/v/api/v1/danmaku")
    Call<com.mynas.nastv.model.DanmakuListResponse> getDanmaku(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Query("douban_id") String doubanId,
        @Query("episode") int episode,
        @Query("season") int season
    );
    
    // 🎬 Danmaku (新接口 - 弹幕服务器 http://192.168.3.20:13401)
    // 响应格式: { "1": [...], "2": [...] } - key 是集数
    // 参数: title, season_number, episode_number (guid/parent_guid 可选，用于缓存)
    // 注意：如果传入guid但数据库没有记录，会返回空数据，所以首次请求不传guid
    @GET("/danmu/get")
    Call<Map<String, List<com.mynas.nastv.model.DanmakuMapResponse.DanmakuItem>>> getDanmakuMap(
        @Query("title") String title,
        @Query("season_number") int seasonNumber,
        @Query("episode_number") int episodeNumber
    );
    
    // 📝 字幕下载
    @GET("/v/api/v1/subtitle/dl/{subtitleGuid}")
    Call<ResponseBody> downloadSubtitle(
        @Header("Authorization") String token,
        @Header("authx") String signature,
        @Path("subtitleGuid") String subtitleGuid
    );
}
