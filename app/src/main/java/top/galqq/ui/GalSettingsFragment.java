package top.galqq.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;
import top.galqq.R;
import top.galqq.config.ConfigManager;

public class GalSettingsFragment extends PreferenceFragmentCompat {

    /**
     * 更新Provider ListPreference的summary显示
     * @param pref ListPreference
     * @param provider 服务商标识
     */
    private void updateProviderSummary(androidx.preference.ListPreference pref, String provider) {
        String displayName = ConfigManager.getProviderDisplayName(provider);
        String apiUrl = ConfigManager.getDefaultApiUrl(provider);
        if (apiUrl.isEmpty()) {
            apiUrl = ConfigManager.getApiUrl();
        }
        pref.setSummary(displayName + "\n" + apiUrl);
    }
    
    /**
     * 更新Reasoning Effort的summary显示
     */
    private void updateReasoningEffortSummary(androidx.preference.ListPreference pref, String effort) {
        String displayText;
        switch (effort) {
            case "off":
                displayText = "已关闭";
                break;
            case "minimal":
                displayText = "minimal - 最小思考";
                break;
            case "none":
                displayText = "none - 无思考";
                break;
            case "low":
                displayText = "low - 低强度";
                break;
            case "medium":
                displayText = "medium - 中等强度";
                break;
            case "high":
                displayText = "high - 高强度";
                break;
            default:
                displayText = effort;
        }
        pref.setSummary("当前: " + displayText + "\n并不是所有模型都支持此参数，可以选择关闭");
    }

