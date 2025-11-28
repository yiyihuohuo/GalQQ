package top.galqq.config;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.tencent.mmkv.MMKV;
import java.io.File;

public class ConfigManager {

    private static final String MMKV_ID = "GalQQ";
    private static MMKV sMmkv;
    private static boolean sInitialized = false;
    
    // Keys
    public static final String KEY_ENABLED = "gal_enabled";
    public static final String KEY_AI_ENABLED = "gal_ai_enabled";
    public static final String KEY_SYS_PROMPT = "gal_sys_prompt";
    public static final String KEY_API_URL = "gal_api_url";
    public static final String KEY_API_KEY = "gal_api_key";
    public static final String KEY_AI_MODEL = "gal_ai_model";
    public static final String KEY_AI_PROVIDER = "gal_ai_provider";
    public static final String KEY_AI_TEMPERATURE = "gal_ai_temperature";
    public static final String KEY_AI_MAX_TOKENS = "gal_ai_max_tokens";
    public static final String KEY_DICT_PATH = "gal_dict_path";
    public static final String KEY_FILTER_MODE = "gal_filter_mode";
    public static final String KEY_WHITELIST = "gal_whitelist";
    public static final String KEY_VERBOSE_LOG = "gal_verbose_log";
    
    // Context Keys
    public static final String KEY_CONTEXT_ENABLED = "gal_context_enabled";
    public static final String KEY_CONTEXT_MESSAGE_COUNT = "gal_context_message_count";

    // AI Providers
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_KIMI = "kimi";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_QWEN = "qwen";
    public static final String PROVIDER_GLM = "glm";
    public static final String PROVIDER_OLLAMA = "ollama";

    // Default Values
    public static final String DEFAULT_SYS_PROMPT = "你是一个聊天助手，根据用户收到的消息，生成3个简短、自然、符合上下文的回复选项。\n\n要求：\n1. 回复要符合对话语境，贴近真实聊天习惯\n2. 语气要自然、友好，可以包含适当的emoji\n3. 必须返回恰好3个选项\n4. 严格按照以下JSON格式返回：\n\n{\n  \"options\": [\n    \"第一个回复选项\",\n    \"第二个回复选项\",\n    \"第三个回复选项\"\n  ]\n}";
    public static final String DEFAULT_MODEL = "gpt-3.5-turbo";
    public static final String DEFAULT_PROVIDER = PROVIDER_OPENAI;
    public static final float DEFAULT_TEMPERATURE = 0.8f;
    public static final int DEFAULT_MAX_TOKENS = 120;
    public static final String DEFAULT_FILTER_MODE = "blacklist";
    
    // Context Default Values
    public static final boolean DEFAULT_CONTEXT_ENABLED = true;
    public static final int DEFAULT_CONTEXT_MESSAGE_COUNT = 10;
    
    // QPS Default Value
    public static final float DEFAULT_AI_QPS = 3.0f;
    public static final String KEY_AI_QPS = "gal_ai_qps";

    /**
     * Initialize MMKV with MULTI_PROCESS_MODE for cross-process access
     * This MUST be called before any other operations
     */
    public static synchronized void init(Context context) {
        if (sInitialized) {
            return;
        }
        
        try {
            // Create MMKV directory in QQ's files directory
            File filesDir = context.getFilesDir();
            File mmkvDir = new File(filesDir, "galqq_mmkv");
            if (!mmkvDir.exists()) {
                mmkvDir.mkdirs();
            }
            
            // Create .tmp cache directory (required by MMKV)
            File cacheDir = new File(mmkvDir, ".tmp");
            if (!cacheDir.exists()) {
                cacheDir.mkdir();
            }
            
            // Initialize MMKV
            String rootDir = MMKV.initialize(context, mmkvDir.getAbsolutePath());
            
            // Get MMKV instance with MULTI_PROCESS_MODE (critical for cross-process access!)
            sMmkv = MMKV.mmkvWithID(MMKV_ID, MMKV.MULTI_PROCESS_MODE);
            
            sInitialized = true;
            
            android.util.Log.i("GalQQ.ConfigManager", "MMKV initialized successfully at: " + rootDir);
        } catch (Exception e) {
            android.util.Log.e("GalQQ.ConfigManager", "Failed to initialize MMKV: " + e.getMessage(), e);
            throw new RuntimeException("MMKV initialization failed", e);
        }
    }

