package top.galqq.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.XposedBridge;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import top.galqq.hook.CookieHookManager;

/**
 * QQ 空间亲密度排行 API 客户端
 * 负责从 Close Rank API 获取好感度数据
 */
public class CloseRankClient {

    private static final String TAG = "GalQQ.CloseRankClient";
    
    private static void debugLog(String message) {
        try {
            if (top.galqq.config.ConfigManager.isVerboseLogEnabled()) {
                XposedBridge.log(message);
            }
        } catch (Throwable ignored) {}
    }
    
    // 调试模式：保存请求和响应到下载目录
    private boolean mDebugMode = false;
    
    // API URL
    private static final String API_URL = "https://h5.qzone.qq.com/close/rank";
    
    // 请求类型
    public static final int TYPE_WHO_I_CARE = 1;      // 我在意谁
    public static final int TYPE_WHO_CARES_ME = 2;    // 谁在意我
    
    // User-Agent（使用通用设备型号）
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 6 Build/TQ3A.230901.001; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/121.0.6167.71 " +
            "MQQBrowser/6.2 TBS/047921 Mobile Safari/537.36 " +
            "V1_AND_SQ_9.0.56_5178_YYB_D QQ/9.0.56.15640 NetType/WIFI WebP/0.3.0 " +
            "AppId/537327451 Pixel/1080 StatusBarHeight/100 SimpleUISwitch/1 QQTheme/2920";
    
    // HTML 解析正则表达式
    // 实际HTML结构: <li class="list-item b-bor j-item" data-num='1' data-uin="3356972318" data-care="0">
    //               <span class="name">老橘</span>
    //               <span class="degree">79</span>
    private static final Pattern PATTERN_LIST_ITEM = Pattern.compile(
            "<li[^>]*data-uin=\"(\\d+)\"[^>]*>.*?<span[^>]*class=\"degree\"[^>]*>(\\d+)</span>",
            Pattern.DOTALL
    );
    
    // 备用正则：更宽松的匹配（分两步提取）
    private static final Pattern PATTERN_UIN_DEGREE = Pattern.compile(
            "data-uin=\"(\\d+)\".*?<span[^>]*class=\"degree\"[^>]*>(\\d+)</span>",
            Pattern.DOTALL
    );
    
    // 按类型提取列表的正则
    // 匹配 <ul class="rank-list j-rank-list" data-type="X">...</ul>
    private static final Pattern PATTERN_RANK_LIST_TYPE1 = Pattern.compile(
            "<ul[^>]*class=\"rank-list[^\"]*\"[^>]*data-type=\"1\"[^>]*>(.*?)</ul>",
            Pattern.DOTALL
    );
    
    private static final Pattern PATTERN_RANK_LIST_TYPE2 = Pattern.compile(
            "<ul[^>]*class=\"rank-list[^\"]*\"[^>]*data-type=\"2\"[^>]*>(.*?)</ul>",
            Pattern.DOTALL
    );

    private OkHttpClient mClient;
    private Handler mMainHandler;

    public interface RankCallback {
        void onSuccess(Map<String, Integer> uinToScore);
        void onFailure(Exception e);
    }
    
    /**
     * 双向好感度回调接口
     */
    public interface BothRankCallback {
        void onSuccess(Map<String, Integer> whoICare, Map<String, Integer> whoCaresMe);
        void onFailure(Exception e);
    }

