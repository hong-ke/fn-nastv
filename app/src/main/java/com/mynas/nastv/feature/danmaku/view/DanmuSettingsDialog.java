package com.mynas.nastv.feature.danmaku.view;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.mynas.nastv.R;
import com.mynas.nastv.feature.danmaku.api.IDanmuController;
import com.mynas.nastv.utils.SharedPreferencesManager;

/**
 * 🎨 弹幕设置对话框
 * 专为 Android TV 遥控器优化
 */
public class DanmuSettingsDialog extends Dialog {

    private final IDanmuController controller;
    private TextView textStatus, textAlpha, textSize, textSpeed, textRegion;

    public DanmuSettingsDialog(Context context, IDanmuController controller) {
        super(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        this.controller = controller;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_danmaku_settings);

        // 绑定视图
        textStatus = findViewById(R.id.text_danmu_status);
        textAlpha = findViewById(R.id.text_danmu_alpha);
        textSize = findViewById(R.id.text_danmu_size);
        textSpeed = findViewById(R.id.text_danmu_speed);
        textRegion = findViewById(R.id.text_danmu_region);

        // 初始化显示
        refreshUI();

        // 设置点击事件（开关切换）
        findViewById(R.id.setting_danmu_enable).setOnClickListener(v -> toggleEnable());
        
        // 绑定左右键调节逻辑
        setupAdjustment(findViewById(R.id.setting_danmu_alpha), 1);
        setupAdjustment(findViewById(R.id.setting_danmu_size), 2);
        setupAdjustment(findViewById(R.id.setting_danmu_speed), 3);
        setupAdjustment(findViewById(R.id.setting_danmu_region), 4);
    }

    private void refreshUI() {
        textStatus.setText(SharedPreferencesManager.isDanmakuEnabled() ? "开启" : "关闭");
        textAlpha.setText(SharedPreferencesManager.getDanmakuAlpha() * 100 / 255 + "%");
        
        int size = SharedPreferencesManager.getDanmakuTextSize();
        textSize.setText(size < 20 ? "小" : (size > 30 ? "大" : "中"));
        
        float speed = SharedPreferencesManager.getDanmakuSpeed();
        textSpeed.setText(speed < 1.0f ? "慢" : (speed > 1.5f ? "快" : "正常"));
        
        int region = SharedPreferencesManager.getDanmakuRegion();
        textRegion.setText(region == 100 ? "满屏" : (region == 50 ? "半屏" : "1/4屏"));
    }

    private void toggleEnable() {
        boolean enabled = !SharedPreferencesManager.isDanmakuEnabled();
        SharedPreferencesManager.setDanmakuEnabled(enabled);
        if (controller != null) {
            if (enabled) controller.show();
            else controller.hide();
        }
        refreshUI();
    }

    private void setupAdjustment(View view, int type) {
        view.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    adjust(type, keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
                    return true;
                }
            }
            return false;
        });
    }

    private void adjust(int type, boolean increase) {
        switch (type) {
            case 1: // Alpha
                int alpha = SharedPreferencesManager.getDanmakuAlpha();
                alpha = increase ? Math.min(255, alpha + 25) : Math.max(25, alpha - 25);
                SharedPreferencesManager.setDanmakuAlpha(alpha);
                break;
            case 2: // Size
                int size = SharedPreferencesManager.getDanmakuTextSize();
                size = increase ? Math.min(48, size + 4) : Math.max(12, size - 4);
                SharedPreferencesManager.setDanmakuTextSize(size);
                break;
            case 3: // Speed
                float speed = SharedPreferencesManager.getDanmakuSpeed();
                speed = increase ? Math.min(3.0f, speed + 0.2f) : Math.max(0.5f, speed - 0.2f);
                SharedPreferencesManager.setDanmakuSpeed(speed);
                break;
            case 4: // Region
                int region = SharedPreferencesManager.getDanmakuRegion();
                if (increase) {
                    if (region == 25) region = 50;
                    else if (region == 50) region = 100;
                } else {
                    if (region == 100) region = 50;
                    else if (region == 50) region = 25;
                }
                SharedPreferencesManager.setDanmakuRegion(region);
                break;
        }
        
        // 实时更新控制器
        if (controller != null) {
            controller.updateConfig(null); // 通知控制器重新加载配置
        }
        refreshUI();
    }
}
