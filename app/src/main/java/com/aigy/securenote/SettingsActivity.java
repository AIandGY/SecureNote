package com.aigy.securenote;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;

import com.aigy.securenote.databinding.ActivitySettingsBinding;
import com.aigy.securenote.utils.PrefUtils;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // --- 跳转逻辑 ---
        binding.itemPersonalization.setOnClickListener(v -> startActivity(new Intent(this, PersonalizationActivity.class)));
        binding.itemLanService.setOnClickListener(v -> startActivity(new Intent(this, LanServiceActivity.class)));
        binding.itemDataSecurity.setOnClickListener(v -> startActivity(new Intent(this, DataSecurityActivity.class)));
        binding.itemApiSettings.setOnClickListener(v -> startActivity(new Intent(this, ApiSettingsActivity.class)));
        binding.itemDataManagement.setOnClickListener(v -> startActivity(new Intent(this, DataManagementActivity.class)));
        binding.itemDeveloperOptions.setOnClickListener(v -> startActivity(new Intent(this, DeveloperActivity.class)));
        binding.itemAbout.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        
        updateDeveloperOptionsVisibility();
    }

    private void updateDeveloperOptionsVisibility() {
        boolean isDev = PrefUtils.isDeveloperMode(this);
        binding.itemDeveloperOptions.setVisibility(isDev ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 实时应用图标主色调
        applyCustomThemeColor();
        // 每次返回页面检查开发者模式状态（可能在关于页开启或开发者页关闭）
        updateDeveloperOptionsVisibility();
    }

    /**
     * 应用用户自定义的图标主色调
     */
    private void applyCustomThemeColor() {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int defaultColor = typedValue.data;

        // 获取用户设置的图标主色调
        int iconColor = PrefUtils.getIconMainColor(this, defaultColor);
        ColorStateList csl = ColorStateList.valueOf(iconColor);

        // 使用 ImageViewCompat 确保兼容性，并清除 XML 中可能存在的 tint 冲突
        ImageViewCompat.setImageTintList(binding.ivPersonalization, csl);
        ImageViewCompat.setImageTintList(binding.ivLanService, csl);
        ImageViewCompat.setImageTintList(binding.ivDataSecurity, csl);
        ImageViewCompat.setImageTintList(binding.ivApiSettings, csl);
        ImageViewCompat.setImageTintList(binding.ivDataManagement, csl);
        ImageViewCompat.setImageTintList(binding.ivDeveloperOptions, csl);
        ImageViewCompat.setImageTintList(binding.ivAbout, csl);
    }
}
