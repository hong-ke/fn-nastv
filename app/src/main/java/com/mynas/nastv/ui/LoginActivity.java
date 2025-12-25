package com.mynas.nastv.ui;

import com.mynas.nastv.utils.ToastUtils;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.mynas.nastv.R;
import com.mynas.nastv.config.AppConfig;
import com.mynas.nastv.model.LoginRequest;
import com.mynas.nastv.model.LoginResponse;
import com.mynas.nastv.network.ApiClient;
import com.mynas.nastv.utils.SharedPreferencesManager;
import com.mynas.nastv.utils.SignatureUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 🔐 登录页Activity - 用户名密码登录
 * 参考 fntv-electron 实现
 */
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    
    // UI组件
    private EditText serverEditText;
    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private ProgressBar progressBar;
    private TextView statusTextView;
    private TextView serverHintTextView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        Log.d(TAG, "🔐 登录页Activity启动");
        
        initializeViews();
        loadSavedConfig();
    }
    
    /**
     * 🔧 初始化视图组件
     */
    private void initializeViews() {
        serverEditText = findViewById(R.id.server_edit_text);
        usernameEditText = findViewById(R.id.username_edit_text);
        passwordEditText = findViewById(R.id.password_edit_text);
        loginButton = findViewById(R.id.login_button);
        progressBar = findViewById(R.id.progress_bar);
        statusTextView = findViewById(R.id.status_text_view);
        serverHintTextView = findViewById(R.id.server_hint_text_view);
        
        // 设置默认服务器地址
        String defaultServer = AppConfig.SERVER_IP + ":" + AppConfig.SERVER_PORT;
        serverEditText.setHint(defaultServer);
        serverHintTextView.setText("例如: " + defaultServer);
        
        // 登录按钮点击事件
        loginButton.setOnClickListener(v -> attemptLogin());
        
        // 密码框回车键登录
        passwordEditText.setOnEditorActionListener((v, actionId, event) -> {
            attemptLogin();
            return true;
        });
        
        Log.d(TAG, "✅ 视图组件初始化完成");
    }
    
    /**
     * 📂 加载保存的配置
     */
    private void loadSavedConfig() {
        // 加载上次使用的服务器地址
        String savedHost = SharedPreferencesManager.getServerHost();
        String savedPort = SharedPreferencesManager.getServerPort();
        if (savedHost != null && !savedHost.isEmpty()) {
            serverEditText.setText(savedHost + ":" + savedPort);
        }
        
        // 加载上次使用的用户名
        String savedUsername = SharedPreferencesManager.getLastUsername();
        if (savedUsername != null && !savedUsername.isEmpty()) {
            usernameEditText.setText(savedUsername);
            passwordEditText.requestFocus();
        }
    }
    
    /**
     * 🔐 尝试登录
     */
    private void attemptLogin() {
        // 获取输入
        String server = serverEditText.getText().toString().trim();
        String username = usernameEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        
        // 验证输入
        if (TextUtils.isEmpty(server)) {
            // 使用默认服务器
            server = AppConfig.SERVER_IP + ":" + AppConfig.SERVER_PORT;
        }
        
        if (TextUtils.isEmpty(username)) {
            usernameEditText.setError("请输入用户名");
            usernameEditText.requestFocus();
            return;
        }
        
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("请输入密码");
            passwordEditText.requestFocus();
            return;
        }
        
        // 解析服务器地址
        String host;
        String port;
        if (server.contains(":")) {
            String[] parts = server.split(":");
            host = parts[0];
            port = parts.length > 1 ? parts[1] : AppConfig.SERVER_PORT;
        } else {
            host = server;
            port = AppConfig.SERVER_PORT;
        }
        
        // 保存服务器配置
        SharedPreferencesManager.setServerHost(host);
        SharedPreferencesManager.setServerPort(port);
        
        // 重新初始化 ApiClient
        ApiClient.initialize(this);
        
        // 执行登录
        performLogin(username, password);
    }
    
    /**
     * 🌐 执行登录请求
     */
    private void performLogin(String username, String password) {
        Log.d(TAG, "🔐 开始登录: " + username);
        
        showLoading(true);
        updateStatus("正在登录...");
        
        // 构建登录请求
        LoginRequest request = new LoginRequest();
        request.setAppName(AppConfig.APP_NAME);
        request.setUsername(username);
        request.setPassword(password);
        
        // 将请求转换为JSON字符串用于签名
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String requestJson = gson.toJson(request);
        
        // 生成签名
        String signature = SignatureUtils.generateSignature("POST", "/v/api/v1/login", requestJson, null);
        
        // 发起登录请求
        Call<LoginResponse> call = ApiClient.getApiService().login(signature, request);
        call.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                showLoading(false);
                
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();
                    
                    if (loginResponse.getCode() == 0 && loginResponse.getData() != null) {
                        // 登录成功
                        String token = loginResponse.getData().getToken();
                        if (token != null && !token.isEmpty()) {
                            onLoginSuccess(token, username);
                        } else {
                            onLoginError("登录失败: 未获取到Token");
                        }
                    } else {
                        String msg = loginResponse.getMsg();
                        if (msg == null || msg.isEmpty()) {
                            msg = "登录失败，请检查用户名和密码";
                        }
                        onLoginError(msg);
                    }
                } else {
                    String errorMsg = "服务器响应错误: " + response.code();
                    try {
                        if (response.errorBody() != null) {
                            errorMsg += " - " + response.errorBody().string();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    onLoginError(errorMsg);
                }
            }
            
            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                showLoading(false);
                Log.e(TAG, "❌ 登录请求失败", t);
                onLoginError("网络连接失败: " + t.getMessage());
            }
        });
    }
    
    /**
     * ✅ 登录成功处理
     */
    private void onLoginSuccess(String token, String username) {
        Log.d(TAG, "✅ 登录成功！");
        
        // 保存认证信息
        SharedPreferencesManager.saveAuthToken(token);
        SharedPreferencesManager.saveLastUsername(username);
        
        updateStatus("登录成功！正在进入应用...");
        
        // 延迟跳转到主页
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            
            // 添加过渡动画
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 500);
    }
    
    /**
     * ❌ 登录错误处理
     */
    private void onLoginError(String errorMessage) {
        Log.e(TAG, "❌ 登录失败: " + errorMessage);
        
        runOnUiThread(() -> {
            updateStatus("登录失败: " + errorMessage);
            ToastUtils.show(this, errorMessage);
        });
    }
    
    /**
     * 🔄 显示/隐藏加载状态
     */
    private void showLoading(boolean show) {
        runOnUiThread(() -> {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            loginButton.setEnabled(!show);
            serverEditText.setEnabled(!show);
            usernameEditText.setEnabled(!show);
            passwordEditText.setEnabled(!show);
        });
    }
    
    /**
     * 📝 更新状态文本
     */
    private void updateStatus(String status) {
        runOnUiThread(() -> {
            statusTextView.setText(status);
            Log.d(TAG, "📱 状态更新: " + status);
        });
    }
    
    /**
     * 🎮 Android TV遥控器按键处理
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 返回键退出应用
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
