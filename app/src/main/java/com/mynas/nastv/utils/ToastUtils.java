package com.mynas.nastv.utils;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.mynas.nastv.R;

/**
 * 自定义 Toast 工具类
 * 去除 Android 图标，使用简洁的深色圆角样式
 */
public class ToastUtils {
    
    private static Toast currentToast;
    
    /**
     * 显示短时间 Toast
     */
    public static void show(Context context, String message) {
        show(context, message, Toast.LENGTH_SHORT);
    }
    
    /**
     * 显示长时间 Toast
     */
    public static void showLong(Context context, String message) {
        show(context, message, Toast.LENGTH_LONG);
    }
    
    /**
     * 显示自定义 Toast
     */
    public static void show(Context context, String message, int duration) {
        if (context == null || message == null) return;
        
        // 取消之前的 Toast
        if (currentToast != null) {
            currentToast.cancel();
        }
        
        try {
            // 创建自定义视图
            LayoutInflater inflater = LayoutInflater.from(context);
            View layout = inflater.inflate(R.layout.custom_toast, null);
            
            TextView textView = layout.findViewById(R.id.toast_text);
            textView.setText(message);
            
            // 创建 Toast
            currentToast = new Toast(context.getApplicationContext());
            currentToast.setDuration(duration);
            currentToast.setView(layout);
            currentToast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 100);
            currentToast.show();
        } catch (Exception e) {
            // 降级到系统 Toast
            Toast.makeText(context, message, duration).show();
        }
    }
    
    /**
     * 显示居中 Toast
     */
    public static void showCenter(Context context, String message) {
        if (context == null || message == null) return;
        
        if (currentToast != null) {
            currentToast.cancel();
        }
        
        try {
            LayoutInflater inflater = LayoutInflater.from(context);
            View layout = inflater.inflate(R.layout.custom_toast, null);
            
            TextView textView = layout.findViewById(R.id.toast_text);
            textView.setText(message);
            
            currentToast = new Toast(context.getApplicationContext());
            currentToast.setDuration(Toast.LENGTH_SHORT);
            currentToast.setView(layout);
            currentToast.setGravity(Gravity.CENTER, 0, 0);
            currentToast.show();
        } catch (Exception e) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 取消当前 Toast
     */
    public static void cancel() {
        if (currentToast != null) {
            currentToast.cancel();
            currentToast = null;
        }
    }
}
