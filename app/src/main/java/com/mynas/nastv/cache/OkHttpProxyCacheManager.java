package com.mynas.nastv.cache;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 基于 OkHttp 的缓存管理器 - 分块文件缓存
 * 
 * 目录结构：
 * okhttp_video_cache/
 * ├── {videoId}/              ← 视频目录 (md5(url))
 * │   ├── chunk_0.cache       ← 2MB
 * │   ├── chunk_1.cache       ← 2MB
 * │   └── ...
 * 
 * 特点：
 * 1. 每个 chunk 独立文件，可随时删除不影响其他 chunk
 * 2. 切换视频时删除其他视频目录
 * 3. 支持边下边播 + 智能预缓存
 */

public class OkHttpProxyCacheManager {
    private static final String TAG = "OkHttpProxyCacheManager";

    // 缓存配置
    private static final String CACHE_DIR = "okhttp_video_cache";
    private static final int CHUNK_SIZE = 2 * 1024 * 1024; // 2MB per chunk
    private static final int PROXY_PORT_START = 39500;
    private static final int PREFETCH_HEAD_CHUNKS = 3; // 预缓存头部 3 个块 (6MB)
    private static final int PREFETCH_TAIL_CHUNKS = 2; // 预缓存尾部 2 个块 (4MB)
    
    // 智能预缓存配置
    private static final int PREFETCH_AHEAD_MB = 50; // 提前缓存 50MB
    private static final int PREFETCH_AHEAD_CHUNKS = PREFETCH_AHEAD_MB / 2; // 25 个块
    private static final int PREFETCH_TRIGGER_CHUNKS = 5; // 当缓存剩余 5 个块时触发预缓存
    
    // 已播放 chunk 清理配置
    private static final int KEEP_BEHIND_CHUNKS = 3; // 保留当前位置前 3 个 chunk（支持回退）
    private static final long CLEANUP_PLAYED_INTERVAL_MS = 30 * 1000; // 每 30 秒清理一次已播放的 chunk
    
    // 缓存过期配置
    private static final long STALE_DIR_AGE_MS = 30 * 60 * 1000; // 30 分钟未修改的目录视为遗留
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000; // 每分钟检查一次
    
    // 磁盘空间限制配置
    private static final long MIN_FREE_SPACE_MB = 500; // 最少保留 500MB 可用空间
    
    // 单例
    private static OkHttpProxyCacheManager instance;
    
    // OkHttp 客户端
    private OkHttpClient httpClient;
    
    // 缓存状态
    private boolean mCacheFile;
    
    // 关键：静态 headers
    private static volatile Map<String, String> sCurrentHeaders = new HashMap<>();
    private static final Object sHeaderLock = new Object();
    
    // 本地代理服务器（静态，所有实例共享）
    private static ServerSocket proxyServer;
    private static int proxyPort = -1;
    private static AtomicBoolean isProxyRunning = new AtomicBoolean(false);
    private static ExecutorService proxyExecutor;
    
    // 当前播放的视频信息（静态，所有实例共享）
    private static String currentOriginUrl;
    private static String currentVideoId;  // md5(url) 作为视频唯一标识
    private static File currentVideoDir;   // 当前视频的缓存目录
    private static long currentContentLength = -1;
    private static Context appContext;
    
    // 分块缓存状态（静态，所有实例共享）
    private static ConcurrentHashMap<Integer, Boolean> cachedChunks = new ConcurrentHashMap<>();
    private static final Object cacheLock = new Object();
    
    // 播放位置跟踪（静态，所有实例共享）
    private static AtomicLong currentPlaybackPosition = new AtomicLong(0);
    private static AtomicInteger currentPlaybackChunk = new AtomicInteger(0);
    private static AtomicInteger prefetchTargetChunk = new AtomicInteger(0);
    private static AtomicBoolean isPrefetching = new AtomicBoolean(false);
    
    // ExoPlayer 是否正在使用代理
    private static boolean exoPlayerUsingProxy = false;
    
    // 定时清理任务
    private static ScheduledExecutorService cleanupScheduler;
    private static ScheduledFuture<?> cleanupTask;
    private static ScheduledFuture<?> playedChunkCleanupTask;
    
    // 缓冲区池 - 减少 GC
    private static final int BUFFER_POOL_SIZE = 4;
    private static final java.util.concurrent.LinkedBlockingQueue<byte[]> bufferPool = 
        new java.util.concurrent.LinkedBlockingQueue<>(BUFFER_POOL_SIZE);
    
    private static byte[] acquireBuffer() {
        byte[] buffer = bufferPool.poll();
        if (buffer == null) {
            buffer = new byte[CHUNK_SIZE + 1024]; // 稍大一点以容纳可能的额外数据
        }
        return buffer;
    }
    
    private static void releaseBuffer(byte[] buffer) {
        if (buffer != null && buffer.length >= CHUNK_SIZE) {
            bufferPool.offer(buffer);
        }
    }

    /**
     * 单例
     */
    public static synchronized OkHttpProxyCacheManager instance() {
        if (instance == null) {
            instance = new OkHttpProxyCacheManager(true);
        }
        return instance;
    }
    
    /**
     * 默认构造函数 - 被 CacheFactory.newInstance() 调用
     */
    public OkHttpProxyCacheManager() {
        if (instance != null) {
            this.httpClient = instance.httpClient;
            Log.d(TAG, "OkHttpProxyCacheManager: 复用单例 httpClient");
        } else {
            this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
            instance = this;
            Log.d(TAG, "OkHttpProxyCacheManager: 创建新实例并设为单例");
        }
    }
    
    /**
     * 私有构造函数 - 用于创建真正的单例
     */
    private OkHttpProxyCacheManager(boolean isSingleton) {
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build();
    }
    
