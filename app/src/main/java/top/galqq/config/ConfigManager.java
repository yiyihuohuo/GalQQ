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
    public static final String KEY_PROMPT_LIST = "gal_prompt_list";
    public static final String KEY_CURRENT_PROMPT_INDEX = "gal_current_prompt_index";
    public static final String KEY_API_URL = "gal_api_url";
    public static final String KEY_API_KEY = "gal_api_key";
    public static final String KEY_AI_MODEL = "gal_ai_model";
    public static final String KEY_AI_PROVIDER = "gal_ai_provider";
    public static final String KEY_AI_TEMPERATURE = "gal_ai_temperature";
    public static final String KEY_AI_MAX_TOKENS = "gal_ai_max_tokens";
    public static final String KEY_AI_REASONING_EFFORT = "gal_ai_reasoning_effort";
    public static final String KEY_DICT_PATH = "gal_dict_path";
    public static final String KEY_FILTER_MODE = "gal_filter_mode";
    public static final String KEY_WHITELIST = "gal_whitelist";
    public static final String KEY_VERBOSE_LOG = "gal_verbose_log";
    public static final String KEY_DEBUG_HOOK_LOG = "gal_debug_hook_log";
    
    // Context Keys
    public static final String KEY_CONTEXT_ENABLED = "gal_context_enabled";
    public static final String KEY_CONTEXT_MESSAGE_COUNT = "gal_context_message_count";
    public static final String KEY_HISTORY_THRESHOLD = "gal_history_threshold";
    public static final String KEY_AUTO_SHOW_OPTIONS = "gal_auto_show_options";
    
    // Affinity Keys (好感度功能)
    public static final String KEY_AFFINITY_ENABLED = "gal_affinity_enabled";
    public static final String KEY_AFFINITY_MODEL = "gal_affinity_model";
    
    // Affinity Model Constants
    public static final int AFFINITY_MODEL_MUTUAL = 0;      // 双向奔赴模型
    public static final int AFFINITY_MODEL_BALANCED = 1;    // 加权平衡模型
    public static final int AFFINITY_MODEL_EGOCENTRIC = 2;  // 综合加权模型
    public static final int DEFAULT_AFFINITY_MODEL = AFFINITY_MODEL_EGOCENTRIC; // 默认使用综合加权模型

    // AI Providers
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_KIMI = "kimi";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_QWEN = "qwen";
    public static final String PROVIDER_GLM = "glm";
    public static final String PROVIDER_OLLAMA = "ollama";
    public static final String PROVIDER_BAIDU = "baidu";
    public static final String PROVIDER_SPARK = "spark";
    public static final String PROVIDER_BAICHUAN = "baichuan";
    public static final String PROVIDER_DOUBAO = "doubao";
    public static final String PROVIDER_SENSENOVA = "sensenova";
    public static final String PROVIDER_LINKAI = "linkai";
    public static final String PROVIDER_GROQ = "groq";
    public static final String PROVIDER_TOGETHER = "together";
    public static final String PROVIDER_FIREWORKS = "fireworks";
    public static final String PROVIDER_DEEPINFRA = "deepinfra";
    public static final String PROVIDER_DASHSCOPE = "dashscope";
    public static final String PROVIDER_SILICONFLOW = "siliconflow";
    public static final String PROVIDER_GOOGLE = "google";
    public static final String PROVIDER_CUSTOM = "custom";

    // Default Values - 拆分为三个部分：提示词内容、输入格式、输出格式
    public static final String DEFAULT_PROMPT_CONTENT = 
        "你是一个精通中文互联网文化的沉浸式社交模拟引擎。你的核心任务是根据一段QQ聊天记录，为用户生成3个接下来最可能发送、且能自然维持或推进对话的选项。\n\n" +
        "第一部分：核心能力要求（你是什么）\n" +
        "1. 梗百科与接梗王：你深刻理解并熟练掌握大量的中文网络梗、流行语、表情包文化、谐音梗和段子（例如：\"太吃建模了\"、\"兄弟你好香\"、\"典\"、\"急\"、\"绷\"、\"赢麻了\"、\"哈哈哈夺笋啊\"、\"你不对劲\"、\"这谁顶得住\"等）。你能准确判断聊天上下文是否适合玩梗，并能以最自然的方式将梗融入回应中。\n" +
        "2. 高情商聊天者：你生成的选项永远是一个在真实群聊中\"会聊天\"、\"有趣\"的人会说的话。你杜绝任何终结对话、暴露无知或令人尴尬的回复。\n\n" +
        "第二部分：生成规则（你必须怎么做）\n" +
        "· 上下文依赖：你只能依据\"目标消息\"及它之前的所有历史记录生成选项。你是对话的参与者，不是预言家。\n\n" +
        "第三部分：选项内容设计法则\n" +
        "1. 自然真实原则：\n" +
        "· 选项必须是纯口语化的QQ消息，简短，通常无句号。\n" +
        "· 优先考虑让对话\"有趣地继续下去\"，而不是机械回答。\n" +
        "2. 智能玩梗与避坑原则：\n" +
        "· 当对话明显在玩梗、开玩笑时：你的选项必须接梗或顺着玩笑逻辑延伸，禁止提问或不解风情。\n" +
        "· 例如，上下文在调侃某人\"太吃建模了\"。\n" +
        "· 好选项：\"懂了，你是颜控雷达\"、\"这建模，我直接进行一个卡的打\"、\"兄弟，你有点太饿了\"。\n" +
        "· 坏选项：\"什么是吃建模？\"、\"哦\"、\"所以你喜欢好看的？\"。\n" +
        "· 当话题有玩梗空间时：你可以主动引入一个恰当且轻微的梗来增加趣味。\n" +
        "· 例如，大家在讨论熬夜。\n" +
        "· 好选项：\"我逐渐理解一切（指黑眼圈）\"、\"还在熬夜，鉴定为纯纯的地球OL肝帝\"。\n" +
        "· 不懂的梗怎么办：如果遇到不认识的梗，在选项中绝对不要暴露。通过语境猜个大概，或用通用的、但符合氛围的方式回应（如\"笑死\"、\"好家伙\"、\"？？\"）。\n" +
        "3. 选项差异化强制要求（关键）：三个选项必须代表三种不同的社交姿态或情感倾向，确保用户有真实的选择感，信息不允许完全顺从对面的话，其中一条信息一定要有反对的意思，不要一直追着一个话题来说，群里的信息是很跳脱的，不要在几次聊天后还在反复提及前文内容：\n" +
        "· 选项A（共鸣/支持位）：对发言者或话题内容表示认同、分享相似感受、一起笑。（例：\"确实\"、\"哈哈哈哈我也这样\"、\"说得好\"）\n" +
        "· 选项B（互动/拓展位）：对话题进行轻松调侃、吐槽、提出一个具体的疑问点、或就某个细节展开。这里必须包含\"不同意见\"的可能，可以是玩笑式反对或质疑。（例：\"？你的观点有点逆天\"、\"我不信，除非发图\"、\"等等，你昨天可不是这么说的\"）\n" +
        "· 选项C（推进/转向位）：在保持语境连贯的前提下，将话题引向一个相关的、更个人或更具体的子方向，或做出一个微小的、不越界的行动暗示，但是注意，不要经常如此，经常如此会让人感到厌烦，不要试图每个话题都岔开，都引导到其他方面，不要彻底偏离当前聊天内容。（例：\"你这一说，我突然想玩XX游戏了\"、\"饿了，你们晚饭攻略是？\"、\"这让我想起之前那个...\"）\n" +
        "请严格按照以上法则生成三个选项，并确保它们彼此不同且自然有趣。";
    
    public static final String DEFAULT_INPUT_FORMAT = 
        "输入格式说明：\n" +
        "你收到的消息格式为 [需生成选项]角色名[我][时间]:消息。你只为标记了[需生成选项]的那条消息生成用户的后续回应选项。\n" +
        "注意分辨不同用户名的用户，不能把这些信息的发送者全部当作一个人，不允许用错误对象说的话来询问要添加选项的用户。\n"+
        "要学习模仿标有[我]的用户发送的信息的语言习惯，并且根据所有信息来分析氛围不要说出超出氛围的话。\n"+
        "上下文依赖：你只能依据“目标消息”及它之前的所有历史记录生成选项。你是对话的参与者，不是预言家。"
        ;
    
    public static final String DEFAULT_OUTPUT_FORMAT = 
        "**系统强制命令**\n"+
        "输出格式：你只能输出以下JSON对象，无任何额外文本：\n" +
        "{\\\"options\\\": [\\\"选项一\\\", \\\"选项二\\\", \\\"选项三\\\"]}";
    
    // 合并后的默认提示词（使用分隔符格式，向后兼容）
    public static final String DEFAULT_SYS_PROMPT = DEFAULT_PROMPT_CONTENT + 
        "\n\n---[INPUT_FORMAT]---\n\n" + DEFAULT_INPUT_FORMAT + 
        "\n\n---[OUTPUT_FORMAT]---\n\n" + DEFAULT_OUTPUT_FORMAT;

    //\n系统发送的消息格式为“[当前需生成选项]角色名[我][时间]：信息内容”，其含义如下：\n- [当前需生成选项]：这是一个标记，表示接下来这条消息是需要你为核心玩家生成后续可选回应的目标消息。\n- 角色名：发送此条消息的游戏角色名称。\n- [我]：如果角色名后带有此标记，则表明这条消息是主人公（玩家）自己之前发送的，用于提供上下文。\n- [时间]：消息发生的具体游戏内时间点，用于把握情境（如清晨、放学后、夜晚）。\n\n选项生成核心要求：\n1. 现实感与沉浸感：选项必须是现实生活中一个真实、有同理心的人在该情境下可能做出的自然反应或行动。避免夸张、戏剧化或明显为\"攻略\"而服务的选项。\n2. 性格一致性：选项需符合主人公（玩家）已被设定的基础性格（如温和、直率、内向），并提供符合不同个性侧面的选择，保持代入感。\n3. 情感多样性：三个选项应提供不同的情感或行动方向，例如：\n   - 体贴理解型：展现倾听、支持或细微的关怀。\n   - 真诚互动型：进行平等的分享、提问或轻微的幽默调侃（需符合关系程度）。\n   - 推进关系型：在关系合适时，提出一个具体、不越界的后续行动建议（如\"明天一起整理笔记？\"）。\n   但是不能直接把情感写出来\n4. 表达自然化：选项语言需口语化、自然，像是脑海中直接浮现的想法或脱口而出的话。禁止使用颜文字或过于直白的\"好感度\"提示，可以少量使用网络用语。情感通过措辞、语气和内容本身来传递。\n5. **强制系统命令**必须返回恰好3个选项\n6. **强制系统命令**严格遵守JSON格式返回：{\\\"options\\\": [\\\"选项一\\\",\\\"选项二\\\",\\\"选项三\\\"]}\n**强制系统命令**仅允许返回json内容，不允许返回其他任何内容";
    public static final String DEFAULT_MODEL = "gpt-3.5-turbo";
    public static final String DEFAULT_PROVIDER = PROVIDER_OPENAI;
    public static final float DEFAULT_TEMPERATURE = 0.8f;
    public static final int DEFAULT_MAX_TOKENS = 120;
    public static final String DEFAULT_FILTER_MODE = "blacklist";
    
    // Context Default Values
    public static final boolean DEFAULT_CONTEXT_ENABLED = true;
    public static final int DEFAULT_CONTEXT_MESSAGE_COUNT = 15; // 从10改为15
    public static final int DEFAULT_HISTORY_THRESHOLD = 600; // 历史消息阈值（秒），默认10分钟
    public static final boolean DEFAULT_AUTO_SHOW_OPTIONS = false;
    
    // QPS Default Value
    public static final float DEFAULT_AI_QPS = 3.0f;
    public static final String KEY_AI_QPS = "gal_ai_qps";
    
    // AI Timeout Default Value (主AI超时时间)
    public static final int DEFAULT_AI_TIMEOUT = 30; // 默认30秒
    public static final String KEY_AI_TIMEOUT = "gal_ai_timeout";
    
    // Proxy Keys (代理配置)
    public static final String KEY_PROXY_ENABLED = "gal_proxy_enabled";
    public static final String KEY_PROXY_TYPE = "gal_proxy_type";
    public static final String KEY_PROXY_HOST = "gal_proxy_host";
    public static final String KEY_PROXY_PORT = "gal_proxy_port";
    public static final String KEY_PROXY_AUTH_ENABLED = "gal_proxy_auth_enabled";
    public static final String KEY_PROXY_USERNAME = "gal_proxy_username";
    public static final String KEY_PROXY_PASSWORD = "gal_proxy_password";
    
    // Proxy Default Values
    public static final String DEFAULT_PROXY_TYPE = "HTTP";
    public static final int DEFAULT_PROXY_PORT = 7890;
    
    // ========== Image Recognition Keys (图片识别配置) ==========
    
    // 图片识别开关
    public static final String KEY_IMAGE_RECOGNITION_ENABLED = "gal_image_recognition_enabled";
    public static final String KEY_EMOJI_RECOGNITION_ENABLED = "gal_emoji_recognition_enabled";
    
    // 外挂AI配置
    public static final String KEY_VISION_AI_ENABLED = "gal_vision_ai_enabled";
    public static final String KEY_VISION_API_URL = "gal_vision_api_url";
    public static final String KEY_VISION_API_KEY = "gal_vision_api_key";
    public static final String KEY_VISION_AI_MODEL = "gal_vision_ai_model";
    public static final String KEY_VISION_AI_PROVIDER = "gal_vision_ai_provider";
    public static final String KEY_VISION_USE_PROXY = "gal_vision_use_proxy";
    
    // 图片识别参数
    public static final String KEY_IMAGE_MAX_SIZE = "gal_image_max_size";
    public static final String KEY_IMAGE_DESCRIPTION_MAX_LENGTH = "gal_image_description_max_length";
    public static final String KEY_VISION_TIMEOUT = "gal_vision_timeout";
    public static final String KEY_VISION_AI_QPS = "gal_vision_ai_qps"; // 外挂AI速率配置
    
    // 上下文图片识别配置
    public static final String KEY_CONTEXT_IMAGE_RECOGNITION_ENABLED = "gal_context_image_recognition_enabled";
    
    // Image Recognition Default Values
    public static final boolean DEFAULT_IMAGE_RECOGNITION_ENABLED = false;
    public static final boolean DEFAULT_EMOJI_RECOGNITION_ENABLED = false;
    public static final boolean DEFAULT_VISION_AI_ENABLED = false;
    public static final boolean DEFAULT_VISION_USE_PROXY = false;
    public static final int DEFAULT_IMAGE_MAX_SIZE = 2048; // 2MB (单位: KB)
    public static final int DEFAULT_IMAGE_DESCRIPTION_MAX_LENGTH = 200; // 字符
    public static final int DEFAULT_VISION_TIMEOUT = 30; // 30秒
    public static final String DEFAULT_VISION_AI_MODEL = "gpt-4-vision-preview";
    public static final String DEFAULT_VISION_AI_PROVIDER = PROVIDER_OPENAI;
    public static final boolean DEFAULT_CONTEXT_IMAGE_RECOGNITION_ENABLED = false; // 默认不识别上下文图片
    public static final float DEFAULT_VISION_AI_QPS = 1.0f; // 外挂AI默认速率（图片识别通常较慢，默认1 QPS）

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
    
    /**
     * 是否启用调试Hook日志
     * 用于控制 SendMessageHelper 等类的详细日志输出
     */
    public static boolean isDebugHookLogEnabled() {
        return getMmkv().decodeBool(KEY_DEBUG_HOOK_LOG, false);
    }
    
    public static void setDebugHookLogEnabled(boolean enabled) {
        getMmkv().encode(KEY_DEBUG_HOOK_LOG, enabled);
    }

    // ========== String Methods ==========
    
    public static String getSysPrompt() {
        return getMmkv().decodeString(KEY_SYS_PROMPT, DEFAULT_SYS_PROMPT);
    }
    
    public static void setSysPrompt(String prompt) {
        getMmkv().encode(KEY_SYS_PROMPT, prompt);
    }

    // ========== Prompt List Methods (提示词列表管理) ==========
    
    /**
     * 获取提示词列表（JSON数组格式存储）
     * @return 提示词列表
     */
    public static java.util.List<PromptItem> getPromptList() {
        String json = getMmkv().decodeString(KEY_PROMPT_LIST, "");
        java.util.List<PromptItem> list = new java.util.ArrayList<>();
        if (json == null || json.isEmpty()) {
            // 默认添加一个提示词
            list.add(new PromptItem("默认提示词", DEFAULT_SYS_PROMPT));
            savePromptList(list);
            return list;
        }
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                String name = obj.getString("name");
                String content = obj.getString("content");
                // 兼容旧数据：缺少字段时默认为空字符串/true/false
                String whitelist = obj.optString("whitelist", "");
                String blacklist = obj.optString("blacklist", "");
                boolean enabled = obj.optBoolean("enabled", true);
                boolean whitelistEnabled = obj.optBoolean("whitelistEnabled", false);
                boolean blacklistEnabled = obj.optBoolean("blacklistEnabled", false);
                // 群黑白名单字段（向后兼容：旧数据默认为空/false）
                String groupWhitelist = obj.optString("groupWhitelist", "");
                String groupBlacklist = obj.optString("groupBlacklist", "");
                boolean groupWhitelistEnabled = obj.optBoolean("groupWhitelistEnabled", false);
                boolean groupBlacklistEnabled = obj.optBoolean("groupBlacklistEnabled", false);
                list.add(new PromptItem(name, content, whitelist, blacklist, enabled, 
                        whitelistEnabled, blacklistEnabled, groupWhitelist, groupBlacklist,
                        groupWhitelistEnabled, groupBlacklistEnabled));
            }
        } catch (Exception e) {
            list.add(new PromptItem("默认提示词", DEFAULT_SYS_PROMPT));
        }
        return list;
    }
    
    /**
     * 保存提示词列表
     * @param list 提示词列表
     */
    public static void savePromptList(java.util.List<PromptItem> list) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (PromptItem item : list) {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("name", item.name);
                obj.put("content", item.content);
                obj.put("whitelist", item.whitelist != null ? item.whitelist : "");
                obj.put("blacklist", item.blacklist != null ? item.blacklist : "");
                obj.put("enabled", item.enabled);
                obj.put("whitelistEnabled", item.whitelistEnabled);
                obj.put("blacklistEnabled", item.blacklistEnabled);
                // 群黑白名单字段
                obj.put("groupWhitelist", item.groupWhitelist != null ? item.groupWhitelist : "");
                obj.put("groupBlacklist", item.groupBlacklist != null ? item.groupBlacklist : "");
                obj.put("groupWhitelistEnabled", item.groupWhitelistEnabled);
                obj.put("groupBlacklistEnabled", item.groupBlacklistEnabled);
                arr.put(obj);
            }
            getMmkv().encode(KEY_PROMPT_LIST, arr.toString());
        } catch (Exception e) {
            android.util.Log.e("GalQQ.ConfigManager", "Failed to save prompt list", e);
        }
    }
    
    /**
     * 获取当前选中的提示词索引
     * @return 索引
     */
    public static int getCurrentPromptIndex() {
        return getMmkv().decodeInt(KEY_CURRENT_PROMPT_INDEX, 0);
    }
    
    /**
     * 设置当前选中的提示词索引
     * @param index 索引
     */
    public static void setCurrentPromptIndex(int index) {
        getMmkv().encode(KEY_CURRENT_PROMPT_INDEX, index);
        // 同时更新当前使用的提示词
        java.util.List<PromptItem> list = getPromptList();
        if (index >= 0 && index < list.size()) {
            setSysPrompt(list.get(index).content);
        }
    }
    
    /**
     * 提示词项
     */
    public static class PromptItem {
        public String name;
        public String content;
        public String whitelist;  // 逗号分隔的QQ号，用户白名单
        public String blacklist;  // 逗号分隔的QQ号，用户黑名单
        public boolean enabled;   // 是否启用，禁用时黑白名单都不触发
        public boolean whitelistEnabled;  // 用户白名单功能是否启用
        public boolean blacklistEnabled;  // 用户黑名单功能是否启用
        
        // 群黑白名单字段
        public String groupWhitelist;      // 逗号分隔的群号，群白名单
        public String groupBlacklist;      // 逗号分隔的群号，群黑名单
        public boolean groupWhitelistEnabled;  // 群白名单功能是否启用
        public boolean groupBlacklistEnabled;  // 群黑名单功能是否启用
        
        public PromptItem(String name, String content) {
            this(name, content, "", "", true, false, false, "", "", false, false);
        }
        
        public PromptItem(String name, String content, String whitelist, String blacklist) {
            this(name, content, whitelist, blacklist, true, false, false, "", "", false, false);
        }
        
        public PromptItem(String name, String content, String whitelist, String blacklist, boolean enabled) {
            this(name, content, whitelist, blacklist, enabled, false, false, "", "", false, false);
        }
        
        public PromptItem(String name, String content, String whitelist, String blacklist, 
                         boolean enabled, boolean whitelistEnabled, boolean blacklistEnabled) {
            this(name, content, whitelist, blacklist, enabled, whitelistEnabled, blacklistEnabled, "", "", false, false);
        }
        
        public PromptItem(String name, String content, String whitelist, String blacklist, 
                         boolean enabled, boolean whitelistEnabled, boolean blacklistEnabled,
                         String groupWhitelist, String groupBlacklist, 
                         boolean groupWhitelistEnabled, boolean groupBlacklistEnabled) {
            this.name = name;
            this.content = content;
            this.whitelist = whitelist != null ? whitelist : "";
            this.blacklist = blacklist != null ? blacklist : "";
            this.enabled = enabled;
            this.whitelistEnabled = whitelistEnabled;
            this.blacklistEnabled = blacklistEnabled;
            this.groupWhitelist = groupWhitelist != null ? groupWhitelist : "";
            this.groupBlacklist = groupBlacklist != null ? groupBlacklist : "";
            this.groupWhitelistEnabled = groupWhitelistEnabled;
            this.groupBlacklistEnabled = groupBlacklistEnabled;
        }
        
        /**
         * 检查指定QQ号是否在用户白名单中
         * @param qq QQ号
         * @return true 如果在白名单中且白名单功能启用
         */
        public boolean isInWhitelist(String qq) {
            if (!whitelistEnabled) {
                return false;
            }
            if (qq == null || qq.isEmpty() || whitelist == null || whitelist.isEmpty()) {
                return false;
            }
            java.util.Set<String> validQQs = parseIdList(whitelist);
            return validQQs.contains(qq.trim());
        }
        
        /**
         * 检查指定QQ号是否在用户黑名单中
         * @param qq QQ号
         * @return true 如果在黑名单中且黑名单功能启用
         */
        public boolean isInBlacklist(String qq) {
            if (!blacklistEnabled) {
                return false;
            }
            if (qq == null || qq.isEmpty() || blacklist == null || blacklist.isEmpty()) {
                return false;
            }
            java.util.Set<String> validQQs = parseIdList(blacklist);
            return validQQs.contains(qq.trim());
        }
        
        /**
         * 检查指定群号是否在群白名单中
         * @param groupId 群号
         * @return true 如果在群白名单中且群白名单功能启用
         */
        public boolean isInGroupWhitelist(String groupId) {
            if (!groupWhitelistEnabled) {
                return false;
            }
            if (groupId == null || groupId.isEmpty() || groupWhitelist == null || groupWhitelist.isEmpty()) {
                return false;
            }
            java.util.Set<String> validIds = parseIdList(groupWhitelist);
            return validIds.contains(groupId.trim());
        }
        
        /**
         * 检查指定群号是否在群黑名单中
         * @param groupId 群号
         * @return true 如果在群黑名单中且群黑名单功能启用
         */
        public boolean isInGroupBlacklist(String groupId) {
            if (!groupBlacklistEnabled) {
                return false;
            }
            if (groupId == null || groupId.isEmpty() || groupBlacklist == null || groupBlacklist.isEmpty()) {
                return false;
            }
            java.util.Set<String> validIds = parseIdList(groupBlacklist);
            return validIds.contains(groupId.trim());
        }
        
        /**
         * 解析ID列表（QQ号或群号），过滤无效条目
         * 有效ID：任意长度的纯数字字符串（非空）
         * @param list 逗号分隔的ID字符串
         * @return 有效ID集合
         */
        private java.util.Set<String> parseIdList(String list) {
            java.util.Set<String> result = new java.util.HashSet<>();
            if (list == null || list.isEmpty()) {
                return result;
            }
            String[] parts = list.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                // 有效ID：非空且仅包含数字
                if (!trimmed.isEmpty() && trimmed.matches("\\d+")) {
                    result.add(trimmed);
                }
            }
            return result;
        }
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

    /**
     * 获取AI推理强度 (reasoning_effort)
     * @return "off", "none", "low", "medium", "high"
     */
    public static String getAiReasoningEffort() {
        return getMmkv().decodeString(KEY_AI_REASONING_EFFORT, "off");
    }
    
    /**
     * 设置AI推理强度
     * @param effort "off", "none", "low", "medium", "high"
     */
    public static void setAiReasoningEffort(String effort) {
        getMmkv().encode(KEY_AI_REASONING_EFFORT, effort);
    }
    
    /**
     * 检查是否启用了reasoning_effort参数
     * @return true 如果不是 "off"
     */
    public static boolean isReasoningEffortEnabled() {
        String effort = getAiReasoningEffort();
        return effort != null && !effort.equals("off");
    }

    public static float getAiQps() {
        return getMmkv().decodeFloat(KEY_AI_QPS, DEFAULT_AI_QPS);
    }
    
    public static void setAiQps(float qps) {
        getMmkv().encode(KEY_AI_QPS, qps);
    }
    
    /**
     * 获取主AI请求超时时间（秒）
     * @return 超时时间
     */
    public static int getAiTimeout() {
        return getMmkv().decodeInt(KEY_AI_TIMEOUT, DEFAULT_AI_TIMEOUT);
    }
    
    /**
     * 设置主AI请求超时时间（秒）
     * @param timeout 超时时间
     */
    public static void setAiTimeout(int timeout) {
        getMmkv().encode(KEY_AI_TIMEOUT, timeout);
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
    
    // ========== 群黑白名单配置 ==========
    public static final String KEY_GROUP_BLACKLIST = "gal_group_blacklist";
    public static final String KEY_GROUP_WHITELIST = "gal_group_whitelist";
    public static final String KEY_GROUP_FILTER_MODE = "gal_group_filter_mode";
    public static final String KEY_DISABLE_GROUP_OPTIONS = "gal_disable_group_options";
    
    // 群黑名单
    public static String getGroupBlacklist() {
        return getMmkv().decodeString(KEY_GROUP_BLACKLIST, "");
    }
    
    public static void setGroupBlacklist(String blacklist) {
        getMmkv().encode(KEY_GROUP_BLACKLIST, blacklist);
    }
    
    public static boolean isInGroupBlacklist(String groupId) {
        String blacklist = getGroupBlacklist();
        if (blacklist == null || blacklist.trim().isEmpty()) {
            return false;
        }
        String[] numbers = blacklist.split(",");
        for (String num : numbers) {
            if (num.trim().equals(groupId)) {
                return true;
            }
        }
        return false;
    }
    
    // 群白名单
    public static String getGroupWhitelist() {
        return getMmkv().decodeString(KEY_GROUP_WHITELIST, "");
    }
    
    public static void setGroupWhitelist(String whitelist) {
        getMmkv().encode(KEY_GROUP_WHITELIST, whitelist);
    }
    
    public static boolean isInGroupWhitelist(String groupId) {
        String whitelist = getGroupWhitelist();
        if (whitelist == null || whitelist.trim().isEmpty()) {
            return false;
        }
        String[] numbers = whitelist.split(",");
        for (String num : numbers) {
            if (num.trim().equals(groupId)) {
                return true;
            }
        }
        return false;
    }
    
    // 群过滤模式
    public static String getGroupFilterMode() {
        return getMmkv().decodeString(KEY_GROUP_FILTER_MODE, DEFAULT_FILTER_MODE);
    }
    
    public static void setGroupFilterMode(String mode) {
        getMmkv().encode(KEY_GROUP_FILTER_MODE, mode);
    }
    
    // 关闭群聊选项显示
    public static boolean isDisableGroupOptions() {
        return getMmkv().decodeBool(KEY_DISABLE_GROUP_OPTIONS, false);
    }
    
    public static void setDisableGroupOptions(boolean disabled) {
        getMmkv().encode(KEY_DISABLE_GROUP_OPTIONS, disabled);
    }
    
    /**
     * 检查是否应该在群聊中显示选项
     * @param peerUin 会话ID（群号或私聊QQ号）
     * @param senderUin 发送者QQ号
     * @return true 如果应该显示选项
     */
    public static boolean shouldShowGroupOptions(String peerUin, String senderUin) {
        // 如果peerUin和senderUin相同，说明是私聊，不受群聊设置影响
        if (peerUin != null && peerUin.equals(senderUin)) {
            return true;
        }
        // 群聊场景
        if (isDisableGroupOptions()) {
            return false;
        }
        return true;
    }
    
    /**
     * 检查群是否通过过滤（基于群过滤模式和群黑白名单）
     * 注意：群过滤模式与用户过滤模式是独立的
     * @param groupId 群号
     * @return true 如果群通过过滤
     */
    public static boolean isGroupPassFilter(String groupId) {
        if (groupId == null || groupId.isEmpty()) {
            return true;
        }
        
        String groupFilterMode = getGroupFilterMode();
        if ("whitelist".equals(groupFilterMode)) {
            // 白名单模式：只有在群白名单中的群才通过
            return isInGroupWhitelist(groupId);
        } else {
            // 黑名单模式：不在群黑名单中的群通过
            return !isInGroupBlacklist(groupId);
        }
    }

    // 缓存 verbose log 状态，避免频繁读取 MMKV
    private static volatile Boolean sVerboseLogCache = null;
    private static volatile long sVerboseLogCacheTime = 0;
    private static final long VERBOSE_LOG_CACHE_DURATION = 5000; // 5秒缓存
    
    public static boolean isVerboseLogEnabled() {
        try {
            if (sMmkv == null) {
                return false;
            }
            
            long now = System.currentTimeMillis();
            // 使用缓存，每5秒刷新一次
            if (sVerboseLogCache != null && (now - sVerboseLogCacheTime) < VERBOSE_LOG_CACHE_DURATION) {
                return sVerboseLogCache;
            }
            
            // 检查外部进程是否修改了配置（跨进程同步）
            sMmkv.checkContentChangedByOuterProcess();
            sVerboseLogCache = sMmkv.decodeBool(KEY_VERBOSE_LOG, false);
            sVerboseLogCacheTime = now;
            return sVerboseLogCache;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * 清除 verbose log 缓存，强制下次读取时刷新
     * 在设置界面修改后调用
     */
    public static void clearVerboseLogCache() {
        sVerboseLogCache = null;
        sVerboseLogCacheTime = 0;
    }
    
    public static void setVerboseLogEnabled(boolean enabled) {
        getMmkv().encode(KEY_VERBOSE_LOG, enabled);
        // 清除缓存，确保其他进程能立即读取到新值
        clearVerboseLogCache();
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
        // 限制在1-200之间
        return Math.max(1, Math.min(200, count));
    }
    
    public static void setContextMessageCount(int count) {
        getMmkv().encode(KEY_CONTEXT_MESSAGE_COUNT, count);
    }
    
    public static int getHistoryThreshold() {
        return getMmkv().decodeInt(KEY_HISTORY_THRESHOLD, DEFAULT_HISTORY_THRESHOLD);
    }
    
    public static void setHistoryThreshold(int seconds) {
        getMmkv().encode(KEY_HISTORY_THRESHOLD, seconds);
    }
    
    public static boolean isAutoShowOptionsEnabled() {
        return getMmkv().decodeBool(KEY_AUTO_SHOW_OPTIONS, DEFAULT_AUTO_SHOW_OPTIONS);
    }
    
    public static void setAutoShowOptionsEnabled(boolean enabled) {
        getMmkv().encode(KEY_AUTO_SHOW_OPTIONS, enabled);
    }

    // ========== Affinity Methods (好感度功能) ==========
    
    /**
     * 检查好感度显示功能是否启用
     * @return true 如果启用
     */
    public static boolean isAffinityEnabled() {
        return getMmkv().decodeBool(KEY_AFFINITY_ENABLED, false);
    }
    
    /**
     * 设置好感度显示功能开关
     * @param enabled 是否启用
     */
    public static void setAffinityEnabled(boolean enabled) {
        getMmkv().encode(KEY_AFFINITY_ENABLED, enabled);
    }
    
    /**
     * 获取好感度计算模型
     * @return 模型ID (0=双向奔赴, 1=加权平衡, 2=综合加权)
     */
    public static int getAffinityModel() {
        return getMmkv().decodeInt(KEY_AFFINITY_MODEL, DEFAULT_AFFINITY_MODEL);
    }
    
    /**
     * 设置好感度计算模型
     * @param model 模型ID
     */
    public static void setAffinityModel(int model) {
        getMmkv().encode(KEY_AFFINITY_MODEL, model);
    }
    
    /**
     * 获取好感度模型名称
     * @param model 模型ID
     * @return 模型名称
     */
    public static String getAffinityModelName(int model) {
        switch (model) {
            case AFFINITY_MODEL_MUTUAL:
                return "双向奔赴模型";
            case AFFINITY_MODEL_BALANCED:
                return "加权平衡模型";
            case AFFINITY_MODEL_EGOCENTRIC:
            default:
                return "综合加权模型";
        }
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
    
    // ========== Proxy Methods (代理配置) ==========
    
    /**
     * 检查代理是否启用
     * @return true 如果启用
     */
    public static boolean isProxyEnabled() {
        return getMmkv().decodeBool(KEY_PROXY_ENABLED, false);
    }
    
    /**
     * 设置代理开关
     * @param enabled 是否启用
     */
    public static void setProxyEnabled(boolean enabled) {
        getMmkv().encode(KEY_PROXY_ENABLED, enabled);
    }
    
    /**
     * 获取代理类型 (HTTP/SOCKS)
     * @return 代理类型
     */
    public static String getProxyType() {
        return getMmkv().decodeString(KEY_PROXY_TYPE, DEFAULT_PROXY_TYPE);
    }
    
    /**
     * 设置代理类型
     * @param type 代理类型 (HTTP/SOCKS)
     */
    public static void setProxyType(String type) {
        getMmkv().encode(KEY_PROXY_TYPE, type);
    }
    
    /**
     * 获取代理主机地址
     * @return 代理主机
     */
    public static String getProxyHost() {
        return getMmkv().decodeString(KEY_PROXY_HOST, "");
    }
    
    /**
     * 设置代理主机地址
     * @param host 代理主机
     */
    public static void setProxyHost(String host) {
        getMmkv().encode(KEY_PROXY_HOST, host);
    }
    
    /**
     * 获取代理端口
     * @return 代理端口
     */
    public static int getProxyPort() {
        return getMmkv().decodeInt(KEY_PROXY_PORT, DEFAULT_PROXY_PORT);
    }
    
    /**
     * 设置代理端口
     * @param port 代理端口
     */
    public static void setProxyPort(int port) {
        getMmkv().encode(KEY_PROXY_PORT, port);
    }
    
    /**
     * 检查代理认证是否启用
     * @return true 如果启用认证
     */
    public static boolean isProxyAuthEnabled() {
        return getMmkv().decodeBool(KEY_PROXY_AUTH_ENABLED, false);
    }
    
    /**
     * 设置代理认证开关
     * @param enabled 是否启用认证
     */
    public static void setProxyAuthEnabled(boolean enabled) {
        getMmkv().encode(KEY_PROXY_AUTH_ENABLED, enabled);
    }
    
    /**
     * 获取代理用户名
     * @return 用户名
     */
    public static String getProxyUsername() {
        return getMmkv().decodeString(KEY_PROXY_USERNAME, "");
    }
    
    /**
     * 设置代理用户名
     * @param username 用户名
     */
    public static void setProxyUsername(String username) {
        getMmkv().encode(KEY_PROXY_USERNAME, username);
    }
    
    /**
     * 获取代理密码
     * @return 密码
     */
    public static String getProxyPassword() {
        return getMmkv().decodeString(KEY_PROXY_PASSWORD, "");
    }
    
    /**
     * 设置代理密码
     * @param password 密码
     */
    public static void setProxyPassword(String password) {
        getMmkv().encode(KEY_PROXY_PASSWORD, password);
    }
    
    /**
     * 检查代理配置是否有效
     * @return true 如果代理配置完整且有效
     */
    public static boolean isProxyConfigValid() {
        if (!isProxyEnabled()) {
            return false;
        }
        String host = getProxyHost();
        int port = getProxyPort();
        if (host == null || host.trim().isEmpty() || port <= 0 || port > 65535) {
            return false;
        }
        // 如果启用了认证，检查用户名密码
        if (isProxyAuthEnabled()) {
            String username = getProxyUsername();
            String password = getProxyPassword();
            if (username == null || username.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 根据服务商获取默认API URL
     * @param provider 服务商标识
     * @return 对应的API端点URL，未知服务商返回空字符串
     */
    @NonNull
    public static String getDefaultApiUrl(String provider) {
        if (provider == null) {
            return "";
        }
        switch (provider) {
            case PROVIDER_KIMI:
                return "https://api.moonshot.cn/v1/chat/completions";
            case PROVIDER_BAIDU:
                return "https://qianfan.baidubce.com/v2/chat/completions";
            case PROVIDER_GLM:
                return "https://open.bigmodel.cn/api/paas/v4/chat/completions";
            case PROVIDER_SPARK:
                return "https://spark-api-open.xf-yun.com/v1/chat/completions";
            case PROVIDER_BAICHUAN:
                return "https://api.baichuan-ai.com/v1/chat/completions";
            case PROVIDER_DOUBAO:
                return "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
            case PROVIDER_SENSENOVA:
                return "https://api.sensenova.cn/compatible-mode/v1/chat/completions";
            case PROVIDER_OPENAI:
                return "https://api.openai.com/v1/chat/completions";
            case PROVIDER_LINKAI:
                return "https://api.link-ai.tech/v1/chat/completions";
            case PROVIDER_GROQ:
                return "https://api.groq.com/openai/v1/chat/completions";
            case PROVIDER_TOGETHER:
                return "https://api.together.xyz/v1/chat/completions";
            case PROVIDER_FIREWORKS:
                return "https://api.fireworks.ai/inference/v1/chat/completions";
            case PROVIDER_DEEPINFRA:
                return "https://api.deepinfra.com/v1/openai/chat/completions";
            case PROVIDER_DEEPSEEK:
                return "https://api.deepseek.com/v1/chat/completions";
            case PROVIDER_DASHSCOPE:
                return "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
            case PROVIDER_SILICONFLOW:
                return "https://api.siliconflow.cn/v1/chat/completions";
            case PROVIDER_OLLAMA:
                return "http://localhost:11434/v1/chat/completions";
            case PROVIDER_QWEN:
                return "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
            case PROVIDER_GOOGLE:
                return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
            default:
                return "";
        }
    }

    /**
     * 获取服务商显示名称
     * @param provider 服务商标识
     * @return 服务商的中文显示名称
     */
    @NonNull
    public static String getProviderDisplayName(String provider) {
        if (provider == null) {
            return "未知";
        }
        switch (provider) {
            case PROVIDER_KIMI:
                return "月之暗面 (Kimi)";
            case PROVIDER_BAIDU:
                return "百度千帆 (文心)";
            case PROVIDER_GLM:
                return "智谱AI (GLM-4)";
            case PROVIDER_SPARK:
                return "讯飞星火 (Spark)";
            case PROVIDER_BAICHUAN:
                return "百川智能 (Baichuan)";
            case PROVIDER_DOUBAO:
                return "字节豆包 (Doubao)";
            case PROVIDER_SENSENOVA:
                return "商汤日日新 (SenseNova)";
            case PROVIDER_OPENAI:
                return "OpenAI";
            case PROVIDER_LINKAI:
                return "LinkAI";
            case PROVIDER_GROQ:
                return "Groq";
            case PROVIDER_TOGETHER:
                return "Together.ai";
            case PROVIDER_FIREWORKS:
                return "Fireworks.ai";
            case PROVIDER_DEEPINFRA:
                return "DeepInfra";
            case PROVIDER_DEEPSEEK:
                return "DeepSeek";
            case PROVIDER_DASHSCOPE:
                return "阿里云DashScope";
            case PROVIDER_SILICONFLOW:
                return "硅基流动 (SiliconFlow)";
            case PROVIDER_OLLAMA:
                return "Ollama (本地)";
            case PROVIDER_QWEN:
                return "通义千问 (Qwen)";
            case PROVIDER_GOOGLE:
                return "Google (Gemini)";
            case PROVIDER_CUSTOM:
                return "自定义";
            default:
                return "未知";
        }
    }
    
    // ========== Image Recognition Methods (图片识别配置方法) ==========
    
    /**
     * 检查图片识别功能是否启用
     * @return true 如果启用图片识别
     */
    public static boolean isImageRecognitionEnabled() {
        return getMmkv().decodeBool(KEY_IMAGE_RECOGNITION_ENABLED, DEFAULT_IMAGE_RECOGNITION_ENABLED);
    }
    
    /**
     * 设置图片识别开关
     * @param enabled 是否启用图片识别
     */
    public static void setImageRecognitionEnabled(boolean enabled) {
        getMmkv().encode(KEY_IMAGE_RECOGNITION_ENABLED, enabled);
    }
    
    /**
     * 检查表情包识别功能是否启用
     * @return true 如果启用表情包识别
     */
    public static boolean isEmojiRecognitionEnabled() {
        return getMmkv().decodeBool(KEY_EMOJI_RECOGNITION_ENABLED, DEFAULT_EMOJI_RECOGNITION_ENABLED);
    }
    
    /**
     * 设置表情包识别开关
     * @param enabled 是否启用表情包识别
     */
    public static void setEmojiRecognitionEnabled(boolean enabled) {
        getMmkv().encode(KEY_EMOJI_RECOGNITION_ENABLED, enabled);
    }
    
    /**
     * 检查外挂AI是否启用
     * @return true 如果启用外挂AI
     */
    public static boolean isVisionAiEnabled() {
        return getMmkv().decodeBool(KEY_VISION_AI_ENABLED, DEFAULT_VISION_AI_ENABLED);
    }
    
    /**
     * 设置外挂AI开关
     * @param enabled 是否启用外挂AI
     */
    public static void setVisionAiEnabled(boolean enabled) {
        getMmkv().encode(KEY_VISION_AI_ENABLED, enabled);
    }
    
    /**
     * 获取外挂AI的API URL
     * @return API URL
     */
    public static String getVisionApiUrl() {
        return getMmkv().decodeString(KEY_VISION_API_URL, "");
    }
    
    /**
     * 设置外挂AI的API URL
     * @param url API URL
     */
    public static void setVisionApiUrl(String url) {
        getMmkv().encode(KEY_VISION_API_URL, url);
    }
    
    /**
     * 获取外挂AI的API Key
     * @return API Key
     */
    public static String getVisionApiKey() {
        return getMmkv().decodeString(KEY_VISION_API_KEY, "");
    }
    
    /**
     * 设置外挂AI的API Key
     * @param key API Key
     */
    public static void setVisionApiKey(String key) {
        getMmkv().encode(KEY_VISION_API_KEY, key);
    }
    
    /**
     * 获取外挂AI的模型名称
     * @return 模型名称
     */
    public static String getVisionAiModel() {
        return getMmkv().decodeString(KEY_VISION_AI_MODEL, DEFAULT_VISION_AI_MODEL);
    }
    
    /**
     * 设置外挂AI的模型名称
     * @param model 模型名称
     */
    public static void setVisionAiModel(String model) {
        getMmkv().encode(KEY_VISION_AI_MODEL, model);
    }
    
    /**
     * 获取外挂AI的服务商
     * @return 服务商标识
     */
    public static String getVisionAiProvider() {
        return getMmkv().decodeString(KEY_VISION_AI_PROVIDER, DEFAULT_VISION_AI_PROVIDER);
    }
    
    /**
     * 设置外挂AI的服务商
     * @param provider 服务商标识
     */
    public static void setVisionAiProvider(String provider) {
        getMmkv().encode(KEY_VISION_AI_PROVIDER, provider);
    }
    
    // 外挂AI服务商常量（用于Vision API）
    public static final String VISION_PROVIDER_OPENAI = "openai";
    public static final String VISION_PROVIDER_GOOGLE = "google";
    public static final String VISION_PROVIDER_ANTHROPIC = "anthropic";
    public static final String VISION_PROVIDER_KIMI = "kimi";
    public static final String VISION_PROVIDER_GLM = "glm";
    public static final String VISION_PROVIDER_DASHSCOPE = "dashscope";
    public static final String VISION_PROVIDER_DOUBAO = "doubao";
    public static final String VISION_PROVIDER_BAIDU = "baidu";
    public static final String VISION_PROVIDER_CUSTOM = "custom";
    
    /**
     * 根据外挂AI服务商获取默认API URL
     * @param provider 服务商标识
     * @return 对应的API端点URL，未知服务商返回空字符串
     */
    @NonNull
    public static String getDefaultVisionApiUrl(String provider) {
        if (provider == null) {
            return "";
        }
        switch (provider) {
            case VISION_PROVIDER_OPENAI:
                return "https://api.openai.com/v1/chat/completions";
            case VISION_PROVIDER_GOOGLE:
                return "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
            case VISION_PROVIDER_ANTHROPIC:
                return "https://api.anthropic.com/v1/chat/completions";
            case VISION_PROVIDER_KIMI:
                return "https://api.moonshot.cn/v1/chat/completions";
            case VISION_PROVIDER_GLM:
                return "https://open.bigmodel.cn/api/paas/v4/chat/completions";
            case VISION_PROVIDER_DASHSCOPE:
                return "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
            case VISION_PROVIDER_DOUBAO:
                return "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
            case VISION_PROVIDER_BAIDU:
                return "https://qianfan.baidubce.com/v2/chat/completions";
            default:
                return "";
        }
    }
    
    /**
     * 获取外挂AI服务商显示名称
     * @param provider 服务商标识
     * @return 服务商的中文显示名称
     */
    @NonNull
    public static String getVisionProviderDisplayName(String provider) {
        if (provider == null) {
            return "未知";
        }
        switch (provider) {
            case VISION_PROVIDER_OPENAI:
                return "OpenAI (GPT-4 Vision)";
            case VISION_PROVIDER_GOOGLE:
                return "Google (Gemini Vision)";
            case VISION_PROVIDER_ANTHROPIC:
                return "Anthropic (Claude Vision)";
            case VISION_PROVIDER_KIMI:
                return "月之暗面 (Kimi Vision)";
            case VISION_PROVIDER_GLM:
                return "智谱AI (GLM-4V)";
            case VISION_PROVIDER_DASHSCOPE:
                return "阿里云DashScope (通义千问VL)";
            case VISION_PROVIDER_DOUBAO:
                return "字节豆包 (Doubao Vision)";
            case VISION_PROVIDER_BAIDU:
                return "百度千帆 (文心视觉)";
            case VISION_PROVIDER_CUSTOM:
                return "自定义";
            default:
                return "未知";
        }
    }
    
    /**
     * 检查外挂AI是否使用代理
     * @return true 如果使用代理
     */
    public static boolean isVisionUseProxy() {
        return getMmkv().decodeBool(KEY_VISION_USE_PROXY, DEFAULT_VISION_USE_PROXY);
    }
    
    /**
     * 设置外挂AI是否使用代理
     * @param useProxy 是否使用代理
     */
    public static void setVisionUseProxy(boolean useProxy) {
        getMmkv().encode(KEY_VISION_USE_PROXY, useProxy);
    }
    
    /**
     * 获取图片大小限制(KB)
     * @return 图片大小限制
     */
    public static int getImageMaxSize() {
        return getMmkv().decodeInt(KEY_IMAGE_MAX_SIZE, DEFAULT_IMAGE_MAX_SIZE);
    }
    
    /**
     * 设置图片大小限制(KB)
     * @param maxSize 图片大小限制
     */
    public static void setImageMaxSize(int maxSize) {
        getMmkv().encode(KEY_IMAGE_MAX_SIZE, maxSize);
    }
    
    /**
     * 获取图片描述最大长度
     * @return 描述最大长度
     */
    public static int getImageDescriptionMaxLength() {
        return getMmkv().decodeInt(KEY_IMAGE_DESCRIPTION_MAX_LENGTH, DEFAULT_IMAGE_DESCRIPTION_MAX_LENGTH);
    }
    
    /**
     * 设置图片描述最大长度
     * @param maxLength 描述最大长度
     */
    public static void setImageDescriptionMaxLength(int maxLength) {
        getMmkv().encode(KEY_IMAGE_DESCRIPTION_MAX_LENGTH, maxLength);
    }
    
    /**
     * 获取外挂AI超时时间(秒)
     * @return 超时时间
     */
    public static int getVisionTimeout() {
        return getMmkv().decodeInt(KEY_VISION_TIMEOUT, DEFAULT_VISION_TIMEOUT);
    }
    
    /**
     * 设置外挂AI超时时间(秒)
     * @param timeout 超时时间
     */
    public static void setVisionTimeout(int timeout) {
        getMmkv().encode(KEY_VISION_TIMEOUT, timeout);
    }
    
    /**
     * 检查图片识别配置是否有效
     * @return true 如果配置完整且有效
     */
    public static boolean isImageRecognitionConfigValid() {
        // 如果图片识别未启用,返回false
        if (!isImageRecognitionEnabled()) {
            return false;
        }
        
        // 如果启用了外挂AI,检查外挂AI配置
        if (isVisionAiEnabled()) {
            String apiUrl = getVisionApiUrl();
            String apiKey = getVisionApiKey();
            String model = getVisionAiModel();
            
            // API URL和模型名称必须非空
            if (apiUrl == null || apiUrl.trim().isEmpty()) {
                return false;
            }
            if (model == null || model.trim().isEmpty()) {
                return false;
            }
            // API Key可以为空(某些服务不需要)
        }
        // 如果未启用外挂AI,则使用主AI配置,无需额外验证
        
        return true;
    }
    
    /**
     * 检查上下文图片识别是否启用
     * 启用后会识别上下文中所有消息的图片，而不仅仅是当前消息
     * @return true 如果启用上下文图片识别
     */
    public static boolean isContextImageRecognitionEnabled() {
        return getMmkv().decodeBool(KEY_CONTEXT_IMAGE_RECOGNITION_ENABLED, DEFAULT_CONTEXT_IMAGE_RECOGNITION_ENABLED);
    }
    
    /**
     * 设置上下文图片识别开关
     * @param enabled 是否启用上下文图片识别
     */
    public static void setContextImageRecognitionEnabled(boolean enabled) {
        getMmkv().encode(KEY_CONTEXT_IMAGE_RECOGNITION_ENABLED, enabled);
    }
    
    /**
     * 获取外挂AI请求速率 (QPS)
     * @return 每秒最大请求数
     */
    public static float getVisionAiQps() {
        return getMmkv().decodeFloat(KEY_VISION_AI_QPS, DEFAULT_VISION_AI_QPS);
    }
    
    /**
     * 设置外挂AI请求速率 (QPS)
     * @param qps 每秒最大请求数
     */
    public static void setVisionAiQps(float qps) {
        getMmkv().encode(KEY_VISION_AI_QPS, qps);
    }
}
