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
            
            // 创建带日志的 DataSource 包装器
            final CacheDataSource cacheDataSource = cacheFactory.createDataSource();
            final CachedDataSourceFactory factoryRef = this;
            Log.e(TAG, "CacheDataSource created successfully");
            
            // 返回一个包装器，用于记录所有 open/read/close 操作
            final long[] totalBytesRead = {0}; // 用于跟踪已读取的总字节数
            return new DataSource() {
                @Override
                public void addTransferListener(TransferListener transferListener) {
                    cacheDataSource.addTransferListener(transferListener);
                }
                
                @Override
                public long open(DataSpec dataSpec) throws IOException {
                    totalBytesRead[0] = 0; // 重置计数器
                    // 获取 prefetchService 的 contentLength，用于验证请求范围
                    long prefetchContentLength = -1;
                    if (factoryRef.prefetchService != null) {
                        prefetchContentLength = factoryRef.prefetchService.getContentLength();
                    }
                    
                    Log.e(TAG, "[DS-OPEN] pos=" + (dataSpec.position/1024/1024) + "MB len=" + 
                          (dataSpec.length > 0 ? dataSpec.length/1024 + "KB" : "unknown") + 
                          " uri=" + dataSpec.uri.toString().substring(0, Math.min(60, dataSpec.uri.toString().length())) +
                          " prefetchLen=" + (prefetchContentLength > 0 ? (prefetchContentLength/1024/1024) + "MB" : "unknown"));
                    
                    // 检查请求位置是否超出文件大小
                    if (prefetchContentLength > 0 && dataSpec.position >= prefetchContentLength) {
                        Log.e(TAG, "[DS-OPEN] WARNING: Request position " + (dataSpec.position/1024/1024) + "MB >= contentLength " + (prefetchContentLength/1024/1024) + "MB, this will cause 416 error");
                    }
                    
                    try {
                        long result = cacheDataSource.open(dataSpec);
                        Log.e(TAG, "[DS-OPEN] CacheDataSource returned: " + (result > 0 ? result/1024/1024 + "MB" : result));
                        
                        // 如果 CacheDataSource 返回 -1（长度未知），尝试从 prefetchService 获取长度
                        if (result == -1 && factoryRef.prefetchService != null) {
                            // 等待 prefetchService 获取 contentLength（最多等待2秒）
                            long contentLength = factoryRef.prefetchService.getContentLength();
                            int waitCount = 0;
                            while (contentLength <= 0 && waitCount < 20 && factoryRef.prefetchService.isRunning()) {
                                try {
                                    Thread.sleep(100);
                                    contentLength = factoryRef.prefetchService.getContentLength();
                                    waitCount++;
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                            
                            if (contentLength > 0) {
                                // 计算从当前位置到文件末尾的长度
                                long remainingLength = contentLength - dataSpec.position;
                                if (remainingLength < 0) {
                                    Log.e(TAG, "[DS-OPEN] ERROR: Remaining length is negative: " + remainingLength + ", position=" + dataSpec.position + ", contentLength=" + contentLength);
                                    remainingLength = 0; // 避免返回负数
                                }
                                Log.e(TAG, "[DS-OPEN] Using prefetch contentLength: " + (contentLength/1024/1024) + "MB, remaining: " + (remainingLength/1024/1024) + "MB");
                                return remainingLength;
                            } else {
                                // 如果还是获取不到长度，返回-1让ExoPlayer使用流式读取
                                // ExoPlayer会通过读取到EOF来判断文件结束，不会预分配大量内存
                                Log.e(TAG, "[DS-OPEN] ContentLength not available yet, returning -1 for streaming read");
                                return -1;
                            }
                        }
                        
                        return result;
                    } catch (IOException e) {
                        Log.e(TAG, "[DS-OPEN] ERROR: " + e.getMessage() + ", position=" + dataSpec.position + ", length=" + dataSpec.length);
                        if (e.getMessage() != null && e.getMessage().contains("416")) {
                            Log.e(TAG, "[DS-OPEN] 416 Error details: position=" + (dataSpec.position/1024/1024) + "MB, prefetchContentLength=" + (prefetchContentLength > 0 ? (prefetchContentLength/1024/1024) + "MB" : "unknown"));
                        }
                        throw e;
                    }
                }
                
                @Override
                public int read(byte[] buffer, int offset, int length) throws IOException {
                    // 检查请求参数
                    if (length <= 0) {
                        Log.e(TAG, "[DS-READ] ERROR: Invalid read request, length=" + length + ", offset=" + offset + ", buffer.length=" + (buffer != null ? buffer.length : 0));
                        return -1; // 返回EOF表示错误
                    }
                    
                    int result = cacheDataSource.read(buffer, offset, length);
                    
                    // 记录前32字节的十六进制内容，用于判断视频格式
                    if (result > 0 && totalBytesRead[0] < 32) {
                        int bytesToLog = Math.min(result, (int)(32 - totalBytesRead[0]));
                        StringBuilder hex = new StringBuilder();
                        for (int i = 0; i < bytesToLog; i++) {
                            hex.append(String.format("%02X ", buffer[offset + i] & 0xFF));
                        }
                        Log.e(TAG, "[DS-READ] First bytes (offset=" + totalBytesRead[0] + "): " + hex.toString().trim());
                        totalBytesRead[0] += result;
                        
                        // 如果已经读取了32字节，尝试识别格式
                        if (totalBytesRead[0] >= 32) {
                            String format = identifyFormat(buffer, offset, result);
                            if (format != null) {
                                Log.e(TAG, "[DS-READ] Detected format: " + format);
                            }
                        }
                    }
                    
                    // 记录读取操作（限制频率）
                    if (result > 0) {
                        if (totalBytesRead[0] <= 32 || result > 100 * 1024) {
                            Log.e(TAG, "[DS-READ] Success: requested=" + length + " bytes, read=" + result + " bytes, total=" + totalBytesRead[0] + " bytes");
                        }
                    } else if (result == 0) {
                        Log.e(TAG, "[DS-READ] WARNING: read returned 0 bytes, requested=" + length + " bytes");
                    } else if (result == -1) {
                        Log.e(TAG, "[DS-READ] EOF reached, requested=" + length + " bytes");
                    }
                    return result;
                }
                
                // 识别视频格式
                private String identifyFormat(byte[] buffer, int offset, int length) {
                    if (length < 4) return null;
                    
                    // MP4: 查找 ftyp box (通常在第4-8字节)
                    if (length >= 8) {
                        // 检查 MP4: 00 00 00 ?? 66 74 79 70 (ftyp)
                        if (buffer[offset + 4] == 0x66 && buffer[offset + 5] == 0x74 && 
                            buffer[offset + 6] == 0x79 && buffer[offset + 7] == 0x70) {
                            return "MP4";
                        }
                    }
                    
                    // MKV: 1A 45 DF A3
                    if (buffer[offset] == 0x1A && buffer[offset + 1] == 0x45 && 
                        buffer[offset + 2] == (byte)0xDF && buffer[offset + 3] == (byte)0xA3) {
                        return "MKV/WebM";
                    }
                    
                    // AVI: 52 49 46 46 (RIFF)
                    if (buffer[offset] == 0x52 && buffer[offset + 1] == 0x49 && 
                        buffer[offset + 2] == 0x46 && buffer[offset + 3] == 0x46) {
                        return "AVI";
                    }
                    
                    // FLV: 46 4C 56 01 (FLV)
                    if (buffer[offset] == 0x46 && buffer[offset + 1] == 0x4C && 
                        buffer[offset + 2] == 0x56 && buffer[offset + 3] == 0x01) {
                        return "FLV";
                    }
                    
                    // MP3: FF FB 或 FF F3
                    if (buffer[offset] == (byte)0xFF && (buffer[offset + 1] == (byte)0xFB || buffer[offset + 1] == (byte)0xF3)) {
                        return "MP3";
                    }
                    
                    // ID3 (MP3 with ID3 tag): 49 44 33 (ID3)
                    if (buffer[offset] == 0x49 && buffer[offset + 1] == 0x44 && buffer[offset + 2] == 0x33) {
                        return "MP3 (with ID3)";
                    }
                    
                    return null;
                }
                
                @Override
                public android.net.Uri getUri() {
                    return cacheDataSource.getUri();
                }
                
                @Override
                public java.util.Map<String, java.util.List<String>> getResponseHeaders() {
                    return cacheDataSource.getResponseHeaders();
                }
                
                @Override
                public void close() throws IOException {
                    Log.e(TAG, "[DS-CLOSE]");
                    cacheDataSource.close();
                }
            };
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
        
        // 为预缓存服务创建独立的 OkHttpClient，避免与 ExoPlayer 共享连接池导致冲突
        okhttp3.Dispatcher prefetchDispatcher = new okhttp3.Dispatcher();
        prefetchDispatcher.setMaxRequests(32);
        prefetchDispatcher.setMaxRequestsPerHost(8);
        
        okhttp3.ConnectionPool prefetchConnectionPool = new okhttp3.ConnectionPool(
            8, 3, java.util.concurrent.TimeUnit.MINUTES);
        
        OkHttpClient prefetchClient = httpClient.newBuilder()
            .dispatcher(prefetchDispatcher)
            .connectionPool(prefetchConnectionPool)
            .build();
        
        prefetchService = new VideoPrefetchService(context, prefetchClient, headers, cache, cacheKey);
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
