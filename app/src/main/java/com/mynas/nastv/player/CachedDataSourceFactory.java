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
import java.util.NavigableSet;

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
                    // 创建缓存目录（不删除已有缓存）
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs();
                    }
                    
                    LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE);
                    androidx.media3.database.StandaloneDatabaseProvider databaseProvider = 
                        new androidx.media3.database.StandaloneDatabaseProvider(context);
                    
                    sharedCache = new SimpleCache(cacheDir, evictor, databaseProvider);
                    Log.e(TAG, "Video cache initialized: " + cacheDir.getAbsolutePath());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create cache, will use direct network", e);
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
    /**
     * 释放共享缓存并清除缓存数据
     */
    public static void releaseSharedCache() {
        synchronized (cacheLock) {
            if (sharedCache != null) {
                try {
                    // 先获取缓存目录
                    sharedCache.release();
                    Log.e(TAG, "Shared cache released");
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing cache", e);
                }
                sharedCache = null;
            }
            cacheInitFailed = false;
        }
    }
    
    /**
     * 释放共享缓存并清除所有缓存文件（切换视频时使用）
     */
    public static void releaseAndClearCache(Context context) {
        synchronized (cacheLock) {
            if (sharedCache != null) {
                try {
                    sharedCache.release();
                    Log.e(TAG, "Shared cache released");
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing cache", e);
                }
                sharedCache = null;
            }
            
            // 清除缓存目录
            File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
            if (cacheDir.exists()) {
                deleteDirectory(cacheDir);
                Log.e(TAG, "Cache directory cleared: " + cacheDir.getAbsolutePath());
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
        Log.e(TAG, "createDataSource called, cacheKey=" + cacheKey);
        
        // 创建上游数据源（用于从网络获取）
        OkHttpDataSource.Factory upstreamFactory = new OkHttpDataSource.Factory(httpClient);
        if (headers != null && !headers.isEmpty()) {
            upstreamFactory.setDefaultRequestProperties(headers);
            Log.e(TAG, "Headers set: " + headers.size() + " headers");
        }
        
        // 尝试获取缓存
        Cache cache = getSharedCache(context);
        if (cache == null) {
            Log.e(TAG, "Cache unavailable, using direct OkHttp");
            return upstreamFactory.createDataSource();
        }
        
        // 检查缓存状态
        try {
            NavigableSet<androidx.media3.datasource.cache.CacheSpan> cachedSpans = 
                cache.getCachedSpans(cacheKey);
            if (cachedSpans != null && !cachedSpans.isEmpty()) {
                long totalCached = 0;
                for (androidx.media3.datasource.cache.CacheSpan span : cachedSpans) {
                    totalCached += span.length;
                }
                Log.e(TAG, "Found existing cache: " + (totalCached / 1024 / 1024) + "MB in " + 
                      cachedSpans.size() + " spans");
            } else {
                Log.e(TAG, "No existing cache found for this video");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check cache status", e);
        }
        
        // 创建统一的CacheKeyFactory
        final String key = cacheKey;
        final Cache finalCache = cache;
        final CachedDataSourceFactory factory = this;
        androidx.media3.datasource.cache.CacheKeyFactory cacheKeyFactory = 
            new androidx.media3.datasource.cache.CacheKeyFactory() {
                @Override
                public String buildCacheKey(DataSpec dataSpec) {
                    // 检查请求的数据是否在缓存中
                    long pos = dataSpec.position;
                    long len = dataSpec.length > 0 ? dataSpec.length : 2 * 1024 * 1024;
                    long cached = finalCache.getCachedBytes(key, pos, len);
                    
                    // 通知预缓存服务 ExoPlayer 的实际读取位置
                    // 只有当读取位置在文件前 90% 时才更新（排除文件尾部 MKV cues 的读取）
                    if (prefetchService != null && pos > 0) {
                        long contentLength = prefetchService.getContentLength();
                        long currentPrefetchPos = prefetchService.getCurrentPlaybackPosition();
                        
                        // 只有当读取位置在文件前 90% 且超过当前预缓存位置时才更新
                        boolean isNotTailRead = contentLength <= 0 || pos < contentLength * 0.9;
                        if (isNotTailRead && pos > currentPrefetchPos) {
                            // 使用强制更新，确保不会被 VideoPlayerActivity 的定时更新覆盖
                            prefetchService.forceUpdatePlaybackPosition(pos);
                            Log.e(TAG, "[EXOPLAYER-JUMP] ExoPlayer jumped to " + (pos/1024/1024) + "MB, force updating prefetch position");
                        }
                    }
                    
                    // 详细日志：检查缓存 spans
                    try {
                        NavigableSet<androidx.media3.datasource.cache.CacheSpan> spans = 
                            finalCache.getCachedSpans(key);
                        int spanCount = spans != null ? spans.size() : 0;
                        long totalCached = 0;
                        String nearbySpan = "none";
                        if (spans != null) {
                            for (androidx.media3.datasource.cache.CacheSpan span : spans) {
                                totalCached += span.length;
                                // 找到包含或接近请求位置的 span
                                if (span.position <= pos && span.position + span.length > pos) {
                                    nearbySpan = String.format("pos=%d len=%d", span.position, span.length);
                                }
                            }
                        }
                        Log.e(TAG, String.format("[EXOPLAYER-READ] pos=%dMB len=%dKB cached=%dKB spans=%d total=%dMB nearby=%s key=%s", 
                            pos/1024/1024, len/1024, cached/1024, spanCount, totalCached/1024/1024, nearbySpan, key));
                    } catch (Exception e) {
                        Log.e(TAG, String.format("[EXOPLAYER-READ] pos=%dMB len=%dKB cached=%dKB key=%s err=%s", 
                            pos/1024/1024, len/1024, cached/1024, key, e.getMessage()));
                    }
                    return key;
                }
            };
        
        // 创建缓存数据源
        Log.e(TAG, "Creating CacheDataSource with unified cache key");
        
        try {
            CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheKeyFactory(cacheKeyFactory)  // 关键：使用统一的cache key
                .setCacheWriteDataSinkFactory(
                    new androidx.media3.datasource.cache.CacheDataSink.Factory()
                        .setCache(cache)
                        .setFragmentSize(2 * 1024 * 1024) // 2MB fragments，与预缓存一致
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
            
            DataSource dataSource = cacheFactory.createDataSource();
            Log.e(TAG, "CacheDataSource created successfully");
            return dataSource;
        } catch (Exception e) {
            Log.e(TAG, "Error creating CacheDataSource, falling back to direct", e);
            return upstreamFactory.createDataSource();
        }
    }
    
    private android.os.Handler monitorHandler;
    private Runnable monitorRunnable;
    
    /**
     * 启动预缓存服务
     */
    public VideoPrefetchService startPrefetch(String url) {
        Cache cache = getSharedCache(context);
        if (cache == null) {
            Log.w(TAG, "Cannot start prefetch: cache unavailable");
            return null;
        }
        
        Log.e(TAG, "[FACTORY] Creating VideoPrefetchService...");
        prefetchService = new VideoPrefetchService(context, httpClient, headers, cache, cacheKey);
        Log.e(TAG, "[FACTORY] Calling prefetchService.start()...");
        prefetchService.start(url);
        Log.e(TAG, "[FACTORY] prefetchService.start() returned, isRunning=" + prefetchService.isRunning());
        Log.e(TAG, "[FACTORY] contentLength=" + prefetchService.getContentLength());
        
        // 启动监控线程，从Factory侧监控prefetch状态
        startPrefetchMonitor();
        
        Log.d(TAG, "Prefetch service started");
        return prefetchService;
    }
    
    /**
     * 启动预缓存监控（从Factory侧输出日志，绕过华为日志过滤）
     */
    private void startPrefetchMonitor() {
        monitorHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        monitorRunnable = new Runnable() {
            private int count = 0;
            @Override
            public void run() {
                if (prefetchService == null) {
                    Log.e(TAG, "[MONITOR] prefetchService is null, stopping");
                    return;
                }
                
                count++;
                boolean running = prefetchService.isRunning();
                long contentLen = prefetchService.getContentLength();
                int cachedChunks = prefetchService.getCachedAheadChunks();
                int threads = prefetchService.getCurrentThreadCount();
                int progress = prefetchService.getCacheProgress();
                
                Log.e(TAG, String.format("[MONITOR] #%d running=%b len=%dMB cached=%d threads=%d progress=%d%%",
                    count, running, contentLen/1024/1024, cachedChunks, threads, progress));
                
                // 检查缓存状态
                Cache cache = getSharedCache(context);
                if (cache != null && contentLen > 0) {
                    try {
                        long cachedBytes = cache.getCachedBytes(cacheKey, 0, contentLen);
                        Log.e(TAG, String.format("[MONITOR] Cache: %dMB / %dMB", 
                            cachedBytes/1024/1024, contentLen/1024/1024));
                    } catch (Exception e) {
                        Log.e(TAG, "[MONITOR] Cache check error: " + e.getMessage());
                    }
                }
                
                if (running && count < 60) {
                    monitorHandler.postDelayed(this, 2000);
                } else {
                    Log.e(TAG, "[MONITOR] Stopped, count=" + count + " running=" + running);
                }
            }
        };
        
        // 延迟1秒后开始监控
        monitorHandler.postDelayed(monitorRunnable, 1000);
        Log.e(TAG, "[FACTORY] Monitor scheduled");
    }
    
    /**
     * 停止预缓存服务
     */
    public void stopPrefetch() {
        // 停止监控
        if (monitorHandler != null && monitorRunnable != null) {
            monitorHandler.removeCallbacks(monitorRunnable);
            monitorHandler = null;
            monitorRunnable = null;
        }
        
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
