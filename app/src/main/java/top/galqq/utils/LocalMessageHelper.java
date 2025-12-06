package top.galqq.utils;

import android.content.Context;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import top.galqq.utils.MessageContextManager;

/**
 * 本地消息记录获取助手
 * 用于从QQ本地数据库获取消息历史，而不是通过拦截实时缓存
 */
public class LocalMessageHelper {
    
    private static final String TAG = "GalQQ.LocalMessageHelper";
    
    /**
     * 从QQ本地数据库获取指定会话的消息历史
     * 
     * @param context 上下文
     * @param conversationId 会话ID（群聊为群号，私聊为对方QQ）
     * @param count 需要获取的消息数量
     * @param currentMsgId 当前消息ID（用于定位，可为null）
     * @return 消息列表（从旧到新）
     */
    public static List<MessageContextManager.ChatMessage> getLocalMessages(
            Context context, String conversationId, int count, String currentMsgId) {
        
        List<MessageContextManager.ChatMessage> messages = new ArrayList<>();
        
        if (context == null || conversationId == null || conversationId.isEmpty()) {
            debugLog(TAG + ": Invalid parameters");
            return messages;
        }
        
        try {
            // 获取 QQ 的消息管理服务
            // QQNT 架构中，消息通过 MsgService 管理
            ClassLoader classLoader = context.getClassLoader();
            
            // 尝试获取 MessageManager 或 MsgService
            Class<?> msgServiceClass = tryFindMsgServiceClass(classLoader);
            if (msgServiceClass == null) {
                debugLog(TAG + ": 无法找到消息服务类");
                return messages;
            }
            
            debugLog(TAG + ": 找到消息服务类: " + msgServiceClass.getName());
            
            // 获取消息服务实例
            Object msgService = getMsgServiceInstance(context, msgServiceClass);
            if (msgService == null) {
                debugLog(TAG + ": 无法获取消息服务实例");
                return messages;
            }
            
            // 查找获取消息历史的方法
            Method getHistoryMethod = findGetHistoryMethod(msgServiceClass);
            if (getHistoryMethod == null) {
                debugLog(TAG + ": 无法找到获取历史消息的方法");
                return messages;
            }
            
            debugLog(TAG + ": 找到历史消息方法: " + getHistoryMethod.getName());
            
            // 调用方法获取消息列表
            Object result = invokeGetHistory(msgService, getHistoryMethod, conversationId, count, currentMsgId);
            
            // 解析返回结果
            if (result != null) {
                messages = parseMessageResult(result);
                debugLog(TAG + ": 成功获取 " + messages.size() + " 条本地消息");
            }
            
        } catch (Throwable t) {
            debugLog(TAG + ": 获取本地消息失败: " + t.getMessage());
            XposedBridge.log(t);
        }
        
        return messages;
    }
    
