package com.mynas.nastv.player;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheSpan;
import androidx.media3.datasource.cache.ContentMetadata;

/**
 * 🚀 视频多线程预缓存服务
 * 
 * 功能：
 * - 后台多线程下载视频数据到 ExoPlayer 缓存
 * - 支持动态调整下载优先级（跟随播放位置）
 * - 与 CacheDataSource 配合，实现多线程加速 + MKV 解析
 */
public class VideoPrefetchService {
    private static final String TAG = "VideoPrefetchService";
    
    // 配置参数
    private static final int THREAD_COUNT = 4;           // 并发下载线程数
    private static final int CHUNK_SIZE = 2 * 1024 * 1024; // 每个块 2MB
    private static final int PREFETCH_CHUNKS = 10;       // 预缓存块数
    private static final int PRIORITY_CHUNKS = 3;        // 高优先级块数（播放位置附近）
    
    private final OkHttpClient httpClient;
    private final Map<String, String> headers;
    private final Cache cache;
    private final String cacheKey;
    
    private ExecutorService executorService;
    private final ConcurrentHashMap<Integer, Future<?>> downloadTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, AtomicBoolean> chunkDownloaded = new ConcurrentHashMap<>();
    
    private String videoUrl;
    private long contentLength = -1;
    private int totalChunks = 0;
    private AtomicLong currentPlaybackPosition = new AtomicLong(0);
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    
    public VideoPrefetchService(OkHttpClient httpClient, Map<String, String> headers, 
                                 Cache cache, String cacheKey) {
        this.httpClient = httpClient;
        this.headers = headers;
        this.cache = cache;
        this.cacheKey = cacheKey;
    }
    
    /**
     * 启动预缓存服务
     */
    public void start(String url) {
        if (isRunning.get()) {
            Log.w(TAG, "Service already running");
            return;
        }
        
        this.videoUrl = url;
        isRunning.set(true);
        
        // 创建线程池
        executorService = Executors.newFixedThreadPool(THREAD_COUNT + 1);
        
        // 启动调度线程
        executorService.submit(this::schedulerLoop);
        
        Log.d(TAG, "🚀 Prefetch service started for: " + url);
    }
    
    /**
     * 停止预缓存服务
     */
    public void stop() {
        isRunning.set(false);
        
        // 取消所有下载任务
        for (Future<?> task : downloadTasks.values()) {
            task.cancel(true);
        }
        downloadTasks.clear();
        
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        
        Log.d(TAG, "🛑 Prefetch service stopped");
    }
    
    /**
     * 更新播放位置（用于调整下载优先级）
     */
    public void updatePlaybackPosition(long positionBytes) {
        currentPlaybackPosition.set(positionBytes);
    }
    
    /**
     * 获取内容长度
     */
    public long getContentLength() {
        return contentLength;
    }
    
    /**
     * 调度循环 - 决定下载哪些块
     */
    private void schedulerLoop() {
        // 首先获取文件大小
        if (!fetchContentLength()) {
            Log.e(TAG, "Failed to get content length");
            return;
        }
        
        totalChunks = (int) Math.ceil((double) contentLength / CHUNK_SIZE);
        Log.d(TAG, "🚀 Total chunks: " + totalChunks + ", content length: " + contentLength);
        
        while (isRunning.get()) {
            try {
                // 计算当前播放位置对应的块
                int currentChunk = (int) (currentPlaybackPosition.get() / CHUNK_SIZE);
                
                // 优先下载播放位置附近的块
                for (int i = 0; i < PRIORITY_CHUNKS && isRunning.get(); i++) {
                    int chunkIndex = currentChunk + i;
                    if (chunkIndex < totalChunks) {
                        scheduleChunkDownload(chunkIndex, true);
                    }
                }
                
                // 预缓存后续块
                for (int i = PRIORITY_CHUNKS; i < PREFETCH_CHUNKS && isRunning.get(); i++) {
                    int chunkIndex = currentChunk + i;
                    if (chunkIndex < totalChunks) {
                        scheduleChunkDownload(chunkIndex, false);
                    }
                }
                
                // 等待一段时间再检查
                Thread.sleep(500);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e(TAG, "Scheduler error", e);
            }
        }
    }
    
