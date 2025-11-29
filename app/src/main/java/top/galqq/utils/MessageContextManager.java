package top.galqq.utils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XposedBridge;

/**
 * 消息上下文管理器
 * 
 * 功能：
 * 1. 缓存每个会话的历史消息
 * 2. 为AI调用提供上下文
 * 3. 自动管理内存，避免泄漏
 */
public class MessageContextManager {
    
    private static final String TAG = "GalQQ.ContextManager";
    
    // 每个会话最多缓存的消息数
    private static final int MAX_MESSAGES_PER_CONVERSATION = 50;
    
    // 最大会话数（超过则清理最旧的）
    private static final int MAX_CONVERSATIONS = 100;
    
    // conversationId -> messages
    private static final Map<String, ConversationContext> contextMap = new ConcurrentHashMap<>();
    
    /**
     * 聊天消息对象
     */
    public static class ChatMessage {
        public final String senderName;   // 发送人名称
        public final String content;       // 消息内容
        public final boolean isSelf;       // 是否是自己发送的
        public final long timestamp;       // 时间戳
        public final String msgId;         // 消息ID（用于去重）
        
        public ChatMessage(String senderName, String content, boolean isSelf, long timestamp, String msgId) {
            this.senderName = senderName;
            this.content = content;
            this.isSelf = isSelf;
            this.timestamp = timestamp;
            this.msgId = msgId;
        }
        
        @Override
        public String toString() {
            return senderName + ": " + content;
        }
    }
    
    /**
     * 会话上下文
     */
    private static class ConversationContext {
        final LinkedList<ChatMessage> messages = new LinkedList<>();
        long lastAccessTime = System.currentTimeMillis();
        
        synchronized void addMessage(ChatMessage message) {
            messages.add(message);
            
            // 按时间戳排序（处理乱序加载问题）
            java.util.Collections.sort(messages, new java.util.Comparator<ChatMessage>() {
                @Override
                public int compare(ChatMessage m1, ChatMessage m2) {
                    return Long.compare(m1.timestamp, m2.timestamp);
                }
            });
            
            // 限制消息数量
            while (messages.size() > MAX_MESSAGES_PER_CONVERSATION) {
                messages.removeFirst();
            }
            lastAccessTime = System.currentTimeMillis();
        }
        
        synchronized List<ChatMessage> getRecentMessages(int count) {
            lastAccessTime = System.currentTimeMillis();
            
            if (count <= 0 || messages.isEmpty()) {
                return new ArrayList<>();
            }
            
            int actualCount = Math.min(count, messages.size());
            // 获取最近的N条消息
            List<ChatMessage> result = new ArrayList<>(actualCount);
            int startIndex = messages.size() - actualCount;
            for (int i = startIndex; i < messages.size(); i++) {
                result.add(messages.get(i));
            }
            
            return result;
        }
    }
    
