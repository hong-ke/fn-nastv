package com.mynas.nastv.ui;

import com.mynas.nastv.config.AppConfig;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.mynas.nastv.R;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.NetworkConfigUpdater;

/**
 * 应用启动页
 * 功能：品牌展示、初始化检查、登录状态判断
 */
public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private static final int SPLASH_DURATION = 1200; // 1.2秒启动页
    
    private ImageView ivLogo;
    private TextView tvAppName;
    private TextView tvSlogan;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Android 12+ SplashScreen API - 立即退出系统启动画面
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> false);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        Log.d(TAG, "NasTV启动页加载");
        
        // 初始化视图
        ivLogo = findViewById(R.id.iv_logo);
        tvAppName = findViewById(R.id.tv_app_name);
        tvSlogan = findViewById(R.id.tv_slogan);
        
        // 初始化网络配置
        NetworkConfigUpdater.initializeNetworkConfig(this);
        NetworkConfigUpdater.forceCustomServer(this, AppConfig.SERVER_IP, AppConfig.SERVER_PORT);
        
        // 启动动画
        startEnterAnimation();
        
        // 延迟跳转
        new Handler(Looper.getMainLooper()).postDelayed(this::checkLoginAndNavigate, SPLASH_DURATION);
    }
    
    /**
     * 启动进入动画
     */
    private void startEnterAnimation() {
        // Logo淡入 + 缩放动画
        ivLogo.setAlpha(0f);
        ivLogo.setScaleX(0.8f);
        ivLogo.setScaleY(0.8f);
        
        ivLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
        
        // 文字淡入动画（延迟）
        tvAppName.setAlpha(0f);
        tvSlogan.setAlpha(0f);
        
        tvAppName.animate()
            .alpha(1f)
            .setStartDelay(200)
            .setDuration(400)
            .start();
            
        tvSlogan.animate()
            .alpha(1f)
            .setStartDelay(350)
            .setDuration(400)
            .start();
    }
    
    /**
     * 检查登录状态并导航到对应页面
     */
    private void checkLoginAndNavigate() {
        boolean isLoggedIn = SharedPreferencesManager.isLoggedIn();
        
        Log.d(TAG, "用户登录状态: " + (isLoggedIn ? "已登录" : "未登录"));
        
        Intent intent;
        if (isLoggedIn) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }
        
        startActivity(intent);
        finish();
        
        // 淡出过渡动画
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
