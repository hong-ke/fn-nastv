# 🌐 服务器地址配置指南

这个项目已经实现了服务器地址的完全配置化，你可以轻松地更改服务器地址。

## 🔧 配置方法

### 方法1: 使用ServerConfigHelper（推荐）

```java
// 在Application或MainActivity中初始化后调用

// 设置完整服务器地址
ServerConfigHelper.setServerUrl(this, "http://192.168.1.100:8123");

// 或分别设置主机和端口
ServerConfigHelper.setServerAddress(this, "192.168.1.100", "8123");

// 查看当前配置
String info = ServerConfigHelper.getCurrentServerInfo();
Log.d("Config", info);

// 重置为默认配置
ServerConfigHelper.resetToDefault(this);
```

### 方法2: 直接使用SharedPreferencesManager

```java
// 设置主机地址
SharedPreferencesManager.setServerHost("192.168.1.100");

// 设置端口
SharedPreferencesManager.setServerPort("8123");
```

### 方法3: 快速配置

```java
// 本机调试
ServerConfigHelper.QuickConfig.setLocalhost(this);

// 局域网IP (192.168.1.XXX)
ServerConfigHelper.QuickConfig.setLAN(this, "100"); // -> 192.168.1.100

// 自定义IP
ServerConfigHelper.QuickConfig.setCustomIP(this, "10.0.0.50");

// 只改端口
ServerConfigHelper.QuickConfig.setCustomPort(this, "9000");
```

## 📱 URL构成说明

配置后的URL结构：

- **API基础地址**: `http://[HOST]:[PORT]/fnos/v/`
- **图片服务**: `http://[HOST]:[PORT]/fnos/v/api/v1/sys/img`  
- **播放服务**: `http://[HOST]:[PORT]/fnos`
- **系统API**: `http://[HOST]:[PORT]/api`

## 🔍 当前配置状态

```java
// 获取各种URL
String apiUrl = SharedPreferencesManager.getApiBaseUrl();
String imageUrl = SharedPreferencesManager.getImageServiceUrl(); 
String playUrl = SharedPreferencesManager.getPlayServiceUrl();
String sysUrl = SharedPreferencesManager.getSystemApiUrl();

// 获取服务器基础信息
String host = SharedPreferencesManager.getServerHost();
String port = SharedPreferencesManager.getServerPort();
String baseUrl = SharedPreferencesManager.getServerBaseUrl();
```

## 🚀 生产环境配置

对于不同的部署环境，你可以：

1. **开发环境**: `http://127.0.0.1:8123`
2. **测试环境**: `http://192.168.1.100:8123`  
3. **生产环境**: `http://your-server.com:8123`

## ⚙️ 配置持久化

所有配置都会自动保存在SharedPreferences中，应用重启后配置依然有效。

## 🔧 测试连接

```java
ServerConfigHelper.testServerConnection("192.168.1.100:8123", new ServerConfigHelper.ServerTestCallback() {
    @Override
    public void onSuccess(String message) {
        Log.d("Test", "✅ " + message);
    }
    
    @Override 
    public void onError(String error) {
        Log.e("Test", "❌ " + error);
    }
});
```

## 🎯 配置建议

1. **首次运行**: 会使用默认地址 `172.16.80.60:8123`
2. **开发调试**: 使用 `ServerConfigHelper.QuickConfig.setLocalhost(this)`
3. **局域网部署**: 使用 `ServerConfigHelper.setServerUrl(this, "http://你的IP:8123")`
4. **生产环境**: 配置真实的服务器域名或IP

现在你可以轻松地在不同环境间切换服务器地址，无需重新编译代码！
