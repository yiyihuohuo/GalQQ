package top.galqq.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import top.galqq.hook.CookieHookManager;

/**
 * Cookie 获取器
 * 从 QQ 运行时获取认证 Cookie，用于 API 请求
 * 
 * 获取优先级：
 * 1. 内存缓存（通过Hook获取）
 * 2. SQLite数据库
 * 3. WebView CookieManager
 */
public class CookieHelper {

    private static final String TAG = "GalQQ.CookieHelper";
    
    /**
     * 调试日志输出（受配置开关控制）
     */
    private static void debugLog(String message) {
        try {
            if (top.galqq.config.ConfigManager.isVerboseLogEnabled()) {
                XposedBridge.log(message);
            }
        } catch (Throwable ignored) {
            // ConfigManager 未初始化时忽略
        }
    }
    
    /**
     * Cookie获取来源枚举
     */
    public enum CookieSource {
        MEMORY,     // 从内存Hook获取
        SQLITE,     // 从SQLite数据库获取
        WEBVIEW,    // 从WebView CookieManager获取
        FAILED      // 获取失败
    }
    
    // 最后一次获取的来源
    private static CookieSource sLastSource = CookieSource.FAILED;
    
    // QQ 空间域名
    private static final String QZONE_DOMAIN = "qzone.qq.com";
    
    // QQ WebView Cookie 数据库可能的相对路径
    private static final String[] COOKIE_DB_RELATIVE_PATHS = {
        "app_webview_tool/Default/Cookies",
        "app_webview/Default/Cookies",
        "app_xwalk/Default/Cookies",
        "databases/webview.db"
    };

    // 调试开关：禁用SQLite兜底方案
    private static final boolean DISABLE_SQLITE_FALLBACK = false;
    
    /**
     * 获取所有需要的 Cookie 字符串
     * @param context 上下文
     * @return Cookie 字符串，格式如 "uin=xxx; skey=xxx; p_uin=xxx; p_skey=xxx"
     */
    public static String getCookies(Context context) {
        // 打印当前内存缓存状态（调试用）
        debugLog(TAG + ": ========== Cookie获取开始 ==========");
        debugLog(TAG + ": [DEBUG] 内存缓存状态:");
        debugLog(TAG + ":   skey: " + (CookieHookManager.getCachedSkey() != null ? "有值" : "空"));
        debugLog(TAG + ":   p_skey: " + (CookieHookManager.getCachedPSkey() != null ? "有值" : "空"));
        debugLog(TAG + ":   uin: " + (CookieHookManager.getCachedUin() != null ? CookieHookManager.getCachedUin() : "空"));
        String puid = CookieHookManager.getCachedPUid();
        debugLog(TAG + ":   p_uid: " + (puid != null ? puid : "空"));
        debugLog(TAG + ":   isCacheValid: " + CookieHookManager.isCacheValid());
        long lastUpdate = CookieHookManager.getLastUpdateTime();
        if (lastUpdate > 0) {
            long ageSeconds = (System.currentTimeMillis() - lastUpdate) / 1000;
            debugLog(TAG + ":   缓存时间: " + ageSeconds + "秒前");
        } else {
            debugLog(TAG + ":   缓存时间: 从未更新");
        }
        
        // 方法0：如果内存缓存无效，尝试主动触发Cookie获取
        if (!CookieHookManager.isCacheValid()) {
            debugLog(TAG + ": [TRIGGER] 内存缓存无效，尝试主动触发获取...");
            triggerCookieFetch(context);
            
            // 等待一小段时间，让Hook有机会捕获数据
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // 忽略中断
            }
            
            // 再次检查缓存状态
            debugLog(TAG + ": [DEBUG] 触发后内存缓存状态:");
            debugLog(TAG + ":   skey: " + (CookieHookManager.getCachedSkey() != null ? "有值" : "空"));
            debugLog(TAG + ":   p_skey: " + (CookieHookManager.getCachedPSkey() != null ? "有值" : "空"));
            debugLog(TAG + ":   uin: " + (CookieHookManager.getCachedUin() != null ? CookieHookManager.getCachedUin() : "空"));
            String puidAfter = CookieHookManager.getCachedPUid();
            debugLog(TAG + ":   p_uid: " + (puidAfter != null ? puidAfter : "空"));
            debugLog(TAG + ":   isCacheValid: " + CookieHookManager.isCacheValid());
            long lastUpdateAfter = CookieHookManager.getLastUpdateTime();
            if (lastUpdateAfter > 0) {
                long ageSecondsAfter = (System.currentTimeMillis() - lastUpdateAfter) / 1000;
                debugLog(TAG + ":   缓存时间: " + ageSecondsAfter + "秒前");
            }
        }
        
