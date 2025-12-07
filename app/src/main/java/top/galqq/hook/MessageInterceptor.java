package top.galqq.hook;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import top.galqq.config.ConfigManager;
import top.galqq.utils.AiRateLimitedQueue;
import top.galqq.utils.DictionaryManager;
import top.galqq.utils.HttpAiClient;
import top.galqq.utils.MessageContextManager;
import top.galqq.utils.QAppUtils;
import java.lang.reflect.Field;
import top.galqq.utils.SendMessageHelper;
import top.galqq.config.ConfigManager;

public class MessageInterceptor {

    private static final String TAG = "GalQQ.MessageInterceptor";
    private static final int OPTION_BAR_ID = 0x7F0A1234; // Custom ID for option bar
    
    /**
     * 调试日志输出（受 gal_debug_hook_log 配置开关控制）
     */
    private static void debugLog(String message) {
        try {
            if (ConfigManager.isDebugHookLogEnabled()) {
                XposedBridge.log(message);
            }
        } catch (Throwable ignored) {
            // ConfigManager 未初始化时忽略
        }
    }
    
    /**
     * 调试日志输出异常（受 gal_debug_hook_log 配置开关控制）
     */
    private static void debugLog(Throwable t) {
        try {
            if (ConfigManager.isDebugHookLogEnabled()) {
                XposedBridge.log(t);
            }
        } catch (Throwable ignored) {
            // ConfigManager 未初始化时忽略
        }
    }
    
    /**
     * 调试日志输出异常（受 gal_debug_hook_log 配置开关控制）
     */
    private static void debugLog(Exception e) {
        try {
            if (ConfigManager.isDebugHookLogEnabled()) {
                XposedBridge.log(e);
            }
        } catch (Throwable ignored) {
            // ConfigManager 未初始化时忽略
        }
    }
    
