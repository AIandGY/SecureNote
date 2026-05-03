package com.aigy.securenote.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 偏好设置工具类
 * 负责应用配置、主题色以及加密凭据（哈希值）的持久化存储
 */
public class PrefUtils {
    private static final String PREF_NAME = "secure_note_prefs";
    
    // 个性化设置 Key
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_LIST_MODE = "list_mode"; 
    private static final String KEY_SHOW_TIMESTAMP = "show_timestamp";
    private static final String KEY_SHOW_LOCATION = "show_location";
    private static final String KEY_SHOW_WEATHER = "show_weather";
    private static final String KEY_LIMIT_CONTENT = "limit_content"; 
    private static final String KEY_COLOR_REMINDER = "color_reminder";
    private static final String KEY_ENHANCED_REMINDER = "enhanced_reminder";
    private static final String KEY_AI_IDENTITY = "ai_identity";
    private static final String KEY_DEFAULT_IMPORTANCE = "default_importance";
    private static final String KEY_LAST_IMPORTANCE = "last_importance_index";
    private static final String KEY_URGENT_NOT_IMPORTANT_UPGRADE_TIME = "urgent_not_important_upgrade_time";
    private static final String KEY_TEXT_MAIN_COLOR = "text_main_color";
    private static final String KEY_ICON_MAIN_COLOR = "icon_main_color";
    private static final String KEY_HIDE_SCHEDULE_BORDER = "hide_schedule_border";
    
    // 局域网服务
    private static final String KEY_LAN_SERVICE_ENABLED = "lan_service_enabled";
    private static final String KEY_LAN_ADDRESS_PREFIX = "lan_address_prefix";
    private static final String KEY_LAN_ADDRESS_SUFFIX = "lan_address_suffix";
    private static final String KEY_LAN_PASSWORD = "lan_password";
    private static final String KEY_LAN_MAX_RUN_TIME = "lan_max_run_time";
    private static final String KEY_TRUSTED_WIFI_LIST = "trusted_wifi_list";
    private static final String KEY_LAN_FORCE_ENABLE = "lan_force_enable";
    private static final String KEY_LAN_IP_WHITELIST = "lan_ip_whitelist";
    private static final String KEY_LAN_WHITELIST_ENABLED = "lan_whitelist_enabled";
    private static final String KEY_LAN_BLACKLIST = "lan_blacklist";

    // 数据安全 Key
    private static final String KEY_ENCRYPTION_ENABLED = "encryption_enabled";
    private static final String KEY_WRAPPED_DEK = "wrapped_dek"; 
    private static final String KEY_DEK_HASH = "dek_hash"; // DEK 的 SHA-256 哈希值
    
    // 引导蒙层状态 Key
    private static final String KEY_GUIDE_SHOWN = "guide_shown";
    private static final String KEY_SELECTION_GUIDE_SHOWN = "selection_guide_shown";
    private static final String KEY_FILTER_GUIDE_SHOWN = "filter_guide_shown";

    // 开发者模式与日志
    private static final String KEY_DEVELOPER_MODE = "developer_mode";
    private static final String KEY_LOGGING_ENABLED = "logging_enabled";

    // API Keys
    private static final String KEY_AI_API_KEY = "ai_api_key";
    private static final String KEY_AI_BASE_URL = "ai_base_url";
    private static final String KEY_WEATHER_API_KEY = "weather_api_key";
    private static final String KEY_ASR_APP_ID = "asr_app_id";
    private static final String KEY_ASR_API_KEY = "asr_api_key";
    private static final String KEY_ASR_SECRET_KEY = "asr_secret_key";