        // 方法1：优先从内存缓存获取（通过Hook）
        try {
            if (CookieHookManager.isCacheValid()) {
                String memoryCookies = buildCookiesFromMemory();
                if (memoryCookies != null && !memoryCookies.isEmpty()) {
                    sLastSource = CookieSource.MEMORY;
                    if (CookieHookManager.isCachePossiblyExpired()) {
                        debugLog(TAG + ": [MEMORY] Cookie可能已过期，但仍然使用");
                    }
                    debugLog(TAG + ": [MEMORY] ✓ 从内存缓存获取到 Cookie: " + memoryCookies.length() + " 字符");
                    debugLog(TAG + ": ========== Cookie获取完成 ==========");
                    return memoryCookies;
                }
            } else {
                debugLog(TAG + ": [MEMORY] ✗ 内存缓存无效");
            }
        } catch (Throwable t) {
            debugLog(TAG + ": [MEMORY] 内存Cookie获取失败: " + t.getMessage());
        }
        
        // 方法2：SQLite兜底（可禁用）
        if (!DISABLE_SQLITE_FALLBACK) {
            try {
                String sqliteCookies = getCookiesFromSqlite(context);
                if (sqliteCookies != null && !sqliteCookies.isEmpty() && sqliteCookies.contains("p_skey")) {
                    sLastSource = CookieSource.SQLITE;
                    debugLog(TAG + ": [SQLITE] 从数据库获取到 Cookie: " + sqliteCookies.length() + " 字符");
                    return sqliteCookies;
                }
            } catch (Throwable t) {
                debugLog(TAG + ": [SQLITE] Cookie获取失败: " + t.getMessage());
            }
        } else {
            debugLog(TAG + ": [SQLITE] ⚠ SQLite兜底已禁用（调试模式）");
        }
        
        // 方法3：尝试从 WebView CookieManager 获取完整 Cookie
        try {
            android.webkit.CookieManager webCookieManager = android.webkit.CookieManager.getInstance();
            String allCookies = webCookieManager.getCookie("https://" + QZONE_DOMAIN);
            if (allCookies != null && !allCookies.isEmpty()) {
                sLastSource = CookieSource.WEBVIEW;
                debugLog(TAG + ": [WEBVIEW] 获取到完整 Cookie: " + allCookies.length() + " 字符");
                return allCookies;
            }
        } catch (Throwable t) {
            debugLog(TAG + ": [WEBVIEW] Cookie获取失败: " + t.getMessage());
        }
        
        // 方法4：降级手动拼接 Cookie
        StringBuilder cookies = new StringBuilder();
        
        String uin = getPUin(context);
        String skey = getSkey(context);
        String pSkey = getPSkey(context);
        
        // uin 字段（不带 o 前缀）
        if (uin != null && !uin.isEmpty()) {
            String cleanUin = uin.startsWith("o") ? uin.substring(1) : uin;
            cookies.append("uin=o").append(cleanUin).append("; ");
        }
        
        if (skey != null && !skey.isEmpty()) {
            cookies.append("skey=").append(skey).append("; ");
        }
        
        // p_uin 字段（带 o 前缀）
        if (uin != null && !uin.isEmpty()) {
            String cleanUin = uin.startsWith("o") ? uin.substring(1) : uin;
            cookies.append("p_uin=o").append(cleanUin).append("; ");
        }
        
        if (pSkey != null && !pSkey.isEmpty()) {
            cookies.append("p_skey=").append(pSkey).append("; ");
        }
        
        // 移除末尾的 "; "
        String result = cookies.toString();
        if (result.endsWith("; ")) {
            result = result.substring(0, result.length() - 2);
        }
        
        if (result.isEmpty()) {
            sLastSource = CookieSource.FAILED;
            debugLog(TAG + ": [FAILED] 所有方法都无法获取Cookie");
        }
        
