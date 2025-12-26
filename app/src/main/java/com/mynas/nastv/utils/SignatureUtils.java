package com.mynas.nastv.utils;

import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;

/**
 * API签名工具类
 * 完全复用Web项目(fntv-electron)的签名算法，生成authx头部
 * 
 * Web端签名算法 (request.ts):
 * signArray = [api_key, url, nonce, timestamp, dataJsonMd5, api_secret]
 * signStr = signArray.join('_')
 * authx = `nonce=${nonce}&timestamp=${timestamp}&sign=${md5(signStr)}`
 */
public class SignatureUtils {
    private static final String TAG = "SignatureUtils";
    
    // API密钥，与Web项目(fntv-electron)保持一致
    private static final String API_KEY = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";      // api_key
    private static final String API_SECRET = "16CCEB3D-AB42-077D-36A1-F355324E4237"; // api_secret
    
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
            Log.e(TAG, "签名生成失败", e);
            return null;
        }
    }
    
    /**
     * 生成API签名 - 使用与Web项目(fntv-electron)完全相同的算法
     * 
     * Web端算法 (request.ts genFnAuthx):
     * 1. nonce = 随机6位数字
     * 2. timestamp = Date.now()
     * 3. dataJsonMd5 = md5(JSON.stringify(data) || '')
     * 4. signArray = [api_key, url, nonce, timestamp, dataJsonMd5, api_secret]
     * 5. signStr = signArray.join('_')
     * 6. authx = `nonce=${nonce}&timestamp=${timestamp}&sign=${md5(signStr)}`
     * 
     * @param method HTTP方法
     * @param url 请求路径 (如 /v/api/v1/stream)
     * @param data 请求体JSON字符串
     * @param params 查询参数 (GET请求用)
     * @return 签名字符串
     */
    public static String generateSignature(String method, String url, String data, Map<String, String> params) {
        try {
            // 确保URL是正确的路径格式
            String path = url;
            if (path.startsWith("http")) {
                path = extractPath(path);
            }
            
            // 生成随机数和时间戳 (与Web端一致)
            String nonce = String.format("%06d", (int)(Math.random() * 900000) + 100000);
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            // 计算数据MD5 - 直接对JSON字符串计算MD5，与Web端一致
            // Web端: const dataJsonMd5 = getMd5(dataJson);
            // 其中 dataJson = data ? JSON.stringify(data) : ''
            String dataJson = (data != null && !data.isEmpty()) ? data : "";
            String dataJsonMd5 = md5(dataJson);
            
            // 构建签名数组 (与Web端完全一致)
            // Web端: const signArray = [api_key, url, nonce, timestamp, dataJsonMd5, api_secret]
            String signStr = API_KEY + "_" + path + "_" + nonce + "_" + timestamp + "_" + dataJsonMd5 + "_" + API_SECRET;
            
            // 生成最终签名
            String sign = md5(signStr);
            
            // 构建最终返回格式: nonce=123456&timestamp=1696080000000&sign=md5hash
            String finalSignature = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;
            
            Log.d(TAG, "签名生成成功:");
            Log.d(TAG, "   路径: " + path);
            Log.d(TAG, "   数据JSON: " + (dataJson.length() > 100 ? dataJson.substring(0, 100) + "..." : dataJson));
            Log.d(TAG, "   数据MD5: " + dataJsonMd5);
            Log.d(TAG, "   签名字符串: " + signStr.substring(0, Math.min(80, signStr.length())) + "...");
            Log.d(TAG, "   最终签名: " + finalSignature);
            
            return finalSignature;
            
        } catch (Exception e) {
            Log.e(TAG, "签名生成异常", e);
            return null;
        }
    }
    
    /**
     * 提取URL路径部分
     * 从完整URL中提取 /v/api/... 格式的路径
     */
    private static String extractPath(String fullUrl) {
        try {
            String path = "";
            
            // 查找 /v/api/ 开头的路径
            int vApiIndex = fullUrl.indexOf("/v/api/");
            if (vApiIndex >= 0) {
                path = fullUrl.substring(vApiIndex);
            } else {
                // 其他情况，尝试提取域名后的路径
                int pathStart = fullUrl.indexOf('/', 8); // 跳过 http:// 或 https://
                if (pathStart >= 0) {
                    path = fullUrl.substring(pathStart);
                }
            }
            
            // 移除查询参数
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) {
                path = path.substring(0, queryIndex);
            }
            
            Log.d(TAG, "提取路径: " + fullUrl + " -> " + path);
            return path;
            
        } catch (Exception e) {
            Log.e(TAG, "URL路径提取失败", e);
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
            Log.e(TAG, "查询参数提取失败", e);
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
            Log.e(TAG, "请求体读取失败", e);
            return "";
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
            Log.e(TAG, "MD5加密失败", e);
            return "";
        }
    }
}