    /**
     * 添加消息到缓存（带去重和时间戳）
     * 
     * @param conversationId 会话ID（通常是对方的UIN或群ID）
     * @param senderName 发送人名称
     * @param content 消息内容
     * @param isSelf 是否是自己发送的
     * @param msgId 消息ID（用于去重）
     * @param msgTime 消息时间戳（毫秒）
     */
    public static void addMessage(String conversationId, String senderName, String content, 
                                  boolean isSelf, String msgId, long msgTime) {
        XposedBridge.log(TAG + ": 📥 准备添加消息到上下文");
        XposedBridge.log(TAG + ":   conversationId=" + conversationId);
        XposedBridge.log(TAG + ":   senderName=" + senderName);
        XposedBridge.log(TAG + ":   content=" + (content != null ? content.substring(0, Math.min(50, content.length())) : "null"));
        XposedBridge.log(TAG + ":   isSelf=" + isSelf);
        XposedBridge.log(TAG + ":   msgId=" + msgId);
        XposedBridge.log(TAG + ":   timestamp=" + msgTime);
        
        if (conversationId == null || content == null || content.trim().isEmpty()) {
            XposedBridge.log(TAG + ": ❌ 拒绝添加：conversationId或content为空");
            return;
        }
        
        try {
            ConversationContext context = contextMap.get(conversationId);
            if (context == null) {
                // 检查是否需要清理旧会话
                if (contextMap.size() >= MAX_CONVERSATIONS) {
                    cleanup();
                }
                
                context = new ConversationContext();
                contextMap.put(conversationId, context);
                XposedBridge.log(TAG + ": Created new conversation context: " + conversationId);
            }
            
            // 去重：如果msgId不为null，检查是否已存在
            if (msgId != null) {
                synchronized (context.messages) {
                    for (ChatMessage msg : context.messages) {
                        if (msgId.equals(msg.msgId)) {
                            XposedBridge.log(TAG + ": ⚠️ 跳过重复消息 (msgId=" + msgId + ")");
                            return;
                        }
                    }
                }
            }
            
            // 如果传入的时间戳无效（0），使用当前时间
            long timestamp = msgTime > 0 ? msgTime : System.currentTimeMillis();
            
            ChatMessage message = new ChatMessage(
                senderName != null ? senderName : "未知",
                content,
                isSelf,
                timestamp,
                msgId
            );
            
            context.addMessage(message);
            
            XposedBridge.log(TAG + ": ✅ 成功添加消息 [" + conversationId + "] " 
                + senderName + ": " + content.substring(0, Math.min(30, content.length())) 
                + (content.length() > 30 ? "..." : ""));
            XposedBridge.log(TAG + ":   当前会话消息数=" + context.messages.size());
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Error adding message: " + e.getMessage());
        }
    }
    
    /**
     * 获取上下文消息
     * 
     * @param conversationId 会话ID
     * @param count 需要的消息数量
     * @return 最近的N条消息（按时间顺序，最旧的在前）
     */
    public static List<ChatMessage> getContext(String conversationId, int count) {
        if (conversationId == null || count <= 0) {
            return new ArrayList<>();
        }
        
        try {
            ConversationContext context = contextMap.get(conversationId);
            if (context == null) {
                return new ArrayList<>();
            }
            
            List<ChatMessage> messages = context.getRecentMessages(count);
            XposedBridge.log(TAG + ": Retrieved " + messages.size() + " context messages for " + conversationId);
            return messages;
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Error getting context: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 清理过期的会话上下文
     * 删除最久未访问的会话，直到数量降到合理范围
     */
    public static void cleanup() {
        try {
            if (contextMap.size() <= MAX_CONVERSATIONS) {
                return;
            }
            
            // 找到最久未访问的会话
            String oldestKey = null;
            long oldestTime = Long.MAX_VALUE;
            
            for (Map.Entry<String, ConversationContext> entry : contextMap.entrySet()) {
                if (entry.getValue().lastAccessTime < oldestTime) {
                    oldestTime = entry.getValue().lastAccessTime;
                    oldestKey = entry.getKey();
                }
            }
            
            if (oldestKey != null) {
                contextMap.remove(oldestKey);
                XposedBridge.log(TAG + ": Cleaned up old conversation: " + oldestKey);
            }
            
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Error during cleanup: " + e.getMessage());
        }
    }
    
    /**
     * 清除指定会话的上下文
     * 
     * @param conversationId 会话ID
     */
    public static void clearConversation(String conversationId) {
        if (conversationId != null) {
            contextMap.remove(conversationId);
            XposedBridge.log(TAG + ": Cleared conversation: " + conversationId);
        }
    }
    
    /**
     * 清除所有上下文
     */
    public static void clearAll() {
        contextMap.clear();
        XposedBridge.log(TAG + ": Cleared all conversations");
    }
    
    /**
     * 获取当前缓存的会话数量
     */
    public static int getConversationCount() {
        return contextMap.size();
    }
}
