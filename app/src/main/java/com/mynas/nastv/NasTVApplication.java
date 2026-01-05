package com.mynas.nastv;

import android.app.Application;
import android.util.Log;

import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.utils.SharedPreferencesManager;

/**
 * NasTV应用程序类
 * 负责全局初始化和配置
 */
public class NasTVApplication extends Application {
    private static final String TAG = "NasTVApplication";
    private static NasTVApplication instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        Log.d(TAG, "NasTV应用启动中...");
        
        // 初始化全局组件
        initializeComponents();
        
        Log.d(TAG, "NasTV应用启动完成");
    }
    
    private void initializeComponents() {
        // 初始化网络客户端
        ApiClient.initialize(this);
        
        // 初始化偏好设置管理器
        SharedPreferencesManager.initialize(this);
        
        Log.d(TAG, "全局组件初始化完成");
    }
    
    public static NasTVApplication getInstance() {
        return instance;
    }
}
