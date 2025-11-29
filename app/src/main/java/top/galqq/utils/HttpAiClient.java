package top.galqq.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import top.galqq.config.ConfigManager;

/**
 * AI客户端 - 支持多种模型和JSON格式响应
 */
public class HttpAiClient {

    private static final String TAG = "GalQQ.AI";
    private static OkHttpClient client;
    private static Handler mainHandler = new Handler(Looper.getMainLooper());

    private static synchronized OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }
        return client;
    }

    public interface AiCallback {
        void onSuccess(List<String> options);
        void onFailure(Exception e);
    }

    /**
     * 获取AI生成的回复选项（无上下文和元数据，向后兼容）
     */
    public static void fetchOptions(Context context, String userMessage, AiCallback callback) {
        fetchOptions(context, userMessage, null, 0, null, callback);
    }

    /**
     * 获取AI生成的回复选项（带上下文和当前消息元数据）
     * 
     * @param context Android上下文
     * @param userMessage 当前用户消息内容
     * @param currentSenderName 当前消息发送人昵称
     * @param currentTimestamp 当前消息时间戳
     * @param contextMessages 历史上下文消息（可为null）
     * @param callback 回调
     */
    public static void fetchOptions(Context context, String userMessage,
                                    String currentSenderName, long currentTimestamp,
                                    List<top.galqq.utils.MessageContextManager.ChatMessage> contextMessages,
                                    AiCallback callback) {
        String apiUrl = ConfigManager.getApiUrl();
        String apiKey = ConfigManager.getApiKey();
        String sysPrompt = ConfigManager.getSysPrompt();
        String model = ConfigManager.getAiModel();
        String provider = ConfigManager.getAiProvider();
        float temperature = ConfigManager.getAiTemperature();
        int maxTokens = ConfigManager.getAiMaxTokens();

        // 验证配置
        if (TextUtils.isEmpty(apiUrl) || TextUtils.isEmpty(apiKey)) {
            String error = "API配置不完整";
            logError(context, provider, model, apiUrl, error);
            showToast(context, "AI服务未配置 😢");
            callback.onFailure(new IllegalArgumentException(error));
            return;
        }

        try {
            // 构建请求体
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", model);
            
            // 可选参数：只在合理范围内添加
            if (temperature > 0 && temperature <= 2.0) {
                jsonBody.put("temperature", temperature);
            }
            if (maxTokens > 0 && maxTokens <= 4096) {
                jsonBody.put("max_tokens", maxTokens);
            }

            JSONArray messages = new JSONArray();
            
            // 系统提示词
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", sysPrompt);
            messages.put(sysMsg);

            // 添加历史上下文（如果有）
            if (contextMessages != null && !contextMessages.isEmpty()) {
                // 创建时间格式化器
                java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
                
                for (top.galqq.utils.MessageContextManager.ChatMessage msg : contextMessages) {
                    JSONObject ctxMsg = new JSONObject();
                    // 对方的消息作为"user"，自己的消息作为"assistant"
                    ctxMsg.put("role", msg.isSelf ? "assistant" : "user");
                    
                    // 格式化时间戳
                    String timeStr = timeFormat.format(new java.util.Date(msg.timestamp));
                    
                    // 格式化为 "发送人 [时间]: 消息内容"
                    // 格式化为 "发送人 [时间]: 消息内容"
                    String formattedContent = msg.senderName + " [" + timeStr + "]: " + msg.content;
                    ctxMsg.put("content", formattedContent);
                    messages.put(ctxMsg);
                }
                Log.i(TAG, "Added " + contextMessages.size() + " context messages");
            }

            // 当前用户消息（添加特殊标注）
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            
            // 格式化当前消息：添加[当前需添加选项信息]标签
            String formattedCurrentMsg;
            if (currentSenderName != null && !currentSenderName.isEmpty() && currentTimestamp > 0) {
                // 创建时间格式化器
                java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
                String currentTimeStr = timeFormat.format(new java.util.Date(currentTimestamp));
                
                // 格式：[当前需添加选项信息] 昵称 [时间]: 内容
                formattedCurrentMsg = "[当前需添加选项信息] " + currentSenderName + " [" + currentTimeStr + "]: " + userMessage;
            } else {
                // 降级：如果没有元数据，仅添加标签
                formattedCurrentMsg = "[当前需添加选项信息] " + userMessage;
            }
            
            userMsg.put("content", formattedCurrentMsg);
            messages.put(userMsg);

            jsonBody.put("messages", messages);

            RequestBody body = RequestBody.create(
                    jsonBody.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            // 记录完整的请求信息到日志（仅在启用详细日志时）
            if (ConfigManager.isVerboseLogEnabled()) {
                String requestLog = buildRequestLog(provider, model, apiUrl, apiKey, jsonBody.toString());
                Log.d(TAG, "发送AI请求:\n" + requestLog);
                AiLogManager.addLog(context, "AI请求\n" + requestLog);
            } else {
                Log.d(TAG, "发送AI请求: " + provider + " / " + model);
            }

            getClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String error = e.getMessage();
                    Log.e(TAG, "AI请求失败: " + error, e);
                    logError(context, provider, model, apiUrl, error);
                    showToast(context, "网络连接失败 😢");
                    callback.onFailure(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = null;
                    try {
                        if (!response.isSuccessful()) {
                            int code = response.code();
                            String error = "HTTP " + code + ": " + response.message();
                            responseBody = response.body() != null ? response.body().string() : "";
                            
                            // 特殊处理429速率限制错误（静默处理，不显示Toast）
                            if (code == 429) {
                                Log.w(TAG, "速率限制: " + error);
                                logError(context, provider, model, apiUrl, "Rate Limit (429)\n" + responseBody);
                                // 不调用showToast，静默失败
                                callback.onFailure(new IOException("Rate limit reached"));
                                return;
                            }
                            
                            // 其他错误正常处理
                            logError(context, provider, model, apiUrl, error + "\n" + responseBody);
                            showToast(context, "AI服务暂时不可用 😢");
                            callback.onFailure(new IOException(error));
                            return;
                        }

                        responseBody = response.body().string();
                        Log.d(TAG, "AI响应: " + responseBody.substring(0, Math.min(200, responseBody.length())));

                        // 解析JSON格式的响应
                        List<String> options = parseJsonResponse(responseBody);
                        
                        if (options == null || options.size() < 3) {
                            String error = "AI返回格式错误或选项不足";
                            logError(context, provider, model, apiUrl, error + "\n响应: " + responseBody);
                            showToast(context, "AI返回格式错误 😢");
                            callback.onFailure(new Exception(error));
                            return;
                        }

                        // 成功
                        AiLogManager.logAiSuccess(context, provider, model, userMessage, options.size());
                        callback.onSuccess(options);

                    } catch (Exception e) {
                        Log.e(TAG, "解析失败", e);
                        String error = "解析错误: " + e.getMessage();
                        logError(context, provider, model, apiUrl, error + "\n响应: " + responseBody);
                        showToast(context, "AI返回格式错误 😢");
                        callback.onFailure(e);
                    } finally {
                        response.close();
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "请求构建失败", e);
            logError(context, provider, model, apiUrl, "请求构建失败: " + e.getMessage());
            showToast(context, "AI请求失败 😢");
            callback.onFailure(e);
        }
    }

    /**
     * 解析JSON格式的AI响应
     * 支持两种格式：
     * 1. 直接返回 {"options": ["...", "...", "..."]}
     * 2. OpenAI格式 {"choices": [{"message": {"content": "{\"options\": [...]}"}}]}
     */
    private static List<String> parseJsonResponse(String responseBody) {
        try {
            JSONObject jsonResponse = new JSONObject(responseBody);
            
            // 方式1: 直接包含options字段
            if (jsonResponse.has("options")) {
                JSONArray optionsArray = jsonResponse.getJSONArray("options");
                return jsonArrayToList(optionsArray);
            }
            
            // 方式2: OpenAI标准格式
            if (jsonResponse.has("choices")) {
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() > 0) {
                    String content = choices.getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                    
                    // content可能是JSON字符串
                    try {
                        JSONObject contentJson = new JSONObject(content);
                        if (contentJson.has("options")) {
                            JSONArray optionsArray = contentJson.getJSONArray("options");
                            return jsonArrayToList(optionsArray);
                        }
                    } catch (Exception e) {
                        // content不是JSON，可能是旧格式的|||分隔
                        return parseLegacyFormat(content);
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "JSON解析失败", e);
            return null;
        }
    }

    /**
     * 将JSONArray转换为List<String>
     */
    private static List<String> jsonArrayToList(JSONArray array) throws Exception {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String option = array.getString(i).trim();
            if (!option.isEmpty()) {
                result.add(option);
            }
        }
        return result;
    }

    /**
     * 解析旧格式（|||分隔）
     */
    private static List<String> parseLegacyFormat(String content) {
        String[] parts = content.split("\\|\\|\\|");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.size() >= 3 ? result : null;
    }

    /**
     * 记录错误日志
     */
    private static void logError(Context context, String provider, String model, String url, String error) {
        AiLogManager.logAiError(context, provider, model, url, error);
    }

    /**
     * 构建请求日志（用于调试）
     */
    private static String buildRequestLog(String provider, String model, String url, String apiKey, String body) {
        StringBuilder log = new StringBuilder();
        log.append("Provider: ").append(provider).append("\n");
        log.append("Model: ").append(model).append("\n");
        log.append("URL: ").append(url).append("\n");
        log.append("Headers:\n");
        log.append("  Authorization: Bearer ").append(maskApiKey(apiKey)).append("\n");
        log.append("  Content-Type: application/json\n");
        log.append("Body:\n");
        
        // 格式化JSON body
        try {
            JSONObject jsonBody = new JSONObject(body);
            log.append(jsonBody.toString(2)); // 缩进2个空格
        } catch (Exception e) {
            log.append(body);
        }
        
        return log.toString();
    }

    /**
     * 遮蔽API Key（只显示前4位和后4位）
     */
    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 显示Toast提示
     */
    private static void showToast(Context context, String message) {
        mainHandler.post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * 测试API连接
     */
    public static void testApiConnection(Context context, AiCallback callback) {
        fetchOptions(context, "你好", callback);
    }
}