    /**
     * 更新提示词管理的summary显示
     * 显示第一个启用的提示词名称（按顺序优先级）
     */
    private void updatePromptManagerSummary(Preference pref) {
        java.util.List<ConfigManager.PromptItem> list = ConfigManager.getPromptList();
        if (list.isEmpty()) {
            pref.setSummary("管理多个提示词，点击切换不同风格");
            return;
        }
        // 找到第一个启用的提示词
        String firstName = null;
        for (ConfigManager.PromptItem item : list) {
            if (item.enabled) {
                firstName = item.name;
                break;
            }
        }
        if (firstName != null) {
            pref.setSummary("当前: " + firstName + " (共" + list.size() + "个)");
        } else {
            // 所有提示词都被禁用
            pref.setSummary("所有提示词已禁用 (共" + list.size() + "个)");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从提示词管理返回时刷新显示
        Preference promptManagerPref = findPreference("gal_prompt_manager");
        if (promptManagerPref != null) {
            updatePromptManagerSummary(promptManagerPref);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Don't use SharedPreferences at all, we'll manually handle everything
        setPreferencesFromResource(R.xml.preferences_gal, rootKey);
        
        // Initialize MMKV
        ConfigManager.init(requireContext());
        
        // Bind preferences to MMKV
        bindPreferences();
    }

    private void bindPreferences() {
        // Module Enable Switch - use base Preference class to avoid ClassCastException
        Preference enableSwitch = findPreference(ConfigManager.KEY_ENABLED);
        if (enableSwitch != null) {
            // For SwitchPreference, we need to handle it differently
            if (enableSwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) enableSwitch).setChecked(ConfigManager.isModuleEnabled());
            }
            enableSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setModuleEnabled((Boolean) newValue);
                return true;
            });
        }

        // AI Enable Switch
        Preference aiEnableSwitch = findPreference(ConfigManager.KEY_AI_ENABLED);
        if (aiEnableSwitch != null) {
            if (aiEnableSwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) aiEnableSwitch).setChecked(ConfigManager.isAiEnabled());
            }
            aiEnableSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setAiEnabled((Boolean) newValue);
                return true;
            });
        }

        // Prompt Manager (提示词管理)
        Preference promptManagerPref = findPreference("gal_prompt_manager");
        if (promptManagerPref != null) {
            // 更新summary显示当前使用的提示词名称
            updatePromptManagerSummary(promptManagerPref);
            promptManagerPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), PromptManagerActivity.class);
                startActivity(intent);
                return true;
            });
        }

        // API URL
        EditTextPreference apiUrlPref = findPreference(ConfigManager.KEY_API_URL);
        if (apiUrlPref != null) {
            apiUrlPref.setText(ConfigManager.getApiUrl());
            apiUrlPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setApiUrl((String) newValue);
                apiUrlPref.setText((String) newValue);
                return true;
            });
        }

        // API Key
        EditTextPreference apiKeyPref = findPreference(ConfigManager.KEY_API_KEY);
        if (apiKeyPref != null) {
            apiKeyPref.setText(ConfigManager.getApiKey());
            apiKeyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setApiKey((String) newValue);
                apiKeyPref.setText((String) newValue);
                return true;
            });
        }

        // Dictionary Path
        EditTextPreference dictPathPref = findPreference(ConfigManager.KEY_DICT_PATH);
        if (dictPathPref != null) {
            dictPathPref.setText(ConfigManager.getDictPath());
            dictPathPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setDictPath((String) newValue);
                dictPathPref.setText((String) newValue);
                return true;
            });
        }
        
        // AI Model - 点击弹出模型选择对话框
        Preference aiModelPref = findPreference(ConfigManager.KEY_AI_MODEL);
        if (aiModelPref != null) {
            // 更新summary显示当前模型
            String currentModel = ConfigManager.getAiModel();
            aiModelPref.setSummary("当前: " + (currentModel.isEmpty() ? "未设置" : currentModel));
            
            aiModelPref.setOnPreferenceClickListener(preference -> {
                showModelSelectionDialog(aiModelPref);
                return true;
            });
        }
        
        // AI Provider
        androidx.preference.ListPreference aiProviderPref = findPreference(ConfigManager.KEY_AI_PROVIDER);
        EditTextPreference apiUrlPrefForProvider = findPreference(ConfigManager.KEY_API_URL);
        if (aiProviderPref != null) {
            String currentProvider = ConfigManager.getAiProvider();
            aiProviderPref.setValue(currentProvider);
            // 初始化summary显示
            updateProviderSummary(aiProviderPref, currentProvider);
            
            aiProviderPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String provider = (String) newValue;
                ConfigManager.setAiProvider(provider);
                
                // 自动填充API URL
                String defaultUrl = ConfigManager.getDefaultApiUrl(provider);
                if (!defaultUrl.isEmpty()) {
                    ConfigManager.setApiUrl(defaultUrl);
                    // 更新API URL EditTextPreference的显示
                    if (apiUrlPrefForProvider != null) {
                        apiUrlPrefForProvider.setText(defaultUrl);
                    }
                }
                
                // 更新Provider的summary显示
                updateProviderSummary(aiProviderPref, provider);
                return true;
            });
        }
        
        // AI Temperature
        EditTextPreference aiTempPref = findPreference(ConfigManager.KEY_AI_TEMPERATURE);
        if (aiTempPref != null) {
            aiTempPref.setText(String.valueOf(ConfigManager.getAiTemperature()));
            aiTempPref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    float temp = Float.parseFloat((String) newValue);
                    if (temp >= 0 && temp <= 2.0) {
                        ConfigManager.setAiTemperature(temp);
                        aiTempPref.setText((String) newValue);
                        return true;
                    }
                } catch (Exception e) {}
                return false;
            });
        }
        
        // AI Max Tokens
        EditTextPreference aiMaxTokensPref = findPreference(ConfigManager.KEY_AI_MAX_TOKENS);
        if (aiMaxTokensPref != null) {
            aiMaxTokensPref.setText(String.valueOf(ConfigManager.getAiMaxTokens()));
            aiMaxTokensPref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int tokens = Integer.parseInt((String) newValue);
                    if (tokens > 0) {
                        ConfigManager.setAiMaxTokens(tokens);
                        aiMaxTokensPref.setText((String) newValue);
                        return true;
                    }
                } catch (Exception e) {}
                return false;
            });
        }
        
        // AI Reasoning Effort (思考模式)
        androidx.preference.ListPreference reasoningEffortPref = findPreference(ConfigManager.KEY_AI_REASONING_EFFORT);
        if (reasoningEffortPref != null) {
            String currentEffort = ConfigManager.getAiReasoningEffort();
            reasoningEffortPref.setValue(currentEffort);
            updateReasoningEffortSummary(reasoningEffortPref, currentEffort);
            reasoningEffortPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String effort = (String) newValue;
                ConfigManager.setAiReasoningEffort(effort);
                updateReasoningEffortSummary(reasoningEffortPref, effort);
                return true;
            });
        }
        
        // AI QPS
        EditTextPreference aiQpsPref = findPreference(ConfigManager.KEY_AI_QPS);
        if (aiQpsPref != null) {
            aiQpsPref.setText(String.valueOf(ConfigManager.getAiQps()));
            aiQpsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    float qps = Float.parseFloat((String) newValue);
                    if (qps > 0.1) {
                        ConfigManager.setAiQps(qps);
                        aiQpsPref.setText((String) newValue);
                        return true;
                    }
                } catch (Exception e) {}
                return false;
            });
        }
        
        // AI Timeout (请求超时时间)
        EditTextPreference aiTimeoutPref = findPreference(ConfigManager.KEY_AI_TIMEOUT);
        if (aiTimeoutPref != null) {
            aiTimeoutPref.setText(String.valueOf(ConfigManager.getAiTimeout()));
            aiTimeoutPref.setSummary("当前: " + ConfigManager.getAiTimeout() + " 秒 (读取超时: " + (ConfigManager.getAiTimeout() * 2) + " 秒)");
            aiTimeoutPref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int timeout = Integer.parseInt((String) newValue);
                    if (timeout >= 1 && timeout <= 600) {
                        ConfigManager.setAiTimeout(timeout);
                        aiTimeoutPref.setText((String) newValue);
                        aiTimeoutPref.setSummary("当前: " + timeout + " 秒 (读取超时: " + (timeout * 2) + " 秒)");
                        // 重置AI客户端以使用新的超时配置
                        top.galqq.utils.HttpAiClient.resetClient();
                        return true;
                    } else {
                        android.widget.Toast.makeText(requireContext(), "超时时间范围: 1-600秒", android.widget.Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    android.widget.Toast.makeText(requireContext(), "请输入有效的超时时间", android.widget.Toast.LENGTH_SHORT).show();
                }
                return false;
            });
        }
        
        // Context Enabled (启用对话上下文)
        Preference contextEnabledSwitch = findPreference(ConfigManager.KEY_CONTEXT_ENABLED);
        if (contextEnabledSwitch != null) {
            if (contextEnabledSwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) contextEnabledSwitch).setChecked(ConfigManager.isContextEnabled());
            }
            contextEnabledSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setContextEnabled((Boolean) newValue);
                return true;
            });
        }
        
        // Context Message Count (上下文消息数)
        EditTextPreference contextCountPref = findPreference(ConfigManager.KEY_CONTEXT_MESSAGE_COUNT);
        if (contextCountPref != null) {
            contextCountPref.setText(String.valueOf(ConfigManager.getContextMessageCount()));
            contextCountPref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int count = Integer.parseInt((String) newValue);
                    if (count >= 1 && count <= 30) {
                        ConfigManager.setContextMessageCount(count);
                        contextCountPref.setText((String) newValue);
                        return true;
                    }
                } catch (Exception e) {}
                return false;
            });
        }
        
        // History Threshold (历史消息阈值)
        EditTextPreference historyThresholdPref = findPreference(ConfigManager.KEY_HISTORY_THRESHOLD);
        if (historyThresholdPref != null) {
            historyThresholdPref.setText(String.valueOf(ConfigManager.getHistoryThreshold()));
            historyThresholdPref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int seconds = Integer.parseInt((String) newValue);
                    if (seconds > 0) {
                        ConfigManager.setHistoryThreshold(seconds);
                        historyThresholdPref.setText((String) newValue);
                        return true;
                    }
                } catch (Exception e) {}
                return false;
            });
        }
        
        // Test API Button
        Preference testApiPref = findPreference("gal_test_api");
        if (testApiPref != null) {
            testApiPref.setOnPreferenceClickListener(preference -> {
                android.widget.Toast.makeText(requireContext(), "正在测试API连接...", android.widget.Toast.LENGTH_SHORT).show();
                top.galqq.utils.HttpAiClient.testApiConnection(requireContext(), new top.galqq.utils.HttpAiClient.AiCallback() {
                    @Override
                    public void onSuccess(java.util.List<String> options) {
                        android.app.Activity activity = getActivity();
                        if (activity != null && isAdded()) {
                            activity.runOnUiThread(() -> {
                                android.widget.Toast.makeText(activity, "✅ API连接成功！", android.widget.Toast.LENGTH_LONG).show();
                            });
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        android.app.Activity activity = getActivity();
                        if (activity != null && isAdded()) {
                            activity.runOnUiThread(() -> {
                                android.widget.Toast.makeText(activity, "❌ API连接失败: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
                return true;
            });
        }
        
        // Auto Show Options
        Preference autoShowOptionsSwitch = findPreference(ConfigManager.KEY_AUTO_SHOW_OPTIONS);
        if (autoShowOptionsSwitch != null) {
            if (autoShowOptionsSwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) autoShowOptionsSwitch).setChecked(ConfigManager.isAutoShowOptionsEnabled());
            }
            autoShowOptionsSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setAutoShowOptionsEnabled((Boolean) newValue);
                return true;
            });
        }
        
        // Affinity Display (好感度显示)
        Preference affinitySwitch = findPreference(ConfigManager.KEY_AFFINITY_ENABLED);
        if (affinitySwitch != null) {
            if (affinitySwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) affinitySwitch).setChecked(ConfigManager.isAffinityEnabled());
            }
            affinitySwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setAffinityEnabled((Boolean) newValue);
                return true;
            });
        }
        
        // Affinity Model (好感度计算模型)
        androidx.preference.ListPreference affinityModelPref = findPreference(ConfigManager.KEY_AFFINITY_MODEL);
        if (affinityModelPref != null) {
            affinityModelPref.setValue(String.valueOf(ConfigManager.getAffinityModel()));
            affinityModelPref.setSummary(ConfigManager.getAffinityModelName(ConfigManager.getAffinityModel()));
            affinityModelPref.setOnPreferenceChangeListener((preference, newValue) -> {
                int model = Integer.parseInt((String) newValue);
                ConfigManager.setAffinityModel(model);
                affinityModelPref.setSummary(ConfigManager.getAffinityModelName(model));
                
                // 【关键】清空好感度显示缓存，以便使用新模型重新计算
                try {
                    top.galqq.hook.MessageInterceptor.clearAffinityDisplayCache();
                    android.widget.Toast.makeText(requireContext(), "计算模型已切换，返回聊天界面后生效", android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    // 忽略错误（可能在非 Xposed 环境下运行）
                }
                return true;
            });
        }
        
        // Test Affinity Data (测试好感度数据获取)
        Preference testAffinityPref = findPreference("gal_test_affinity");
        if (testAffinityPref != null) {
            testAffinityPref.setOnPreferenceClickListener(preference -> {
                android.widget.Toast.makeText(requireContext(), "正在测试好感度数据获取...", android.widget.Toast.LENGTH_SHORT).show();
                
                // 获取Cookie状态信息
                final String cookieStatus = top.galqq.utils.CookieHelper.getCookieStatusDescription(requireContext());
                
                // 创建 CloseRankClient 并测试
                top.galqq.utils.CloseRankClient client = new top.galqq.utils.CloseRankClient();
                
                // 测试获取"谁在意我"数据
                client.fetchWhoCaresMe(requireContext(), new top.galqq.utils.CloseRankClient.RankCallback() {
                    @Override
                    public void onSuccess(java.util.Map<String, Integer> uinToScore) {
                        android.app.Activity activity = getActivity();
                        if (activity != null && isAdded()) {
                            activity.runOnUiThread(() -> {
                                StringBuilder sb = new StringBuilder();
                                sb.append("✅ 获取成功！\n\n");
                                
                                // 显示Cookie获取来源
                                sb.append("【Cookie状态】\n");
                                sb.append(cookieStatus);
                                sb.append("\n\n");
                                
                                sb.append("【数据结果】\n");
                                sb.append("谁在意我: ").append(uinToScore.size()).append(" 条数据\n\n");
                                
                                // 显示前5条数据
                                int count = 0;
                                for (java.util.Map.Entry<String, Integer> entry : uinToScore.entrySet()) {
                                    if (count >= 5) {
                                        sb.append("...(共").append(uinToScore.size()).append("条)");
                                        break;
                                    }
                                    sb.append("QQ: ").append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
                                    count++;
                                }
                                
                                new android.app.AlertDialog.Builder(activity)
                                    .setTitle("好感度数据测试结果")
                                    .setMessage(sb.toString())
                                    .setPositiveButton("确定", null)
                                    .show();
                            });
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        android.app.Activity activity = getActivity();
                        if (activity != null && isAdded()) {
                            activity.runOnUiThread(() -> {
                                StringBuilder sb = new StringBuilder();
                                sb.append("❌ 获取失败: ").append(e.getMessage()).append("\n\n");
                                
                                // 显示Cookie状态帮助诊断
                                sb.append("【Cookie状态】\n");
                                sb.append(cookieStatus);
                                sb.append("\n\n");
                                
                                sb.append("【排查建议】\n");
                                top.galqq.utils.CookieHelper.CookieSource source = top.galqq.utils.CookieHelper.getLastCookieSource();
                                if (source == top.galqq.utils.CookieHelper.CookieSource.FAILED) {
                                    sb.append("• Cookie获取失败，请确保已登录QQ\n");
                                    sb.append("• 尝试打开QQ空间触发Cookie刷新\n");
                                } else if (source == top.galqq.utils.CookieHelper.CookieSource.SQLITE) {
                                    sb.append("• 内存Hook未生效，使用SQLite降级\n");
                                    sb.append("• 请确认LSPosed模块已激活\n");
                                }
                                sb.append("• 检查网络连接是否正常\n");
                                
                                new android.app.AlertDialog.Builder(activity)
                                    .setTitle("好感度数据测试失败")
                                    .setMessage(sb.toString())
                                    .setPositiveButton("确定", null)
                                    .show();
                            });
                        }
                    }
                });
                return true;
            });
        }

        // Verbose Log
        SwitchPreference verboseLogPref = findPreference(ConfigManager.KEY_VERBOSE_LOG);
        if (verboseLogPref != null) {
            verboseLogPref.setChecked(ConfigManager.isVerboseLogEnabled());
            verboseLogPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setVerboseLogEnabled((Boolean) newValue);
                return true;
            });
        }
        
        // Debug Hook Log (调试Hook日志)
        SwitchPreference debugHookLogPref = findPreference(ConfigManager.KEY_DEBUG_HOOK_LOG);
        if (debugHookLogPref != null) {
            debugHookLogPref.setChecked(ConfigManager.isDebugHookLogEnabled());
            debugHookLogPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setDebugHookLogEnabled((Boolean) newValue);
                return true;
            });
        }
        
        // Filter Mode
        androidx.preference.ListPreference filterModePref = findPreference(ConfigManager.KEY_FILTER_MODE);
        if (filterModePref != null) {
            filterModePref.setValue(ConfigManager.getFilterMode());
            filterModePref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setFilterMode((String) newValue);
                return true;
            });
        }
        
        // Blacklist
        EditTextPreference blacklistPref = findPreference(ConfigManager.KEY_BLACKLIST);
        if (blacklistPref != null) {
            blacklistPref.setText(ConfigManager.getBlacklist());
            blacklistPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setBlacklist((String) newValue);
                return true;
            });
        }
        
        // Whitelist
        EditTextPreference whitelistPref = findPreference(ConfigManager.KEY_WHITELIST);
        if (whitelistPref != null) {
            whitelistPref.setText(ConfigManager.getWhitelist());
            whitelistPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setWhitelist((String) newValue);
                return true;
            });
        }
        
        // Group Filter Mode (群过滤模式)
        androidx.preference.ListPreference groupFilterModePref = findPreference(ConfigManager.KEY_GROUP_FILTER_MODE);
        if (groupFilterModePref != null) {
            groupFilterModePref.setValue(ConfigManager.getGroupFilterMode());
            groupFilterModePref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setGroupFilterMode((String) newValue);
                return true;
            });
        }
        
        // Group Blacklist (群黑名单)
        EditTextPreference groupBlacklistPref = findPreference(ConfigManager.KEY_GROUP_BLACKLIST);
        if (groupBlacklistPref != null) {
            groupBlacklistPref.setText(ConfigManager.getGroupBlacklist());
            groupBlacklistPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setGroupBlacklist((String) newValue);
                return true;
            });
        }
        
        // Group Whitelist (群白名单)
        EditTextPreference groupWhitelistPref = findPreference(ConfigManager.KEY_GROUP_WHITELIST);
        if (groupWhitelistPref != null) {
            groupWhitelistPref.setText(ConfigManager.getGroupWhitelist());
            groupWhitelistPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setGroupWhitelist((String) newValue);
                return true;
            });
        }
        
        // Disable Group Options (关闭群聊选项显示)
        Preference disableGroupOptionsSwitch = findPreference(ConfigManager.KEY_DISABLE_GROUP_OPTIONS);
        if (disableGroupOptionsSwitch != null) {
            if (disableGroupOptionsSwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) disableGroupOptionsSwitch).setChecked(ConfigManager.isDisableGroupOptions());
            }
            disableGroupOptionsSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setDisableGroupOptions((Boolean) newValue);
                return true;
            });
        }
        
        // AI Log Viewer
        Preference aiLogPref = findPreference("gal_ai_log");
        if (aiLogPref != null) {
            aiLogPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), AiLogViewerActivity.class);
                startActivity(intent);
                return true;
            });
        }
        
        // AI Monitor
        Preference aiMonitorPref = findPreference("gal_ai_monitor");
        if (aiMonitorPref != null) {
            aiMonitorPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), AiMonitorActivity.class);
                startActivity(intent);
                return true;
            });
        }
        
        // Open Source Address
        Preference sourcePref = findPreference("gal_source");
        if (sourcePref != null) {
            sourcePref.setOnPreferenceClickListener(preference -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/yiyihuohuo/GalQQ"));
                    startActivity(intent);
                } catch (Exception e) {
                    android.widget.Toast.makeText(requireContext(), "无法打开浏览器", android.widget.Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        // Join Group - 实现加入QQ群 859142525
        Preference joinGroupPref = findPreference("gal_join_group");
        if (joinGroupPref != null) {
            joinGroupPref.setOnPreferenceClickListener(preference -> {
                try {
                    // QQ群号
                    String groupNumber = " 859142525";
                    
                    // 构建QQ群跳转链接（使用mqqapi协议）
                    // 格式：mqqapi://card/show_pslcard?src_type=internal&version=1&uin=群号&card_type=group&source=qrcode
                    String url = "mqqapi://card/show_pslcard?src_type=internal&version=1&uin=" + 
                                groupNumber + "&card_type=group&source=qrcode";
                    
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                    intent.setData(android.net.Uri.parse(url));
                    intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    requireContext().startActivity(intent);
                    android.widget.Toast.makeText(requireContext(), "正在打开QQ群...", android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                } catch (Exception e) {
                    android.widget.Toast.makeText(requireContext(), "打开QQ群失败: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                    return false;
                }
            });
        }
        
        // ========== 代理设置 ==========
        
        // Proxy Enabled (启用代理)
        Preference proxyEnabledSwitch = findPreference(ConfigManager.KEY_PROXY_ENABLED);
        if (proxyEnabledSwitch != null) {
            if (proxyEnabledSwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) proxyEnabledSwitch).setChecked(ConfigManager.isProxyEnabled());
            }
            proxyEnabledSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setProxyEnabled((Boolean) newValue);
                // 重置代理客户端
                top.galqq.utils.HttpAiClient.resetProxyClient();
                return true;
            });
        }
        
        // Proxy Type (代理类型)
        androidx.preference.ListPreference proxyTypePref = findPreference(ConfigManager.KEY_PROXY_TYPE);
        if (proxyTypePref != null) {
            proxyTypePref.setValue(ConfigManager.getProxyType());
            proxyTypePref.setSummary("当前: " + ConfigManager.getProxyType());
            proxyTypePref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setProxyType((String) newValue);
                proxyTypePref.setSummary("当前: " + newValue);
                // 重置代理客户端
                top.galqq.utils.HttpAiClient.resetProxyClient();
                return true;
            });
        }
        
        // Proxy Host (代理地址)
        EditTextPreference proxyHostPref = findPreference(ConfigManager.KEY_PROXY_HOST);
        if (proxyHostPref != null) {
            proxyHostPref.setText(ConfigManager.getProxyHost());
            String host = ConfigManager.getProxyHost();
            proxyHostPref.setSummary(host.isEmpty() ? "代理服务器IP或域名（如 127.0.0.1）" : "当前: " + host);
            proxyHostPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String newHost = (String) newValue;
                ConfigManager.setProxyHost(newHost);
                proxyHostPref.setText(newHost);
                proxyHostPref.setSummary(newHost.isEmpty() ? "代理服务器IP或域名（如 127.0.0.1）" : "当前: " + newHost);
                // 重置代理客户端
                top.galqq.utils.HttpAiClient.resetProxyClient();
                return true;
            });
        }
        
        // Proxy Port (代理端口)
        EditTextPreference proxyPortPref = findPreference(ConfigManager.KEY_PROXY_PORT);
        if (proxyPortPref != null) {
            proxyPortPref.setText(String.valueOf(ConfigManager.getProxyPort()));
            proxyPortPref.setSummary("当前: " + ConfigManager.getProxyPort());
            proxyPortPref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int port = Integer.parseInt((String) newValue);
                    if (port > 0 && port <= 65535) {
                        ConfigManager.setProxyPort(port);
                        proxyPortPref.setText((String) newValue);
                        proxyPortPref.setSummary("当前: " + port);
                        // 重置代理客户端
                        top.galqq.utils.HttpAiClient.resetProxyClient();
                        return true;
                    } else {
                        android.widget.Toast.makeText(requireContext(), "端口范围: 1-65535", android.widget.Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    android.widget.Toast.makeText(requireContext(), "请输入有效的端口号", android.widget.Toast.LENGTH_SHORT).show();
                }
                return false;
            });
        }
        
        // Proxy Auth Enabled (启用代理认证)
        Preference proxyAuthSwitch = findPreference(ConfigManager.KEY_PROXY_AUTH_ENABLED);
        if (proxyAuthSwitch != null) {
            if (proxyAuthSwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) proxyAuthSwitch).setChecked(ConfigManager.isProxyAuthEnabled());
            }
            proxyAuthSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setProxyAuthEnabled((Boolean) newValue);
                // 重置代理客户端
                top.galqq.utils.HttpAiClient.resetProxyClient();
                return true;
            });
        }
        
        // Proxy Username (代理用户名)
        EditTextPreference proxyUsernamePref = findPreference(ConfigManager.KEY_PROXY_USERNAME);
        if (proxyUsernamePref != null) {
            proxyUsernamePref.setText(ConfigManager.getProxyUsername());
            String username = ConfigManager.getProxyUsername();
            proxyUsernamePref.setSummary(username.isEmpty() ? "代理认证用户名" : "当前: " + username);
            proxyUsernamePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String newUsername = (String) newValue;
                ConfigManager.setProxyUsername(newUsername);
                proxyUsernamePref.setText(newUsername);
                proxyUsernamePref.setSummary(newUsername.isEmpty() ? "代理认证用户名" : "当前: " + newUsername);
                // 重置代理客户端
                top.galqq.utils.HttpAiClient.resetProxyClient();
                return true;
            });
        }
        
        // Proxy Password (代理密码)
        EditTextPreference proxyPasswordPref = findPreference(ConfigManager.KEY_PROXY_PASSWORD);
        if (proxyPasswordPref != null) {
            proxyPasswordPref.setText(ConfigManager.getProxyPassword());
            String password = ConfigManager.getProxyPassword();
            proxyPasswordPref.setSummary(password.isEmpty() ? "代理认证密码" : "已设置 (*****)");
            proxyPasswordPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String newPassword = (String) newValue;
                ConfigManager.setProxyPassword(newPassword);
                proxyPasswordPref.setText(newPassword);
                proxyPasswordPref.setSummary(newPassword.isEmpty() ? "代理认证密码" : "已设置 (*****)");
                // 重置代理客户端
                top.galqq.utils.HttpAiClient.resetProxyClient();
                return true;
            });
        }
        
        // Test Proxy Button (测试代理连接)
        Preference testProxyPref = findPreference("gal_test_proxy");
        if (testProxyPref != null) {
            testProxyPref.setOnPreferenceClickListener(preference -> {
                // 检查代理配置
                if (!ConfigManager.isProxyEnabled()) {
                    android.widget.Toast.makeText(requireContext(), "请先启用代理", android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                }
                
                String host = ConfigManager.getProxyHost();
                
                if (host == null || host.trim().isEmpty()) {
                    android.widget.Toast.makeText(requireContext(), "请填写代理地址", android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                }
                
                android.widget.Toast.makeText(requireContext(), "正在测试代理连接...", android.widget.Toast.LENGTH_SHORT).show();
                
                // 使用专门的代理测试方法（不依赖AI API配置）
                top.galqq.utils.HttpAiClient.testProxyConnection(requireContext(), (success, message) -> {
                    android.app.Activity activity = getActivity();
                    if (activity != null && isAdded()) {
                        activity.runOnUiThread(() -> {
                            if (success) {
                                new android.app.AlertDialog.Builder(activity)
                                    .setTitle("✅ 代理测试成功")
                                    .setMessage(message)
                                    .setPositiveButton("确定", null)
                                    .show();
                            } else {
                                new android.app.AlertDialog.Builder(activity)
                                    .setTitle("❌ 代理测试失败")
                                    .setMessage(message + "\n\n常见问题排查：\n" +
                                        "1. 确认代理软件（如Clash）正在运行\n" +
                                        "2. 检查代理端口是否正确（Clash默认7890）\n" +
                                        "3. 确认代理类型（HTTP/SOCKS）是否匹配\n" +
                                        "4. 如果使用127.0.0.1，确保代理允许局域网连接")
                                    .setPositiveButton("确定", null)
                                    .show();
                            }
                        });
                    }
                });
                return true;
            });
        }
        
        // ========== 图片识别设置 ==========
        
        // Image Recognition Enabled (启用图片识别)
        Preference imageRecognitionSwitch = findPreference(ConfigManager.KEY_IMAGE_RECOGNITION_ENABLED);
        if (imageRecognitionSwitch != null) {
            if (imageRecognitionSwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) imageRecognitionSwitch).setChecked(ConfigManager.isImageRecognitionEnabled());
            }
            imageRecognitionSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setImageRecognitionEnabled((Boolean) newValue);
                return true;
            });
        }
        
        // Emoji Recognition Enabled (启用表情包识别)
        Preference emojiRecognitionSwitch = findPreference(ConfigManager.KEY_EMOJI_RECOGNITION_ENABLED);
        if (emojiRecognitionSwitch != null) {
            if (emojiRecognitionSwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) emojiRecognitionSwitch).setChecked(ConfigManager.isEmojiRecognitionEnabled());
            }
            emojiRecognitionSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setEmojiRecognitionEnabled((Boolean) newValue);
                return true;
            });
        }
        
        // Vision AI Enabled (启用外挂AI)
        Preference visionAiSwitch = findPreference(ConfigManager.KEY_VISION_AI_ENABLED);
        if (visionAiSwitch != null) {
            if (visionAiSwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) visionAiSwitch).setChecked(ConfigManager.isVisionAiEnabled());
            }
            visionAiSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setVisionAiEnabled((Boolean) newValue);
                return true;
            });
        }
        
        // Vision API URL (外挂AI API URL) - 需要先获取引用，供Provider切换时使用
        EditTextPreference visionApiUrlPrefForProvider = findPreference(ConfigManager.KEY_VISION_API_URL);
        
        // Vision AI Provider (外挂AI服务商)
        androidx.preference.ListPreference visionProviderPref = findPreference(ConfigManager.KEY_VISION_AI_PROVIDER);
        if (visionProviderPref != null) {
            String currentVisionProvider = ConfigManager.getVisionAiProvider();
            visionProviderPref.setValue(currentVisionProvider);
            updateVisionProviderSummary(visionProviderPref, currentVisionProvider);
            
            visionProviderPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String provider = (String) newValue;
                ConfigManager.setVisionAiProvider(provider);
                
                // 自动填充API URL
                String defaultUrl = ConfigManager.getDefaultVisionApiUrl(provider);
                if (!defaultUrl.isEmpty()) {
                    ConfigManager.setVisionApiUrl(defaultUrl);
                    // 更新API URL EditTextPreference的显示
                    if (visionApiUrlPrefForProvider != null) {
                        visionApiUrlPrefForProvider.setText(defaultUrl);
                        visionApiUrlPrefForProvider.setSummary(defaultUrl);
                    }
                }
                
                // 更新Provider的summary显示
                updateVisionProviderSummary(visionProviderPref, provider);
                return true;
            });
        }
        
        // Vision API URL (外挂AI API URL)
        EditTextPreference visionApiUrlPref = findPreference(ConfigManager.KEY_VISION_API_URL);
        if (visionApiUrlPref != null) {
            visionApiUrlPref.setText(ConfigManager.getVisionApiUrl());
            String url = ConfigManager.getVisionApiUrl();
            visionApiUrlPref.setSummary(url.isEmpty() ? "图片识别API地址" : url);
            visionApiUrlPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String newUrl = (String) newValue;
                ConfigManager.setVisionApiUrl(newUrl);
                visionApiUrlPref.setText(newUrl);
                visionApiUrlPref.setSummary(newUrl.isEmpty() ? "图片识别API地址" : newUrl);
                return true;
            });
        }
        
        // Vision API Key (外挂AI API Key)
        EditTextPreference visionApiKeyPref = findPreference(ConfigManager.KEY_VISION_API_KEY);
        if (visionApiKeyPref != null) {
            visionApiKeyPref.setText(ConfigManager.getVisionApiKey());
            String key = ConfigManager.getVisionApiKey();
            visionApiKeyPref.setSummary(key.isEmpty() ? "图片识别API密钥" : "已设置 (*****)");
            visionApiKeyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String newKey = (String) newValue;
                ConfigManager.setVisionApiKey(newKey);
                visionApiKeyPref.setText(newKey);
                visionApiKeyPref.setSummary(newKey.isEmpty() ? "图片识别API密钥" : "已设置 (*****)");
                return true;
            });
        }
        
        // Vision AI Model (外挂AI模型)
        EditTextPreference visionModelPref = findPreference(ConfigManager.KEY_VISION_AI_MODEL);
        if (visionModelPref != null) {
            visionModelPref.setText(ConfigManager.getVisionAiModel());
            visionModelPref.setSummary("当前: " + ConfigManager.getVisionAiModel());
            visionModelPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String model = (String) newValue;
                ConfigManager.setVisionAiModel(model);
                visionModelPref.setText(model);
                visionModelPref.setSummary("当前: " + model);
                return true;
            });
        }
        
        // Vision Use Proxy (外挂AI使用代理)
        Preference visionUseProxySwitch = findPreference(ConfigManager.KEY_VISION_USE_PROXY);
        if (visionUseProxySwitch != null) {
            if (visionUseProxySwitch instanceof androidx.preference.TwoStatePreference) {
                ((androidx.preference.TwoStatePreference) visionUseProxySwitch).setChecked(ConfigManager.isVisionUseProxy());
            }
            visionUseProxySwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setVisionUseProxy((Boolean) newValue);
                return true;
            });
        }
        
        // Test Vision AI Button (测试外挂AI)
        Preference testVisionAiPref = findPreference("gal_test_vision_ai");
        if (testVisionAiPref != null) {
            testVisionAiPref.setOnPreferenceClickListener(preference -> {
                // 检查配置
                if (!ConfigManager.isVisionAiEnabled()) {
                    android.widget.Toast.makeText(requireContext(), "请先启用外挂AI", android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                }
                
                String apiUrl = ConfigManager.getVisionApiUrl();
                if (apiUrl == null || apiUrl.trim().isEmpty()) {
                    android.widget.Toast.makeText(requireContext(), "请填写外挂AI API URL", android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                }
                
                android.widget.Toast.makeText(requireContext(), "正在测试外挂AI连接...", android.widget.Toast.LENGTH_SHORT).show();
                
                top.galqq.utils.VisionAiClient.testConnection(requireContext(), (success, message) -> {
                    android.app.Activity activity = getActivity();
                    if (activity != null && isAdded()) {
                        activity.runOnUiThread(() -> {
                            new android.app.AlertDialog.Builder(activity)
                                .setTitle(success ? "✅ 外挂AI测试成功" : "❌ 外挂AI测试失败")
                                .setMessage(message)
                                .setPositiveButton("确定", null)
                                .show();
                        });
                    }
                });
                return true;
            });
        }
        
        // Image Max Size (图片大小限制)
        EditTextPreference imageMaxSizePref = findPreference(ConfigManager.KEY_IMAGE_MAX_SIZE);
        if (imageMaxSizePref != null) {
            imageMaxSizePref.setText(String.valueOf(ConfigManager.getImageMaxSize()));
            imageMaxSizePref.setSummary("当前: " + ConfigManager.getImageMaxSize() + " KB");
            imageMaxSizePref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int size = Integer.parseInt((String) newValue);
                    if (size > 0) {
                        ConfigManager.setImageMaxSize(size);
                        imageMaxSizePref.setText((String) newValue);
                        imageMaxSizePref.setSummary("当前: " + size + " KB");
                        return true;
                    }
                } catch (Exception e) {}
                return false;
            });
        }
        
        // Image Description Max Length (描述长度限制)
        EditTextPreference descMaxLengthPref = findPreference(ConfigManager.KEY_IMAGE_DESCRIPTION_MAX_LENGTH);
        if (descMaxLengthPref != null) {
            descMaxLengthPref.setText(String.valueOf(ConfigManager.getImageDescriptionMaxLength()));
            descMaxLengthPref.setSummary("当前: " + ConfigManager.getImageDescriptionMaxLength() + " 字符");
            descMaxLengthPref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int length = Integer.parseInt((String) newValue);
                    if (length > 0) {
                        ConfigManager.setImageDescriptionMaxLength(length);
                        descMaxLengthPref.setText((String) newValue);
                        descMaxLengthPref.setSummary("当前: " + length + " 字符");
                        return true;
                    }
                } catch (Exception e) {}
                return false;
            });
        }
        
        // Vision Timeout (识别超时时间)
        EditTextPreference visionTimeoutPref = findPreference(ConfigManager.KEY_VISION_TIMEOUT);
        if (visionTimeoutPref != null) {
            visionTimeoutPref.setText(String.valueOf(ConfigManager.getVisionTimeout()));
            visionTimeoutPref.setSummary("当前: " + ConfigManager.getVisionTimeout() + " 秒");
            visionTimeoutPref.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int timeout = Integer.parseInt((String) newValue);
                    if (timeout > 0) {
                        ConfigManager.setVisionTimeout(timeout);
                        visionTimeoutPref.setText((String) newValue);
                        visionTimeoutPref.setSummary("当前: " + timeout + " 秒");
                        return true;
                    }
                } catch (Exception e) {}
                return false;
            });
        }
    }
    
    /**
     * 更新外挂AI服务商的summary显示
     */
    private void updateVisionProviderSummary(androidx.preference.ListPreference pref, String provider) {
        String displayName = ConfigManager.getVisionProviderDisplayName(provider);
        String apiUrl = ConfigManager.getDefaultVisionApiUrl(provider);
        if (apiUrl.isEmpty()) {
            apiUrl = ConfigManager.getVisionApiUrl();
        }
        if (apiUrl.isEmpty()) {
            pref.setSummary(displayName);
        } else {
            pref.setSummary(displayName + "\n" + apiUrl);
        }
    }
    
    // 模型选择对话框相关变量
    private android.app.AlertDialog modelDialog;
    private java.util.List<String> cachedModels = new java.util.ArrayList<>();
    
    /**
     * 显示模型选择对话框
     * 使用原生AlertDialog，自动适配深色模式
     */
    private void showModelSelectionDialog(Preference aiModelPref) {
        android.app.Activity activity = getActivity();
        if (activity == null || !isAdded()) return;
        
        // 显示加载提示
        android.app.ProgressDialog loadingDialog = new android.app.ProgressDialog(activity);
        loadingDialog.setMessage("正在获取模型列表...");
        loadingDialog.setCancelable(true);
        loadingDialog.show();
        
        // 获取模型列表（使用缓存）
        top.galqq.utils.ModelListFetcher.fetchModels(requireContext(), false, 
            new top.galqq.utils.ModelListFetcher.ModelListCallback() {
                @Override
                public void onSuccess(java.util.List<String> models) {
                    loadingDialog.dismiss();
                    if (activity == null || !isAdded()) return;
                    
                    cachedModels = models;
                    showModelListDialog(aiModelPref, models, false);
                }
                
                @Override
                public void onFailure(String error) {
                    loadingDialog.dismiss();
                    if (activity == null || !isAdded()) return;
                    
                    // 获取失败，显示错误并提供手动输入选项
                    new android.app.AlertDialog.Builder(activity)
                        .setTitle("获取模型列表失败")
                        .setMessage("错误: " + error + "\n\n请检查API配置或手动输入模型名称。")
                        .setPositiveButton("手动输入", (d, w) -> showCustomModelInputDialog(aiModelPref))
                        .setNegativeButton("取消", null)
                        .setNeutralButton("重试", (d, w) -> {
                            top.galqq.utils.ModelListFetcher.clearCache();
                            showModelSelectionDialog(aiModelPref);
                        })
                        .show();
                }
            });
    }
    
    /**
     * 显示模型列表对话框
     */
    private void showModelListDialog(Preference aiModelPref, java.util.List<String> models, boolean fromRefresh) {
        android.app.Activity activity = getActivity();
        if (activity == null || !isAdded()) return;
        
        String currentModel = ConfigManager.getAiModel();
        
        // 构建选项列表
        java.util.List<String> displayList = new java.util.ArrayList<>();
        if (models.isEmpty()) {
            displayList.add("未获取到模型，点击手动输入");
        } else {
            displayList.addAll(models);
        }
        
        String[] items = displayList.toArray(new String[0]);
        
        // 找到当前选中的位置
        int checkedItem = -1;
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(currentModel)) {
                checkedItem = i;
                break;
            }
        }
        
        modelDialog = new android.app.AlertDialog.Builder(activity)
            .setTitle("选择AI模型")
            .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                String selected = items[which];
                if (selected.equals("未获取到模型，点击手动输入")) {
                    dialog.dismiss();
                    showCustomModelInputDialog(aiModelPref);
                } else {
                    ConfigManager.setAiModel(selected);
                    aiModelPref.setSummary("当前: " + selected);
                    dialog.dismiss();
                    android.widget.Toast.makeText(activity, "已选择: " + selected, android.widget.Toast.LENGTH_SHORT).show();
                }
            })
            .setPositiveButton("手动输入", (d, w) -> showCustomModelInputDialog(aiModelPref))
            .setNegativeButton("取消", null)
            .setNeutralButton("刷新", (d, w) -> {
                // 强制刷新
                android.app.ProgressDialog refreshDialog = new android.app.ProgressDialog(activity);
                refreshDialog.setMessage("正在刷新模型列表...");
                refreshDialog.setCancelable(true);
                refreshDialog.show();
                
                top.galqq.utils.ModelListFetcher.fetchModels(requireContext(), true, 
                    new top.galqq.utils.ModelListFetcher.ModelListCallback() {
                        @Override
                        public void onSuccess(java.util.List<String> newModels) {
                            refreshDialog.dismiss();
                            if (activity == null || !isAdded()) return;
                            cachedModels = newModels;
                            showModelListDialog(aiModelPref, newModels, true);
                        }
                        
                        @Override
                        public void onFailure(String error) {
                            refreshDialog.dismiss();
                            if (activity == null || !isAdded()) return;
                            android.widget.Toast.makeText(activity, "刷新失败: " + error, android.widget.Toast.LENGTH_SHORT).show();
                            // 显示之前的列表
                            showModelListDialog(aiModelPref, cachedModels, false);
                        }
                    });
            })
            .create();
        
        modelDialog.show();
    }
    
    /**
     * 显示自定义模型输入对话框
     */
    private void showCustomModelInputDialog(Preference aiModelPref) {
        android.app.Activity activity = getActivity();
        if (activity == null || !isAdded()) return;
        
        android.widget.EditText input = new android.widget.EditText(activity);
        input.setHint("输入模型名称，如 gpt-4o");
        input.setText(ConfigManager.getAiModel());
        input.setSelectAllOnFocus(true);
        
        // 设置padding
        int padding = (int) (16 * activity.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(activity);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(padding, padding / 2, padding, 0);
        input.setLayoutParams(params);
        container.addView(input);
        
        new android.app.AlertDialog.Builder(activity)
            .setTitle("自定义模型")
            .setMessage("请输入模型名称：")
            .setView(container)
            .setPositiveButton("确定", (dialog, which) -> {
                String modelName = input.getText().toString().trim();
                if (!modelName.isEmpty()) {
                    ConfigManager.setAiModel(modelName);
                    aiModelPref.setSummary("当前: " + modelName);
                    android.widget.Toast.makeText(activity, "已设置模型: " + modelName, android.widget.Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
        
        // 自动弹出键盘
        input.requestFocus();
        input.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }
}
