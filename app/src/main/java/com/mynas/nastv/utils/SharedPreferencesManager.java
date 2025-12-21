package com.mynas.nastv.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 💾 SharedPreferences管理器
 * 负责用户设置和认证信息的持久化存储
 */
public class SharedPreferencesManager {
    private static final String TAG = "SharedPreferencesManager";
    private static final String PREFS_NAME = "nastv_preferences";
    
    // 认证相关
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_USER_INFO = "user_info";
    
    // 播放设置
    private static final String KEY_DANMAKU_ENABLED = "danmaku_enabled";
    private static final String KEY_DANMAKU_SPEED = "danmaku_speed";
    private static final String KEY_DANMAKU_ALPHA = "danmaku_alpha";
    private static final String KEY_VIDEO_QUALITY = "video_quality";
    
    // 系统设置
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_API_BASE_URL = "api_base_url";
    private static final String KEY_FNOS_SERVER_URL = "fnos_server_url";
    private static final String KEY_SERVER_HOST = "server_host";
    private static final String KEY_SERVER_PORT = "server_port";
    
    // 🌐 默认服务器配置
    private static final String DEFAULT_SERVER_HOST = "172.20.10.3";
    private static final String DEFAULT_SERVER_PORT = "8123";
    
    private static SharedPreferences sharedPreferences;
    private static Context context;
    
    /**
     * 🚀 初始化SharedPreferences管理器
     */
    public static void initialize(Context ctx) {
        context = ctx.getApplicationContext();
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Log.d(TAG, "💾 SharedPreferences管理器初始化完成");
    }
    
    // 🔐 认证相关方法
    
