package com.mynas.nastv.utils;

import com.mynas.nastv.config.AppConfig;
import android.content.Context;
import android.util.Log;

/**
 * 服务器配置助手
 * 提供便捷的服务器地址配置功能
 */
public class ServerConfigHelper {
    private static final String TAG = "ServerConfigHelper";
    
    /**
     * 设置服务器地址（完整地址）
     * @param context 应用上下文
     * @param serverUrl 完整服务器地址，如 "http://192.168.1.100:8123"
     */
    public static void setServerUrl(Context context, String serverUrl) {
        if (serverUrl == null || serverUrl.isEmpty()) {
            Log.w(TAG, "服务器地址为空，跳过设置");
            return;
        }
        
        // 解析服务器地址
        try {
            String cleanUrl = serverUrl.trim();
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "http://" + cleanUrl;
            }
            
            // 移除末尾的斜杠
            if (cleanUrl.endsWith("/")) {
                cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
            }
            
            // 解析主机和端口
            String[] parts = cleanUrl.replace("http://", "").replace("https://", "").split(":");
            String host = parts[0];
            String port = parts.length > 1 ? parts[1] : AppConfig.SERVER_PORT;
            
            // 保存到配置
            SharedPreferencesManager.setServerHost(host);
            SharedPreferencesManager.setServerPort(port);
            
            Log.d(TAG, "服务器地址设置成功: " + host + ":" + port);
            Log.d(TAG, "当前配置:");
            Log.d(TAG, "  - API地址: " + SharedPreferencesManager.getApiBaseUrl());
            Log.d(TAG, "  - 图片服务: " + SharedPreferencesManager.getImageServiceUrl());
            Log.d(TAG, "  - 播放服务: " + SharedPreferencesManager.getPlayServiceUrl());
            Log.d(TAG, "  - 系统API: " + SharedPreferencesManager.getSystemApiUrl());
            
        } catch (Exception e) {
            Log.e(TAG, "服务器地址解析失败: " + serverUrl, e);
        }
    }
    
    /**
     * 设置服务器地址（分别指定主机和端口）
     * @param context 应用上下文  
     * @param host 主机地址，如 "192.168.1.100"
     * @param port 端口号，如 "8123"
     */
    public static void setServerAddress(Context context, String host, String port) {
        if (host == null || host.isEmpty()) {
            Log.w(TAG, "主机地址为空，跳过设置");
            return;
        }
        
        String finalPort = (port == null || port.isEmpty()) ? AppConfig.SERVER_PORT : port;
        
        SharedPreferencesManager.setServerHost(host.trim());
        SharedPreferencesManager.setServerPort(finalPort.trim());
        
        Log.d(TAG, "服务器地址设置成功: " + host + ":" + finalPort);
    }
    
    /**
     * 获取当前服务器配置信息
     * @return 服务器配置信息字符串
     */
    public static String getCurrentServerInfo() {
        StringBuilder info = new StringBuilder();
        info.append("当前服务器配置:\n");
        info.append("主机: ").append(SharedPreferencesManager.getServerHost()).append("\n");
        info.append("端口: ").append(SharedPreferencesManager.getServerPort()).append("\n");
        info.append("完整地址: ").append(SharedPreferencesManager.getServerBaseUrl()).append("\n");
        info.append("API地址: ").append(SharedPreferencesManager.getApiBaseUrl()).append("\n");
        info.append("图片服务: ").append(SharedPreferencesManager.getImageServiceUrl()).append("\n");
        info.append("播放服务: ").append(SharedPreferencesManager.getPlayServiceUrl());
        return info.toString();
    }
    
    /**
     * 重置为默认服务器地址
     * @param context 应用上下文
     */
    public static void resetToDefault(Context context) {
        setServerAddress(context, AppConfig.SERVER_IP, AppConfig.SERVER_PORT);
        Log.d(TAG, "服务器地址已重置为默认值");
    }
    
    /**
     * 测试服务器连通性
     * @param serverUrl 要测试的服务器地址
     * @param callback 测试结果回调
     */
    public static void testServerConnection(String serverUrl, ServerTestCallback callback) {
        new Thread(() -> {
            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                
                String testUrl = serverUrl;
                if (!testUrl.startsWith("http")) {
                    testUrl = "http://" + testUrl;
                }
                if (testUrl.endsWith("/")) {
                    testUrl = testUrl.substring(0, testUrl.length() - 1);
                }
                testUrl += "/api/health";
                
                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(testUrl)
                        .build();
                
                okhttp3.Response response = client.newCall(request).execute();
                
                if (response.isSuccessful()) {
                    callback.onSuccess("服务器连接成功！");
                } else {
                    callback.onError("服务器响应异常: " + response.code());
                }
                
            } catch (Exception e) {
                callback.onError("连接失败: " + e.getMessage());
            }
        }).start();
    }
    
    /**
     * 服务器测试回调接口
     */
    public interface ServerTestCallback {
        void onSuccess(String message);
        void onError(String error);
    }
    
    /**
     * 快速配置常用服务器地址
     */
    public static class QuickConfig {
        public static void setLocalhost(Context context) {
            setServerAddress(context, "127.0.0.1", "8123");
        }
        
        public static void setLAN(Context context, String ipSuffix) {
            // 例如: setLAN(context, "100") -> 192.168.1.100
            setServerAddress(context, "192.168.1." + ipSuffix, AppConfig.SERVER_PORT);
        }
        
        public static void setCustomIP(Context context, String ip) {
            setServerAddress(context, ip, AppConfig.SERVER_PORT);
        }
        
        public static void setCustomPort(Context context, String port) {
            String currentHost = SharedPreferencesManager.getServerHost();
            setServerAddress(context, currentHost, port);
        }
    }
}
