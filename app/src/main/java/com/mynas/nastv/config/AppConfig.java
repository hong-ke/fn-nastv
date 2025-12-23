package com.mynas.nastv.config;

/**
 * 全局应用配置
 * 集中管理服务器地址等核心配置
 * 
 * 服务器配置说明:
 * - 主 API 服务器: http://192.168.3.20:13381 (媒体库、播放、用户等接口)
 * - 弹幕 API 服务器: http://192.168.3.20:13401 (弹幕数据接口)
 */
public class AppConfig {
    /**
     * 默认服务器 IP 地址
     */
    public static final String SERVER_IP = "192.168.3.20";

    /**
     * 主 API 服务器端口 (媒体库、播放、用户等接口)
     */
    public static final String SERVER_PORT = "13381";
    
    /**
     * 弹幕 API 服务器端口
     */
    public static final String DANMU_PORT = "13401";
    
    /**
     * API 路径前缀
     */
    public static final String API_PATH_PREFIX = "/v/api/v1";
    
    /**
     * 登录时使用的应用名称 (与 Web 端一致)
     */
    public static final String APP_NAME = "trimemedia-web";
}