    private static SharedPreferences getSp(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- 局域网服务 ---
    public static boolean isLanServiceEnabled(Context context) { return getSp(context).getBoolean(KEY_LAN_SERVICE_ENABLED, false); }
    public static void setLanServiceEnabled(Context context, boolean enabled) { getSp(context).edit().putBoolean(KEY_LAN_SERVICE_ENABLED, enabled).apply(); }

    public static String getLanAddressPrefix(Context context) { return getSp(context).getString(KEY_LAN_ADDRESS_PREFIX, ""); }
    public static void setLanAddressPrefix(Context context, String prefix) { getSp(context).edit().putString(KEY_LAN_ADDRESS_PREFIX, prefix).apply(); }

    public static String getLanAddressSuffix(Context context) { return getSp(context).getString(KEY_LAN_ADDRESS_SUFFIX, ""); }
    public static void setLanAddressSuffix(Context context, String suffix) { getSp(context).edit().putString(KEY_LAN_ADDRESS_SUFFIX, suffix).apply(); }

    public static String getLanPassword(Context context) { return getSp(context).getString(KEY_LAN_PASSWORD, ""); }
    public static void setLanPassword(Context context, String password) { getSp(context).edit().putString(KEY_LAN_PASSWORD, password).apply(); }

    public static int getLanMaxRunTime(Context context) { return getSp(context).getInt(KEY_LAN_MAX_RUN_TIME, 1); }
    public static void setLanMaxRunTime(Context context, int hours) { getSp(context).edit().putInt(KEY_LAN_MAX_RUN_TIME, hours).apply(); }

    public static boolean isLanForceEnable(Context context) { return getSp(context).getBoolean(KEY_LAN_FORCE_ENABLE, false); }
    public static void setLanForceEnable(Context context, boolean force) { getSp(context).edit().putBoolean(KEY_LAN_FORCE_ENABLE, force).apply(); }

    public static boolean isLanWhitelistEnabled(Context context) { return getSp(context).getBoolean(KEY_LAN_WHITELIST_ENABLED, true); }
    public static void setLanWhitelistEnabled(Context context, boolean enabled) { getSp(context).edit().putBoolean(KEY_LAN_WHITELIST_ENABLED, enabled).apply(); }

    public static List<String> getLanIpWhitelist(Context context) {
        String json = getSp(context).getString(KEY_LAN_IP_WHITELIST, "");
        if (TextUtils.isEmpty(json)) return new ArrayList<>();
        try {
            return new Gson().fromJson(json, new TypeToken<List<String>>(){}.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void saveLanIpWhitelist(Context context, List<String> list) {
        String json = new Gson().toJson(list);
        getSp(context).edit().putString(KEY_LAN_IP_WHITELIST, json).apply();
    }

    // --- 黑名单相关 ---
    public static class BlacklistRule {
        public String id = UUID.randomUUID().toString();
        public String deviceName = "";
        public String deviceId = ""; // cookie中的device_id
        public String ip = "";
        public String os = "";
        public String browser = "";

        // 是否针对该项生效
        public boolean blockDeviceName = false;
        public boolean blockDeviceId = false;
        public boolean blockIp = false;
        public boolean blockOs = false;
        public boolean blockBrowser = false;

        public boolean isEnabled = true;
        public boolean isFullMatch = false; // 是否全匹配（所有勾选项都匹配才拦截）

        public BlacklistRule() {}
        
        public BlacklistRule(String deviceName, String deviceId, String ip, String os, String browser) {
            this.deviceName = deviceName;
            this.deviceId = deviceId;
            this.ip = ip;
            this.os = os;
            this.browser = browser;
        }
    }

    public static List<BlacklistRule> getLanBlacklist(Context context) {
        String json = getSp(context).getString(KEY_LAN_BLACKLIST, "");
        if (TextUtils.isEmpty(json)) return new ArrayList<>();
        try {
            return new Gson().fromJson(json, new TypeToken<List<BlacklistRule>>(){}.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void saveLanBlacklist(Context context, List<BlacklistRule> list) {
        String json = new Gson().toJson(list);
        getSp(context).edit().putString(KEY_LAN_BLACKLIST, json).apply();
    }

    public static List<TrustedWifi> getTrustedWifiList(Context context) {
        String json = getSp(context).getString(KEY_TRUSTED_WIFI_LIST, "");
        if (TextUtils.isEmpty(json)) return new ArrayList<>();
        try {
            return new Gson().fromJson(json, new TypeToken<List<TrustedWifi>>(){}.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void saveTrustedWifiList(Context context, List<TrustedWifi> list) {
        String json = new Gson().toJson(list);
        getSp(context).edit().putString(KEY_TRUSTED_WIFI_LIST, json).apply();
    }

    /**
     * 获取当前连接的 WiFi 信息
     */
    public static TrustedWifi getCurrentWifi(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            WifiInfo info = wifiManager.getConnectionInfo();
            if (info != null && !TextUtils.isEmpty(info.getBSSID()) && !info.getBSSID().equals("00:00:00:00:00:00")) {
                String ssid = info.getSSID();
                if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid = ssid.substring(1, ssid.length() - 1);
                }
                return new TrustedWifi(ssid, info.getBSSID());
            }
        }
        return null;
    }

    /**
     * 检查当前网络是否受信任
     */
    public static boolean isCurrentNetworkTrusted(Context context) {
        if (isLanForceEnable(context)) return true;
        
        TrustedWifi current = getCurrentWifi(context);
        if (current == null) return false;

        List<TrustedWifi> trustedList = getTrustedWifiList(context);
        for (TrustedWifi wifi : trustedList) {
            if (wifi.bssid.equals(current.bssid)) {
                return true;
            }
        }
        return false;
    }

    public static class TrustedWifi {
        public String ssid;
        public String bssid;
        public TrustedWifi(String ssid, String bssid) {
            this.ssid = ssid;
            this.bssid = bssid;
        }
    }

    // --- 数据安全 ---
    public static boolean isEncryptionEnabled(Context context) { return getSp(context).getBoolean(KEY_ENCRYPTION_ENABLED, false); }
    public static void setEncryptionEnabled(Context context, boolean enabled) { getSp(context).edit().putBoolean(KEY_ENCRYPTION_ENABLED, enabled).apply(); }
    
    public static String getWrappedDek(Context context) { return getSp(context).getString(KEY_WRAPPED_DEK, ""); }
    public static void saveWrappedDek(Context context, String wrapped) { getSp(context).edit().putString(KEY_WRAPPED_DEK, wrapped).apply(); }

    public static String getDekHash(Context context) { return getSp(context).getString(KEY_DEK_HASH, ""); }
    public static void saveDekHash(Context context, String hash) { getSp(context).edit().putString(KEY_DEK_HASH, hash).apply(); }

    // --- 开发者模式 ---
    public static boolean isDeveloperMode(Context context) { return getSp(context).getBoolean(KEY_DEVELOPER_MODE, false); }
    public static void setDeveloperMode(Context context, boolean enabled) { getSp(context).edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply(); }

    // --- 日志开关 ---
    public static boolean isLoggingEnabled(Context context) { return getSp(context).getBoolean(KEY_LOGGING_ENABLED, false); }
    public static void setLoggingEnabled(Context context, boolean enabled) { getSp(context).edit().putBoolean(KEY_LOGGING_ENABLED, enabled).apply(); }

    // --- 引导页状态 ---
    public static boolean isGuideShown(Context context) { return getSp(context).getBoolean(KEY_GUIDE_SHOWN, false); }
    public static void setGuideShown(Context context, boolean shown) { getSp(context).edit().putBoolean(KEY_GUIDE_SHOWN, shown).apply(); }

    public static boolean isSelectionGuideShown(Context context) { return getSp(context).getBoolean(KEY_SELECTION_GUIDE_SHOWN, false); }
    public static void setSelectionGuideShown(Context context, boolean shown) { getSp(context).edit().putBoolean(KEY_SELECTION_GUIDE_SHOWN, shown).apply(); }

    public static boolean isFilterGuideShown(Context context) { return getSp(context).getBoolean(KEY_FILTER_GUIDE_SHOWN, false); }
    public static void setFilterGuideShown(Context context, boolean shown) { getSp(context).edit().putBoolean(KEY_FILTER_GUIDE_SHOWN, shown).apply(); }

    // --- AI 身份设置 ---
    public static void setAiIdentity(Context context, String identity) { getSp(context).edit().putString(KEY_AI_IDENTITY, identity).apply(); }
    public static String getAiIdentity(Context context) { 
        String identity = getSp(context).getString(KEY_AI_IDENTITY, "");
        return TextUtils.isEmpty(identity) ? "高效的笔记助手" : identity;
    }

    public static int getLastImportance(Context context) {
        return getSp(context).getInt(KEY_LAST_IMPORTANCE, getDefaultImportance(context));
    }

    public static void saveLastImportance(Context context, int index) {
        getSp(context).edit().putInt(KEY_LAST_IMPORTANCE, index).apply();
    }

    // --- 个性化配置 ---
    public static void setNightMode(Context context, int mode) { getSp(context).edit().putInt(KEY_NIGHT_MODE, mode).apply(); }
    public static int getNightMode(Context context) { return getSp(context).getInt(KEY_NIGHT_MODE, 0); }

    public static void setListMode(Context context, int mode) { getSp(context).edit().putInt(KEY_LIST_MODE, mode).apply(); }
    public static int getListMode(Context context) { return getSp(context).getInt(KEY_LIST_MODE, 0); }

    public static void setShowTimestamp(Context context, boolean show) { getSp(context).edit().putBoolean(KEY_SHOW_TIMESTAMP, show).apply(); }
    public static boolean isShowTimestamp(Context context) { return getSp(context).getBoolean(KEY_SHOW_TIMESTAMP, true); }

    public static void setShowLocation(Context context, boolean show) { getSp(context).edit().putBoolean(KEY_SHOW_LOCATION, show).apply(); }
    public static boolean isShowLocation(Context context) { return getSp(context).getBoolean(KEY_SHOW_LOCATION, true); }

    public static void setShowWeather(Context context, boolean show) { getSp(context).edit().putBoolean(KEY_SHOW_WEATHER, show).apply(); }
    public static boolean isShowWeather(Context context) { return getSp(context).getBoolean(KEY_SHOW_WEATHER, true); }

    public static void setLimitContent(Context context, boolean limit) { getSp(context).edit().putBoolean(KEY_LIMIT_CONTENT, limit).apply(); }
    public static boolean isLimitContent(Context context) { return getSp(context).getBoolean(KEY_LIMIT_CONTENT, true); }

    public static void setColorReminder(Context context, boolean enabled) { getSp(context).edit().putBoolean(KEY_COLOR_REMINDER, enabled).apply(); }
    public static boolean isColorReminder(Context context) { return getSp(context).getBoolean(KEY_COLOR_REMINDER, true); }

    public static void setEnhancedReminder(Context context, boolean enabled) { getSp(context).edit().putBoolean(KEY_ENHANCED_REMINDER, enabled).apply(); }
    public static boolean isEnhancedReminder(Context context) { return getSp(context).getBoolean(KEY_ENHANCED_REMINDER, false); }

    public static void setDefaultImportance(Context context, int index) { getSp(context).edit().putInt(KEY_DEFAULT_IMPORTANCE, index).apply(); }
    public static int getDefaultImportance(Context context) { return getSp(context).getInt(KEY_DEFAULT_IMPORTANCE, 3); }

    public static void setUrgentUpgradeTime(Context context, int minutes) { getSp(context).edit().putInt(KEY_URGENT_NOT_IMPORTANT_UPGRADE_TIME, minutes).apply(); }
    public static int getUrgentUpgradeTime(Context context) { return getSp(context).getInt(KEY_URGENT_NOT_IMPORTANT_UPGRADE_TIME, 30); }

    public static void setHideScheduleBorder(Context context, boolean hide) { getSp(context).edit().putBoolean(KEY_HIDE_SCHEDULE_BORDER, hide).apply(); }
    public static boolean isHideScheduleBorder(Context context) { return getSp(context).getBoolean(KEY_HIDE_SCHEDULE_BORDER, false); }

    // 文字主色调
    public static void setTextMainColor(Context context, String colorHex) { getSp(context).edit().putString(KEY_TEXT_MAIN_COLOR, colorHex).apply(); }
    public static String getTextMainColorHex(Context context) { return getSp(context).getString(KEY_TEXT_MAIN_COLOR, ""); }
    public static int getTextMainColor(Context context, int defaultColor) {
        String hex = getTextMainColorHex(context);
        if (TextUtils.isEmpty(hex)) return defaultColor;
        try {
            if (!hex.startsWith("#")) hex = "#" + hex;
            return Color.parseColor(hex);
        } catch (Exception e) { return defaultColor; }
    }

    // 图片图标主色调
    public static void setIconMainColor(Context context, String colorHex) { getSp(context).edit().putString(KEY_ICON_MAIN_COLOR, colorHex).apply(); }
    public static String getIconMainColorHex(Context context) { return getSp(context).getString(KEY_ICON_MAIN_COLOR, ""); }
    public static int getIconMainColor(Context context, int defaultColor) {
        String hex = getIconMainColorHex(context);
        if (TextUtils.isEmpty(hex)) return defaultColor;
        try {
            if (!hex.startsWith("#")) hex = "#" + hex;
            return Color.parseColor(hex);
        } catch (Exception e) { return defaultColor; }
    }

    // --- API 保存与读取 ---
    public static void saveAiSettings(Context context, String key, String url) { getSp(context).edit().putString(KEY_AI_API_KEY, key).putString(KEY_AI_BASE_URL, url).apply(); }
    public static void saveAsrSettings(Context context, String appId, String apiKey, String secretKey) { getSp(context).edit().putString(KEY_ASR_APP_ID, appId).putString(KEY_ASR_API_KEY, apiKey).putString(KEY_ASR_SECRET_KEY, secretKey).apply(); }
    public static void saveWeatherApiKey(Context context, String key) { getSp(context).edit().putString(KEY_WEATHER_API_KEY, key).apply(); }
    public static String getAiApiKey(Context context) { return getSp(context).getString(KEY_AI_API_KEY, ""); }
    public static String getAiBaseUrl(Context context) { return getSp(context).getString(KEY_AI_BASE_URL, "https://api.deepseek.com/v1"); }
    public static String getAsrAppId(Context context) { return getSp(context).getString(KEY_ASR_APP_ID, ""); }
    public static String getAsrApiKey(Context context) { return getSp(context).getString(KEY_ASR_API_KEY, ""); }
    public static String getAsrSecretKey(Context context) { return getSp(context).getString(KEY_ASR_SECRET_KEY, ""); }
    public static String getWeatherApiKey(Context context) { return getSp(context).getString(KEY_WEATHER_API_KEY, ""); }
}
