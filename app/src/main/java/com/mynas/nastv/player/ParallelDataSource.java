package com.mynas.nastv.player;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 🚀 并行分块下载数据源
 * 
 * 使用多个连接并行下载不同的 Range 块，加速视频缓冲
 * 类似于下载加速器的原理
 */
public class ParallelDataSource extends BaseDataSource {
    
    private static final String TAG = "ParallelDataSource";
    
    // 配置参数 - 平衡内存和流畅度
    private static final int CHUNK_SIZE = 512 * 1024;       // 每块512KB（平衡内存和流畅度）
    
    // 动态参数（根据内存调整）
    private int numConnections = 2;                         // 并行连接数
    private int bufferChunks = 3;                           // 缓冲区块数
    private int prefetchChunks = 2;                         // 预取块数
    
    private final OkHttpClient httpClient;
    private final Map<String, String> defaultHeaders;
    
    private Uri uri;
    private long contentLength = C.LENGTH_UNSET;
    private long currentPosition = 0;
    private long bytesRemaining = C.LENGTH_UNSET;
    
    // 多线程下载
    private ExecutorService downloadExecutor;
    private BlockingQueue<ChunkData> chunkBuffer;  // 动态创建
    private final ConcurrentHashMap<Long, ChunkData> pendingChunks;  // 存储乱序到达的块
    private final List<Future<?>> downloadTasks;
    private final AtomicBoolean isOpened;
    private final AtomicLong nextChunkToDownload;
    private final AtomicLong nextChunkToRead;
    
    // 当前正在读取的块
    private ChunkData currentChunk;
    private int currentChunkOffset;
    
    public ParallelDataSource(OkHttpClient httpClient, Map<String, String> defaultHeaders) {
        super(/* isNetwork= */ true);
        this.httpClient = httpClient;
        this.defaultHeaders = defaultHeaders;
        this.pendingChunks = new ConcurrentHashMap<>();
        this.downloadTasks = new ArrayList<>();
        this.isOpened = new AtomicBoolean(false);
        this.nextChunkToDownload = new AtomicLong(0);
        this.nextChunkToRead = new AtomicLong(0);
    }
    
    @Override
    public long open(@NonNull DataSpec dataSpec) throws HttpDataSource.HttpDataSourceException {
        uri = dataSpec.uri;
        currentPosition = dataSpec.position;
        
        Log.d(TAG, "🚀 Opening parallel data source: " + uri);
        Log.d(TAG, "🚀 Start position: " + currentPosition);
        
        try {
            // 🔑 根据可用内存动态调整并发参数
            Runtime runtime = Runtime.getRuntime();
            long freeMemory = runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
            long usedMemory = runtime.totalMemory() - freeMemory;
            long availableMemory = maxMemory - usedMemory;
            long availableMB = availableMemory / 1024 / 1024;
            
            Log.d(TAG, "🚀 Memory: available=" + availableMB + "MB, max=" + (maxMemory / 1024 / 1024) + "MB");
            
            // 根据内存动态调整参数 - 保守策略避免OOM
            if (availableMB >= 80) {
                // 内存充足：2连接，4块缓冲（最多2MB）
                numConnections = 2;
                bufferChunks = 4;
                prefetchChunks = 3;
            } else if (availableMB >= 40) {
                // 内存中等：2连接，3块缓冲（最多1.5MB）
                numConnections = 2;
                bufferChunks = 3;
                prefetchChunks = 2;
            } else {
                // 内存紧张：1连接，2块缓冲（最多1MB）
                numConnections = 1;
                bufferChunks = 2;
                prefetchChunks = 1;
            }
            
            Log.d(TAG, "🚀 Dynamic config: connections=" + numConnections + ", bufferChunks=" + bufferChunks);
            
            // 创建缓冲区
            chunkBuffer = new ArrayBlockingQueue<>(bufferChunks);
            
            // 首先获取文件总大小
            contentLength = getContentLength();
            Log.d(TAG, "🚀 Content length: " + contentLength);
            
            if (contentLength == C.LENGTH_UNSET) {
                // 无法获取大小，回退到单连接
                Log.w(TAG, "🚀 Cannot get content length, falling back to single connection");
                return openSingleConnection(dataSpec);
            }
            
            // 计算剩余字节
            if (dataSpec.length != C.LENGTH_UNSET) {
                bytesRemaining = dataSpec.length;
            } else {
                bytesRemaining = contentLength - currentPosition;
            }
            
            // 初始化多线程下载
            isOpened.set(true);
            nextChunkToDownload.set(currentPosition / CHUNK_SIZE);
            nextChunkToRead.set(currentPosition / CHUNK_SIZE);
            
            // 创建下载线程池
            downloadExecutor = Executors.newFixedThreadPool(numConnections);
            
            // 启动预取
            startPrefetch();
            
            transferStarted(dataSpec);
            return bytesRemaining;
            
        } catch (Exception e) {
            Log.e(TAG, "🚀 Open failed", e);
            throw new HttpDataSource.HttpDataSourceException(
                e instanceof IOException ? (IOException) e : new IOException(e), 
                dataSpec, 
                HttpDataSource.HttpDataSourceException.TYPE_OPEN);
        }
    }
    
