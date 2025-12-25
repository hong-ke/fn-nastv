package com.mynas.nastv.cache;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.danikula.videocache.CacheListener;
import com.danikula.videocache.HttpProxyCacheServer;
import com.danikula.videocache.file.Md5FileNameGenerator;
import com.danikula.videocache.headers.HeaderInjector;
import com.shuyu.gsyvideoplayer.cache.ICacheManager;
import com.danikula.videocache.StorageUtils;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;

/**
 * 🔧 支持鉴权的代理缓存管理器
 * 
 * 解决 GSYVideoPlayer 内置 HttpProxyCacheServer 的鉴权问题：
 * - 使用动态 HeaderInjector，每次请求都获取最新的 headers
 * - 支持 authx 签名、Cookie、Authorization 等认证头
 * 
 * 参考 VideoPlayerActivity 中 CachedDataSourceFactory 的实现逻辑
 */
public class AuthProxyCacheManager implements ICacheManager, CacheListener {
    private static final String TAG = "AuthProxyCacheManager";
    
    // 缓存配置
    public static long DEFAULT_MAX_SIZE = 512 * 1024 * 1024; // 512MB
    public static int DEFAULT_MAX_COUNT = -1;
    
    // 代理服务器
    protected HttpProxyCacheServer proxy;
    protected File mCacheDir;
    protected boolean mCacheFile;
    
    // 单例
    private static AuthProxyCacheManager instance;
    
    // 缓存监听
    private ICacheAvailableListener cacheAvailableListener;
    
    // 🔑 关键：静态 headers，供 HeaderInjector 使用
    private static volatile Map<String, String> sCurrentHeaders = new HashMap<>();
    private static final Object sHeaderLock = new Object();
    
    /**
     * 单例
     */
    public static synchronized AuthProxyCacheManager instance() {
        if (instance == null) {
            instance = new AuthProxyCacheManager();
        }
        return instance;
    }
    
    /**
     * 🔑 设置当前请求的 headers（在播放前调用）
     */
    public static void setCurrentHeaders(Map<String, String> headers) {
        synchronized (sHeaderLock) {
            sCurrentHeaders.clear();
            if (headers != null) {
                sCurrentHeaders.putAll(headers);
            }
            Log.d(TAG, "🔑 Headers updated: " + sCurrentHeaders.size() + " headers");
            for (Map.Entry<String, String> entry : sCurrentHeaders.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                // 隐藏敏感信息
                if (key.equalsIgnoreCase("Cookie") || 
                    key.equalsIgnoreCase("Authorization") || 
                    key.equalsIgnoreCase("authx")) {
                    Log.d(TAG, "🔑   " + key + ": " + (value.length() > 20 ? value.substring(0, 20) + "..." : value));
                } else {
                    Log.d(TAG, "🔑   " + key + ": " + value);
                }
            }
        }
    }
    
    /**
     * 获取当前 headers
     */
    public static Map<String, String> getCurrentHeaders() {
        synchronized (sHeaderLock) {
            return new HashMap<>(sCurrentHeaders);
        }
    }
    
    @Override
    public void onCacheAvailable(File cacheFile, String url, int percentsAvailable) {
        if (cacheAvailableListener != null) {
            cacheAvailableListener.onCacheAvailable(cacheFile, url, percentsAvailable);
        }
    }
    
    @Override
    public void doCacheLogic(Context context, IMediaPlayer mediaPlayer, 
                             String originUrl, Map<String, String> header, File cachePath) {
        String url = originUrl;
        
        // 🔑 关键：更新静态 headers
        setCurrentHeaders(header);
        
        if (url.startsWith("http") && !url.contains("127.0.0.1") && !url.contains(".m3u8")) {
            HttpProxyCacheServer proxy = getProxy(context.getApplicationContext(), cachePath);
            if (proxy != null) {
                // 转换为代理 URL
                url = proxy.getProxyUrl(url);
                mCacheFile = (!url.startsWith("http"));
                
                // 注册缓存监听
                if (!mCacheFile) {
                    proxy.registerCacheListener(this, originUrl);
                }
                
                Log.d(TAG, "🔑 Proxy URL: " + url.substring(0, Math.min(80, url.length())) + "...");
            }
        } else if ((!url.startsWith("http") && !url.startsWith("rtmp")
                && !url.startsWith("rtsp") && !url.contains(".m3u8"))) {
            mCacheFile = true;
        }
        
        try {
            mediaPlayer.setDataSource(context, Uri.parse(url), header);
        } catch (IOException e) {
            Log.e(TAG, "Error setting data source", e);
        }
    }
    
    /**
     * 获取或创建代理服务器
     */
    public HttpProxyCacheServer getProxy(Context context, File cacheDir) {
        if (proxy == null) {
            proxy = newProxy(context, cacheDir);
        }
        return proxy;
    }
    
