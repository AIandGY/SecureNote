package com.aigy.securenote;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aigy.securenote.databinding.ActivityLanServiceBinding;
import com.aigy.securenote.databinding.DialogBlacklistManagerBinding;
import com.aigy.securenote.databinding.DialogConnectedClientsBinding;
import com.aigy.securenote.databinding.DialogEditBlacklistRuleBinding;
import com.aigy.securenote.databinding.DialogIpWhitelistBinding;
import com.aigy.securenote.databinding.DialogTrustedWifiBinding;
import com.aigy.securenote.databinding.ItemBlacklistRuleBinding;
import com.aigy.securenote.databinding.ItemConnectedClientBinding;
import com.aigy.securenote.databinding.ItemIpWhitelistBinding;
import com.aigy.securenote.databinding.ItemTrustedWifiListBinding;
import com.aigy.securenote.service.LanServerService;
import com.aigy.securenote.utils.PrefUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.List;

/**
 * 局域网服务配置页面
 */
public class LanServiceActivity extends AppCompatActivity {

    private ActivityLanServiceBinding binding;
    private ConnectivityManager.NetworkCallback networkCallback;
    private LanServerService lanService;
    private boolean isBound = false;

    private static final String DEFAULT_SUFFIX = "securenote";
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
    private String oldPassword;

