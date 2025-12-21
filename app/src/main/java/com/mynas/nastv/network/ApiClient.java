package com.mynas.nastv.network;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.SignatureUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 🔗 API客户端
 * 负责网络请求的统一管理，包括签名、认证等
 */
public class ApiClient {
    private static final String TAG = "ApiClient";
    private static final int CONNECT_TIMEOUT = 30;
    private static final int READ_TIMEOUT = 60;
    private static final int PLAY_API_TIMEOUT = 60;  // 播放API专用超时时间（秒）
    
    private static ApiService apiService;
    private static ApiService fnOSApiService;  // 专门用于FnOS服务器的API服务
    private static ApiService playApiService;  // 专门用于播放API的服务（长超时）
    private static ApiService danmuApiService; // 🎬 专门用于弹幕API的服务（服务器根路径）
    private static Context context;
    private static String fnOSBaseUrl;
    
    public static void initialize(Context ctx) {
        context = ctx.getApplicationContext();
        // 强制重新创建，清除可能的缓存
        apiService = null;
        playApiService = null;
        danmuApiService = null; // 清除弹幕API服务
        createApiService();
        createPlayApiService();
        createDanmuApiService(); // 创建弹幕API服务
        Log.d(TAG, "🔗 API客户端初始化完成");
    }
    
    private static void createApiService() {
        String baseUrl = getBaseUrl();
        Log.d(TAG, "🔍 [DEBUG] 创建ApiService，使用BASE_URL: " + baseUrl);
        
        // 🔍 日志拦截器
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC); // 🔧 改为BASIC级别，避免打印大量响应体
        
        // 🔧 OkHttp客户端配置
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .addInterceptor(new AuthInterceptor())
                .build();
        
        // 🎯 Gson配置
        Gson gson = new GsonBuilder()
                .setLenient()
                .create();
        
        // 🚀 Retrofit配置
        Log.d(TAG, "🔍 [DEBUG] 即将创建Retrofit实例，BASE_URL: " + baseUrl);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        
        Log.d(TAG, "🔍 [DEBUG] Retrofit实例创建完成，baseUrl: " + retrofit.baseUrl());
        
