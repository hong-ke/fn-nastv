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

import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.okhttp.OkHttpDataSource;

public class VideoPrefetchService {
    private static final String TAG = "CachedDataSourceFactory";
    
    private static final int MAX_THREAD_COUNT = 4;
    private static final int MIN_THREAD_COUNT = 2;
    private static final int CHUNK_SIZE = 2 * 1024 * 1024;
    private static final int PREFETCH_CHUNKS = 6;
    private static final int LOW_CACHE_THRESHOLD = 6;
    private static final long MIN_FREE_MEMORY_MB = 50;
    private static final long SAFE_DISTANCE_BYTES = 3 * 1024 * 1024;
    private static final long STARTUP_DELAY_MS = 500;
    
    private final OkHttpClient httpClient;
    private final Map<String, String> headers;
    private final Cache cache;
    private final String cacheKey;
    private final Context context;
    
    private ExecutorService executorService;
    private final ConcurrentHashMap<Integer, Future<?>> downloadTasks = new ConcurrentHashMap<>();
    
    private String videoUrl;
    private long contentLength = -1;
    private int totalChunks = 0;
    private AtomicLong currentPlaybackPosition = new AtomicLong(0);
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    
    private AtomicInteger cachedAheadChunks = new AtomicInteger(0);
    private AtomicLong totalBytesDownloaded = new AtomicLong(0);
    private AtomicInteger activeDownloads = new AtomicInteger(0);
    private AtomicInteger downloadSuccessCount = new AtomicInteger(0);
    private AtomicInteger downloadFailCount = new AtomicInteger(0);
    private long lastStatsTime = 0;
    private long lastTotalBytes = 0;
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
        Log.e(TAG, "[PREFETCH] Created, cacheKey=" + cacheKey);
    }
    
    public void setBufferCallback(BufferCallback callback) {
        this.bufferCallback = callback;
    }
    
    public boolean isRunning() {
        return isRunning.get();
    }

    public void start(String url) {
        if (isRunning.get()) {
            Log.e(TAG, "[PREFETCH] Already running");
            return;
        }
        this.videoUrl = url;
        isRunning.set(true);
        Log.e(TAG, "[PREFETCH-START] URL: " + url.substring(0, Math.min(80, url.length())));
        Log.e(TAG, "[PREFETCH-START] cacheKey=" + cacheKey);
        executorService = Executors.newFixedThreadPool(MAX_THREAD_COUNT + 1);
        Log.e(TAG, "[PREFETCH-START] ExecutorService created");
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "[PREFETCH-THREAD] Started, threadId=" + Thread.currentThread().getId());
                try {
                    schedulerLoop();
                } catch (Exception e) {
                    Log.e(TAG, "[PREFETCH-THREAD] Crashed: " + e.getMessage(), e);
                }
                Log.e(TAG, "[PREFETCH-THREAD] Exited");
            }
        });
        Log.e(TAG, "[PREFETCH-START] Done");
    }
    
    public void stop() {
        Log.e(TAG, "[PREFETCH] stop()");
        isRunning.set(false);
        for (Future<?> task : downloadTasks.values()) {
            task.cancel(true);
        }
        downloadTasks.clear();
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }
    
    public void updatePlaybackPosition(long positionBytes) {
        currentPlaybackPosition.set(positionBytes);
    }
    
    public void notifyBufferingStart() {
        isBuffering.set(true);
        Log.e(TAG, "[PREFETCH] BUFFERING! Active:" + activeDownloads.get() + " Cached:" + cachedAheadChunks.get());
    }
    
    public void notifyBufferingEnd() {
        isBuffering.set(false);
    }
    
    public long getContentLength() {
        return contentLength;
    }
    
    private void schedulerLoop() {
        Log.e(TAG, "[PREFETCH-LOOP] Started");
        try {
            for (int i = 0; i < STARTUP_DELAY_MS / 100 && isRunning.get(); i++) {
                Thread.sleep(100);
            }
            if (!isRunning.get()) {
                Log.e(TAG, "[PREFETCH-LOOP] Stopped during delay");
                return;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "[PREFETCH-LOOP] Delay interrupted");
            return;
        }
        Log.e(TAG, "[PREFETCH-LOOP] Fetching content length...");
        if (!fetchContentLength()) {
            Log.e(TAG, "[PREFETCH-LOOP] Failed to get content length");
            return;
        }
        totalChunks = (int) Math.ceil((double) contentLength / CHUNK_SIZE);
        Log.e(TAG, "[PREFETCH-LOOP] Total: " + totalChunks + " chunks (" + (contentLength / 1024 / 1024) + "MB)");
        
        // 并行缓存：同时缓存 head chunks 和 tail chunks
        Log.e(TAG, "[PREFETCH-LOOP] Caching head and tail in parallel...");
        
        // 缓存 head chunks (0-5)
        int headChunks = Math.min(6, totalChunks);
        for (int i = 0; i < headChunks; i++) {
            if (!isChunkCachedQuiet(i)) {
                scheduleChunkDownload(i);
                Log.e(TAG, "[PREFETCH-LOOP] Scheduled head chunk " + i);
            }
        }
        
        // 缓存 tail chunks (最后3个)
        int tailCount = 3;
        for (int i = 0; i < tailCount && i < totalChunks; i++) {
            int chunkIndex = totalChunks - 1 - i;
            if (chunkIndex >= headChunks && !isChunkCachedQuiet(chunkIndex)) {
                scheduleChunkDownload(chunkIndex);
                Log.e(TAG, "[PREFETCH-LOOP] Scheduled tail chunk " + chunkIndex);
            }
        }
        
        // 等待关键缓存完成（至少 chunk 0 和最后一个 chunk）
        Log.e(TAG, "[PREFETCH-LOOP] Waiting for critical chunks...");
        try {
            int waitCount = 0;
            int maxWait = 50; // 最多等待 5 秒
            while (waitCount < maxWait && isRunning.get()) {
                boolean chunk0Ready = isChunkCachedQuiet(0);
                boolean tailReady = isChunkCachedQuiet(totalChunks - 1);
                int active = activeDownloads.get();
                
                if (waitCount % 10 == 0) {
                    int headCached = 0;
                    for (int i = 0; i < headChunks; i++) {
                        if (isChunkCachedQuiet(i)) headCached++;
                    }
                    Log.e(TAG, "[PREFETCH-LOOP] Wait #" + waitCount + " head=" + headCached + "/" + headChunks + 
                          " tail=" + tailReady + " active=" + active);
                }
                
                // 只要 chunk 0 和 tail 准备好就可以开始播放
                if (chunk0Ready && tailReady) {
                    Log.e(TAG, "[PREFETCH-LOOP] Critical chunks ready!");
                    printCacheSpans();
                    break;
                }
                
                Thread.sleep(100);
                waitCount++;
            }
            Log.e(TAG, "[PREFETCH-LOOP] Critical cache done, waited " + (waitCount * 100) + "ms");
        } catch (InterruptedException e) {
            Log.e(TAG, "[PREFETCH-LOOP] Critical cache wait interrupted");
        }
        
        int loopCount = 0;
        while (isRunning.get()) {
            try {
                loopCount++;
                if (!hasEnoughMemory()) {
                    Thread.sleep(1000);
                    continue;
                }
                // 从 chunk 0 开始缓存，不跳过文件头
                long playPos = currentPlaybackPosition.get();
                int playChunk = (int) (playPos / CHUNK_SIZE);
                int startChunk = Math.max(0, playChunk); // 确保从0开始
                
                cleanupOldChunkStates(startChunk);
                int cachedAhead = calculateCachedAheadChunks();
                cachedAheadChunks.set(cachedAhead);
                if (loopCount % 10 == 1 || System.currentTimeMillis() - lastStatsTime > 2000) {
                    Log.e(TAG, "[PREFETCH-LOOP] #" + loopCount + " pos=" + (playPos/1024/1024) + 
                          "MB chunk=" + startChunk + " cached=" + cachedAhead);
                    printCacheStatus(startChunk);
                    printDiagnostics(startChunk, cachedAhead);
                }
                int maxConcurrent = cachedAhead < LOW_CACHE_THRESHOLD ? MAX_THREAD_COUNT : MIN_THREAD_COUNT;
                for (int i = startChunk; i < startChunk + PREFETCH_CHUNKS && i < totalChunks; i++) {
                    if (activeDownloads.get() >= maxConcurrent) break;
                    if (downloadTasks.containsKey(i)) continue;
                    if (isChunkCachedQuiet(i)) continue;
                    if (scheduleChunkDownload(i)) {
                        Log.e(TAG, "[PREFETCH-LOOP] Scheduled chunk " + i);
                    }
                }
                int sleepTime = cachedAhead < LOW_CACHE_THRESHOLD ? 200 : 500;
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Log.e(TAG, "[PREFETCH-LOOP] Interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                Log.e(TAG, "[PREFETCH-LOOP] Error: " + e.getMessage(), e);
            }
        }
        Log.e(TAG, "[PREFETCH-LOOP] Exited");
    }
    
    private boolean hasEnoughMemory() {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);
            return memInfo.availMem / (1024 * 1024) >= MIN_FREE_MEMORY_MB;
        } catch (Exception e) {
            return true;
        }
    }
    
    private void cleanupOldChunkStates(int currentStartChunk) {
        if (currentStartChunk <= 0) return;
        java.util.Iterator<Map.Entry<Integer, Future<?>>> it = downloadTasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Future<?>> entry = it.next();
            if (entry.getKey() < currentStartChunk) {
                entry.getValue().cancel(true);
                it.remove();
            }
        }
    }
    
    private void printDiagnostics(int currentChunk, int cachedAhead) {
        long now = System.currentTimeMillis();
        if (now - lastStatsTime < 2000) return;
        long totalBytes = totalBytesDownloaded.get();
        long bytesInPeriod = totalBytes - lastTotalBytes;
        float speedMBps = bytesInPeriod / 1024f / 1024f / ((now - lastStatsTime) / 1000f);
        Log.e(TAG, String.format("[PREFETCH-STATS] Chunk:%d Cached:%d Active:%d Speed:%.2fMB/s Total:%dMB OK:%d Fail:%d",
            currentChunk, cachedAhead, activeDownloads.get(), speedMBps,
            (int)(totalBytes / 1024 / 1024), downloadSuccessCount.get(), downloadFailCount.get()));
        lastStatsTime = now;
        lastTotalBytes = totalBytes;
    }
    
    private void printCacheStatus(int startChunk) {
        try {
            StringBuilder sb = new StringBuilder("[PREFETCH-CACHE] [");
            for (int i = 0; i < Math.min(10, totalChunks - startChunk); i++) {
                int chunk = startChunk + i;
                long start = (long) chunk * CHUNK_SIZE;
                long length = Math.min(CHUNK_SIZE, contentLength - start);
                long cachedBytes = cache.getCachedBytes(cacheKey, start, length);
                sb.append(cachedBytes >= length * 0.9 ? "O" : cachedBytes > 0 ? "~" : "X");
            }
            sb.append("]");
            Log.e(TAG, sb.toString());
        } catch (Exception e) {}
    }
    
    private void printCacheSpans() {
        try {
            java.util.NavigableSet<androidx.media3.datasource.cache.CacheSpan> spans = 
                cache.getCachedSpans(cacheKey);
            if (spans == null || spans.isEmpty()) {
                Log.e(TAG, "[PREFETCH-SPANS] No spans found for key=" + cacheKey);
                return;
            }
            Log.e(TAG, "[PREFETCH-SPANS] Total " + spans.size() + " spans:");
            int count = 0;
            for (androidx.media3.datasource.cache.CacheSpan span : spans) {
                if (count < 5 || count >= spans.size() - 3) {
                    Log.e(TAG, String.format("[PREFETCH-SPANS] #%d pos=%dMB len=%dKB", 
                        count, span.position/1024/1024, span.length/1024));
                } else if (count == 5) {
                    Log.e(TAG, "[PREFETCH-SPANS] ... (skipping middle spans)");
                }
                count++;
            }
        } catch (Exception e) {
            Log.e(TAG, "[PREFETCH-SPANS] Error: " + e.getMessage());
        }
    }
    
    private int calculateCachedAheadChunks() {
        long playPos = currentPlaybackPosition.get();
        int playChunk = (int) (playPos / CHUNK_SIZE);
        int startChunk = Math.max(0, playChunk); // 从当前播放位置开始
        int count = 0;
        for (int i = 0; i < totalChunks - startChunk; i++) {
            if (isChunkCached(startChunk + i)) count++;
            else break;
        }
        return count;
    }
    
    private boolean isChunkCachedQuiet(int chunkIndex) {
        try {
            long start = (long) chunkIndex * CHUNK_SIZE;
            long end = Math.min(start + CHUNK_SIZE - 1, contentLength - 1);
            long length = end - start + 1;
            long cachedBytes = cache.getCachedBytes(cacheKey, start, length);
            boolean cached = cachedBytes >= length * 0.9;
            if (!cached && cachedBytes > 0) {
                Log.e(TAG, "[PREFETCH-CHECK] Chunk " + chunkIndex + " partial: " + cachedBytes + "/" + length);
            }
            return cached;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean fetchContentLength() {
        Log.e(TAG, "[PREFETCH-FETCH] URL: " + videoUrl.substring(0, Math.min(80, videoUrl.length())));
        for (int retry = 0; retry < 3; retry++) {
            if (!isRunning.get()) return false;
            try {
                okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                    .url(videoUrl)
                    .header("Range", "bytes=0-0");
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        builder.addHeader(entry.getKey(), entry.getValue());
                    }
                }
                Log.e(TAG, "[PREFETCH-FETCH] Request #" + (retry + 1));
                okhttp3.Response response = httpClient.newCall(builder.build()).execute();
                Log.e(TAG, "[PREFETCH-FETCH] Response: " + response.code());
                if (response.isSuccessful() || response.code() == 206) {
                    String contentRange = response.header("Content-Range");
                    Log.e(TAG, "[PREFETCH-FETCH] Content-Range: " + contentRange);
                    if (contentRange != null && contentRange.contains("/")) {
                        String[] parts = contentRange.split("/");
                        if (parts.length == 2 && !parts[1].equals("*")) {
                            contentLength = Long.parseLong(parts[1]);
                            Log.e(TAG, "[PREFETCH-FETCH] Length: " + (contentLength / 1024 / 1024) + "MB");
                            response.close();
                            return true;
                        }
                    }
                }
                response.close();
            } catch (Exception e) {
                Log.e(TAG, "[PREFETCH-FETCH] Error: " + e.getMessage());
                if (Thread.currentThread().isInterrupted()) return false;
                try { Thread.sleep(500); } catch (InterruptedException ie) { return false; }
            }
        }
        return false;
    }
    
    private boolean scheduleChunkDownload(int chunkIndex) {
        if (downloadTasks.containsKey(chunkIndex) || isChunkCachedQuiet(chunkIndex)) return false;
        final int chunk = chunkIndex;
        try {
            Future<?> task = executorService.submit(() -> downloadChunk(chunk));
            downloadTasks.put(chunkIndex, task);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isChunkCached(int chunkIndex) {
        try {
            long start = (long) chunkIndex * CHUNK_SIZE;
            long end = Math.min(start + CHUNK_SIZE - 1, contentLength - 1);
            long cachedBytes = cache.getCachedBytes(cacheKey, start, end - start + 1);
            return cachedBytes >= (end - start + 1) * 0.9;
        } catch (Exception e) {
            return false;
        }
    }

    private void downloadChunk(int chunkIndex) {
        if (!isRunning.get()) return;
        
        long start = (long) chunkIndex * CHUNK_SIZE;
        long length = Math.min(CHUNK_SIZE, contentLength - start);
        
        // 下载前再次检查是否已缓存（可能被ExoPlayer缓存了）
        long cachedBefore = 0;
        try {
            cachedBefore = cache.getCachedBytes(cacheKey, start, length);
            if (cachedBefore >= length * 0.9) {
                Log.e(TAG, "[PREFETCH-DL] Chunk " + chunkIndex + " already cached by ExoPlayer, skip");
                return;
            }
        } catch (Exception e) {}
        
        activeDownloads.incrementAndGet();
        long startTime = System.currentTimeMillis();
        Log.e(TAG, "[PREFETCH-DL] Chunk " + chunkIndex + " start, range=" + start + "-" + (start+length-1) + ", existing=" + cachedBefore/1024 + "KB");
        try {
            OkHttpDataSource.Factory dataSourceFactory = new OkHttpDataSource.Factory(httpClient);
            if (headers != null && !headers.isEmpty()) {
                dataSourceFactory.setDefaultRequestProperties(headers);
            }
            final String key = cacheKey;
            CacheDataSource.Factory cacheFactory = new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(dataSourceFactory)
                .setCacheKeyFactory(dataSpec -> key)
                .setCacheWriteDataSinkFactory(
                    new androidx.media3.datasource.cache.CacheDataSink.Factory()
                        .setCache(cache)
                        .setFragmentSize(CHUNK_SIZE)
                )
                .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE);  // 阻塞等待缓存写入
            CacheDataSource cacheDataSource = cacheFactory.createDataSource();
            DataSpec dataSpec = new DataSpec.Builder()
                .setUri(videoUrl)
                .setPosition(start)
                .setLength(length)
                .setKey(key)
                .build();
            CacheWriter cacheWriter = new CacheWriter(cacheDataSource, dataSpec, null, null);
            cacheWriter.cache();
            long elapsed = System.currentTimeMillis() - startTime;
            
            // 验证缓存是否真的写入了
            long cachedAfter = cache.getCachedBytes(key, start, length);
            if (cachedAfter < length * 0.9) {
                // 缓存写入失败，记录详细信息
                Log.e(TAG, String.format("[PREFETCH-DL] Chunk %d WRITE FAILED: downloaded but cached=%dKB/%dKB", 
                    chunkIndex, cachedAfter/1024, length/1024));
                downloadFailCount.incrementAndGet();
            } else {
                totalBytesDownloaded.addAndGet(length);
                downloadSuccessCount.incrementAndGet();
                Log.e(TAG, String.format("[PREFETCH-DL] Chunk %d done: %dKB in %dms", chunkIndex, length/1024, elapsed));
            }
        } catch (Exception e) {
            downloadFailCount.incrementAndGet();
            Log.e(TAG, "[PREFETCH-DL] Chunk " + chunkIndex + " error: " + e.getMessage());
        } finally {
            activeDownloads.decrementAndGet();
            downloadTasks.remove(chunkIndex);
        }
    }
    
    public int getCacheProgress() {
        if (contentLength <= 0) return 0;
        try {
            return (int) (cache.getCachedBytes(cacheKey, 0, contentLength) * 100 / contentLength);
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
        return "cached:" + cachedAheadChunks.get() + " | threads:" + activeDownloads.get();
    }
}
