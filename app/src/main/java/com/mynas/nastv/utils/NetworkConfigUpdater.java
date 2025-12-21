package com.mynas.nastv.utils;

import android.content.Context;
import android.util.Log;

/**
 * 🔧 网络配置更新器
 * 用于在运行时更新服务器配置，避免使用旧的硬编码地址
 */
public class NetworkConfigUpdater {
    private static final String TAG = "NetworkConfigUpdater";
    
    /**
     * 🚀 初始化网络配置
     * 在Application启动时调用，确保不使用硬编码的IP
     */
    public static void initializeNetworkConfig(Context context) {
        Log.d(TAG, "🔧 开始初始化网络配置");
        
        // 检查当前配置
        String currentHost = SharedPreferencesManager.getServerHost();
        String currentPort = SharedPreferencesManager.getServerPort();
        
        Log.d(TAG, "📊 当前服务器配置:");
        Log.d(TAG, "  - 主机: " + currentHost);
        Log.d(TAG, "  - 端口: " + currentPort);
        Log.d(TAG, "  - 完整地址: " + SharedPreferencesManager.getServerBaseUrl());
        
        // 如果你想使用不同的服务器地址，可以在这里设置
        // 例如：
        // if ("172.16.80.60".equals(currentHost)) {
        //     Log.d(TAG, "🔄 检测到默认IP，更新为新地址");
        //     ServerConfigHelper.setServerAddress(context, "你的新IP", "8123");
        // }
        
        Log.d(TAG, "✅ 网络配置初始化完成");
        Log.d(TAG, "🌐 最终API地址: " + SharedPreferencesManager.getApiBaseUrl());
    }
    
    /**
     * 🔧 强制使用本地服务器（用于开发测试）
     */
    public static void forceLocalhost(Context context) {
        Log.d(TAG, "🔄 强制使用localhost");
        ServerConfigHelper.QuickConfig.setLocalhost(context);
        
        // 🚨 重要：强制重新创建ApiClient以使用新配置
        com.mynas.nastv.network.ApiClient.initialize(context);
        
        logCurrentConfig();
    }
    
    /**
     * 🔧 强制使用自定义IP
     */
    public static void forceCustomServer(Context context, String host, String port) {
        Log.d(TAG, "🔄 强制使用自定义服务器: " + host + ":" + port);
        ServerConfigHelper.setServerAddress(context, host, port);
        
        // 🚨 重要：强制重新创建ApiClient以使用新配置
        com.mynas.nastv.network.ApiClient.initialize(context);
        
        logCurrentConfig();
    }
    
    /**
     * 📊 记录当前配置信息
     */
    private static void logCurrentConfig() {
        Log.d(TAG, "📊 当前网络配置:");
        Log.d(TAG, "  - API地址: " + SharedPreferencesManager.getApiBaseUrl());
        Log.d(TAG, "  - 图片服务: " + SharedPreferencesManager.getImageServiceUrl());
        Log.d(TAG, "  - 播放服务: " + SharedPreferencesManager.getPlayServiceUrl());
        Log.d(TAG, "  - 系统API: " + SharedPreferencesManager.getSystemApiUrl());
    }
}
