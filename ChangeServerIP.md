# 🔧 如何更改服务器IP地址

由于你遇到了连接 `172.16.80.60:8123` 的问题，这里提供几种方法来更改服务器地址：

## 🚀 方法1：通过代码强制修改（推荐）

在 `NetworkConfigUpdater.java` 中，你可以取消注释并修改以下代码：

```java
// 如果你想使用不同的服务器地址，可以在这里设置
// 例如：
if ("172.16.80.60".equals(currentHost)) {
    Log.d(TAG, "🔄 检测到默认IP，更新为新地址");
    ServerConfigHelper.setServerAddress(context, "你的新IP", "8123");
}
```

**具体步骤：**

1. 打开 `app/src/main/java/com/mynas/nastv/utils/NetworkConfigUpdater.java`
2. 找到 `initializeNetworkConfig` 方法中的注释代码
3. 取消注释，将 `"你的新IP"` 替换为你的实际服务器IP
4. 重新编译和安装

## 🚀 方法2：直接修改默认值

修改 `SharedPreferencesManager.java` 中的默认值：

```java
// 找到这两行，修改为你的服务器IP
private static final String DEFAULT_SERVER_HOST = "你的服务器IP";
private static final String DEFAULT_SERVER_PORT = "8123";
```

## 🚀 方法3：在SplashActivity中强制设置

在 `SplashActivity.java` 的 `onCreate` 方法中添加：

```java
// 在 NetworkConfigUpdater.initializeNetworkConfig(this); 之后添加
NetworkConfigUpdater.forceCustomServer(this, "你的服务器IP", "8123");
```

## 🔍 常用IP地址示例

```java
// 本地开发
NetworkConfigUpdater.forceLocalhost(this);

// 局域网服务器
NetworkConfigUpdater.forceCustomServer(this, "192.168.1.100", "8123");

// 自定义IP
NetworkConfigUpdater.forceCustomServer(this, "10.0.0.50", "8123");
```

## 📱 快速解决当前问题

如果你现在就想测试，最快的方法是：

1. 打开 `app/src/main/java/com/mynas/nastv/ui/SplashActivity.java`
2. 在第32行后添加一行：
   ```java
   NetworkConfigUpdater.forceCustomServer(this, "你的服务器IP", "8123");
   ```
3. 将 `"你的服务器IP"` 替换为实际可用的服务器地址
4. 重新编译安装

## 🔧 验证配置是否生效

安装运行后，查看adb日志：
```bash
adb logcat | grep "NetworkConfigUpdater\|ApiClient"
```

应该能看到类似的输出：
```
NetworkConfigUpdater: 🔧 开始初始化网络配置
NetworkConfigUpdater: 📊 当前服务器配置:
NetworkConfigUpdater:   - 主机: [你的IP]
NetworkConfigUpdater:   - 端口: 8123
ApiClient: 🔍 [DEBUG] 创建ApiService，使用BASE_URL: http://[你的IP]:8123/fnos/v/
```

这样就能确认配置已经生效了！
