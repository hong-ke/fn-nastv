package com.mynas.nastv.manager;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.mynas.nastv.model.BaseResponse;
import com.mynas.nastv.model.LoginRequest;
import com.mynas.nastv.model.LoginResponse;
import com.mynas.nastv.model.QrCodeResponse;
import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.ui.LoginActivity;
import com.mynas.nastv.utils.SharedPreferencesManager;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 🔐 认证管理器
 * 负责用户登录、注销、Token管理等认证相关功能
 */
public class AuthManager {
    private static final String TAG = "AuthManager";
    private static AuthManager instance;
    private Context context;
    
    private AuthManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }
    
    /**
     * 🔍 检查登录状态
     */
    public boolean isLoggedIn() {
        String token = SharedPreferencesManager.getAuthToken();
        boolean loggedIn = token != null && !token.isEmpty();
        Log.d(TAG, "🔍 检查登录状态: " + (loggedIn ? "已登录" : "未登录"));
        return loggedIn;
    }
    
    /**
     * 💾 保存用户认证信息
     */
    public void saveAuthInfo(String token, String userInfo) {
        SharedPreferencesManager.saveAuthToken(token);
        if (userInfo != null) {
            SharedPreferencesManager.putString("user_info", userInfo);
        }
        Log.d(TAG, "💾 用户认证信息已保存");
    }
    
    /**
     * 🚪 退出登录
     */
    public void logout() {
        // 🗑️ 清除本地认证信息
        SharedPreferencesManager.clearAuthInfo();
        
        // 📱 跳转到登录页面
        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        
        Log.d(TAG, "🚪 用户已退出登录");
    }
    
    /**
     * 🔑 获取二维码登录码
     */
    public void getQrCode(AuthCallback<QrCodeResponse> callback) {
        Log.d(TAG, "🔑 开始获取二维码登录码...");
        
        ApiClient.getApiService().getQrCode().enqueue(new Callback<QrCodeResponse>() {
            @Override
            public void onResponse(@NonNull Call<QrCodeResponse> call, @NonNull Response<QrCodeResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "✅ 二维码获取成功");
                    callback.onSuccess(response.body());
                } else {
                    String error = "二维码获取失败: " + response.message();
                    Log.e(TAG, "❌ " + error);
                    callback.onError(error);
                }
            }
            
            @Override
            public void onFailure(@NonNull Call<QrCodeResponse> call, @NonNull Throwable t) {
                String error = "网络请求失败: " + t.getMessage();
                Log.e(TAG, "❌ " + error, t);
                callback.onError(error);
            }
        });
    }
    
    /**
     * 🔄 检查二维码登录状态
     */
    public void checkQrLogin(String code, AuthCallback<LoginResponse> callback) {
        Log.d(TAG, "🔄 检查二维码登录状态: " + code);
        
        ApiClient.getApiService().checkQrLogin(code).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponse> call, @NonNull Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();
                    if (loginResponse.getCode() == 0 && loginResponse.getToken() != null) {
                        // 🎉 登录成功
                        saveAuthInfo(loginResponse.getToken(), loginResponse.getUserInfo());
                        Log.d(TAG, "🎉 二维码登录成功");
                        callback.onSuccess(loginResponse);
                    } else {
                        // ⏳ 等待扫码或其他状态
                        Log.d(TAG, "⏳ 二维码登录状态: " + loginResponse.getMsg());
                        callback.onPending(loginResponse.getMsg());
                    }
                } else {
                    String error = "登录检查失败: " + response.message();
                    Log.e(TAG, "❌ " + error);
                    callback.onError(error);
                }
            }
            
            @Override
            public void onFailure(@NonNull Call<LoginResponse> call, @NonNull Throwable t) {
                String error = "网络请求失败: " + t.getMessage();
                Log.e(TAG, "❌ " + error, t);
                callback.onError(error);
            }
        });
    }
    
    /**
     * 🔐 账号密码登录
     */
    public void login(String username, String password, AuthCallback<LoginResponse> callback) {
        Log.d(TAG, "🔐 开始账号密码登录: " + username);
        
        LoginRequest request = new LoginRequest(username, password);
        ApiClient.getApiService().login("", request).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponse> call, @NonNull Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();
                    if (loginResponse.getCode() == 0 && loginResponse.getToken() != null) {
                        // 🎉 登录成功
                        saveAuthInfo(loginResponse.getToken(), loginResponse.getUserInfo());
                        Log.d(TAG, "🎉 账号密码登录成功");
                        callback.onSuccess(loginResponse);
                    } else {
                        String error = loginResponse.getMsg() != null ? loginResponse.getMsg() : "登录失败";
                        Log.e(TAG, "❌ " + error);
                        callback.onError(error);
                    }
                } else {
                    String error = "登录失败: " + response.message();
                    Log.e(TAG, "❌ " + error);
                    callback.onError(error);
                }
            }
            
            @Override
            public void onFailure(@NonNull Call<LoginResponse> call, @NonNull Throwable t) {
                String error = "网络请求失败: " + t.getMessage();
                Log.e(TAG, "❌ " + error, t);
                callback.onError(error);
            }
        });
    }
    
    /**
     * 🌐 获取后端地址
     */
    public void getFnUrl(AuthCallback<String> callback) {
        Log.d(TAG, "🌐 获取后端地址...");
        
        ApiClient.getApiService().getFnUrl().enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        // 📝 提取响应体中的纯文本URL
                        String fnUrl = response.body().string().trim();
                        Log.d(TAG, "✅ 后端地址获取成功: " + fnUrl);
                        callback.onSuccess(fnUrl);
                    } catch (Exception e) {
                        String error = "后端地址解析失败: " + e.getMessage();
                        Log.e(TAG, "❌ " + error, e);
                        callback.onError(error);
                    }
                } else {
                    String error = "后端地址获取失败: " + response.message();
                    Log.e(TAG, "❌ " + error);
                    callback.onError(error);
                }
            }
            
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                String error = "网络请求失败: " + t.getMessage();
                Log.e(TAG, "❌ " + error, t);
                callback.onError(error);
            }
        });
    }
    
    /**
     * 🔄 获取当前用户信息
     */
    public String getCurrentUserInfo() {
        return SharedPreferencesManager.getString("user_info", null);
    }
    
    /**
     * 🔄 获取当前Token
     */
    public String getCurrentToken() {
        return SharedPreferencesManager.getAuthToken();
    }
    
    /**
     * 认证回调接口
     */
    public interface AuthCallback<T> {
        void onSuccess(T data);
        void onError(String error);
        
        default void onPending(String message) {
            // 默认实现，二维码登录等待状态
        }
    }
}