    // 权限请求启动器
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "定位权限已授予，请再次尝试添加", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "需要定位权限才能获取 WiFi 的 Mac 地址", Toast.LENGTH_SHORT).show();
                }
            });

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            lanService = ((LanServerService.LocalBinder) service).getService();
            isBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityLanServiceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPadding = Math.max(systemBars.bottom, imeInsets.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
            return WindowInsetsCompat.CONSUMED;
        });

        initView();
        setupListeners();
        initNetworkListener();
        checkWifiRestriction();

        Intent intent = new Intent(this, LanServerService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void initNetworkListener() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(@NonNull Network network) { runOnUiThread(() -> { updateAddressPreview(); checkWifiRestriction(); }); }
                @Override public void onLost(@NonNull Network network) { runOnUiThread(() -> { updateAddressPreview(); checkWifiRestriction(); }); }
                @Override public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) { runOnUiThread(() -> updateAddressPreview()); }
            };
            connectivityManager.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        }
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    private void initView() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.switchLanService.setChecked(PrefUtils.isLanServiceEnabled(this));
        String password = PrefUtils.getLanPassword(this);
        if (TextUtils.isEmpty(password)) {
            password = generateRandomPassword(10);
            PrefUtils.setLanPassword(this, password);
        }
        oldPassword = password;
        binding.idEtLanPassword.setText(password);
        binding.etLanMaxTime.setText(String.valueOf(PrefUtils.getLanMaxRunTime(this)));
        binding.etLanPrefix.setText(PrefUtils.getLanAddressPrefix(this));
        binding.etLanSuffix.setText(PrefUtils.getLanAddressSuffix(this));
        updateAddressPreview();
    }

    private void updateAddressPreview() {
        boolean isServiceEnabled = binding.switchLanService.isChecked();
        if (!isServiceEnabled) {
            binding.tvLanAddressPreview.setText("未开启局域网服务");
            binding.tvLanIpAddress.setText("未开启局域网服务");
            return;
        }
        String ip = getLocalIpAddress();
        if (ip == null) {
            binding.tvLanAddressPreview.setText("未连接局域网");
            binding.tvLanIpAddress.setText("未连接局域网");
        } else {
            String prefix = binding.etLanPrefix.getText().toString().trim();
            String suffix = binding.etLanSuffix.getText().toString().trim();
            String dP = TextUtils.isEmpty(prefix) ? getSafeDeviceName() : prefix;
            String dS = TextUtils.isEmpty(suffix) ? DEFAULT_SUFFIX : suffix;
            binding.tvLanAddressPreview.setText(String.format("http://%s.%s.local:8080", dP, dS));
            binding.tvLanIpAddress.setText(String.format("http://%s:8080", ip));
        }
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface intf = en.nextElement();
                Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                while (enumIpAddr.hasMoreElements()) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        String ip = inetAddress.getHostAddress();
                        if (ip != null && (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))) return ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void checkWifiRestriction() {
        if (PrefUtils.isLanForceEnable(this)) {
            binding.switchLanService.setEnabled(true);
            return;
        }
        PrefUtils.TrustedWifi current = PrefUtils.getCurrentWifi(this);
        List<PrefUtils.TrustedWifi> trustedList = PrefUtils.getTrustedWifiList(this);
        boolean isTrusted = false;
        if (current != null) {
            for (PrefUtils.TrustedWifi wifi : trustedList) { 
                if (wifi.bssid != null && wifi.bssid.equalsIgnoreCase(current.bssid)) { 
                    isTrusted = true; 
                    break; 
                } 
            }
        }
        if (!isTrusted) {
            binding.switchLanService.setChecked(false);
            binding.switchLanService.setEnabled(false);
        } else {
            binding.switchLanService.setEnabled(true);
        }
    }

    private void setupListeners() {
        binding.itemTrustedWifi.setOnClickListener(v -> showTrustedWifiDialog());
        binding.itemIpWhitelist.setOnClickListener(v -> showIpWhitelistDialog());
        binding.itemLanSwitch.setOnClickListener(v -> binding.switchLanService.setChecked(!binding.switchLanService.isChecked()));
        binding.itemConnectedClients.setOnClickListener(v -> showConnectedClientsDialog());
        binding.itemLanBlacklist.setOnClickListener(v -> showBlacklistDialog());
        
        binding.tvLanAddressPreview.setOnClickListener(v -> {
            String text = binding.tvLanAddressPreview.getText().toString();
            copyToClipboard(text);
        });

        binding.tvLanIpAddress.setOnClickListener(v -> {
            String text = binding.tvLanIpAddress.getText().toString();
            copyToClipboard(text);
        });
        
        binding.switchLanService.setOnCheckedChangeListener((v, isChecked) -> {
            PrefUtils.setLanServiceEnabled(this, isChecked);
            updateAddressPreview();
            Intent intent = new Intent(this, LanServerService.class);
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent); else startService(intent);
            } else stopService(intent);
        });

        binding.btnSavePassword.setOnClickListener(v -> handlePasswordChange());
        binding.idEtLanPassword.setOnEditorActionListener((v, actionId, event) -> { binding.btnSavePassword.performClick(); return true; });

        TextWatcher addressWatcher = new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                PrefUtils.setLanAddressPrefix(LanServiceActivity.this, binding.etLanPrefix.getText().toString().trim());
                PrefUtils.setLanAddressSuffix(LanServiceActivity.this, binding.etLanSuffix.getText().toString().trim());
                updateAddressPreview();
            }
        };
        binding.etLanPrefix.addTextChangedListener(addressWatcher);
        binding.etLanSuffix.addTextChangedListener(addressWatcher);

        binding.etLanMaxTime.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                if (!TextUtils.isEmpty(input)) {
                    try {
                        int hours = Integer.parseInt(input);
                        if (hours >= 1 && hours <= 24) {
                            PrefUtils.setLanMaxRunTime(LanServiceActivity.this, hours);
                        } else if (hours > 24) {
                            binding.etLanMaxTime.setText("24");
                            binding.etLanMaxTime.setSelection(2);
                            PrefUtils.setLanMaxRunTime(LanServiceActivity.this, 24);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
    }

    private void handlePasswordChange() {
        String newPassword = binding.idEtLanPassword.getText().toString();
        if (TextUtils.isEmpty(newPassword) || newPassword.equals(oldPassword)) return;
        if (binding.switchLanService.isChecked()) {
            new MaterialAlertDialogBuilder(this).setTitle("确认修改密码").setMessage("修改访问密码将自动重启局域网服务，是否继续？").setCancelable(false)
                    .setPositiveButton("确定", (d, w) -> { PrefUtils.setLanPassword(this, newPassword); oldPassword = newPassword; restartLanService(); })
                    .setNegativeButton("取消", (d, w) -> binding.idEtLanPassword.setText(oldPassword)).show();
        } else {
            PrefUtils.setLanPassword(this, newPassword);
            oldPassword = newPassword;
            Toast.makeText(this, "密码已保存", Toast.LENGTH_SHORT).show();
        }
    }

    private void restartLanService() {
        binding.switchLanService.setChecked(false);
        binding.getRoot().postDelayed(() -> binding.switchLanService.setChecked(true), 500);
    }

    private void showConnectedClientsDialog() {
        if (!isBound || lanService == null) { Toast.makeText(this, "服务未运行", Toast.LENGTH_SHORT).show(); return; }
        List<LanServerService.ClientInfo> clients = lanService.getConnectedClients();
        DialogConnectedClientsBinding dialogBinding = DialogConnectedClientsBinding.inflate(getLayoutInflater());
        dialogBinding.rvConnectedClients.setLayoutManager(new LinearLayoutManager(this));
        
        ClientAdapter adapter = new ClientAdapter(clients, client -> {
            lanService.disconnectClient(client.deviceId);
            PrefUtils.BlacklistRule rule = new PrefUtils.BlacklistRule(client.deviceName, client.deviceId, client.ip, client.os, client.browser);
            rule.blockDeviceId = true;
            List<PrefUtils.BlacklistRule> list = PrefUtils.getLanBlacklist(this);
            list.add(rule);
            PrefUtils.saveLanBlacklist(this, list);
            Toast.makeText(this, "已踢出并拉黑该设备 ID", Toast.LENGTH_SHORT).show();
        });
        dialogBinding.rvConnectedClients.setAdapter(adapter);

        new MaterialAlertDialogBuilder(this)
                .setTitle("已连接设备")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showBlacklistDialog() {
        List<PrefUtils.BlacklistRule> rules = PrefUtils.getLanBlacklist(this);
        DialogBlacklistManagerBinding dialogBinding = DialogBlacklistManagerBinding.inflate(getLayoutInflater());
        dialogBinding.rvBlacklist.setLayoutManager(new LinearLayoutManager(this));
        
        BlacklistAdapter adapter = new BlacklistAdapter(rules, new BlacklistAdapter.OnRuleActionListener() {
            @Override public void onDelete(PrefUtils.BlacklistRule rule) { 
                rules.remove(rule); 
                PrefUtils.saveLanBlacklist(LanServiceActivity.this, rules); 
            }
            @Override public void onToggle(PrefUtils.BlacklistRule rule, boolean enabled) { 
                rule.isEnabled = enabled; 
                PrefUtils.saveLanBlacklist(LanServiceActivity.this, rules); 
            }
            @Override public void onEdit(PrefUtils.BlacklistRule rule) { 
                showEditRuleDialog(rule, () -> {
                    PrefUtils.saveLanBlacklist(LanServiceActivity.this, rules);
                    dialogBinding.rvBlacklist.getAdapter().notifyDataSetChanged();
                }); 
            }
        });
        dialogBinding.rvBlacklist.setAdapter(adapter);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("黑名单管理").setView(dialogBinding.getRoot()).create();
        dialogBinding.btnAddManualRule.setOnClickListener(v -> {
            PrefUtils.BlacklistRule newRule = new PrefUtils.BlacklistRule("手动规则", "", "", "", "");
            showEditRuleDialog(newRule, () -> {
                rules.add(newRule);
                PrefUtils.saveLanBlacklist(LanServiceActivity.this, rules);
                adapter.notifyDataSetChanged();
            });
        });
        dialogBinding.btnCloseBlacklist.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showEditRuleDialog(PrefUtils.BlacklistRule rule, Runnable onSave) {
        DialogEditBlacklistRuleBinding b = DialogEditBlacklistRuleBinding.inflate(getLayoutInflater());
        b.etRuleDeviceName.setText(rule.deviceName);
        b.etRuleIp.setText(rule.ip);
        b.etRuleOs.setText(rule.os);
        b.etRuleBrowser.setText(rule.browser);
        b.etRuleDeviceId.setText(rule.deviceId);

        b.cbBlockDeviceName.setChecked(rule.blockDeviceName);
        b.cbBlockIp.setChecked(rule.blockIp);
        b.cbBlockOs.setChecked(rule.blockOs);
        b.cbBlockBrowser.setChecked(rule.blockBrowser);
        b.cbBlockDeviceId.setChecked(rule.blockDeviceId);
        
        b.switchMatchMode.setChecked(!rule.isFullMatch);

        new MaterialAlertDialogBuilder(this).setTitle("编辑拦截准则").setView(b.getRoot())
                .setPositiveButton("保存", (d, w) -> {
                    rule.deviceName = b.etRuleDeviceName.getText().toString();
                    rule.ip = b.etRuleIp.getText().toString();
                    rule.os = b.etRuleOs.getText().toString();
                    rule.browser = b.etRuleBrowser.getText().toString();
                    rule.deviceId = b.etRuleDeviceId.getText().toString();
                    rule.blockDeviceName = b.cbBlockDeviceName.isChecked();
                    rule.blockIp = b.cbBlockIp.isChecked();
                    rule.blockOs = b.cbBlockOs.isChecked();
                    rule.blockBrowser = b.cbBlockBrowser.isChecked();
                    rule.blockDeviceId = b.cbBlockDeviceId.isChecked();
                    rule.isFullMatch = !b.switchMatchMode.isChecked();
                    
                    if (onSave != null) onSave.run();
                }).setNegativeButton("取消", null).show();
    }

    private static class ClientAdapter extends RecyclerView.Adapter<ClientAdapter.VH> {
        private final List<LanServerService.ClientInfo> list;
        private final OnKickListener listener;
        public ClientAdapter(List<LanServerService.ClientInfo> l, OnKickListener li) { list = l; listener = li; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new VH(ItemConnectedClientBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            LanServerService.ClientInfo c = list.get(pos);
            h.b.tvDeviceName.setText(c.deviceName);
            h.b.tvIp.setText("IP: " + c.ip);
            h.b.tvEnv.setText(String.format("环境: %s | %s ", c.os, c.browser));
            h.b.tvDuration.setText(String.format("连接时长: %s", c.getDuration()));
            h.b.btnKick.setOnClickListener(v -> listener.onKick(c));
        }
        @Override public int getItemCount() { return list.size(); }
        interface OnKickListener { void onKick(LanServerService.ClientInfo c); }
        static class VH extends RecyclerView.ViewHolder { ItemConnectedClientBinding b; VH(ItemConnectedClientBinding b) { super(b.getRoot()); this.b = b; } }
    }

    private static class BlacklistAdapter extends RecyclerView.Adapter<BlacklistAdapter.VH> {
        private final List<PrefUtils.BlacklistRule> list;
        private final OnRuleActionListener listener;
        public BlacklistAdapter(List<PrefUtils.BlacklistRule> l, OnRuleActionListener li) { list = l; listener = li; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new VH(ItemBlacklistRuleBinding.inflate(LayoutInflater.from(p.getContext()), p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            PrefUtils.BlacklistRule r = list.get(pos);
            h.b.tvRuleTitle.setText(r.deviceName);
            StringBuilder sb = new StringBuilder("拦截项: ");
            if (r.blockIp) sb.append("IP "); if (r.blockOs) sb.append("系统 "); if (r.blockBrowser) sb.append("浏览器 ");
            h.b.tvRuleDetails.setText(sb.toString());
            h.b.switchRuleEnabled.setChecked(r.isEnabled);
            h.b.switchRuleEnabled.setOnCheckedChangeListener((v, isChecked) -> listener.onToggle(r, isChecked));
            h.b.btnDeleteRule.setOnClickListener(v -> { listener.onDelete(r); notifyItemRemoved(h.getAdapterPosition()); });
            h.b.btnEditRule.setOnClickListener(v -> listener.onEdit(r));
        }
        @Override public int getItemCount() { return list.size(); }
        interface OnRuleActionListener { void onDelete(PrefUtils.BlacklistRule r); void onToggle(PrefUtils.BlacklistRule r, boolean e); void onEdit(PrefUtils.BlacklistRule r); }
        static class VH extends RecyclerView.ViewHolder { ItemBlacklistRuleBinding b; VH(ItemBlacklistRuleBinding b) { super(b.getRoot()); this.b = b; } }
    }

    private String getSafeDeviceName() { String m = Build.MODEL; return TextUtils.isEmpty(m) ? "android-device" : m.replaceAll("[^a-zA-Z0-9]", "-").toLowerCase(); }
    private void copyToClipboard(String t) {
        if (!TextUtils.isEmpty(t) && !t.contains("未")) {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("LAN Access Info", t));
            Toast.makeText(this, "内容已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }
    private String generateRandomPassword(int l) {
        SecureRandom r = new SecureRandom(); StringBuilder sb = new StringBuilder(l);
        for (int i = 0; i < l; i++) sb.append(PASSWORD_CHARS.charAt(r.nextInt(PASSWORD_CHARS.length())));
        return sb.toString();
    }
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    private void showTrustedWifiDialog() {
        List<PrefUtils.TrustedWifi> list = PrefUtils.getTrustedWifiList(this);
        DialogTrustedWifiBinding b = DialogTrustedWifiBinding.inflate(getLayoutInflater());
        b.rvTrustedWifi.setLayoutManager(new LinearLayoutManager(this));
        
        RecyclerView.Adapter adapter = new RecyclerView.Adapter() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) { 
                return new VH(ItemTrustedWifiListBinding.inflate(LayoutInflater.from(p.getContext()), p, false)); 
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
                ItemTrustedWifiListBinding itemB = ((VH)h).binding;
                PrefUtils.TrustedWifi wifi = list.get(pos);
                itemB.tvWifiSsid.setText(wifi.ssid);
                // 核心修复：在这里对 BSSID 文本框进行赋值，而不是使用默认值
                itemB.tvWifiBssid.setText("MAC: " + (TextUtils.isEmpty(wifi.bssid) ? "未知" : wifi.bssid));
                itemB.btnDeleteWifi.setOnClickListener(v -> { 
                    list.remove(pos); 
                    PrefUtils.saveTrustedWifiList(LanServiceActivity.this, list); 
                    notifyDataSetChanged(); 
                    checkWifiRestriction(); 
                });
            }
            @Override public int getItemCount() { return list.size(); }
            class VH extends RecyclerView.ViewHolder { ItemTrustedWifiListBinding binding; VH(ItemTrustedWifiListBinding b) { super(b.getRoot()); this.binding = b; } }
        };
        b.rvTrustedWifi.setAdapter(adapter);

        updateForceEnableText(b.tvToggleForceEnable);
        b.tvToggleForceEnable.setOnClickListener(v -> {
            boolean current = PrefUtils.isLanForceEnable(this);
            PrefUtils.setLanForceEnable(this, !current);
            updateForceEnableText(b.tvToggleForceEnable);
            checkWifiRestriction();
            Toast.makeText(this, !current ? "已开启强制访问模式" : "已关闭强制访问模式", Toast.LENGTH_SHORT).show();
        });

        b.btnAddWifi.setOnClickListener(v -> {
            // 1. 检查权限 (Android 10+ 必须拥有定位权限才能获取 SSID/BSSID)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
                return;
            }

            // 2. 检查系统定位开关
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (!lm.isLocationEnabled()) {
                    showLocationServiceDialog();
                    return;
                }
            } else {
                boolean isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
                boolean isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                if (!isGpsEnabled && !isNetworkEnabled) {
                    showLocationServiceDialog();
                    return;
                }
            }

            PrefUtils.TrustedWifi current = PrefUtils.getCurrentWifi(this);
            if (current == null) {
                Toast.makeText(this, "无法获取 WiFi 信息，请确保已连接并刷新", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 过滤全 0 的伪地址提示用户
            if ("02:00:00:00:00:00".equals(current.bssid)) {
                Toast.makeText(this, "系统返回了加密的伪 MAC 地址，请确保定位已开启", Toast.LENGTH_LONG).show();
            }

            for (PrefUtils.TrustedWifi tw : list) {
                if (tw.bssid != null && tw.bssid.equalsIgnoreCase(current.bssid)) {
                    Toast.makeText(this, "当前 WiFi 已在列表中", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            list.add(current);
            PrefUtils.saveTrustedWifiList(this, list);
            adapter.notifyDataSetChanged();
            checkWifiRestriction();
            Toast.makeText(this, "已添加: " + current.ssid, Toast.LENGTH_SHORT).show();
        });

        new MaterialAlertDialogBuilder(this).setTitle("受信任WiFi").setView(b.getRoot()).show();
    }

    private void showLocationServiceDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("需要开启定位服务")
                .setMessage("Android 系统要求开启定位服务才能识别 WiFi 信息。")
                .setPositiveButton("去开启", (d, w) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateForceEnableText(android.widget.TextView tv) {
        boolean force = PrefUtils.isLanForceEnable(this);
        tv.setText(force ? "当前：不受网络限制 (点击恢复)" : "当前：仅受信任WiFi可用 (点击解除)");
    }

    private void showIpWhitelistDialog() {
        List<String> list = PrefUtils.getLanIpWhitelist(this);
        DialogIpWhitelistBinding b = DialogIpWhitelistBinding.inflate(getLayoutInflater());
        b.switchWhitelistEnabled.setChecked(PrefUtils.isLanWhitelistEnabled(this));
        b.switchWhitelistEnabled.setOnCheckedChangeListener((v, c) -> PrefUtils.setLanWhitelistEnabled(this, c));
        b.rvIpWhitelist.setLayoutManager(new LinearLayoutManager(this));
        
        RecyclerView.Adapter adapter = new RecyclerView.Adapter() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) { return new RecyclerView.ViewHolder(ItemIpWhitelistBinding.inflate(LayoutInflater.from(p.getContext()), p, false).getRoot()){}; }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
                ItemIpWhitelistBinding itemB = ItemIpWhitelistBinding.bind(h.itemView);
                itemB.tvIpAddress.setText(list.get(pos));
                itemB.btnDeleteIp.setOnClickListener(v -> { list.remove(pos); PrefUtils.saveLanIpWhitelist(LanServiceActivity.this, list); notifyDataSetChanged(); });
            }
            @Override public int getItemCount() { return list.size(); }
        };
        b.rvIpWhitelist.setAdapter(adapter);

        b.btnAddIp.setOnClickListener(v -> {
            EditText et = new EditText(this);
            et.setHint("例如: 192.168.1.100");
            new MaterialAlertDialogBuilder(this).setTitle("添加白名单IP").setView(et)
                    .setPositiveButton("添加", (d, w) -> {
                        String ip = et.getText().toString().trim();
                        if (!TextUtils.isEmpty(ip)) {
                            list.add(ip);
                            PrefUtils.saveLanIpWhitelist(this, list);
                            adapter.notifyDataSetChanged();
                        }
                    }).setNegativeButton("取消", null).show();
        });

        new MaterialAlertDialogBuilder(this).setTitle("IP白名单").setView(b.getRoot()).show();
    }
}