    // Alias for backwards compatibility
    public static void initPref(Context context) {
        init(context);
    }

    @NonNull
    private static MMKV getMmkv() {
        if (sMmkv == null) {
            throw new IllegalStateException("ConfigManager not initialized. Call init() first.");
        }
        return sMmkv;
    }

    // ========== Boolean Methods ==========
    
    public static boolean isModuleEnabled() {
        return getMmkv().decodeBool(KEY_ENABLED, true);
    }
    
    public static void setModuleEnabled(boolean enabled) {
        getMmkv().encode(KEY_ENABLED, enabled);
    }

    public static boolean isAiEnabled() {
        return getMmkv().decodeBool(KEY_AI_ENABLED, false);
    }
    
    public static void setAiEnabled(boolean enabled) {
        getMmkv().encode(KEY_AI_ENABLED, enabled);
    }

    // ========== String Methods ==========
    
    public static String getSysPrompt() {
        return getMmkv().decodeString(KEY_SYS_PROMPT, DEFAULT_SYS_PROMPT);
    }
    
    public static void setSysPrompt(String prompt) {
        getMmkv().encode(KEY_SYS_PROMPT, prompt);
    }

    public static String getApiUrl() {
        return getMmkv().decodeString(KEY_API_URL, "");
    }
    
    public static void setApiUrl(String url) {
        getMmkv().encode(KEY_API_URL, url);
    }

    public static String getApiKey() {
        return getMmkv().decodeString(KEY_API_KEY, "");
    }
    
    public static void setApiKey(String key) {
        getMmkv().encode(KEY_API_KEY, key);
    }

    public static String getAiModel() {
        return getMmkv().decodeString(KEY_AI_MODEL, DEFAULT_MODEL);
    }
    
    public static void setAiModel(String model) {
        getMmkv().encode(KEY_AI_MODEL, model);
    }

    public static String getAiProvider() {
        return getMmkv().decodeString(KEY_AI_PROVIDER, DEFAULT_PROVIDER);
    }
    
    public static void setAiProvider(String provider) {
        getMmkv().encode(KEY_AI_PROVIDER, provider);
    }

    public static float getAiTemperature() {
        return getMmkv().decodeFloat(KEY_AI_TEMPERATURE, DEFAULT_TEMPERATURE);
    }
    
    public static void setAiTemperature(float temperature) {
        getMmkv().encode(KEY_AI_TEMPERATURE, temperature);
    }

    public static int getAiMaxTokens() {
        return getMmkv().decodeInt(KEY_AI_MAX_TOKENS, DEFAULT_MAX_TOKENS);
    }
    
    public static void setAiMaxTokens(int maxTokens) {
        getMmkv().encode(KEY_AI_MAX_TOKENS, maxTokens);
    }

    public static float getAiQps() {
        return getMmkv().decodeFloat(KEY_AI_QPS, DEFAULT_AI_QPS);
    }
    
    public static void setAiQps(float qps) {
        getMmkv().encode(KEY_AI_QPS, qps);
    }

    public static String getDictPath() {
        return getMmkv().decodeString(KEY_DICT_PATH, "");
    }
    
    public static void setDictPath(String path) {
        getMmkv().encode(KEY_DICT_PATH, path);
    }

    // Filter Mode
    public static String getFilterMode() {
        return getMmkv().decodeString(KEY_FILTER_MODE, DEFAULT_FILTER_MODE);
    }
    
    public static void setFilterMode(String mode) {
        getMmkv().encode(KEY_FILTER_MODE, mode);
    }
    
    // Blacklist
    public static final String KEY_BLACKLIST = "gal_blacklist";
    
