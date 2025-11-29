package top.galqq.utils;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 消息发送追踪 - 深入分析消息对象
 */
public class MessageSendTracker {
    private static final String TAG = "GalQQ.MSG_DETAIL";
    private static boolean isTracking = false;
    
    public static void startTracking(Context context) {
        if (isTracking) return;
        
        XposedBridge.log(TAG + ": ========== 启动消息内容追踪 ==========");
        isTracking = true;
        
        try {
            hookAIOSendMsgVMDelegate(context);
            hookToSaveVMDelegateInstance(context);  // Hook来保存实例
            XposedBridge.log(TAG + ": ========== 追踪已就绪 ==========");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 启动失败");
            XposedBridge.log(t);
        }
    }
    
    /**
     * Hook AIOSendMsgVMDelegate.n0 来保存实例
     */
    /**
     * Hook AIOSendMsgVMDelegate.n0 和 构造函数 来保存实例
     */
    /**
     * Hook AIOSendMsgVMDelegate.n0 和 构造函数 来保存实例
     */
    private static void hookToSaveVMDelegateInstance(Context context) {
        try {
            Class<?> vmDelegateClass = XposedHelpers.findClass(
                "com.tencent.mobileqq.aio.input.sendmsg.AIOSendMsgVMDelegate", 
                context.getClassLoader()
            );
            
            // Hook 构造函数，在对象创建时就保存实例
            XposedBridge.hookAllConstructors(vmDelegateClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log(TAG + ": AIOSendMsgVMDelegate构造函数被调用，保存实例");
                    SendMessageHelper.setAIOSendMsgVMDelegate(param.thisObject);
                }
            });
            