    /**
     * 尝试找到消息服务类
     */
    private static Class<?> tryFindMsgServiceClass(ClassLoader classLoader) {
        // QQNT 可能的类名
        String[] possibleClasses = {
            "com.tencent.mobileqq.service.message.MessageRecordFactory",
            "com.tencent.qqnt.kernel.nativeinterface.MsgService",
            "com.tencent.mobileqq.app.MessageHandler",
            "com.tencent.imcore.message.QQMessageFacade",
            "com.tencent.mobileqq.data.MessageRecord"
        };
        
        for (String className : possibleClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, classLoader);
                if (clazz != null) {
                    return clazz;
                }
            } catch (Throwable t) {
                // 继续尝试下一个
            }
        }
        
        return null;
    }
    
    /**
     * 获取消息服务实例
     */
    private static Object getMsgServiceInstance(Context context, Class<?> msgServiceClass) {
        try {
            // 尝试通过单例模式获取
            try {
                Method getInstance = msgServiceClass.getMethod("getInstance");
                return getInstance.invoke(null);
            } catch (NoSuchMethodException e) {
                // 尝试通过 getDefault
                try {
                    Method getDefault = msgServiceClass.getMethod("getDefault");
                    return getDefault.invoke(null);
                } catch (NoSuchMethodException e2) {
                    // 尝试从 Application 获取
                    Object app = context.getApplicationContext();
                    Method getService = app.getClass().getMethod("getMsgService");
                    return getService.invoke(app);
                }
            }
        } catch (Throwable t) {
            debugLog(TAG + ": 获取服务实例失败: " + t.getMessage());
        }
        
        return null;
    }
    
    /**
     * 查找获取历史消息的方法
     */
    private static Method findGetHistoryMethod(Class<?> msgServiceClass) {
        // 常见的方法名
        String[] methodNames = {
            "getHistoryMsg",
            "getLocalMessages",
            "queryMessages",
            "getMsgList",
            "getMessageRecords"
        };
        
        for (String methodName : methodNames) {
            try {
                for (Method method : msgServiceClass.getDeclaredMethods()) {
                    if (method.getName().equals(methodName) || 
                        method.getName().contains("History") || 
                        method.getName().contains("Message")) {
                        return method;
                    }
                }
            } catch (Throwable t) {
                // 继续尝试
            }
        }
        
        return null;
    }
    
    /**
     * 调用获取历史消息的方法
     */
    private static Object invokeGetHistory(Object msgService, Method method, 
                                          String conversationId, int count, String currentMsgId) {
        try {
            method.setAccessible(true);
            
            // 根据参数数量调用
            Class<?>[] paramTypes = method.getParameterTypes();
            
            if (paramTypes.length == 2) {
                // (String conversationId, int count)
                return method.invoke(msgService, conversationId, count);
            } else if (paramTypes.length == 3) {
                // (String conversationId, int count, String startMsgId)
                return method.invoke(msgService, conversationId, count, currentMsgId);
            } else {
                // 尝试默认参数
                return method.invoke(msgService, conversationId);
            }
            
        } catch (Throwable t) {
            debugLog(TAG + ": 调用历史消息方法失败: " + t.getMessage());
        }
        
        return null;
    }
    
    /**
     * 解析消息结果
     */
    private static List<MessageContextManager.ChatMessage> parseMessageResult(Object result) {
        List<MessageContextManager.ChatMessage> messages = new ArrayList<>();
        
        try {
            // 如果返回的是 List
            if (result instanceof List) {
                List<?> msgList = (List<?>) result;
                
                for (Object msgRecord : msgList) {
                    try {
                        MessageContextManager.ChatMessage msg = parseMessageRecord(msgRecord);
                        if (msg != null) {
                            messages.add(msg);
                        }
                    } catch (Throwable t) {
                        debugLog(TAG + ": 解析单条消息失败: " + t.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            debugLog(TAG + ": 解析消息结果失败: " + t.getMessage());
        }
        
        return messages;
    }
    
    /**
     * 解析单条消息记录
     */
    private static MessageContextManager.ChatMessage parseMessageRecord(Object msgRecord) {
        try {
            // 获取消息内容
            String content = "";
            try {
                Object msgObj = XposedHelpers.getObjectField(msgRecord, "msg");
                if (msgObj != null) {
                    content = String.valueOf(msgObj);
                }
            } catch (Throwable t) {
                // 尝试其他字段名
                try {
                    Object elementsObj = XposedHelpers.getObjectField(msgRecord, "elements");
                    if (elementsObj instanceof List) {
                        content = extractTextFromElements((List<?>) elementsObj);
                    }
                } catch (Throwable t2) {
                    // 忽略
                }
            }
            
            // 获取发送者信息
            String senderName = "";
            try {
                Object nameObj = XposedHelpers.getObjectField(msgRecord, "sendRemarkName");
                if (nameObj != null && !String.valueOf(nameObj).isEmpty()) {
                    senderName = String.valueOf(nameObj);
                } else {
                    nameObj = XposedHelpers.getObjectField(msgRecord, "sendNickName");
                    if (nameObj != null) {
                        senderName = String.valueOf(nameObj);
                    }
                }
            } catch (Throwable t) {
                senderName = "未知";
            }
            
            // 判断是否是自己发送的
            boolean isSelf = false;
            try {
                int sendType = XposedHelpers.getIntField(msgRecord, "sendType");
                isSelf = (sendType == 1);
            } catch (Throwable t) {
                // 忽略
            }
            
            // 获取消息ID
            String msgId = null;
            try {
                Object msgIdObj = XposedHelpers.getObjectField(msgRecord, "msgId");
                if (msgIdObj != null) {
                    msgId = String.valueOf(msgIdObj);
                }
            } catch (Throwable t) {
                // 忽略
            }
            
            // 获取时间戳
            long timestamp = System.currentTimeMillis();
            try {
                Object timeObj = XposedHelpers.getObjectField(msgRecord, "msgTime");
                if (timeObj != null) {
                    timestamp = Long.parseLong(String.valueOf(timeObj)) * 1000L;
                }
            } catch (Throwable t) {
                // 忽略
            }
            
            // 创建消息对象（使用构造函数，因为字段是final的）
            MessageContextManager.ChatMessage msg = new MessageContextManager.ChatMessage(
                senderName,  // senderName
                content,     // content
                isSelf,      // isSelf
                timestamp,   // timestamp
                msgId        // msgId
            );
            
            return msg;
            
        } catch (Throwable t) {
            debugLog(TAG + ": 解析消息记录失败: " + t.getMessage());
        }
        
        return null;
    }
    
    /**
     * 从 elements 列表中提取文本内容
     */
    private static String extractTextFromElements(List<?> elements) {
        StringBuilder sb = new StringBuilder();
        
        for (Object element : elements) {
            try {
                // 尝试获取文本内容
                Object textObj = XposedHelpers.getObjectField(element, "textElement");
                if (textObj != null) {
                    Object contentObj = XposedHelpers.getObjectField(textObj, "content");
                    if (contentObj != null) {
                        sb.append(String.valueOf(contentObj));
                    }
                }
            } catch (Throwable t) {
                // 忽略
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 调试日志输出
     */
    private static void debugLog(String message) {
        try {
            if (top.galqq.config.ConfigManager.isDebugHookLogEnabled()) {
                XposedBridge.log(message);
            }
        } catch (Throwable ignored) {
            // ConfigManager 未初始化时忽略
        }
    }
}
