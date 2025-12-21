package com.mynas.nastv.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mynas.nastv.R;
import com.mynas.nastv.model.LoginResponse;
import com.mynas.nastv.model.QrCodeResponse;
import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.network.ApiService;
import com.mynas.nastv.utils.SharedPreferencesManager;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 🔐 登录页Activity - 二维码登录
 * 对应Web项目：Login.vue
 * 功能：
 * - 显示登录二维码
 * - 轮询检查登录状态
 * - 获取FnOS服务器地址
 * - 用户登录状态管理
 */
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    
    // 登录轮询设置
    private static final int POLLING_INTERVAL = 6000; // 6秒轮询一次，减少服务器压力
    private static final int MAX_POLLING_COUNT = 50;   // 最多轮询50次(5分钟)
    
    // UI组件
    private ImageView qrCodeImageView;
    private TextView statusTextView;
    private TextView instructionTextView;
    private TextView serverInfoTextView;
    
    // 登录状态
    private String qrCode;
    private String fnOSServerUrl;
    private Handler pollingHandler;
    private Runnable pollingRunnable;
    private int pollingCount = 0;
    private boolean isLoginSuccessful = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        Log.d(TAG, "🔐 登录页Activity启动");
        
        // 🔧 初始化
        initializeViews();
        initializePollingHandler();
        startLoginProcess();
    }
    
    /**
     * 🔧 初始化视图组件
     */
    private void initializeViews() {
        qrCodeImageView = findViewById(R.id.qr_code_image_view);
        statusTextView = findViewById(R.id.status_text_view);
        instructionTextView = findViewById(R.id.instruction_text_view);
        serverInfoTextView = findViewById(R.id.server_info_text_view);
        
        // 设置默认文本
        statusTextView.setText("正在初始化登录...");
        instructionTextView.setText("请使用飞牛OS手机客户端扫描二维码登录");
        serverInfoTextView.setText("");
        
        Log.d(TAG, "✅ 视图组件初始化完成");
    }
    
    /**
     * 🔄 初始化轮询处理器
     */
    private void initializePollingHandler() {
        pollingHandler = new Handler(Looper.getMainLooper());
        
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isLoginSuccessful && pollingCount < MAX_POLLING_COUNT) {
                    pollingCount++;
                    checkLoginStatus();
                    pollingHandler.postDelayed(this, POLLING_INTERVAL);
                } else if (pollingCount >= MAX_POLLING_COUNT) {
                    Log.w(TAG, "⏰ 轮询超时，重新生成二维码");
                    onLoginTimeout();
                }
            }
        };
        
        Log.d(TAG, "✅ 轮询处理器初始化完成");
    }
    
    /**
     * 🚀 启动登录流程
     */
    private void startLoginProcess() {
        Log.d(TAG, "🚀 启动登录流程...");
        
        updateStatus("正在获取服务器信息...");
        
        // 🔄 第一步：获取FnOS服务器地址
        getFnOSServerUrl();
    }
    
    /**
     * 🌐 获取FnOS服务器地址
     */
    private void getFnOSServerUrl() {
        Log.d(TAG, "🌐 [VERSION-20250930-1] 开始获取FnOS服务器地址...");
        
        Call<ResponseBody> call = ApiClient.getApiService().getFnUrl();
        Log.d(TAG, "🔗 [DEBUG] API调用已创建，开始异步请求");
        
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        // 📝 提取响应体中的纯文本URL
                        fnOSServerUrl = response.body().string().trim();
                        
                        Log.d(TAG, "✅ [DEBUG] FnOS服务器地址获取成功: " + fnOSServerUrl);
                        updateServerInfo("服务器: " + fnOSServerUrl);
                        
                        // 🔗 设置FnOS API客户端
                        Log.d(TAG, "🔗 [DEBUG] 正在设置FnOS API客户端...");
                        ApiClient.setFnOSBaseUrl(fnOSServerUrl);
                        
                        // 💾 保存FnOS服务器URL，以便后续使用
                        SharedPreferencesManager.saveFnOSServerUrl(fnOSServerUrl);
                        Log.d(TAG, "✅ [DEBUG] FnOS API客户端设置完成并已保存");
                        
                        // 🔄 第二步：获取登录二维码
                        Log.d(TAG, "🔄 [DEBUG] 准备获取登录二维码...");
                        getQRCode();
                    } catch (Exception e) {
                        Log.e(TAG, "❌ FnOS服务器地址解析失败", e);
                        onLoginError("服务器响应解析失败: " + e.getMessage());
                    }
                } else {
                    Log.e(TAG, "❌ FnOS服务器地址获取失败，响应码: " + response.code());
                    onLoginError("无法连接到服务器，请检查网络连接");
                }
            }
            
            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e(TAG, "❌ FnOS服务器地址获取异常", t);
                onLoginError("网络连接失败: " + t.getMessage());
            }
        });
    }
    
    /**
     * 🔲 获取登录二维码
     */
    private void getQRCode() {
        Log.d(TAG, "🔲 [DEBUG] 开始获取登录二维码...");
        
        updateStatus("正在生成登录二维码...");
        
        try {
            // 🌐 使用FnOS API服务获取二维码
            Log.d(TAG, "🌐 [DEBUG] 正在获取FnOS API服务实例...");
            ApiService fnOSService = ApiClient.getFnOSApiService();
            Log.d(TAG, "✅ [DEBUG] FnOS API服务实例获取成功");
            
            Call<QrCodeResponse> call = fnOSService.getQrCode();
            Log.d(TAG, "🔗 [DEBUG] 二维码API调用已创建，开始异步请求");
            
            call.enqueue(new Callback<QrCodeResponse>() {
                @Override
                public void onResponse(Call<QrCodeResponse> call, Response<QrCodeResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        QrCodeResponse qrResponse = response.body();
                    
                    if (qrResponse.getCode() == 0) {  // 修复：服务器返回0表示成功
                        // ✅ 二维码获取成功
                        String rawCode;
                        if (qrResponse.getData() != null) {
                            rawCode = qrResponse.getData().getQrUrl();
                        } else {
                            // 🔄 使用模拟数据作为fallback
                            rawCode = "QR_CODE_" + System.currentTimeMillis();
                        }
                        
                        // 保存原始code用于轮询
                        qrCode = rawCode;
                        
                        // 生成标准的深度链接URL作为二维码内容
                        String deviceName = "NasTV-AndroidTV";
                        String qrUrl = String.format(
                            "fn://com.trim.tv/trim.media-center?platform=AndroidTV&osver=35&clientName=飞牛影视TV&code=%s&event=scanLogin&deviceName=%s",
                            rawCode, deviceName
                        );
                        
                        Log.d(TAG, "✅ [DEBUG] 二维码数据解析成功，rawCode: " + rawCode);
                        Log.d(TAG, "🔗 [DEBUG] 生成二维码URL: " + qrUrl);
                        
                        // 🖼️ 生成并显示二维码图片（使用完整URL）
                        generateQRCodeImage(qrUrl);
                        
                        // 🔄 第三步：开始轮询登录状态
                        startLoginPolling();
                    } else {
                        Log.e(TAG, "❌ 二维码获取失败，响应码: " + qrResponse.getCode() + ", 消息: " + qrResponse.getMsg());
                        onLoginError("二维码生成失败: " + qrResponse.getMsg());
                    }
                } else {
                    Log.e(TAG, "❌ 二维码请求响应异常");
                    onLoginError("二维码获取失败，请重试");
                }
            }
            
            @Override
            public void onFailure(Call<QrCodeResponse> call, Throwable t) {
                Log.e(TAG, "❌ 二维码获取网络异常", t);
                onLoginError("网络异常: " + t.getMessage());
            }
        });
        } catch (Exception e) {
            Log.e(TAG, "❌ [DEBUG] FnOS API服务获取失败", e);
            onLoginError("API服务错误: " + e.getMessage());
        }
    }
    
    /**
     * 🖼️ 生成二维码图片
     */
    private void generateQRCodeImage(String content) {
        try {
            Log.d(TAG, "🔗 [DEBUG] 准备生成二维码，内容: " + content);
            
            QRCodeWriter writer = new QRCodeWriter();
            // 增加尺寸提高清晰度
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 400, 400);
            
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            // 使用ARGB_8888提高图像质量
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            
            // 优化像素设置，确保高对比度
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            
            runOnUiThread(() -> {
                qrCodeImageView.setImageBitmap(bitmap);
                updateStatus("请使用飞牛OS手机客户端扫描二维码");
                Log.d(TAG, "✅ 二维码图片生成完成，尺寸: " + width + "x" + height);
            });
            
        } catch (WriterException e) {
            Log.e(TAG, "❌ 二维码图片生成失败", e);
            onLoginError("二维码图片生成失败");
        }
    }
    
    /**
     * 🔄 开始登录状态轮询
     */
    private void startLoginPolling() {
        Log.d(TAG, "🔄 开始登录状态轮询...");
        
        pollingCount = 0;
        isLoginSuccessful = false;
        
        // 立即检查一次，然后开始定时轮询
        checkLoginStatus();
        pollingHandler.postDelayed(pollingRunnable, POLLING_INTERVAL);
    }
    
    /**
     * ✅ 检查登录状态
     */
    private void checkLoginStatus() {
        if (qrCode == null) return;
        
        Log.d(TAG, "✅ 检查登录状态 (第" + pollingCount + "次)...");
        
        // 🌐 使用FnOS API服务检查登录状态
        Log.d(TAG, "🌐 [DEBUG] 使用FnOS API服务检查登录状态...");
        Call<LoginResponse> call = ApiClient.getFnOSApiService().checkQrLogin(qrCode);
        call.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();
                    
                    if (loginResponse.getCode() == 0) {  // API调用成功
                        // 检查实际的登录状态
                        String status = loginResponse.getData() != null ? loginResponse.getData().getStatus() : null;
                        Log.d(TAG, "🔍 [DEBUG] 登录状态检查，status: " + status);
                        
                        if ("Success".equalsIgnoreCase(status)) {
                            // ✅ 用户扫码成功，真正登录
                            Log.d(TAG, "✅ [DEBUG] 用户扫码登录成功！");
                            onLoginSuccess(loginResponse);
                        } else if ("Pending".equalsIgnoreCase(status)) {
                            // 🔄 继续等待用户扫码
                            Log.d(TAG, "🔄 等待用户扫码中...");
                            updateStatus("等待用户扫码 (" + pollingCount + "/" + MAX_POLLING_COUNT + ")");
                        } else if ("Expired".equalsIgnoreCase(status)) {
                            // ⏰ 二维码已过期
                            Log.w(TAG, "⏰ 二维码已过期，重新生成");
                            onLoginError("二维码已过期，请重新扫码");
                        } else {
                            // 🔄 其他状态，继续轮询
                            Log.d(TAG, "🔄 未知状态: " + status + "，继续轮询...");
                            updateStatus("等待用户扫码 (" + pollingCount + "/" + MAX_POLLING_COUNT + ")");
                        }
                    } else {
                        // 🔄 API调用失败，继续轮询
                        Log.d(TAG, "🔄 API调用失败，状态码: " + loginResponse.getCode() + "，继续轮询...");
                        updateStatus("等待用户扫码 (" + pollingCount + "/" + MAX_POLLING_COUNT + ")");
                    }
                } else {
                    Log.w(TAG, "⚠️ 登录状态检查响应异常");
                }
            }
            
            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Log.w(TAG, "⚠️ 登录状态检查网络异常", t);
            }
        });
    }
    
    /**
     * ✅ 登录成功处理
     */
    private void onLoginSuccess(LoginResponse response) {
        isLoginSuccessful = true;
        pollingHandler.removeCallbacks(pollingRunnable);
        
        Log.d(TAG, "✅ 用户登录成功！");
        
        // 💾 保存认证信息
        String token = response.getToken();
        if (token != null) {
            SharedPreferencesManager.saveAuthToken(token);
            Log.d(TAG, "💾 认证Token已保存");
        }
        
        updateStatus("登录成功！正在进入应用...");
        
        // 🔄 延迟跳转到主页
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            
            // 🎨 添加过渡动画
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 1000);
    }
    
    /**
     * ❌ 登录错误处理
     */
    private void onLoginError(String errorMessage) {
        runOnUiThread(() -> {
            updateStatus("登录失败: " + errorMessage);
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
            
            // 🔄 显示重试按钮或自动重试
            showRetryOption();
        });
    }
    
    /**
     * ⏰ 登录超时处理
     */
    private void onLoginTimeout() {
        runOnUiThread(() -> {
            updateStatus("登录超时，请重新扫码");
            Toast.makeText(this, "登录超时，正在重新生成二维码...", Toast.LENGTH_SHORT).show();
            
            // 🔄 重新开始登录流程
            startLoginProcess();
        });
    }
    
    /**
     * 🔄 显示重试选项
     */
    private void showRetryOption() {
        // TODO: 实现重试按钮或自动重试逻辑
        updateStatus("按确认键重试");
    }
    
    /**
     * 🎮 Android TV遥控器按键处理
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "🎮 遥控器按键: " + keyCode);
        
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                // 确认键 -> 重试登录
                if (!isLoginSuccessful) {
                    startLoginProcess();
                }
                return true;
                
            case KeyEvent.KEYCODE_BACK:
                // 返回键 -> 退出应用
                finish();
                return true;
        }
        
        return super.onKeyDown(keyCode, event);
    }
    
    // 🔧 UI更新辅助方法
    private void updateStatus(String status) {
        runOnUiThread(() -> {
            statusTextView.setText(status);
            Log.d(TAG, "📱 状态更新: " + status);
        });
    }
    
    private void updateServerInfo(String serverInfo) {
        runOnUiThread(() -> {
            serverInfoTextView.setText(serverInfo);
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 🔄 清理轮询任务
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
        
        Log.d(TAG, "🔄 登录页Activity销毁，清理轮询任务");
    }
}