    /**
     * 保存认证Token
     */
    public static void saveAuthToken(String token) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_AUTH_TOKEN, token)
                    .apply();
            Log.d(TAG, "🔐 认证Token已保存");
        }
    }
    
    /**
     * 获取认证Token
     */
    public static String getAuthToken() {
        if (sharedPreferences != null) {
            String token = sharedPreferences.getString(KEY_AUTH_TOKEN, null);
            Log.d(TAG, "🔐 获取认证Token: " + (token != null ? "存在" : "不存在"));
            return token;
        }
        return null;
    }
    
    /**
     * 清除认证信息
     */
    public static void clearAuthInfo() {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .remove(KEY_AUTH_TOKEN)
                    .remove(KEY_USER_INFO)
                    .apply();
            Log.d(TAG, "🔐 认证信息已清除");
        }
    }
    
    /**
     * 检查是否已登录
     */
    public static boolean isLoggedIn() {
        String token = getAuthToken();
        return token != null && !token.isEmpty();
    }
    
    // 🎨 弹幕设置方法
    
    /**
     * 保存弹幕开关状态
     */
    public static void setDanmakuEnabled(boolean enabled) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putBoolean(KEY_DANMAKU_ENABLED, enabled)
                    .apply();
            Log.d(TAG, "🎨 弹幕开关设置为: " + enabled);
        }
    }
    
    /**
     * 获取弹幕开关状态
     */
    public static boolean isDanmakuEnabled() {
        return sharedPreferences != null ? 
                sharedPreferences.getBoolean(KEY_DANMAKU_ENABLED, true) : true;
    }
    
    /**
     * 保存弹幕速度
     */
    public static void setDanmakuSpeed(float speed) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putFloat(KEY_DANMAKU_SPEED, speed)
                    .apply();
            Log.d(TAG, "🎨 弹幕速度设置为: " + speed);
        }
    }
    
    /**
     * 获取弹幕速度
     */
    public static float getDanmakuSpeed() {
        return sharedPreferences != null ? 
                sharedPreferences.getFloat(KEY_DANMAKU_SPEED, 1.2f) : 1.2f;
    }
    
    /**
     * 保存弹幕透明度
     */
    public static void setDanmakuAlpha(int alpha) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putInt(KEY_DANMAKU_ALPHA, alpha)
                    .apply();
            Log.d(TAG, "🎨 弹幕透明度设置为: " + alpha);
        }
    }
    
    /**
     * 获取弹幕透明度
     */
    public static int getDanmakuAlpha() {
        return sharedPreferences != null ? 
                sharedPreferences.getInt(KEY_DANMAKU_ALPHA, 180) : 180;
    }
    
    // 🎬 视频设置方法
    
    /**
     * 保存默认视频质量
     */
    public static void setVideoQuality(String quality) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_VIDEO_QUALITY, quality)
                    .apply();
            Log.d(TAG, "🎬 默认视频质量设置为: " + quality);
        }
    }
    
    /**
     * 获取默认视频质量
     */
    public static String getVideoQuality() {
        return sharedPreferences != null ? 
                sharedPreferences.getString(KEY_VIDEO_QUALITY, "1080p") : "1080p";
    }
    
    // ⚙️ 系统设置方法
    
    /**
     * 设置是否首次启动
     */
    public static void setFirstLaunch(boolean isFirstLaunch) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putBoolean(KEY_FIRST_LAUNCH, isFirstLaunch)
                    .apply();
            Log.d(TAG, "⚙️ 首次启动标记设置为: " + isFirstLaunch);
        }
    }
    
    /**
     * 检查是否首次启动
     */
    public static boolean isFirstLaunch() {
        return sharedPreferences != null ? 
                sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true) : true;
    }
    
    /**
     * 保存API基础URL
     */
    public static void setApiBaseUrl(String url) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_API_BASE_URL, url)
                    .apply();
            Log.d(TAG, "⚙️ API基础URL设置为: " + url);
        }
    }
    
    /**
     * 获取API基础URL
     */
    public static String getApiBaseUrl() {
        return sharedPreferences != null ? 
                sharedPreferences.getString(KEY_API_BASE_URL, getDefaultApiBaseUrl()) : 
                getDefaultApiBaseUrl();
    }
    
    /**
     * 🌐 获取服务器主机地址
     */
    public static String getServerHost() {
        return sharedPreferences != null ? 
                sharedPreferences.getString(KEY_SERVER_HOST, DEFAULT_SERVER_HOST) : 
                DEFAULT_SERVER_HOST;
    }
    
    /**
     * 🌐 设置服务器主机地址
     */
    public static void setServerHost(String host) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_SERVER_HOST, host)
                    .apply();
            Log.d(TAG, "🌐 服务器主机地址设置为: " + host);
        }
    }
    
    /**
     * 🌐 获取服务器端口
     */
    public static String getServerPort() {
        return sharedPreferences != null ? 
                sharedPreferences.getString(KEY_SERVER_PORT, DEFAULT_SERVER_PORT) : 
                DEFAULT_SERVER_PORT;
    }
    
    /**
     * 🌐 设置服务器端口
     */
    public static void setServerPort(String port) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_SERVER_PORT, port)
                    .apply();
            Log.d(TAG, "🌐 服务器端口设置为: " + port);
        }
    }
    
    /**
     * 🌐 获取完整服务器地址 (http://host:port)
     */
    public static String getServerBaseUrl() {
        return "http://" + getServerHost() + ":" + getServerPort();
    }
    
    /**
     * 🌐 获取默认API基础URL
     */
    private static String getDefaultApiBaseUrl() {
        return getServerBaseUrl() + "/fnos/v/";
    }
    
    /**
     * 🖼️ 获取图片服务URL
     */
    public static String getImageServiceUrl() {
        return getServerBaseUrl() + "/fnos/v/api/v1/sys/img";
    }
    
    /**
     * 🎬 获取播放服务URL前缀
     */
    public static String getPlayServiceUrl() {
        return getServerBaseUrl() + "/fnos";
    }
    
    /**
     * 🔧 获取系统API URL
     */
    public static String getSystemApiUrl() {
        return getServerBaseUrl() + "/api";
    }
    
    /**
     * 保存FnOS服务器URL
     */
    public static void saveFnOSServerUrl(String url) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_FNOS_SERVER_URL, url)
                    .apply();
            Log.d(TAG, "🌐 FnOS服务器URL已保存: " + url);
        }
    }
    
    /**
     * 获取FnOS服务器URL
     */
    public static String getFnOSServerUrl() {
        if (sharedPreferences != null) {
            String url = sharedPreferences.getString(KEY_FNOS_SERVER_URL, null);
            if (url != null) {
                Log.d(TAG, "🌐 获取FnOS服务器URL: " + url);
            } else {
                Log.d(TAG, "🌐 FnOS服务器URL: 未设置");
            }
            return url;
        }
        return null;
    }
    
    // 🔧 通用方法
    
    /**
     * 保存字符串值
     */
    public static void putString(String key, String value) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putString(key, value).apply();
        }
    }
    
    /**
     * 获取字符串值
     */
    public static String getString(String key, String defaultValue) {
        return sharedPreferences != null ? 
                sharedPreferences.getString(key, defaultValue) : defaultValue;
    }
    
    /**
     * 保存布尔值
     */
    public static void putBoolean(String key, boolean value) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putBoolean(key, value).apply();
        }
    }
    
    /**
     * 获取布尔值
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        return sharedPreferences != null ? 
                sharedPreferences.getBoolean(key, defaultValue) : defaultValue;
    }
    
    /**
     * 保存整数值
     */
    public static void putInt(String key, int value) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putInt(key, value).apply();
        }
    }
    
    /**
     * 获取整数值
     */
    public static int getInt(String key, int defaultValue) {
        return sharedPreferences != null ? 
                sharedPreferences.getInt(key, defaultValue) : defaultValue;
    }
    
    /**
     * 保存浮点数值
     */
    public static void putFloat(String key, float value) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putFloat(key, value).apply();
        }
    }
    
    /**
     * 获取浮点数值
     */
    public static float getFloat(String key, float defaultValue) {
        return sharedPreferences != null ? 
                sharedPreferences.getFloat(key, defaultValue) : defaultValue;
    }
    
    /**
     * 移除指定key的数据
     */
    public static void remove(String key) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().remove(key).apply();
            Log.d(TAG, "🗑️ 已移除设置项: " + key);
        }
    }
    
    /**
     * 清除所有数据
     */
    public static void clear() {
        if (sharedPreferences != null) {
            sharedPreferences.edit().clear().apply();
            Log.d(TAG, "🗑️ 所有设置已清除");
        }
    }
}
