package com.aigy.securenote;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.NumberPicker;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import com.aigy.securenote.databinding.ActivityPersonalizationBinding;
import com.aigy.securenote.utils.PrefUtils;
import com.aigy.securenote.utils.ThemeUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 个性化设置页面
 * 实现功能：内容显隐控制、自动升级策略、AI身份定义、布局切换、双色调自定义（文字/图标）
 * 优化：使用 ThemeUtils 统一管理全局主题色应用
 */
public class PersonalizationActivity extends AppCompatActivity {

    private ActivityPersonalizationBinding binding;
    private static final int REQUEST_CALENDAR_PERMISSION = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityPersonalizationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomPadding = Math.max(systemBars.bottom, imeInsets.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
            return WindowInsetsCompat.CONSUMED;
        });

        initUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCustomThemeColor();
    }

    private void initUI() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        // 1. 初始化开关状态
        binding.switchTimestamp.setChecked(PrefUtils.isShowTimestamp(this));
        binding.switchLocation.setChecked(PrefUtils.isShowLocation(this));
        binding.switchWeather.setChecked(PrefUtils.isShowWeather(this));
        binding.switchLimitContent.setChecked(PrefUtils.isLimitContent(this));
        binding.switchColorReminder.setChecked(PrefUtils.isColorReminder(this));
        binding.switchScheduleBorder.setChecked(PrefUtils.isHideScheduleBorder(this));

        binding.switchTimestamp.setOnCheckedChangeListener((v, isChecked) -> PrefUtils.setShowTimestamp(this, isChecked));
        binding.switchLocation.setOnCheckedChangeListener((v, isChecked) -> PrefUtils.setShowLocation(this, isChecked));
        binding.switchWeather.setOnCheckedChangeListener((v, isChecked) -> PrefUtils.setShowWeather(this, isChecked));
        binding.switchLimitContent.setOnCheckedChangeListener((v, isChecked) -> PrefUtils.setLimitContent(this, isChecked));
        binding.switchColorReminder.setOnCheckedChangeListener((v, isChecked) -> PrefUtils.setColorReminder(this, isChecked));
        binding.switchScheduleBorder.setOnCheckedChangeListener((v, isChecked) -> PrefUtils.setHideScheduleBorder(this, isChecked));

        // 2. 升级策略设置
        updateUrgentUpgradeUI();
        binding.layoutUrgentUpgrade.setOnClickListener(v -> showUrgentUpgradePickerDialog());

        // 3. 增强提醒
        binding.switchEnhancedReminder.setChecked(PrefUtils.isEnhancedReminder(this));
        binding.switchEnhancedReminder.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) checkAndRequestCalendarPermissions();
            else PrefUtils.setEnhancedReminder(this, false);
        });

        // 4. AI 助手定义
        binding.etAiIdentity.setText(PrefUtils.getAiIdentity(this));
        binding.etAiIdentity.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                PrefUtils.setAiIdentity(PersonalizationActivity.this, s.toString().trim());
            }
        });

        // 5. 排列方式
        String[] listModes = {"垂直列表", "瀑布流网格"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, listModes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerListMode.setAdapter(modeAdapter);
        binding.spinnerListMode.setSelection(PrefUtils.getListMode(this));
        binding.spinnerListMode.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                PrefUtils.setListMode(PersonalizationActivity.this, position);
            }
        });

        // 6. 默认优先级
        String[] importanceLevels = {"重要且紧急", "重要不紧急", "紧急不重要", "不重要不紧急"};
        ArrayAdapter<String> impAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, importanceLevels);
        impAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerDefaultImportance.setAdapter(impAdapter);
        binding.spinnerDefaultImportance.setSelection(PrefUtils.getDefaultImportance(this));
        binding.spinnerDefaultImportance.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                PrefUtils.setDefaultImportance(PersonalizationActivity.this, position);
            }
        });

        // 7. 外观 - 主题模式
        String[] nightModes = {"跟随系统", "始终浅色", "始终深色"};
        ArrayAdapter<String> nightAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, nightModes);
        nightAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerNightMode.setAdapter(nightAdapter);
        binding.spinnerNightMode.setSelection(PrefUtils.getNightMode(this));
        binding.spinnerNightMode.setOnItemSelectedListener(new SimpleItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (PrefUtils.getNightMode(PersonalizationActivity.this) != position) {
                    PrefUtils.setNightMode(PersonalizationActivity.this, position);
                    applyNightMode(position);
                }
            }
        });

        // 8. 文字主色调
        binding.etTextMainColor.setText(PrefUtils.getTextMainColorHex(this));
        binding.etTextMainColor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String hex = s.toString().trim();
                if (hex.isEmpty() || hex.length() == 6 || (hex.startsWith("#") && hex.length() == 7)) {
                    PrefUtils.setTextMainColor(PersonalizationActivity.this, hex);
                    applyCustomThemeColor();
                }
            }
        });

        // 9. 图标主色调
        binding.etIconMainColor.setText(PrefUtils.getIconMainColorHex(this));
        binding.etIconMainColor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String hex = s.toString().trim();
                if (hex.isEmpty() || hex.length() == 6 || (hex.startsWith("#") && hex.length() == 7)) {
                    PrefUtils.setIconMainColor(PersonalizationActivity.this, hex);
                    applyCustomThemeColor();
                }
            }
        });
    }

    /**
     * 应用双色调适配
     * 使用 ThemeUtils 工具类批量处理，实现全局风格统一
     */
    private void applyCustomThemeColor() {
        int textColor = ThemeUtils.getTextMainColor(this);
        int iconColor = ThemeUtils.getIconMainColor(this);

        // 1. 批量应用文字色到标题及分割线
        ThemeUtils.applyTextTheme(textColor,
                binding.headerVisibility,
                binding.headerUpgradeStrategy,
                binding.headerSystemFunctions,
                binding.headerAi,
                binding.headerPreference,
                binding.headerAppearance,
                binding.tvUrgentUpgradeValue,
                binding.lineAiIdentity,
                binding.lineTextMainColor,
                binding.lineIconMainColor
        );

        // 2. 批量应用双色调逻辑到所有 MaterialSwitch 开关
        ThemeUtils.applySwitchTheme(this, iconColor,
                binding.switchTimestamp,
                binding.switchLocation,
                binding.switchWeather,
                binding.switchLimitContent,
                binding.switchColorReminder,
                binding.switchEnhancedReminder,
                binding.switchScheduleBorder
        );
    }

    private void updateUrgentUpgradeUI() {
        int minutes = PrefUtils.getUrgentUpgradeTime(this);
        binding.tvUrgentUpgradeValue.setText(minutes + " 分钟");
    }

    private void showUrgentUpgradePickerDialog() {
        final NumberPicker np = new NumberPicker(this);
        np.setMinValue(1); np.setMaxValue(120);
        np.setValue(PrefUtils.getUrgentUpgradeTime(this));
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        np.setLayoutParams(params);
        container.addView(np);

        new MaterialAlertDialogBuilder(this)
                .setTitle("设置紧急不重要升级时间")
                .setView(container)
                .setPositiveButton("确定", (d, w) -> {
                    PrefUtils.setUrgentUpgradeTime(this, np.getValue());
                    updateUrgentUpgradeUI();
                }).setNegativeButton("取消", null).show();
    }

    private void checkAndRequestCalendarPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR}, REQUEST_CALENDAR_PERMISSION);
        } else { PrefUtils.setEnhancedReminder(this, true); }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CALENDAR_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                PrefUtils.setEnhancedReminder(this, true);
                Toast.makeText(this, "增强提醒功能已开启", Toast.LENGTH_SHORT).show();
            } else {
                binding.switchEnhancedReminder.setChecked(false);
                PrefUtils.setEnhancedReminder(this, false);
                Toast.makeText(this, "开启失败：需要日历读写权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void applyNightMode(int mode) {
        int nightMode = (mode == 1) ? AppCompatDelegate.MODE_NIGHT_NO : (mode == 2 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    private abstract static class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override public abstract void onItemSelected(AdapterView<?> parent, View view, int position, long id);
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
}
