package com.mynas.nastv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.mynas.nastv.R;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.NetworkConfigUpdater;

/**
 * 🚀 应用启动页
 * 功能：品牌展示、初始化检查、登录状态判断
 * 对应Web项目：应用入口逻辑
 */
public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private static final int SPLASH_DURATION = 2000; // 2秒启动页
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        Log.d(TAG, "🚀 NasTV启动页加载");
        
        // 🔧 初始化网络配置，避免使用硬编码IP
        NetworkConfigUpdater.initializeNetworkConfig(this);
        
        // 🌐 强制使用新的服务器地址
        NetworkConfigUpdater.forceCustomServer(this, "172.20.10.3", "8123");
        
        // 🔄 延迟跳转，给用户展示品牌
        new Handler(Looper.getMainLooper()).postDelayed(this::checkLoginAndNavigate, SPLASH_DURATION);
    }
    
    /**
     * 🔐 检查登录状态并导航到对应页面
     */
    private void checkLoginAndNavigate() {
        boolean isLoggedIn = SharedPreferencesManager.isLoggedIn();
        
        Log.d(TAG, "🔐 用户登录状态: " + (isLoggedIn ? "已登录" : "未登录"));
        
        Intent intent;
        if (isLoggedIn) {
            // 📱 已登录 -> 进入主页
            intent = new Intent(this, MainActivity.class);
        } else {
            // 🔐 未登录 -> 进入登录页
            intent = new Intent(this, LoginActivity.class);
        }
        
        startActivity(intent);
        finish(); // 关闭启动页
        
        // 🎨 添加过渡动画
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
