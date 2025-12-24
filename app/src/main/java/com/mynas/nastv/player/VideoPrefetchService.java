package com.mynas.nastv.player;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.okhttp.OkHttpDataSource;

/**
 * 🚀 视频多线程预缓存服务
 * 
 * 使用 ExoPlayer 官方的 CacheWriter API 实现多线程预缓存。
 * CacheWriter 会正确地将数据写入 SimpleCache。
 * 
 * 🔧 内存优化：
 * - 减少线程数避免 OOM
 * - 动态调整下载策略
 * - 内存不足时暂停预缓存
 */
public class VideoPrefetchService {
    private static final String TAG = "VideoPrefetchService";
    
    // 配置参数 - 针对低内存设备优化
    private static final int MAX_THREAD_COUNT = 4;  // 从8降到4，减少内存压力
    private static final int CHUNK_SIZE = 2 * 1024 * 1024; // 2MB per chunk
    private static final int PREFETCH_CHUNKS = 8;  // 从15降到8，减少预缓存范围
    private static final long MIN_FREE_MEMORY_MB = 50; // 最小可用内存阈值
    
    private final OkHttpClient httpClient;
    private final Map<String, String> headers;
    private final Cache cache;
    private final String cacheKey;
    private final Context context;  // 用于内存检查
    
    private ExecutorService executorService;
    private final ConcurrentHashMap<Integer, Future<?>> downloadTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, AtomicBoolean> chunkDownloaded = new ConcurrentHashMap<>();
    
    private String videoUrl;
    private long contentLength = -1;
    private int totalChunks = 0;
    private AtomicLong currentPlaybackPosition = new AtomicLong(0);
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    
    // 统计
    private AtomicInteger cachedAheadChunks = new AtomicInteger(0);
    private AtomicLong totalBytesDownloaded = new AtomicLong(0);
    private AtomicInteger activeDownloads = new AtomicInteger(0);
    private AtomicInteger downloadSuccessCount = new AtomicInteger(0);
    private AtomicInteger downloadFailCount = new AtomicInteger(0);
    private long lastStatsTime = 0;
    private long lastTotalBytes = 0;
    
    // 卡顿状态
    private AtomicBoolean isBuffering = new AtomicBoolean(false);
    
    public interface BufferCallback {
        void onBufferStatusChanged(int cachedChunks, int threadCount, boolean isLowBuffer);
    }
    private BufferCallback bufferCallback;
    
    public VideoPrefetchService(Context context, OkHttpClient httpClient, Map<String, String> headers, 
                                 Cache cache, String cacheKey) {
        this.context = context.getApplicationContext();
        this.httpClient = httpClient;
        this.headers = headers;
        this.cache = cache;
        this.cacheKey = cacheKey;
        
        Log.d(TAG, "🔧 VideoPrefetchService created (optimized for low memory)");
    }
    
    public void setBufferCallback(BufferCallback callback) {
        this.bufferCallback = callback;
    }