    // AI选项缓存：msgId -> List<String> options
    private static final java.util.Map<String, java.util.List<String>> optionsCache = 
        new java.util.LinkedHashMap<String, java.util.List<String>>(100, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, java.util.List<String>> eldest) {
                return size() > 100; // 最多缓存100条消息的选项
            }
        };

    // 记录已请求显示选项的消息ID，防止View复用时重置回按钮状态
    private static final java.util.Set<String> requestedOptionsMsgIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    
    // 记录已收起的消息ID，用于区分「显示选项」和「展开选项」按钮
    private static final java.util.Set<String> collapsedMsgIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    
    // 好感度缓存: senderUin -> affinity，用于View复用时快速获取
    // 这个缓存与 AffinityManager 的缓存不同，这里是为了解决 View 复用时的显示问题
    private static final java.util.Map<String, Integer> affinityDisplayCache = 
        java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<String, Integer>(200, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, Integer> eldest) {
                return size() > 200; // 最多缓存200个用户的好感度
            }
        });
    
    /**
     * 清空好感度显示缓存
     * 当用户修改计算模型时需要调用此方法，以便重新计算好感度
     */
    public static void clearAffinityDisplayCache() {
        affinityDisplayCache.clear();
        XposedBridge.log(TAG + ": 好感度显示缓存已清空");
    }

    public static void init(ClassLoader classLoader) {
        // Detect QQ architecture and use appropriate hook strategy
        if (top.galqq.utils.QQNTUtils.isQQNT(classLoader)) {
            debugLog(TAG + ": Detected QQNT, using QQNT hook strategy");
            hookAIOBubbleMsgItemVB(classLoader);  // QQNT architecture
            
            // 【DEBUG】Hook AIOSendMsgVMDelegate to analyze message structure
            hookDebugAIOSendMsgVMDelegate(classLoader);
        } else {
            debugLog(TAG + ": Detected legacy QQ, using TextItemBuilder hook strategy");
            hookTextItemBuilder(classLoader);      // Legacy QQ architecture
        }
    }
    
    // 标记好感度管理器是否已初始化
    private static boolean sAffinityManagerInitialized = false;
    
    /**
     * 初始化好感度管理器（需要在有 Context 时调用）
     * 只会初始化一次
     */
    private static void initAffinityManager(Context context) {
        if (sAffinityManagerInitialized) {
            return; // 已经初始化过了
        }
        
        if (ConfigManager.isAffinityEnabled()) {
            try {
                debugLog(TAG + ": [Affinity] 开始初始化好感度管理器...");
                top.galqq.utils.AffinityManager affinityManager = 
                    top.galqq.utils.AffinityManager.getInstance(context);
                // 强制刷新数据
                affinityManager.refreshData(true, new top.galqq.utils.AffinityManager.RefreshCallback() {
                    @Override
                    public void onSuccess() {
                        debugLog(TAG + ": [Affinity] ✓ 好感度数据刷新成功");
                    }
                    
                    @Override
                    public void onFailure(Exception e) {
                        debugLog(TAG + ": [Affinity] ✗ 好感度数据刷新失败: " + e.getMessage());
                    }
                });
                sAffinityManagerInitialized = true;
                debugLog(TAG + ": [Affinity] 好感度管理器初始化完成");
            } catch (Throwable t) {
                debugLog(TAG + ": [Affinity] 初始化失败: " + t.getMessage());
            }
        }
    }

    private static void hookTextItemBuilder(ClassLoader classLoader) {
        try {
            // Try more indices to handle different QQ versions
            Class<?> textItemBuilderClass = top.galqq.utils.ReflectionUtils.findClassWithSynthetics(
                "com.tencent.mobileqq.activity.aio.item.TextItemBuilder", 
                classLoader,
                10, 7, 6, 3, 8, 1, 2, 4, 5, 9, 11, 12, 13, 14, 15
            );
            
            if (textItemBuilderClass == null) {
                debugLog(TAG + ": TextItemBuilder class not found, skipping hook");
                return;
            }
            
            debugLog(TAG + ": Found TextItemBuilder class: " + textItemBuilderClass.getName());
            
            // Find the target method
            Method targetMethod = null;
            String methodName = null;
            
            // Try both method names
            for (String name : new String[]{"F", "a"}) {
                for (Method m : textItemBuilderClass.getDeclaredMethods()) {
                    if (!m.getName().equals(name)) continue;
                    if (!View.class.isAssignableFrom(m.getReturnType())) continue;
                    
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 5 && 
                        View.class.isAssignableFrom(params[2]) && 
                        ViewGroup.class.isAssignableFrom(params[3])) {
                        targetMethod = m;
                        methodName = name;
                        break;
                    }
                }
                if (targetMethod != null) break;
            }

            if (targetMethod == null) {
                debugLog(TAG + ": Failed to find target method in TextItemBuilder");
                return;
            }
            
            debugLog(TAG + ": Found target method: " + methodName);
            
            // Hook the method using QQ's approach (modify BaseChatItemLayout directly)
            XposedBridge.hookMethod(targetMethod, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Object chatMessage = param.args[0];
                        RelativeLayout baseChatItemLayout = (RelativeLayout) param.args[3];
                        
                        if (baseChatItemLayout == null) return;
                        
                        Context context = baseChatItemLayout.getContext();
                        ConfigManager.init(context);
                        
                        if (!ConfigManager.isModuleEnabled()) return;

                        boolean isSend = XposedHelpers.getBooleanField(chatMessage, "isSend");
                        if (isSend) return;
                        
                        // 如果选项条已存在，总是更新内容（解决视图复用导致的内容混乱）
                        if (baseChatItemLayout.findViewById(OPTION_BAR_ID) != null) {
                            LinearLayout existingBar = baseChatItemLayout.findViewById(OPTION_BAR_ID);
                            existingBar.removeAllViews();
                            String msgContent = (String) XposedHelpers.getObjectField(chatMessage, "msg");
                            setupOptionBarContent(context, existingBar, msgContent, chatMessage, null, null);
                            return;
                        }
                        
                        LinearLayout optionBar = createOptionBar(context, chatMessage);
                        optionBar.setId(OPTION_BAR_ID);
                        
                        // 获取屏幕宽度
                        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                        int barWidth = screenWidth - dp2px(context, 24); // 左8+右16=24dp
                        
                        debugLog(TAG + ": Screen width=" + screenWidth + ", bar width=" + barWidth);
                        
                        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                            barWidth, // 使用计算出的具体宽度
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                        params.leftMargin = dp2px(context, 8);  // 左边距8dp
                        params.rightMargin = dp2px(context, 16); // 右边距16dp（冗余但保留）
                        params.bottomMargin = dp2px(context, 5);
                        
                        baseChatItemLayout.addView(optionBar, params);
                        
                        debugLog(TAG + ": Successfully added option bar to BaseChatItemLayout");
                        
                    } catch (Throwable t) {
                        debugLog(TAG + ": Error in afterHook: " + t.getMessage());
                    }
                }
            });
            
            debugLog(TAG + ": ✓ Successfully hooked TextItemBuilder." + methodName);
            
        } catch (Throwable t) {
            debugLog(TAG + ": Failed to hook TextItemBuilder: " + t.getMessage());
        }
    }

    private static LinearLayout createOptionBar(Context context, Object chatMessage) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.LEFT);
        bar.setPadding(0, dp2px(context, 5), 0, dp2px(context, 5));
        
        String msgContent = (String) XposedHelpers.getObjectField(chatMessage, "msg");
        setupOptionBarContent(context, bar, msgContent, chatMessage, null, null);
        return bar;
    }
    
    private static void updateOptionBar(Context context, LinearLayout bar, Object chatMessage) {
        String msgContent = (String) XposedHelpers.getObjectField(chatMessage, "msg");
        bar.removeAllViews();
        setupOptionBarContent(context, bar, msgContent, chatMessage, null, null);
    }

    // 公开缓存方法供队列恢复时使用
    public static void cacheOptions(String msgId, List<String> options) {
        if (msgId != null && options != null) {
            optionsCache.put(msgId, new java.util.ArrayList<>(options));
            // debugLog(TAG + ": Cached AI options for msgId=" + msgId);
        }
    }

    private static void setupOptionBarContent(Context context, LinearLayout bar, String msgContent, 
                                               Object msgObj, String msgId, String conversationId) {
        // 调用带 rootView 参数的版本，rootView 为 null 时不添加操作按钮
        setupOptionBarContentWithRoot(context, bar, msgContent, msgObj, msgId, conversationId, null);
    }
    
    private static void setupOptionBarContentWithRoot(Context context, LinearLayout bar, String msgContent, 
                                               Object msgObj, String msgId, String conversationId, ViewGroup rootView) {
        // 提取senderQQ和peerUin，用于群聊过滤
        String senderQQ = null;
        String peerUin = null;
        try {
            Object senderUinObj = XposedHelpers.getObjectField(msgObj, "senderUin");
            if (senderUinObj != null) {
                senderQQ = String.valueOf(senderUinObj);
            }
            Object peerUinObj = XposedHelpers.getObjectField(msgObj, "peerUin");
            if (peerUinObj != null) {
                peerUin = String.valueOf(peerUinObj);
            }
        } catch (Throwable t) {
            debugLog(TAG + ": Failed to extract sender/peer info for pre-check: " + t.getMessage());
        }
        
        // 群聊选项显示控制：在方法开头就检查，避免显示"加载中"后再隐藏
        boolean isGroupChat = peerUin != null && senderQQ != null && !peerUin.equals(senderQQ);
        if (isGroupChat) {
            // 检查是否关闭群聊选项显示
            if (ConfigManager.isDisableGroupOptions()) {
                debugLog(TAG + ": Group options disabled, hiding option bar for group: " + peerUin);
                bar.setVisibility(View.GONE);
                return;
            }
            
            // 检查群是否通过过滤（基于群黑白名单和群过滤模式）
            if (!ConfigManager.isGroupPassFilter(peerUin)) {
                debugLog(TAG + ": Group " + peerUin + " filtered out, hiding option bar");
                bar.setVisibility(View.GONE);
                return;
            }
        }
        
        if (ConfigManager.isAiEnabled()) {
            // 添加加载指示器 (Loading Text)
            bar.removeAllViews();
            // 加载时减少顶部间距，使其更贴近消息
            bar.setPadding(0, 0, 0, dp2px(context, 5));
            
            TextView tvLoading = new TextView(context);
            tvLoading.setText("加载中");
            tvLoading.setTextSize(12); // 小字体
            tvLoading.setTextColor(Color.parseColor("#999999")); // 浅灰色
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            lp.leftMargin = dp2px(context, 2); // 稍微偏移一点
            tvLoading.setLayoutParams(lp);
            
            bar.addView(tvLoading);
            bar.setVisibility(View.VISIBLE);

            // 启动呼吸动画 "..." (变长变短)
            final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            final Runnable animator = new Runnable() {
                int count = 0;
                @Override
                public void run() {
                    // 检查View是否还在显示，如果已被移除或隐藏则停止动画
                    if (tvLoading.getParent() == null || bar.getVisibility() != View.VISIBLE) {
                        return;
                    }
                    
                    StringBuilder sb = new StringBuilder("加载中");
                    // 动画逻辑：0->1->2->3->2->1->0 (循环)
                    int phase = count % 6;
                    int dots = phase <= 3 ? phase : 6 - phase;
                    
                    for (int i = 0; i < dots; i++) sb.append(".");
                    tvLoading.setText(sb.toString());
                    
                    count++;
                    handler.postDelayed(this, 400); // 400ms刷新一次
                }
            };
            handler.postDelayed(animator, 400);
            
            // 获取上下文消息（如果启用）
            List<MessageContextManager.ChatMessage> contextMessages = null;
            if (ConfigManager.isContextEnabled() && conversationId != null) {
                int contextCount = ConfigManager.getContextMessageCount();
                
                // 【优化】判断是否启用自动显示选项
                boolean autoShow = ConfigManager.isAutoShowOptionsEnabled();
                
                if (autoShow) {
                    // 启用自动显示时，从缓存的上下文管理器获取（已通过拦截缓存）
                    debugLog(TAG + ": [AUTO_SHOW] 从上下文管理器缓存获取消息");
                    // 多获取一条，以便如果最后一条是当前消息时移除
                    contextMessages = MessageContextManager.getContext(conversationId, contextCount + 1);
                    
                    // 去除当前消息（如果它已经被存入上下文）
                    if (!contextMessages.isEmpty()) {
                        MessageContextManager.ChatMessage lastMsg = contextMessages.get(contextMessages.size() - 1);
                        // 通过msgId判断（如果msgId不为空）
                        boolean isSameMsg = false;
                        if (msgId != null && lastMsg.msgId != null) {
                            if (msgId.equals(lastMsg.msgId)) {
                                isSameMsg = true;
                            }
                        } else {
                            // 降级：通过内容和时间戳判断（防止重复）
                            // 如果内容相同且时间差在1秒内
                            if (msgContent.equals(lastMsg.content) && 
                                Math.abs(System.currentTimeMillis() - lastMsg.timestamp) < 1000) {
                                isSameMsg = true;
                            }
                        }
                        
                        if (isSameMsg) {
                            contextMessages.remove(contextMessages.size() - 1);
                        }
                    }
                    
                    // 确保数量不超过配置
                    if (contextMessages.size() > contextCount) {
                        contextMessages = contextMessages.subList(contextMessages.size() - contextCount, contextMessages.size());
                    }
                } else {
                    // 未启用自动显示时，从内存缓存获取（如果有的话）
                    debugLog(TAG + ": [ON_DEMAND] 从内存缓存获取上下文");
                    try {
                        contextMessages = MessageContextManager.getContext(conversationId, contextCount + 1);
                        
                        // 去除当前消息（如果获取到了）
                        if (!contextMessages.isEmpty()) {
                            MessageContextManager.ChatMessage lastMsg = contextMessages.get(contextMessages.size() - 1);
                            boolean isSameMsg = false;
                            if (msgId != null && lastMsg.msgId != null && msgId.equals(lastMsg.msgId)) {
                                isSameMsg = true;
                            }
                            if (isSameMsg) {
                                contextMessages.remove(contextMessages.size() - 1);
                            }
                        }
                        
                        // 确保数量不超过配置
                        if (contextMessages.size() > contextCount) {
                            contextMessages = contextMessages.subList(contextMessages.size() - contextCount, contextMessages.size());
                        }
                        
                        debugLog(TAG + ": [ON_DEMAND] 成功获取 " + contextMessages.size() + " 条本地消息");
                    } catch (Throwable t) {
                        debugLog(TAG + ": [ON_DEMAND] 从本地获取消息失败，使用空上下文: " + t.getMessage());
                        contextMessages = new java.util.ArrayList<>();
                    }
                }
            }

            // 判断选项条是否在屏幕可见区域（用于设置优先级）
            android.graphics.Rect rect = new android.graphics.Rect();
            boolean isVisible = bar.getGlobalVisibleRect(rect) && bar.isShown();
            AiRateLimitedQueue.Priority priority = isVisible ? 
                AiRateLimitedQueue.Priority.HIGH : 
                AiRateLimitedQueue.Priority.NORMAL;
            
            // 【新增】提取当前消息的元数据（发送人昵称、时间戳）
            // 注意：senderQQ和peerUin已在方法开头提取
            String currentSenderName = null;
            long currentTimestamp = 0;
            try {
                // 尝试从msgObj（msgRecord）提取senderName
                Object remarkNameObj = XposedHelpers.getObjectField(msgObj, "sendRemarkName");
                if (remarkNameObj != null && !String.valueOf(remarkNameObj).trim().isEmpty()) {
                    currentSenderName = String.valueOf(remarkNameObj);
                } else {
                    Object nickNameObj = XposedHelpers.getObjectField(msgObj, "sendNickName");
                    if (nickNameObj != null) {
                        currentSenderName = String.valueOf(nickNameObj);
                    }
                }
                
                // 提取时间戳
                Object msgTimeObj = XposedHelpers.getObjectField(msgObj, "msgTime");
                if (msgTimeObj != null) {
                    currentTimestamp = Long.parseLong(String.valueOf(msgTimeObj)) * 1000L; // 秒转毫秒
                }
            } catch (Throwable t) {
                // 提取失败，使用默认值（null和0）
                debugLog(TAG + ": Failed to extract current message metadata: " + t.getMessage());
            }
            
            // 使用 PromptSelector 选择合适的提示词（传递peerUin作为groupId）
            // 注意：peerUin和senderQQ已在方法开头提取
            java.util.List<ConfigManager.PromptItem> allPrompts = ConfigManager.getPromptList();
            ConfigManager.PromptItem selectedPrompt = top.galqq.utils.PromptSelector.getSelectedPrompt(
                allPrompts, senderQQ, peerUin, ConfigManager.isAiEnabled());
            
            // 如果没有可用的提示词（全部被屏蔽），隐藏选项栏
            if (selectedPrompt == null) {
                debugLog(TAG + ": No available prompt for sender: " + senderQQ + ", hiding option bar");
                bar.setVisibility(View.GONE);
                return;
            }
            
            String customPrompt = selectedPrompt.content;
            debugLog(TAG + ": Using prompt: " + selectedPrompt.name + " for sender: " + senderQQ);
            
            // 【图片识别】提取消息中的图片元素
            java.util.List<top.galqq.utils.ImageExtractor.ImageElement> imageElements = null;
            if (ConfigManager.isImageRecognitionEnabled() && msgObj != null) {
                try {
                    imageElements = top.galqq.utils.ImageExtractor.extractImages(msgObj);
                    if (imageElements != null && !imageElements.isEmpty()) {
                        debugLog(TAG + ": 检测到 " + imageElements.size() + " 张图片");
                    }
                } catch (Throwable t) {
                    debugLog(TAG + ": 图片提取失败: " + t.getMessage());
                }
            }
            
            // 提交到限流队列（带优先级、上下文、发送者QQ、自定义提示词、图片元素和会话ID）
            // 使用支持重试的回调接口
            final String finalSenderQQ = senderQQ;
            final String finalCustomPrompt = customPrompt;
            final java.util.List<top.galqq.utils.ImageExtractor.ImageElement> finalImageElements = imageElements;
            AiRateLimitedQueue.getInstance(context).submitRequest(
                context, 
                msgContent, 
                msgId, // 传递msgId用于持久化
                priority,
                contextMessages, // 传递上下文消息
                currentSenderName, // 当前消息发送人昵称
                currentTimestamp, // 当前消息时间戳
                finalSenderQQ, // 发送者QQ号
                finalCustomPrompt, // 自定义提示词
                finalImageElements, // 图片元素列表
                conversationId, // 会话ID（用于图片描述缓存）
                new HttpAiClient.AiCallbackWithRetry() {
                    @Override
                    public void onSuccess(List<String> options) {
                        // 恢复顶部间距
                        bar.setPadding(0, dp2px(context, 5), 0, dp2px(context, 5));
                        
                        // 缓存AI结果
                        cacheOptions(msgId, options);
                        
                        // 如果有 rootView，使用带操作按钮的版本
                        if (rootView != null) {
                            populateBarAndShowWithActions(context, bar, options, msgObj, msgId, conversationId, rootView);
                        } else {
                            populateBarAndShow(context, bar, options, msgObj);
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        // AI失败时隐藏选项条（已在UI线程）
                        bar.setVisibility(View.GONE);
                    }
                    
                    @Override
                    public void onAllRetriesFailed(Runnable retryAction) {
                        // 所有重试都失败后，显示"重新加载"按钮
                        showReloadButton(context, bar, retryAction, msgObj);
                    }
                }
            );
        } else {
            // 本地词库模式：每次随机生成，不使用缓存
            useDictionaryNT(context, bar, msgObj);
        }
    }

    /**
     * 显示"重新加载"按钮（当所有重试都失败后）
     * 使用与选项按钮相同的UI风格
     */
    private static void showReloadButton(Context context, LinearLayout bar, Runnable retryAction, Object chatMessage) {
        bar.removeAllViews();
        bar.setPadding(0, dp2px(context, 5), 0, dp2px(context, 5));
        
        TextView reloadBtn = new TextView(context);
        reloadBtn.setText("重新加载");
        reloadBtn.setTextSize(13);
        reloadBtn.setPadding(dp2px(context, 12), dp2px(context, 8), dp2px(context, 12), dp2px(context, 8));
        // 使用浅红色背景表示错误状态
        reloadBtn.setBackground(getSelectableRoundedBackground(Color.parseColor("#FFEBEE"), dp2px(context, 12)));
        reloadBtn.setTextColor(Color.parseColor("#D32F2F"));
        reloadBtn.setClickable(true);
        reloadBtn.setFocusable(true);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.START;
        reloadBtn.setLayoutParams(lp);
        
        reloadBtn.setOnClickListener(v -> {
            // 点击后显示加载状态
            bar.removeAllViews();
            bar.setPadding(0, 0, 0, dp2px(context, 5));
            
            TextView tvLoading = new TextView(context);
            tvLoading.setText("重新加载中...");
            tvLoading.setTextSize(12);
            tvLoading.setTextColor(Color.parseColor("#999999"));
            LinearLayout.LayoutParams loadingLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            loadingLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            loadingLp.leftMargin = dp2px(context, 2);
            tvLoading.setLayoutParams(loadingLp);
            bar.addView(tvLoading);
            
            // 执行重试
            retryAction.run();
        });
        
        bar.addView(reloadBtn);
        bar.setVisibility(View.VISIBLE);
        
        debugLog(TAG + ": Showing reload button after all retries failed");
    }

    private static void useDictionary(Context context, LinearLayout bar, Object chatMessage) {
        DictionaryManager.loadDictionary(context);
        List<String> options = DictionaryManager.pickRandomLines(3);
        populateBarAndShow(context, bar, options, chatMessage);
    }

    // 填充选项条并显示（如果有选项的话）
    // 支持双击引用回复功能
    private static void populateBarAndShow(Context context, LinearLayout bar, List<String> options, Object chatMessage) {
        bar.removeAllViews();
        
        debugLog(TAG + ": populateBarAndShow - options count=" + (options != null ? options.size() : "null"));
        
        if (options == null || options.isEmpty()) {
            debugLog(TAG + ": No options available, hiding bar");
            bar.setVisibility(View.GONE); // 没有选项时隐藏
            return;
        }
        
        // 提取消息的引用回复信息
        long replyMsgId = 0L;
        long replyMsgSeq = 0L;
        String replyNick = "";
        String replyContent = "";
        
        try {
            // 尝试从 chatMessage 中提取 msgId (long 类型)
            Object msgIdObj = XposedHelpers.getObjectField(chatMessage, "msgId");
            if (msgIdObj instanceof Long) {
                replyMsgId = (Long) msgIdObj;
            } else if (msgIdObj != null) {
                try {
                    replyMsgId = Long.parseLong(String.valueOf(msgIdObj));
                } catch (NumberFormatException ignored) {}
            }
            
            // 尝试提取消息序列号
            try {
                Object msgSeqObj = XposedHelpers.getObjectField(chatMessage, "msgSeq");
                if (msgSeqObj instanceof Long) {
                    replyMsgSeq = (Long) msgSeqObj;
                } else if (msgSeqObj instanceof Integer) {
                    replyMsgSeq = ((Integer) msgSeqObj).longValue();
                }
            } catch (Throwable t) {
                // 尝试其他可能的字段名
                try {
                    Object seqObj = XposedHelpers.getObjectField(chatMessage, "seq");
                    if (seqObj instanceof Long) {
                        replyMsgSeq = (Long) seqObj;
                    } else if (seqObj instanceof Integer) {
                        replyMsgSeq = ((Integer) seqObj).longValue();
                    }
                } catch (Throwable ignored) {}
            }
            
            // 提取发送者昵称
            try {
                Object remarkNameObj = XposedHelpers.getObjectField(chatMessage, "sendRemarkName");
                if (remarkNameObj != null && !String.valueOf(remarkNameObj).trim().isEmpty()) {
                    replyNick = String.valueOf(remarkNameObj);
                } else {
                    Object nickNameObj = XposedHelpers.getObjectField(chatMessage, "sendNickName");
                    if (nickNameObj != null) {
                        replyNick = String.valueOf(nickNameObj);
                    }
                }
            } catch (Throwable ignored) {}
            
            // 提取消息内容
            try {
                Object contentObj = XposedHelpers.getObjectField(chatMessage, "msgContent");
                if (contentObj != null) {
                    replyContent = replyNick + ":" + String.valueOf(contentObj);
                }
            } catch (Throwable ignored) {}
            
            debugLog(TAG + ": 提取引用信息 - msgId=" + replyMsgId + ", seq=" + replyMsgSeq + ", nick=" + replyNick);
            
        } catch (Throwable t) {
            debugLog(TAG + ": 提取引用信息失败: " + t.getMessage());
        }
        
        // 保存引用信息供点击事件使用
        final long finalReplyMsgId = replyMsgId;
        final long finalReplyMsgSeq = replyMsgSeq;
        final String finalReplyNick = replyNick;
        final String finalReplyContent = replyContent;
        
        debugLog(TAG + ": Adding " + options.size() + " options to bar");
        for (String option : options) {
            TextView tv = new TextView(context);
            tv.setText(option);
            tv.setTextSize(13);
            tv.setPadding(dp2px(context, 12), dp2px(context, 8), dp2px(context, 12), dp2px(context, 8));
            // 使用带按压状态的背景，支持视觉反馈
            tv.setBackground(getSelectableRoundedBackground(Color.parseColor("#F2F2F2"), dp2px(context, 12)));
            tv.setTextColor(Color.BLACK);
            // 启用点击和焦点，确保按压状态生效
            tv.setClickable(true);
            tv.setFocusable(true);
            
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int maxWidth = screenWidth - dp2px(context, 16);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, dp2px(context, 6));
            tv.setMaxWidth(maxWidth);
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            
            // 单击事件 - 直接发送消息
            tv.setOnClickListener(v -> {
                debugLog(TAG + ": 选项被点击: " + option);
                sendMessage(context, option, chatMessage);
            });
            
            // 长按：弹出编辑对话框（包含引用发送选项）
            tv.setOnLongClickListener(v -> {
                // 触觉反馈
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                // 显示编辑对话框，传递引用信息
                showEditDialog(context, option, chatMessage, finalReplyMsgId, finalReplyMsgSeq, finalReplyNick, finalReplyContent);
                return true; // 消费事件，防止触发 onClick
            });
            
            bar.addView(tv);
        }
        
        bar.setVisibility(View.VISIBLE); // 有选项时显示
        debugLog(TAG + ": Option bar populated and made visible (单击=发送, 长按=编辑/引用)");
    }


    private static void sendMessage(Context context, String text, Object originMsg) {
        try {
            // Check if it's QQNT MsgRecord
            if (originMsg.getClass().getName().contains("qqnt")) {
                // Use SendMessageHelper for QQNT
                SendMessageHelper.sendMessageNT(context, originMsg, text);
                return;
            }

            Class<?> baseAppClass = XposedHelpers.findClass(
                "com.tencent.common.app.BaseApplicationImpl", 
                context.getClassLoader()
            );
            Object app = XposedHelpers.callStaticMethod(baseAppClass, "getApplication");
            Object runtime = XposedHelpers.callMethod(app, "getRuntime");
            
            Class<?> sessionInfoClass = XposedHelpers.findClass(
                "com.tencent.mobileqq.activity.aio.SessionInfo", 
                context.getClassLoader()
            );
            Object session = sessionInfoClass.newInstance();
            
            int istroop = XposedHelpers.getIntField(originMsg, "istroop");
            String frienduin = (String) XposedHelpers.getObjectField(originMsg, "frienduin");
            
            XposedHelpers.setIntField(session, "curType", istroop);
            XposedHelpers.setObjectField(session, "curFriendUin", frienduin);
            
            Class<?> facadeClass = XposedHelpers.findClass(
                "com.tencent.mobileqq.activity.ChatActivityFacade", 
                context.getClassLoader()
            );
            XposedHelpers.callStaticMethod(facadeClass, "sendMessage", runtime, session, text);
            
            Toast.makeText(context, "已发送: " + text, Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            debugLog(TAG + ": Failed to send message: " + e.getMessage());
            Toast.makeText(context, "发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示编辑对话框，允许用户修改选项内容后发送
     * 使用自定义 UI，风格更现代美观
     * @param context 上下文
     * @param originalText 原始选项文本
     * @param chatMessage 消息对象（用于发送）
     */
    private static void showEditDialog(Context context, String originalText, Object chatMessage) {
        // 调用带引用参数的版本，引用信息为空
        showEditDialog(context, originalText, chatMessage, 0, 0, "", "");
    }
    
    /**
     * 显示编辑对话框，允许用户修改选项内容后发送（支持引用回复）
     * @param context 上下文
     * @param originalText 原始选项文本
     * @param chatMessage 消息对象（用于发送）
     * @param replyMsgId 引用消息ID
     * @param replyMsgSeq 引用消息序列号
     * @param replyNick 引用消息发送者昵称
     * @param replyContent 引用消息内容
     */
    private static void showEditDialog(Context context, String originalText, Object chatMessage,
                                       long replyMsgId, long replyMsgSeq, String replyNick, String replyContent) {
        // 创建半透明遮罩层
        android.widget.FrameLayout overlay = new android.widget.FrameLayout(context);
        overlay.setBackgroundColor(Color.parseColor("#80000000")); // 半透明黑色
        
        // 创建对话框容器（白色圆角卡片）
        LinearLayout dialogContainer = new LinearLayout(context);
        dialogContainer.setOrientation(LinearLayout.VERTICAL);
        dialogContainer.setBackgroundDrawable(createDialogBackground());
        dialogContainer.setPadding(dp2px(context, 20), dp2px(context, 20), dp2px(context, 20), dp2px(context, 16));
        
        // 计算对话框宽度（屏幕宽度 - 48dp 边距）
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int dialogWidth = screenWidth - dp2px(context, 48);
        
        android.widget.FrameLayout.LayoutParams dialogParams = new android.widget.FrameLayout.LayoutParams(
            dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dialogParams.gravity = Gravity.CENTER;
        dialogContainer.setLayoutParams(dialogParams);
        
        // 标题
        TextView titleView = new TextView(context);
        titleView.setText("编辑回复");
        titleView.setTextSize(18);
        titleView.setTextColor(Color.parseColor("#333333"));
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = dp2px(context, 16);
        titleView.setLayoutParams(titleParams);
        dialogContainer.addView(titleView);
        
        // 输入框容器（带边框的圆角背景）
        android.widget.FrameLayout inputContainer = new android.widget.FrameLayout(context);
        inputContainer.setBackgroundDrawable(createInputBackground());
        inputContainer.setPadding(dp2px(context, 12), dp2px(context, 8), dp2px(context, 12), dp2px(context, 8));
        LinearLayout.LayoutParams inputContainerParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        inputContainerParams.bottomMargin = dp2px(context, 20);
        inputContainer.setLayoutParams(inputContainerParams);
        
        // 输入框
        android.widget.EditText editText = new android.widget.EditText(context);
        editText.setText(originalText);
        editText.setSelection(originalText.length());
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setMinLines(2);
        editText.setMaxLines(6);
        editText.setTextSize(15);
        editText.setTextColor(Color.parseColor("#333333"));
        editText.setHintTextColor(Color.parseColor("#999999"));
        editText.setHint("输入回复内容...");
        editText.setBackgroundColor(Color.TRANSPARENT); // 去掉默认下划线
        editText.setPadding(0, 0, 0, 0);
        editText.setGravity(Gravity.TOP | Gravity.START);
        inputContainer.addView(editText, new android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        dialogContainer.addView(inputContainer);
        
        // 按钮容器
        LinearLayout buttonContainer = new LinearLayout(context);
        buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
        buttonContainer.setGravity(Gravity.END);
        LinearLayout.LayoutParams buttonContainerParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonContainer.setLayoutParams(buttonContainerParams);
        
        // 创建 Dialog 引用（用于关闭）
        final android.app.Dialog[] dialogRef = new android.app.Dialog[1];
        
        // 取消按钮
        TextView cancelBtn = createDialogButton(context, "取消", false);
        cancelBtn.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cancelParams.rightMargin = dp2px(context, 12);
        cancelBtn.setLayoutParams(cancelParams);
        buttonContainer.addView(cancelBtn);
        
        // 引用发送按钮（始终显示）
        TextView replyBtn = createDialogButton(context, "引用", false);
        // 设置引用按钮的特殊样式（浅蓝色背景）
        android.graphics.drawable.GradientDrawable replyBg = new android.graphics.drawable.GradientDrawable();
        replyBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        replyBg.setCornerRadius(dp2px(context, 20));
        replyBg.setColor(Color.parseColor("#E3F2FD"));
        replyBtn.setBackground(replyBg);
        replyBtn.setTextColor(Color.parseColor("#1976D2"));
        
        replyBtn.setOnClickListener(v -> {
            String modifiedText = editText.getText().toString().trim();
            if (!modifiedText.isEmpty()) {
                debugLog(TAG + ": 引用发送 - msgId=" + replyMsgId + ", text=" + modifiedText);
                // 尝试发送引用回复，如果失败会自动回退到普通发送
                SendMessageHelper.sendReplyMessageNT(context, chatMessage, modifiedText,
                    replyMsgId, replyMsgSeq, replyNick, replyContent);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            } else {
                Toast.makeText(context, "内容不能为空", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams replyParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        replyParams.rightMargin = dp2px(context, 12);
        replyBtn.setLayoutParams(replyParams);
        buttonContainer.addView(replyBtn);
        
        // 发送按钮
        TextView sendBtn = createDialogButton(context, "发送", true);
        sendBtn.setOnClickListener(v -> {
            String modifiedText = editText.getText().toString().trim();
            if (!modifiedText.isEmpty()) {
                sendMessage(context, modifiedText, chatMessage);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            } else {
                Toast.makeText(context, "内容不能为空", Toast.LENGTH_SHORT).show();
            }
        });
        buttonContainer.addView(sendBtn);
        
        dialogContainer.addView(buttonContainer);
        
        // 将对话框添加到遮罩层
        overlay.addView(dialogContainer);
        
        // 点击遮罩层关闭对话框
        overlay.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        
        // 阻止点击对话框时关闭
        dialogContainer.setOnClickListener(v -> {});
        
        // 创建 Dialog
        android.app.Dialog dialog = new android.app.Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
        dialog.setContentView(overlay);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialogRef[0] = dialog;
        
        // 设置 Enter 键发送
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND || 
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                String modifiedText = editText.getText().toString().trim();
                if (!modifiedText.isEmpty()) {
                    sendMessage(context, modifiedText, chatMessage);
                    dialog.dismiss();
                }
                return true;
            }
            return false;
        });
        
        dialog.show();
        
        // 自动显示键盘
        editText.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }
    
    /**
     * 创建对话框背景（白色圆角）
     */
    private static android.graphics.drawable.Drawable createDialogBackground() {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(24); // 12dp 圆角
        return drawable;
    }
    
    /**
     * 创建输入框背景（浅灰色圆角边框）
     */
    private static android.graphics.drawable.Drawable createInputBackground() {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        drawable.setColor(Color.parseColor("#F5F5F5"));
        drawable.setCornerRadius(16); // 8dp 圆角
        drawable.setStroke(2, Color.parseColor("#E0E0E0")); // 1dp 边框
        return drawable;
    }
    
    /**
     * 创建对话框按钮
     * @param context 上下文
     * @param text 按钮文字
     * @param isPrimary 是否为主按钮（发送按钮）
     */
    private static TextView createDialogButton(Context context, String text, boolean isPrimary) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp2px(context, 20), dp2px(context, 10), dp2px(context, 20), dp2px(context, 10));
        
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp2px(context, 20)); // 圆角按钮
        
        if (isPrimary) {
            // 主按钮：蓝色背景，白色文字
            bg.setColor(Color.parseColor("#4A90D9"));
            button.setTextColor(Color.WHITE);
        } else {
            // 次按钮：透明背景，灰色文字
            bg.setColor(Color.TRANSPARENT);
            button.setTextColor(Color.parseColor("#666666"));
        }
        
        button.setBackgroundDrawable(bg);
        button.setClickable(true);
        button.setFocusable(true);
        
        return button;
    }
    
    // ========== QQNT Architecture Hook ==========
    
    private static void hookAIOBubbleMsgItemVB(ClassLoader classLoader) {
        try {
            Class<?> kAIOBubbleMsgItemVB = Class.forName(
                "com.tencent.mobileqq.aio.msglist.holder.AIOBubbleMsgItemVB",
                false,
                classLoader
            );
            
            Class<?> kAIOMsgItem = Class.forName("com.tencent.mobileqq.aio.msg.AIOMsgItem", false, classLoader);
            Method getMsgRecord = kAIOMsgItem.getMethod("getMsgRecord");
            
            // 1. Try to find handleUIState (Newer QQNT)
            Method handleUIState = null;
            try {
                for (Method m : kAIOBubbleMsgItemVB.getDeclaredMethods()) {
                    if ("handleUIState".equals(m.getName())) {
                        handleUIState = m;
                        break;
                    }
                }
            } catch (Exception e) {}

            if (handleUIState != null) {
                debugLog(TAG + ": Found handleUIState method");
                XposedBridge.hookMethod(handleUIState, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object uiState = param.args[0];
                        if (uiState == null) return;
                        
                        // QAuxiliary calls "b()" on uiState to get msgItem
                        Object msgItem = null;
                        try {
                            Method b = uiState.getClass().getMethod("b");
                            b.setAccessible(true);
                            msgItem = b.invoke(uiState);
                        } catch (Exception e) {
                            // Fallback: search for method returning AIOMsgItem
                            for (Method m : uiState.getClass().getDeclaredMethods()) {
                                if (m.getParameterCount() == 0 && kAIOMsgItem.isAssignableFrom(m.getReturnType())) {
                                    m.setAccessible(true);
                                    msgItem = m.invoke(uiState);
                                    break;
                                }
                            }
                        }
                        
                        if (msgItem == null) return;
                        
                        processQQNTMessage(param.thisObject, msgItem, getMsgRecord);
                    }
                });
                return;
            }

            // 2. Fallback to bind method (Older QQNT)
            Method bindMethod = null;
            for (Method m : kAIOBubbleMsgItemVB.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (m.getReturnType() == Void.TYPE && params.length == 4) {
                    // Check params: int, AIOMsgItem(or super), List, Bundle
                    if (params[0] == Integer.TYPE && 
                        params[1].isAssignableFrom(kAIOMsgItem) && // Check if params[1] is super of AIOMsgItem
                        params[2] == List.class &&
                        params[3] == android.os.Bundle.class) {
                        bindMethod = m;
                        break;
                    }
                }
            }
            
            if (bindMethod != null) {
                debugLog(TAG + ": Found bind method");
                XposedBridge.hookMethod(bindMethod, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object msgItem = param.args[1];
                        processQQNTMessage(param.thisObject, msgItem, getMsgRecord);
                    }
                });
                return;
            }
            
            debugLog(TAG + ": Failed to find handleUIState or bind method in AIOBubbleMsgItemVB");

        } catch (Throwable t) {
            debugLog(TAG + ": Error in hookAIOBubbleMsgItemVB: " + t.getMessage());
            debugLog(t);
        }
    }

    /**
     * 调试消息结构 - 分析msgRecord中的图片和表情包元素
     * 受 gal_debug_hook_log 配置开关控制
     */
    private static void debugMessageStructure(Object msgRecord) {
        if (!ConfigManager.isDebugHookLogEnabled()) {
            return; // 调试日志未启用，直接返回
        }
        
        try {
            debugLog(TAG + ": ========== 开始分析消息结构 ==========");
            
            // 1. 分析msgRecord的基本信息
            debugLog(TAG + ": msgRecord类型: " + msgRecord.getClass().getName());
            
            // 2. 获取elements列表
            List<?> elements = null;
            try {
                elements = (List<?>) XposedHelpers.getObjectField(msgRecord, "elements");
                if (elements == null || elements.isEmpty()) {
                    debugLog(TAG + ": ⚠️ elements为空或不存在");
                    return;
                }
                debugLog(TAG + ": ✓ elements数量: " + elements.size());
            } catch (Throwable t) {
                debugLog(TAG + ": ✗ 获取elements失败: " + t.getMessage());
                return;
            }
            
            // 3. 遍历每个element
            for (int i = 0; i < elements.size(); i++) {
                Object element = elements.get(i);
                debugLog(TAG + ": --- Element[" + i + "] ---");
                debugLog(TAG + ":   类型: " + element.getClass().getName());
                
                // 4. 分析element的所有字段
                try {
                    Field[] fields = element.getClass().getDeclaredFields();
                    for (Field field : fields) {
                        field.setAccessible(true);
                        try {
                            Object value = field.get(element);
                            String valueStr = (value != null) ? value.toString() : "null";
                            // 限制输出长度
                            if (valueStr.length() > 200) {
                                valueStr = valueStr.substring(0, 200) + "...";
                            }
                            debugLog(TAG + ":   ." + field.getName() + " = " + valueStr);
                            
                            // 如果字段值是对象,尝试分析其类型
                            if (value != null && !field.getType().isPrimitive() && !field.getType().equals(String.class)) {
                                debugLog(TAG + ":     └─ 类型: " + value.getClass().getName());
                                
                                // 特别关注可能是图片或表情包的字段
                                String fieldName = field.getName().toLowerCase();
                                if (fieldName.contains("pic") || fieldName.contains("image") || 
                                    fieldName.contains("face") || fieldName.contains("emoji")) {
                                    debugLog(TAG + ":     ⭐ 可能包含图片/表情包数据!");
                                    // 递归分析这个对象的字段
                                    analyzeObjectFields(value, "       ");
                                }
                            }
                        } catch (Throwable t) {
                            debugLog(TAG + ":   ." + field.getName() + " - 访问失败: " + t.getMessage());
                        }
                    }
                } catch (Throwable t) {
                    debugLog(TAG + ":   分析字段失败: " + t.getMessage());
                }
            }
            
            debugLog(TAG + ": ========== 消息结构分析完成 ==========");
            
        } catch (Throwable t) {
            debugLog(TAG + ": debugMessageStructure失败: " + t.getMessage());
            debugLog(t);
        }
    }
    
    /**
     * 递归分析对象的字段（用于深入分析图片/表情包元素）
     */
    private static void analyzeObjectFields(Object obj, String indent) {
        if (obj == null) return;
        
        try {
            Field[] fields = obj.getClass().getDeclaredFields();
            int maxFields = Math.min(fields.length, 20); // 限制最多分析20个字段
            
            for (int i = 0; i < maxFields; i++) {
                Field field = fields[i];
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    String valueStr = (value != null) ? value.toString() : "null";
                    if (valueStr.length() > 100) {
                        valueStr = valueStr.substring(0, 100) + "...";
                    }
                    debugLog(TAG + ": " + indent + "." + field.getName() + " = " + valueStr);
                    
                    if (value != null && !field.getType().isPrimitive() && !field.getType().equals(String.class)) {
                        debugLog(TAG + ": " + indent + "  └─ 类型: " + value.getClass().getName());
                    }
                } catch (Throwable t) {
                    // 忽略访问失败的字段
                }
            }
            
            if (fields.length > maxFields) {
                debugLog(TAG + ": " + indent + "... (还有 " + (fields.length - maxFields) + " 个字段未显示)");
            }
        } catch (Throwable t) {
            debugLog(TAG + ": " + indent + "分析对象字段失败: " + t.getMessage());
        }
    }

    private static void processQQNTMessage(Object aioBubbleMsgItemVB, Object msgItem, Method getMsgRecord) {
        try {
            // Get MsgRecord
            Object msgRecord = getMsgRecord.invoke(msgItem);
            
            // 【调试】分析消息结构（仅当包含图片或表情包时）
            try {
                List<?> elements = (List<?>) XposedHelpers.getObjectField(msgRecord, "elements");
                if (elements != null && !elements.isEmpty()) {
                    // 检查是否包含图片或表情包
                    boolean hasImageOrEmoji = false;
                    for (Object element : elements) {
                        String className = element.getClass().getName().toLowerCase();
                        if (className.contains("pic") || className.contains("image") || 
                            className.contains("face") || className.contains("emoji")) {
                            hasImageOrEmoji = true;
                            break;
                        }
                    }
                    
                    // 如果包含图片或表情包，执行调试分析
                    if (hasImageOrEmoji) {
                        debugMessageStructure(msgRecord);
                    }
                }
            } catch (Throwable t) {
                // 忽略调试失败
            }
            
            // Get root ViewGroup via getHostView
            Method getHostView = aioBubbleMsgItemVB.getClass().getMethod("getHostView");
            ViewGroup rootView = (ViewGroup) getHostView.invoke(aioBubbleMsgItemVB);
            
            if (rootView == null || msgRecord == null) return;
            
            Context context = rootView.getContext();
            ConfigManager.init(context);
            
            // 【方法1】识别 Activity/Context 类型
            try {
                String activityName = "unknown";
                if (context instanceof Activity) {
                    activityName = context.getClass().getName();
                } else {
                    // 尝试解包 ContextWrapper
                    Context currentCtx = context;
                    while (currentCtx instanceof android.content.ContextWrapper) {
                        if (currentCtx instanceof Activity) {
                            activityName = currentCtx.getClass().getName();
                            break;
                        }
                        currentCtx = ((android.content.ContextWrapper) currentCtx).getBaseContext();
                    }
                }
                // debugLog(TAG + ": [Context] Activity/Context: " + activityName);
                
                // 【转发消息过滤】如果是转发消息详情页，则不显示AI选项条
                if (activityName != null && activityName.contains("MultiForwardActivity")) {
                    debugLog(TAG + ": ⚠️ Skipping forwarded message in MultiForwardActivity");
                    return;
                }
            } catch (Throwable t) {
                // debugLog(TAG + ": [Context] Error getting activity: " + t.getMessage());
            }
            
            // 【方法2】识别 ViewHolder 类型
            try {
                String viewHolderName = aioBubbleMsgItemVB.getClass().getName();
                // debugLog(TAG + ": [ViewHolder] Type: " + viewHolderName);
            } catch (Throwable t) {
                // debugLog(TAG + ": [ViewHolder] Error: " + t.getMessage());
            }
            
            // 【方法3】识别 RecyclerView 的 Adapter 类型 (使用反射)
            try {
                android.view.ViewParent parent = rootView.getParent();
                boolean foundRecyclerView = false;
                while (parent != null) {
                    Class<?> parentClass = parent.getClass();
                    // 检查是否是 RecyclerView (通过类名判断，避免依赖)
                    if (isRecyclerView(parentClass)) {
                        foundRecyclerView = true;
                        // 反射调用 getAdapter
                        try {
                            Method getAdapterMethod = parentClass.getMethod("getAdapter");
                            Object adapter = getAdapterMethod.invoke(parent);
                            if (adapter != null) {
                                // debugLog(TAG + ": [Adapter] Type: " + adapter.getClass().getName());
                            } else {
                                // debugLog(TAG + ": [Adapter] Adapter is null");
                            }
                        } catch (NoSuchMethodException e) {
                            // 可能是混淆后的名字，或者不是标准的RecyclerView
                            // debugLog(TAG + ": [Adapter] getAdapter method not found on " + parentClass.getName());
                        }
                        break;
                    }
                    parent = parent.getParent();
                }
                if (!foundRecyclerView) {
                    // debugLog(TAG + ": [Adapter] RecyclerView not found in parent hierarchy");
                }
            } catch (Throwable t) {
                debugLog(TAG + ": [Adapter] Error: " + t.getMessage());
            }
            
            // Check if module is enabled
            if (!ConfigManager.isModuleEnabled()) {
                return; // Module is disabled, don't show option bar
            }
            
            // 先获取 peerUin，用于后续判断聊天类型
            String peerUin = null;
            try {
                Object peerUinObj = XposedHelpers.getObjectField(msgRecord, "peerUin");
                peerUin = String.valueOf(peerUinObj);
            } catch (Throwable t) {
                debugLog(TAG + ": Failed to get peerUin: " + t.getMessage());
            }
            
            // Check if it's a received message
            int sendType = XposedHelpers.getIntField(msgRecord, "sendType");
            boolean isSelfBySendType = (sendType == 1); // 1=自己发送, 0=收到的消息
            
            // 判断是群聊还是私聊
            // 在群聊中：peerUin = 群号，senderUin = 发送者QQ
            // 在私聊中：peerUin = 对方QQ = senderUin（当对方发消息时）
            boolean isGroupChat = false;
            String senderUinForDebug = null;
            try {
                Object senderUinObj = XposedHelpers.getObjectField(msgRecord, "senderUin");
                String senderUinStr = String.valueOf(senderUinObj);
                senderUinForDebug = senderUinStr;
                
                // 如果 peerUin != senderUin，说明是群聊
                isGroupChat = (peerUin != null && senderUinStr != null && !peerUin.equals(senderUinStr));
            } catch (Throwable t) {
                debugLog(TAG + ": [isSelf] Failed to get senderUin: " + t.getMessage());
            }
            
            // 在私聊中，只用 sendType 判断；在群聊中，需要额外用 senderUin 判断
            boolean isSelfBySenderUin = false;
            long currentUinForDebug = 0;
            if (isGroupChat) {
                // 群聊：需要比较 senderUin 和当前登录用户UIN
                try {
                    long currentUin = top.galqq.utils.AppRuntimeHelper.getLongAccountUin(context);
                    currentUinForDebug = currentUin;
                    if (currentUin > 0 && senderUinForDebug != null && !senderUinForDebug.isEmpty()) {
                        isSelfBySenderUin = senderUinForDebug.equals(String.valueOf(currentUin));
                    }
                } catch (Throwable t) {
                    debugLog(TAG + ": [isSelf] Failed to compare senderUin: " + t.getMessage());
                }
            }
            
            // 根据聊天类型判断：
            // - 私聊：直接用 sendType（sendType == 1 表示自己发的）
            // - 群聊：sendType == 1 或 senderUin == currentUin
            boolean isSelf = isGroupChat ? (isSelfBySendType || isSelfBySenderUin) : isSelfBySendType;
            
            // 【调试日志】输出判断详情
            debugLog(TAG + ": [isSelf_CHECK] chatType=" + (isGroupChat ? "GROUP" : "PRIVATE") + 
                    ", sendType=" + sendType + ", isSelfBySendType=" + isSelfBySendType + 
                    ", senderUin=" + senderUinForDebug + ", currentUin=" + currentUinForDebug + 
                    ", isSelfBySenderUin=" + isSelfBySenderUin + ", peerUin=" + peerUin + 
                    ", FINAL_isSelf=" + isSelf);

            // Filter out unwanted message types
            int msgType = XposedHelpers.getIntField(msgRecord, "msgType");
            
            // 【过滤转发聊天记录容器】msgType=11且subMsgType=7是转发聊天记录的容器消息
            try {
                int subMsgType = XposedHelpers.getIntField(msgRecord, "subMsgType");
                
                // debugLog(TAG + ": ===== Message Type Analysis =====");
                // debugLog(TAG + ": msgType=" + msgType + ", subMsgType=" + subMsgType);
                // debugLog(TAG + ": sendType=" + sendType);
                
                if (msgType == 11 && subMsgType == 7) {
                    // debugLog(TAG + ": ⚠️ FORWARDED CHAT RECORD CONTAINER - SKIPPING!");
                    return; // 跳过转发聊天记录容器
                }
                
                // debugLog(TAG + ": ================================");
            } catch (Throwable t) {
                // debugLog(TAG + ": Error detecting forwarded message: " + t.getMessage());
            }
            
            // 5 = Gray Tips (Revoke), 3 = File, 7 = Video
            if (msgType == 5 || msgType == 3 || msgType == 7) {
                return;
            }

            // 获取文字内容
            String msgContent = getMessageContentNT(msgRecord);
            
            // 【图片识别】提取图片和表情包元素
            java.util.List<top.galqq.utils.ImageExtractor.ImageElement> imageElements = null;
            java.util.List<top.galqq.utils.ImageExtractor.EmojiElement> emojiElements = null;
            boolean hasImages = false;
            boolean hasEmojis = false;
            
            if (ConfigManager.isImageRecognitionEnabled() || ConfigManager.isEmojiRecognitionEnabled()) {
                try {
                    if (ConfigManager.isImageRecognitionEnabled()) {
                        imageElements = top.galqq.utils.ImageExtractor.extractImages(msgRecord);
                        hasImages = imageElements != null && !imageElements.isEmpty();
                        if (hasImages && ConfigManager.isDebugHookLogEnabled()) {
                            debugLog(TAG + ": 提取到 " + imageElements.size() + " 张图片");
                        }
                    }
                    
                    if (ConfigManager.isEmojiRecognitionEnabled()) {
                        emojiElements = top.galqq.utils.ImageExtractor.extractEmojis(msgRecord);
                        hasEmojis = emojiElements != null && !emojiElements.isEmpty();
                        if (hasEmojis && ConfigManager.isDebugHookLogEnabled()) {
                            debugLog(TAG + ": 提取到 " + emojiElements.size() + " 个表情包");
                        }
                    }
                } catch (Throwable t) {
                    debugLog(TAG + ": 图片/表情包提取失败: " + t.getMessage());
                }
            }
            
            // 如果没有文字内容且没有图片/表情包,则跳过
            if (msgContent.isEmpty() && !hasImages && !hasEmojis) {
                return;
            }
            
            // 【图片识别】合并图片描述到消息内容
            if (hasImages || hasEmojis) {
                java.util.List<String> imageDescriptions = null;
                java.util.List<String> emojiDescriptions = null;
                
                if (hasEmojis) {
                    // 表情包可以直接获取描述
                    emojiDescriptions = top.galqq.utils.ImageContextManager.createEmojiDescriptions(emojiElements);
                }
                
                if (hasImages) {
                    // 根据是否启用外挂AI决定如何处理图片
                    if (ConfigManager.isVisionAiEnabled()) {
                        // 启用外挂AI时,使用占位符(后续异步识别)
                        imageDescriptions = top.galqq.utils.ImageContextManager.createPlaceholderDescriptions(imageElements);
                    } else {
                        // 未启用外挂AI时,直接把图片信息发送给主AI
                        imageDescriptions = new java.util.ArrayList<>();
                        for (top.galqq.utils.ImageExtractor.ImageElement img : imageElements) {
                            // 使用新的getDescriptionForAi方法获取完整描述
                            String desc = img.getDescriptionForAi();
                            imageDescriptions.add(desc);
                            
                            if (ConfigManager.isDebugHookLogEnabled()) {
                                debugLog(TAG + ": 图片描述: " + desc);
                                debugLog(TAG + ":   sourcePath=" + img.sourcePath);
                                debugLog(TAG + ":   imageUrl=" + img.imageUrl);
                            }
                        }
                    }
                }
                
                // 合并内容
                msgContent = top.galqq.utils.ImageContextManager.mergeImageContext(
                    msgContent, imageDescriptions, emojiDescriptions);
                
                if (ConfigManager.isDebugHookLogEnabled()) {
                    debugLog(TAG + ": 合并后消息内容: " + msgContent);
                }
            }
            
            // 无条件清理旧选项条和好感度视图（RecyclerView的ViewHolder会复用）
            // 使用View接收，避免ClassCastException（因为可能是LinearLayout也可能是TextView）
            View existingView = rootView.findViewById(OPTION_BAR_ID);
            if (existingView != null) {
                if (existingView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) existingView.getParent()).removeView(existingView);
                } else {
                    rootView.removeView(existingView);
                }
            }
            
            // 清理好感度视图 - 遍历所有可能的位置
            // 好感度视图可能被添加到 rootView 或其子 ViewGroup 中
            removeAffinityViewRecursively(rootView);
            
            // 黑白名单过滤
            String senderUin = null;
            try {
                Object senderUinObj = XposedHelpers.getObjectField(msgRecord, "senderUin");
                senderUin = String.valueOf(senderUinObj);
                String filterMode = ConfigManager.getFilterMode();
                
                // debugLog(TAG + ": Filter - senderUin=" + senderUin + ", mode=" + filterMode);
                
                if ("blacklist".equals(filterMode)) {
                    if (ConfigManager.isInBlacklist(senderUin)) {
                        // debugLog(TAG + ": ✗ BLOCKED by blacklist: " + senderUin);
                        return; // 不通过过滤，不添加选项条
                    }
                } else if ("whitelist".equals(filterMode)) {
                    if (!ConfigManager.isInWhitelist(senderUin)) {
                        // debugLog(TAG + ": ✗ BLOCKED by whitelist (not in list): " + senderUin);
                        return; // 不通过过滤，不添加选项条
                    }
                }
                // debugLog(TAG + ": ✓ PASSED filter: " + senderUin);
                
                // 【详细调试日志】已禁用
                // debugLog(TAG + ": isSelf=" + isSelf);
                // debugLog(TAG + ": senderUin=" + senderUin);
                // if (msgContent != null && !msgContent.isEmpty()) {
                //     String contentPreview = msgContent.length() > 50 ? msgContent.substring(0, 50) + "..." : msgContent;
                //     debugLog(TAG + ": msgContent=" + contentPreview);
                // }
            } catch (Throwable t) {
                // debugLog(TAG + ": Error filtering by list: " + t.getMessage());
                // debugLog(t);
                return; // 过滤失败时不添加选项条
            }
            
            // 获取消息ID（用于AI缓存和上下文去重）
            String msgId = null;
            try {
                Object msgIdObj = XposedHelpers.getObjectField(msgRecord, "msgId");
                msgId = String.valueOf(msgIdObj);
            } catch (Throwable t) {
                debugLog(TAG + ": Failed to get msgId: " + t.getMessage());
            }
            
            // 提前获取消息时间戳，用于历史消息判断
            long msgTime = 0;
            try {
                Object msgTimeObj = XposedHelpers.getObjectField(msgRecord, "msgTime");
                if (msgTimeObj != null) {
                    // msgTime通常是秒，转换为毫秒
                    msgTime = Long.parseLong(String.valueOf(msgTimeObj)) * 1000L;
                }
            } catch (Throwable t) {
                // 失败则使用当前时间（降级）
                msgTime = System.currentTimeMillis();
                debugLog(TAG + ": Failed to get msgTime, using current time: " + t.getMessage());
            }
            
            // 保存消息到上下文缓存（带去重）
            // 注意：peerUin 已在前面获取，这里不需要重复定义
            try {
                // 获取发送人昵称（优先使用备注名，其次QQ昵称）
                String senderName = null;
                try {
                    // 优先尝试获取备注名
                    Object remarkNameObj = XposedHelpers.getObjectField(msgRecord, "sendRemarkName");
                    if (remarkNameObj != null && !String.valueOf(remarkNameObj).trim().isEmpty()) {
                        senderName = String.valueOf(remarkNameObj);
                    } else {
                        // 备注名为空，尝试获取QQ昵称
                        Object nickNameObj = XposedHelpers.getObjectField(msgRecord, "sendNickName");
                        if (nickNameObj != null && !String.valueOf(nickNameObj).trim().isEmpty()) {
                            senderName = String.valueOf(nickNameObj);
                        }
                    }
                } catch (Throwable t) {
                    // 字段获取失败
                    debugLog(TAG + ": Failed to get sender name: " + t.getMessage());
                }
                
                // 如果所有尝试都失败，使用UIN
                if (senderName == null || senderName.trim().isEmpty()) {
                    senderName = senderUin != null ? senderUin : "未知";
                }
                
                // peerUin 已在前面获取，这里添加调试日志
                debugLog(TAG + ": [Affinity] peerUin=" + peerUin + ", senderUin=" + senderUin);

                // 使用peerUin作为conversationId（群聊时为群号，私聊时为对方QQ）
                // 这样可以确保群聊中不同用户的消息被聚合到同一个上下文中
                if (peerUin != null && !msgContent.isEmpty()) {
                    
                    // 【修改】总是缓存消息到上下文管理器，以便在按需显示时也能使用缓存
                    // 这样无论是自动显示还是按需显示模式，都能从内存缓存中快速获取上下文
                    boolean shouldCacheMessage = true;
                    
                    // 【新增】提取引用回复的内容并整合到消息
                    try {
                        List<?> elements = (List<?>) XposedHelpers.getObjectField(msgRecord, "elements");
                        if (elements != null && !elements.isEmpty()) {
                            // 【调试日志已禁用】
                            // debugLog(TAG + ": Elements count: " + elements.size());
                            // for (int i = 0; i < elements.size(); i++) {
                            //     Object element = elements.get(i);
                            //     debugLog(TAG + ":   [" + i + "] " + element.getClass().getSimpleName());
                            //     
                            //     // 尝试所有可能的引用字段名
                            //     for (String fieldName : new String[]{"replyElement", "g", "h"}) {
                            //         try {
                            //             Object field = XposedHelpers.getObjectField(element, fieldName);
                            //             if (field != null) {
                            //                 debugLog(TAG + ":     ." + fieldName + " exists: " + field.getClass().getSimpleName());
                            //             }
                            //         } catch (Throwable ignored) {}
                            //     }
                            // }
                            
                            for (Object element : elements) {
                                try {
                                    // 尝试获取replyElement
                                    Object replyElement = XposedHelpers.getObjectField(element, "replyElement");
                                    if (replyElement != null) {
                                        // 提取引用的消息文本
                                        String replyText = null;
                                        try {
                                            Object replyTextObj = XposedHelpers.getObjectField(replyElement, "sourceMsgText");
                                            if (replyTextObj != null) {
                                                replyText = String.valueOf(replyTextObj);
                                            }
                                        } catch (Throwable ignored) {}
                                        
                                        // 提取引用消息的发送人
                                        String replySenderName = null;
                                        try {
                                            Object senderShowNameObj = XposedHelpers.getObjectField(replyElement, "senderShowName");
                                            if (senderShowNameObj != null) {
                                                replySenderName = String.valueOf(senderShowNameObj);
                                            }
                                        } catch (Throwable ignored) {}
                                        
                                        // 降级策略1：尝试从当前消息内容中解析 "@昵称 "
                                        if (replySenderName == null && msgContent != null) {
                                            String trimmedContent = msgContent.trim();
                                            if (trimmedContent.startsWith("@")) {
                                                int spaceIndex = trimmedContent.indexOf(' ');
                                                if (spaceIndex > 1) {
                                                    // 提取 @ 和 空格 之间的内容作为名字
                                                    String potentialName = trimmedContent.substring(1, spaceIndex);
                                                    // 简单的合法性检查（避免提取到过长的错误内容）
                                                    if (potentialName.length() < 20) {
                                                        replySenderName = potentialName;
                                                        // debugLog(TAG + ": Extracted reply sender from content: " + replySenderName);
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // 降级策略2：使用 senderUid
                                        if (replySenderName == null) {
                                            try {
                                                long senderUid = XposedHelpers.getLongField(replyElement, "senderUid");
                                                if (senderUid > 0) {
                                                    replySenderName = String.valueOf(senderUid);
                                                } else {
                                                    // 尝试 senderUidStr
                                                    Object senderUidStrObj = XposedHelpers.getObjectField(replyElement, "senderUidStr");
                                                    if (senderUidStrObj != null) {
                                                        replySenderName = String.valueOf(senderUidStrObj);
                                                    }
                                                }
                                            } catch (Throwable ignored) {}
                                        }
                                        
                                        // 最终兜底
                                        if (replySenderName == null) {
                                            replySenderName = "某人";
                                        }
                                        
                                        // 如果成功提取引用内容，整合到当前消息内容中
                                        if (replyText != null && !replyText.trim().isEmpty()) {
                                            if (replySenderName == null || replySenderName.trim().isEmpty()) {
                                                replySenderName = "某人";
                                            }
                                            
                                            // 将引用信息附加到消息内容
                                            // 格式: 原消息内容 (回复 @被引用者: "被引用内容")
                                            msgContent = msgContent + " (回复 @" + replySenderName + ": \"" + replyText + "\")";
                                            
                                            debugLog(TAG + ": ✓ 已将引用信息整合到消息内容");
                                        }
                                        break; // 只处理第一个replyElement
                                    }
                                } catch (Throwable ignored) {
                                    // replyElement字段不存在或获取失败，继续下一个element
                                }
                            }
                        }
                    } catch (Throwable t) {
                        debugLog(TAG + ": Error extracting reply content: " + t.getMessage());
                    }
                    
                    // 【修改自己消息的显示格式为"昵称[我]"】
                    if (isSelf && senderName != null && !senderName.isEmpty()) {
                        senderName = senderName + "[我]";
                    }
                    
                    // 【修改】总是缓存消息，无论是自动显示还是按需显示模式
                    if (shouldCacheMessage) {
                        // 【上下文图片识别】传递图片数量，用于后续识别上下文中的图片
                        int imageCount = (imageElements != null) ? imageElements.size() : 0;
                        MessageContextManager.addMessage(peerUin, senderName, msgContent, isSelf, msgId, msgTime, imageCount);
                        
                        // 【上下文图片识别】缓存图片元素，以便后续识别
                        if (imageElements != null && !imageElements.isEmpty() && peerUin != null && msgId != null) {
                            top.galqq.utils.ImageDescriptionCache.putImageElements(peerUin, msgId, imageElements);
                        }
                        
                        debugLog(TAG + ": 已缓存消息到上下文管理器（支持自动显示和按需显示）");
                    }
                }
            } catch (Throwable t) {
                debugLog(TAG + ": Error saving message to context: " + t.getMessage());
            }
            
            if (!isSelf) {
                // 如果启用好感度功能，添加好感度视图
                final String finalSenderUin = senderUin;
                if (ConfigManager.isAffinityEnabled() && finalSenderUin != null && !finalSenderUin.isEmpty()) {
                    try {
                        // 初始化好感度管理器（首次调用时会触发数据获取）
                        initAffinityManager(context);
                        
                        // 【View复用修复】优先从显示缓存获取，避免重复计算
                        int affinity;
                        if (affinityDisplayCache.containsKey(finalSenderUin)) {
                            affinity = affinityDisplayCache.get(finalSenderUin);
                        } else {
                            // 从 AffinityManager 获取并缓存
                            top.galqq.utils.AffinityManager affinityManager = 
                                top.galqq.utils.AffinityManager.getInstance(context);
                            affinity = affinityManager.getAffinity(finalSenderUin);
                            
                            // 只缓存有效的好感度值（非-1）
                            if (affinity >= 0) {
                                affinityDisplayCache.put(finalSenderUin, affinity);
                            }
                        }
                        
                        // 【调试日志】打印好感度获取结果
                        // debugLog(TAG + ": [Affinity] senderUin=" + finalSenderUin + ", affinity=" + affinity);
                        
                        // 【修改】只有当好感度有效时才创建视图，无好感度数据的用户不显示
                        if (affinity >= 0) {
                            // 创建好感度视图
                            TextView affinityView = top.galqq.utils.AffinityViewHelper.createAffinityView(context, affinity);
                            
                            // 判断是否是私聊：peerUin == senderUin 时为私聊
                            boolean isPrivateChat = (peerUin != null && peerUin.equals(finalSenderUin));
                            debugLog(TAG + ": [Affinity] isPrivateChat=" + isPrivateChat + ", peerUin=" + peerUin + ", senderUin=" + finalSenderUin);
                            
                            // 添加到布局（传递私聊标志以调整位置）
                            addAffinityViewToLayout(context, rootView, affinityView, msgRecord, isPrivateChat);
                        }
                    } catch (Throwable t) {
                        debugLog(TAG + ": [Affinity] Error: " + t.getMessage());
                    }
                }
                
                // 防止加载历史记录时触发AI刷屏
                // 动态获取配置的阈值（秒转毫秒）
                int thresholdSeconds = ConfigManager.getHistoryThreshold();
                long thresholdMs = thresholdSeconds * 1000L;
                
                // 检查是否已缓存AI选项（如果有缓存，即使超过阈值也显示）
                boolean hasCachedOptions = (msgId != null && optionsCache.containsKey(msgId));
                
                // 历史消息判断：超过阈值的消息不显示任何UI（包括私聊）
                long currentTime = System.currentTimeMillis();
                long timeDiff = Math.abs(currentTime - msgTime);
                boolean isHistoryMessage = (!hasCachedOptions && timeDiff > thresholdMs);
                
                debugLog(TAG + ": [HISTORY_CHECK] msgTime=" + msgTime + ", currentTime=" + currentTime + 
                        ", diff=" + timeDiff + "ms, threshold=" + thresholdMs + "ms, hasCached=" + hasCachedOptions + 
                        ", isHistory=" + isHistoryMessage);
                
                if (isHistoryMessage) {
                    debugLog(TAG + ": [HISTORY] 历史消息，不显示任何UI (diff=" + timeDiff + "ms > threshold=" + thresholdMs + "ms)");
                    return;
                }


                // 检查是否自动显示选项
                // 检查是否自动显示选项
                boolean autoShow = ConfigManager.isAutoShowOptionsEnabled();
                // 确保 conversationId 在作用域内
                String conversationId = (peerUin != null && !peerUin.isEmpty()) ? peerUin : senderUin;

                // 群聊选项显示控制：仅对群聊进行过滤，私聊不受影响
                // 判断是否为群聊：peerUin != senderUin 时为群聊
                boolean isGroupChatForFilter = peerUin != null && senderUin != null && !peerUin.equals(senderUin);
                if (isGroupChatForFilter) {
                    // 检查是否关闭群聊选项显示
                    if (ConfigManager.isDisableGroupOptions()) {
                        debugLog(TAG + ": [GROUP_FILTER] Group options disabled, skipping all UI for group: " + peerUin);
                        return; // 直接返回，不创建任何UI元素
                    }
                    // 检查群是否通过过滤（基于群黑白名单和群过滤模式）
                    if (!ConfigManager.isGroupPassFilter(peerUin)) {
                        debugLog(TAG + ": [GROUP_FILTER] Group " + peerUin + " filtered out, skipping all UI");
                        return; // 直接返回，不创建任何UI元素
                    }
                }
                // 【私聊】如果不是群聊，说明是私聊，继续显示选项
                debugLog(TAG + ": [CHAT_TYPE] isGroupChat=" + isGroupChatForFilter + ", autoShow=" + autoShow);

                // 检查是否用户手动点击过显示
                boolean hasRequested = (msgId != null && requestedOptionsMsgIds.contains(msgId));
                // 检查是否有缓存结果（有结果也应该直接显示）
                boolean hasCache = (msgId != null && optionsCache.containsKey(msgId));
                
                // 检查是否已收起（优先显示「展开选项」按钮）
                boolean isCollapsed = (msgId != null && collapsedMsgIds.contains(msgId));
                
                // 【修改逻辑】根据是否启用自动显示来决定UI显示方式
                View viewToAdd = null;
                boolean needFillContent = false;
                
                if (isCollapsed && hasCache) {
                    // 已收起且有缓存：显示「展开选项」按钮
                    debugLog(TAG + ": [UI_MODE] 显示「展开选项」按钮（已收起状态）");
                    viewToAdd = createExpandFromCacheButton(context, msgRecord, msgId, conversationId, rootView);
                    
                } else if (isHistoryMessage) {
                    // 【历史消息】始终只显示「显示选项」按钮，不自动加载AI
                    debugLog(TAG + ": [UI_MODE] 历史消息，显示「显示选项」按钮");
                    viewToAdd = createShowOptionsButton(context, msgRecord, msgId, conversationId, rootView);
                    
                } else if (autoShow) {
                    // 【仅在启用自动显示时】自动创建选项条并填充
                    debugLog(TAG + ": [UI_MODE] 自动显示选项（autoShow=true）");
                    LinearLayout optionBar = createEmptyOptionBarNT(context);
                    optionBar.setId(OPTION_BAR_ID);
                    viewToAdd = optionBar;
                    needFillContent = true;
                    
                } else if (hasRequested || hasCache) {
                    // 【用户已点击显示或有缓存】显示选项条
                    debugLog(TAG + ": [UI_MODE] 显示选项条（用户已请求或有缓存）");
                    LinearLayout optionBar = createEmptyOptionBarNT(context);
                    optionBar.setId(OPTION_BAR_ID);
                    viewToAdd = optionBar;
                    needFillContent = true;
                    
                } else {
                    // 【未启用自动显示且未请求】始终显示「显示选项」按钮
                    debugLog(TAG + ": [UI_MODE] 显示「显示选项」按钮（按需模式）");
                    viewToAdd = createShowOptionsButton(context, msgRecord, msgId, conversationId, rootView);
                }
                
                // 统一处理布局添加
                if (viewToAdd != null) {
                    Class<?> constraintLayoutClass = null;
                    try {
                        constraintLayoutClass = XposedHelpers.findClass("androidx.constraintlayout.widget.ConstraintLayout", context.getClassLoader());
                    } catch (Throwable t) {
                        // Ignore if class not found
                    }

                    if (constraintLayoutClass != null && constraintLayoutClass.isAssignableFrom(rootView.getClass())) {
                        handleConstraintLayout(context, rootView, viewToAdd, msgRecord);
                    } else if (rootView.getClass().getName().contains("ConstraintLayout")) {
                        handleConstraintLayout(context, rootView, viewToAdd, msgRecord);
                    } else {
                        handleLegacyLayout(context, rootView, viewToAdd);
                    }
                    
                    // 如果需要填充内容（选项条）
                    if (needFillContent && viewToAdd instanceof LinearLayout) {
                        fillOptionBarContentWithRoot(context, (LinearLayout) viewToAdd, msgRecord, msgId, conversationId, rootView);
                    }
                }
            } else {
                // 【新增】即使是自己的消息，也需要检测并调整表情回复位置
                // 避免表情回复和消息气泡重叠
                debugLog(TAG + ": [EMOJI_REACTION_SELF] 检查自己消息的表情回复");
                adjustEmojiReactionForSelfMessage(context, rootView, msgRecord);
            }
            
            // debugLog(TAG + ": Successfully added option bar to QQNT message");

        } catch (Throwable t) {
            debugLog(TAG + ": Error processing QQNT message: " + t.getMessage());
            debugLog(t);
        }
    }

    /**
     * 递归清理好感度视图
     * 由于 View 复用，好感度视图可能被添加到不同的位置
     */
    private static void removeAffinityViewRecursively(ViewGroup parent) {
        if (parent == null) return;
        
        int affinityViewId = top.galqq.utils.AffinityViewHelper.AFFINITY_VIEW_ID;
        
        // 先检查直接子 View
        View affinityView = parent.findViewById(affinityViewId);
        if (affinityView != null) {
            ViewGroup actualParent = (ViewGroup) affinityView.getParent();
            if (actualParent != null) {
                actualParent.removeView(affinityView);
            }
        }
        
        // 遍历所有子 View，查找并移除
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child.getId() == affinityViewId) {
                parent.removeViewAt(i);
            } else if (child instanceof ViewGroup) {
                // 递归检查子 ViewGroup
                removeAffinityViewRecursively((ViewGroup) child);
            }
        }
    }

    /**
     * 添加好感度视图到布局
     * 使用与选项条完全相同的方式：找到消息气泡，定位到气泡上方
     * 不使用任何降级逻辑，只支持 ConstraintLayout
     * 
     * @param isPrivateChat 是否是私聊（私聊时需要调整位置，因为没有昵称显示）
     */
    private static void addAffinityViewToLayout(Context context, ViewGroup rootView, View affinityView, Object msgRecord, boolean isPrivateChat) {
        try {
            // 1. 创建 ConstraintLayout.LayoutParams
            Class<?> constraintLayoutParamsClass = XposedHelpers.findClass(
                "androidx.constraintlayout.widget.ConstraintLayout$LayoutParams", 
                context.getClassLoader()
            );
            Object clp = constraintLayoutParamsClass
                .getConstructor(int.class, int.class)
                .newInstance(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            
            // 2. 查找消息气泡（与选项条使用完全相同的逻辑）
            int msgBubbleId = -1;
            
            // 2.1 BubbleLayout Class Name Search (Priority)
            for (int i = 0; i < rootView.getChildCount(); i++) {
                View child = rootView.getChildAt(i);
                if (child.getClass().getName().contains("BubbleLayout") && child.getId() != View.NO_ID) {
                    msgBubbleId = child.getId();
                    debugLog(TAG + ": [Affinity] 找到气泡 via BubbleLayout: " + child.getClass().getSimpleName() + 
                            ", ID=" + msgBubbleId + ", Top=" + child.getTop() + ", Height=" + child.getHeight());
                    break;
                }
            }
            
            // 2.2 Text Content Search (Fallback)
            if (msgBubbleId == -1) {
                String msgContent = getMessageContentNT(msgRecord);
                if (!msgContent.isEmpty()) {
                    View textContainer = findViewWithText(rootView, msgContent);
                    if (textContainer != null) {
                        View bubble = textContainer;
                        while (bubble.getParent() != rootView && bubble.getParent() instanceof View) {
                            bubble = (View) bubble.getParent();
                        }
                        
                        if (bubble.getParent() == rootView && bubble.getId() != View.NO_ID) {
                            msgBubbleId = bubble.getId();
                            debugLog(TAG + ": [Affinity] 找到气泡 via TextContent: " + bubble.getClass().getSimpleName() + 
                                    ", ID=" + msgBubbleId + ", Top=" + bubble.getTop() + ", Height=" + bubble.getHeight());
                        }
                    }
                }
            }
            
            // 2.3 LinearLayout Fallback (Last Resort)
            if (msgBubbleId == -1) {
                for (int i = 0; i < rootView.getChildCount(); i++) {
                    View child = rootView.getChildAt(i);
                    if (child instanceof LinearLayout && child.getId() != View.NO_ID) {
                        msgBubbleId = child.getId();
                        break;
                    }
                }
            }
            
            // 3. 如果找不到气泡，不添加好感度视图
            if (msgBubbleId == -1) {
                debugLog(TAG + ": [Affinity] Could not find message bubble, skipping affinity view");
                return;
            }
            
            // 4. 添加视图到布局
            rootView.addView(affinityView, (ViewGroup.LayoutParams) clp);
            
            // 5. 使用 ConstraintSet 设置约束（与选项条一样的方式）
            Class<?> constraintSetClass = XposedHelpers.findClass(
                "androidx.constraintlayout.widget.ConstraintSet", 
                context.getClassLoader()
            );
            Object constraintSet = constraintSetClass.newInstance();
            XposedHelpers.callMethod(constraintSet, "clone", rootView);
            
            // 约束常量: TOP=3, BOTTOM=4, LEFT=1
            int TOP = 3, BOTTOM = 4, LEFT = 1;
            int PARENT_ID = 0;
            int viewId = affinityView.getId();
            
            // 【私聊/群聊位置调整】
            // 群聊：气泡上方有昵称，需要8dp间距
            // 私聊：气泡上方没有昵称，直接使用8dp（和群聊一样，因为群聊正常）
            int bottomMargin = isPrivateChat ? dp2px(context, -16) : dp2px(context, 8);
            
            debugLog(TAG + ": [Affinity] isPrivateChat=" + isPrivateChat + ", bottomMargin=" + bottomMargin + "px, msgBubbleId=" + msgBubbleId);
            
            // 底部连接到气泡顶部
            XposedHelpers.callMethod(constraintSet, "connect", viewId, BOTTOM, msgBubbleId, TOP, bottomMargin);
            // 左侧对齐父容器左边
            XposedHelpers.callMethod(constraintSet, "connect", viewId, LEFT, PARENT_ID, LEFT, dp2px(context, 4));
            
            // 应用约束
            XposedHelpers.callMethod(constraintSet, "applyTo", rootView);
            
            debugLog(TAG + ": [Affinity] 好感度视图已添加，使用 ConstraintSet 方式");
            
        } catch (Throwable t) {
            debugLog(TAG + ": [Affinity] Error adding affinity view: " + t.getMessage());
            // 出错时移除视图
            try {
                rootView.removeView(affinityView);
            } catch (Throwable ignored) {}
        }
    }
    
    private static void handleLegacyLayout(Context context, ViewGroup rootView, View optionBar) {
        ViewGroup.LayoutParams lp;
        if (rootView instanceof RelativeLayout) {
            RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rlp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            rlp.leftMargin = dp2px(context, 15);
            rlp.bottomMargin = dp2px(context, 5);
            lp = rlp;
        } else if (rootView instanceof android.widget.FrameLayout) {
            android.widget.FrameLayout.LayoutParams flp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            flp.gravity = Gravity.BOTTOM | Gravity.LEFT;
            flp.leftMargin = dp2px(context, 15);
            flp.bottomMargin = dp2px(context, 5);
            lp = flp;
        } else {
            ViewGroup.MarginLayoutParams mlp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            mlp.leftMargin = dp2px(context, 15);
            lp = mlp;
        }
        rootView.addView(optionBar, lp);
    }

    private static void handleConstraintLayout(Context context, ViewGroup rootView, View optionBar, Object msgRecord) {
        try {
            // 确保 rootView 的高度是 WRAP_CONTENT，避免高度限制导致布局被压缩
            ViewGroup.LayoutParams rootParams = rootView.getLayoutParams();
            if (rootParams != null) {
                int originalHeight = rootParams.height;
                if (originalHeight != ViewGroup.LayoutParams.WRAP_CONTENT && originalHeight != ViewGroup.LayoutParams.MATCH_PARENT) {
                    debugLog(TAG + ": [LAYOUT_FIX] rootView 高度为固定值: " + originalHeight + "px，可能导致布局问题");
                }
                // 注意：不要强制修改 rootView 的高度，这可能会破坏QQ的布局
            }
            
            // 检测是否存在表情回复元素
            int emojiReactionViewId = findEmojiReactionView(rootView);
            boolean hasEmojiReaction = (emojiReactionViewId != -1);
            
            if (hasEmojiReaction) {
                debugLog(TAG + ": [EMOJI_REACTION] 检测到表情回复，ID=" + emojiReactionViewId);
            }
            
            // 1. Add view to ConstraintLayout first (needed for ConstraintSet to work)
            // 判断是否是按钮（TextView），如果是则用WRAP_CONTENT，否则用MATCH_CONSTRAINT(0)
            boolean isButton = (optionBar instanceof TextView);
            int widthParam = isButton ? ViewGroup.LayoutParams.WRAP_CONTENT : 0; // 0 = MATCH_CONSTRAINT

            // Use ConstraintLayout.LayoutParams if possible
            Class<?> constraintLayoutParamsClass = XposedHelpers.findClass("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams", context.getClassLoader());
            ViewGroup.LayoutParams clp = (ViewGroup.LayoutParams) constraintLayoutParamsClass.getConstructor(int.class, int.class)
                .newInstance(widthParam, ViewGroup.LayoutParams.WRAP_CONTENT);
            
            rootView.addView(optionBar, clp);
            
            // 2. Use ConstraintSet to position it
            Class<?> constraintSetClass = XposedHelpers.findClass("androidx.constraintlayout.widget.ConstraintSet", context.getClassLoader());
            Object constraintSet = constraintSetClass.newInstance();
            Class<?> constraintLayoutClass = rootView.getClass();
            
            // clone(ConstraintLayout)
            XposedHelpers.callMethod(constraintSet, "clone", rootView);
            
            // Find anchor views (Message Bubble)
            int msgBubbleId = -1;

            // 1. BubbleLayout Class Name Search (Priority)
            // debugLog(TAG + ": Trying to find bubble via BubbleLayout class name...");
            for (int i = 0; i < rootView.getChildCount(); i++) {
                View child = rootView.getChildAt(i);
                if (child.getClass().getName().contains("BubbleLayout") && child.getId() != View.NO_ID) {
                    msgBubbleId = child.getId();
                    // debugLog(TAG + ": Found bubble via class name, ID: " + msgBubbleId);
                    break;
                }
            }

            // 2. Text Content Search (Fallback)
            if (msgBubbleId == -1) {
                String msgContent = getMessageContentNT(msgRecord);
                if (!msgContent.isEmpty()) {
                    debugLog(TAG + ": BubbleLayout not found, trying text content: " + msgContent);
                    View textContainer = findViewWithText(rootView, msgContent);
                    if (textContainer != null) {
                        View bubble = textContainer;
                        while (bubble.getParent() != rootView && bubble.getParent() instanceof View) {
                            bubble = (View) bubble.getParent();
                        }
                        
                        if (bubble.getParent() == rootView && bubble.getId() != View.NO_ID) {
                            msgBubbleId = bubble.getId();
                            debugLog(TAG + ": Found bubble via text content, ID: " + msgBubbleId + ", Class: " + bubble.getClass().getName());
                        }
                    }
                }
            }

            // 3. LinearLayout Fallback (Last Resort)
            if (msgBubbleId == -1) {
                debugLog(TAG + ": Text search failed, trying LinearLayout fallback...");
                for (int i = 0; i < rootView.getChildCount(); i++) {
                    View child = rootView.getChildAt(i);
                    if (child instanceof LinearLayout && child.getId() != View.NO_ID) {
                        msgBubbleId = child.getId();
                        debugLog(TAG + ": Found bubble via LinearLayout fallback, ID: " + msgBubbleId);
                        break;
                    }
                }
            }
            
            if (msgBubbleId != -1) {
                // 将 msgBubbleId 声明为 final，以便在内部类中使用
                final int finalMsgBubbleId = msgBubbleId;
                
                // Connect TOP of OptionBar to BOTTOM of MessageBubble
                // connect(int startID, int startSide, int endID, int endSide, int margin)
                // Sides: TOP=3, BOTTOM=4, LEFT=1, RIGHT=2, START=6, END=7
                int TOP = 3, BOTTOM = 4, LEFT = 1, RIGHT = 2, START = 6, END = 7;
                
                // 计算 topMargin：如果有表情回复，根据表情数量增加额外的向下偏移
                int topMargin = dp2px(context, 5); // 默认间距
                
                if (hasEmojiReaction) {
                    final View emojiView = rootView.findViewById(emojiReactionViewId);
                    
                    if (emojiView != null && emojiView.getVisibility() == View.VISIBLE) {
                        // 测量表情回复的高度
                        int emojiHeight = emojiView.getHeight();
                        if (emojiHeight == 0) {
                            emojiView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                            emojiHeight = emojiView.getMeasuredHeight();
                        }
                        
                        // 基础偏移：表情回复的基本高度
                        topMargin = topMargin + emojiHeight + dp2px(context, 3);
                        
                        // 延迟到下一帧检查父容器高度（此时布局已完成）
                        final int baseTopMargin = topMargin;
                        final Object finalConstraintSet = constraintSet;
                        
                        emojiView.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    // 向上遍历多层容器，找到真正变化的那个
                                    ViewParent current = emojiView.getParent();
                                    int level = 1;
                                    
                                    while (current != null && current instanceof View && level <= 5) {
                                        View currentView = (View) current;
                                        int currentHeight = currentView.getHeight();
                                        
                                        debugLog(TAG + ": [EMOJI_REACTION] 延迟检测 - 第" + level + "层父容器: " + 
                                                currentView.getClass().getSimpleName() + 
                                                ", 高度=" + currentHeight + "px" +
                                                ", ID=" + currentView.getId());
                                        
                                        // 检查这层容器是否高度超过阈值
                                        int singleLineThreshold = dp2px(context, 30); // 约90px
                                        
                                        if (currentHeight > singleLineThreshold) {
                                            int extraOffset = currentHeight - singleLineThreshold;
                                            int newTopMargin = baseTopMargin + extraOffset;
                                            
                                            debugLog(TAG + ": [EMOJI_REACTION] 在第" + level + "层检测到多行表情，重新调整topMargin: " + 
                                                    baseTopMargin + " + " + extraOffset + " = " + newTopMargin + "px");
                                            
                                            // 【修复】直接修改选项气泡的 LayoutParams，不使用 ConstraintSet.applyTo()
                                            // 这样不会影响好感度视图的约束
                                            View optionBarView = rootView.findViewById(OPTION_BAR_ID);
                                            if (optionBarView != null) {
                                                ViewGroup.LayoutParams lp = optionBarView.getLayoutParams();
                                                if (lp != null) {
                                                    try {
                                                        // 直接修改 topMargin
                                                        XposedHelpers.setIntField(lp, "topMargin", newTopMargin);
                                                        optionBarView.setLayoutParams(lp);
                                                        optionBarView.requestLayout();
                                                        debugLog(TAG + ": [EMOJI_REACTION] ✓ 已直接更新选项气泡的 topMargin");
                                                    } catch (Throwable t2) {
                                                        debugLog(TAG + ": [EMOJI_REACTION] ✗ 直接更新失败: " + t2.getMessage());
                                                    }
                                                }
                                            }
                                            
                                            return; // 找到变化的层级，退出
                                        }
                                        
                                        current = current.getParent();
                                        level++;
                                    }
                                    
                                    debugLog(TAG + ": [EMOJI_REACTION] 所有层级都未检测到高度变化，可能是单行表情");
                                    
                                } catch (Throwable t) {
                                    debugLog(TAG + ": [EMOJI_REACTION] 延迟检测失败: " + t.getMessage());
                                }
                            }
                        });
                        
                        debugLog(TAG + ": [EMOJI_REACTION] ✓ 检测到表情回复，基础topMargin=" + topMargin + "px（将延迟检测多行）");
                    }
                }
                
                // 统一约束设置：始终连接到消息气泡，通过 topMargin 控制位置
                XposedHelpers.callMethod(constraintSet, "connect", 
                    OPTION_BAR_ID, TOP, finalMsgBubbleId, BOTTOM, topMargin);
                
                debugLog(TAG + ": 选项气泡已连接到消息气泡，topMargin=" + topMargin + "px");
                
                // Align START (Left) of OptionBar to START (Left) of MessageBubble with 8dp margin
                // 向左移动选项条
                XposedHelpers.callMethod(constraintSet, "connect", 
                    OPTION_BAR_ID, START, finalMsgBubbleId, START, dp2px(context, 8)); // 8dp左边距
                
                // 仅对非按钮（即选项条）添加右侧约束，避免按钮被拉伸
                if (!isButton) {
                    // 限制右边界到parent的END，留16dp margin
                    XposedHelpers.callMethod(constraintSet, "connect",
                        OPTION_BAR_ID, END, 0 /* PARENT_ID */, END, dp2px(context, 16)); // 16dp右边距
                }
                
                // 确保消息气泡的约束不被影响，固定其顶部位置
                try {
                    // 保存消息气泡的原始顶部约束（如果有的话）
                    // 这里不做修改，只是确保它保持原样
                    debugLog(TAG + ": 保持消息气泡原始约束不变");
                } catch (Throwable t) {
                    debugLog(TAG + ": 检查消息气泡约束时出错: " + t.getMessage());
                }
                
                // Apply constraints
                XposedHelpers.callMethod(constraintSet, "applyTo", rootView);
                
                // 【调试】等待布局完成后验证位置
                if (hasEmojiReaction) {
                    final View finalEmojiView = rootView.findViewById(emojiReactionViewId);
                    final View finalOptionBar = rootView.findViewById(OPTION_BAR_ID);
                    
                    rootView.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (finalEmojiView != null && finalOptionBar != null) {
                                    View msgBubble = rootView.findViewById(finalMsgBubbleId);
                                    int emojiTop = finalEmojiView.getTop();
                                    int emojiHeight = finalEmojiView.getHeight();
                                    int optionTop = finalOptionBar.getTop();
                                    int msgBubbleTop = (msgBubble != null) ? msgBubble.getTop() : -1;
                                    int msgBubbleBottom = (msgBubble != null) ? msgBubble.getBottom() : -1;
                                    
                                    debugLog(TAG + ": [EMOJI_REACTION] 最终位置验证:");
                                    debugLog(TAG + ": [EMOJI_REACTION] - 消息气泡: top=" + msgBubbleTop + "px, bottom=" + msgBubbleBottom + "px");
                                    debugLog(TAG + ": [EMOJI_REACTION] - 表情回复: top=" + emojiTop + "px, height=" + emojiHeight + "px");
                                    debugLog(TAG + ": [EMOJI_REACTION] - 选项气泡: top=" + optionTop + "px");
                                    debugLog(TAG + ": [EMOJI_REACTION] - 预期选项气泡 top: " + (msgBubbleBottom + emojiHeight) + "px");
                                    
                                    int expectedTop = msgBubbleBottom + emojiHeight + dp2px(context, 3);
                                    if (Math.abs(optionTop - expectedTop) > dp2px(context, 10)) {
                                        debugLog(TAG + ": [EMOJI_REACTION] ⚠️ 位置偏差较大！实际=" + optionTop + "px, 预期=" + expectedTop + "px");
                                    } else {
                                        debugLog(TAG + ": [EMOJI_REACTION] ✓ 位置正确");
                                    }
                                }
                            } catch (Throwable t) {
                                debugLog(TAG + ": [EMOJI_REACTION] 位置验证失败: " + t.getMessage());
                            }
                        }
                    });
                }
            } else {
                debugLog(TAG + ": Could not find message bubble ID for ConstraintLayout");
            }
            
        } catch (Exception e) {
            debugLog(TAG + ": Error handling ConstraintLayout: " + e.getMessage());
            debugLog(e);
            // Fallback to simple add if failed
            if (optionBar.getParent() == null) {
                rootView.addView(optionBar);
            }
        }
    }

    // Helper to find view with text
    private static View findViewWithText(ViewGroup root, String text) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView) {
                String viewText = ((TextView) child).getText().toString();
                // Check if viewText contains the msgContent (or vice versa, to be safe)
                if (viewText.contains(text) || text.contains(viewText)) {
                    return child;
                }
            } else if (child instanceof ViewGroup) {
                View found = findViewWithText((ViewGroup) child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    
    /**
     * 查找表情回复视图
     * 表情回复通常在消息气泡下方，类名为 EmojiUpdateTextView 或包含 msgtail 等关键字
     * 
     * @param rootView 根视图
     * @return 表情回复视图的ID，如果未找到返回-1
     */
    private static int findEmojiReactionView(ViewGroup rootView) {
        try {
            
            // 遍历所有子视图，查找表情回复元素
            for (int i = 0; i < rootView.getChildCount(); i++) {
                View child = rootView.getChildAt(i);
                String className = child.getClass().getName();
                String classNameLower = className.toLowerCase();
                String simpleName = child.getClass().getSimpleName().toLowerCase();
                
                // 【宽松匹配】只要包含特征关键词就可能是表情回复
                boolean isReactionView = 
                    // 精确匹配
                    className.contains("EmojiUpdateTextView") ||
                    className.contains("msgtail") ||
                    // 包含 emoji 且不是输入框
                    (classNameLower.contains("emoji") && !classNameLower.contains("input") && !classNameLower.contains("edit")) ||
                    // 包含 reaction
                    classNameLower.contains("reaction") ||
                    // 包含 emoticon
                    classNameLower.contains("emoticon") ||
                    // 包含 face 且不是头像
                    (classNameLower.contains("face") && !classNameLower.contains("avatar"));
                
                if (isReactionView && child.getId() != View.NO_ID && child.getVisibility() == View.VISIBLE) {
                    return child.getId();
                }
                
                // 递归检查子视图
                if (child instanceof ViewGroup) {
                    int foundId = findEmojiReactionViewRecursive((ViewGroup) child);
                    if (foundId != -1) {
                        return foundId;
                    }
                }
            }
        } catch (Throwable t) {
            debugLog(TAG + ": [EMOJI_REACTION] 查找表情回复视图失败: " + t.getMessage());
        }
        
        return -1; // 未找到
    }
    
    /**
     * 递归查找表情回复视图
     * 【改回模糊匹配】不使用精确类名，使用多种关键字组合判断
     */
    private static int findEmojiReactionViewRecursive(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            String className = child.getClass().getName();
            String classNameLower = className.toLowerCase();
            String simpleName = child.getClass().getSimpleName().toLowerCase();
            
            // 【模糊匹配】使用多种关键字组合判断，不依赖精确类名
            boolean isReactionView = 
                // 匹配包含 emoji 相关的类名
                (classNameLower.contains("emoji") && (classNameLower.contains("text") || classNameLower.contains("update") || classNameLower.contains("view"))) ||
                // 匹配包含 reaction 的类名
                classNameLower.contains("reaction") || 
                // 匹配包含 emoticon 的类名
                classNameLower.contains("emoticon") ||
                // 匹配 msgtail + emoji 组合
                (classNameLower.contains("msgtail") && classNameLower.contains("emoji")) ||
                // 匹配 aio + reaction 组合
                (classNameLower.contains("aio") && classNameLower.contains("reaction")) ||
                // 匹配 msg + reaction 组合
                (classNameLower.contains("msg") && classNameLower.contains("reaction")) ||
                // 简单类名匹配
                simpleName.contains("reaction") ||
                simpleName.contains("emoji");
            
            if (isReactionView && child.getId() != View.NO_ID) {
                // 检查视图是否可见（有些表情回复可能是隐藏的）
                if (child.getVisibility() == View.VISIBLE) {
                    debugLog(TAG + ": [EMOJI_REACTION] ✓ 模糊匹配找到表情回复: " + className);
                    return child.getId();
                }
            }
            
            if (child instanceof ViewGroup) {
                int foundId = findEmojiReactionViewRecursive((ViewGroup) child);
                if (foundId != -1) {
                    return foundId;
                }
            }
        }
        
        return -1;
    }
    
    /**
     * 为自己发送的消息检测表情回复
     * 【对齐handleConstraintLayout】使用相同的检测逻辑
     * 自己的消息不显示选项气泡，表情回复保持在消息气泡下方的默认位置
     * 
     * @param context 上下文
     * @param rootView 根视图
     * @param msgRecord 消息记录
     */
    private static void adjustEmojiReactionForSelfMessage(Context context, ViewGroup rootView, Object msgRecord) {
        try {
            // 【对齐handleConstraintLayout】检测表情回复视图
            int emojiReactionViewId = findEmojiReactionView(rootView);
            boolean hasEmojiReaction = (emojiReactionViewId != -1);
            
            if (hasEmojiReaction) {
                // 【对齐handleConstraintLayout】获取表情回复视图并测量
                View emojiView = rootView.findViewById(emojiReactionViewId);
                if (emojiView != null && emojiView.getVisibility() == View.VISIBLE) {
                    // 【对齐handleConstraintLayout】强制布局和异步获取实际位置
                    rootView.requestLayout();
                    rootView.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                int emojiHeight = emojiView.getHeight();
                                int emojiTop = emojiView.getTop();
                                debugLog(TAG + ": [EMOJI_REACTION_SELF] 表情回复实际位置: top=" + emojiTop + "px, height=" + emojiHeight + "px");
                            } catch (Throwable t) {
                                debugLog(TAG + ": [EMOJI_REACTION_SELF] 获取表情回复位置失败: " + t.getMessage());
                            }
                        }
                    });
                    
                    // 【对齐handleConstraintLayout】测量表情回复视图的高度
                    int emojiHeight = emojiView.getHeight();
                    if (emojiHeight == 0) {
                        // 如果还没有布局，强制测量
                        emojiView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                        emojiHeight = emojiView.getMeasuredHeight();
                    }
                    
                    debugLog(TAG + ": [EMOJI_REACTION_SELF] 自己的消息检测到表情回复，ID=" + emojiReactionViewId + 
                            ", 高度=" + emojiHeight + "px，保持默认位置（消息气泡下方）");
                } else {
                    debugLog(TAG + ": [EMOJI_REACTION_SELF] 表情回复视图不可见或为空");
                }
            } else {
                debugLog(TAG + ": [EMOJI_REACTION_SELF] 未检测到表情回复");
            }
        } catch (Throwable t) {
            debugLog(TAG + ": [EMOJI_REACTION_SELF] 检测失败: " + t.getMessage());
        }
    }
    
    // 创建空的选项条（稍后填充内容）
    private static LinearLayout createEmptyOptionBarNT(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.LEFT);
        bar.setPadding(0, dp2px(context, 5), 0, dp2px(context, 5));
        bar.setVisibility(View.GONE); // 初始隐藏，等有内容后再显示
        return bar;
    }
    
    // 创建"显示选项"按钮
    private static View createShowOptionsButton(
        Context context, 
        Object msgRecord, 
        String msgId, 
        String conversationId,
        ViewGroup rootView
    ) {
        TextView button = new TextView(context);
        button.setText("显示选项");
        button.setTextSize(12);
        // 减小Padding，使其更紧凑
        button.setPadding(dp2px(context, 8), dp2px(context, 4), dp2px(context, 8), dp2px(context, 4));
        
        // 样式优化：模仿加载中，使用极淡背景或无背景
        // 这里使用一个非常淡的灰色背景，带圆角，类似Tag
        button.setBackground(getRoundedBackground(Color.parseColor("#F5F5F5"), dp2px(context, 10)));
        button.setTextColor(Color.parseColor("#333333")); // 深灰色，比黑色淡一点
        button.setGravity(Gravity.CENTER);
        
        button.setOnClickListener(v -> {
            // 记录该消息已请求显示选项
            if (msgId != null) {
                requestedOptionsMsgIds.add(msgId);
            }

            // 1. 移除按钮本身
            if (button.getParent() instanceof ViewGroup) {
                ((ViewGroup)button.getParent()).removeView(button);
            }
            
            // 创建选项条并填充
            LinearLayout optionBar = createEmptyOptionBarNT(context);
            optionBar.setId(OPTION_BAR_ID);
            
            // 添加到相同位置
            Class<?> constraintLayoutClass = null;
            try {
                constraintLayoutClass = XposedHelpers.findClass(
                    "androidx.constraintlayout.widget.ConstraintLayout", 
                    context.getClassLoader()
                );
            } catch (Throwable t) {
                // Ignore
            }
            
            if (constraintLayoutClass != null && constraintLayoutClass.isAssignableFrom(rootView.getClass())) {
                handleConstraintLayout(context, rootView, optionBar, msgRecord);
            } else if (rootView.getClass().getName().contains("ConstraintLayout")) {
                handleConstraintLayout(context, rootView, optionBar, msgRecord);
            } else {
                handleLegacyLayout(context, rootView, optionBar);
            }
            
            // 填充选项（使用带 rootView 的版本以支持操作按钮）
            fillOptionBarContentWithRoot(context, optionBar, msgRecord, msgId, conversationId, rootView);
        });
        
        button.setId(OPTION_BAR_ID); // 使用相同ID避免冲突
        return button;
    }
    
    // 填充选项条内容（AI或本地词库）- 无 rootView 版本（兼容旧调用）
    private static void fillOptionBarContent(Context context, LinearLayout bar, Object msgRecord, 
                                             String msgId, String conversationId) {
        fillOptionBarContentWithRoot(context, bar, msgRecord, msgId, conversationId, null);
    }
    
    // 填充选项条内容（AI或本地词库）- 带 rootView 版本（支持操作按钮）
    private static void fillOptionBarContentWithRoot(Context context, LinearLayout bar, Object msgRecord, 
                                             String msgId, String conversationId, ViewGroup rootView) {
        String msgContent = getMessageContentNT(msgRecord);
        
        // 【AI缓存优化】如果启用AI且缓存中有选项，直接使用缓存数据
        if (ConfigManager.isAiEnabled() && msgId != null && optionsCache.containsKey(msgId)) {
            List<String> cachedOptions = optionsCache.get(msgId);
            if (rootView != null) {
                populateBarAndShowWithActions(context, bar, cachedOptions, msgRecord, msgId, conversationId, rootView);
            } else {
                populateBarAndShow(context, bar, cachedOptions, msgRecord);
            }
            return;
        }
        
        // 否则重新获取选项（AI或本地词库）
        setupOptionBarContentWithRoot(context, bar, msgContent, msgRecord, msgId, conversationId, rootView);
    }
    
    private static void useDictionaryNT(Context context, LinearLayout bar, Object msgRecord) {
        DictionaryManager.loadDictionary(context);
        List<String> options = DictionaryManager.pickRandomLines(3);
        populateBarAndShow(context, bar, options, msgRecord);
    }
    
    // ========== 操作按钮（刷新/收起/展开）==========
    
    /**
     * 创建操作按钮行（包含刷新和收起按钮）
     * @param context 上下文
     * @param optionBar 选项条容器
     * @param msgRecord 消息记录对象
     * @param msgId 消息ID
     * @param conversationId 会话ID
     * @param rootView 根视图
     * @param currentOptions 当前显示的选项列表
     * @return 操作按钮行容器
     */
    private static LinearLayout createActionButtonsRow(
        Context context,
        LinearLayout optionBar,
        Object msgRecord,
        String msgId,
        String conversationId,
        ViewGroup rootView,
        List<String> currentOptions
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dp2px(context, 6); // 与选项间隔
        row.setLayoutParams(rowParams);
        
        // 添加刷新按钮
        TextView refreshBtn = createRefreshButton(context, optionBar, msgRecord, msgId, conversationId, rootView);
        row.addView(refreshBtn);
        
        // 添加收起按钮
        TextView collapseBtn = createCollapseButton(context, optionBar, msgRecord, msgId, conversationId, rootView, currentOptions);
        row.addView(collapseBtn);
        
        return row;
    }
    
    /**
     * 创建刷新按钮
     */
    private static TextView createRefreshButton(
        Context context,
        LinearLayout optionBar,
        Object msgRecord,
        String msgId,
        String conversationId,
        ViewGroup rootView
    ) {
        TextView btn = new TextView(context);
        btn.setText("刷新");
        btn.setTextSize(12);
        btn.setPadding(dp2px(context, 10), dp2px(context, 6), dp2px(context, 10), dp2px(context, 6));
        btn.setBackground(getSelectableRoundedBackground(Color.parseColor("#F5F5F5"), dp2px(context, 12)));
        btn.setTextColor(Color.parseColor("#666666"));
        btn.setClickable(true);
        btn.setFocusable(true);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btn.setLayoutParams(lp);
        
        btn.setOnClickListener(v -> {
            // 触觉反馈
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            
            // 清空选项条内容并重新获取（使用带 rootView 的版本以保留操作按钮）
            optionBar.removeAllViews();
            String msgContent = getMessageContentNT(msgRecord);
            setupOptionBarContentWithRoot(context, optionBar, msgContent, msgRecord, msgId, conversationId, rootView);
        });
        
        return btn;
    }
    
    /**
     * 创建收起按钮
     */
    private static TextView createCollapseButton(
        Context context,
        LinearLayout optionBar,
        Object msgRecord,
        String msgId,
        String conversationId,
        ViewGroup rootView,
        List<String> currentOptions
    ) {
        TextView btn = new TextView(context);
        btn.setText("收起");
        btn.setTextSize(12);
        btn.setPadding(dp2px(context, 10), dp2px(context, 6), dp2px(context, 10), dp2px(context, 6));
        btn.setBackground(getSelectableRoundedBackground(Color.parseColor("#F5F5F5"), dp2px(context, 12)));
        btn.setTextColor(Color.parseColor("#666666"));
        btn.setClickable(true);
        btn.setFocusable(true);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.leftMargin = dp2px(context, 8); // 与刷新按钮间距
        btn.setLayoutParams(lp);
        
        btn.setOnClickListener(v -> {
            // 触觉反馈
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            
            // 1. 确保当前选项已缓存
            if (msgId != null && currentOptions != null && !currentOptions.isEmpty()) {
                cacheOptions(msgId, currentOptions);
                collapsedMsgIds.add(msgId);
            }
            
            // 2. 移除选项条
            if (optionBar.getParent() instanceof ViewGroup) {
                ((ViewGroup) optionBar.getParent()).removeView(optionBar);
            }
            
            // 3. 创建并显示「展开选项」按钮
            View expandBtn = createExpandFromCacheButton(context, msgRecord, msgId, conversationId, rootView);
            
            Class<?> constraintLayoutClass = null;
            try {
                constraintLayoutClass = XposedHelpers.findClass(
                    "androidx.constraintlayout.widget.ConstraintLayout",
                    context.getClassLoader()
                );
            } catch (Throwable t) {
                // Ignore
            }
            
            if (constraintLayoutClass != null && constraintLayoutClass.isAssignableFrom(rootView.getClass())) {
                handleConstraintLayout(context, rootView, expandBtn, msgRecord);
            } else if (rootView.getClass().getName().contains("ConstraintLayout")) {
                handleConstraintLayout(context, rootView, expandBtn, msgRecord);
            } else {
                handleLegacyLayout(context, rootView, expandBtn);
            }
        });
        
        return btn;
    }
    
    /**
     * 创建「展开选项」按钮（从缓存恢复）
     */
    private static View createExpandFromCacheButton(
        Context context,
        Object msgRecord,
        String msgId,
        String conversationId,
        ViewGroup rootView
    ) {
        TextView button = new TextView(context);
        button.setText("展开选项");
        button.setTextSize(12);
        button.setPadding(dp2px(context, 8), dp2px(context, 4), dp2px(context, 8), dp2px(context, 4));
        button.setBackground(getRoundedBackground(Color.parseColor("#F5F5F5"), dp2px(context, 10)));
        button.setTextColor(Color.parseColor("#666666"));
        button.setGravity(Gravity.CENTER);
        
        button.setOnClickListener(v -> {
            // 触觉反馈
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            
            // 1. 从缓存获取选项
            List<String> cachedOptions = (msgId != null) ? optionsCache.get(msgId) : null;
            
            // 2. 从收起状态移除
            if (msgId != null) {
                collapsedMsgIds.remove(msgId);
            }
            
            // 3. 移除展开按钮
            if (button.getParent() instanceof ViewGroup) {
                ((ViewGroup) button.getParent()).removeView(button);
            }
            
            // 4. 创建选项条
            LinearLayout optionBar = createEmptyOptionBarNT(context);
            optionBar.setId(OPTION_BAR_ID);
            
            Class<?> constraintLayoutClass = null;
            try {
                constraintLayoutClass = XposedHelpers.findClass(
                    "androidx.constraintlayout.widget.ConstraintLayout",
                    context.getClassLoader()
                );
            } catch (Throwable t) {
                // Ignore
            }
            
            if (constraintLayoutClass != null && constraintLayoutClass.isAssignableFrom(rootView.getClass())) {
                handleConstraintLayout(context, rootView, optionBar, msgRecord);
            } else if (rootView.getClass().getName().contains("ConstraintLayout")) {
                handleConstraintLayout(context, rootView, optionBar, msgRecord);
            } else {
                handleLegacyLayout(context, rootView, optionBar);
            }
            
            // 5. 填充选项（优先使用缓存，否则重新获取）
            if (cachedOptions != null && !cachedOptions.isEmpty()) {
                // 直接使用缓存，不调用AI
                populateBarAndShowWithActions(context, optionBar, cachedOptions, msgRecord, msgId, conversationId, rootView);
            } else {
                // 缓存为空，降级为重新获取（使用带 rootView 的版本）
                fillOptionBarContentWithRoot(context, optionBar, msgRecord, msgId, conversationId, rootView);
            }
        });
        
        button.setId(OPTION_BAR_ID);
        return button;
    }
    
    /**
     * 填充选项条并显示，同时添加操作按钮行
     * 这是 populateBarAndShow 的增强版本，用于需要操作按钮的场景
     */
    private static void populateBarAndShowWithActions(
        Context context,
        LinearLayout bar,
        List<String> options,
        Object msgRecord,
        String msgId,
        String conversationId,
        ViewGroup rootView
    ) {
        bar.removeAllViews();
        
        if (options == null || options.isEmpty()) {
            bar.setVisibility(View.GONE);
            return;
        }
        
        // 提取消息的引用回复信息
        long replyMsgId = 0L;
        long replyMsgSeq = 0L;
        String replyNick = "";
        String replyContent = "";
        
        try {
            // 尝试从 msgRecord 中提取 msgId (long 类型)
            Object msgIdObj = XposedHelpers.getObjectField(msgRecord, "msgId");
            debugLog(TAG + ": [WithActions] msgIdObj=" + msgIdObj + " (type=" + (msgIdObj != null ? msgIdObj.getClass().getName() : "null") + ")");
            if (msgIdObj instanceof Long) {
                replyMsgId = (Long) msgIdObj;
            } else if (msgIdObj != null) {
                try {
                    replyMsgId = Long.parseLong(String.valueOf(msgIdObj));
                } catch (NumberFormatException ignored) {}
            }
            
            // 尝试提取消息序列号
            try {
                Object msgSeqObj = XposedHelpers.getObjectField(msgRecord, "msgSeq");
                if (msgSeqObj instanceof Long) {
                    replyMsgSeq = (Long) msgSeqObj;
                } else if (msgSeqObj instanceof Integer) {
                    replyMsgSeq = ((Integer) msgSeqObj).longValue();
                }
            } catch (Throwable t) {
                try {
                    Object seqObj = XposedHelpers.getObjectField(msgRecord, "seq");
                    if (seqObj instanceof Long) {
                        replyMsgSeq = (Long) seqObj;
                    } else if (seqObj instanceof Integer) {
                        replyMsgSeq = ((Integer) seqObj).longValue();
                    }
                } catch (Throwable ignored) {}
            }
            
            // 提取发送者昵称
            try {
                Object remarkNameObj = XposedHelpers.getObjectField(msgRecord, "sendRemarkName");
                if (remarkNameObj != null && !String.valueOf(remarkNameObj).trim().isEmpty()) {
                    replyNick = String.valueOf(remarkNameObj);
                } else {
                    Object nickNameObj = XposedHelpers.getObjectField(msgRecord, "sendNickName");
                    if (nickNameObj != null) {
                        replyNick = String.valueOf(nickNameObj);
                    }
                }
            } catch (Throwable ignored) {}
            
            // 提取消息内容
            String msgContent = getMessageContentNT(msgRecord);
            if (msgContent != null && !msgContent.isEmpty()) {
                replyContent = replyNick + ":" + msgContent;
            }
            
            debugLog(TAG + ": [WithActions] 提取引用信息 - msgId=" + replyMsgId + ", seq=" + replyMsgSeq + ", nick=" + replyNick);
            
        } catch (Throwable t) {
            debugLog(TAG + ": [WithActions] 提取引用信息失败: " + t.getMessage());
        }
        
        // 保存引用信息供点击事件使用
        final long finalReplyMsgId = replyMsgId;
        final long finalReplyMsgSeq = replyMsgSeq;
        final String finalReplyNick = replyNick;
        final String finalReplyContent = replyContent;
        
        // 添加选项按钮
        for (String option : options) {
            TextView tv = new TextView(context);
            tv.setText(option);
            tv.setTextSize(13);
            tv.setPadding(dp2px(context, 12), dp2px(context, 8), dp2px(context, 12), dp2px(context, 8));
            tv.setBackground(getSelectableRoundedBackground(Color.parseColor("#F2F2F2"), dp2px(context, 12)));
            tv.setTextColor(Color.BLACK);
            tv.setClickable(true);
            tv.setFocusable(true);
            
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int maxWidth = screenWidth - dp2px(context, 16);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, dp2px(context, 6));
            tv.setMaxWidth(maxWidth);
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            
            // 短按：直接发送消息
            tv.setOnClickListener(v -> {
                sendMessage(context, option, msgRecord);
            });
            
            // 长按：弹出编辑对话框（传递引用信息）
            tv.setOnLongClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                showEditDialog(context, option, msgRecord, finalReplyMsgId, finalReplyMsgSeq, finalReplyNick, finalReplyContent);
                return true;
            });
            
            bar.addView(tv);
        }
        
        // 添加操作按钮行
        LinearLayout actionRow = createActionButtonsRow(context, bar, msgRecord, msgId, conversationId, rootView, options);
        bar.addView(actionRow);
        
        bar.setVisibility(View.VISIBLE);
    }
    
    private static String getMessageContentNT(Object msgRecord) {
        try {
            List<?> elements = (List<?>) XposedHelpers.getObjectField(msgRecord, "elements");
            if (elements == null || elements.isEmpty()) {
                return "";
            }
            
            StringBuilder content = new StringBuilder();
            for (Object element : elements) {
                Object textElement = XposedHelpers.getObjectField(element, "textElement");
                if (textElement != null) {
                    String text = (String) XposedHelpers.getObjectField(textElement, "content");
                    if (text != null) {
                        content.append(text);
                    }
                }
            }
            
            return content.toString();
        } catch (Exception e) {
            debugLog(TAG + ": Failed to extract message content: " + e.getMessage());
            return "";
        }
    }

    // Helper method for DP to PX conversion
    private static int dp2px(Context context, float dp) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    // Helper to create rounded background
    private static android.graphics.drawable.Drawable getRoundedBackground(int color, int radiusPx) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    /**
     * 将颜色加深指定比例
     * @param color 原始颜色
     * @param factor 加深因子 (0.0-1.0)，例如 0.1 表示加深 10%
     * @return 加深后的颜色
     */
    private static int darkenColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.max(0, (int) (Color.red(color) * (1 - factor)));
        int g = Math.max(0, (int) (Color.green(color) * (1 - factor)));
        int b = Math.max(0, (int) (Color.blue(color) * (1 - factor)));
        return Color.argb(a, r, g, b);
    }

    /**
     * 创建带按压状态的圆角背景
     * 普通状态使用原始颜色，按压状态使用加深的颜色
     * @param color 背景颜色
     * @param radiusPx 圆角半径（像素）
     * @return StateListDrawable
     */
    private static android.graphics.drawable.Drawable getSelectableRoundedBackground(int color, int radiusPx) {
        // 创建普通状态背景
        android.graphics.drawable.GradientDrawable normalDrawable = new android.graphics.drawable.GradientDrawable();
        normalDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        normalDrawable.setColor(color);
        normalDrawable.setCornerRadius(radiusPx);
        
        // 创建按压状态背景（颜色加深 10%）
        android.graphics.drawable.GradientDrawable pressedDrawable = new android.graphics.drawable.GradientDrawable();
        pressedDrawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        pressedDrawable.setColor(darkenColor(color, 0.1f));
        pressedDrawable.setCornerRadius(radiusPx);
        
        // 创建 StateListDrawable
        android.graphics.drawable.StateListDrawable stateListDrawable = new android.graphics.drawable.StateListDrawable();
        stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
        stateListDrawable.addState(new int[]{}, normalDrawable);
        
        return stateListDrawable;
    }

    // 辅助方法：判断类是否是 RecyclerView 或其子类
    private static boolean isRecyclerView(Class<?> clazz) {
        if (clazz == null) return false;
        if ("androidx.recyclerview.widget.RecyclerView".equals(clazz.getName()) || 
            "android.support.v7.widget.RecyclerView".equals(clazz.getName())) {
            return true;
        }
        return isRecyclerView(clazz.getSuperclass());
    }

    // Hook AIO消息发送相关类的所有方法以分析调用流程
    private static void hookDebugAIOSendMsgVMDelegate(ClassLoader classLoader) {
        String[] targetClasses = {
            // 尝试两个可能的包路径
            "com.tencent.mobileqq.aio.input.sendmsg.AIOSendMsgVMDelegate",
            "com.tencent.mobileqq.aio.msg.AIOSendMsgVMDelegate",
            "com.tencent.mobileqq.aio.msg.AIOSendMsgViewModel",
            "com.tencent.mobileqq.aio.input.sendmsg.AIOSendMsgViewModel"
        };

        for (String className : targetClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, classLoader);
                
                int hookedCount = 0;
                // 遍历所有方法并 Hook
                for (Method method : clazz.getDeclaredMethods()) {
                    // 过滤掉常见的基础方法以减少日志噪音
                    String methodName = method.getName();
                    if (methodName.equals("toString") || methodName.equals("hashCode") || 
                        methodName.equals("equals") || methodName.equals("getClass")) {
                        continue;
                    }
                    
                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                logMethodCall(param);
                            }
                        });
                        hookedCount++;
                    } catch (Throwable hookError) {
                        // 单个方法 Hook 失败不影响整体
                    }
                }
                
                // debugLog(TAG + ": [DEBUG] Hooked " + hookedCount + " methods in " + className);
            } catch (Throwable t) {
                // 类不存在或其他错误，跳过
            }
        }
    }




    private static void logMethodCall(XC_MethodHook.MethodHookParam param) {
        // DEBUG 日志已启用 - 用于分析引用回复发送
        try {
            String methodName = param.method.getName();
            String className = param.thisObject != null ? param.thisObject.getClass().getSimpleName() : "static";
            
            //debugLog(TAG + ": [DEBUG] " + className + "." + methodName + " called");
            //debugLog(TAG + ":   Args count: " + param.args.length);
            
            for (int i = 0; i < param.args.length; i++) {
                Object arg = param.args[i];
                String type = arg != null ? arg.getClass().getName() : "null";
                //debugLog(TAG + ":   arg[" + i + "] (" + type + "): " + arg);
                
                // 如果参数是 List，尝试打印第一个元素的详细信息
                if (arg instanceof List) {
                    List<?> list = (List<?>) arg;
                    if (!list.isEmpty()) {
                        for (int j = 0; j < list.size(); j++) {
                            Object item = list.get(j);
                            if (item != null) {
                                //debugLog(TAG + ":     List[" + j + "] class: " + item.getClass().getName());
                                // 反射打印字段
                                try {
                                    for (Field f : item.getClass().getDeclaredFields()) {
                                        f.setAccessible(true);
                                        Object val = f.get(item);
                                        String fieldType = f.getType().getName();
                                        //debugLog(TAG + ":       Field '" + f.getName() + "' (" + fieldType + "): " + val);
                                        
                                        // 深入分析 AIOElementType 相关对象
                                        if (val != null && val.getClass().getName().contains("AIOElementType")) {
                                            //debugLog(TAG + ":         -> Found AIOElementType: " + val.getClass().getName());
                                            analyzeAIOElementType(val);
                                        }
                                        
                                        // 深入分析可能的 ReplyElement
                                        if (val != null && (fieldType.contains("Reply") || f.getName().equals("h"))) {
                                            //debugLog(TAG + ":         -> Potential ReplyElement found!");
                                            analyzeReplyElement(val);
                                        }
                                    }
                                } catch (Exception e) {
                                    //debugLog(TAG + ":       Error inspecting list item: " + e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            debugLog(TAG + ": [DEBUG] Error logging method call: " + t.getMessage());
        }
    }
    
    /**
     * 深入分析 AIOElementType 对象的所有字段
     */
    private static void analyzeAIOElementType(Object element) {
        try {
            //debugLog(TAG + ":         ┌─ AIOElementType 分析 ─┐");
            for (Field f : element.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(element);
                String fieldType = f.getType().getSimpleName();
                String valStr = val != null ? val.toString() : "null";
                if (valStr.length() > 100) valStr = valStr.substring(0, 100) + "...";
                debugLog(TAG + ":         │ " + f.getName() + " (" + fieldType + "): " + valStr);
            }
           // debugLog(TAG + ":         └─────────────────────┘");
        } catch (Throwable t) {
            debugLog(TAG + ":         分析AIOElementType失败: " + t.getMessage());
        }
    }
    
    /**
     * 深入分析 ReplyElement 对象的所有字段
     * 这对于实现引用回复发送功能至关重要
     */
    private static void analyzeReplyElement(Object replyElement) {
        try {
            //debugLog(TAG + ":         ┌─ ReplyElement 分析 ─┐");
            //debugLog(TAG + ":         │ Class: " + replyElement.getClass().getName());
            for (Field f : replyElement.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(replyElement);
                String fieldType = f.getType().getSimpleName();
                String valStr = val != null ? val.toString() : "null";
                if (valStr.length() > 100) valStr = valStr.substring(0, 100) + "...";
                debugLog(TAG + ":         │ " + f.getName() + " (" + fieldType + "): " + valStr);
            }
            //debugLog(TAG + ":         └─────────────────────┘");
        } catch (Throwable t) {
            debugLog(TAG + ":         分析ReplyElement失败: " + t.getMessage());
        }
    }
}
