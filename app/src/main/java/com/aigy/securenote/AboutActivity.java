package com.aigy.securenote;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aigy.securenote.databinding.ActivityAboutBinding;
import com.aigy.securenote.utils.PrefUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 关于页面
 * 负责展示版本信息、开发者模式入口触发及核心权限完整性检查。
 */
public class AboutActivity extends AppCompatActivity {

    private ActivityAboutBinding binding;
    private final List<PermissionItem> permissionQueue = new ArrayList<>();
    private int currentPermissionIndex = 0;

    // 开发者模式点击逻辑相关
    private int clickCount = 0;
    private long lastClickTime = 0;
    private static final int REQUIRED_CLICKS = 7;
    private static final long CLICK_INTERVAL = 500;
    private Toast mToast; 

    // 权限模型内部类
    private static class PermissionItem {
        String permission;
        String name;
        String description;

        PermissionItem(String permission, String name, String description) {
            this.permission = permission;
            this.name = name;
            this.description = description;
        }
    }

    // 权限请求启动器
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                handlePermissionResult(isGranted);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 实时应用双色调
        applyCustomThemeColor();
    }

    /**
     * 应用文字主色调与图标主色调，保持全局主题统一
     */
    private void applyCustomThemeColor() {
        if (binding == null) return;
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int defaultColor = typedValue.data;

        int textColor = PrefUtils.getTextMainColor(this, defaultColor);
        int iconColor = PrefUtils.getIconMainColor(this, defaultColor);

        binding.btnGithub.setTextColor(textColor);
        binding.btnCheckPermissions.setIconTint(ColorStateList.valueOf(iconColor));
        binding.btnGithub.setIconTint(ColorStateList.valueOf(iconColor));
    }

    private void initUI() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // GitHub 项目跳转
        binding.btnGithub.setOnClickListener(v -> {
            String url = "https://github.com/AIandGY/SecureNote";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });

        // 启动权限完整性检查流程
        binding.btnCheckPermissions.setOnClickListener(v -> startPermissionCheckFlow());

        // 连续点击图标开启开发者模式
        binding.ivLogo.setOnClickListener(v -> handleLogoClick());
    }

    /**
     * 处理 Logo 点击，实现连续点击开启开发者模式
     */
    private void handleLogoClick() {
        long currentTime = SystemClock.elapsedRealtime();
        if (currentTime - lastClickTime < CLICK_INTERVAL) {
            clickCount++;
        } else {
            clickCount = 1;
        }
        lastClickTime = currentTime;

        if (PrefUtils.isDeveloperMode(this)) {
            showSnappyToast("开发者模式已开启");
            return;
        }

        if (clickCount >= REQUIRED_CLICKS) {
            PrefUtils.setDeveloperMode(this, true);
            showSnappyToast("你已开启开发者模式！");
            clickCount = 0;
        } else if (clickCount > 3) {
            showSnappyToast("再点击 " + (REQUIRED_CLICKS - clickCount) + " 次开启开发者模式");
        }
    }

    /**
     * 即时取消旧 Toast 并显示新 Toast，防止提示堆积
     */
    private void showSnappyToast(String message) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        mToast.show();
    }

    private void startPermissionCheckFlow() {
        permissionQueue.clear();
        currentPermissionIndex = 0;
        permissionQueue.add(new PermissionItem(Manifest.permission.ACCESS_FINE_LOCATION, "定位权限", "用于获取天气信息和记录笔记地点"));
        permissionQueue.add(new PermissionItem(Manifest.permission.RECORD_AUDIO, "录音权限", "用于语音笔记录入功能"));
        permissionQueue.add(new PermissionItem(Manifest.permission.WRITE_CALENDAR, "日历权限", "用于将笔记提醒同步至系统日历"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionQueue.add(new PermissionItem(Manifest.permission.POST_NOTIFICATIONS, "通知权限", "用于显示闹钟和笔记到时提醒"));
        }
        checkNextPermission();
    }

    private void checkNextPermission() {
        if (currentPermissionIndex >= permissionQueue.size()) {
            checkBatteryOptimization();
            return;
        }
        PermissionItem item = permissionQueue.get(currentPermissionIndex);
        if (ContextCompat.checkSelfPermission(this, item.permission) == PackageManager.PERMISSION_GRANTED) {
            currentPermissionIndex++;
            checkNextPermission();
        } else {
            requestPermissionLauncher.launch(item.permission);
        }
    }

    private void handlePermissionResult(boolean isGranted) {
        PermissionItem item = permissionQueue.get(currentPermissionIndex);
        if (isGranted) {
            currentPermissionIndex++;
            checkNextPermission();
        } else {
            if (!shouldShowRequestPermissionRationale(item.permission)) {
                showPermanentDenialDialog(item);
            } else {
                Toast.makeText(this, item.name + "未开启，部分功能可能受限", Toast.LENGTH_SHORT).show();
                currentPermissionIndex++;
                checkNextPermission();
            }
        }
    }

    private void showPermanentDenialDialog(PermissionItem item) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(item.name + " 已禁用")
                .setMessage(item.description + "。您已关闭权限并勾选了“不再询问”，请前往设置手动开启。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("跳过", (dialog, which) -> {
                    currentPermissionIndex++;
                    checkNextPermission();
                })
                .setCancelable(false)
                .show();
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("电池无限制设置")
                        .setMessage("为了确保提醒功能的绝对准时，建议将应用设为“不优化电池使用”。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            startActivity(intent);
                        })
                        .setNegativeButton("完成检查", (dialog, which) -> {
                            Toast.makeText(this, "权限检查已完成", Toast.LENGTH_SHORT).show();
                        })
                        .show();
            } else {
                Toast.makeText(this, "所有核心权限均已就绪", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        // 清理 Toast 防止窗口泄漏
        if (mToast != null) {
            mToast.cancel();
        }
        // 清理 ViewBinding 引用防止内存泄漏
        binding = null;
        super.onDestroy();
    }
}
