package com.mynas.nastv.utils;

import com.mynas.nastv.config.AppConfig;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * SharedPreferences管理器
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
    private static final String KEY_DANMAKU_TEXT_SIZE = "danmaku_text_size_v2";
    private static final String KEY_DANMAKU_REGION = "danmaku_region_v2";
    private static final String KEY_VIDEO_QUALITY = "video_quality";
    
    // 系统设置
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_API_BASE_URL = "api_base_url";
    private static final String KEY_FNOS_SERVER_URL = "fnos_server_url";
    private static final String KEY_SERVER_HOST = "server_host";
    private static final String KEY_SERVER_PORT = "server_port";
    private static final String KEY_DANMU_SERVER_PORT = "danmu_server_port";
    private static final String KEY_DANMU_SERVER_HOST = "danmu_server_host";
    private static final String KEY_LAST_USERNAME = "last_username";
    
    // 默认服务器配置
    private static final String DEFAULT_SERVER_HOST = AppConfig.SERVER_IP;
    private static final String DEFAULT_SERVER_PORT = AppConfig.SERVER_PORT;
    private static final String DEFAULT_DANMU_PORT = "5001"; // 本地弹幕服务器端口
    private static final String DEFAULT_DANMU_HOST = "192.168.3.19"; // 本地弹幕服务器

    private static SharedPreferences sharedPreferences;
    private static Context context;
    
    public static void initialize(Context ctx) {
        context = ctx.getApplicationContext();
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Log.d(TAG, "SharedPreferences管理器初始化完成");
    }
    
    // 认证相关方法
    
    /**
     * 保存认证Token
     */
    public static void saveAuthToken(String token) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_AUTH_TOKEN, token)
                    .apply();
            Log.d(TAG, "认证Token已保存");
        }
    }
    
    /**
     * 获取认证Token
     */
    public static String getAuthToken() {
        if (sharedPreferences != null) {
            String token = sharedPreferences.getString(KEY_AUTH_TOKEN, null);
            Log.d(TAG, "获取认证Token: " + (token != null ? "存在" : "不存在"));
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
            Log.d(TAG, "认证信息已清除");
        }
    }
    
    /**
     * 检查是否已登录
     */
    public static boolean isLoggedIn() {
        String token = getAuthToken();
        return token != null && !token.isEmpty();
    }
    
    /**
     * 保存上次登录的用户名
     */
    public static void saveLastUsername(String username) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_LAST_USERNAME, username)
                    .apply();
            Log.d(TAG, "用户名已保存: " + username);
        }
    }
    
    /**
     * 获取上次登录的用户名
     */
    public static String getLastUsername() {
        if (sharedPreferences != null) {
            return sharedPreferences.getString(KEY_LAST_USERNAME, null);
        }
        return null;
    }
    
    // 弹幕设置方法
    
    /**
     * 保存弹幕开关状态
     */
    public static void setDanmakuEnabled(boolean enabled) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putBoolean(KEY_DANMAKU_ENABLED, enabled)
                    .apply();
            Log.d(TAG, "弹幕开关设置为: " + enabled);
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
            Log.d(TAG, "弹幕速度设置为: " + speed);
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
            Log.d(TAG, "弹幕透明度设置为: " + alpha);
        }
    }
    
    /**
     * 获取弹幕透明度
     */
    public static int getDanmakuAlpha() {
        return sharedPreferences != null ? 
                sharedPreferences.getInt(KEY_DANMAKU_ALPHA, 180) : 180;
    }

    /**
     * 保存弹幕文字大小
     */
    public static void setDanmakuTextSize(int size) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putInt(KEY_DANMAKU_TEXT_SIZE, size).apply();
            Log.d(TAG, "弹幕文字大小设置为: " + size);
        }
    }

    /**
     * 获取弹幕文字大小 (默认 24)
     */
    public static int getDanmakuTextSize() {
        return sharedPreferences != null ? 
                sharedPreferences.getInt(KEY_DANMAKU_TEXT_SIZE, 24) : 24;
    }

    /**
     * 保存弹幕区域百分比 (0-100)
     */
    public static void setDanmakuRegion(int percent) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putInt(KEY_DANMAKU_REGION, percent).apply();
            Log.d(TAG, "弹幕区域设置为: " + percent + "%");
        }
    }

    /**
     * 获取弹幕区域百分比 (默认 100)
     */
    public static int getDanmakuRegion() {
        return sharedPreferences != null ? 
                sharedPreferences.getInt(KEY_DANMAKU_REGION, 100) : 100;
    }
    
    // 视频设置方法
    
    /**
     * 保存默认视频质量
     */
    public static void setVideoQuality(String quality) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_VIDEO_QUALITY, quality)
                    .apply();
            Log.d(TAG, "视频质量设置为: " + quality);
        }
    }
    
    /**
     * 获取默认视频质量
     */
    public static String getVideoQuality() {
        return sharedPreferences != null ? 
                sharedPreferences.getString(KEY_VIDEO_QUALITY, "1080p") : "1080p";
    }
    
    // 播放器设置 - 自动连播
    private static final String KEY_AUTO_PLAY_NEXT = "auto_play_next";
    
    /**
     * 设置自动连播
     */
    public static void setAutoPlayNext(boolean enabled) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putBoolean(KEY_AUTO_PLAY_NEXT, enabled).apply();
            Log.d(TAG, "自动连播设置为: " + enabled);
        }
    }
    
    /**
     * 获取自动连播设置
     */
    public static boolean isAutoPlayNext() {
        return sharedPreferences != null ? 
                sharedPreferences.getBoolean(KEY_AUTO_PLAY_NEXT, true) : true;
    }
    
    // 播放器设置 - 跳过片头
    private static final String KEY_SKIP_INTRO = "skip_intro";
    
    /**
     * 设置跳过片头秒数
     */
    public static void setSkipIntro(int seconds) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putInt(KEY_SKIP_INTRO, seconds).apply();
            Log.d(TAG, "跳过片头设置为: " + seconds + "秒");
        }
    }
    
    /**
     * 获取跳过片头秒数
     */
    public static int getSkipIntro() {
        return sharedPreferences != null ? 
                sharedPreferences.getInt(KEY_SKIP_INTRO, 0) : 0;
    }
    
    // 播放器设置 - 跳过片尾
    private static final String KEY_SKIP_OUTRO = "skip_outro";
    
    /**
     * 设置跳过片尾秒数
     */
    public static void setSkipOutro(int seconds) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putInt(KEY_SKIP_OUTRO, seconds).apply();
            Log.d(TAG, "跳过片尾设置为: " + seconds + "秒");
        }
    }
    
    /**
     * 获取跳过片尾秒数
     */
    public static int getSkipOutro() {
        return sharedPreferences != null ? 
                sharedPreferences.getInt(KEY_SKIP_OUTRO, 0) : 0;
    }
    
    // 播放器设置 - 画面比例
    private static final String KEY_ASPECT_RATIO = "aspect_ratio";
    
    // 播放器设置 - 解码器类型 (0=自动/硬解优先, 1=软解)
    private static final String KEY_DECODER_TYPE = "decoder_type";
    
    /**
     * 设置画面比例 (0=默认, 1=16:9, 2=4:3, 3=填充)
     */
    public static void setAspectRatio(int ratio) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putInt(KEY_ASPECT_RATIO, ratio).apply();
            Log.d(TAG, "画面比例设置为: " + ratio);
        }
    }
    
    /**
     * 获取画面比例
     */
    public static int getAspectRatio() {
        return sharedPreferences != null ? 
                sharedPreferences.getInt(KEY_ASPECT_RATIO, 0) : 0;
    }
    
    /**
     * 设置解码器类型 (0=自动/硬解优先, 1=软解)
     */
    public static void setDecoderType(int type) {
        if (sharedPreferences != null) {
            sharedPreferences.edit().putInt(KEY_DECODER_TYPE, type).apply();
            Log.d(TAG, "解码器类型设置为: " + (type == 0 ? "硬解" : "软解"));
        }
    }
    
    /**
     * 获取解码器类型 (0=自动/硬解优先, 1=软解)
     */
    public static int getDecoderType() {
        return sharedPreferences != null ? 
                sharedPreferences.getInt(KEY_DECODER_TYPE, 0) : 0;
    }
    
    /**
     * 是否使用软解
     */
    public static boolean useSoftwareDecoder() {
        return getDecoderType() == 1;
    }
    
    // 系统设置方法
    
    /**
     * 设置是否首次启动
     */
    public static void setFirstLaunch(boolean isFirstLaunch) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putBoolean(KEY_FIRST_LAUNCH, isFirstLaunch)
                    .apply();
            Log.d(TAG, "首次启动标记设置为: " + isFirstLaunch);
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
            Log.d(TAG, "API基础URL设置为: " + url);
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
     * 获取服务器主机地址
     */
    public static String getServerHost() {
        return sharedPreferences != null ? 
                sharedPreferences.getString(KEY_SERVER_HOST, DEFAULT_SERVER_HOST) : 
                DEFAULT_SERVER_HOST;
    }
    
    /**
     * 设置服务器主机地址
     */
    public static void setServerHost(String host) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_SERVER_HOST, host)
                    .apply();
            Log.d(TAG, "服务器主机地址设置为: " + host);
        }
    }
    
    /**
     * 获取服务器端口
     */
    public static String getServerPort() {
        return sharedPreferences != null ? 
                sharedPreferences.getString(KEY_SERVER_PORT, DEFAULT_SERVER_PORT) : 
                DEFAULT_SERVER_PORT;
    }
    
    /**
     * 设置服务器端口
     */
    public static void setServerPort(String port) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_SERVER_PORT, port)
                    .apply();
            Log.d(TAG, "服务器端口设置为: " + port);
        }
    }
    
    /**
     * 获取完整服务器地址 (http://host:port)
     */
    public static String getServerBaseUrl() {
        return "http://" + getServerHost() + ":" + getServerPort();
    }
    
    /**
     * 获取弹幕服务器端口
     */
    public static String getDanmuServerPort() {
        return sharedPreferences != null ? 
                sharedPreferences.getString(KEY_DANMU_SERVER_PORT, DEFAULT_DANMU_PORT) : 
                DEFAULT_DANMU_PORT;
    }
    
    /**
     * 设置弹幕服务器端口
     */
    public static void setDanmuServerPort(String port) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_DANMU_SERVER_PORT, port)
                    .apply();
            Log.d(TAG, "弹幕服务器端口设置为: " + port);
        }
    }
    
    /**
     * 获取弹幕服务器主机地址
     */
    public static String getDanmuServerHost() {
        return sharedPreferences != null ? 
                sharedPreferences.getString(KEY_DANMU_SERVER_HOST, DEFAULT_DANMU_HOST) : 
                DEFAULT_DANMU_HOST;
    }
    
    /**
     * 设置弹幕服务器主机地址
     */
    public static void setDanmuServerHost(String host) {
        if (sharedPreferences != null) {
            sharedPreferences.edit()
                    .putString(KEY_DANMU_SERVER_HOST, host)
                    .apply();
            Log.d(TAG, "弹幕服务器主机设置为: " + host);
        }
    }
    
    /**
     * 获取弹幕服务器完整地址 (http://danmuHost:danmuPort)
     */
    public static String getDanmuServerBaseUrl() {
        return "http://" + getDanmuServerHost() + ":" + getDanmuServerPort();
    }
    
    /**
     * 获取默认API基础URL
     * 注意: 主API服务器使用 /v/ 路径前缀
     */
    private static String getDefaultApiBaseUrl() {
        return getServerBaseUrl() + "/v/";
    }
    
    /**
     * 图片服务URL
     */
    public static String getImageServiceUrl() {
        return getServerBaseUrl() + "/v/api/v1/sys/img";
    }
    
    /**
     * 获取播放服务URL前缀
     * 视频播放地址: {baseUrl}/v/api/v1/media/range/{mediaGuid}
     */
    public static String getPlayServiceUrl() {
        return getServerBaseUrl();
    }
    
    /**
     * 获取系统API URL
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
            Log.d(TAG, "FnOS服务器URL已保存: " + url);
        }
    }
    
    /**
     * 获取FnOS服务器URL
     */
    public static String getFnOSServerUrl() {
        if (sharedPreferences != null) {
            String url = sharedPreferences.getString(KEY_FNOS_SERVER_URL, null);
            if (url != null) {
                Log.d(TAG, "获取FnOS服务器URL: " + url);
            } else {
                Log.d(TAG, "FnOS服务器URL: 未设置");
            }
            return url;
        }
        return null;
    }
    
    // 通用方法
    
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
            Log.d(TAG, "已移除设置项: " + key);
        }
    }
    
    /**
     * 清除所有数据
     */
    public static void clear() {
        if (sharedPreferences != null) {
            sharedPreferences.edit().clear().apply();
            Log.d(TAG, "所有设置已清除");
        }
    }
}