        return result;
    }
    
    /**
     * 主动触发Cookie获取（调用QQ的方法来触发Hook）
     */
    private static void triggerCookieFetch(Context context) {
        String cachedUin = CookieHookManager.getCachedUin();
        
        debugLog(TAG + ": [TRIGGER] 开始主动触发Cookie获取");
        debugLog(TAG + ": [TRIGGER] 当前缓存状态 - UIN: " + (cachedUin != null ? "有值" : "空") + 
                 ", isCacheValid: " + CookieHookManager.isCacheValid());
        
        if (CookieHookManager.isCacheValid()) {
            debugLog(TAG + ": [TRIGGER] 缓存已有效，无需触发");
            return;
        }
        
        if (cachedUin != null && !cachedUin.isEmpty()) {
            debugLog(TAG + ": [TRIGGER] 已有缓存UIN: " + cachedUin + "，尝试主动获取skey/p_skey");
            
            if (CookieHookManager.getCachedTicketManager() != null) {
                debugLog(TAG + ": [TRIGGER] 使用缓存的TicketManager实例");
                if (CookieHookManager.fetchSkeyAndPskeyFromTicketManager(cachedUin)) {
                    debugLog(TAG + ": [TRIGGER] ✓ 从缓存TicketManager获取成功");
                    return;
                } else {
                    debugLog(TAG + ": [TRIGGER] ✗ 从缓存TicketManager获取失败，尝试其他方法");
                }
            } else {
                debugLog(TAG + ": [TRIGGER] TicketManager实例未缓存，尝试其他方法");
            }
            
            tryFetchSkeyAndPskey(context, cachedUin);
            return;
        }
        
        debugLog(TAG + ": [TRIGGER] 未找到缓存UIN，尝试从其他来源获取");
        
        String uin = null;
        
        // 方法1：通过AppRuntimeHelper获取（模仿QAuxiliary）
        try {
            Object appRuntime = AppRuntimeHelper.getAppRuntime(context);
            if (appRuntime != null) {
                Object uinObj = XposedHelpers.callMethod(appRuntime, "getCurrentAccountUin");
                if (uinObj != null) {
                    uin = String.valueOf(uinObj);
                    debugLog(TAG + ": [TRIGGER] 从AppRuntime获取到UIN: " + uin);
                    CookieHookManager.setCachedUin(uin);
                } else {
                    debugLog(TAG + ": [TRIGGER] AppRuntime.getCurrentAccountUin返回null");
                }
            } else {
                debugLog(TAG + ": [TRIGGER] AppRuntime为null");
            }
        } catch (Throwable t) {
            debugLog(TAG + ": [TRIGGER] AppRuntime获取UIN失败: " + t.getMessage());
        }
        
        // 方法2：从getCurrentUin获取
        if (uin == null || uin.isEmpty()) {
            uin = getCurrentUin(context);
            if (uin != null && !uin.isEmpty()) {
                debugLog(TAG + ": [TRIGGER] 从getCurrentUin获取到UIN: " + uin);
                CookieHookManager.setCachedUin(uin);
            } else {
                debugLog(TAG + ": [TRIGGER] getCurrentUin返回空");
            }
        }
        
        // 方法3：从SQLite的p_uin获取（不是降级，只是获取UIN值）
        if (uin == null || uin.isEmpty()) {
            uin = getUinFromSqlite(context);
            if (uin != null && !uin.isEmpty()) {
                debugLog(TAG + ": [TRIGGER] 从SQLite获取到UIN: " + uin);
                CookieHookManager.setCachedUin(uin);
            } else {
                debugLog(TAG + ": [TRIGGER] SQLite返回空");
            }
        }
        
        if (uin == null || uin.isEmpty()) {
            debugLog(TAG + ": [TRIGGER] ✗ 无法获取UIN，跳过主动触发");
            return;
        }
        
        debugLog(TAG + ": [TRIGGER] 获取到UIN: " + uin + "，开始主动触发Cookie获取");
        
        // 尝试通过多种方式获取TicketManager
        Object appRuntime = null;
        
        // 方式1：通过AppRuntimeHelper获取AppRuntime
        try {
            appRuntime = AppRuntimeHelper.getAppRuntime(context);
            if (appRuntime != null) {
                Object ticketManager = XposedHelpers.callMethod(appRuntime, "getManager", 2);
                if (ticketManager != null) {
                    triggerWithTicketManager(ticketManager, uin);
                    return;
                }
            }
        } catch (Throwable t) {
            debugLog(TAG + ": [TRIGGER] 通过AppRuntime获取TicketManager失败: " + t.getMessage());
        }
        
        // 方式2：通过HostInfo.getApplication
        try {
            android.app.Application app = HostInfo.getApplication();
            if (app != null) {
                Object runtime = XposedHelpers.callMethod(app, "getRuntime");
                Object ticketManager = XposedHelpers.callMethod(runtime, "getManager", 2);
                if (ticketManager != null) {
                    triggerWithTicketManager(ticketManager, uin);
                    return;
                }
            }
        } catch (Throwable t) {
            // 继续
        }
        
        debugLog(TAG + ": [TRIGGER] 无法获取TicketManager，等待QQ自动调用");
    }
    
    /**
     * 尝试获取skey和p_skey（当已有UIN时）
     */
    private static void tryFetchSkeyAndPskey(Context context, String uin) {
        // 方式1：通过AppRuntimeHelper获取AppRuntime
        try {
            Object appRuntime = AppRuntimeHelper.getAppRuntime(context);
            if (appRuntime != null) {
                Object ticketManager = XposedHelpers.callMethod(appRuntime, "getManager", 2);
                if (ticketManager != null) {
                    triggerWithTicketManager(ticketManager, uin);
                    return;
                }
            }
        } catch (Throwable t) {
            debugLog(TAG + ": [TRIGGER] 通过AppRuntime获取TicketManager失败: " + t.getMessage());
        }
        
        // 方式2：通过HostInfo.getApplication
        try {
            android.app.Application app = HostInfo.getApplication();
            if (app != null) {
                Object runtime = XposedHelpers.callMethod(app, "getRuntime");
                Object ticketManager = XposedHelpers.callMethod(runtime, "getManager", 2);
                if (ticketManager != null) {
                    triggerWithTicketManager(ticketManager, uin);
                    return;
                }
            }
        } catch (Throwable t) {
            debugLog(TAG + ": [TRIGGER] 通过HostInfo获取TicketManager失败: " + t.getMessage());
        }
        
        debugLog(TAG + ": [TRIGGER] 无法获取TicketManager，等待QQ自动调用");
    }
    
    /**
     * 使用TicketManager触发Cookie获取
     */
    private static void triggerWithTicketManager(Object ticketManager, String uin) {
        debugLog(TAG + ": [TRIGGER] TicketManager类型: " + ticketManager.getClass().getName());
        debugLog(TAG + ": [TRIGGER] 目标UIN: " + uin);
        
        // 缓存TicketManager实例供后续使用
        if (CookieHookManager.getCachedTicketManager() == null) {
            try {
                java.lang.reflect.Field field = CookieHookManager.class.getDeclaredField("sCachedTicketManager");
                field.setAccessible(true);
                field.set(null, ticketManager);
                debugLog(TAG + ": [TRIGGER] ✓ 缓存TicketManager实例成功");
            } catch (Throwable t) {
                debugLog(TAG + ": [TRIGGER] 缓存TicketManager实例失败: " + t.getMessage());
            }
        }
        
        try {
            Object skey = XposedHelpers.callMethod(ticketManager, "getSkey", uin);
            debugLog(TAG + ": [TRIGGER] getSkey返回类型: " + (skey != null ? skey.getClass().getName() : "null"));
            if (skey instanceof String && !((String) skey).isEmpty()) {
                CookieHookManager.setCachedSkey((String) skey);
                String preview = ((String) skey).length() > 10 ? ((String) skey).substring(0, 10) + "..." : (String) skey;
                debugLog(TAG + ": [TRIGGER] ✓ getSkey调用成功: " + preview);
            } else {
                debugLog(TAG + ": [TRIGGER] ✗ getSkey返回值为空或非String: " + skey);
            }
        } catch (Throwable t) {
            debugLog(TAG + ": [TRIGGER] getSkey调用失败: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
        }
        
        try {
            Object pskey = XposedHelpers.callMethod(ticketManager, "getPskey", uin, QZONE_DOMAIN);
            debugLog(TAG + ": [TRIGGER] getPskey返回类型: " + (pskey != null ? pskey.getClass().getName() : "null"));
            if (pskey instanceof String && !((String) pskey).isEmpty()) {
                CookieHookManager.setCachedPSkey((String) pskey);
                String preview = ((String) pskey).length() > 10 ? ((String) pskey).substring(0, 10) + "..." : (String) pskey;
                debugLog(TAG + ": [TRIGGER] ✓ getPskey调用成功: " + preview);
            } else {
                debugLog(TAG + ": [TRIGGER] ✗ getPskey返回值为空或非String: " + pskey);
            }
        } catch (Throwable t) {
            debugLog(TAG + ": [TRIGGER] getPskey调用失败: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
        }
        
        // 最后再次确认缓存状态
        debugLog(TAG + ": [TRIGGER] 最终缓存状态 - isCacheValid: " + CookieHookManager.isCacheValid());
    }
    
    /**
     * 从SQLite数据库获取UIN（仅用于获取UIN值，不是降级方案）
     */
    private static String getUinFromSqlite(Context context) {
        try {
            File dbFile = findCookieDatabase(context);
            if (dbFile == null) return null;
            
            SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            Cursor cursor = db.rawQuery("SELECT value FROM cookies WHERE name='p_uin' AND host_key LIKE ? LIMIT 1", 
                new String[]{"%" + QZONE_DOMAIN});
            
            String uin = null;
            if (cursor.moveToFirst()) {
                uin = cursor.getString(0);
                if (uin != null && uin.startsWith("o")) {
                    uin = uin.substring(1);
                }
            }
            cursor.close();
            db.close();
            
            if (uin != null) {
                debugLog(TAG + ": [TRIGGER] 从SQLite获取到UIN: " + uin);
            }
            return uin;
        } catch (Throwable t) {
            return null;
        }
    }
    
    /**
     * 从内存缓存构建Cookie字符串
     * 格式: uin=o{uin}; skey={skey}; p_uin=o{uin}; p_uid={p_uid}; p_skey={p_skey}
     */
    private static String buildCookiesFromMemory() {
        String skey = CookieHookManager.getCachedSkey();
        String pSkey = CookieHookManager.getCachedPSkey();
        String uin = CookieHookManager.getCachedUin();
        String pUid = CookieHookManager.getCachedPUid();
        
        if (skey == null || pSkey == null || uin == null) {
            return null;
        }
        
        StringBuilder cookies = new StringBuilder();
        
        // 清理uin（移除可能的o前缀）
        String cleanUin = uin.startsWith("o") ? uin.substring(1) : uin;
        
        cookies.append("uin=o").append(cleanUin).append("; ");
        cookies.append("skey=").append(skey).append("; ");
        cookies.append("p_uin=o").append(cleanUin).append("; ");
        
        // p_uid是可选的
        if (pUid != null && !pUid.isEmpty()) {
            cookies.append("p_uid=").append(pUid).append("; ");
        }
        
        cookies.append("p_skey=").append(pSkey);
        
        return cookies.toString();
    }
    
    /**
     * 获取最后一次Cookie获取的来源
     */
    public static CookieSource getLastCookieSource() {
        return sLastSource;
    }
    
    /**
     * 获取Cookie获取状态描述（用于UI显示）
     */
    public static String getCookieStatusDescription(Context context) {
        StringBuilder sb = new StringBuilder();
        
        // 来源信息
        sb.append("获取来源: ");
        switch (sLastSource) {
            case MEMORY:
                sb.append("内存缓存 ✓");
                break;
            case SQLITE:
                sb.append("SQLite数据库 ✓");
                break;
            case WEBVIEW:
                sb.append("WebView ✓");
                break;
            case FAILED:
                sb.append("获取失败 ✗");
                break;
        }
        sb.append("\n\n");
        
        // 内存缓存状态
        sb.append("内存缓存状态:\n");
        
        String skey = CookieHookManager.getCachedSkey();
        String pskey = CookieHookManager.getCachedPSkey();
        String uin = CookieHookManager.getCachedUin();
        String puid = CookieHookManager.getCachedPUid();
        
        sb.append("  skey: ").append(skey != null ? "已缓存 (" + skey.substring(0, Math.min(8, skey.length())) + "...)" : "未缓存").append("\n");
        sb.append("  p_skey: ").append(pskey != null ? "已缓存 (" + pskey.substring(0, Math.min(8, pskey.length())) + "...)" : "未缓存").append("\n");
        sb.append("  uin: ").append(uin != null ? uin : "未缓存").append("\n");
        sb.append("  p_uid: ").append(puid != null ? puid : "未缓存").append("\n");
        
        // 过期状态
        long lastUpdateTime = CookieHookManager.getLastUpdateTime();
        if (lastUpdateTime > 0) {
            long ageSeconds = (System.currentTimeMillis() - lastUpdateTime) / 1000;
            if (ageSeconds < 60) {
                sb.append("  缓存时间: ").append(ageSeconds).append("秒前");
            } else {
                long ageMinutes = ageSeconds / 60;
                sb.append("  缓存时间: ").append(ageMinutes).append("分钟前");
            }
            if (CookieHookManager.isCachePossiblyExpired()) {
                sb.append(" (可能已过期)");
            }
        } else {
            sb.append("  缓存时间: 从未更新");
        }
        
        return sb.toString();
    }
    
    /**
     * 从 QQ WebView 的 SQLite Cookie 数据库读取 Cookie
     * 
     * 表结构: cookies(creation_utc, host_key, top_frame_site_key, name, value, encrypted_value, 
     *                 path, expires_utc, is_secure, is_httponly, last_access_utc, has_expires, 
     *                 is_persistent, priority, samesite, source_scheme, source_port, 
     *                 last_update_utc, source_type, has_cross_site_ancestor)
     */
    private static String getCookiesFromSqlite(Context context) {
        // 动态查找 Cookie 数据库
        File dbFile = findCookieDatabase(context);
        if (dbFile == null) {
            debugLog(TAG + ": 未找到 Cookie 数据库");
            return null;
        }
        
        debugLog(TAG + ": 尝试读取 Cookie 数据库: " + dbFile.getAbsolutePath());
        
        SQLiteDatabase db = null;
        Cursor cursor = null;
        // 使用 Map 存储 Cookie，key 为 name，value 为 CookieEntry（包含值和更新时间）
        Map<String, CookieEntry> cookieMap = new HashMap<>();
        
        try {
            db = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            
            // 查询 qzone.qq.com 域名下的所有 Cookie
            // 按 last_update_utc 排序，确保获取最新的值
            // name 和 value 可能重复，所以需要用 last_update_utc 来判断哪个是最新的
            String query = "SELECT name, value, last_update_utc FROM cookies WHERE host_key LIKE ? ORDER BY last_update_utc DESC";
            cursor = db.rawQuery(query, new String[]{"%" + QZONE_DOMAIN});
            
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                String value = cursor.getString(1);
                long lastUpdate = cursor.getLong(2);
                
                if (name != null && value != null && !value.isEmpty()) {
                    // 只保留最新的 Cookie（因为已按 last_update_utc DESC 排序，第一个就是最新的）
                    if (!cookieMap.containsKey(name)) {
                        cookieMap.put(name, new CookieEntry(value, lastUpdate));
                    }
                }
            }
            
            debugLog(TAG + ": 从 SQLite 读取到 " + cookieMap.size() + " 个唯一 Cookie");
            
            // 打印关键 Cookie 用于调试
            for (String key : new String[]{"uin", "skey", "p_uin", "p_uid", "p_skey"}) {
                if (cookieMap.containsKey(key)) {
                    String val = cookieMap.get(key).value;
                    String preview = val.length() > 15 ? val.substring(0, 15) + "..." : val;
                    debugLog(TAG + ":   " + key + " = " + preview);
                }
            }
            
            // 将关键Cookie缓存到CookieHookManager
            if (cookieMap.containsKey("uin") || cookieMap.containsKey("p_uin")) {
                String uin = cookieMap.containsKey("p_uin") ? cookieMap.get("p_uin").value : cookieMap.get("uin").value;
                // 移除o前缀
                if (uin != null && uin.startsWith("o")) {
                    uin = uin.substring(1);
                }
                if (uin != null && !uin.isEmpty()) {
                    CookieHookManager.setCachedUin(uin);
                    debugLog(TAG + ": [SQLITE] 已缓存uin到内存: " + uin);
                }
            }
            
            if (cookieMap.containsKey("skey")) {
                CookieHookManager.setCachedSkey(cookieMap.get("skey").value);
                debugLog(TAG + ": [SQLITE] 已缓存skey到内存");
            }
            
            if (cookieMap.containsKey("p_skey")) {
                CookieHookManager.setCachedPSkey(cookieMap.get("p_skey").value);
                debugLog(TAG + ": [SQLITE] 已缓存p_skey到内存");
            }
            
            if (cookieMap.containsKey("p_uid")) {
                CookieHookManager.setCachedPUid(cookieMap.get("p_uid").value);
                debugLog(TAG + ": [SQLITE] 已缓存p_uid到内存");
            }
            
            // 按照正确的顺序拼接 Cookie
            StringBuilder sb = new StringBuilder();
            String[] orderedKeys = {"uin", "skey", "p_uin", "p_uid", "uskey", "p_skey"};
            
            for (String key : orderedKeys) {
                if (cookieMap.containsKey(key)) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(key).append("=").append(cookieMap.get(key).value);
                }
            }
            
            // 添加其他可能需要的 Cookie（排除已添加的）
            java.util.Set<String> orderedKeySet = new java.util.HashSet<>(java.util.Arrays.asList(orderedKeys));
            for (Map.Entry<String, CookieEntry> entry : cookieMap.entrySet()) {
                String key = entry.getKey();
                if (!orderedKeySet.contains(key)) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(key).append("=").append(entry.getValue().value);
                }
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            debugLog(TAG + ": 读取 SQLite Cookie 失败: " + e.getMessage());
            return null;
        } finally {
            if (cursor != null) cursor.close();
            if (db != null) db.close();
        }
    }
    
    /**
     * Cookie 条目，包含值和最后更新时间
     */
    private static class CookieEntry {
        String value;
        long lastUpdate;
        
        CookieEntry(String value, long lastUpdate) {
            this.value = value;
            this.lastUpdate = lastUpdate;
        }
    }
    
    /**
     * 动态查找 Cookie 数据库文件
     * @param context 上下文
     * @return 数据库文件，如果未找到返回 null
     */
    private static File findCookieDatabase(Context context) {
        String dataDir = context.getApplicationInfo().dataDir;
        debugLog(TAG + ": 应用数据目录: " + dataDir);
        
        // 尝试已知的相对路径
        for (String relativePath : COOKIE_DB_RELATIVE_PATHS) {
            File dbFile = new File(dataDir, relativePath);
            if (dbFile.exists() && dbFile.isFile()) {
                debugLog(TAG + ": 找到 Cookie 数据库: " + dbFile.getAbsolutePath());
                return dbFile;
            }
        }
        
        // 递归搜索包含 "Cookies" 的文件
        File foundDb = searchCookieDatabase(new File(dataDir), 0);
        if (foundDb != null) {
            debugLog(TAG + ": 搜索找到 Cookie 数据库: " + foundDb.getAbsolutePath());
            return foundDb;
        }
        
        // 尝试其他用户目录（多用户场景）
        String[] userDirs = {"/data/user/0/com.tencent.mobileqq", "/data/data/com.tencent.mobileqq"};
        for (String userDir : userDirs) {
            if (!userDir.equals(dataDir)) {
                for (String relativePath : COOKIE_DB_RELATIVE_PATHS) {
                    File dbFile = new File(userDir, relativePath);
                    if (dbFile.exists() && dbFile.isFile()) {
                        debugLog(TAG + ": 在其他目录找到 Cookie 数据库: " + dbFile.getAbsolutePath());
                        return dbFile;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 递归搜索 Cookie 数据库文件
     * @param dir 搜索目录
     * @param depth 当前深度
     * @return 找到的数据库文件，未找到返回 null
     */
    private static File searchCookieDatabase(File dir, int depth) {
        // 限制搜索深度，避免性能问题
        if (depth > 4 || dir == null || !dir.isDirectory()) {
            return null;
        }
        
        try {
            File[] files = dir.listFiles();
            if (files == null) return null;
            
            for (File file : files) {
                if (file.isFile() && file.getName().equals("Cookies")) {
                    // 验证是否是 SQLite 数据库
                    if (isValidCookieDatabase(file)) {
                        return file;
                    }
                } else if (file.isDirectory() && !file.getName().startsWith(".")) {
                    File found = searchCookieDatabase(file, depth + 1);
                    if (found != null) return found;
                }
            }
        } catch (Exception e) {
            // 忽略权限错误
        }
        
        return null;
    }
    
    /**
     * 验证文件是否是有效的 Cookie 数据库
     */
    private static boolean isValidCookieDatabase(File file) {
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            // 检查是否有 cookies 表
            Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='cookies'", null);
            boolean hasTable = cursor.moveToFirst();
            cursor.close();
            return hasTable;
        } catch (Exception e) {
            return false;
        } finally {
            if (db != null) db.close();
        }
    }

    /**
     * 获取 skey Cookie
     * @param context 上下文
     * @return skey 值，获取失败返回 null
     */
    public static String getSkey(Context context) {
        return getCookie(context, "skey");
    }

    /**
     * 获取 p_skey Cookie
     * @param context 上下文
     * @return p_skey 值，获取失败返回 null
     */
    public static String getPSkey(Context context) {
        return getCookie(context, "p_skey");
    }

    /**
     * 获取 p_uin Cookie（当前登录的 QQ 号，不带 o 前缀）
     * @param context 上下文
     * @return QQ 号（纯数字），获取失败返回 null
     */
    public static String getPUin(Context context) {
        try {
            // 方法1：从 AppRuntime 获取当前登录账号
            String uin = getCurrentUin(context);
            if (uin != null && !uin.isEmpty()) {
                // 移除可能的 o 前缀
                return uin.startsWith("o") ? uin.substring(1) : uin;
            }
            
            // 方法2：从 Cookie 获取
            String cookieUin = getCookie(context, "p_uin");
            if (cookieUin != null && !cookieUin.isEmpty()) {
                // 移除可能的 o 前缀
                return cookieUin.startsWith("o") ? cookieUin.substring(1) : cookieUin;
            }
            
            // 方法3：从 uin Cookie 获取
            String uinCookie = getCookie(context, "uin");
            if (uinCookie != null && !uinCookie.isEmpty()) {
                return uinCookie.startsWith("o") ? uinCookie.substring(1) : uinCookie;
            }
            
            return null;
        } catch (Throwable t) {
            debugLog(TAG + ": 获取 p_uin 失败: " + t.getMessage());
            return null;
        }
    }

    /**
     * 获取指定名称的 Cookie
     * @param context 上下文
     * @param name Cookie 名称
     * @return Cookie 值，获取失败返回 null
     */
    public static String getCookie(Context context, String name) {
        try {
            // 方法0：优先从内存缓存获取
            if (CookieHookManager.isCacheValid()) {
                if ("skey".equals(name)) {
                    String skey = CookieHookManager.getCachedSkey();
                    if (skey != null && !skey.isEmpty()) {
                        return skey;
                    }
                } else if ("p_skey".equals(name)) {
                    String pskey = CookieHookManager.getCachedPSkey();
                    if (pskey != null && !pskey.isEmpty()) {
                        return pskey;
                    }
                } else if ("uin".equals(name) || "p_uin".equals(name)) {
                    String uin = CookieHookManager.getCachedUin();
                    if (uin != null && !uin.isEmpty()) {
                        return uin;
                    }
                } else if ("p_uid".equals(name)) {
                    String puid = CookieHookManager.getCachedPUid();
                    if (puid != null && !puid.isEmpty()) {
                        return puid;
                    }
                }
            }
            
            // 尝试通过 QQ 的 CookieManager 获取
            ClassLoader classLoader = context.getClassLoader();
            
            // 方法1：使用 Tencent 的 CookieManager
            try {
                Class<?> cookieManagerClass = XposedHelpers.findClass(
                    "com.tencent.mobileqq.webview.CookieManager", 
                    classLoader
                );
                Object cookieManager = XposedHelpers.callStaticMethod(cookieManagerClass, "getInstance");
                String cookie = (String) XposedHelpers.callMethod(cookieManager, "getCookie", QZONE_DOMAIN, name);
                if (cookie != null && !cookie.isEmpty()) {
                    return cookie;
                }
            } catch (Throwable t) {
                // 方法1失败，尝试其他方法
            }
            
            // 方法2：使用 Android WebView 的 CookieManager
            try {
                android.webkit.CookieManager webCookieManager = android.webkit.CookieManager.getInstance();
                String allCookies = webCookieManager.getCookie("https://" + QZONE_DOMAIN);
                if (allCookies != null) {
                    String[] cookiePairs = allCookies.split(";");
                    for (String pair : cookiePairs) {
                        String[] keyValue = pair.trim().split("=", 2);
                        if (keyValue.length == 2 && keyValue[0].trim().equals(name)) {
                            return keyValue[1].trim();
                        }
                    }
                }
            } catch (Throwable t) {
                // 方法2失败
            }
            
            // 方法3：通过 QQ 的 TicketManager 获取
            try {
                Class<?> ticketManagerClass = XposedHelpers.findClass(
                    "com.tencent.mobileqq.app.TicketManager",
                    classLoader
                );
                
                // 获取 AppRuntime
                Class<?> baseAppClass = XposedHelpers.findClass(
                    "com.tencent.common.app.BaseApplicationImpl",
                    classLoader
                );
                Object app = XposedHelpers.callStaticMethod(baseAppClass, "getApplication");
                Object runtime = XposedHelpers.callMethod(app, "getRuntime");
                
                // 获取 TicketManager
                Object ticketManager = XposedHelpers.callMethod(runtime, "getManager", 2); // 2 = TicketManager
                
                if ("skey".equals(name)) {
                    String skey = (String) XposedHelpers.callMethod(ticketManager, "getSkey", getCurrentUin(context));
                    if (skey != null && !skey.isEmpty()) {
                        return skey;
                    }
                } else if ("p_skey".equals(name)) {
                    String pskey = (String) XposedHelpers.callMethod(ticketManager, "getPskey", getCurrentUin(context), QZONE_DOMAIN);
                    if (pskey != null && !pskey.isEmpty()) {
                        return pskey;
                    }
                }
            } catch (Throwable t) {
                // 方法3失败
            }
            
            debugLog(TAG + ": 无法获取 Cookie: " + name);
            return null;
            
        } catch (Throwable t) {
            debugLog(TAG + ": 获取 Cookie 失败: " + t.getMessage());
            return null;
        }
    }

    /**
     * 获取当前登录的 QQ 号
     */
    private static String getCurrentUin(Context context) {
        try {
            ClassLoader classLoader = context.getClassLoader();
            
            // 通过 AppRuntime 获取
            Class<?> baseAppClass = XposedHelpers.findClass(
                "com.tencent.common.app.BaseApplicationImpl",
                classLoader
            );
            Object app = XposedHelpers.callStaticMethod(baseAppClass, "getApplication");
            Object runtime = XposedHelpers.callMethod(app, "getRuntime");
            String uin = (String) XposedHelpers.callMethod(runtime, "getCurrentAccountUin");
            
            return uin;
        } catch (Throwable t) {
            debugLog(TAG + ": 获取当前 UIN 失败: " + t.getMessage());
            return null;
        }
    }

    /**
     * 检查 Cookie 是否可用
     * @param context 上下文
     * @return true 如果所有必需的 Cookie 都可用
     */
    public static boolean isCookiesAvailable(Context context) {
        debugLog(TAG + ": ========== Cookie 可用性检查 ==========");
        
        String cookies = getCookies(context);
        
        if (cookies == null || cookies.isEmpty()) {
            debugLog(TAG + ": Cookie 字符串为空");
            debugLog(TAG + ": =======================================");
            return false;
        }
        
        boolean hasSkey = cookies.contains("skey=");
        boolean hasPSkey = cookies.contains("p_skey=");
        boolean hasUin = cookies.contains("uin=");
        
        debugLog(TAG + ": Cookie 组件检查:");
        debugLog(TAG + ":   skey: " + (hasSkey ? "✓" : "✗"));
        debugLog(TAG + ":   p_skey: " + (hasPSkey ? "✓" : "✗"));
        debugLog(TAG + ":   uin: " + (hasUin ? "✓" : "✗"));
        debugLog(TAG + ":   来源: " + sLastSource);
        
        boolean available = hasSkey && hasPSkey && hasUin;
        
        if (!available) {
            debugLog(TAG + ": Cookie 不可用");
        } else {
            debugLog(TAG + ": Cookie 可用 ✓");
        }
        
        debugLog(TAG + ": =======================================");
        return available;
    }
}