package com.mynas.nastv;

import android.app.Application;
import android.util.Log;

import com.mynas.nastv.cache.OkHttpProxyCacheManager;
import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.shuyu.gsyvideoplayer.cache.CacheFactory;

/**
 * 📱 NasTV应用程序类
 * 负责全局初始化和配置
 */
public class NasTVApplication extends Application {
    private static final String TAG = "NasTVApplication";
    private static NasTVApplication instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        Log.d(TAG, "🚀 NasTV应用启动中...");
        
        // 初始化全局组件
        initializeComponents();
        
        Log.d(TAG, "✅ NasTV应用启动完成");
    }
    
    private void initializeComponents() {
        // 🔗 初始化网络客户端
        ApiClient.initialize(this);
        
        // 💾 初始化偏好设置管理器
        SharedPreferencesManager.initialize(this);
        
        // 🔑 注册基于 OkHttp 的缓存管理器
        // 解决 HttpProxyCacheServer (HttpURLConnection) 无法正确传递认证头的问题
        CacheFactory.setCacheManager(OkHttpProxyCacheManager.class);
        Log.d(TAG, "🔑 OkHttpProxyCacheManager 已注册");
        
        // 🔑 初始化缓存清理任务（定时清理遗留缓存文件）
        OkHttpProxyCacheManager.initCleanupTask(this);
        Log.d(TAG, "🔑 缓存清理任务已启动");
        
        Log.d(TAG, "📦 全局组件初始化完成");
    }
    
    public static NasTVApplication getInstance() {
        return instance;
    }
}
