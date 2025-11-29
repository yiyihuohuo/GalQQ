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

        // System Prompt
        EditTextPreference sysPromptPref = findPreference(ConfigManager.KEY_SYS_PROMPT);
        if (sysPromptPref != null) {
            sysPromptPref.setText(ConfigManager.getSysPrompt());
            sysPromptPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setSysPrompt((String) newValue);
                sysPromptPref.setText((String) newValue);
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
        
        // AI Model
        EditTextPreference aiModelPref = findPreference(ConfigManager.KEY_AI_MODEL);
        if (aiModelPref != null) {
            aiModelPref.setText(ConfigManager.getAiModel());
            aiModelPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setAiModel((String) newValue);
                aiModelPref.setText((String) newValue);
                return true;
            });
        }
        
        // AI Provider
        androidx.preference.ListPreference aiProviderPref = findPreference(ConfigManager.KEY_AI_PROVIDER);
        if (aiProviderPref != null) {
            aiProviderPref.setValue(ConfigManager.getAiProvider());
            aiProviderPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setAiProvider((String) newValue);
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
        
        // Verbose Log
        SwitchPreference verboseLogPref = findPreference(ConfigManager.KEY_VERBOSE_LOG);
        if (verboseLogPref != null) {
            verboseLogPref.setChecked(ConfigManager.isVerboseLogEnabled());
            verboseLogPref.setOnPreferenceChangeListener((preference, newValue) -> {
                ConfigManager.setVerboseLogEnabled((Boolean) newValue);
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

        // Join Group - 实现加入QQ群1026163188
        Preference joinGroupPref = findPreference("gal_join_group");
        if (joinGroupPref != null) {
            joinGroupPref.setOnPreferenceClickListener(preference -> {
                try {
                    // QQ群号
                    String groupNumber = "1026163188";
                    
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
    }
}
