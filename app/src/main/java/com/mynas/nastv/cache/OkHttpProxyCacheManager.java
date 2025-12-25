package com.mynas.nastv.cache;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.shuyu.gsyvideoplayer.cache.ICacheManager;

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

import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * 🔧 基于 OkHttp 的缓存管理器 - 分块文件缓存
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
public class OkHttpProxyCacheManager implements ICacheManager {
    private static final String TAG = "OkHttpProxyCacheManager";

    // 缓存配置
    private static final String CACHE_DIR = "okhttp_video_cache";
    private static final int CHUNK_SIZE = 2 * 1024 * 1024; // 2MB per chunk
    private static final int PROXY_PORT_START = 39500;
    private static final int PREFETCH_HEAD_CHUNKS = 3; // 预缓存头部 3 个块 (6MB)
    private static final int PREFETCH_TAIL_CHUNKS = 2; // 预缓存尾部 2 个块 (4MB)
    
    // 🔑 智能预缓存配置
    private static final int PREFETCH_AHEAD_MB = 50; // 提前缓存 50MB
    private static final int PREFETCH_AHEAD_CHUNKS = PREFETCH_AHEAD_MB / 2; // 25 个块
    private static final int PREFETCH_TRIGGER_CHUNKS = 5; // 当缓存剩余 5 个块时触发预缓存
    
    // 🔑 已播放 chunk 清理配置
    private static final int KEEP_BEHIND_CHUNKS = 3; // 保留当前位置前 3 个 chunk（支持回退）
    private static final long CLEANUP_PLAYED_INTERVAL_MS = 30 * 1000; // 每 30 秒清理一次已播放的 chunk
    
    // 🔑 缓存过期配置
    private static final long STALE_DIR_AGE_MS = 30 * 60 * 1000; // 30 分钟未修改的目录视为遗留
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000; // 每分钟检查一次
    
    // 🔑 磁盘空间限制配置
    private static final long MIN_FREE_SPACE_MB = 500; // 最少保留 500MB 可用空间
    
    // 单例
    private static OkHttpProxyCacheManager instance;
    
    // OkHttp 客户端
    private OkHttpClient httpClient;
    
    // 缓存状态
    private boolean mCacheFile;
    
    // 缓存监听
    private ICacheAvailableListener cacheAvailableListener;
    
    // 🔑 关键：静态 headers
    private static volatile Map<String, String> sCurrentHeaders = new HashMap<>();
    private static final Object sHeaderLock = new Object();
    
    // 🔑 本地代理服务器（静态，所有实例共享）
    private static ServerSocket proxyServer;
    private static int proxyPort = -1;
    private static AtomicBoolean isProxyRunning = new AtomicBoolean(false);
    private static ExecutorService proxyExecutor;
    
    // 🔑 当前播放的视频信息（静态，所有实例共享）
    private static String currentOriginUrl;
    private static String currentVideoId;  // md5(url) 作为视频唯一标识
    private static File currentVideoDir;   // 当前视频的缓存目录
    private static long currentContentLength = -1;
    private static Context appContext;
    
    // 🔑 分块缓存状态（静态，所有实例共享）
    private static ConcurrentHashMap<Integer, Boolean> cachedChunks = new ConcurrentHashMap<>();
    private static final Object cacheLock = new Object();
    
    // 🔑 播放位置跟踪（静态，所有实例共享）
    private static AtomicLong currentPlaybackPosition = new AtomicLong(0);
    private static AtomicInteger currentPlaybackChunk = new AtomicInteger(0);
    private static AtomicInteger prefetchTargetChunk = new AtomicInteger(0);
    private static AtomicBoolean isPrefetching = new AtomicBoolean(false);
    
    // 🔑 ExoPlayer 是否正在使用代理
    private static boolean exoPlayerUsingProxy = false;
    