    public CloseRankClient() {
        mClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 设置调试模式
     * @param debug 是否启用调试模式（保存请求和响应到下载目录）
     */
    public void setDebugMode(boolean debug) {
        mDebugMode = debug;
    }
    
    /**
     * 是否处于调试模式
     */
    public boolean isDebugMode() {
        return mDebugMode;
    }

    public void fetchWhoCaresMe(Context context, RankCallback callback) {
        fetchRankData(context, TYPE_WHO_CARES_ME, callback);
    }

    public void fetchWhoICare(Context context, RankCallback callback) {
        fetchRankData(context, TYPE_WHO_I_CARE, callback);
    }
    
    /**
     * 一次请求获取双向好感度数据
     * 直接访问 https://h5.qzone.qq.com/close/rank（不带 type 参数）
     * 返回的 HTML 同时包含"我在意谁"和"谁在意我"两个列表
     */
    public void fetchBothRankData(Context context, BothRankCallback callback) {
        if (!CookieHelper.isCookiesAvailable(context)) {
            String errorMsg = "获取Cookie失败";
            CookieHelper.CookieSource source = CookieHelper.getLastCookieSource();
            
            debugLog(TAG + ": Cookie不可用 - 来源: " + source);
            debugLog(TAG + ": Cookie状态: " + CookieHookManager.getCookieSource());
            
            if (source == CookieHelper.CookieSource.FAILED) {
                errorMsg = "获取Cookie失败，请确保已登录QQ并且模块已激活";
            }
            
            String finalErrorMsg = errorMsg;
            if (callback != null) {
                mMainHandler.post(() -> callback.onFailure(new Exception(finalErrorMsg)));
            }
            return;
        }
        
        Request request = buildRequestWithoutType(context);
        if (request == null) {
            if (callback != null) {
                mMainHandler.post(() -> callback.onFailure(new Exception("构建请求失败")));
            }
            return;
        }
        
        mClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                debugLog(TAG + ": 请求失败: " + e.getMessage());
                if (callback != null) {
                    mMainHandler.post(() -> callback.onFailure(e));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP 错误: " + response.code());
                    }
                    
                    String html = response.body().string();
                    
                    // 【调试模式】保存响应
                    if (mDebugMode) {
                        saveDebugResponse(context, 0, html, request);
                    }
                    
                    // 解析双向数据
                    Map<String, Integer> whoICare = parseHtmlResponseByType(html, TYPE_WHO_I_CARE);
                    Map<String, Integer> whoCaresMe = parseHtmlResponseByType(html, TYPE_WHO_CARES_ME);
                    
                    if (callback != null) {
                        mMainHandler.post(() -> callback.onSuccess(whoICare, whoCaresMe));
                    }
                    
                } catch (Exception e) {
                    debugLog(TAG + ": 处理响应失败: " + e.getMessage());
                    if (callback != null) {
                        mMainHandler.post(() -> callback.onFailure(e));
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    private void fetchRankData(Context context, int type, RankCallback callback) {
        if (!CookieHelper.isCookiesAvailable(context)) {
            String errorMsg = "获取Cookie失败";
            CookieHelper.CookieSource source = CookieHelper.getLastCookieSource();
            
            debugLog(TAG + ": Cookie不可用 - 来源: " + source);
            debugLog(TAG + ": Cookie状态: " + CookieHookManager.getCookieSource());
            
            if (source == CookieHelper.CookieSource.FAILED) {
                errorMsg = "获取Cookie失败，请确保已登录QQ并且模块已激活";
            }
            
            String finalErrorMsg = errorMsg;
            if (callback != null) {
                mMainHandler.post(() -> callback.onFailure(new Exception(finalErrorMsg)));
            }
            return;
        }
        
        Request request = buildRequest(context, type);
        if (request == null) {
            if (callback != null) {
                mMainHandler.post(() -> callback.onFailure(new Exception("构建请求失败")));
            }
            return;
        }
        
        debugLog(TAG + ": 开始请求亲密度数据, type=" + type);
        
        mClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                debugLog(TAG + ": 请求失败: " + e.getMessage());
                if (callback != null) {
                    mMainHandler.post(() -> callback.onFailure(e));
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    debugLog(TAG + ": ========== 好感度API响应 ==========");
                    debugLog(TAG + ": HTTP状态码: " + response.code());
                    
                    if (!response.isSuccessful()) {
                        debugLog(TAG + ": HTTP 错误: " + response.code());
                        throw new IOException("HTTP 错误: " + response.code());
                    }
                    
                    String html = response.body().string();
                    debugLog(TAG + ": 响应长度: " + html.length() + " 字符");
                    
                    String preview = html.length() > 500 ? html.substring(0, 500) + "..." : html;
                    debugLog(TAG + ": 响应预览: " + preview);
                    
                    // 【调试模式】保存响应到下载目录
                    if (mDebugMode) {
                        saveDebugResponse(context, type, html, request);
                    }
                    
                    Map<String, Integer> result = parseHtmlResponse(html);
                    debugLog(TAG + ": 解析结果: 共 " + result.size() + " 条数据");
                    
                    if (!result.isEmpty()) {
                        StringBuilder sb = new StringBuilder("好感度数据: ");
                        int count = 0;
                        for (Map.Entry<String, Integer> entry : result.entrySet()) {
                            if (count > 0) sb.append(", ");
                            sb.append(entry.getKey()).append("=").append(entry.getValue());
                            count++;
                            if (count >= 10) {
                                sb.append("... (共").append(result.size()).append("条)");
                                break;
                            }
                        }
                        debugLog(TAG + ": " + sb.toString());
                    } else {
                        debugLog(TAG + ": 未解析到任何好感度数据！");
                    }
                    debugLog(TAG + ": ====================================");
                    
                    if (callback != null) {
                        mMainHandler.post(() -> callback.onSuccess(result));
                    }
                    
                } catch (Exception e) {
                    debugLog(TAG + ": 处理响应失败: " + e.getMessage());
                    if (callback != null) {
                        mMainHandler.post(() -> callback.onFailure(e));
                    }
                } finally {
                    response.close();
                }
            }
        });
    }

