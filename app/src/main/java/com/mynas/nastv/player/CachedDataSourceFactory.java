package com.mynas.nastv.player;

import android.content.Context;
import android.util.Log;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.okhttp.OkHttpDataSource;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import okhttp3.OkHttpClient;

/**
 * 🎬 缓存数据源工厂
 * 
 * 提供带缓存的数据源，配合 VideoPrefetchService 实现：
 * - 多线程预缓存
 * - ExoPlayer 从缓存读取
 * - 支持 MKV 内嵌字幕解析
 */
public class CachedDataSourceFactory implements DataSource.Factory {
    private static final String TAG = "CachedDataSourceFactory";
    
    // 缓存配置
    private static final long MAX_CACHE_SIZE = 500 * 1024 * 1024; // 500MB 缓存
    private static final String CACHE_DIR = "video_cache";
    
    private static volatile Cache sharedCache;
    private static final Object cacheLock = new Object();
    private static boolean cacheInitFailed = false;
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final Map<String, String> headers;
    private final String cacheKey;
    
    private VideoPrefetchService prefetchService;
    
    public CachedDataSourceFactory(Context context, OkHttpClient httpClient, 
                                    Map<String, String> headers, String cacheKey) {
        this.context = context.getApplicationContext();
        this.httpClient = httpClient;
        this.headers = headers;
        this.cacheKey = cacheKey;
        
        Log.d(TAG, "🎬 CachedDataSourceFactory created, cacheKey=" + cacheKey);
    }
    
    /**
     * 获取或创建共享缓存
     */
    private static Cache getSharedCache(Context context) {
        if (cacheInitFailed) {
            return null;
        }
        
        synchronized (cacheLock) {
            if (sharedCache == null && !cacheInitFailed) {
                File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
                
                try {
                    // 先清理旧缓存
                    if (cacheDir.exists()) {
                        deleteDirectory(cacheDir);
                    }
                    cacheDir.mkdirs();
                    
                    LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE);
                    androidx.media3.database.StandaloneDatabaseProvider databaseProvider = 
                        new androidx.media3.database.StandaloneDatabaseProvider(context);
                    
                    sharedCache = new SimpleCache(cacheDir, evictor, databaseProvider);
                    Log.d(TAG, "🎬 Video cache initialized: " + cacheDir.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "🎬 Failed to create cache, will use direct network", e);
                    cacheInitFailed = true;
                    return null;
                }
            }
            return sharedCache;
        }
    }
    
    /**
     * 释放共享缓存
     */
    public static void releaseSharedCache() {
        synchronized (cacheLock) {
            if (sharedCache != null) {
                try {
                    sharedCache.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing cache", e);
                }
                sharedCache = null;
            }
            cacheInitFailed = false;
        }
    }
    
    /**
     * 清理缓存目录
     */
    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
    
    @Override
    public DataSource createDataSource() {
        Log.d(TAG, "🎬 createDataSource called");
        
        // 🔑 创建带 Range 头修复的 OkHttpClient
        // 问题：OkHttpDataSource 在某些情况下不发送 Range 头，导致服务器返回 416
        // 解决：拦截器确保所有请求都有 Range 头
        okhttp3.OkHttpClient fixedClient = httpClient.newBuilder()
            .addInterceptor(chain -> {
                okhttp3.Request original = chain.request();
                okhttp3.Request.Builder builder = original.newBuilder();
                
                // 🔑 关键修复：如果请求没有 Range 头，添加 Range: bytes=0-
                // 这样服务器就知道这是一个 Range 请求，会返回 206 而不是 416
                if (original.header("Range") == null) {
                    builder.header("Range", "bytes=0-");
                    Log.d(TAG, "🔧 Added missing Range header: bytes=0-");
                }
                
                okhttp3.Request request = builder.build();
                
                // 打印请求详情（调试用）
                Log.d(TAG, "🔍 HTTP Request: " + request.method() + " " + request.url());
                Log.d(TAG, "🔍 Range: " + request.header("Range"));
                
                okhttp3.Response response = chain.proceed(request);
                
                // 打印响应详情
                Log.d(TAG, "🔍 HTTP Response: " + response.code() + " " + response.message());
                
                return response;
            })
            .build();
        
        // 创建上游数据源（用于从网络获取）
        OkHttpDataSource.Factory upstreamFactory = new OkHttpDataSource.Factory(fixedClient);
        if (headers != null && !headers.isEmpty()) {
            upstreamFactory.setDefaultRequestProperties(headers);
            Log.d(TAG, "🎬 Headers set: " + headers.size() + " headers");
        }
        
        // 🚀 启用缓存功能，提升播放性能
        Log.d(TAG, "🎬 Using CacheDataSource with Range header fix");
        
        // 尝试获取缓存
        Cache cache = getSharedCache(context);
        if (cache == null) {
            Log.w(TAG, "🎬 Cache unavailable, using direct OkHttp");
            return upstreamFactory.createDataSource();
        }
        
        // 创建缓存数据源
        Log.d(TAG, "🎬 Creating CacheDataSource with cache");
        
        try {
            CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheWriteDataSinkFactory(
                    new androidx.media3.datasource.cache.CacheDataSink.Factory()
                        .setCache(cache)
                        .setFragmentSize(5 * 1024 * 1024) // 5MB fragments
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
            
            return cacheFactory.createDataSource();
        } catch (Exception e) {
            Log.e(TAG, "🎬 Error creating CacheDataSource, falling back to direct", e);
            return upstreamFactory.createDataSource();
        }
    }
    
    /**
     * 启动预缓存服务
     */
    public VideoPrefetchService startPrefetch(String url) {
        Cache cache = getSharedCache(context);
        if (cache == null) {
            Log.w(TAG, "🎬 Cannot start prefetch: cache unavailable");
            return null;
        }
        
        prefetchService = new VideoPrefetchService(httpClient, headers, cache, cacheKey);
        prefetchService.start(url);
        
        Log.d(TAG, "🎬 Prefetch service started");
        return prefetchService;
    }
    
    /**
     * 停止预缓存服务
     */
    public void stopPrefetch() {
        if (prefetchService != null) {
            prefetchService.stop();
            prefetchService = null;
        }
    }
    
    /**
     * 获取预缓存服务
     */
    public VideoPrefetchService getPrefetchService() {
        return prefetchService;
    }
    
    /**
     * 获取缓存 Key
     */
    public String getCacheKey() {
        return cacheKey;
    }
}