    // 🔑 定时清理任务
    private static ScheduledExecutorService cleanupScheduler;
    private static ScheduledFuture<?> cleanupTask;
    private static ScheduledFuture<?> playedChunkCleanupTask;

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
     * 🔑 默认构造函数 - 被 CacheFactory.newInstance() 调用
     */
    public OkHttpProxyCacheManager() {
        if (instance != null) {
            this.httpClient = instance.httpClient;
            Log.d(TAG, "🔑 OkHttpProxyCacheManager: 复用单例 httpClient");
        } else {
            this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
            instance = this;
            Log.d(TAG, "🔑 OkHttpProxyCacheManager: 创建新实例并设为单例");
        }
    }
    
    /**
     * 🔑 私有构造函数 - 用于创建真正的单例
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
     * 🔑 初始化定时清理任务（在 Application 中调用）
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
        
        Log.d(TAG, "🔑 Cleanup task initialized, interval=" + (CLEANUP_INTERVAL_MS / 1000) + "s");
        
        // 启动时立即清理一次
        cleanupScheduler.submit(() -> cleanupStaleCacheDirectories(ctx));
    }

    /**
     * 🔑 清理遗留的缓存目录（非当前视频的目录）
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
                    // 🔑 如果是当前正在播放的视频目录，跳过
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
                            Log.i(TAG, "🔑 Cleanup: deleted " + dir.getName() + " (age=" + (age / 60000) + "min)");
                        }
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.i(TAG, "🔑 Cleanup: deleted " + deletedCount + " dirs, freed " + (deletedSize / 1024 / 1024) + "MB");
            }
        } catch (Exception e) {
            Log.e(TAG, "🔑 Cleanup error: " + e.getMessage());
        }
    }
    
    /**
     * 🔑 删除其他视频的缓存目录（切换视频时调用）
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
                        Log.i(TAG, "📦 ✅ 删除其他视频缓存: " + dir.getName() + " (" + (size / 1024 / 1024) + "MB)");
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.i(TAG, "📦 清理完成: 删除 " + deletedCount + " 个视频目录, 释放 " + (deletedSize / 1024 / 1024) + "MB");
            }
        } catch (Exception e) {
            Log.e(TAG, "📦 清理其他视频缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 🔑 启动已播放 chunk 清理任务
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
        
        Log.d(TAG, "🔑 Played chunk cleanup task started, interval=" + (CLEANUP_PLAYED_INTERVAL_MS / 1000) + "s");
    }
    
    /**
     * 🔑 清理已播放的 chunk（保留当前位置前 KEEP_BEHIND_CHUNKS 个）
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
            Log.d(TAG, "🔑 Cleaned " + deletedCount + " played chunks, freed " + (deletedSize / 1024 / 1024) + "MB, current=" + currentChunk);
        }
    }

    public static void setCurrentHeaders(Map<String, String> headers) {
        synchronized (sHeaderLock) {
            sCurrentHeaders.clear();
            if (headers != null) sCurrentHeaders.putAll(headers);
            Log.d(TAG, "🔑 Headers updated: " + sCurrentHeaders.size() + " headers");
        }
    }
    
    public static Map<String, String> getCurrentHeaders() {
        synchronized (sHeaderLock) {
            return new HashMap<>(sCurrentHeaders);
        }
    }
    
    @Override
    public void doCacheLogic(Context context, IMediaPlayer mediaPlayer, 
                             String originUrl, Map<String, String> header, File cachePath) {
        appContext = context.getApplicationContext();
        setCurrentHeaders(header);
        
        boolean isDirectLink = originUrl.contains("direct_link_quality_index") ||
            (originUrl.startsWith("https://") && !originUrl.contains("192.168.") && !originUrl.contains("localhost"));
        
        String playUrl = originUrl;
        
        if (isDirectLink && originUrl.startsWith("http") && !originUrl.contains(".m3u8")) {
            String newVideoId = md5(originUrl);
            
            Log.i(TAG, "📦 ========== 切换视频 ==========");
            Log.i(TAG, "📦 上一个视频ID: " + (currentVideoId != null ? currentVideoId : "null"));
            Log.i(TAG, "📦 新视频ID: " + newVideoId);
            
            // 🔑 列出缓存目录
            listCacheDirectory(context);
            
            // 🔑 删除其他视频的缓存目录
            cleanupOtherVideoDirectories(context, newVideoId);
            
            // 重置状态
            cachedChunks.clear();
            currentContentLength = -1;
            currentPlaybackPosition.set(0);
            currentPlaybackChunk.set(0);
            prefetchTargetChunk.set(0);
            isPrefetching.set(false);
            
            // 设置新视频信息
            currentOriginUrl = originUrl;
            currentVideoId = newVideoId;
            currentVideoDir = getVideoDirectory(context, newVideoId);
            
            // 确保目录存在
            if (!currentVideoDir.exists()) {
                currentVideoDir.mkdirs();
            }
            
            // 🔑 扫描已存在的 chunk 文件（断点续传支持）
            scanExistingChunks();
            
            Log.i(TAG, "📦 视频缓存目录: " + currentVideoDir.getAbsolutePath());
            
            startProxyServer();
            
            if (proxyPort > 0) {
                playUrl = "http://127.0.0.1:" + proxyPort + "/video";
                mCacheFile = true;
                Log.d(TAG, "🔑 Using proxy URL: " + playUrl);
                
                // 启动预缓存和清理任务
                startInitialPrefetch();
                startPlayedChunkCleanupTask();
                
                listCacheDirectory(context);
            } else {
                Log.e(TAG, "🔑 Proxy failed, using network URL");
                mCacheFile = false;
            }
        } else if (!originUrl.startsWith("http") && !originUrl.startsWith("rtmp")
                && !originUrl.startsWith("rtsp") && !originUrl.contains(".m3u8")) {
            mCacheFile = true;
        }
        
        try {
            Log.d(TAG, "🔑 Setting data source: " + playUrl.substring(0, Math.min(80, playUrl.length())));
            if (playUrl.startsWith("http://127.0.0.1")) {
                mediaPlayer.setDataSource(context, Uri.parse(playUrl), null);
            } else {
                mediaPlayer.setDataSource(context, Uri.parse(playUrl), header);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting data source", e);
        }
    }
    
    /**
     * 🔑 扫描已存在的 chunk 文件
     */
    private void scanExistingChunks() {
        if (currentVideoDir == null || !currentVideoDir.exists()) return;
        
        File[] files = currentVideoDir.listFiles();
        if (files == null) return;
        
        int count = 0;
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("chunk_") && name.endsWith(".cache")) {
                try {
                    int chunkIndex = Integer.parseInt(name.substring(6, name.length() - 6));
                    cachedChunks.put(chunkIndex, true);
                    count++;
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }
        
        if (count > 0) {
            Log.d(TAG, "🔑 Found " + count + " existing chunks");
        }
    }