    private Request buildRequest(Context context, int type) {
        try {
            String cookies = CookieHelper.getCookies(context);
            String url = API_URL + "?type=" + type;
            
            // 【隐私保护】不记录 Cookie 内容
            // XposedBridge.log(TAG + ": URL: " + url);
            
            return new Request.Builder()
                    .url(url)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .header("Cookie", cookies)
                    .header("Host", "h5.qzone.qq.com")
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build();
                    
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 构建请求失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 构建不带 type 参数的请求（获取完整页面，包含双向数据）
     */
    private Request buildRequestWithoutType(Context context) {
        try {
            String cookies = CookieHelper.getCookies(context);
            
            // 【隐私保护】不记录 Cookie 内容
            // XposedBridge.log(TAG + ": URL: " + API_URL);
            
            return new Request.Builder()
                    .url(API_URL)  // 不带 type 参数
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .header("Cookie", cookies)
                    .header("Host", "h5.qzone.qq.com")
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build();
                    
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 构建请求失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 保存调试数据到下载目录
     */
    private void saveDebugResponse(Context context, int type, String html, Request request) {
        try {
            // 获取下载目录
            File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
            File galqqDir = new File(downloadDir, "GalQQ_Debug");
            if (!galqqDir.exists()) {
                galqqDir.mkdirs();
            }
            
            // 生成时间戳
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String typeStr = (type == TYPE_WHO_CARES_ME) ? "who_cares_me" : "who_i_care";
            
            // 保存请求信息
            File requestFile = new File(galqqDir, "request_" + typeStr + "_" + timestamp + ".txt");
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(requestFile), "UTF-8")) {
                writer.write("========== 请求信息 ==========\n");
                writer.write("URL: " + request.url() + "\n");
                writer.write("Method: " + request.method() + "\n");
                writer.write("\n========== 请求头 ==========\n");
                for (String name : request.headers().names()) {
                    writer.write(name + ": " + request.header(name) + "\n");
                }
                writer.write("\n========== 时间 ==========\n");
                writer.write("请求时间: " + new Date().toString() + "\n");
            }
            XposedBridge.log(TAG + ": [Debug] 请求信息已保存到: " + requestFile.getAbsolutePath());
            
            // 保存响应HTML
            File responseFile = new File(galqqDir, "response_" + typeStr + "_" + timestamp + ".html");
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(responseFile), "UTF-8")) {
                writer.write(html);
            }
            XposedBridge.log(TAG + ": [Debug] 响应HTML已保存到: " + responseFile.getAbsolutePath());
            
            // 保存解析结果
            Map<String, Integer> result = parseHtmlResponse(html);
            File resultFile = new File(galqqDir, "result_" + typeStr + "_" + timestamp + ".txt");
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(resultFile), "UTF-8")) {
                writer.write("========== 解析结果 ==========\n");
                writer.write("总数: " + result.size() + " 条\n\n");
                for (Map.Entry<String, Integer> entry : result.entrySet()) {
                    writer.write("UIN: " + entry.getKey() + " -> Degree: " + entry.getValue() + "\n");
                }
            }
            XposedBridge.log(TAG + ": [Debug] 解析结果已保存到: " + resultFile.getAbsolutePath());
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": [Debug] 保存调试数据失败: " + e.getMessage());
        }
    }

    public static Map<String, Integer> parseHtmlResponse(String html) {
        Map<String, Integer> result = new HashMap<>();
        
        if (html == null || html.isEmpty()) {
            XposedBridge.log(TAG + ": HTML 响应为空");
            return result;
        }
        
        try {
            Matcher matcher = PATTERN_LIST_ITEM.matcher(html);
            while (matcher.find()) {
                String uin = matcher.group(1);
                String degreeStr = matcher.group(2);
                if (uin != null && degreeStr != null) {
                    try {
                        int degree = Integer.parseInt(degreeStr);
                        result.put(uin, degree);
                    } catch (NumberFormatException e) {
                        XposedBridge.log(TAG + ": 解析 degree 失败: " + degreeStr);
                    }
                }
            }
            
            if (result.isEmpty()) {
                XposedBridge.log(TAG + ": 主正则未匹配，尝试备用正则");
                matcher = PATTERN_UIN_DEGREE.matcher(html);
                while (matcher.find()) {
                    String uin = matcher.group(1);
                    String degreeStr = matcher.group(2);
                    if (uin != null && degreeStr != null) {
                        try {
                            int degree = Integer.parseInt(degreeStr);
                            result.put(uin, degree);
                        } catch (NumberFormatException e) {
                            XposedBridge.log(TAG + ": 解析 degree 失败: " + degreeStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 解析 HTML 失败: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 按类型解析 HTML 响应
     * @param html 完整的 HTML 响应
     * @param type TYPE_WHO_I_CARE (1) 或 TYPE_WHO_CARES_ME (2)
     * @return UIN 到好感度的映射
     */
    public static Map<String, Integer> parseHtmlResponseByType(String html, int type) {
        Map<String, Integer> result = new HashMap<>();
        
        if (html == null || html.isEmpty()) {
            XposedBridge.log(TAG + ": HTML 响应为空");
            return result;
        }
        
        try {
            // 根据类型选择正则
            Pattern listPattern = (type == TYPE_WHO_I_CARE) ? PATTERN_RANK_LIST_TYPE1 : PATTERN_RANK_LIST_TYPE2;
            String typeName = (type == TYPE_WHO_I_CARE) ? "我在意谁" : "谁在意我";
            
            // 先提取对应类型的列表内容
            Matcher listMatcher = listPattern.matcher(html);
            if (listMatcher.find()) {
                String listContent = listMatcher.group(1);
                XposedBridge.log(TAG + ": 找到 " + typeName + " 列表，长度: " + listContent.length());
                
                // 从列表内容中提取 UIN 和 degree
                Matcher itemMatcher = PATTERN_LIST_ITEM.matcher(listContent);
                while (itemMatcher.find()) {
                    String uin = itemMatcher.group(1);
                    String degreeStr = itemMatcher.group(2);
                    if (uin != null && degreeStr != null) {
                        try {
                            int degree = Integer.parseInt(degreeStr);
                            result.put(uin, degree);
                        } catch (NumberFormatException e) {
                            XposedBridge.log(TAG + ": 解析 degree 失败: " + degreeStr);
                        }
                    }
                }
                
                // 如果主正则没匹配到，尝试备用正则
                if (result.isEmpty()) {
                    XposedBridge.log(TAG + ": " + typeName + " 主正则未匹配，尝试备用正则");
                    itemMatcher = PATTERN_UIN_DEGREE.matcher(listContent);
                    while (itemMatcher.find()) {
                        String uin = itemMatcher.group(1);
                        String degreeStr = itemMatcher.group(2);
                        if (uin != null && degreeStr != null) {
                            try {
                                int degree = Integer.parseInt(degreeStr);
                                result.put(uin, degree);
                            } catch (NumberFormatException e) {
                                XposedBridge.log(TAG + ": 解析 degree 失败: " + degreeStr);
                            }
                        }
                    }
                }
            } else {
                XposedBridge.log(TAG + ": 未找到 " + typeName + " 列表 (data-type=\"" + type + "\")");
            }
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": 解析 HTML 失败: " + e.getMessage());
        }
        
        XposedBridge.log(TAG + ": 类型 " + type + " 解析结果: " + result.size() + " 条");
        return result;
    }
}