            // 动态查找并Hook发送方法 (替代硬编码的 "n0")
            Method sendMethod = null;
            for (Method method : vmDelegateClass.getDeclaredMethods()) {
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length == 4 && 
                    paramTypes[0] == java.util.List.class && 
                    paramTypes[1] == android.os.Bundle.class && 
                    paramTypes[2] == Long.class && 
                    paramTypes[3] == String.class) {
                    sendMethod = method;
                    break;
                }
            }
            
            if (sendMethod != null) {
                XposedBridge.hookMethod(sendMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // 保存AIOSendMsgVMDelegate实例供SendMessageHelper使用
                        SendMessageHelper.setAIOSendMsgVMDelegate(param.thisObject);
                    }
                });
                XposedBridge.log(TAG + ": 动态Hook发送方法成功: " + sendMethod.getName());
            } else {
                XposedBridge.log(TAG + ": 未找到符合特征的发送方法，仅依赖构造函数Hook");
            }
            
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook保存VM Delegate实例失败: " + t.getMessage());
        }
    }
    
    /**
     * Hook AIOSendMsgVMDelegate - 重点分析n0/l0方法
     */
    private static void hookAIOSendMsgVMDelegate(Context context) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                "com.tencent.mobileqq.aio.input.sendmsg.AIOSendMsgVMDelegate", 
                context.getClassLoader()
            );
            
            for (Method method : cls.getDeclaredMethods()) {
                final String methodName = method.getName();
                final Class<?>[] paramTypes = method.getParameterTypes();
                
                // 只Hook n0, l0, f0 这些关键方法
                if (!methodName.equals("n0") && !methodName.equals("l0") && !methodName.equals("f0")) {
                    continue;
                }
                
                try {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            XposedBridge.log(TAG + ": ═══════════════════════════════════");
                            XposedBridge.log(TAG + ": [AIOSendMsgVMDelegate." + methodName + "] (" + param.args.length + "参数)");
                            XposedBridge.log(TAG + ": 参数类型: " + Arrays.toString(paramTypes));
                            
                            for (int i = 0; i < param.args.length; i++) {
                                Object arg = param.args[i];
                                if (arg == null) {
                                    XposedBridge.log(TAG + ": [" + i + "] null");
                                    continue;
                                }
                                
                                String className = arg.getClass().getName();
                                XposedBridge.log(TAG + ": [" + i + "] " + className);
                                
                                // 如果是ArrayList，深入分析
                                if (arg instanceof java.util.List) {
                                    java.util.List<?> list = (java.util.List<?>) arg;
                                    XposedBridge.log(TAG + ":   → List大小: " + list.size());
                                    
                                    for (int j = 0; j < list.size(); j++) {
                                        Object item = list.get(j);
                                        if (item != null) {
                                            XposedBridge.log(TAG + ":   → [" + j + "] " + item.getClass().getName());
                                            
                                            // 如果是msg.data.a，深入分析其字段
                                            if (item.getClass().getName().contains("msg.data.a")) {
                                                analyzeMsgDataObject(item);
                                            }
                                        }
                                    }
                                }
                                // 如果是Bundle
                                else if (className.contains("Bundle")) {
                                    android.os.Bundle bundle = (android.os.Bundle) arg;
                                    String inputText = bundle.getString("input_text");
                                    if (inputText != null) {
                                        XposedBridge.log(TAG + ":   ★★★ 消息文本: " + inputText + " ★★★");
                                    }
                                }
                                // 其他对象
                                else {
                                    String str = arg.toString();
                                    if (str.length() > 200) str = str.substring(0, 200) + "...";
                                    XposedBridge.log(TAG + ":   = " + str);
                                }
                            }
                            XposedBridge.log(TAG + ": ═══════════════════════════════════");
                            
                            // 【提取发送的消息并添加到上下文】- 只在n0方法中执行
                            if (methodName.equals("n0")) {
                                try {
                                    // 从元素列表中提取文本内容和引用内容
                                    java.util.List<?> elementsList = (java.util.List<?>) param.args[0];
                                    if (elementsList != null && !elementsList.isEmpty()) {
                                        StringBuilder messageText = new StringBuilder();
                                        String replyContent = null;
                                        String replyNick = null;
                                        
                                        for (Object element : elementsList) {
                                            try {
                                                // 提取 TextElement (字段c)
                                                Object textElement = null;
                                                try {
                                                    textElement = XposedHelpers.getObjectField(element, "c");
                                                } catch (Throwable ignored) {}
                                                
                                                if (textElement != null) {
                                                    // 提取content字段
                                                    try {
                                                        Object contentObj = XposedHelpers.getObjectField(textElement, "content");
                                                        if (contentObj != null) {
                                                            String content = String.valueOf(contentObj);
                                                            if (!content.trim().isEmpty()) {
                                                                messageText.append(content);
                                                            }
                                                        }
                                                    } catch (Throwable ignored) {}
                                                }
                                                
                                                // 提取 ReplyElement (字段h)
                                                if (replyContent == null) {  // 只提取第一个
                                                    try {
                                                        Object replyElement = XposedHelpers.getObjectField(element, "h");
                                                        if (replyElement != null) {
                                                            // 提取引用的内容
                                                            try {
                                                                Object replyContentObj = XposedHelpers.getObjectField(replyElement, "replyContent");
                                                                if (replyContentObj != null) {
                                                                    replyContent = String.valueOf(replyContentObj);
                                                                }
                                                            } catch (Throwable ignored) {}
                                                            
                                                            // 提取引用的昵称
                                                            try {
                                                                Object replyNickObj = XposedHelpers.getObjectField(replyElement, "replyNick");
                                                                if (replyNickObj != null) {
                                                                    replyNick = String.valueOf(replyNickObj);
                                                                }
                                                            } catch (Throwable ignored) {}
                                                        }
                                                    } catch (Throwable ignored) {}
                                                }
                                            } catch (Throwable ignored) {}
                                        }
                                        
                                        String finalText = messageText.toString().trim();
                                        
                                        // 获取peerUin
                                        String peerUin = null;
                                        try {
                                            if (param.args.length > 2 && param.args[2] != null) {
                                                peerUin = String.valueOf(param.args[2]);
                                            }
                                        } catch (Throwable ignored) {}
                                        
                                        // 先添加引用的消息（如果有）
                                        if (peerUin != null && replyContent != null && !replyContent.trim().isEmpty()) {
                                            MessageContextManager.addMessage(
                                                peerUin,
                                                replyNick != null ? replyNick : "引用消息",
                                                "[引用] " + replyContent,
                                                false,  // 被引用的不是自己发的
                                                null,
                                                System.currentTimeMillis() - 1000  // 时间稍早
                                            );
                                            XposedBridge.log(TAG + ": ✓ 已将引用消息添加到上下文: " + replyContent.substring(0, Math.min(30, replyContent.length())));
                                        }
                                        
                                        // 再添加自己发送的消息
                                        if (peerUin != null && !finalText.isEmpty()) {
                                            MessageContextManager.addMessage(
                                                peerUin,
                                                "我",
                                                finalText,
                                                true,  // isSelf = true
                                                null,
                                                System.currentTimeMillis()
                                            );
                                            XposedBridge.log(TAG + ": ✓ 已将发送的消息添加到上下文: [" + peerUin + "] " + finalText);
                                        }
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + ": 提取发送消息失败: " + t.getMessage());
                                    XposedBridge.log(t);
                                }
                            }
                        }
                    });
                    XposedBridge.log(TAG + ": Hook " + methodName + " 成功");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": Hook " + methodName + " 失败: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Hook AIOSendMsgVMDelegate失败: " + t.getMessage());
        }
    }
    
    /**
     * 深入分析 com.tencent.mobileqq.aio.msg.data.a 对象
     */
    private static void analyzeMsgDataObject(Object obj) {
        try {
            XposedBridge.log(TAG + ":   ┌─ 分析msg.data.a对象 ─┐");
            
            Class<?> cls = obj.getClass();
            
            // 列出所有字段
            for (Field field : cls.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    
                    String fieldName = field.getName();
                    String typeName = field.getType().getSimpleName();
                    
                    if (value != null) {
                        String valStr = value.toString();
                        if (valStr.length() > 150) valStr = valStr.substring(0, 150) + "...";
                        XposedBridge.log(TAG + ":   │ " + fieldName + " (" + typeName + ") = " + valStr);
                        
                        // 如果是List，展开
                        if (value instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>) value;
                            XposedBridge.log(TAG + ":   │   List大小: " + list.size());
                            for (int i = 0; i < Math.min(3, list.size()); i++) {
                                Object item = list.get(i);
                                if (item != null) {
                                    XposedBridge.log(TAG + ":   │   [" + i + "] " + item.getClass().getSimpleName());
                                }
                            }
                        }
                    } else {
                        XposedBridge.log(TAG + ":   │ " + fieldName + " (" + typeName + ") = null");
                    }
                } catch (Throwable ignored) {}
            }
            
            XposedBridge.log(TAG + ":   └─────────────────────┘");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ":   分析失败: " + t.getMessage());
        }
    }
}
