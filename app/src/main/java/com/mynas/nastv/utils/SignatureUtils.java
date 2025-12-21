package com.mynas.nastv.utils;

import android.util.Log;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;

/**
 * 🔑 API签名工具类
 * 完全复用Web项目的签名算法，生成authx头部
 */
public class SignatureUtils {
    private static final String TAG = "SignatureUtils";
    
    // 🔑 API密钥，与Web项目保持一致
    private static final String API_KEY = "16CCEB3D-AB42-077D-36A1-F355324E4237";
    private static final String SIGNATURE_PREFIX = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
    
    /**
     * 为请求生成API签名
     * @param request HTTP请求对象
     * @return 签名字符串，失败返回null
     */
    public static String generateSignature(Request request) {
        try {
            String method = request.method();
            String url = extractPath(request.url().toString());
            String data = getRequestBody(request);
            Map<String, String> params = extractQueryParams(request.url().toString());
            
            return generateSignature(method, url, data, params);
        } catch (Exception e) {
            Log.e(TAG, "❌ 签名生成失败", e);
            return null;
        }
    }
    
    /**
     * 生成API签名 - 使用与Web项目完全相同的算法
     * @param method HTTP方法
     * @param url 请求路径
     * @param data 请求体数据
     * @param params 查询参数
     * @return 签名字符串
     */
    public static String generateSignature(String method, String url, String data, Map<String, String> params) {
        try {
            // 🔧 解析URL路径 (模拟Web项目的parseUrl)
            String path = url;
            if (path.startsWith("http")) {
                // 如果是完整URL，提取路径部分
                path = extractPath(path);
            }
            
            // 📝 处理数据和参数 (模拟Web项目逻辑)
            String requestData = "";
            boolean isGet = "GET".equalsIgnoreCase(method);
            
            if (isGet) {
                // GET请求：使用参数
                if (params != null && !params.isEmpty()) {
                    StringBuilder paramString = new StringBuilder();
                    TreeMap<String, String> sortedParams = new TreeMap<>(params);
                    for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
                        if (paramString.length() > 0) {
                            paramString.append("&");
                        }
                        paramString.append(entry.getKey()).append("=").append(entry.getValue());
                    }
                    requestData = paramString.toString();
                }
            } else {
                // 非GET请求：使用请求体数据 (模拟Web项目逻辑)
                if (data != null && !data.isEmpty()) {
                    requestData = data;
                } else {
                    requestData = ""; // Web项目对空数据使用空字符串，不是{}
                }
            }
            
            // 🔐 计算数据哈希 - 完全模拟Web项目的hashSignatureData函数
            String dataHash = hashSignatureData(requestData);
            
            // 🎲 生成随机数和时间戳
            String nonce = String.format("%06d", (int)(Math.random() * 900000) + 100000);
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            // 🔗 构建签名字符串 (模拟Web项目逻辑)
            String signatureString = SIGNATURE_PREFIX + "_" + path + "_" + nonce + "_" + timestamp + "_" + dataHash + "_" + API_KEY;
            
            // 🔐 生成最终签名
            String sign = md5(signatureString);
            
            // 📋 构建最终返回格式: nonce=123456&timestamp=1696080000000&sign=md5hash
            String finalSignature = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;
            
            Log.d(TAG, "🔑 签名生成成功:");
            Log.d(TAG, "   方法: " + method);
            Log.d(TAG, "   路径: " + path);
            Log.d(TAG, "   数据: " + requestData);
            Log.d(TAG, "   数据哈希: " + dataHash);
            Log.d(TAG, "   随机数: " + nonce);
            Log.d(TAG, "   时间戳: " + timestamp);
            Log.d(TAG, "   签名字符串: " + signatureString.substring(0, Math.min(100, signatureString.length())) + "...");
            Log.d(TAG, "   最终签名: " + finalSignature.substring(0, Math.min(50, finalSignature.length())) + "...");
            
            return finalSignature;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 签名生成异常", e);
            return null;
        }
    }
    
    /**
     * 提取URL路径部分
     */
    private static String extractPath(String fullUrl) {
        try {
            // 🔧 处理不同的URL格式
            String path = "";
            
            // 1. 对于 /fnos/v/ 开头的URL，按照Web项目逻辑提取/v开头的路径
            String basePath = "/fnos/v";
            int baseIndex = fullUrl.indexOf(basePath);
            if (baseIndex >= 0) {
                // Web项目逻辑：url = "/v" + config.url.split('?')[0]
                // 所以从/fnos/v/api/v1/mediadb/list 提取出 /v/api/v1/mediadb/list
                path = "/v" + fullUrl.substring(baseIndex + basePath.length());
            } else {
                // 2. 对于飞牛服务器URL（包含/v/api/），提取/v开头的路径
                int vApiIndex = fullUrl.indexOf("/v/api/");
                if (vApiIndex >= 0) {
                    path = fullUrl.substring(vApiIndex);
                } else {
                    // 3. 对于其他 /api/ 开头的URL，直接提取路径
                    int apiIndex = fullUrl.indexOf("/api/");
                    if (apiIndex >= 0) {
                        path = fullUrl.substring(apiIndex);
                    } else {
                        // 4. 其他情况，尝试提取域名后的路径
                        int pathStart = fullUrl.indexOf('/', 8); // 跳过 http:// 或 https://
                        if (pathStart >= 0) {
                            path = fullUrl.substring(pathStart);
                        }
                    }
                }
            }
            
            // 📝 移除查询参数
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) {
                path = path.substring(0, queryIndex);
            }
            
            Log.d(TAG, "🔧 提取路径: " + fullUrl + " -> " + path);
            return path;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ URL路径提取失败", e);
            return "";
        }
    }
    
    /**
     * 提取查询参数
     */
    private static Map<String, String> extractQueryParams(String fullUrl) {
        Map<String, String> params = new TreeMap<>();
        try {
            int queryIndex = fullUrl.indexOf('?');
            if (queryIndex >= 0) {
                String queryString = fullUrl.substring(queryIndex + 1);
                String[] pairs = queryString.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        params.put(keyValue[0], keyValue[1]);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ 查询参数提取失败", e);
        }
        return params;
    }
    
    /**
     * 获取请求体内容
     */
    private static String getRequestBody(Request request) {
        try {
            RequestBody body = request.body();
            if (body == null) {
                return "";
            }
            
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            return buffer.readUtf8();
        } catch (IOException e) {
            Log.e(TAG, "❌ 请求体读取失败", e);
            return "";
        }
    }
    
    /**
     * 数据签名哈希 - 完全模拟Web项目的hashSignatureData函数
     * 先进行URL解码处理，然后计算MD5
     */
    private static String hashSignatureData(String data) {
        try {
            if (data == null || data.isEmpty()) {
                return md5("");
            }
            
            // 模拟Web项目的URL解码处理
            // const s = o.replace(/%(?![0-9A-Fa-f]{2})/g, "%25")
            // const a = decodeURIComponent(s);
            String processed = data.replaceAll("%(?![0-9A-Fa-f]{2})", "%25");
            
            try {
                // 尝试URL解码
                String decoded = URLDecoder.decode(processed, StandardCharsets.UTF_8.toString());
                return md5(decoded);
            } catch (Exception e) {
                // 解码失败时直接MD5原始数据 (模拟Web项目的catch逻辑)
                Log.w(TAG, "⚠️ URL解码失败，使用原始数据: " + e.getMessage());
                return md5(data);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ hashSignatureData处理失败", e);
            return md5(data);
        }
    }
    
    /**
     * MD5加密
     */
    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "❌ MD5加密失败", e);
            return "";
        }
    }
}
