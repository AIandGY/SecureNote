package com.aigy.securenote;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.databinding.ActivityDeveloperBinding;
import com.aigy.securenote.utils.PrefUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 开发者选项页面
 * 提供日志抓取、导出、功能引导重置及系统信息展示等高级调试工具。
 */
public class DeveloperActivity extends AppCompatActivity {

    private ActivityDeveloperBinding binding;

    // 分享返回监听，用于自动清理临时日志文件
    private final ActivityResultLauncher<Intent> shareLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                clearTempLogFiles();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDeveloperBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        initControls();
        initDebugTools();
        displaySystemInfo();
    }

    private void initControls() {
        // 1. 开发者模式总开关
        binding.switchDeveloperMode.setChecked(PrefUtils.isDeveloperMode(this));
        binding.switchDeveloperMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrefUtils.setDeveloperMode(this, isChecked);
            if (!isChecked) {
                // 如果关闭了开发者模式，强制关闭日志记录并清空缓冲区
                toggleLogging(false);
                Toast.makeText(this, "开发者模式已关闭", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        // 2. 日志记录开关
        binding.switchLogging.setChecked(PrefUtils.isLoggingEnabled(this));
        binding.switchLogging.setOnCheckedChangeListener((buttonView, isChecked) -> {
            toggleLogging(isChecked);
        });
    }

    /**
     * 处理日志开关逻辑
     * @param enabled 是否开启
     */
    private void toggleLogging(boolean enabled) {
        PrefUtils.setLoggingEnabled(this, enabled);
        if (!enabled) {
            // 当用户选择关闭日志时，物理清空当前的 logcat 缓冲区
            // 确保即便之后再开启，也无法看到“关闭期间”的日志，保障隐私
            try {
                Runtime.getRuntime().exec("logcat -c");
                Toast.makeText(this, "日志记录已关闭，缓冲区已清空", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "缓存清空失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "实时日志抓取已开启", Toast.LENGTH_SHORT).show();
        }
    }

    private void initDebugTools() {
        // 3. 导出日志按钮
        binding.btnExportLogs.setOnClickListener(v -> exportLogs());

        // 4. 重置功能引导按钮
        binding.btnResetGuides.setOnClickListener(v -> {
            PrefUtils.setGuideShown(this, false);
            PrefUtils.setSelectionGuideShown(this, false);
            PrefUtils.setFilterGuideShown(this, false);
            Toast.makeText(this, "所有功能引导已重置", Toast.LENGTH_SHORT).show();
        });

        // 5. 清除所有笔记按钮
        binding.btnClearNotes.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("危险操作")
                    .setMessage("此操作将永久删除本地所有加密笔记且无法恢复，确定要继续吗？")
                    .setPositiveButton("确认清除", (dialog, which) -> {
                        new Thread(() -> {
                            AppDatabase.getDatabase(this).noteDao().deleteAllNotes();
                            runOnUiThread(() -> Toast.makeText(this, "所有笔记已清除", Toast.LENGTH_SHORT).show());
                        }).start();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private String getSystemInfoString() {
        StringBuilder info = new StringBuilder();
        info.append("应用包名: ").append(getPackageName()).append("\n");
        info.append("应用版本: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        info.append("Android 版本: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        info.append("制造商: ").append(Build.MANUFACTURER).append("\n");
        info.append("品牌: ").append(Build.BRAND).append("\n");
        info.append("设备型号: ").append(Build.MODEL).append("\n");
        info.append("硬件名称: ").append(Build.HARDWARE).append("\n");
        try {
            info.append("编译日期: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(BuildConfig.BUILD_TIME))).append("\n");
        } catch (Exception e) { info.append("编译日期: 未知\n"); }
        return info.toString();
    }

    private void displaySystemInfo() {
        binding.tvDeviceInfo.setText(getSystemInfoString());
    }

    /**
     * 抓取日志逻辑
     */
    private void exportLogs() {
        // 拦截：如果开关没开，不允许进行抓取操作
        if (!PrefUtils.isLoggingEnabled(this)) {
            Toast.makeText(this, "请先开启“系统日志记录”开关", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "正在导出日志...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                StringBuilder logBuilder = new StringBuilder();
                logBuilder.append("========== 设备与应用信息 ==========\n");
                logBuilder.append(getSystemInfoString());
                logBuilder.append("====================================\n\n");
                logBuilder.append("========== 运行日志 (Logcat) ==========\n");

                // 获取当前全量日志（包含重启前的应用存留日志）
                Process process = Runtime.getRuntime().exec("logcat -d");
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    logBuilder.append(line).append("\n");
                }

                String fileName = "SecureNote_Log_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";
                File logFile = new File(getExternalCacheDir(), fileName);
                FileOutputStream fos = new FileOutputStream(logFile);
                fos.write(logBuilder.toString().getBytes());
                fos.close();

                if (!isFinishing() && !isDestroyed()) {
                    runOnUiThread(() -> shareLogFile(logFile));
                }
            } catch (Exception e) {
                if (!isFinishing() && !isDestroyed()) {
                    runOnUiThread(() -> Toast.makeText(this, "导出失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }

    private void shareLogFile(File file) {
        try {
            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareLauncher.launch(Intent.createChooser(intent, "分享日志文件"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearTempLogFiles() {
        File cacheDir = getExternalCacheDir();
        if (cacheDir != null && cacheDir.exists()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().startsWith("SecureNote_Log_") && f.getName().endsWith(".txt")) {
                        f.delete();
                    }
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        clearTempLogFiles();
        binding = null;
        super.onDestroy();
    }
}