    /**
     * 初始化定时清理任务（在 Application 中调用）
     */
    public static void initCleanupTask(Context context) {
        if (cleanupScheduler == null) {
            cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        }
        
        if (cleanupTask != null) {
            cleanupTask.cancel(false);
        }
        
        final Context ctx = context.getApplicationContext();
        cleanupTask = cleanupScheduler.scheduleAtFixedRate(() -> {
            cleanupStaleCacheDirectories(ctx);
        }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        Log.d(TAG, "Cleanup task initialized, interval=" + (CLEANUP_INTERVAL_MS / 1000) + "s");
        
        // 启动时立即清理一次
        cleanupScheduler.submit(() -> cleanupStaleCacheDirectories(ctx));
    }

    /**
     * 清理遗留的缓存目录（非当前视频的目录）
     */
    private static void cleanupStaleCacheDirectories(Context context) {
        try {
            File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
            if (!cacheDir.exists()) return;
            
            File[] dirs = cacheDir.listFiles();
            if (dirs == null || dirs.length == 0) return;
            
            long now = System.currentTimeMillis();
            int deletedCount = 0;
            long deletedSize = 0;
            
            for (File dir : dirs) {
                if (dir.isDirectory()) {
                    // 如果是当前正在播放的视频目录，跳过
                    if (currentVideoId != null && dir.getName().equals(currentVideoId)) {
                        continue;
                    }
                    
                    // 检查目录年龄
                    long age = now - dir.lastModified();
                    if (age > STALE_DIR_AGE_MS) {
                        long size = getDirectorySize(dir);
                        if (deleteDirectory(dir)) {
                            deletedCount++;
                            deletedSize += size;
                            Log.d(TAG, "Cleanup: deleted " + dir.getName() + " (age=" + (age / 60000) + "min)");
                        }
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.d(TAG, "Cleanup: deleted " + deletedCount + " dirs, freed " + (deletedSize / 1024 / 1024) + "MB");
            }
        } catch (Exception e) {
            Log.e(TAG, "Cleanup error: " + e.getMessage());
        }
    }
    
    /**
     * 删除其他视频的缓存目录（切换视频时调用）
     */
    private void cleanupOtherVideoDirectories(Context context, String currentVideoId) {
        try {
            File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
            if (!cacheDir.exists()) return;
            
            File[] dirs = cacheDir.listFiles();
            if (dirs == null || dirs.length == 0) return;
            
            int deletedCount = 0;
            long deletedSize = 0;
            
            for (File dir : dirs) {
                if (dir.isDirectory() && !dir.getName().equals(currentVideoId)) {
                    long size = getDirectorySize(dir);
                    if (deleteDirectory(dir)) {
                        deletedCount++;
                        deletedSize += size;
                        Log.d(TAG, "删除其他视频缓存: " + dir.getName() + " (" + (size / 1024 / 1024) + "MB)");
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.d(TAG, "清理完成: 删除 " + deletedCount + " 个视频目录, 释放 " + (deletedSize / 1024 / 1024) + "MB");
            }
        } catch (Exception e) {
            Log.e(TAG, "清理其他视频缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 启动已播放 chunk 清理任务
     */
    private void startPlayedChunkCleanupTask() {
        if (cleanupScheduler == null) {
            cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        }
        
        if (playedChunkCleanupTask != null) {
            playedChunkCleanupTask.cancel(false);
        }
        
        playedChunkCleanupTask = cleanupScheduler.scheduleAtFixedRate(() -> {
            cleanupPlayedChunks();
        }, CLEANUP_PLAYED_INTERVAL_MS, CLEANUP_PLAYED_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        Log.d(TAG, "Played chunk cleanup task started, interval=" + (CLEANUP_PLAYED_INTERVAL_MS / 1000) + "s");
    }
    
    /**
     * 清理已播放的 chunk（保留当前位置前 KEEP_BEHIND_CHUNKS 个）
     */
    private void cleanupPlayedChunks() {
        if (currentVideoDir == null || !currentVideoDir.exists()) return;
        
        int currentChunk = currentPlaybackChunk.get();
        int deleteBeforeChunk = currentChunk - KEEP_BEHIND_CHUNKS;
        
        if (deleteBeforeChunk <= 0) return;
        
        int deletedCount = 0;
        long deletedSize = 0;
        
        for (int i = 0; i < deleteBeforeChunk; i++) {
            File chunkFile = new File(currentVideoDir, "chunk_" + i + ".cache");
            if (chunkFile.exists()) {
                long size = chunkFile.length();
                if (chunkFile.delete()) {
                    cachedChunks.remove(i);
                    deletedCount++;
                    deletedSize += size;
                }
            }
        }
        
        if (deletedCount > 0) {
            Log.d(TAG, "Cleaned " + deletedCount + " played chunks, freed " + (deletedSize / 1024 / 1024) + "MB, current=" + currentChunk);
        }
    }

    public static void setCurrentHeaders(Map<String, String> headers) {
        synchronized (sHeaderLock) {
            sCurrentHeaders.clear();
            if (headers != null) sCurrentHeaders.putAll(headers);
            Log.d(TAG, "Headers updated: " + sCurrentHeaders.size() + " headers");
        }
    }
    
    public static Map<String, String> getCurrentHeaders() {
        synchronized (sHeaderLock) {
            return new HashMap<>(sCurrentHeaders);
        }
    }
    
    /**
     * 扫描已存在的 chunk 文件，并尝试推算内容长度
     */
    private void scanExistingChunks() {
        if (currentVideoDir == null || !currentVideoDir.exists()) return;
        
        File[] files = currentVideoDir.listFiles();
        if (files == null) return;
        
        int count = 0;
        int maxChunkIndex = -1;
        long lastChunkSize = 0;
        
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("chunk_") && name.endsWith(".cache")) {
                try {
                    int chunkIndex = Integer.parseInt(name.substring(6, name.length() - 6));
                    cachedChunks.put(chunkIndex, true);
                    count++;
                    
                    // 记录最大的 chunk 索引和其大小
                    if (chunkIndex > maxChunkIndex) {
                        maxChunkIndex = chunkIndex;
                        lastChunkSize = file.length();
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        
        if (count > 0) {
            Log.d(TAG, "Found " + count + " existing chunks, maxIndex=" + maxChunkIndex);
            
            // 如果有缓存且内容长度未知，尝试从缓存推算
            // 注意：这只是估算值，可能不准确，但可以让播放器先工作
            if (currentContentLength <= 0 && maxChunkIndex >= 0) {
                // 估算：(maxChunkIndex * CHUNK_SIZE) + lastChunkSize
                // 但这可能不准确，因为最后一个 chunk 可能不是真正的最后一个
                // 所以我们只在有足够多的 chunk 时才估算
                if (count > 3) {
                    long estimatedLength = (long) maxChunkIndex * CHUNK_SIZE + lastChunkSize;
                    Log.d(TAG, "Estimated content length from cache: " + (estimatedLength / 1024 / 1024) + "MB");
                    // 不直接设置，让 fetchContentLength 有机会获取准确值
                }
            }
        }
    }

    /**
     * [DEBUG] 列出缓存目录
     */
    private void listCacheDirectory(Context context) {
        try {
            File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
            Log.d(TAG, "缓存根目录: " + cacheDir.getAbsolutePath());
            
            if (!cacheDir.exists()) {
                Log.d(TAG, "缓存目录不存在");
                return;
            }
            
            File[] dirs = cacheDir.listFiles();
            if (dirs == null || dirs.length == 0) {
                Log.d(TAG, "缓存目录为空");
                return;
            }
            
            long totalSize = 0;
            Log.d(TAG, "视频缓存目录列表 (" + dirs.length + " 个):");
            for (File dir : dirs) {
                if (dir.isDirectory()) {
                    long size = getDirectorySize(dir);
                    totalSize += size;
                    int chunkCount = countChunkFiles(dir);
                    String marker = dir.getName().equals(currentVideoId) ? " ← 当前" : "";
                    Log.d(TAG, "  - " + dir.getName() + " (" + chunkCount + " chunks, " + (size / 1024 / 1024) + "MB)" + marker);
                }
            }
            Log.d(TAG, "缓存总大小: " + (totalSize / 1024 / 1024) + "MB");
        } catch (Exception e) {
            Log.e(TAG, "列出缓存目录失败: " + e.getMessage());
        }
    }
    
    private int countChunkFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            if (f.getName().startsWith("chunk_")) count++;
        }
        return count;
    }
    
    /**
     * 启动初始预缓存（头部 + 尾部）
     */
    private void startInitialPrefetch() {
        proxyExecutor.submit(() -> {
            try {
                if (currentContentLength <= 0) {
                    currentContentLength = fetchContentLength(currentOriginUrl, getCurrentHeaders());
                    if (currentContentLength <= 0) {
                        Log.e(TAG, "Prefetch failed: cannot get content length");
                        return;
                    }
                    Log.d(TAG, "Content length: " + (currentContentLength / 1024 / 1024) + "MB");
                }
                
                int totalChunks = (int) Math.ceil((double) currentContentLength / CHUNK_SIZE);
                Log.d(TAG, "Total chunks: " + totalChunks);
                
                // 预缓存头部
                for (int i = 0; i < Math.min(PREFETCH_HEAD_CHUNKS, totalChunks); i++) {
                    if (!isProxyRunning.get()) break;
                    if (!checkDiskSpaceForChunk()) break;
                    downloadAndCacheChunk(i);
                }
                
                // 预缓存尾部
                for (int i = 0; i < Math.min(PREFETCH_TAIL_CHUNKS, totalChunks); i++) {
                    if (!isProxyRunning.get()) break;
                    if (!checkDiskSpaceForChunk()) break;
                    int chunkIndex = totalChunks - 1 - i;
                    if (chunkIndex >= PREFETCH_HEAD_CHUNKS) {
                        downloadAndCacheChunk(chunkIndex);
                    }
                }
                
                prefetchTargetChunk.set(PREFETCH_HEAD_CHUNKS + PREFETCH_AHEAD_CHUNKS);
                
                Log.d(TAG, "Initial prefetch done: head=" + PREFETCH_HEAD_CHUNKS + 
                      ", tail=" + PREFETCH_TAIL_CHUNKS + ", target=" + prefetchTargetChunk.get());
                
            } catch (Exception e) {
                Log.e(TAG, "Initial prefetch error: " + e.getMessage());
            }
        });
    }
    
    /**
     * 检查是否有足够空间缓存一个块
     */
    private boolean checkDiskSpaceForChunk() {
        try {
            if (appContext == null) return true;
            
            File cacheDir = appContext.getCacheDir();
            long freeSpace = cacheDir.getFreeSpace();
            long required = CHUNK_SIZE + MIN_FREE_SPACE_MB * 1024 * 1024;
            
            if (freeSpace < required) {
                Log.w(TAG, "磁盘空间不足，停止预缓存");
                return false;
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 智能预缓存：根据播放位置提前缓存
     * 修复：当播放位置远离当前预缓存范围时，中断并重新开始
     */
    private void triggerSmartPrefetch(int playbackChunk) {
        if (currentContentLength <= 0) return;
        
        int totalChunks = (int) Math.ceil((double) currentContentLength / CHUNK_SIZE);
        
        // 计算播放位置后面连续缓存了多少块
        int cachedAhead = 0;
        for (int i = playbackChunk; i < Math.min(playbackChunk + PREFETCH_AHEAD_CHUNKS, totalChunks); i++) {
            if (cachedChunks.containsKey(i)) cachedAhead++;
            else break;
        }
        
        // 当缓存剩余不足 PREFETCH_TRIGGER_CHUNKS 块时，触发预缓存
        if (cachedAhead < PREFETCH_TRIGGER_CHUNKS) {
            int newTarget = Math.min(playbackChunk + PREFETCH_AHEAD_CHUNKS, totalChunks);
            if (newTarget > playbackChunk) {
                // 如果当前正在预缓存，检查是否需要中断
                // 如果播放位置远离当前预缓存目标（超过 PREFETCH_AHEAD_CHUNKS），则中断并重新开始
                int currentTarget = prefetchTargetChunk.get();
                boolean needInterrupt = isPrefetching.get() && 
                    (playbackChunk > currentTarget || playbackChunk < currentTarget - PREFETCH_AHEAD_CHUNKS * 2);
                
                if (needInterrupt) {
                    Log.d(TAG, "Smart prefetch interrupted: playback=" + playbackChunk + 
                          ", currentTarget=" + currentTarget + ", newTarget=" + newTarget);
                    // 强制重置预缓存状态，让新的预缓存可以开始
                    isPrefetching.set(false);
                }
                
                if (!isPrefetching.get()) {
                    Log.d(TAG, "Smart prefetch triggered: playback=" + playbackChunk + 
                          ", cachedAhead=" + cachedAhead + ", target=" + newTarget);
                    startSmartPrefetch(playbackChunk, newTarget);
                }
            }
        }
    }
    
    /**
     * 执行智能预缓存
     */
    private void startSmartPrefetch(int startChunk, int endChunk) {
        if (isPrefetching.compareAndSet(false, true)) {
            // 更新预缓存目标，用于判断是否需要中断
            prefetchTargetChunk.set(endChunk);
            
            proxyExecutor.submit(() -> {
                try {
                    int downloaded = 0;
                    int currentTarget = prefetchTargetChunk.get();
                    
                    for (int i = startChunk; i < endChunk && isProxyRunning.get(); i++) {
                        // 检查预缓存目标是否已改变（被中断）
                        if (prefetchTargetChunk.get() != currentTarget) {
                            Log.d(TAG, "Smart prefetch aborted: target changed from " + 
                                  currentTarget + " to " + prefetchTargetChunk.get());
                            break;
                        }
                        
                        if (!cachedChunks.containsKey(i)) {
                            if (!checkDiskSpaceForChunk()) {
                                Log.w(TAG, "磁盘空间不足，停止智能预缓存");
                                break;
                            }
                            downloadAndCacheChunk(i);
                            downloaded++;
                        }
                    }
                    Log.d(TAG, "Smart prefetch done: " + downloaded + " chunks (" + 
                          startChunk + "-" + endChunk + ")");
                } catch (Exception e) {
                    Log.e(TAG, "Smart prefetch error: " + e.getMessage());
                } finally {
                    isPrefetching.set(false);
                }
            });
        }
    }
    
    /**
     * 下载并缓存单个块（写入独立文件）
     */
    private void downloadAndCacheChunk(int chunkIndex) {
        if (cachedChunks.containsKey(chunkIndex)) return;
        
        long start = (long) chunkIndex * CHUNK_SIZE;
        long end = Math.min(start + CHUNK_SIZE - 1, currentContentLength - 1);
        
        byte[] data = downloadChunk(start, end);
        if (data != null && data.length > 0) {
            writeChunkToFile(chunkIndex, data);
            Log.d(TAG, "Prefetch chunk " + chunkIndex + " (" + (data.length/1024) + "KB)");
        }
    }
    
    /**
     * 写入 chunk 到独立文件
     */
    private void writeChunkToFile(int chunkIndex, byte[] data) {
        if (currentVideoDir == null) return;
        
        File chunkFile = new File(currentVideoDir, "chunk_" + chunkIndex + ".cache");
        synchronized (cacheLock) {
            try (RandomAccessFile raf = new RandomAccessFile(chunkFile, "rw")) {
                raf.write(data);
                cachedChunks.put(chunkIndex, true);
            } catch (Exception e) {
                Log.e(TAG, "Write chunk error: " + e.getMessage());
            }
        }
    }
    
    /**
     * 从文件读取 chunk 数据
     */
    private byte[] readChunkFromFile(int chunkIndex, long requestStart, long requestEnd) {
        if (currentVideoDir == null) {
            Log.e(TAG, "Read chunk error: currentVideoDir is null");
            return null;
        }
        
        File chunkFile = new File(currentVideoDir, "chunk_" + chunkIndex + ".cache");
        if (!chunkFile.exists()) {
            Log.d(TAG, "Chunk " + chunkIndex + " not in cache");
            cachedChunks.remove(chunkIndex);
            return null;
        }
        
        // 检查文件大小
        long fileSize = chunkFile.length();
        if (fileSize == 0) {
            Log.w(TAG, "Chunk " + chunkIndex + " file is empty, removing");
            try {
                chunkFile.delete();
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete empty chunk file: " + e.getMessage());
            }
            cachedChunks.remove(chunkIndex);
            return null;
        }
        
        long chunkStart = (long) chunkIndex * CHUNK_SIZE;
        int offsetInChunk = (int) (requestStart - chunkStart);
        int length = (int) (requestEnd - requestStart + 1);
        
        // 检查偏移量是否有效
        if (offsetInChunk < 0) {
            Log.e(TAG, "Invalid offset (negative): " + offsetInChunk + ", fileSize: " + fileSize);
            return null;
        }
        
        if (offsetInChunk >= fileSize) {
            Log.e(TAG, "Invalid offset (beyond file): " + offsetInChunk + ", fileSize: " + fileSize);
            return null;
        }
        
        // 调整读取长度
        int availableLength = (int) Math.min(length, fileSize - offsetInChunk);
        if (availableLength <= 0) {
            Log.e(TAG, "No data available: offset=" + offsetInChunk + ", fileSize=" + fileSize + ", length=" + length);
            return null;
        }
        
        synchronized (cacheLock) {
            RandomAccessFile raf = null;
            try {
                raf = new RandomAccessFile(chunkFile, "r");
                byte[] data = new byte[availableLength];
                raf.seek(offsetInChunk);
                int bytesRead = raf.read(data);
                
                if (bytesRead <= 0) {
                    Log.w(TAG, "Read chunk " + chunkIndex + " returned 0 bytes");
                    return null;
                }
                
                if (bytesRead < availableLength) {
                    Log.w(TAG, "Read less than expected: " + bytesRead + "/" + availableLength);
                    // 返回实际读取的数据
                    byte[] actualData = new byte[bytesRead];
                    System.arraycopy(data, 0, actualData, 0, bytesRead);
                    return actualData;
                }
                return data;
            } catch (java.io.FileNotFoundException e) {
                Log.w(TAG, "Chunk file not found (may have been deleted): " + chunkIndex);
                cachedChunks.remove(chunkIndex);
                return null;
            } catch (java.io.IOException e) {
                Log.e(TAG, "Read chunk " + chunkIndex + " IO error: " + e.getMessage());
                // 不删除缓存，可能是临时 IO 错误
                return null;
            } catch (Exception e) {
                Log.e(TAG, "Read chunk " + chunkIndex + " error: " + e.getMessage(), e);
                cachedChunks.remove(chunkIndex);
                return null;
            } finally {
                if (raf != null) {
                    try {
                        raf.close();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
    }

    private void startProxyServer() {
        if (isProxyRunning.get()) {
            Log.d(TAG, "Proxy already running on port " + proxyPort);
            return;
        }
        
        try {
            for (int port = PROXY_PORT_START; port < PROXY_PORT_START + 100; port++) {
                try {
                    proxyServer = new ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"));
                    proxyPort = port;
                    break;
                } catch (IOException e) {}
            }
            
            if (proxyServer == null) {
                Log.e(TAG, "Failed to find available port");
                return;
            }
            
            isProxyRunning.set(true);
            proxyExecutor = Executors.newCachedThreadPool();
            
            proxyExecutor.submit(() -> {
                Log.d(TAG, "Proxy server started on port " + proxyPort);
                while (isProxyRunning.get()) {
                    try {
                        Socket client = proxyServer.accept();
                        proxyExecutor.submit(() -> handleClient(client));
                    } catch (IOException e) {
                        if (isProxyRunning.get()) {
                            Log.e(TAG, "Proxy accept error: " + e.getMessage());
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start proxy server", e);
            proxyPort = -1;
        }
    }

    private void handleClient(Socket client) {
        try {
            InputStream input = client.getInputStream();
            OutputStream output = client.getOutputStream();
            
            byte[] buffer = new byte[8192];
            int len = input.read(buffer);
            if (len <= 0) {
                client.close();
                return;
            }
            
            String request = new String(buffer, 0, len);
            Log.d(TAG, "Proxy request: " + request.split("\r\n")[0]);
            
            long rangeStart = 0;
            long rangeEnd = -1;
            if (request.contains("Range:")) {
                String rangeLine = request.substring(request.indexOf("Range:"));
                rangeLine = rangeLine.substring(0, rangeLine.indexOf("\r\n"));
                String rangeValue = rangeLine.substring(rangeLine.indexOf("=") + 1);
                String[] parts = rangeValue.split("-");
                rangeStart = Long.parseLong(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    rangeEnd = Long.parseLong(parts[1]);
                }
            }
            
            // 更新播放位置
            int playbackChunk = (int) (rangeStart / CHUNK_SIZE);
            currentPlaybackChunk.set(playbackChunk);
            
            // 触发智能预缓存
            triggerSmartPrefetch(playbackChunk);
            
            Log.d(TAG, "Range: " + rangeStart + "-" + (rangeEnd > 0 ? rangeEnd : "") + 
                  " (chunk " + playbackChunk + ")");
            
            if (currentContentLength <= 0) {
                currentContentLength = fetchContentLength(currentOriginUrl, getCurrentHeaders());
                Log.d(TAG, "Content length: " + (currentContentLength / 1024 / 1024) + "MB");
            }
            
            // 如果仍然无法获取内容长度，尝试从缓存推算
            if (currentContentLength <= 0) {
                currentContentLength = estimateContentLengthFromCache();
                if (currentContentLength > 0) {
                    Log.d(TAG, "Estimated content length from cache: " + (currentContentLength / 1024 / 1024) + "MB");
                }
            }
            
            // 如果还是无法获取，尝试直接下载并获取长度
            if (currentContentLength <= 0) {
                Log.w(TAG, "Cannot get content length, trying direct download...");
                // 尝试直接下载第一个 chunk 来获取内容长度
                currentContentLength = fetchContentLengthViaDownload(currentOriginUrl, getCurrentHeaders());
                if (currentContentLength > 0) {
                    Log.d(TAG, "Got content length via download: " + (currentContentLength / 1024 / 1024) + "MB");
                }
            }
            
            if (currentContentLength <= 0) {
                Log.e(TAG, "Failed to get content length, returning 500");
                output.write("HTTP/1.1 500 Internal Server Error\r\n\r\n".getBytes());
                output.flush();
                client.close();
                return;
            }
            
            // 如果 rangeEnd 未指定（-1），需要判断是格式检测还是正常播放
            // ExoPlayer 的行为：
            // 1. Range: 0- 用于格式检测，返回 128KB 足够
            // 2. Range: start- 用于正常播放，但 ExoPlayer 只需要部分数据来解析 MP4 头部
            //    如果返回整个文件，ExoPlayer 会提前断开连接
            //    返回 10MB 足够 ExoPlayer 解析和初始播放，ExoPlayer 会按需发起新的 Range 请求
            if (rangeEnd < 0) {
                if (rangeStart == 0) {
                    // 格式检测请求，返回前 128KB
                    rangeEnd = Math.min(rangeStart + 128 * 1024 - 1, currentContentLength - 1);
                    Log.d(TAG, "Range end not specified, format detection, using " + rangeEnd);
                } else {
                    // 正常播放请求：返回 10MB，足够 ExoPlayer 解析和初始播放
                    // ExoPlayer 会按需发起新的 Range 请求，不会一次性读取整个文件
                    long maxReturn = rangeStart + 10 * 1024 * 1024 - 1; // 10MB
                    rangeEnd = Math.min(maxReturn, currentContentLength - 1);
                    Log.d(TAG, "Range end not specified, normal playback, using " + rangeEnd + 
                          " (10MB from " + rangeStart + ")");
                }
            } else if (rangeEnd >= currentContentLength) {
                rangeEnd = currentContentLength - 1;
            }
            long contentLength = rangeEnd - rangeStart + 1;
            
            String responseHeader = "HTTP/1.1 206 Partial Content\r\n" +
                "Content-Type: video/mp4\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Content-Range: bytes " + rangeStart + "-" + rangeEnd + "/" + currentContentLength + "\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Connection: close\r\n\r\n";
            
            try {
                output.write(responseHeader.getBytes());
                output.flush();
                
                try {
                    sendData(output, rangeStart, rangeEnd);
                    output.flush();
                } catch (java.io.IOException e) {
                    // sendData 中的异常不应该导致崩溃
                    // 如果已经发送了响应头，客户端可能已经断开，这是正常的
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && (errorMsg.contains("Broken pipe") || errorMsg.contains("Connection reset"))) {
                        Log.d(TAG, "Client disconnected during sendData (normal)");
                    } else {
                        Log.w(TAG, "Error in sendData: " + errorMsg);
                    }
                }
            } catch (java.net.SocketException e) {
                // 客户端断开连接，正常情况
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("Broken pipe") || errorMsg.contains("Connection reset"))) {
                    Log.d(TAG, "Client disconnected (normal)");
                } else {
                    Log.w(TAG, "Socket error: " + e.getMessage());
                }
            } finally {
                try {
                    client.close();
                } catch (Exception e) {
                    // ignore
                }
            }
            
        } catch (java.io.IOException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("Broken pipe")) {
                Log.d(TAG, "Client disconnected (broken pipe)");
            } else {
                Log.e(TAG, "Handle client error: " + errorMsg, e);
            }
            try { 
                client.close(); 
            } catch (Exception ignored) {}
        } catch (Exception e) {
            Log.e(TAG, "Handle client error: " + e.getMessage(), e);
            try { 
                client.close(); 
            } catch (Exception ignored) {}
        }
    }

    private void sendData(OutputStream output, long start, long end) throws IOException {
        long position = start;
        int retryCount = 0;
        final int MAX_CHUNK_RETRY = 3;
        long totalSent = 0; // 跟踪实际发送的总字节数
        long expectedTotal = end - start + 1; // 期望发送的总字节数
        
        Log.d(TAG, "sendData: start=" + start + ", end=" + end + ", expected=" + expectedTotal);
        
        while (position <= end && isProxyRunning.get()) {
            int chunkIndex = (int) (position / CHUNK_SIZE);
            long chunkStart = (long) chunkIndex * CHUNK_SIZE;
            long chunkEnd = Math.min(chunkStart + CHUNK_SIZE - 1, currentContentLength - 1);
            
            long sendStart = position;
            long sendEnd = Math.min(end, chunkEnd);
            
            boolean success = false;
            byte[] chunkData = null;
            
            // 先检查缓存文件
            if (cachedChunks.containsKey(chunkIndex)) {
                chunkData = readChunkFromFile(chunkIndex, sendStart, sendEnd);
                if (chunkData != null && chunkData.length > 0) {
                    try {
                        long expectedLength = sendEnd - sendStart + 1;
                        if (chunkData.length != expectedLength) {
                            Log.w(TAG, "Chunk data length mismatch: expected " + expectedLength + 
                                  ", got " + chunkData.length + " (chunk " + chunkIndex + 
                                  ", range " + sendStart + "-" + sendEnd + ")");
                            // 如果数据长度不对，只发送实际有的数据
                            if (chunkData.length < expectedLength) {
                                // 数据不足，可能需要从网络补充
                                chunkData = null;
                            } else {
                                // 数据太多，只发送需要的部分
                                byte[] trimmedData = new byte[(int)expectedLength];
                                System.arraycopy(chunkData, 0, trimmedData, 0, (int)expectedLength);
                                chunkData = trimmedData;
                            }
                        }
                        
                        if (chunkData != null) {
                            output.write(chunkData);
                            output.flush(); // 确保数据立即发送
                            totalSent += chunkData.length;
                            Log.d(TAG, "From cache: chunk " + chunkIndex + ", sent " + chunkData.length + 
                                  " bytes (total: " + totalSent + "/" + expectedTotal + ")");
                            position = sendEnd + 1;
                            retryCount = 0; // 重置重试计数
                            success = true;
                        }
                    } catch (java.net.SocketException e) {
                        // 客户端断开连接，停止发送
                        String errorMsg = e.getMessage();
                        if (errorMsg != null && (errorMsg.contains("Broken pipe") || errorMsg.contains("Connection reset"))) {
                            Log.d(TAG, "Client disconnected, stopping send");
                            return; // 直接返回，不再继续
                        }
                        throw e;
                    } catch (IOException e) {
                        Log.e(TAG, "Write cached chunk " + chunkIndex + " error: " + e.getMessage());
                        // 如果写入失败，尝试从网络下载
                        chunkData = null;
                    }
                } else {
                    // 缓存读取失败，从缓存中移除该 chunk，尝试从网络下载
                    Log.w(TAG, "Cache read failed for chunk " + chunkIndex + ", trying network");
                    cachedChunks.remove(chunkIndex);
                    chunkData = null;
                }
            }
            
            // 如果缓存失败，从网络下载
            if (!success && chunkData == null) {
                boolean authError = false;
                boolean serverError = false;
                
                for (int retry = 0; retry < MAX_CHUNK_RETRY; retry++) {
                    try {
                        chunkData = downloadChunk(chunkStart, chunkEnd);
                        if (chunkData != null && chunkData.length > 0) {
                            // 写入缓存文件
                            if (checkDiskSpaceForChunk()) {
                                writeChunkToFile(chunkIndex, chunkData);
                                Log.d(TAG, "From network -> cache: chunk " + chunkIndex);
                            } else {
                                Log.d(TAG, "From network (no space): chunk " + chunkIndex);
                            }
                            
                            // 检查下载的数据大小是否合理
                            long expectedChunkSize = chunkEnd - chunkStart + 1;
                            if (chunkData.length < expectedChunkSize * 0.5) {
                                // 数据明显小于预期，可能是错误响应
                                Log.e(TAG, "Downloaded chunk " + chunkIndex + " data too small: " + 
                                      chunkData.length + " bytes (expected ~" + expectedChunkSize + ")");
                                chunkData = null;
                                continue; // 继续重试
                            }
                            
                            // 发送请求的部分
                            int offsetInChunk = (int) (sendStart - chunkStart);
                            int lengthToSend = (int) (sendEnd - sendStart + 1);
                            
                            // 如果 chunkData 小于预期，但大于请求的长度，仍然可以发送
                            if (offsetInChunk < chunkData.length) {
                                int actualLengthToSend = Math.min(lengthToSend, chunkData.length - offsetInChunk);
                                if (actualLengthToSend > 0) {
                                    try {
                                        output.write(chunkData, offsetInChunk, actualLengthToSend);
                                        output.flush(); // 确保数据立即发送
                                        totalSent += actualLengthToSend;
                                        Log.d(TAG, "From network: chunk " + chunkIndex + ", sent " + actualLengthToSend + 
                                              " bytes (total: " + totalSent + "/" + expectedTotal + ")");
                                        position = sendStart + actualLengthToSend;
                                        retryCount = 0; // 重置重试计数
                                        success = true;
                                        
                                        if (actualLengthToSend < lengthToSend) {
                                            Log.w(TAG, "Sent partial chunk " + chunkIndex + ": " + 
                                                  actualLengthToSend + "/" + lengthToSend + " bytes");
                                        }
                                        break;
                                    } catch (java.net.SocketException e) {
                                        // 客户端断开连接，停止发送
                                        String errorMsg = e.getMessage();
                                        if (errorMsg != null && (errorMsg.contains("Broken pipe") || errorMsg.contains("Connection reset"))) {
                                            Log.d(TAG, "Client disconnected during send, stopping");
                                            return; // 直接返回，不再继续
                                        }
                                        throw e;
                                    }
                                } else {
                                    Log.e(TAG, "No data to send: offset=" + offsetInChunk + 
                                          ", dataLength=" + chunkData.length);
                                    chunkData = null;
                                }
                            } else {
                                Log.e(TAG, "Invalid chunk data offset: offset=" + offsetInChunk + 
                                      ", dataLength=" + chunkData.length);
                                chunkData = null;
                            }
                        } else {
                            Log.w(TAG, "Download chunk " + chunkIndex + " failed (retry " + (retry + 1) + "/" + MAX_CHUNK_RETRY + ")");
                            if (retry < MAX_CHUNK_RETRY - 1) {
                                try {
                                    Thread.sleep(500 * (retry + 1)); // 递增延迟
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        }
                    } catch (java.net.SocketException e) {
                        // 客户端断开连接，停止重试
                        String errorMsg = e.getMessage();
                        if (errorMsg != null && (errorMsg.contains("Broken pipe") || errorMsg.contains("Connection reset"))) {
                            Log.d(TAG, "Client disconnected, stopping retry");
                            return; // 直接返回，不再继续
                        }
                        Log.e(TAG, "Download/write chunk " + chunkIndex + " socket error: " + e.getMessage());
                        if (retry < MAX_CHUNK_RETRY - 1) {
                            try {
                                Thread.sleep(500 * (retry + 1));
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } catch (AuthenticationException e) {
                        // 鉴权失败，停止重试
                        Log.e(TAG, "Authentication failed for chunk " + chunkIndex + ": " + e.getMessage());
                        authError = true;
                        break; // 停止重试
                    } catch (ServerException e) {
                        // 服务器错误，停止重试
                        Log.e(TAG, "Server error for chunk " + chunkIndex + ": " + e.getMessage());
                        serverError = true;
                        break; // 停止重试
                    } catch (IOException e) {
                        Log.e(TAG, "Download/write chunk " + chunkIndex + " error: " + e.getMessage());
                        if (retry < MAX_CHUNK_RETRY - 1) {
                            try {
                                Thread.sleep(500 * (retry + 1));
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } catch (RuntimeException e) {
                        // 捕获其他运行时异常，避免崩溃
                        Log.e(TAG, "Unexpected runtime exception for chunk " + chunkIndex + ": " + e.getMessage(), e);
                        if (retry < MAX_CHUNK_RETRY - 1) {
                            try {
                                Thread.sleep(500 * (retry + 1));
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
            }
            
            // 如果仍然失败，记录错误
            if (!success) {
                Log.e(TAG, "Failed to send chunk " + chunkIndex + " after " + MAX_CHUNK_RETRY + " retries");
                retryCount++;
                if (retryCount >= 3) {
                    // 连续失败 3 个 chunk，记录错误但不抛出异常
                    // 抛出异常会导致响应不完整，ExoPlayer 会崩溃
                    // 直接返回，让客户端知道数据已结束
                    Log.e(TAG, "Too many consecutive failures, stopping send");
                    break; // 停止发送，而不是抛出异常
                }
                // 跳过这个 chunk，继续下一个
                position = sendEnd + 1;
            }
        }
        
        // 检查实际发送的数据是否与期望一致
        // 注意：如果客户端提前断开连接（Broken pipe），totalSent 可能小于 expectedTotal，这是正常的
        if (totalSent != expectedTotal) {
            Log.w(TAG, "Data length mismatch: expected " + expectedTotal + " bytes, but sent " + totalSent + 
                  " bytes (client may have disconnected)");
        } else {
            Log.d(TAG, "sendData completed: sent " + totalSent + " bytes (matches expected)");
        }
    }
    
    private byte[] downloadChunk(long start, long end) {
        Map<String, String> headers = getCurrentHeaders();
        
        try {
            Request.Builder builder = new Request.Builder()
                .url(currentOriginUrl)
                .header("Range", "bytes=" + start + "-" + end);
            
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            }
            
            try {
                String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature("GET", currentOriginUrl, "", null);
                if (signature != null) builder.header("authx", signature);
            } catch (Exception e) {
                Log.w(TAG, "Failed to generate signature: " + e.getMessage());
            }
            
            Response response = null;
            try {
                response = httpClient.newCall(builder.build()).execute();
                
                int code = response.code();
                okhttp3.ResponseBody body = response.body();
                
                if (body != null) {
                    long contentLength = body.contentLength();
                    long expectedSize = end - start + 1;
                    
                    // 如果响应很小，可能是错误响应，直接读取检查
                    if (contentLength > 0 && contentLength < 500) {
                        byte[] smallData = body.bytes();
                        try {
                            String responseText = new String(smallData, "UTF-8");
                            if (responseText.trim().startsWith("{") && responseText.contains("\"code\"")) {
                                if (responseText.contains("Auth Failed") || responseText.contains("\"code\":-2")) {
                                    Log.e(TAG, "鉴权失败: " + responseText);
                                    throw new AuthenticationException("鉴权失败: " + responseText);
                                } else {
                                    Log.e(TAG, "服务器返回错误响应: " + responseText);
                                    throw new ServerException("服务器错误: " + responseText);
                                }
                            }
                        } catch (AuthenticationException | ServerException e) {
                            throw e;
                        } catch (Exception e) {
                            // 忽略
                        }
                        return smallData;
                    }
                    
                    // 正常大小的响应，使用流式读取到复用缓冲区
                    if (code == 206 || (code >= 200 && code < 300)) {
                        java.io.InputStream inputStream = body.byteStream();
                        byte[] buffer = acquireBuffer();
                        try {
                            int totalRead = 0;
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer, totalRead, buffer.length - totalRead)) != -1) {
                                totalRead += bytesRead;
                                if (totalRead >= buffer.length) break;
                            }
                            
                            if (totalRead < 100) {
                                // 数据太小，可能是错误
                                String responseText = new String(buffer, 0, totalRead, "UTF-8");
                                Log.e(TAG, "Download chunk returned suspiciously small data: " + responseText);
                                releaseBuffer(buffer);
                                return null;
                            }
                            
                            // 返回实际大小的数组（这里仍需要创建新数组，但频率低很多）
                            byte[] result = new byte[totalRead];
                            System.arraycopy(buffer, 0, result, 0, totalRead);
                            releaseBuffer(buffer);
                            return result;
                        } catch (Exception e) {
                            releaseBuffer(buffer);
                            throw e;
                        }
                    }
                } else {
                    if (code == 206 || (code >= 200 && code < 300)) {
                        Log.e(TAG, "Download response body is null");
                    }
                }
                
                // 处理非 200-299 状态码
                if (code != 206 && (code < 200 || code >= 300)) {
                    Log.e(TAG, "Download failed: " + code + " " + response.message());
                }
            } finally {
                if (response != null) {
                    try {
                        response.close();
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to close response: " + e.getMessage());
                    }
                }
            }
        } catch (AuthenticationException e) {
            Log.e(TAG, "鉴权失败，停止下载: " + e.getMessage());
            throw e;
        } catch (ServerException e) {
            Log.e(TAG, "服务器错误，停止下载: " + e.getMessage());
            throw e;
        } catch (java.net.SocketTimeoutException e) {
            Log.e(TAG, "Download timeout: " + e.getMessage());
        } catch (java.net.UnknownHostException e) {
            Log.e(TAG, "Download unknown host: " + e.getMessage());
        } catch (java.io.IOException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("unexpected end of stream")) {
                Log.w(TAG, "Download stream ended unexpectedly");
            } else {
                Log.e(TAG, "Download IO error: " + errorMsg);
            }
        } catch (Exception e) {
            Log.e(TAG, "Download error: " + e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 鉴权异常
     */
    private static class AuthenticationException extends RuntimeException {
        AuthenticationException(String message) {
            super(message);
        }
    }
    
    /**
     * 服务器错误异常
     */
    private static class ServerException extends RuntimeException {
        ServerException(String message) {
            super(message);
        }
    }
    
    private long fetchContentLength(String url, Map<String, String> headers) {
        for (int retry = 0; retry < 3; retry++) {
            try {
                Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-0");
                
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        builder.addHeader(entry.getKey(), entry.getValue());
                    }
                }
                
                try {
                    String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature("GET", url, "", null);
                    if (signature != null) builder.header("authx", signature);
                } catch (Exception e) {}
                
                Response response = httpClient.newCall(builder.build()).execute();
                
                if (response.isSuccessful() || response.code() == 206) {
                    String contentRange = response.header("Content-Range");
                    if (contentRange != null && contentRange.contains("/")) {
                        String[] parts = contentRange.split("/");
                        if (parts.length == 2 && !parts[1].equals("*")) {
                            long length = Long.parseLong(parts[1]);
                            response.close();
                            return length;
                        }
                    }
                    
                    // 如果没有 Content-Range，尝试从 Content-Length 获取
                    String contentLength = response.header("Content-Length");
                    if (contentLength != null) {
                        // 这是 Range 请求返回的长度，不是总长度
                        // 但如果服务器不支持 Range，可能返回完整内容
                        Log.d(TAG, "No Content-Range, Content-Length: " + contentLength);
                    }
                } else {
                    Log.e(TAG, "Fetch content length failed: " + response.code() + " " + response.message());
                }
                response.close();
            } catch (Exception e) {
                Log.e(TAG, "Fetch content length error: " + e.getMessage());
                try { Thread.sleep(500); } catch (InterruptedException ie) { break; }
            }
        }
        return -1;
    }
    
    /**
     * 从缓存文件推算内容长度
     */
    private long estimateContentLengthFromCache() {
        if (currentVideoDir == null || !currentVideoDir.exists()) return -1;
        
        File[] files = currentVideoDir.listFiles();
        if (files == null || files.length == 0) return -1;
        
        int maxChunkIndex = -1;
        long lastChunkSize = 0;
        
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("chunk_") && name.endsWith(".cache")) {
                try {
                    int chunkIndex = Integer.parseInt(name.substring(6, name.length() - 6));
                    if (chunkIndex > maxChunkIndex) {
                        maxChunkIndex = chunkIndex;
                        lastChunkSize = file.length();
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        
        if (maxChunkIndex >= 0) {
            // 估算：前面的 chunks 都是 CHUNK_SIZE，最后一个可能小于 CHUNK_SIZE
            return (long) maxChunkIndex * CHUNK_SIZE + lastChunkSize;
        }
        
        return -1;
    }
    
    /**
     * 通过实际下载获取内容长度（备用方案）
     */
    private long fetchContentLengthViaDownload(String url, Map<String, String> headers) {
        try {
            Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1023"); // 下载前 1KB
            
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.addHeader(entry.getKey(), entry.getValue());
                }
            }
            
            try {
                String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature("GET", url, "", null);
                if (signature != null) builder.header("authx", signature);
            } catch (Exception e) {}
            
            Response response = httpClient.newCall(builder.build()).execute();
            
            if (response.isSuccessful() || response.code() == 206) {
                String contentRange = response.header("Content-Range");
                if (contentRange != null && contentRange.contains("/")) {
                    String[] parts = contentRange.split("/");
                    if (parts.length == 2 && !parts[1].equals("*")) {
                        long length = Long.parseLong(parts[1]);
                        response.close();
                        return length;
                    }
                }
            }
            response.close();
        } catch (Exception e) {
            Log.e(TAG, "Fetch content length via download error: " + e.getMessage());
        }
        return -1;
    }

    /**
     * 获取视频缓存目录
     */
    private File getVideoDirectory(Context context, String videoId) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists()) cacheDir.mkdirs();
        return new File(cacheDir, videoId);
    }
    
    private String md5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
    
    private static long getDirectorySize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += getDirectorySize(file);
                }
            }
        }
        return size;
    }
    
    private static boolean deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        return dir.delete();
    }
    
    private void stopProxyServer() {
        isProxyRunning.set(false);
        if (proxyServer != null) {
            try { proxyServer.close(); } catch (Exception e) {}
            proxyServer = null;
        }
        if (proxyExecutor != null) {
            proxyExecutor.shutdownNow();
            proxyExecutor = null;
        }
        proxyPort = -1;
        Log.d(TAG, "Proxy server stopped");
    }
    
    
    public void clearCache(Context context, File cachePath, String url) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (TextUtils.isEmpty(url)) {
            if (cacheDir.exists()) {
                long size = getDirectorySize(cacheDir);
                if (deleteDirectory(cacheDir)) {
                    Log.d(TAG, "clearCache: 清空所有缓存成功, 释放 " + (size / 1024 / 1024) + "MB");
                } else {
                    Log.e(TAG, "clearCache: 清空缓存失败");
                }
            } else {
                Log.d(TAG, "clearCache: 缓存目录不存在");
            }
        } else {
            String videoId = md5(url);
            File videoDir = new File(cacheDir, videoId);
            if (videoDir.exists()) {
                long size = getDirectorySize(videoDir);
                if (deleteDirectory(videoDir)) {
                    Log.d(TAG, "clearCache: 清空视频 " + videoId + " 缓存成功, 释放 " + (size / 1024 / 1024) + "MB");
                }
            }
        }
    }
    
    /**
     * 清空所有缓存并返回清理的大小（字节）
     */
    public long clearAllCache(Context context) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        long totalSize = 0;
        
        if (cacheDir.exists()) {
            totalSize = getDirectorySize(cacheDir);
            if (deleteDirectory(cacheDir)) {
                Log.d(TAG, "clearAllCache: 清空所有缓存成功, 释放 " + (totalSize / 1024 / 1024) + "MB");
            } else {
                Log.e(TAG, "clearAllCache: 清空缓存失败");
                totalSize = 0;
            }
        }
        
        // 重置状态
        cachedChunks.clear();
        currentVideoId = null;
        currentVideoDir = null;
        currentContentLength = -1;
        
        return totalSize;
    }
    
    /**
     * 获取缓存大小（字节）
     */
    public long getCacheSize(Context context) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (cacheDir.exists()) {
            return getDirectorySize(cacheDir);
        }
        return 0;
    }
    
    
    public void release() {
        Log.d(TAG, "========== release() 被调用 ==========");
        Log.d(TAG, "exoPlayerUsingProxy=" + exoPlayerUsingProxy);
        Log.d(TAG, "当前视频ID: " + (currentVideoId != null ? currentVideoId : "null"));
        
        if (exoPlayerUsingProxy) {
            Log.d(TAG, "ExoPlayer 正在使用代理，跳过释放");
            return;
        }
        
        // 停止已播放 chunk 清理任务
        if (playedChunkCleanupTask != null) {
            playedChunkCleanupTask.cancel(false);
            playedChunkCleanupTask = null;
        }
        
        stopProxyServer();
        
        // 删除当前视频的缓存目录
        if (currentVideoDir != null && currentVideoDir.exists()) {
            long size = getDirectorySize(currentVideoDir);
            String dirName = currentVideoDir.getName();
            if (deleteDirectory(currentVideoDir)) {
                Log.d(TAG, "release() 删除缓存目录: " + dirName + " (" + (size / 1024 / 1024) + "MB)");
            } else {
                Log.e(TAG, "release() 删除缓存目录失败: " + dirName);
            }
        }
        
        if (appContext != null) {
            listCacheDirectory(appContext);
        }
        
        currentOriginUrl = null;
        currentVideoId = null;
        currentVideoDir = null;
        currentContentLength = -1;
        cachedChunks.clear();
        
        Log.d(TAG, "========== release() 完成 ==========");
    }
    
    /**
     * 强制释放
     */
    public void forceRelease() {
        Log.d(TAG, "forceRelease() called");
        exoPlayerUsingProxy = false;
        release();
    }
    
    /**
     * 设置 ExoPlayer 是否正在使用代理
     */
    public static void setExoPlayerUsingProxy(boolean using) {
        exoPlayerUsingProxy = using;
        Log.d(TAG, "setExoPlayerUsingProxy: " + using);
    }
    
    
    public boolean cachePreview(Context context, File cacheDir, String url) { return false; }
    
    
    public boolean hadCached() { return mCacheFile; }
    
    public int getDownloadProgress() {
        if (currentContentLength <= 0) return 0;
        int totalChunks = (int) Math.ceil((double) currentContentLength / CHUNK_SIZE);
        return cachedChunks.size() * 100 / totalChunks;
    }
    
    public int getCachedChunksCount() { return cachedChunks.size(); }
    
    public int getCurrentPlaybackChunk() { return currentPlaybackChunk.get(); }

    /**
     * 获取代理 URL（供 ExoPlayer 使用）
     */
    public String getProxyUrl(Context context, String originUrl, Map<String, String> headers, File cachePath) {
        appContext = context.getApplicationContext();
        setCurrentHeaders(headers);
        
        boolean isDirectLink = originUrl.contains("direct_link_quality_index") ||
            (originUrl.startsWith("https://") && !originUrl.contains("192.168.") && !originUrl.contains("localhost"));
        
        if (isDirectLink && originUrl.startsWith("http") && !originUrl.contains(".m3u8")) {
            String newVideoId = md5(originUrl);
            
            Log.d(TAG, "========== ExoPlayer 切换视频 ==========");
            Log.d(TAG, "上一个视频ID: " + (currentVideoId != null ? currentVideoId : "null"));
            Log.d(TAG, "新视频ID: " + newVideoId);
            
            listCacheDirectory(context);
            
            // 检查是否是同一个视频
            boolean isSameVideo = newVideoId.equals(currentVideoId);
            
            // 删除其他视频的缓存目录
            cleanupOtherVideoDirectories(context, newVideoId);
            
            // 只有切换到不同视频时才重置状态
            if (!isSameVideo) {
                cachedChunks.clear();
                currentContentLength = -1;
                currentPlaybackPosition.set(0);
                currentPlaybackChunk.set(0);
                prefetchTargetChunk.set(0);
                isPrefetching.set(false);
            } else {
                // 同一个视频，保留内容长度和缓存状态
                Log.d(TAG, "Same video, keeping content length: " + (currentContentLength / 1024 / 1024) + "MB");
                currentPlaybackPosition.set(0);
                currentPlaybackChunk.set(0);
            }
            
            // 设置新视频信息
            currentOriginUrl = originUrl;
            currentVideoId = newVideoId;
            currentVideoDir = getVideoDirectory(context, newVideoId);
            
            if (!currentVideoDir.exists()) {
                currentVideoDir.mkdirs();
            }
            
            scanExistingChunks();
            
            Log.d(TAG, "ExoPlayer 视频缓存目录: " + currentVideoDir.getAbsolutePath());
            
            startProxyServer();
            
            if (proxyPort > 0) {
                String proxyUrl = "http://127.0.0.1:" + proxyPort + "/video";
                mCacheFile = true;
                Log.d(TAG, "ExoPlayer proxy URL: " + proxyUrl);
                
                startInitialPrefetch();
                startPlayedChunkCleanupTask();
                
                listCacheDirectory(context);
                
                return proxyUrl;
            } else {
                Log.e(TAG, "Proxy failed for ExoPlayer, using original URL");
                mCacheFile = false;
                return originUrl;
            }
        }
        
        return originUrl;
    }
}
