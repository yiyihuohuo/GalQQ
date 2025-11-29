package top.galqq.hook;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

public class MessageInterceptor {

    private static final String TAG = "GalQQ.MessageInterceptor";
    private static final int OPTION_BAR_ID = 0x7F0A1234; // Custom ID for option bar
    
    // AI选项缓存：msgId -> List<String> options
    private static final java.util.Map<String, java.util.List<String>> optionsCache = 
        new java.util.LinkedHashMap<String, java.util.List<String>>(100, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(java.util.Map.Entry<String, java.util.List<String>> eldest) {
                return size() > 100; // 最多缓存100条消息的选项
            }
        };

    public static void init(ClassLoader classLoader) {
        // Detect QQ architecture and use appropriate hook strategy
        if (top.galqq.utils.QQNTUtils.isQQNT(classLoader)) {
            XposedBridge.log(TAG + ": Detected QQNT, using QQNT hook strategy");
            hookAIOBubbleMsgItemVB(classLoader);  // QQNT architecture
        } else {
            XposedBridge.log(TAG + ": Detected legacy QQ, using TextItemBuilder hook strategy");
            hookTextItemBuilder(classLoader);      // Legacy QQ architecture
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
                XposedBridge.log(TAG + ": TextItemBuilder class not found, skipping hook");
                return;
            }
            
            XposedBridge.log(TAG + ": Found TextItemBuilder class: " + textItemBuilderClass.getName());
            
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
                XposedBridge.log(TAG + ": Failed to find target method in TextItemBuilder");
                return;
            }
            
            XposedBridge.log(TAG + ": Found target method: " + methodName);
            
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
                        
                        XposedBridge.log(TAG + ": Screen width=" + screenWidth + ", bar width=" + barWidth);
                        
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
                        