    public static String getBlacklist() {
        String blacklist = getMmkv().decodeString(KEY_BLACKLIST, "");
        // 默认包含2854196310
        if (blacklist.isEmpty()) {
            return "2854196310";
        }
        // 确保2854196310在黑名单中
        if (!blacklist.contains("2854196310")) {
            return blacklist + ",2854196310";
        }
        return blacklist;
    }
    
    public static void setBlacklist(String blacklist) {
        getMmkv().encode(KEY_BLACKLIST, blacklist);
    }
    
    public static boolean isInBlacklist(String qqNumber) {
        String blacklist = getBlacklist();
        if (blacklist == null || blacklist.trim().isEmpty()) {
            return false;
        }
        String[] numbers = blacklist.split(",");
        for (String num : numbers) {
            if (num.trim().equals(qqNumber)) {
                return true;
            }
        }
        return false;
    }
    
    // Whitelist
    public static String getWhitelist() {
        return getMmkv().decodeString(KEY_WHITELIST, "");
    }
    
    public static void setWhitelist(String whitelist) {
        getMmkv().encode(KEY_WHITELIST, whitelist);
    }
    
    public static boolean isInWhitelist(String qqNumber) {
        String whitelist = getWhitelist();
        if (whitelist == null || whitelist.trim().isEmpty()) {
            return false;
        }
        String[] numbers = whitelist.split(",");
        for (String num : numbers) {
            if (num.trim().equals(qqNumber)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVerboseLogEnabled() {
        return getMmkv().decodeBool(KEY_VERBOSE_LOG, false);
    }
    
    public static void setVerboseLogEnabled(boolean enabled) {
        getMmkv().encode(KEY_VERBOSE_LOG, enabled);
    }

    // ========== Context Methods ==========
    
    public static boolean isContextEnabled() {
        return getMmkv().decodeBool(KEY_CONTEXT_ENABLED, DEFAULT_CONTEXT_ENABLED);
    }
    
    public static void setContextEnabled(boolean enabled) {
        getMmkv().encode(KEY_CONTEXT_ENABLED, enabled);
    }
    
    public static int getContextMessageCount() {
        int count = getMmkv().decodeInt(KEY_CONTEXT_MESSAGE_COUNT, DEFAULT_CONTEXT_MESSAGE_COUNT);
        // 限制在1-20之间
        return Math.max(1, Math.min(20, count));
    }
    
    public static void setContextMessageCount(int count) {
        getMmkv().encode(KEY_CONTEXT_MESSAGE_COUNT, count);
    }

    // ========== Generic Methods ==========
    
    public static boolean getBoolean(String key, boolean defaultValue) {
        return getMmkv().decodeBool(key, defaultValue);
    }
    
    public static void putBoolean(String key, boolean value) {
        getMmkv().encode(key, value);
    }
    
    public static int getInt(String key, int defaultValue) {
        return getMmkv().decodeInt(key, defaultValue);
    }
    
    public static void putInt(String key, int value) {
        getMmkv().encode(key, value);
    }
    
    public static long getLong(String key, long defaultValue) {
        return getMmkv().decodeLong(key, defaultValue);
    }
    
    public static void putLong(String key, long value) {
        getMmkv().encode(key, value);
    }
    
    public static String getString(String key, String defaultValue) {
        return getMmkv().decodeString(key, defaultValue);
    }
    
    public static void putString(String key, String value) {
        getMmkv().encode(key, value);
    }
    
    public static boolean contains(String key) {
        return getMmkv().contains(key);
    }
    
    public static void remove(String key) {
        getMmkv().remove(key);
    }
    
    public static void clear() {
        getMmkv().clearAll();
    }

    /**
     * Get the MMKV file for debugging
     */
    @Nullable
    public static File getConfigFile() {
        if (!sInitialized) {
            return null;
        }
        String rootDir = MMKV.getRootDir();
        if (rootDir == null) {
            return null;
        }
        return new File(rootDir, MMKV_ID);
    }
}
