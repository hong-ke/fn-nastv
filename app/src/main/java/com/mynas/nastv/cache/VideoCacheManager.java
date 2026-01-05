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
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 视频缓存管理器
 * 
 * 基于本地代理服务器实现边下边播
 * 支持 Media3 ExoPlayer
 */
public class VideoCacheManager {
    private static final String TAG = "VideoCacheManager";
    
    private static final String CACHE_DIR = "video_cache";
    private static final int CHUNK_SIZE = 2 * 1024 * 1024; // 2MB
    private static final int PROXY_PORT_START = 39500;
    
    private static VideoCacheManager instance;
    
    private OkHttpClient httpClient;
    private static ServerSocket proxyServer;
    private static int proxyPort = -1;
    private static AtomicBoolean isProxyRunning = new AtomicBoolean(false);
    private static ExecutorService proxyExecutor;
    
    private static String currentOriginUrl;
    private static String currentVideoId;
    private static File currentCacheDir;
    private static long currentContentLength = -1;
    private static Map<String, String> currentHeaders = new HashMap<>();
    
    // 缓存状态
    private static ConcurrentHashMap<Integer, Boolean> cachedChunks = new ConcurrentHashMap<>();
    
    private VideoCacheManager() {
        httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
    }
    
    public static synchronized VideoCacheManager instance() {
        if (instance == null) {
            instance = new VideoCacheManager();
        }
        return instance;
    }
    
    /**
     * 设置请求头
     */
    public static void setCurrentHeaders(Map<String, String> headers) {
        if (headers != null) {
            currentHeaders.clear();
            currentHeaders.putAll(headers);
        }
    }
    
    /**
     * 获取代理 URL
     */
    public String getProxyUrl(Context context, String originUrl, Map<String, String> headers, File cacheDir) {
        if (TextUtils.isEmpty(originUrl)) {
            return originUrl;
        }
        
        // 本地文件直接返回
        if (originUrl.startsWith("file://") || originUrl.startsWith("/")) {
            return originUrl;
        }
        
        // 设置当前视频信息
        currentOriginUrl = originUrl;
        currentVideoId = md5(originUrl);
        currentCacheDir = new File(cacheDir, currentVideoId);
        if (!currentCacheDir.exists()) {
            currentCacheDir.mkdirs();
        }
        
        if (headers != null) {
            setCurrentHeaders(headers);
        }
        
        // 启动代理服务器
        startProxyServer(context);
        
        if (proxyPort > 0) {
            String proxyUrl = "http://127.0.0.1:" + proxyPort + "/" + currentVideoId;
            Log.d(TAG, "代理 URL: " + proxyUrl);
            return proxyUrl;
        }
        
        return originUrl;
    }
    