    /**
     * 🔑 [DEBUG] 列出缓存目录
     */
    private void listCacheDirectory(Context context) {
        try {
            File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
            Log.i(TAG, "📂 缓存根目录: " + cacheDir.getAbsolutePath());
            
            if (!cacheDir.exists()) {
                Log.i(TAG, "📂 缓存目录不存在");
                return;
            }
            
            File[] dirs = cacheDir.listFiles();
            if (dirs == null || dirs.length == 0) {
                Log.i(TAG, "📂 缓存目录为空");
                return;
            }
            
            long totalSize = 0;
            Log.i(TAG, "📂 视频缓存目录列表 (" + dirs.length + " 个):");
            for (File dir : dirs) {
                if (dir.isDirectory()) {
                    long size = getDirectorySize(dir);
                    totalSize += size;
                    int chunkCount = countChunkFiles(dir);
                    String marker = dir.getName().equals(currentVideoId) ? " ← 当前" : "";
                    Log.i(TAG, "📂   - " + dir.getName() + " (" + chunkCount + " chunks, " + (size / 1024 / 1024) + "MB)" + marker);
                }
            }
            Log.i(TAG, "📂 缓存总大小: " + (totalSize / 1024 / 1024) + "MB");
        } catch (Exception e) {
            Log.e(TAG, "📂 列出缓存目录失败: " + e.getMessage());
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
     * 🔑 启动初始预缓存（头部 + 尾部）
     */
    private void startInitialPrefetch() {
        proxyExecutor.submit(() -> {
            try {
                if (currentContentLength <= 0) {
                    currentContentLength = fetchContentLength(currentOriginUrl, getCurrentHeaders());
                    if (currentContentLength <= 0) {
                        Log.e(TAG, "🔑 Prefetch failed: cannot get content length");
                        return;
                    }
                    Log.d(TAG, "🔑 Content length: " + (currentContentLength / 1024 / 1024) + "MB");
                }
                
                int totalChunks = (int) Math.ceil((double) currentContentLength / CHUNK_SIZE);
                Log.d(TAG, "🔑 Total chunks: " + totalChunks);
                
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
                
                Log.d(TAG, "🔑 Initial prefetch done: head=" + PREFETCH_HEAD_CHUNKS + 
                      ", tail=" + PREFETCH_TAIL_CHUNKS + ", target=" + prefetchTargetChunk.get());
                
            } catch (Exception e) {
                Log.e(TAG, "🔑 Initial prefetch error: " + e.getMessage());
            }
        });
    }
    
    /**
     * 🔑 检查是否有足够空间缓存一个块
     */
    private boolean checkDiskSpaceForChunk() {
        try {
            if (appContext == null) return true;
            
            File cacheDir = appContext.getCacheDir();
            long freeSpace = cacheDir.getFreeSpace();
            long required = CHUNK_SIZE + MIN_FREE_SPACE_MB * 1024 * 1024;
            
            if (freeSpace < required) {
                Log.w(TAG, "🔑 磁盘空间不足，停止预缓存");
                return false;
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 🔑 智能预缓存：根据播放位置提前缓存
     */
    private void triggerSmartPrefetch(int playbackChunk) {
        if (isPrefetching.get()) return;
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
                Log.d(TAG, "🔑 Smart prefetch triggered: playback=" + playbackChunk + 
                      ", cachedAhead=" + cachedAhead + ", target=" + newTarget);
                startSmartPrefetch(playbackChunk, newTarget);
            }
        }
    }
    
    /**
     * 🔑 执行智能预缓存
     */
    private void startSmartPrefetch(int startChunk, int endChunk) {
        if (isPrefetching.compareAndSet(false, true)) {
            proxyExecutor.submit(() -> {
                try {
                    int downloaded = 0;
                    
                    for (int i = startChunk; i < endChunk && isProxyRunning.get(); i++) {
                        if (!cachedChunks.containsKey(i)) {
                            if (!checkDiskSpaceForChunk()) {
                                Log.w(TAG, "🔑 磁盘空间不足，停止智能预缓存");
                                break;
                            }
                            downloadAndCacheChunk(i);
                            downloaded++;
                        }
                    }
                    Log.d(TAG, "🔑 Smart prefetch done: " + downloaded + " chunks (" + 
                          startChunk + "-" + endChunk + ")");
                } catch (Exception e) {
                    Log.e(TAG, "🔑 Smart prefetch error: " + e.getMessage());
                } finally {
                    isPrefetching.set(false);
                }
            });
        }
    }
    
    /**
     * 🔑 下载并缓存单个块（写入独立文件）
     */
    private void downloadAndCacheChunk(int chunkIndex) {
        if (cachedChunks.containsKey(chunkIndex)) return;
        
        long start = (long) chunkIndex * CHUNK_SIZE;
        long end = Math.min(start + CHUNK_SIZE - 1, currentContentLength - 1);
        
        byte[] data = downloadChunk(start, end);
        if (data != null && data.length > 0) {
            writeChunkToFile(chunkIndex, data);
            Log.d(TAG, "🔑 Prefetch chunk " + chunkIndex + " (" + (data.length/1024) + "KB)");
        }
    }
    
    /**
     * 🔑 写入 chunk 到独立文件
     */
    private void writeChunkToFile(int chunkIndex, byte[] data) {
        if (currentVideoDir == null) return;
        
        File chunkFile = new File(currentVideoDir, "chunk_" + chunkIndex + ".cache");
        synchronized (cacheLock) {
            try (RandomAccessFile raf = new RandomAccessFile(chunkFile, "rw")) {
                raf.write(data);
                cachedChunks.put(chunkIndex, true);
            } catch (Exception e) {
                Log.e(TAG, "🔑 Write chunk error: " + e.getMessage());
            }
        }
    }
    
    /**
     * 🔑 从文件读取 chunk 数据
     */
    private byte[] readChunkFromFile(int chunkIndex, long requestStart, long requestEnd) {
        if (currentVideoDir == null) return null;
        
        File chunkFile = new File(currentVideoDir, "chunk_" + chunkIndex + ".cache");
        if (!chunkFile.exists()) {
            cachedChunks.remove(chunkIndex);
            return null;
        }
        
        long chunkStart = (long) chunkIndex * CHUNK_SIZE;
        int offsetInChunk = (int) (requestStart - chunkStart);
        int length = (int) (requestEnd - requestStart + 1);
        
        synchronized (cacheLock) {
            try (RandomAccessFile raf = new RandomAccessFile(chunkFile, "r")) {
                byte[] data = new byte[length];
                raf.seek(offsetInChunk);
                raf.readFully(data);
                return data;
            } catch (Exception e) {
                Log.e(TAG, "🔑 Read chunk error: " + e.getMessage());
                cachedChunks.remove(chunkIndex);
                return null;
            }
        }
    }

    private void startProxyServer() {
        if (isProxyRunning.get()) {
            Log.d(TAG, "🔑 Proxy already running on port " + proxyPort);
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
                Log.e(TAG, "🔑 Failed to find available port");
                return;
            }
            
            isProxyRunning.set(true);
            proxyExecutor = Executors.newCachedThreadPool();
            
            proxyExecutor.submit(() -> {
                Log.d(TAG, "🔑 Proxy server started on port " + proxyPort);
                while (isProxyRunning.get()) {
                    try {
                        Socket client = proxyServer.accept();
                        proxyExecutor.submit(() -> handleClient(client));
                    } catch (IOException e) {
                        if (isProxyRunning.get()) {
                            Log.e(TAG, "🔑 Proxy accept error: " + e.getMessage());
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "🔑 Failed to start proxy server", e);
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
            Log.d(TAG, "🔑 Proxy request: " + request.split("\r\n")[0]);
            
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
            
            // 🔑 更新播放位置
            int playbackChunk = (int) (rangeStart / CHUNK_SIZE);
            currentPlaybackChunk.set(playbackChunk);
            
            // 🔑 触发智能预缓存
            triggerSmartPrefetch(playbackChunk);
            
            Log.d(TAG, "🔑 Range: " + rangeStart + "-" + (rangeEnd > 0 ? rangeEnd : "") + 
                  " (chunk " + playbackChunk + ")");
            
            if (currentContentLength <= 0) {
                currentContentLength = fetchContentLength(currentOriginUrl, getCurrentHeaders());
                Log.d(TAG, "🔑 Content length: " + (currentContentLength / 1024 / 1024) + "MB");
            }
            
            if (currentContentLength <= 0) {
                output.write("HTTP/1.1 500 Internal Server Error\r\n\r\n".getBytes());
                output.flush();
                client.close();
                return;
            }
            
            if (rangeEnd < 0 || rangeEnd >= currentContentLength) {
                rangeEnd = currentContentLength - 1;
            }
            long contentLength = rangeEnd - rangeStart + 1;
            
            String responseHeader = "HTTP/1.1 206 Partial Content\r\n" +
                "Content-Type: video/mp4\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Content-Range: bytes " + rangeStart + "-" + rangeEnd + "/" + currentContentLength + "\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Connection: close\r\n\r\n";
            output.write(responseHeader.getBytes());
            output.flush();
            
            sendData(output, rangeStart, rangeEnd);
            
            output.flush();
            client.close();
            
        } catch (Exception e) {
            Log.e(TAG, "🔑 Handle client error: " + e.getMessage());
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private void sendData(OutputStream output, long start, long end) throws IOException {
        long position = start;
        
        while (position <= end && isProxyRunning.get()) {
            int chunkIndex = (int) (position / CHUNK_SIZE);
            long chunkStart = (long) chunkIndex * CHUNK_SIZE;
            long chunkEnd = Math.min(chunkStart + CHUNK_SIZE - 1, currentContentLength - 1);
            
            long sendStart = position;
            long sendEnd = Math.min(end, chunkEnd);
            
            // 🔑 先检查缓存文件
            if (cachedChunks.containsKey(chunkIndex)) {
                byte[] data = readChunkFromFile(chunkIndex, sendStart, sendEnd);
                if (data != null && data.length > 0) {
                    output.write(data);
                    Log.d(TAG, "🔑 From cache: chunk " + chunkIndex);
                    position = sendEnd + 1;
                    continue;
                }
            }
            
            // 🔑 从网络下载整个 chunk
            byte[] chunkData = downloadChunk(chunkStart, chunkEnd);
            if (chunkData == null || chunkData.length == 0) {
                Log.e(TAG, "🔑 Download failed at chunk " + chunkIndex);
                break;
            }
            
            // 🔑 写入缓存文件
            if (checkDiskSpaceForChunk()) {
                writeChunkToFile(chunkIndex, chunkData);
                Log.d(TAG, "🔑 From network -> cache: chunk " + chunkIndex);
            } else {
                Log.d(TAG, "🔑 From network (no space): chunk " + chunkIndex);
            }
            
            // 发送请求的部分
            int offsetInChunk = (int) (sendStart - chunkStart);
            int lengthToSend = (int) (sendEnd - sendStart + 1);
            output.write(chunkData, offsetInChunk, lengthToSend);
            
            position = sendEnd + 1;
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
            } catch (Exception e) {}
            
            Response response = httpClient.newCall(builder.build()).execute();
            
            if (response.isSuccessful() || response.code() == 206) {
                byte[] data = response.body().bytes();
                response.close();
                return data;
            }
            
            Log.e(TAG, "🔑 Download failed: " + response.code());
            response.close();
        } catch (Exception e) {
            Log.e(TAG, "🔑 Download error: " + e.getMessage());
        }
        return null;
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
                }
                response.close();
            } catch (Exception e) {
                Log.e(TAG, "🔑 Fetch content length error: " + e.getMessage());
                try { Thread.sleep(500); } catch (InterruptedException ie) { break; }
            }
        }
        return -1;
    }

    /**
     * 🔑 获取视频缓存目录
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
        Log.d(TAG, "🔑 Proxy server stopped");
    }
    
    @Override
    public void clearCache(Context context, File cachePath, String url) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (TextUtils.isEmpty(url)) {
            if (cacheDir.exists()) deleteDirectory(cacheDir);
        } else {
            String videoId = md5(url);
            File videoDir = new File(cacheDir, videoId);
            if (videoDir.exists()) deleteDirectory(videoDir);
        }
    }
    
    @Override
    public void release() {
        Log.i(TAG, "📦 ========== release() 被调用 ==========");
        Log.i(TAG, "📦 exoPlayerUsingProxy=" + exoPlayerUsingProxy);
        Log.i(TAG, "📦 当前视频ID: " + (currentVideoId != null ? currentVideoId : "null"));
        
        if (exoPlayerUsingProxy) {
            Log.i(TAG, "📦 ExoPlayer 正在使用代理，跳过释放");
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
                Log.i(TAG, "📦 ✅ release() 删除缓存目录: " + dirName + " (" + (size / 1024 / 1024) + "MB)");
            } else {
                Log.e(TAG, "📦 ❌ release() 删除缓存目录失败: " + dirName);
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
        
        Log.i(TAG, "📦 ========== release() 完成 ==========");
    }
    
    /**
     * 🔑 强制释放
     */
    public void forceRelease() {
        Log.d(TAG, "🔑 forceRelease() called");
        exoPlayerUsingProxy = false;
        release();
    }
    
    /**
     * 🔑 设置 ExoPlayer 是否正在使用代理
     */
    public static void setExoPlayerUsingProxy(boolean using) {
        exoPlayerUsingProxy = using;
        Log.d(TAG, "🔑 setExoPlayerUsingProxy: " + using);
    }
    
    @Override
    public boolean cachePreview(Context context, File cacheDir, String url) { return false; }
    
    @Override
    public boolean hadCached() { return mCacheFile; }
    
    @Override
    public void setCacheAvailableListener(ICacheAvailableListener listener) {
        this.cacheAvailableListener = listener;
    }
    
    public int getDownloadProgress() {
        if (currentContentLength <= 0) return 0;
        int totalChunks = (int) Math.ceil((double) currentContentLength / CHUNK_SIZE);
        return cachedChunks.size() * 100 / totalChunks;
    }
    
    public int getCachedChunksCount() { return cachedChunks.size(); }
    
    public int getCurrentPlaybackChunk() { return currentPlaybackChunk.get(); }

    /**
     * 🔑 获取代理 URL（供 ExoPlayer 使用）
     */
    public String getProxyUrl(Context context, String originUrl, Map<String, String> headers, File cachePath) {
        appContext = context.getApplicationContext();
        setCurrentHeaders(headers);
        
        boolean isDirectLink = originUrl.contains("direct_link_quality_index") ||
            (originUrl.startsWith("https://") && !originUrl.contains("192.168.") && !originUrl.contains("localhost"));
        
        if (isDirectLink && originUrl.startsWith("http") && !originUrl.contains(".m3u8")) {
            String newVideoId = md5(originUrl);
            
            Log.i(TAG, "📦 ========== ExoPlayer 切换视频 ==========");
            Log.i(TAG, "📦 上一个视频ID: " + (currentVideoId != null ? currentVideoId : "null"));
            Log.i(TAG, "📦 新视频ID: " + newVideoId);
            
            listCacheDirectory(context);
            
            // 🔑 删除其他视频的缓存目录
            cleanupOtherVideoDirectories(context, newVideoId);
            
            // 重置状态
            cachedChunks.clear();
            currentContentLength = -1;
            currentPlaybackPosition.set(0);
            currentPlaybackChunk.set(0);
            prefetchTargetChunk.set(0);
            isPrefetching.set(false);
            
            // 设置新视频信息
            currentOriginUrl = originUrl;
            currentVideoId = newVideoId;
            currentVideoDir = getVideoDirectory(context, newVideoId);
            
            if (!currentVideoDir.exists()) {
                currentVideoDir.mkdirs();
            }
            
            scanExistingChunks();
            
            Log.i(TAG, "📦 ExoPlayer 视频缓存目录: " + currentVideoDir.getAbsolutePath());
            
            startProxyServer();
            
            if (proxyPort > 0) {
                String proxyUrl = "http://127.0.0.1:" + proxyPort + "/video";
                mCacheFile = true;
                Log.d(TAG, "🔑 ExoPlayer proxy URL: " + proxyUrl);
                
                startInitialPrefetch();
                startPlayedChunkCleanupTask();
                
                listCacheDirectory(context);
                
                return proxyUrl;
            } else {
                Log.e(TAG, "🔑 Proxy failed for ExoPlayer, using original URL");
                mCacheFile = false;
                return originUrl;
            }
        }
        
        return originUrl;
    }
}