    private long getContentLength() throws IOException {
        // 🔑 服务器不支持 HEAD 请求，使用 GET + Range: bytes=0-0 获取文件大小
        Request.Builder requestBuilder = new Request.Builder()
            .url(uri.toString())
            .get()
            .header("Range", "bytes=0-0");  // 只请求第一个字节
        
        addHeaders(requestBuilder);
        
        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            Log.d(TAG, "🚀 getContentLength response code: " + response.code());
            
            if (response.isSuccessful() || response.code() == 206) {
                // 从 Content-Range 获取总大小: "bytes 0-0/1009689143"
                String contentRange = response.header("Content-Range");
                Log.d(TAG, "🚀 Content-Range: " + contentRange);
                
                if (contentRange != null && contentRange.contains("/")) {
                    String[] parts = contentRange.split("/");
                    if (parts.length == 2 && !parts[1].equals("*")) {
                        long size = Long.parseLong(parts[1]);
                        Log.d(TAG, "🚀 File size from Content-Range: " + size);
                        return size;
                    }
                }
                
                // 备用：从 Content-Length 获取
                String contentLengthHeader = response.header("Content-Length");
                if (contentLengthHeader != null) {
                    Log.d(TAG, "🚀 Content-Length: " + contentLengthHeader);
                    return Long.parseLong(contentLengthHeader);
                }
            }
        }
        return C.LENGTH_UNSET;
    }
    
    private long openSingleConnection(DataSpec dataSpec) throws IOException {
        // 回退到单连接模式
        Request.Builder requestBuilder = new Request.Builder()
            .url(uri.toString())
            .get();
        
        addHeaders(requestBuilder);
        
        if (dataSpec.position > 0) {
            requestBuilder.header("Range", "bytes=" + dataSpec.position + "-");
        }
        
        Response response = httpClient.newCall(requestBuilder.build()).execute();
        if (!response.isSuccessful()) {
            throw new IOException("HTTP error: " + response.code());
        }
        
        // 存储响应流
        currentChunk = new ChunkData(0, response.body().bytes());
        currentChunkOffset = 0;
        bytesRemaining = currentChunk.data.length;
        
        return bytesRemaining;
    }
    
    private void startPrefetch() {
        for (int i = 0; i < prefetchChunks && i < numConnections; i++) {
            scheduleNextChunkDownload();
        }
    }
    
    private void scheduleNextChunkDownload() {
        if (!isOpened.get()) return;
        
        long chunkIndex = nextChunkToDownload.getAndIncrement();
        long startByte = chunkIndex * CHUNK_SIZE;
        
        if (startByte >= contentLength) {
            return; // 已经到文件末尾
        }
        
        long endByte = Math.min(startByte + CHUNK_SIZE - 1, contentLength - 1);
        
        Future<?> task = downloadExecutor.submit(() -> downloadChunk(chunkIndex, startByte, endByte));
        synchronized (downloadTasks) {
            downloadTasks.add(task);
        }
    }
    
    private void downloadChunk(long chunkIndex, long startByte, long endByte) {
        if (!isOpened.get()) return;
        
        Log.d(TAG, "🚀 Downloading chunk " + chunkIndex + ": bytes=" + startByte + "-" + endByte);
        
        try {
            Request.Builder requestBuilder = new Request.Builder()
                .url(uri.toString())
                .get()
                .header("Range", "bytes=" + startByte + "-" + endByte);
            
            addHeaders(requestBuilder);
            
            Response response = httpClient.newCall(requestBuilder.build()).execute();
            
            if (response.isSuccessful() || response.code() == 206) {
                byte[] data = response.body().bytes();
                ChunkData chunk = new ChunkData(chunkIndex, data);
                
                // 等待放入缓冲区（阻塞直到有空间）
                while (isOpened.get()) {
                    try {
                        if (chunkBuffer.offer(chunk, 100, TimeUnit.MILLISECONDS)) {
                            Log.d(TAG, "🚀 Chunk " + chunkIndex + " buffered, size=" + data.length);
                            
                            // 调度下一个块下载
                            scheduleNextChunkDownload();
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } else {
                Log.e(TAG, "🚀 Chunk " + chunkIndex + " download failed: " + response.code());
            }
            
            response.close();
            
        } catch (Exception e) {
            if (isOpened.get()) {
                Log.e(TAG, "🚀 Chunk " + chunkIndex + " download error", e);
            }
        }
    }
    
    private void addHeaders(Request.Builder builder) {
        if (defaultHeaders != null) {
            for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
    }
    
    @Override
    public int read(@NonNull byte[] buffer, int offset, int length) throws HttpDataSource.HttpDataSourceException {
        if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }
        
        try {
            int bytesRead = 0;
            
            while (bytesRead < length && bytesRemaining > 0) {
                // 确保有当前块可读
                if (currentChunk == null || currentChunkOffset >= currentChunk.data.length) {
                    // 需要获取下一个块
                    currentChunk = getNextChunk();
                    if (currentChunk == null) {
                        break; // 没有更多数据
                    }
                    currentChunkOffset = (int) (currentPosition % CHUNK_SIZE);
                }
                
                // 🔑 提前预取：当读取到块的50%时，确保有足够的预取
                if (currentChunkOffset > currentChunk.data.length / 2) {
                    ensurePrefetch();
                }
                
                // 从当前块读取数据
                int availableInChunk = currentChunk.data.length - currentChunkOffset;
                int toRead = (int) Math.min(Math.min(length - bytesRead, availableInChunk), bytesRemaining);
                
                System.arraycopy(currentChunk.data, currentChunkOffset, buffer, offset + bytesRead, toRead);
                
                currentChunkOffset += toRead;
                currentPosition += toRead;
                bytesRead += toRead;
                bytesRemaining -= toRead;
                
                bytesTransferred(toRead);
            }
            
            return bytesRead > 0 ? bytesRead : C.RESULT_END_OF_INPUT;
            
        } catch (Exception e) {
            throw new HttpDataSource.HttpDataSourceException(
                e instanceof IOException ? (IOException) e : new IOException(e), 
                new DataSpec(uri), 
                HttpDataSource.HttpDataSourceException.TYPE_READ);
        }
    }
    
    // 确保有足够的预取任务在运行
    private void ensurePrefetch() {
        int bufferedCount = chunkBuffer.size() + pendingChunks.size();
        if (bufferedCount < prefetchChunks) {
            scheduleNextChunkDownload();
        }
    }
    
    private ChunkData getNextChunk() throws InterruptedException {
        long expectedChunkIndex = nextChunkToRead.get();
        
        Log.d(TAG, "🚀 getNextChunk: expecting chunk " + expectedChunkIndex);
        
        // 首先检查是否已经在 pendingChunks 中
        ChunkData pending = pendingChunks.remove(expectedChunkIndex);
        if (pending != null) {
            Log.d(TAG, "🚀 Found chunk " + expectedChunkIndex + " in pending");
            nextChunkToRead.incrementAndGet();
            // 清理过期的 pending chunks 释放内存
            cleanupOldPendingChunks(expectedChunkIndex);
            return pending;
        }
        
        // 从缓冲区获取块
        int waitCount = 0;
        while (isOpened.get()) {
            ChunkData chunk = chunkBuffer.poll(200, TimeUnit.MILLISECONDS);
            if (chunk != null) {
                Log.d(TAG, "🚀 Got chunk " + chunk.index + " from buffer, expecting " + expectedChunkIndex);
                
                if (chunk.index == expectedChunkIndex) {
                    nextChunkToRead.incrementAndGet();
                    cleanupOldPendingChunks(expectedChunkIndex);
                    return chunk;
                } else if (chunk.index > expectedChunkIndex) {
                    // 块顺序不对，存入 pendingChunks
                    pendingChunks.put(chunk.index, chunk);
                    Log.d(TAG, "🚀 Chunk " + chunk.index + " stored in pending, waiting for " + expectedChunkIndex);
                }
                // 如果 chunk.index < expectedChunkIndex，丢弃（已经过时）
            } else {
                waitCount++;
                if (waitCount % 10 == 0) {
                    Log.d(TAG, "🚀 Waiting for chunk " + expectedChunkIndex + ", waited " + (waitCount * 200) + "ms");
                }
            }
            
            // 检查是否已经下载完成
            if (currentPosition >= contentLength) {
                Log.d(TAG, "🚀 Reached end of content");
                return null;
            }
            
            // 超时保护：等待超过10秒就放弃
            if (waitCount > 50) {
                Log.e(TAG, "🚀 Timeout waiting for chunk " + expectedChunkIndex);
                return null;
            }
        }
        return null;
    }
    
    // 清理过期的 pending chunks 释放内存
    private void cleanupOldPendingChunks(long currentIndex) {
        // 使用传统循环避免 lambda 兼容性问题
        java.util.Iterator<java.util.Map.Entry<Long, ChunkData>> iterator = pendingChunks.entrySet().iterator();
        while (iterator.hasNext()) {
            java.util.Map.Entry<Long, ChunkData> entry = iterator.next();
            if (entry.getKey() < currentIndex) {
                iterator.remove();
            }
        }
    }
    
    @Nullable
    @Override
    public Uri getUri() {
        return uri;
    }
    
    @Override
    public void close() {
        Log.d(TAG, "🚀 Closing parallel data source");
        
        isOpened.set(false);
        
        // 取消所有下载任务
        synchronized (downloadTasks) {
            for (Future<?> task : downloadTasks) {
                task.cancel(true);
            }
            downloadTasks.clear();
        }
        
        // 关闭线程池
        if (downloadExecutor != null) {
            downloadExecutor.shutdownNow();
            downloadExecutor = null;
        }
        
        // 清空缓冲区
        if (chunkBuffer != null) {
            chunkBuffer.clear();
        }
        pendingChunks.clear();
        currentChunk = null;
        
        // 🔑 安全调用 transferEnded - 只有在 uri 不为空时才调用
        if (uri != null) {
            try {
                transferEnded();
            } catch (Exception e) {
                Log.w(TAG, "transferEnded error (ignored)", e);
            }
        }
    }
    
    /**
     * 数据块
     */
    private static class ChunkData {
        final long index;
        final byte[] data;
        
        ChunkData(long index, byte[] data) {
            this.index = index;
            this.data = data;
        }
    }
    
    /**
     * 工厂类
     */
    public static class Factory implements androidx.media3.datasource.DataSource.Factory {
        
        private final OkHttpClient httpClient;
        private final Map<String, String> defaultHeaders;
        
        public Factory(OkHttpClient httpClient, Map<String, String> defaultHeaders) {
            this.httpClient = httpClient;
            this.defaultHeaders = defaultHeaders;
        }
        
        @NonNull
        @Override
        public ParallelDataSource createDataSource() {
            return new ParallelDataSource(httpClient, defaultHeaders);
        }
    }
}