        apiService = retrofit.create(ApiService.class);
        Log.d(TAG, "✅ [DEBUG] ApiService创建完成");
    }
    
    /**
     * 🎬 创建播放API专用服务（长超时时间）
     */
    private static void createPlayApiService() {
        String baseUrl = getBaseUrl();
        Log.d(TAG, "🎬 [DEBUG] 创建PlayApiService，使用BASE_URL: " + baseUrl);
        
        // 🔍 日志拦截器
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC); // 🔧 改为BASIC级别，避免打印大量响应体
        
        // 🔧 OkHttp客户端配置（专门为播放API使用更长的超时时间）
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(PLAY_API_TIMEOUT, TimeUnit.SECONDS)  // 使用播放API专用超时
                .writeTimeout(PLAY_API_TIMEOUT, TimeUnit.SECONDS)  // 使用播放API专用超时
                .addInterceptor(loggingInterceptor)
                .addInterceptor(new AuthInterceptor())
                .build();
        
        // 🎯 Gson配置
        Gson gson = new GsonBuilder()
                .setLenient()
                .create();
        
        // 🚀 Retrofit配置
        Log.d(TAG, "🎬 [DEBUG] 即将创建播放API Retrofit实例，超时: " + PLAY_API_TIMEOUT + "秒");
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        
        playApiService = retrofit.create(ApiService.class);
        Log.d(TAG, "✅ [DEBUG] PlayApiService创建完成，超时设置: " + PLAY_API_TIMEOUT + "秒");
    }
    
    public static ApiService getApiService() {
        if (apiService == null) {
            throw new IllegalStateException("ApiClient未初始化，请先调用initialize()");
        }
        return apiService;
    }
    
    /**
     * 🎬 获取播放API专用服务实例（长超时）
     */
    public static ApiService getPlayApiService() {
        if (playApiService == null) {
            throw new IllegalStateException("播放API服务未初始化，请先调用initialize()");
        }
        return playApiService;
    }
    
    /**
     * 🎬 创建弹幕API专用服务（服务器根路径）
     */
    private static void createDanmuApiService() {
        String serverBaseUrl = SharedPreferencesManager.getServerBaseUrl(); // 获取服务器根路径
        Log.d(TAG, "🎬 [DEBUG] 创建DanmuApiService，使用服务器根路径: " + serverBaseUrl);
        
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC); // 🔧 改为BASIC级别，避免打印大量响应体
        
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .addInterceptor(new AuthInterceptor())
                .build();
        
        Gson gson = new GsonBuilder()
                .setLenient()
                .create();
        
        Log.d(TAG, "🎬 [DEBUG] 即将创建弹幕API Retrofit实例，使用根路径: " + serverBaseUrl);
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(serverBaseUrl + "/") // 使用服务器根路径，如 http://172.20.10.3:8123/
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        
        danmuApiService = retrofit.create(ApiService.class);
        Log.d(TAG, "✅ [DEBUG] DanmuApiService创建完成，根路径: " + serverBaseUrl);
    }
    
    /**
     * 🎬 获取弹幕API专用服务实例（服务器根路径）
     */
    public static ApiService getDanmuApiService() {
        if (danmuApiService == null) {
            throw new IllegalStateException("弹幕API服务未初始化，请先调用initialize()");
        }
        return danmuApiService;
    }
    
    /**
     * 🌐 获取当前配置的BASE_URL
     */
    private static String getBaseUrl() {
        return SharedPreferencesManager.getApiBaseUrl();
    }
    
    /**
     * 🌐 设置FnOS服务器地址并创建专用API服务
     */
    public static void setFnOSBaseUrl(String fnOSUrl) {
        fnOSBaseUrl = fnOSUrl;
        createFnOSApiService();
        Log.d(TAG, "🔗 FnOS API客户端创建完成，地址: " + fnOSUrl);
    }
    
    /**
     * 🎯 获取FnOS API服务实例
     */
    public static ApiService getFnOSApiService() {
        if (fnOSApiService == null) {
            throw new IllegalStateException("FnOS ApiService未初始化，请先调用setFnOSBaseUrl()");
        }
        return fnOSApiService;
    }
    
    /**
     * 🚀 创建FnOS专用API服务
     */
    private static void createFnOSApiService() {
        if (fnOSBaseUrl == null || fnOSBaseUrl.isEmpty()) {
            Log.e(TAG, "❌ FnOS BaseUrl为空，无法创建API服务");
            return;
        }
        
        // 🔍 日志拦截器
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC); // 🔧 改为BASIC级别，避免打印大量响应体
        
        // 🔧 OkHttp客户端配置
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .addInterceptor(new AuthInterceptor())
                .build();
        
        // 🎯 Gson配置
        Gson gson = new GsonBuilder()
                .setLenient()
                .create();
        
        // 🚀 Retrofit配置，使用FnOS地址
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(fnOSBaseUrl.endsWith("/") ? fnOSBaseUrl : fnOSBaseUrl + "/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        
        fnOSApiService = retrofit.create(ApiService.class);
    }
    
    /**
     * 🔐 认证拦截器
     * 自动添加必要的请求头
     */
    private static class AuthInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request originalRequest = chain.request();
            
            // 🔧 添加通用请求头
            Request.Builder requestBuilder = originalRequest.newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "NasTV-Android/1.0");
            
            // 🔐 添加认证Token (如果还没有Authorization头)
            if (originalRequest.header("Authorization") == null && needsAuth(originalRequest)) {
                String token = SharedPreferencesManager.getAuthToken();
                if (token != null && !token.isEmpty()) {
                    // fnos-tv后端期望原始token，不要Bearer前缀（与Web项目一致）
                    String authToken = token.startsWith("Bearer ") ? token.substring(7) : token;
                    requestBuilder.addHeader("Authorization", authToken);
                    
                    // 🔧 关键修复：同时添加Cookie认证（模拟浏览器行为）
                    requestBuilder.addHeader("Cookie", "authorization=" + authToken);
                    
                    Log.d(TAG, "🔐 已添加认证Token（Header + Cookie）");
                }
            }
            
            // 🔍 如果需要签名，自动生成authx头 (但不覆盖已有的)
            if (originalRequest.header("authx") == null && needsSignature(originalRequest)) {
                String signature = SignatureUtils.generateSignature(originalRequest);
                if (signature != null) {
                    requestBuilder.addHeader("authx", signature);
                    Log.d(TAG, "🔑 已添加API签名: " + signature.substring(0, Math.min(16, signature.length())) + "...");
                }
            } else if (originalRequest.header("authx") != null) {
                Log.d(TAG, "🔑 使用已有的authx签名，不重新生成");
            }
            
            Request newRequest = requestBuilder.build();
            
            Log.d(TAG, "🌐 请求: " + newRequest.method() + " " + newRequest.url());
            
            return chain.proceed(newRequest);
        }
        
        /**
         * 判断接口是否需要认证Token
         */
        private boolean needsAuth(Request request) {
            String url = request.url().toString();
            // 🚫 不需要认证的接口: getFnUrl, getQrCode, checkQrLogin
            return !url.contains("/api/getFnUrl") && 
                   !url.contains("/api/v1/logincode/tv") && 
                   !url.contains("/api/v1/logincode/check");
        }
        
        /**
         * 判断接口是否需要签名
         */
        private boolean needsSignature(Request request) {
            String url = request.url().toString();
            // 🚫 不需要签名的接口: getFnUrl, getQrCode
            return !url.contains("/api/getFnUrl") && 
                   !url.contains("/api/v1/logincode/tv");
        }
    }
}