    private void startProxyServer(Context context) {
        if (isProxyRunning.get() && proxyServer != null && !proxyServer.isClosed()) {
            return;
        }
        
        try {
            // 查找可用端口
            for (int port = PROXY_PORT_START; port < PROXY_PORT_START + 100; port++) {
                try {
                    proxyServer = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
                    proxyPort = port;
                    break;
                } catch (IOException e) {
                    // 端口被占用，尝试下一个
                }
            }
            
            if (proxyServer == null) {
                Log.e(TAG, "无法启动代理服务器");
                return;
            }
            
            isProxyRunning.set(true);
            
            if (proxyExecutor == null || proxyExecutor.isShutdown()) {
                proxyExecutor = Executors.newCachedThreadPool();
            }
            
            // 启动代理服务器线程
            proxyExecutor.execute(() -> {
                Log.d(TAG, "代理服务器启动，端口: " + proxyPort);
                while (isProxyRunning.get() && !proxyServer.isClosed()) {
                    try {
                        Socket client = proxyServer.accept();
                        proxyExecutor.execute(() -> handleClient(client));
                    } catch (IOException e) {
                        if (isProxyRunning.get()) {
                            Log.e(TAG, "代理服务器错误", e);
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "启动代理服务器失败", e);
        }
    }
    
    private void handleClient(Socket client) {
        try {
            InputStream input = client.getInputStream();
            OutputStream output = client.getOutputStream();
            
            // 读取 HTTP 请求
            byte[] buffer = new byte[8192];
            int len = input.read(buffer);
            if (len <= 0) {
                client.close();
                return;
            }
            
            String request = new String(buffer, 0, len);
            
            // 解析 Range 头
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
            
            // 获取内容长度
            if (currentContentLength < 0) {
                fetchContentLength();
            }
            
            if (rangeEnd < 0 && currentContentLength > 0) {
                rangeEnd = currentContentLength - 1;
            }
            
            // 发送响应
            sendResponse(output, rangeStart, rangeEnd);
            
            client.close();
        } catch (Exception e) {
            Log.e(TAG, "处理客户端请求失败", e);
            try {
                client.close();
            } catch (IOException ignored) {}
        }
    }
    
    private void fetchContentLength() {
        try {
            Request.Builder builder = new Request.Builder()
                .url(currentOriginUrl)
                .head();
            
            for (Map.Entry<String, String> entry : currentHeaders.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
            
            Response response = httpClient.newCall(builder.build()).execute();
            String contentLengthStr = response.header("Content-Length");
            if (contentLengthStr != null) {
                currentContentLength = Long.parseLong(contentLengthStr);
                Log.d(TAG, "视频大小: " + (currentContentLength / 1024 / 1024) + "MB");
            }
            response.close();
        } catch (Exception e) {
            Log.e(TAG, "获取内容长度失败", e);
        }
    }
    
    private void sendResponse(OutputStream output, long rangeStart, long rangeEnd) throws IOException {
        long contentLength = rangeEnd - rangeStart + 1;
        
        // 发送 HTTP 响应头
        StringBuilder header = new StringBuilder();
        header.append("HTTP/1.1 206 Partial Content\r\n");
        header.append("Content-Type: video/mp4\r\n");
        header.append("Content-Length: ").append(contentLength).append("\r\n");
        header.append("Content-Range: bytes ").append(rangeStart).append("-").append(rangeEnd).append("/").append(currentContentLength).append("\r\n");
        header.append("Accept-Ranges: bytes\r\n");
        header.append("Connection: close\r\n");
        header.append("\r\n");
        
        output.write(header.toString().getBytes());
        output.flush();
        
        // 发送数据
        sendData(output, rangeStart, rangeEnd);
    }
    
    private void sendData(OutputStream output, long rangeStart, long rangeEnd) throws IOException {
        int startChunk = (int) (rangeStart / CHUNK_SIZE);
        int endChunk = (int) (rangeEnd / CHUNK_SIZE);
        
        long bytesWritten = 0;
        long totalBytes = rangeEnd - rangeStart + 1;
        
        for (int chunkIndex = startChunk; chunkIndex <= endChunk && bytesWritten < totalBytes; chunkIndex++) {
            File chunkFile = new File(currentCacheDir, "chunk_" + chunkIndex + ".cache");
            
            // 如果 chunk 不存在，从网络下载
            if (!chunkFile.exists() || chunkFile.length() < CHUNK_SIZE) {
                downloadChunk(chunkIndex);
            }
            
            // 读取 chunk 并发送
            if (chunkFile.exists()) {
                long chunkStart = (long) chunkIndex * CHUNK_SIZE;
                long offsetInChunk = Math.max(0, rangeStart - chunkStart);
                long bytesToRead = Math.min(CHUNK_SIZE - offsetInChunk, totalBytes - bytesWritten);
                
                RandomAccessFile raf = new RandomAccessFile(chunkFile, "r");
                raf.seek(offsetInChunk);
                
                byte[] buffer = new byte[8192];
                long remaining = bytesToRead;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = raf.read(buffer, 0, toRead);
                    if (read <= 0) break;
                    output.write(buffer, 0, read);
                    remaining -= read;
                    bytesWritten += read;
                }
                
                raf.close();
            }
        }
        
        output.flush();
    }
    
    private void downloadChunk(int chunkIndex) {
        long start = (long) chunkIndex * CHUNK_SIZE;
        long end = start + CHUNK_SIZE - 1;
        if (currentContentLength > 0 && end >= currentContentLength) {
            end = currentContentLength - 1;
        }
        
        try {
            Request.Builder builder = new Request.Builder()
                .url(currentOriginUrl)
                .addHeader("Range", "bytes=" + start + "-" + end);
            
            for (Map.Entry<String, String> entry : currentHeaders.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
            
            Response response = httpClient.newCall(builder.build()).execute();
            
            if (response.isSuccessful() && response.body() != null) {
                File chunkFile = new File(currentCacheDir, "chunk_" + chunkIndex + ".cache");
                RandomAccessFile raf = new RandomAccessFile(chunkFile, "rw");
                
                InputStream input = response.body().byteStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    raf.write(buffer, 0, read);
                }
                
                raf.close();
                input.close();
                cachedChunks.put(chunkIndex, true);
                Log.d(TAG, "下载 chunk " + chunkIndex + " 完成");
            }
            
            response.close();
        } catch (Exception e) {
            Log.e(TAG, "下载 chunk " + chunkIndex + " 失败", e);
        }
    }
    
    public void stopProxy() {
        isProxyRunning.set(false);
        if (proxyServer != null) {
            try {
                proxyServer.close();
            } catch (IOException ignored) {}
            proxyServer = null;
        }
        proxyPort = -1;
    }
    
    public void clearCache() {
        cachedChunks.clear();
        if (currentCacheDir != null && currentCacheDir.exists()) {
            File[] files = currentCacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }
    
    private static String md5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
