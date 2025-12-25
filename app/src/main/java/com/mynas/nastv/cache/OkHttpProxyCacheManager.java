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
 * 🔧 基于 OkHttp 的缓存管理器 - 支持边下边播 + 智能预缓存
 * 
 * 实现原理：
 * 1. 启动本地 HTTP 代理服务器
 * 2. IJKPlayer 请求本地代理服务器
 * 3. 跟踪播放器请求位置，提前缓存后面 PREFETCH_AHEAD_MB 的数据
 * 4. 预缓存头部和尾部数据块（解决 moov atom 在文件末尾的问题）
 * 5. 缓存使用超过 5 分钟后自动删除
 * 6. 定时清理异常退出遗留的缓存文件
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
    
    // 🔑 缓存过期配置
    private static final long CACHE_EXPIRE_TIME_MS = 5 * 60 * 1000; // 5 分钟后删除
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000; // 每分钟检查一次
    private static final long STALE_FILE_AGE_MS = 30 * 60 * 1000; // 30 分钟未修改的文件视为遗留文件
    
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
    
    // 🔑 当前播放的 URL 和缓存文件（静态，所有实例共享）
    private static String currentOriginUrl;
    private static File currentCacheFile;
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
    
    // 🔑 缓存开始使用时间
    private static long cacheStartTime = 0;
    
    // 🔑 ExoPlayer 是否正在使用代理（防止 release 时停止代理）
    private static boolean exoPlayerUsingProxy = false;
    
    // 🔑 定时清理任务
    private static ScheduledExecutorService cleanupScheduler;
    private static ScheduledFuture<?> cleanupTask;
    private static ScheduledFuture<?> expireTask;
    
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
     * 返回单例实例的引用，确保 GSYVideoPlayer 和我们的代码使用同一个实例
     */
    public OkHttpProxyCacheManager() {
        // 🔑 关键：确保使用单例
        if (instance != null) {
            // 复用单例的 httpClient
            this.httpClient = instance.httpClient;
            // 注意：其他字段会在 doCacheLogic 中被重新初始化
            Log.d(TAG, "🔑 OkHttpProxyCacheManager: 复用单例 httpClient");
        } else {
            // 第一次创建
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
        
        final Context appContext = context.getApplicationContext();
        cleanupTask = cleanupScheduler.scheduleAtFixedRate(() -> {
            cleanupStaleCacheFiles(appContext);
        }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        Log.d(TAG, "🔑 Cleanup task initialized, interval=" + (CLEANUP_INTERVAL_MS / 1000) + "s");
        
        // 启动时立即清理一次
        cleanupScheduler.submit(() -> cleanupStaleCacheFiles(appContext));
    }
    
    /**
     * 🔑 清理遗留的缓存文件
     */
    private static void cleanupStaleCacheFiles(Context context) {
        try {
            File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
            if (!cacheDir.exists()) return;
            
            File[] files = cacheDir.listFiles();
            if (files == null || files.length == 0) return;
            
            long now = System.currentTimeMillis();
            int deletedCount = 0;
            long deletedSize = 0;
            
            for (File file : files) {
                if (file.isFile()) {
                    long age = now - file.lastModified();
                    if (age > STALE_FILE_AGE_MS) {
                        long size = file.length();
                        if (file.delete()) {
                            deletedCount++;
                            deletedSize += size;
                            Log.d(TAG, "🔑 Cleanup: deleted " + file.getName() + " (age=" + (age / 60000) + "min)");
                        }
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.d(TAG, "🔑 Cleanup: deleted " + deletedCount + " files, freed " + (deletedSize / 1024 / 1024) + "MB");
            }
        } catch (Exception e) {
            Log.e(TAG, "🔑 Cleanup error: " + e.getMessage());
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
            // 重置状态
            cachedChunks.clear();
            currentContentLength = -1;
            cacheStartTime = System.currentTimeMillis();
            currentPlaybackPosition.set(0);
            currentPlaybackChunk.set(0);
            prefetchTargetChunk.set(0);
            isPrefetching.set(false);
            
            if (expireTask != null) {
                expireTask.cancel(false);
                expireTask = null;
            }
            
            currentOriginUrl = originUrl;
            currentCacheFile = getCacheFile(context, originUrl);
            
            startProxyServer();
            
            if (proxyPort > 0) {
                playUrl = "http://127.0.0.1:" + proxyPort + "/video";
                mCacheFile = true;
                Log.d(TAG, "🔑 Using proxy URL: " + playUrl);
                Log.d(TAG, "🔑 Cache file: " + currentCacheFile.getAbsolutePath());
                
                // 启动预缓存（头部 + 尾部）
                startInitialPrefetch();
                scheduleExpireTask();
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
    
    private void scheduleExpireTask() {
        if (cleanupScheduler == null) {
            cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        }
        
        final File cacheFile = currentCacheFile;
        expireTask = cleanupScheduler.schedule(() -> {
            if (cacheFile != null && cacheFile.exists()) {
                long size = cacheFile.length();
                if (cacheFile.delete()) {
                    Log.d(TAG, "🔑 Cache expired: " + (size / 1024 / 1024) + "MB");
                }
            }
        }, CACHE_EXPIRE_TIME_MS, TimeUnit.MILLISECONDS);
        
        Log.d(TAG, "🔑 Cache will expire in " + (CACHE_EXPIRE_TIME_MS / 60000) + " minutes");
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
                    
                    try (RandomAccessFile raf = new RandomAccessFile(currentCacheFile, "rw")) {
                        raf.setLength(currentContentLength);
                    }
                }
                
                int totalChunks = (int) Math.ceil((double) currentContentLength / CHUNK_SIZE);
                Log.d(TAG, "🔑 Total chunks: " + totalChunks);
                
                // 预缓存头部
                for (int i = 0; i < Math.min(PREFETCH_HEAD_CHUNKS, totalChunks); i++) {
                    if (!isProxyRunning.get()) break;
                    downloadAndCacheChunk(i);
                }
                
                // 预缓存尾部
                for (int i = 0; i < Math.min(PREFETCH_TAIL_CHUNKS, totalChunks); i++) {
                    if (!isProxyRunning.get()) break;
                    int chunkIndex = totalChunks - 1 - i;
                    if (chunkIndex >= PREFETCH_HEAD_CHUNKS) {
                        downloadAndCacheChunk(chunkIndex);
                    }
                }
                
                // 设置初始预缓存目标
                prefetchTargetChunk.set(PREFETCH_HEAD_CHUNKS + PREFETCH_AHEAD_CHUNKS);
                
                Log.d(TAG, "🔑 Initial prefetch done: head=" + PREFETCH_HEAD_CHUNKS + 
                      ", tail=" + PREFETCH_TAIL_CHUNKS + ", target=" + prefetchTargetChunk.get());
                
            } catch (Exception e) {
                Log.e(TAG, "🔑 Initial prefetch error: " + e.getMessage());
            }
        });
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
            Log.d(TAG, "🔑 Smart prefetch triggered: playback=" + playbackChunk + 
                  ", cachedAhead=" + cachedAhead + ", target=" + newTarget);
            startSmartPrefetch(playbackChunk, newTarget);
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
     * 下载并缓存单个块
     */
    private void downloadAndCacheChunk(int chunkIndex) {
        if (cachedChunks.containsKey(chunkIndex)) return;
        
        long start = (long) chunkIndex * CHUNK_SIZE;
        long end = Math.min(start + CHUNK_SIZE - 1, currentContentLength - 1);
        
        byte[] data = downloadChunk(start, end);
        if (data != null && data.length > 0) {
            writeToCache(chunkIndex, start, data);
            Log.d(TAG, "🔑 Prefetch chunk " + chunkIndex + " (" + (data.length/1024) + "KB)");
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
            currentPlaybackPosition.set(rangeStart);
            int playbackChunk = (int) (rangeStart / CHUNK_SIZE);
            currentPlaybackChunk.set(playbackChunk);
            
            // 🔑 触发智能预缓存
            triggerSmartPrefetch(playbackChunk);
            
            Log.d(TAG, "🔑 Range: " + rangeStart + "-" + (rangeEnd > 0 ? rangeEnd : "") + 
                  " (chunk " + playbackChunk + ")");
            
            if (currentContentLength <= 0) {
                currentContentLength = fetchContentLength(currentOriginUrl, getCurrentHeaders());
                Log.d(TAG, "🔑 Content length: " + (currentContentLength / 1024 / 1024) + "MB");
                
                if (currentContentLength > 0) {
                    try (RandomAccessFile raf = new RandomAccessFile(currentCacheFile, "rw")) {
                        raf.setLength(currentContentLength);
                    }
                }
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
            
            if (cachedChunks.containsKey(chunkIndex)) {
                byte[] data = readFromCache(chunkIndex, sendStart, sendEnd);
                if (data != null && data.length > 0) {
                    output.write(data);
                    Log.d(TAG, "🔑 From cache: chunk " + chunkIndex);
                    position = sendEnd + 1;
                    continue;
                }
            }
            
            byte[] chunkData = downloadChunk(chunkStart, chunkEnd);
            if (chunkData == null || chunkData.length == 0) {
                Log.e(TAG, "🔑 Download failed at chunk " + chunkIndex);
                break;
            }
            
            writeToCache(chunkIndex, chunkStart, chunkData);
            
            int offsetInChunk = (int) (sendStart - chunkStart);
            int lengthToSend = (int) (sendEnd - sendStart + 1);
            output.write(chunkData, offsetInChunk, lengthToSend);
            
            Log.d(TAG, "🔑 From network: chunk " + chunkIndex);
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
    
    private void writeToCache(int chunkIndex, long position, byte[] data) {
        synchronized (cacheLock) {
            try (RandomAccessFile raf = new RandomAccessFile(currentCacheFile, "rw")) {
                raf.seek(position);
                raf.write(data);
                cachedChunks.put(chunkIndex, true);
            } catch (Exception e) {
                Log.e(TAG, "🔑 Write cache error: " + e.getMessage());
            }
        }
    }
    
    private byte[] readFromCache(int chunkIndex, long start, long end) {
        synchronized (cacheLock) {
            try (RandomAccessFile raf = new RandomAccessFile(currentCacheFile, "r")) {
                int length = (int) (end - start + 1);
                byte[] data = new byte[length];
                raf.seek(start);
                raf.readFully(data);
                return data;
            } catch (Exception e) {
                Log.e(TAG, "🔑 Read cache error: " + e.getMessage());
                return null;
            }
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
                }
                response.close();
            } catch (Exception e) {
                Log.e(TAG, "🔑 Fetch content length error: " + e.getMessage());
                try { Thread.sleep(500); } catch (InterruptedException ie) { break; }
            }
        }
        return -1;
    }
    
    private File getCacheFile(Context context, String url) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists()) cacheDir.mkdirs();
        return new File(cacheDir, md5(url) + ".cache");
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
            File cacheFile = getCacheFile(context, url);
            if (cacheFile.exists()) cacheFile.delete();
        }
    }
    
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) for (File file : files) deleteDirectory(file);
        }
        dir.delete();
    }
    
    @Override
    public void release() {
        Log.d(TAG, "🔑 release() called, exoPlayerUsingProxy=" + exoPlayerUsingProxy);
        
        // 🔑 如果 ExoPlayer 正在使用代理，不要停止代理服务器
        if (exoPlayerUsingProxy) {
            Log.d(TAG, "🔑 ExoPlayer 正在使用代理，跳过释放");
            return;
        }
        
        stopProxyServer();
        
        if (expireTask != null) {
            expireTask.cancel(false);
            expireTask = null;
        }
        
        if (currentCacheFile != null && currentCacheFile.exists()) {
            long size = currentCacheFile.length();
            if (currentCacheFile.delete()) {
                Log.d(TAG, "🔑 Cache deleted: " + (size / 1024 / 1024) + "MB");
            }
        }
        
        currentOriginUrl = null;
        currentCacheFile = null;
        currentContentLength = -1;
        cachedChunks.clear();
        cacheStartTime = 0;
    }
    
    /**
     * 🔑 强制释放（忽略 exoPlayerUsingProxy 标志）
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
     * 与 doCacheLogic 类似，但不设置 MediaPlayer 数据源，只返回代理 URL
     * @param context 上下文
     * @param originUrl 原始视频 URL
     * @param headers 请求头
     * @param cachePath 缓存目录
     * @return 代理 URL，如果不支持缓存则返回原始 URL
     */
    public String getProxyUrl(Context context, String originUrl, Map<String, String> headers, File cachePath) {
        appContext = context.getApplicationContext();
        setCurrentHeaders(headers);
        
        boolean isDirectLink = originUrl.contains("direct_link_quality_index") ||
            (originUrl.startsWith("https://") && !originUrl.contains("192.168.") && !originUrl.contains("localhost"));
        
        if (isDirectLink && originUrl.startsWith("http") && !originUrl.contains(".m3u8")) {
            // 重置状态
            cachedChunks.clear();
            currentContentLength = -1;
            cacheStartTime = System.currentTimeMillis();
            currentPlaybackPosition.set(0);
            currentPlaybackChunk.set(0);
            prefetchTargetChunk.set(0);
            isPrefetching.set(false);
            
            if (expireTask != null) {
                expireTask.cancel(false);
                expireTask = null;
            }
            
            currentOriginUrl = originUrl;
            currentCacheFile = getCacheFile(context, originUrl);
            
            startProxyServer();
            
            if (proxyPort > 0) {
                String proxyUrl = "http://127.0.0.1:" + proxyPort + "/video";
                mCacheFile = true;
                Log.d(TAG, "🔑 ExoPlayer proxy URL: " + proxyUrl);
                Log.d(TAG, "🔑 Cache file: " + currentCacheFile.getAbsolutePath());
                
                // 启动预缓存（头部 + 尾部）
                startInitialPrefetch();
                scheduleExpireTask();
                
                return proxyUrl;
            } else {
                Log.e(TAG, "🔑 Proxy failed for ExoPlayer, using original URL");
                mCacheFile = false;
                return originUrl;
            }
        }
        
        // 不支持缓存的情况，返回原始 URL
        return originUrl;
    }
}
