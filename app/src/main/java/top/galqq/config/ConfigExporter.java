package top.galqq.config;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * 配置导出器
 * 负责从 MMKV 读取配置并序列化为 JSON
 */
public class ConfigExporter {
    
    private static final String TAG = "GalQQ.ConfigExporter";
    
    // 当前配置文件版本
    public static final int CURRENT_SCHEMA_VERSION = 1;
    
    // 应用版本
    public static final String APP_VERSION = "1.0.7";
    
    /**
     * 获取所有配置项（按分类组织）
     * @return 分类配置映射 Map<分类ID, Map<配置键, 配置值>>
     */
    @NonNull
    public static Map<String, Map<String, Object>> getAllConfigsByCategory() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        
        // 初始化所有分类
        for (String category : ConfigManager.ALL_CATEGORIES) {
            result.put(category, new HashMap<>());
        }
        
        // 遍历所有配置键，读取值并按分类组织
        for (Map.Entry<String, String> entry : ConfigManager.KEY_TO_CATEGORY.entrySet()) {
            String key = entry.getKey();
            String category = entry.getValue();
            
            Object value = readConfigValue(key);
            if (value != null) {
                result.get(category).put(key, value);
            }
        }
        
        return result;
    }
    
    /**
     * 读取单个配置项的值
     * 根据配置键自动判断类型
     * @param key 配置键
     * @return 配置值，如果不存在返回null
     */
    private static Object readConfigValue(String key) {
        if (key == null) return null;
        
        // 根据配置键判断类型并读取
        switch (key) {
            // Boolean 类型
            case ConfigManager.KEY_ENABLED:
                return ConfigManager.isModuleEnabled();
            case ConfigManager.KEY_AI_ENABLED:
                return ConfigManager.isAiEnabled();
            case ConfigManager.KEY_CONTEXT_ENABLED:
                return ConfigManager.isContextEnabled();
            case ConfigManager.KEY_AUTO_SHOW_OPTIONS:
                return ConfigManager.isAutoShowOptionsEnabled();
            case ConfigManager.KEY_AFFINITY_ENABLED:
                return ConfigManager.isAffinityEnabled();
            case ConfigManager.KEY_AI_INCLUDE_AFFINITY:
                return ConfigManager.isAiIncludeAffinity();
            case ConfigManager.KEY_VERBOSE_LOG:
                return ConfigManager.isVerboseLogEnabled();
            case ConfigManager.KEY_DEBUG_HOOK_LOG:
                return ConfigManager.isDebugHookLogEnabled();
            case ConfigManager.KEY_PROXY_ENABLED:
                return ConfigManager.isProxyEnabled();
            case ConfigManager.KEY_PROXY_AUTH_ENABLED:
                return ConfigManager.isProxyAuthEnabled();
            case ConfigManager.KEY_IMAGE_RECOGNITION_ENABLED:
                return ConfigManager.isImageRecognitionEnabled();
            case ConfigManager.KEY_EMOJI_RECOGNITION_ENABLED:
                return ConfigManager.isEmojiRecognitionEnabled();
            case ConfigManager.KEY_VISION_AI_ENABLED:
                return ConfigManager.isVisionAiEnabled();
            case ConfigManager.KEY_VISION_USE_PROXY:
                return ConfigManager.isVisionUseProxy();
            case ConfigManager.KEY_CONTEXT_IMAGE_RECOGNITION_ENABLED:
                return ConfigManager.isContextImageRecognitionEnabled();
            case ConfigManager.KEY_DISABLE_GROUP_OPTIONS:
                return ConfigManager.isDisableGroupOptions();
                
            // String 类型
            case ConfigManager.KEY_API_URL:
                return ConfigManager.getApiUrl();
            case ConfigManager.KEY_CUSTOM_API_URL:
                return ConfigManager.getCustomApiUrl();
            case ConfigManager.KEY_API_KEY:
                return ConfigManager.getApiKey();
            case ConfigManager.KEY_AI_MODEL:
                return ConfigManager.getAiModel();
            case ConfigManager.KEY_AI_PROVIDER:
                return ConfigManager.getAiProvider();
            case ConfigManager.KEY_AI_REASONING_EFFORT:
                return ConfigManager.getAiReasoningEffort();
            case ConfigManager.KEY_SYS_PROMPT:
                return ConfigManager.getSysPrompt();
            case ConfigManager.KEY_FILTER_MODE:
                return ConfigManager.getFilterMode();
            case ConfigManager.KEY_BLACKLIST:
                return ConfigManager.getBlacklist();
            case ConfigManager.KEY_WHITELIST:
                return ConfigManager.getWhitelist();
            case ConfigManager.KEY_GROUP_FILTER_MODE:
                return ConfigManager.getGroupFilterMode();
            case ConfigManager.KEY_GROUP_BLACKLIST:
                return ConfigManager.getGroupBlacklist();
            case ConfigManager.KEY_GROUP_WHITELIST:
                return ConfigManager.getGroupWhitelist();
            case ConfigManager.KEY_PROXY_TYPE:
                return ConfigManager.getProxyType();
            case ConfigManager.KEY_PROXY_HOST:
                return ConfigManager.getProxyHost();
            case ConfigManager.KEY_PROXY_USERNAME:
                return ConfigManager.getProxyUsername();
            case ConfigManager.KEY_PROXY_PASSWORD:
                return ConfigManager.getProxyPassword();
            case ConfigManager.KEY_VISION_API_URL:
                return ConfigManager.getVisionApiUrl();
            case ConfigManager.KEY_VISION_API_KEY:
                return ConfigManager.getVisionApiKey();
            case ConfigManager.KEY_VISION_AI_MODEL:
                return ConfigManager.getVisionAiModel();
            case ConfigManager.KEY_VISION_AI_PROVIDER:
                return ConfigManager.getVisionAiProvider();
                
            // Int 类型
            case ConfigManager.KEY_AI_MAX_TOKENS:
                return ConfigManager.getAiMaxTokens();
            case ConfigManager.KEY_CONTEXT_MESSAGE_COUNT:
                return ConfigManager.getContextMessageCount();
            case ConfigManager.KEY_HISTORY_THRESHOLD:
                return ConfigManager.getHistoryThreshold();
            case ConfigManager.KEY_AFFINITY_MODEL:
                return ConfigManager.getAffinityModel();
            case ConfigManager.KEY_PROXY_PORT:
                return ConfigManager.getProxyPort();
            case ConfigManager.KEY_IMAGE_MAX_SIZE:
                return ConfigManager.getImageMaxSize();
            case ConfigManager.KEY_IMAGE_DESCRIPTION_MAX_LENGTH:
                return ConfigManager.getImageDescriptionMaxLength();
            case ConfigManager.KEY_VISION_TIMEOUT:
                return ConfigManager.getVisionTimeout();
            case ConfigManager.KEY_AI_TIMEOUT:
                return ConfigManager.getAiTimeout();
            case ConfigManager.KEY_CURRENT_PROMPT_INDEX:
                return ConfigManager.getCurrentPromptIndex();
            
            // 按钮样式 - Int 类型
            case ConfigManager.KEY_BUTTON_FILL_COLOR:
                return ConfigManager.getButtonFillColor();
            case ConfigManager.KEY_BUTTON_BORDER_COLOR:
                return ConfigManager.getButtonBorderColor();
            case ConfigManager.KEY_BUTTON_BORDER_WIDTH:
                return ConfigManager.getButtonBorderWidth();
            case ConfigManager.KEY_BUTTON_TEXT_COLOR:
                return ConfigManager.getButtonTextColor();
                
            // Float 类型
            case ConfigManager.KEY_AI_TEMPERATURE:
                return ConfigManager.getAiTemperature();
            case ConfigManager.KEY_AI_QPS:
                return ConfigManager.getAiQps();
            case ConfigManager.KEY_VISION_AI_QPS:
                return ConfigManager.getVisionAiQps();
                
            // 特殊类型 - 提示词列表（JSON字符串）
            case ConfigManager.KEY_PROMPT_LIST:
                return getPromptListJson();
                
            default:
                // 尝试通用读取
                return ConfigManager.getString(key, null);
        }
    }
    
    /**
     * 获取提示词列表的JSON字符串
     */
    private static String getPromptListJson() {
        try {
            java.util.List<ConfigManager.PromptItem> list = ConfigManager.getPromptList();
            org.json.JSONArray arr = new org.json.JSONArray();
            for (ConfigManager.PromptItem item : list) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("name", item.name);
                obj.put("content", item.content);
                obj.put("whitelist", item.whitelist != null ? item.whitelist : "");
                obj.put("blacklist", item.blacklist != null ? item.blacklist : "");
                obj.put("enabled", item.enabled);
                obj.put("whitelistEnabled", item.whitelistEnabled);
                obj.put("blacklistEnabled", item.blacklistEnabled);
                obj.put("groupWhitelist", item.groupWhitelist != null ? item.groupWhitelist : "");
                obj.put("groupBlacklist", item.groupBlacklist != null ? item.groupBlacklist : "");
                obj.put("groupWhitelistEnabled", item.groupWhitelistEnabled);
                obj.put("groupBlacklistEnabled", item.groupBlacklistEnabled);
                arr.put(obj);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    
    /**
     * 导出配置到 JSON 字符串
     * @param includeSensitive 是否包含敏感数据
     * @return JSON 字符串（格式化输出，2空格缩进）
     */
    @NonNull
    public static String exportToJson(boolean includeSensitive) {
        try {
            JSONObject root = new JSONObject();
            
            // 1. 添加元数据
            JSONObject metadata = new JSONObject();
            metadata.put("exportTime", getIso8601Timestamp());
            metadata.put("appVersion", APP_VERSION);
            metadata.put("schemaVersion", CURRENT_SCHEMA_VERSION);
            metadata.put("deviceInfo", getDeviceInfo());
            root.put("_metadata", metadata);
            
            // 2. 添加分类描述
            JSONObject description = new JSONObject();
            for (String category : ConfigManager.ALL_CATEGORIES) {
                description.put(category, ConfigManager.getCategoryDescription(category));
            }
            root.put("_description", description);
            
            // 3. 获取所有配置并按分类组织
            Map<String, Map<String, Object>> configsByCategory = getAllConfigsByCategory();
            
            // 4. 添加各分类配置
            for (String category : ConfigManager.ALL_CATEGORIES) {
                JSONObject categoryObj = new JSONObject();
                Map<String, Object> configs = configsByCategory.get(category);
                
                if (configs != null) {
                    for (Map.Entry<String, Object> entry : configs.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        
                        // 处理敏感数据
                        if (!includeSensitive && ConfigManager.isSensitiveKey(key)) {
                            value = "";  // 敏感数据替换为空字符串
                        }
                        
                        categoryObj.put(key, value);
                    }
                }
                
                root.put(category, categoryObj);
            }
            
            // 5. 格式化输出（2空格缩进）
            return root.toString(2);
            
        } catch (JSONException e) {
            android.util.Log.e(TAG, "Failed to export config to JSON", e);
            return "{}";
        }
    }
    
    /**
     * 导出配置到文件
     * @param context 上下文
     * @param uri 文件 URI（通过 SAF 获取）
     * @param includeSensitive 是否包含敏感数据
     * @return 是否成功
     */
    public static boolean exportToFile(Context context, Uri uri, boolean includeSensitive) {
        if (context == null || uri == null) {
            return false;
        }
        
        try {
            String json = exportToJson(includeSensitive);
            
            OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
            if (outputStream == null) {
                android.util.Log.e(TAG, "Failed to open output stream for URI: " + uri);
                return false;
            }
            
            try {
                byte[] bytes = json.getBytes("UTF-8");
                outputStream.write(bytes);
                outputStream.flush();
                return true;
            } finally {
                outputStream.close();
            }
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to export config to file", e);
            return false;
        }
    }
    
    /**
     * 获取 ISO 8601 格式的时间戳
     * 例如: 2024-12-09T10:30:00+08:00
     */
    @NonNull
    private static String getIso8601Timestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        sdf.setTimeZone(TimeZone.getDefault());
        return sdf.format(new Date());
    }
    
    /**
     * 获取设备信息
     * 例如: Android 14, Xiaomi 14
     */
    @NonNull
    private static String getDeviceInfo() {
        return "Android " + Build.VERSION.RELEASE + ", " + Build.MANUFACTURER + " " + Build.MODEL;
    }
    
    /**
     * 获取建议的导出文件名
     * 格式: galqq_config_20241209_103000.json
     */
    @NonNull
    public static String getSuggestedFileName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        return "galqq_config_" + sdf.format(new Date()) + ".json";
    }
}
