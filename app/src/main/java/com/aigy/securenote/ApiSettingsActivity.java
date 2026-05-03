package com.aigy.securenote;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aigy.securenote.databinding.ActivityApiSettingsBinding;
import com.aigy.securenote.utils.PrefUtils;

/**
 * API 接口设置页面
 * 包含：双色调适配、各接口配置读取与保存
 */
public class ApiSettingsActivity extends AppCompatActivity {

    private ActivityApiSettingsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityApiSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initToolbar();
        loadSettings();
        initClickListeners();
        binding.btnSave.setOnClickListener(v -> saveSettings());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 动态应用自定义主题色
        applyCustomThemeColor();
    }

    /**
     * 应用双色调：文字主色调 vs 图标主色调
     */
    private void applyCustomThemeColor() {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int defaultColor = typedValue.data;

        // 获取独立的主色调配置
        int textColor = PrefUtils.getTextMainColor(this, defaultColor);
        int iconColor = PrefUtils.getIconMainColor(this, defaultColor);

        // 1. 修改 Header 文字颜色 (应用文字主色调)
        binding.headerAi.setTextColor(textColor);
        binding.headerAsr.setTextColor(textColor);
        binding.headerWeather.setTextColor(textColor);

        // 2. 修改保存按钮背景颜色 (应用图标主色调)
        binding.btnSave.setBackgroundTintList(ColorStateList.valueOf(iconColor));
    }

    private void initToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadSettings() {
        // AI
        binding.etAiKey.setText(PrefUtils.getAiApiKey(this));
        binding.etAiUrl.setText(PrefUtils.getAiBaseUrl(this));

        // Speech
        binding.etAsrId.setText(PrefUtils.getAsrAppId(this));
        binding.etAsrKey.setText(PrefUtils.getAsrApiKey(this));
        binding.etAsrSecret.setText(PrefUtils.getAsrSecretKey(this));

        // Weather
        binding.etWeatherKey.setText(PrefUtils.getWeatherApiKey(this));
    }

    private void saveSettings() {
        String aiKey = binding.etAiKey.getText().toString().trim();
        String aiUrl = binding.etAiUrl.getText().toString().trim();
        
        String asrId = binding.etAsrId.getText().toString().trim();
        String asrKey = binding.etAsrKey.getText().toString().trim();
        String asrSecret = binding.etAsrSecret.getText().toString().trim();
        
        String weatherKey = binding.etWeatherKey.getText().toString().trim();

        // 保存 AI 配置
        PrefUtils.saveAiSettings(this, aiKey, aiUrl);
        
        // 保存百度语音识别配置
        PrefUtils.saveAsrSettings(this, asrId, asrKey, asrSecret);
        
        // 保存和风天气配置
        PrefUtils.saveWeatherApiKey(this, weatherKey);

        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
    private void initClickListeners() {
        // 跳转外部链接
        binding.tvGetAi.setOnClickListener(v -> openBrowser("https://platform.deepseek.com/api_keys/"));
        binding.tvGetAsr.setOnClickListener(v -> openBrowser("https://console.bce.baidu.com/ai-engine/speech/overview/index"));
        binding.tvGetWeather.setOnClickListener(v -> openBrowser("https://console.qweather.com/project"));

        // 保存配置
        binding.btnSave.setOnClickListener(v -> saveSettings());
    }

    private void openBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "未找到浏览器", Toast.LENGTH_SHORT).show();
        }
    }
}