    /**
     * 创建代理服务器
     */
    public HttpProxyCacheServer newProxy(Context context, File cacheDir) {
        File dir = cacheDir;
        if (dir == null) {
            dir = StorageUtils.getIndividualCacheDirectory(context);
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        HttpProxyCacheServer.Builder builder = new HttpProxyCacheServer.Builder(context);
        builder.cacheDirectory(dir);
        
        if (DEFAULT_MAX_COUNT > 0) {
            builder.maxCacheFilesCount(DEFAULT_MAX_COUNT);
        } else {
            builder.maxCacheSize(DEFAULT_MAX_SIZE);
        }
        
        // 🔑 关键：使用动态 HeaderInjector
        builder.headerInjector(new DynamicHeaderInjector());
        builder.fileNameGenerator(new Md5FileNameGenerator());
        
        mCacheDir = dir;
        
        Log.d(TAG, "🔑 Created new HttpProxyCacheServer with DynamicHeaderInjector");
        return builder.build();
    }
    
    /**
     * 🔑 动态 HeaderInjector
     * 每次请求都从静态变量获取最新的 headers
     * 🔧 关键修复：每次请求都重新生成签名，避免 nonce 重复
     */
    private static class DynamicHeaderInjector implements HeaderInjector {
        @Override
        public Map<String, String> addHeaders(String url) {
            Map<String, String> headers = new HashMap<>(getCurrentHeaders());
            
            // 🔧 关键修复：每次请求都重新生成签名
            // 因为服务器可能检测 nonce 重复，所以每次请求都需要新的签名
            if (url.contains("direct_link_quality_index") || url.contains("/range/")) {
                try {
                    String signature = com.mynas.nastv.utils.SignatureUtils.generateSignature("GET", url, "", null);
                    if (signature != null) {
                        headers.put("authx", signature);
                        Log.d(TAG, "🔑 [HeaderInjector] Generated NEW signature for request");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "🔑 [HeaderInjector] Failed to generate signature", e);
                }
            }
            
            Log.d(TAG, "🔑 [HeaderInjector] Injecting " + headers.size() + " headers for: " + 
                  (url.length() > 60 ? url.substring(0, 60) + "..." : url));
            
            // 打印 headers（隐藏敏感信息）
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.equalsIgnoreCase("Cookie") || 
                    key.equalsIgnoreCase("Authorization") || 
                    key.equalsIgnoreCase("authx")) {
                    Log.d(TAG, "🔑 [HeaderInjector]   " + key + ": " + 
                          (value.length() > 20 ? value.substring(0, 20) + "..." : value));
                } else {
                    Log.d(TAG, "🔑 [HeaderInjector]   " + key + ": " + value);
                }
            }
            
            return headers;
        }
    }
    
    @Override
    public void clearCache(Context context, File cachePath, String url) {
        if (TextUtils.isEmpty(url)) {
            if (cachePath == null) {
                String path = StorageUtils.getIndividualCacheDirectory(
                    context.getApplicationContext()).getAbsolutePath();
                FileUtils.deleteFiles(new File(path));
            } else {
                FileUtils.deleteFiles(cachePath);
            }
        } else {
            Md5FileNameGenerator generator = new Md5FileNameGenerator();
            String name = generator.generate(url);
            if (cachePath != null) {
                String tmpPath = cachePath.getAbsolutePath() + File.separator + name + ".download";
                String path = cachePath.getAbsolutePath() + File.separator + name;
                CommonUtil.deleteFile(tmpPath);
                CommonUtil.deleteFile(path);
            } else {
                String pathTmp = StorageUtils.getIndividualCacheDirectory(
                    context.getApplicationContext()).getAbsolutePath()
                    + File.separator + name + ".download";
                String path = StorageUtils.getIndividualCacheDirectory(
                    context.getApplicationContext()).getAbsolutePath()
                    + File.separator + name;
                CommonUtil.deleteFile(pathTmp);
                CommonUtil.deleteFile(path);
            }
        }
    }
    
    @Override
    public void release() {
        if (proxy != null) {
            try {
                proxy.unregisterCacheListener(this);
            } catch (Exception e) {
                Log.e(TAG, "Error releasing proxy", e);
            }
        }
    }
    
    @Override
    public boolean cachePreview(Context context, File cacheDir, String url) {
        HttpProxyCacheServer proxy = getProxy(context.getApplicationContext(), cacheDir);
        if (proxy != null) {
            url = proxy.getProxyUrl(url);
        }
        return (!url.startsWith("http"));
    }
    
    @Override
    public boolean hadCached() {
        return mCacheFile;
    }
    
    @Override
    public void setCacheAvailableListener(ICacheAvailableListener listener) {
        this.cacheAvailableListener = listener;
    }
    
    /**
     * 释放代理服务器（切换视频时调用）
     */
    public static void releaseProxy() {
        if (instance != null && instance.proxy != null) {
            try {
                instance.proxy.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down proxy", e);
            }
            instance.proxy = null;
        }
        Log.d(TAG, "🔑 Proxy released");
    }
}