    /**
     * 获取文件大小
     */
    private boolean fetchContentLength() {
        try {
            Request.Builder builder = new Request.Builder()
                .url(videoUrl)
                .head();
            
            // 添加请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            }
            
            // 服务器不支持 HEAD，改用 Range 请求获取
            builder.removeHeader("Range");
            builder = new Request.Builder()
                .url(videoUrl)
                .header("Range", "bytes=0-0");
            
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            }
            
            Response response = httpClient.newCall(builder.build()).execute();
            
            if (response.isSuccessful() || response.code() == 206) {
                String contentRange = response.header("Content-Range");
                if (contentRange != null && contentRange.contains("/")) {
                    String[] parts = contentRange.split("/");
                    if (parts.length == 2 && !parts[1].equals("*")) {
                        contentLength = Long.parseLong(parts[1]);
                        Log.d(TAG, "🚀 Content length from Range: " + contentLength);
                        response.close();
                        return true;
                    }
                }
                
                // 尝试从 Content-Length 获取
                String lengthHeader = response.header("Content-Length");
                if (lengthHeader != null) {
                    contentLength = Long.parseLong(lengthHeader);
                    Log.d(TAG, "🚀 Content length from header: " + contentLength);
                    response.close();
                    return true;
                }
            }
            
            response.close();
            Log.e(TAG, "Failed to get content length, response: " + response.code());
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching content length", e);
            return false;
        }
    }
    
    /**
     * 调度块下载
     */
    private void scheduleChunkDownload(int chunkIndex, boolean highPriority) {
        // 检查是否已下载或正在下载
        AtomicBoolean downloaded = chunkDownloaded.computeIfAbsent(chunkIndex, k -> new AtomicBoolean(false));
        if (downloaded.get()) {
            return;
        }
        
        // 检查缓存中是否已有数据
        if (isChunkCached(chunkIndex)) {
            downloaded.set(true);
            return;
        }
        
        // 检查是否已有下载任务
        if (downloadTasks.containsKey(chunkIndex)) {
            return;
        }
        
        // 提交下载任务
        Future<?> task = executorService.submit(() -> downloadChunk(chunkIndex));
        downloadTasks.put(chunkIndex, task);
        
        if (highPriority) {
            Log.v(TAG, "📥 Scheduled HIGH priority chunk " + chunkIndex);
        }
    }
    
    /**
     * 检查块是否已缓存
     */
    private boolean isChunkCached(int chunkIndex) {
        try {
            long start = (long) chunkIndex * CHUNK_SIZE;
            long end = Math.min(start + CHUNK_SIZE - 1, contentLength - 1);
            
            // 检查缓存中是否有这个范围的数据
            java.util.NavigableSet<CacheSpan> spans = cache.getCachedSpans(cacheKey);
            if (spans == null || spans.isEmpty()) {
                return false;
            }
            
            // 简单检查：是否有覆盖起始位置的 span
            for (CacheSpan span : spans) {
                if (span.position <= start && span.position + span.length > start) {
                    // 检查是否完全覆盖
                    if (span.position + span.length >= end) {
                        return true;
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 下载单个块
     */
    private void downloadChunk(int chunkIndex) {
        if (!isRunning.get()) return;
        
        long start = (long) chunkIndex * CHUNK_SIZE;
        long end = Math.min(start + CHUNK_SIZE - 1, contentLength - 1);
        
        Log.v(TAG, "📥 Downloading chunk " + chunkIndex + " [" + start + "-" + end + "]");
        
        try {
            Request.Builder builder = new Request.Builder()
                .url(videoUrl)
                .header("Range", "bytes=" + start + "-" + end);
            
            // 添加请求头
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            }
            
            Response response = httpClient.newCall(builder.build()).execute();
            
            if (response.isSuccessful() || response.code() == 206) {
                byte[] data = response.body().bytes();
                
                // 写入缓存
                writeToCacheCompat(start, data);
                
                // 标记已下载
                AtomicBoolean downloaded = chunkDownloaded.get(chunkIndex);
                if (downloaded != null) {
                    downloaded.set(true);
                }
                
                Log.v(TAG, "✅ Chunk " + chunkIndex + " downloaded: " + data.length + " bytes");
            } else {
                Log.w(TAG, "❌ Chunk " + chunkIndex + " failed: " + response.code());
            }
            
            response.close();
            
        } catch (Exception e) {
            if (isRunning.get()) {
                Log.e(TAG, "Error downloading chunk " + chunkIndex, e);
            }
        } finally {
            downloadTasks.remove(chunkIndex);
        }
    }
    
    /**
     * 兼容方式写入缓存
     * 注意：ExoPlayer 的 Cache 接口不直接支持写入，
     * 我们通过 CacheDataSource 的上游数据源机制间接实现
     */
    private void writeToCacheCompat(long position, byte[] data) {
        // ExoPlayer 的 SimpleCache 不提供直接写入 API
        // 数据会通过 CacheDataSource 在读取时自动缓存
        // 这里我们使用一个技巧：创建临时的 CacheDataSink 写入
        
        try {
            androidx.media3.datasource.cache.CacheDataSink sink = 
                new androidx.media3.datasource.cache.CacheDataSink(cache, CHUNK_SIZE);
            
            androidx.media3.datasource.DataSpec dataSpec = new androidx.media3.datasource.DataSpec.Builder()
                .setUri(videoUrl)
                .setPosition(position)
                .setLength(data.length)
                .setKey(cacheKey)
                .build();
            
            sink.open(dataSpec);
            sink.write(data, 0, data.length);
            sink.close();
            
        } catch (Exception e) {
            Log.e(TAG, "Error writing to cache at position " + position, e);
        }
    }
    
    /**
     * 获取缓存进度（0-100）
     */
    public int getCacheProgress() {
        if (contentLength <= 0) return 0;
        
        try {
            long cachedBytes = cache.getCachedBytes(cacheKey, 0, contentLength);
            return (int) (cachedBytes * 100 / contentLength);
        } catch (Exception e) {
            return 0;
        }
    }
}
