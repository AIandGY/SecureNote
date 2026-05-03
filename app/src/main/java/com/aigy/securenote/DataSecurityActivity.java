package com.aigy.securenote;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.Database.PasswordEntry;
import com.aigy.securenote.databinding.ActivityDataSecurityBinding;
import com.aigy.securenote.utils.PrefUtils;
import com.aigy.securenote.utils.SecurityManager;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * 数据安全设置页面
 * 实现全量加密迁移：标题、账号、密码、备注均进行动态加解密处理。
 */
public class DataSecurityActivity extends AppCompatActivity {
    private static final String TAG = "DataSecurityActivity";
    private ActivityDataSecurityBinding binding;
    private boolean pendingTargetState = false;
    private String tempDek = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityDataSecurityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        initEncryptionSwitch();
        initRestoreLogic();
    }

    private void initEncryptionSwitch() {
        binding.switchEncryption.setChecked(PrefUtils.isEncryptionEnabled(this));
        binding.switchEncryption.setOnClickListener(v -> {
            boolean targetState = binding.switchEncryption.isChecked();
            binding.switchEncryption.setChecked(!targetState);
            pendingTargetState = targetState;
            checkAndAuthenticate();
        });
    }

    private void initRestoreLogic() {
        binding.btnRestore.setOnClickListener(v -> {
            String recoveryKey = binding.editRecoveryKey.getText().toString().trim();
            if (recoveryKey.isEmpty()) {
                Toast.makeText(this, "请输入恢复密钥", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("确认恢复数据")
                    .setMessage("系统将尝试使用此密钥解密数据。如果密钥错误，恢复操作将失败。")
                    .setPositiveButton("确定", (dialog, which) -> performDataRecovery(recoveryKey))
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void performDataRecovery(String recoveryKey) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(this);
                List<PasswordEntry> entries = db.passwordDao().getAllPasswordsSync();

                if (entries.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "没有需要恢复的数据", Toast.LENGTH_SHORT).show());
                    return;
                }

                try {
                    PasswordEntry first = entries.get(0);
                    SecurityManager.decryptData(first.getPassword(), recoveryKey);
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "校验失败：密钥无效或数据已损坏", Toast.LENGTH_LONG).show());
                    return;
                }

                int successCount = 0;
                for (PasswordEntry entry : entries) {
                    try {
                        // 全量恢复：解密所有字段
                        entry.setTitle(SecurityManager.decryptData(entry.getTitle(), recoveryKey));
                        entry.setAccount(SecurityManager.decryptData(entry.getAccount(), recoveryKey));
                        entry.setPassword(SecurityManager.decryptData(entry.getPassword(), recoveryKey));
                        if (entry.getNote() != null && !entry.getNote().isEmpty()) {
                            entry.setNote(SecurityManager.decryptData(entry.getNote(), recoveryKey));
                        }
                        entry.setIsEncrypted(0); // 恢复为明文后，同步标识符
                        db.passwordDao().update(entry);
                        successCount++;
                    } catch (Exception ignored) {}
                }

                int finalSuccessCount = successCount;
                runOnUiThread(() -> {
                    PrefUtils.setEncryptionEnabled(this, false);
                    PrefUtils.saveWrappedDek(this, "");
                    PrefUtils.saveDekHash(this, "");
                    binding.switchEncryption.setChecked(false);
                    binding.editRecoveryKey.setText("");
                    Toast.makeText(this, "全量恢复完成，还原 " + finalSuccessCount + " 条数据", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "恢复过程出错", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void checkAndAuthenticate() {
        if (pendingTargetState) {
            try {
                if (!SecurityManager.isMasterKeyExists()) SecurityManager.generateMasterKey();
                tempDek = SecurityManager.generateNewDEK();
                showRecoveryKeyDialog(tempDek);
            } catch (Exception e) {
                Toast.makeText(this, "初始化失败", Toast.LENGTH_SHORT).show();
            }
        } else {
            authenticateUser();
        }
    }

    /**
     * 显示恢复密钥对话框
     * 优化：为确定和取消按钮均设置主题色，增强视觉统一性
     */
    private void showRecoveryKeyDialog(String recoveryKey) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("开启加密保护")
                .setMessage("这是您的【恢复密钥】\n请务必离线妥善保存：\n\n"+recoveryKey+"\n")
                .setPositiveButton("我已保存", (d, which) -> {
                    authenticateUser();
                })
                .setNegativeButton("取消", (d, which) -> tempDek = null)
                .setCancelable(false)
                .create();

        dialog.show();

        // 统一获取主题颜色
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int themeColor = PrefUtils.getTextMainColor(this, typedValue.data);

        final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        final Button negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

        // 1. 为取消按钮立即设置主题色
        if (negativeButton != null) {
            negativeButton.setTextColor(themeColor);
        }

        // 2. 为确定按钮设置倒计时激活逻辑及主题色
        if (positiveButton != null) {
            positiveButton.setEnabled(false);
            positiveButton.setAlpha(0.5f);
            long delayMillis = 10000; // 10秒强制阅读时间
            positiveButton.postDelayed(() -> {
                if (!isFinishing() && dialog.isShowing()) {
                    positiveButton.setEnabled(true);
                    positiveButton.setAlpha(1.0f);
                    positiveButton.setTextColor(themeColor);
                }
            }, delayMillis);
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("RecoveryKey", text));
            Toast.makeText(this, "密钥已复制到剪贴板", Toast.LENGTH_SHORT).show();
        }
    }

    private void authenticateUser() {
        BiometricManager biometricManager = BiometricManager.from(this);
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "未设置安全锁定", Toast.LENGTH_LONG).show();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                performFinalMigration();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("安全身份确认")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void performFinalMigration() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(this);
                List<PasswordEntry> entries = db.passwordDao().getAllPasswordsSync();
                String dek;

                if (pendingTargetState) {
                    dek = tempDek;
                    PrefUtils.saveDekHash(this, SecurityManager.getSha256(dek));
                } else {
                    dek = SecurityManager.unwrapDEK(PrefUtils.getWrappedDek(this));
                }

                for (PasswordEntry entry : entries) {
                    try {
                        if (pendingTargetState) {
                            // 开启全量加密：标题、账号、密码、备注
                            entry.setTitle(SecurityManager.encryptData(entry.getTitle(), dek));
                            entry.setAccount(SecurityManager.encryptData(entry.getAccount(), dek));
                            entry.setPassword(SecurityManager.encryptData(entry.getPassword(), dek));
                            if (entry.getNote() != null) entry.setNote(SecurityManager.encryptData(entry.getNote(), dek));
                            entry.setIsEncrypted(1); // 同步更新加密标识
                        } else {
                            // 关闭全量解密：标题、账号、密码、备注
                            entry.setTitle(SecurityManager.decryptData(entry.getTitle(), dek));
                            entry.setAccount(SecurityManager.decryptData(entry.getAccount(), dek));
                            entry.setPassword(SecurityManager.decryptData(entry.getPassword(), dek));
                            if (entry.getNote() != null) entry.setNote(SecurityManager.decryptData(entry.getNote(), dek));
                            entry.setIsEncrypted(0); // 同步更新解密标识
                        }
                        db.passwordDao().update(entry);
                    } catch (Exception ignored) {}
                }

                runOnUiThread(() -> {
                    try {
                        if (pendingTargetState) {
                            PrefUtils.saveWrappedDek(this, SecurityManager.wrapDEK(dek));
                        } else {
                            PrefUtils.saveWrappedDek(this, "");
                            PrefUtils.saveDekHash(this, "");
                        }
                        PrefUtils.setEncryptionEnabled(this, pendingTargetState);
                        binding.switchEncryption.setChecked(pendingTargetState);
                        Toast.makeText(this, "安全设置已更新", Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