                        XposedBridge.log(TAG + ": Successfully added option bar to BaseChatItemLayout");
                        
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": Error in afterHook: " + t.getMessage());
                    }
                }
            });
            
            XposedBridge.log(TAG + ": ✓ Successfully hooked TextItemBuilder." + methodName);
            
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to hook TextItemBuilder: " + t.getMessage());
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
            // XposedBridge.log(TAG + ": Cached AI options for msgId=" + msgId);
        }
    }

    private static void setupOptionBarContent(Context context, LinearLayout bar, String msgContent, 
                                               Object msgObj, String msgId, String conversationId) {
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
                        // XposedBridge.log("Removed current message from context to avoid duplication");
                    }
                }
                
                // 确保数量不超过配置
                if (contextMessages.size() > contextCount) {
                    contextMessages = contextMessages.subList(contextMessages.size() - contextCount, contextMessages.size());
                }
            }

            // 判断选项条是否在屏幕可见区域（用于设置优先级）
            android.graphics.Rect rect = new android.graphics.Rect();
            boolean isVisible = bar.getGlobalVisibleRect(rect) && bar.isShown();
            AiRateLimitedQueue.Priority priority = isVisible ? 
                AiRateLimitedQueue.Priority.HIGH : 
                AiRateLimitedQueue.Priority.NORMAL;
            
            // 【新增】提取当前消息的元数据（发送人昵称和时间戳）
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
                XposedBridge.log(TAG + ": Failed to extract current message metadata: " + t.getMessage());
            }
            
            // 提交到限流队列（带优先级、上下文和当前消息元数据）
            AiRateLimitedQueue.getInstance(context).submitRequest(
                context, 
                msgContent, 
                msgId, // 传递msgId用于持久化
                priority,
                contextMessages, // 传递上下文消息
                currentSenderName, // 当前消息发送人昵称
                currentTimestamp, // 当前消息时间戳
                new HttpAiClient.AiCallback() {
                    @Override
                    public void onSuccess(List<String> options) {
                        // 恢复顶部间距
                        bar.setPadding(0, dp2px(context, 5), 0, dp2px(context, 5));
                        
                        // 缓存AI结果
                        cacheOptions(msgId, options);
                        populateBarAndShow(context, bar, options, msgObj);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        // AI失败时隐藏选项条（已在UI线程）
                        bar.setVisibility(View.GONE);
                    }
                }
            );
        } else {
            // 本地词库模式：每次随机生成，不使用缓存
            useDictionaryNT(context, bar, msgObj);
        }
    }

    private static void useDictionary(Context context, LinearLayout bar, Object chatMessage) {
        DictionaryManager.loadDictionary(context);
        List<String> options = DictionaryManager.pickRandomLines(3);
        populateBarAndShow(context, bar, options, chatMessage);
    }

    // 填充选项条并显示（如果有选项的话）
    private static void populateBarAndShow(Context context, LinearLayout bar, List<String> options, Object chatMessage) {
        bar.removeAllViews();
        
        if (options == null || options.isEmpty()) {
            bar.setVisibility(View.GONE); // 没有选项时隐藏
            return;
        }
        
        for (String option : options) {
            TextView tv = new TextView(context);
            tv.setText(option);
            tv.setTextSize(13);
            tv.setPadding(dp2px(context, 12), dp2px(context, 8), dp2px(context, 12), dp2px(context, 8));
            tv.setBackground(getRoundedBackground(Color.parseColor("#F2F2F2"), dp2px(context, 12)));
            tv.setTextColor(Color.BLACK);
            
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int maxWidth = screenWidth - dp2px(context, 16);
            
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(0, 0, 0, dp2px(context, 6));
            tv.setMaxWidth(maxWidth);
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            
            tv.setOnClickListener(v -> {
                sendMessage(context, option, chatMessage);
            });
            
            bar.addView(tv);
        }
        
        bar.setVisibility(View.VISIBLE); // 有选项时显示
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
            XposedBridge.log(TAG + ": Failed to send message: " + e.getMessage());
            Toast.makeText(context, "发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
                XposedBridge.log(TAG + ": Found handleUIState method");
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
                XposedBridge.log(TAG + ": Found bind method");
                XposedBridge.hookMethod(bindMethod, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object msgItem = param.args[1];
                        processQQNTMessage(param.thisObject, msgItem, getMsgRecord);
                    }
                });
                return;
            }
            
            XposedBridge.log(TAG + ": Failed to find handleUIState or bind method in AIOBubbleMsgItemVB");

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error in hookAIOBubbleMsgItemVB: " + t.getMessage());
            XposedBridge.log(t);
        }
    }

    private static void processQQNTMessage(Object aioBubbleMsgItemVB, Object msgItem, Method getMsgRecord) {
        try {
            // Get MsgRecord
            Object msgRecord = getMsgRecord.invoke(msgItem);
            
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
                // XposedBridge.log(TAG + ": [Context] Activity/Context: " + activityName);
                
                // 【转发消息过滤】如果是转发消息详情页，则不显示AI选项条
                if (activityName != null && activityName.contains("MultiForwardActivity")) {
                    XposedBridge.log(TAG + ": ⚠️ Skipping forwarded message in MultiForwardActivity");
                    return;
                }
            } catch (Throwable t) {
                // XposedBridge.log(TAG + ": [Context] Error getting activity: " + t.getMessage());
            }
            
            // 【方法2】识别 ViewHolder 类型
            try {
                String viewHolderName = aioBubbleMsgItemVB.getClass().getName();
                // XposedBridge.log(TAG + ": [ViewHolder] Type: " + viewHolderName);
            } catch (Throwable t) {
                // XposedBridge.log(TAG + ": [ViewHolder] Error: " + t.getMessage());
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
                                // XposedBridge.log(TAG + ": [Adapter] Type: " + adapter.getClass().getName());
                            } else {
                                // XposedBridge.log(TAG + ": [Adapter] Adapter is null");
                            }
                        } catch (NoSuchMethodException e) {
                            // 可能是混淆后的名字，或者不是标准的RecyclerView
                            // XposedBridge.log(TAG + ": [Adapter] getAdapter method not found on " + parentClass.getName());
                        }
                        break;
                    }
                    parent = parent.getParent();
                }
                if (!foundRecyclerView) {
                    // XposedBridge.log(TAG + ": [Adapter] RecyclerView not found in parent hierarchy");
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": [Adapter] Error: " + t.getMessage());
            }
            
            // Check if module is enabled
            if (!ConfigManager.isModuleEnabled()) {
                return; // Module is disabled, don't show option bar
            }
            
            // Check if it's a received message
            int sendType = XposedHelpers.getIntField(msgRecord, "sendType");
            boolean isSelf = (sendType == 1); // 1=自己发送, 0=收到的消息

            // Filter out unwanted message types
            int msgType = XposedHelpers.getIntField(msgRecord, "msgType");
            
            // 【调试日志】打印消息类型相关字段，用于识别转发聊天记录
            try {
                int subMsgType = XposedHelpers.getIntField(msgRecord, "subMsgType");
                
                XposedBridge.log(TAG + ": ===== Message Type Analysis =====");
                XposedBridge.log(TAG + ": msgType=" + msgType + ", subMsgType=" + subMsgType);
                XposedBridge.log(TAG + ": sendType=" + sendType);
                
                // 【过滤转发聊天记录容器】msgType=11且subMsgType=7是转发聊天记录的容器消息
                // 不过滤容器内的具体Text消息（msgType=2），因为它们的字段与普通消息相同
                if (msgType == 11 && subMsgType == 7) {
                    XposedBridge.log(TAG + ": ⚠️ FORWARDED CHAT RECORD CONTAINER - SKIPPING!");
                    XposedBridge.log(TAG + ": ================================");
                    return; // 跳过转发聊天记录容器
                }
                
                XposedBridge.log(TAG + ": ================================");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Error detecting forwarded message: " + t.getMessage());
            }
            
            // 5 = Gray Tips (Revoke), 3 = File, 7 = Video
            if (msgType == 5 || msgType == 3 || msgType == 7) {
                return;
            }

            // Filter out messages with no text content (e.g. pure images, stickers)
            String msgContent = getMessageContentNT(msgRecord);
            if (msgContent.isEmpty()) {
                return;
            }
            
            // 【关键修复】无条件清理旧选项条（RecyclerView的ViewHolder会复用）
            LinearLayout existingBar = rootView.findViewById(OPTION_BAR_ID);
            if (existingBar != null) {
                rootView.removeView(existingBar);
            }
            
            // 黑白名单过滤
            String senderUin = null;
            try {
                Object senderUinObj = XposedHelpers.getObjectField(msgRecord, "senderUin");
                senderUin = String.valueOf(senderUinObj);
                String filterMode = ConfigManager.getFilterMode();
                
                // XposedBridge.log(TAG + ": Filter - senderUin=" + senderUin + ", mode=" + filterMode);
                
                if ("blacklist".equals(filterMode)) {
                    if (ConfigManager.isInBlacklist(senderUin)) {
                        // XposedBridge.log(TAG + ": ✗ BLOCKED by blacklist: " + senderUin);
                        return; // 不通过过滤，不添加选项条
                    }
                } else if ("whitelist".equals(filterMode)) {
                    if (!ConfigManager.isInWhitelist(senderUin)) {
                        // XposedBridge.log(TAG + ": ✗ BLOCKED by whitelist (not in list): " + senderUin);
                        return; // 不通过过滤，不添加选项条
                    }
                }
                // XposedBridge.log(TAG + ": ✓ PASSED filter: " + senderUin);
                
                // 【详细调试日志】在提取到所有变量后输出
                XposedBridge.log(TAG + ": isSelf=" + isSelf);
                XposedBridge.log(TAG + ": senderUin=" + senderUin);
                if (msgContent != null && !msgContent.isEmpty()) {
                    String contentPreview = msgContent.length() > 50 ? msgContent.substring(0, 50) + "..." : msgContent;
                    XposedBridge.log(TAG + ": msgContent=" + contentPreview);
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Error filtering by list: " + t.getMessage());
                XposedBridge.log(t);
                return; // 过滤失败时不添加选项条
            }
            
            // 获取消息ID（用于AI缓存和上下文去重）
            String msgId = null;
            try {
                Object msgIdObj = XposedHelpers.getObjectField(msgRecord, "msgId");
                msgId = String.valueOf(msgIdObj);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Failed to get msgId: " + t.getMessage());
            }
            
            // 保存消息到上下文缓存（带去重）
            String peerUin = null; // 提升作用域
            long msgTime = 0; // 【修复】提升作用域，供后续AI判断使用
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
                    XposedBridge.log(TAG + ": Failed to get sender name: " + t.getMessage());
                }
                
                // 如果所有尝试都失败，使用UIN
                if (senderName == null || senderName.trim().isEmpty()) {
                    senderName = senderUin != null ? senderUin : "未知";
                }
                
                // 获取peerUin（会话ID）
                try {
                    Object peerUinObj = XposedHelpers.getObjectField(msgRecord, "peerUin");
                    peerUin = String.valueOf(peerUinObj);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": Failed to get peerUin: " + t.getMessage());
                }

                // 使用peerUin作为conversationId（群聊时为群号，私聊时为对方QQ）
                // 这样可以确保群聊中不同用户的消息被聚合到同一个上下文中
                if (peerUin != null && !msgContent.isEmpty()) {
                    // 获取消息时间戳
                    try {
                        Object msgTimeObj = XposedHelpers.getObjectField(msgRecord, "msgTime");
                        if (msgTimeObj != null) {
                            // msgTime通常是秒，转换为毫秒
                            msgTime = Long.parseLong(String.valueOf(msgTimeObj)) * 1000L;
                        }
                    } catch (Throwable t) {
                        // 失败则使用当前时间（降级）
                        msgTime = System.currentTimeMillis();
                    }
                    
                    // 【新增】提取引用回复的内容并整合到消息
                    try {
                        List<?> elements = (List<?>) XposedHelpers.getObjectField(msgRecord, "elements");
                        if (elements != null && !elements.isEmpty()) {
                            // 【调试】输出元素列表信息
                            XposedBridge.log(TAG + ": Elements count: " + elements.size());
                            for (int i = 0; i < elements.size(); i++) {
                                Object element = elements.get(i);
                                XposedBridge.log(TAG + ":   [" + i + "] " + element.getClass().getSimpleName());
                                
                                // 尝试所有可能的引用字段名
                                for (String fieldName : new String[]{"replyElement", "g", "h"}) {
                                    try {
                                        Object field = XposedHelpers.getObjectField(element, fieldName);
                                        if (field != null) {
                                            XposedBridge.log(TAG + ":     ." + fieldName + " exists: " + field.getClass().getSimpleName());
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            }
                            
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
                                                        // XposedBridge.log(TAG + ": Extracted reply sender from content: " + replySenderName);
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
                                            
                                            XposedBridge.log(TAG + ": ✓ 已将引用信息整合到消息内容");
                                        }
                                        break; // 只处理第一个replyElement
                                    }
                                } catch (Throwable ignored) {
                                    // replyElement字段不存在或获取失败，继续下一个element
                                }
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": Error extracting reply content: " + t.getMessage());
                    }
                    
                    // 【修改自己消息的显示格式为"昵称[我]"】
                    if (isSelf && senderName != null && !senderName.isEmpty()) {
                        senderName = senderName + "[我]";
                    }
                    
                    MessageContextManager.addMessage(peerUin, senderName, msgContent, isSelf, msgId, msgTime);
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Error saving message to context: " + t.getMessage());
            }
            
            // 【只为对方消息创建选项栏，自己的消息不显示UI】
            if (!isSelf) {
                // 【关键修复】防止加载历史记录时触发AI刷屏
                // 动态获取配置的阈值（秒转毫秒）
                int thresholdSeconds = ConfigManager.getHistoryThreshold();
                long thresholdMs = thresholdSeconds * 1000L;
                
                // 检查是否已缓存AI选项（如果有缓存，即使超过阈值也显示）
                boolean hasCachedOptions = (msgId != null && optionsCache.containsKey(msgId));
                
                long currentTime = System.currentTimeMillis();
                if (!hasCachedOptions && Math.abs(currentTime - msgTime) > thresholdMs) {
                    // XposedBridge.log(TAG + ": ⏳ Skipping option bar for old message without cache (diff=" + (currentTime - msgTime) + "ms)");
                    return; 
                }

                // 创建新选项条（每次都重新创建，不复用View）
                LinearLayout optionBar = createEmptyOptionBarNT(context);
                optionBar.setId(OPTION_BAR_ID);
                
                // 先添加选项条到布局（空的，稍后异步填充内容）
                // XposedBridge.log(TAG + ": RootView class: " + rootView.getClass().getName());
                Class<?> constraintLayoutClass = null;
                try {
                    constraintLayoutClass = XposedHelpers.findClass("androidx.constraintlayout.widget.ConstraintLayout", context.getClassLoader());
                } catch (Throwable t) {
                    // Ignore if class not found
                }

                if (constraintLayoutClass != null && constraintLayoutClass.isAssignableFrom(rootView.getClass())) {
                    // XposedBridge.log(TAG + ": RootView is a ConstraintLayout (or subclass)");
                    handleConstraintLayout(context, rootView, optionBar, msgRecord);
                } else if (rootView.getClass().getName().contains("ConstraintLayout")) {
                    // XposedBridge.log(TAG + ": RootView name contains ConstraintLayout");
                    handleConstraintLayout(context, rootView, optionBar, msgRecord);
                } else {
                    // XposedBridge.log(TAG + ": RootView is NOT identified as ConstraintLayout, using legacy handler");
                    handleLegacyLayout(context, rootView, optionBar);
                }
                
                // 填充选项条内容（本地词库或AI，传递peerUin作为conversationId，确保群聊上下文正确）
                String conversationId = (peerUin != null && !peerUin.isEmpty()) ? peerUin : senderUin;
                fillOptionBarContent(context, optionBar, msgRecord, msgId, conversationId);
            }
            
            // XposedBridge.log(TAG + ": Successfully added option bar to QQNT message");

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error processing QQNT message: " + t.getMessage());
            XposedBridge.log(t);
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
            // 1. Add view to ConstraintLayout first (needed for ConstraintSet to work)
            // 使用MATCH_CONSTRAINT(0)让宽度由约束决定
            // XposedBridge.log(TAG + ": Creating LayoutParams with MATCH_CONSTRAINT");
            
            // Use ConstraintLayout.LayoutParams if possible
            Class<?> constraintLayoutParamsClass = XposedHelpers.findClass("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams", context.getClassLoader());
            ViewGroup.LayoutParams clp = (ViewGroup.LayoutParams) constraintLayoutParamsClass.getConstructor(int.class, int.class)
                .newInstance(0, ViewGroup.LayoutParams.WRAP_CONTENT); // 宽度0 = MATCH_CONSTRAINT
            
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
            // XposedBridge.log(TAG + ": Trying to find bubble via BubbleLayout class name...");
            for (int i = 0; i < rootView.getChildCount(); i++) {
                View child = rootView.getChildAt(i);
                if (child.getClass().getName().contains("BubbleLayout") && child.getId() != View.NO_ID) {
                    msgBubbleId = child.getId();
                    // XposedBridge.log(TAG + ": Found bubble via class name, ID: " + msgBubbleId);
                    break;
                }
            }

            // 2. Text Content Search (Fallback)
            if (msgBubbleId == -1) {
                String msgContent = getMessageContentNT(msgRecord);
                if (!msgContent.isEmpty()) {
                    XposedBridge.log(TAG + ": BubbleLayout not found, trying text content: " + msgContent);
                    View textContainer = findViewWithText(rootView, msgContent);
                    if (textContainer != null) {
                        View bubble = textContainer;
                        while (bubble.getParent() != rootView && bubble.getParent() instanceof View) {
                            bubble = (View) bubble.getParent();
                        }
                        
                        if (bubble.getParent() == rootView && bubble.getId() != View.NO_ID) {
                            msgBubbleId = bubble.getId();
                            XposedBridge.log(TAG + ": Found bubble via text content, ID: " + msgBubbleId + ", Class: " + bubble.getClass().getName());
                        }
                    }
                }
            }

            // 3. LinearLayout Fallback (Last Resort)
            if (msgBubbleId == -1) {
                XposedBridge.log(TAG + ": Text search failed, trying LinearLayout fallback...");
                for (int i = 0; i < rootView.getChildCount(); i++) {
                    View child = rootView.getChildAt(i);
                    if (child instanceof LinearLayout && child.getId() != View.NO_ID) {
                        msgBubbleId = child.getId();
                        XposedBridge.log(TAG + ": Found bubble via LinearLayout fallback, ID: " + msgBubbleId);
                        break;
                    }
                }
            }
            
            if (msgBubbleId != -1) {
                // Connect TOP of OptionBar to BOTTOM of MessageBubble
                // connect(int startID, int startSide, int endID, int endSide, int margin)
                // Sides: TOP=3, BOTTOM=4, LEFT=1, RIGHT=2, START=6, END=7
                int TOP = 3, BOTTOM = 4, LEFT = 1, RIGHT = 2, START = 6, END = 7;
                
                XposedHelpers.callMethod(constraintSet, "connect", 
                    OPTION_BAR_ID, TOP, msgBubbleId, BOTTOM, dp2px(context, 5));
                
                // Align START (Left) of OptionBar to START (Left) of MessageBubble with 8dp margin
                // 向左移动选项条
                XposedHelpers.callMethod(constraintSet, "connect", 
                    OPTION_BAR_ID, START, msgBubbleId, START, dp2px(context, 8)); // 8dp左边距
                
                // 限制右边界到parent的END，留16dp margin
                XposedHelpers.callMethod(constraintSet, "connect",
                    OPTION_BAR_ID, END, 0 /* PARENT_ID */, END, dp2px(context, 16)); // 16dp右边距
                
                // Apply constraints
                XposedHelpers.callMethod(constraintSet, "applyTo", rootView);
                // XposedBridge.log(TAG + ": Applied ConstraintSet layout with margins");
            } else {
                XposedBridge.log(TAG + ": Could not find message bubble ID for ConstraintLayout");
            }
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Error handling ConstraintLayout: " + e.getMessage());
            XposedBridge.log(e);
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
    
    // 创建空的选项条（稍后填充内容）
    private static LinearLayout createEmptyOptionBarNT(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setGravity(Gravity.LEFT);
        bar.setPadding(0, dp2px(context, 5), 0, dp2px(context, 5));
        bar.setVisibility(View.GONE); // 初始隐藏，等有内容后再显示
        return bar;
    }
    
    // 填充选项条内容（AI或本地词库）
    private static void fillOptionBarContent(Context context, LinearLayout bar, Object msgRecord, 
                                             String msgId, String conversationId) {
        String msgContent = getMessageContentNT(msgRecord);
        
        // 【AI缓存优化】如果启用AI且缓存中有选项，直接使用缓存数据
        if (ConfigManager.isAiEnabled() && msgId != null && optionsCache.containsKey(msgId)) {
            // XposedBridge.log(TAG + ": ✓ Using cached AI options for msgId=" + msgId);
            List<String> cachedOptions = optionsCache.get(msgId);
            populateBarAndShow(context, bar, cachedOptions, msgRecord);
            return;
        }
        
        // 否则重新获取选项（AI或本地词库）
        setupOptionBarContent(context, bar, msgContent, msgRecord, msgId, conversationId);
    }
    
    private static void useDictionaryNT(Context context, LinearLayout bar, Object msgRecord) {
        DictionaryManager.loadDictionary(context);
        List<String> options = DictionaryManager.pickRandomLines(3);
        populateBarAndShow(context, bar, options, msgRecord);
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
            XposedBridge.log(TAG + ": Failed to extract message content: " + e.getMessage());
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

    // 辅助方法：判断类是否是 RecyclerView 或其子类
    private static boolean isRecyclerView(Class<?> clazz) {
        if (clazz == null) return false;
        if ("androidx.recyclerview.widget.RecyclerView".equals(clazz.getName()) || 
            "android.support.v7.widget.RecyclerView".equals(clazz.getName())) {
            return true;
        }
        return isRecyclerView(clazz.getSuperclass());
    }
}