    public void start(String url) {
        if (isRunning.get()) {
            Log.w(TAG, "Service already running");
            return;
        }
        
        this.videoUrl = url;
        isRunning.set(true);
        
        // 创建线程池
        executorService = Executors.newFixedThreadPool(MAX_THREAD_COUNT + 1);
        
        // 启动调度线程
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                schedulerLoop();
            }
        });
        
        Log.d(TAG, "🚀 Prefetch service started with " + MAX_THREAD_COUNT + " threads");
    }
    
    public void stop() {
        isRunning.set(false);
        
        for (Future<?> task : downloadTasks.values()) {
            task.cancel(true);
        }
        downloadTasks.clear();
        
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        
        Log.d(TAG, "🛑 Prefetch stopped. Downloaded: " + (totalBytesDownloaded.get() / 1024 / 1024) + "MB");
    }
    
    public void updatePlaybackPosition(long positionBytes) {
        currentPlaybackPosition.set(positionBytes);
    }
    
    public void notifyBufferingStart() {
        isBuffering.set(true);
        Log.w(TAG, "⚠️ BUFFERING! Active: " + activeDownloads.get() + ", Cached: " + cachedAheadChunks.get());
    }
    
    public void notifyBufferingEnd() {
        isBuffering.set(false);
        Log.d(TAG, "✅ Buffering ended");
    }
    
    public long getContentLength() {
        return contentLength;
    }
    
    private void schedulerLoop() {
        // 获取文件大小
        if (!fetchContentLength()) {
            Log.e(TAG, "❌ Failed to get content length");
            return;
        }
        
        totalChunks = (int) Math.ceil((double) contentLength / CHUNK_SIZE);
        Log.d(TAG, "🚀 Total: " + totalChunks + " chunks (" + (contentLength / 1024 / 1024) + "MB)");
        
        while (isRunning.get()) {
            try {
                // 🔧 内存检查：内存不足时暂停预缓存
                if (!hasEnoughMemory()) {
                    Log.w(TAG, "⚠️ Low memory, pausing prefetch...");
                    Thread.sleep(1000);
                    continue;
                }
                
                int currentChunk = (int) (currentPlaybackPosition.get() / CHUNK_SIZE);
                int cachedAhead = calculateCachedAheadChunks();
                cachedAheadChunks.set(cachedAhead);
                
                // 打印诊断信息
                printDiagnostics(currentChunk, cachedAhead);
                
                // 🔧 动态调整：已缓存足够时减少下载
                int maxConcurrent = cachedAhead >= 5 ? 2 : MAX_THREAD_COUNT;
                
                // 调度下载任务
                int scheduled = 0;
                for (int i = 0; i < PREFETCH_CHUNKS && isRunning.get(); i++) {
                    int chunkIndex = currentChunk + i;
                    if (chunkIndex < totalChunks && activeDownloads.get() < maxConcurrent) {
                        if (scheduleChunkDownload(chunkIndex)) {
                            scheduled++;
                        }
                    }
                }
                
                if (scheduled > 0) {
                    Log.d(TAG, "📋 Scheduled " + scheduled + " downloads (max:" + maxConcurrent + ")");
                }
                
                // 缓存少时检查更频繁
                int sleepTime = cachedAhead < 3 ? 200 : 500;
                Thread.sleep(sleepTime);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e(TAG, "Scheduler error", e);
            }
        }
    }
    
    /**
     * 🔧 检查是否有足够内存进行预缓存
     */
    private boolean hasEnoughMemory() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);
            
            long availableMB = memInfo.availMem / (1024 * 1024);
            
            if (availableMB < MIN_FREE_MEMORY_MB) {
                Log.w(TAG, "⚠️ Low memory: " + availableMB + "MB available");
                return false;
            }
            return true;
        } catch (Exception e) {
            return true; // 出错时默认允许
        }
    }
    
    private void printDiagnostics(int currentChunk, int cachedAhead) {
        long now = System.currentTimeMillis();
        if (now - lastStatsTime < 2000) return;
        
        long totalBytes = totalBytesDownloaded.get();
        long bytesInPeriod = totalBytes - lastTotalBytes;
        float speedMBps = bytesInPeriod / 1024f / 1024f / ((now - lastStatsTime) / 1000f);
        
        Log.d(TAG, String.format("📊 Chunk:%d | Cached:%d | Active:%d | Speed:%.2fMB/s | Total:%dMB | OK:%d | Fail:%d",
            currentChunk, cachedAhead, activeDownloads.get(), speedMBps,
            (int)(totalBytes / 1024 / 1024), downloadSuccessCount.get(), downloadFailCount.get()));
        
        lastStatsTime = now;
        lastTotalBytes = totalBytes;
    }
    
    private int calculateCachedAheadChunks() {
        int currentChunk = (int) (currentPlaybackPosition.get() / CHUNK_SIZE);
        int cachedCount = 0;
        
        for (int i = 0; i < PREFETCH_CHUNKS && (currentChunk + i) < totalChunks; i++) {
            if (isChunkCached(currentChunk + i)) {
                cachedCount++;
            } else {
                break;
            }
        }
        
        return cachedCount;
    }

    private boolean fetchContentLength() {
        try {
            okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                .url(videoUrl)
                .header("Range", "bytes=0-0");
            
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            }
            
            okhttp3.Response response = httpClient.newCall(builder.build()).execute();
            
            if (response.isSuccessful() || response.code() == 206) {
                String contentRange = response.header("Content-Range");
                if (contentRange != null && contentRange.contains("/")) {
                    String[] parts = contentRange.split("/");
                    if (parts.length == 2 && !parts[1].equals("*")) {
                        contentLength = Long.parseLong(parts[1]);
                        Log.d(TAG, "🚀 Content length: " + (contentLength / 1024 / 1024) + "MB");
                        response.close();
                        return true;
                    }
                }
            }
            
            response.close();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error fetching content length", e);
            return false;
        }
    }
    
    private boolean scheduleChunkDownload(int chunkIndex) {
        // 检查是否已下载
        AtomicBoolean downloaded = chunkDownloaded.get(chunkIndex);
        if (downloaded == null) {
            downloaded = new AtomicBoolean(false);
            chunkDownloaded.put(chunkIndex, downloaded);
        }
        if (downloaded.get()) {
            return false;
        }
        
        // 检查缓存
        if (isChunkCached(chunkIndex)) {
            downloaded.set(true);
            return false;
        }
        
        // 检查是否已有下载任务
        if (downloadTasks.containsKey(chunkIndex)) {
            return false;
        }
        
        // 提交下载任务
        final int chunk = chunkIndex;
        try {
            Future<?> task = executorService.submit(new Runnable() {
                @Override
                public void run() {
                    downloadChunkWithCacheWriter(chunk);
                }
            });
            downloadTasks.put(chunkIndex, task);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to submit task for chunk " + chunkIndex, e);
            return false;
        }
    }
    
    private boolean isChunkCached(int chunkIndex) {
        try {
            long start = (long) chunkIndex * CHUNK_SIZE;
            long end = Math.min(start + CHUNK_SIZE - 1, contentLength - 1);
            
            // 使用 cache.getCachedBytes 检查
            long cachedBytes = cache.getCachedBytes(cacheKey, start, end - start + 1);
            return cachedBytes >= (end - start + 1) * 0.9; // 90% 以上认为已缓存
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 🔑 使用 CacheWriter 下载并缓存数据
     * CacheWriter 是 ExoPlayer 官方提供的预缓存 API
     */
    private void downloadChunkWithCacheWriter(int chunkIndex) {
        if (!isRunning.get()) return;
        
        activeDownloads.incrementAndGet();
        long start = (long) chunkIndex * CHUNK_SIZE;
        long length = Math.min(CHUNK_SIZE, contentLength - start);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // 创建 OkHttpDataSource
            OkHttpDataSource.Factory dataSourceFactory = new OkHttpDataSource.Factory(httpClient);
            if (headers != null && !headers.isEmpty()) {
                dataSourceFactory.setDefaultRequestProperties(headers);
            }
            
            // 创建 CacheDataSource（用于写入缓存）
            CacheDataSource.Factory cacheDataSourceFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(dataSourceFactory)
                .setCacheWriteDataSinkFactory(
                    new androidx.media3.datasource.cache.CacheDataSink.Factory()
                        .setCache(cache)
                        .setFragmentSize(CHUNK_SIZE)
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
            
            // 创建 CacheDataSource 实例
            CacheDataSource cacheDataSource = cacheDataSourceFactory.createDataSource();
            
            // 创建 DataSpec
            DataSpec dataSpec = new DataSpec.Builder()
                .setUri(videoUrl)
                .setPosition(start)
                .setLength(length)
                .setKey(cacheKey)
                .build();
            
            // 🔑 使用 CacheWriter 预缓存
            CacheWriter cacheWriter = new CacheWriter(
                cacheDataSource,
                dataSpec,
                null,  // 不需要临时缓冲
                null   // 不需要进度回调
            );
            
            // 执行缓存（这会下载数据并写入缓存）
            cacheWriter.cache();
            
            long elapsed = System.currentTimeMillis() - startTime;
            float speedMBps = length / 1024f / 1024f / (elapsed / 1000f);
            
            // 标记已下载
            AtomicBoolean downloaded = chunkDownloaded.get(chunkIndex);
            if (downloaded != null) {
                downloaded.set(true);
            }
            
            totalBytesDownloaded.addAndGet(length);
            downloadSuccessCount.incrementAndGet();
            
            Log.d(TAG, String.format("✅ Chunk %d: %dKB in %dms (%.2fMB/s)", 
                chunkIndex, length / 1024, elapsed, speedMBps));
            
        } catch (Exception e) {
            downloadFailCount.incrementAndGet();
            Log.e(TAG, "❌ Chunk " + chunkIndex + " error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        } finally {
            activeDownloads.decrementAndGet();
            downloadTasks.remove(chunkIndex);
        }
    }
    
    public int getCacheProgress() {
        if (contentLength <= 0) return 0;
        try {
            long cachedBytes = cache.getCachedBytes(cacheKey, 0, contentLength);
            return (int) (cachedBytes * 100 / contentLength);
        } catch (Exception e) {
            return 0;
        }
    }
    
    public int getCachedAheadChunks() {
        return cachedAheadChunks.get();
    }
    
    public int getCurrentThreadCount() {
        return activeDownloads.get();
    }
    
    public String getBufferStatus() {
        int cached = cachedAheadChunks.get();
        int active = activeDownloads.get();
        return "缓存:" + cached + "块 | 下载:" + active;
    }
}
