package com.aigy.securenote;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.aigy.securenote.databinding.ActivityPasswordBinding;
import com.aigy.securenote.utils.PrefUtils;
import com.aigy.securenote.utils.SecurityManager;

import java.util.concurrent.Executor;

/**
 * 密码本主页面 (Activity 版)
 * 优化逻辑：仅在开启全量加密时进行安全验证，取消初次进入的密钥生成与强制备份对话框。
 */
public class PasswordActivity extends AppCompatActivity {
    private static final String TAG = "PasswordActivity";
    private ActivityPasswordBinding binding;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 1. 初始化 UI 状态，默认隐藏敏感组件
        binding.passwordRecyclerView.setVisibility(View.GONE);
        binding.addPasswordFab.setVisibility(View.GONE);

        // 2. 统一软件主题风格
        applyThemeColors();

        // 3. 设置导航与交互逻辑
        setupNavigation();

        // 4. 初始化生物识别组件（仅用于已开启加密的情况）
        setupBiometricPrompt();

        // 5. 根据加密开关执行不同的进入策略
        binding.getRoot().post(this::checkSecurityStatus);
    }

    /**
     * 应用全局主题色，确保 UI 一致性
     */
    private void applyThemeColors() {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int defaultPrimary = typedValue.data;

        int textColor = PrefUtils.getTextMainColor(this, defaultPrimary);
        int iconColor = PrefUtils.getIconMainColor(this, defaultPrimary);

        binding.addPasswordFab.setBackgroundTintList(ColorStateList.valueOf(iconColor));
        binding.bottomNavigation.setItemIconTintList(ColorStateList.valueOf(iconColor));
        binding.bottomNavigation.setItemTextColor(ColorStateList.valueOf(textColor));
    }

    private void setupNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.navigation_passwords);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_notes) {
                finish();
                return true;
            }
            return false;
        });
    }

    /**
     * 安全状态检查
     * 逻辑更新：若未开启加密，直接解锁；若已开启，执行验证。
     */
    private void checkSecurityStatus() {
        if (!PrefUtils.isEncryptionEnabled(this)) {
            // 用户未开启全量加密，直接进入密码本
            unlockVault();
        } else {
            // 已开启全量加密，必须进行身份验证
            startAuthentication();
        }
    }

    private void setupBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // 用户取消验证或多次失败，则无法进入加密区域
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                    Toast.makeText(PasswordActivity.this, "安全验证失败: " + errString, Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                // 验证成功，解锁库
                unlockVault();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(PasswordActivity.this, "认证未通过，请重试", Toast.LENGTH_SHORT).show();
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("密码本解锁")
                .setSubtitle("全量加密已开启，请验证身份")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
    }

    private void startAuthentication() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL);

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo);
        } else {
            // 设备无安全锁时，如果已开启加密却无法验证，提示用户
            Toast.makeText(this, "加密已开启但设备未设置安全锁", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    /**
     * 解锁密码本内容
     */
    private void unlockVault() {
        binding.passwordRecyclerView.setVisibility(View.VISIBLE);
        binding.addPasswordFab.setVisibility(View.VISIBLE);
        if (PrefUtils.isEncryptionEnabled(this)) {
            Toast.makeText(this, "验证成功", Toast.LENGTH_SHORT).show();
        }
    }
}