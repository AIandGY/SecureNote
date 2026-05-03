package com.aigy.securenote;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.Database.Note;
import com.aigy.securenote.Statusbar.Weather;
import com.aigy.securenote.databinding.ActivityWriteBinding;
import com.aigy.securenote.utils.PrefUtils;
import com.aigy.securenote.utils.ReminderManager;
import com.aigy.securenote.utils.SystemReminderHelper;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 笔记编辑/新建页面
 * 实现：双色调深度适配（包含表盘选中圆圈、日历选中圈、操作按钮颜色）
 */
public class writeActivity extends AppCompatActivity {

    private static final String TAG = "WriteActivity";
    private ActivityWriteBinding binding;
    private AppDatabase db;
    private int reminderMinutes = 0;
    private long noteId = -1;
    private Weather weatherClient;
    
    private String selectedOnceDate = ""; 
    private boolean isProgrammaticChange = false; 
    private boolean currentNoteIsAi = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            EdgeToEdge.enable(this);
            binding = ActivityWriteBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            db = AppDatabase.getDatabase(this);

            ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });

            noteId = getIntent().getLongExtra("note_id", -1);

            initToolbar();
            initCheckboxes();
            initWeatherAndLocation();
            initImportanceSpinner();
            
            if (noteId != -1) {
                loadNoteData();
            } else {
                initNewNoteDefaults();
            }

            binding.btnSave.setOnClickListener(v -> saveNote());

        } catch (Exception e) {
            Log.e(TAG, "onCreate 错误: ", e);
            Toast.makeText(this, "页面加载失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCustomThemeColor();
    }

    /**
     * 适配页面内静态组件颜色
     */
    private void applyCustomThemeColor() {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int defaultColor = typedValue.data;

        int textColor = PrefUtils.getTextMainColor(this, defaultColor);
        int iconColor = PrefUtils.getIconMainColor(this, defaultColor);
        ColorStateList iconCsl = ColorStateList.valueOf(iconColor);

        // 文字主色调
        binding.btnSave.setTextColor(textColor);
        binding.tvAiLabel.setTextColor(textColor);
        binding.tvRemindTime.setTextColor(textColor);
        binding.tvOnceRemindTime.setTextColor(textColor);
        binding.btnSetReminder.setTextColor(textColor);

        // 图标主色调 (包含 CheckBox)
        binding.ivRefreshLocation.setColorFilter(iconColor);
        binding.cbIncludeTime.setButtonTintList(iconCsl);
        binding.cbIncludeLocation.setButtonTintList(iconCsl);
        binding.cbIncludeWeather.setButtonTintList(iconCsl);
        binding.cbRemind.setButtonTintList(iconCsl);
        binding.cbOnceRemind.setButtonTintList(iconCsl);
    }

    /**
     * 针对 Material 弹窗进行深度颜色适配：
     * 1. 按钮使用“文字主色调”
     * 2. 指针圆圈/选中日期圆圈使用“图标主色调”
     */
    private void themeMaterialFragment(androidx.fragment.app.DialogFragment fragment) {
        fragment.getLifecycle().addObserver((androidx.lifecycle.LifecycleEventObserver) (source, event) -> {
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                if (fragment.getDialog() != null && fragment.getDialog().getWindow() != null) {
                    View decorView = fragment.getDialog().getWindow().getDecorView();
                    
                    TypedValue typedValue = new TypedValue();
                    getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
                    int defaultColor = typedValue.data;

                    int textColor = PrefUtils.getTextMainColor(this, defaultColor);
                    int iconColor = PrefUtils.getIconMainColor(this, defaultColor);
                    ColorStateList iconCsl = ColorStateList.valueOf(iconColor);

                    // 1. 适配确定/取消按钮 (文字主色调) - 修复 ArrayList 编译错误
                    ArrayList<View> buttons = new ArrayList<>();
                    decorView.findViewsWithText(buttons, "确定", View.FIND_VIEWS_WITH_TEXT);
                    decorView.findViewsWithText(buttons, "确认", View.FIND_VIEWS_WITH_TEXT);
                    decorView.findViewsWithText(buttons, "OK", View.FIND_VIEWS_WITH_TEXT);
                    decorView.findViewsWithText(buttons, "取消", View.FIND_VIEWS_WITH_TEXT);
                    decorView.findViewsWithText(buttons, "CANCEL", View.FIND_VIEWS_WITH_TEXT);
                    
                    for (View v : buttons) {
                        if (v instanceof Button) ((Button) v).setTextColor(textColor);
                    }

                    // 2. 深度查找并修改“选中指示器”（指针圆圈/日期高亮）背景色 (图标主色调)
                    applyThemeToSelectionIndicators(decorView, iconColor, iconCsl);
                }
            }
        });
    }

    /**
     * 精准锁定选中元素进行变色，不触碰大背景
     */
    private void applyThemeToSelectionIndicators(View root, int color, ColorStateList csl) {
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyThemeToSelectionIndicators(vg.getChildAt(i), color, csl);
            }
        }

        String className = root.getClass().getName();
        
        // 适配 TimePicker 时钟指针和指针末端的选中圆圈背景 (图标主色调)
        if (className.contains("ClockHandView")) {
            try {
                // 反射调用 Material 内部绘制逻辑颜色设置
                Method setHandColor = root.getClass().getMethod("setHandColor", int.class);
                setHandColor.setAccessible(true);
                setHandColor.invoke(root, color);
                
                // 尝试修改末端 selector 圆圈色
                Method setSelectorColor = root.getClass().getDeclaredMethod("setSelectorColor", int.class);
                setSelectorColor.setAccessible(true);
                setSelectorColor.invoke(root, color);
            } catch (Exception ignored) {}
        }


        // ----------------- 表盘背景色：选中时变主题色，未选中时恢复默认 -----------------
        if (root instanceof com.google.android.material.chip.Chip) {
            Chip chip = (Chip) root;
            ColorStateList originalBg = chip.getChipBackgroundColor();
            int defaultColor = (originalBg != null) ? originalBg.getDefaultColor() : Color.TRANSPARENT;
            chip.setChipBackgroundColor(new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{}
                    },
                    new int[]{ color, defaultColor }
            ));
        }

        // 适配 TimePicker 的指针、中心点和选中圆圈
        if (className.contains("ClockHandView")) {
            try {
                // 1. 修改 Clock dial selector track (指针线条) 和 center (中心圆点)
                // 在 Material 源码中，线条和中心点共用一个名为 'paint' 的 Paint 对象
                java.lang.reflect.Field paintField = root.getClass().getDeclaredField("paint");
                paintField.setAccessible(true);
                android.graphics.Paint paint = (android.graphics.Paint) paintField.get(root);
                if (paint != null) {
                    paint.setColor(color);
                }

                // 2. 修改 Clock dial selector container (选中数字时的圆形背景框)
                // 这种方式调用 setter 方法，会自动触发重绘
                Method setSelectorColor = root.getClass().getDeclaredMethod("setSelectorColor", int.class);
                setSelectorColor.setAccessible(true);
                setSelectorColor.invoke(root, color);

                // 3. 兼容性：某些版本可能需要显式设置 handColor
                try {
                    Method setHandColor = root.getClass().getMethod("setHandColor", int.class);
                    setHandColor.setAccessible(true);
                    setHandColor.invoke(root, color);
                } catch (NoSuchMethodException ignored) {}

                root.invalidate(); // 强制重绘
            } catch (Exception e) {
                Log.e(TAG, "适配时钟拨盘失败: " + e.getMessage());
            }
        }

        // ----------------- 表盘数字选中的背景色 (Chip 适配) -----------------
        if (root instanceof com.google.android.material.chip.Chip) {
            Chip chip = (Chip) root;
            ColorStateList originalBg = chip.getChipBackgroundColor();
            int defaultColor = (originalBg != null) ? originalBg.getDefaultColor() : Color.TRANSPARENT;

            // 设置选中时为主题图标色，未选中时保持默认
            chip.setChipBackgroundColor(new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{}
                    },
                    new int[]{ color, defaultColor }
            ));
        }



        // 适配 DatePicker 的功能切换图标 (如：切换到输入模式的铅笔图标、左右翻页图标)
        if (root instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) root).setImageTintList(csl);
        }

        // 适配 DatePicker 的功能切换图标 (图标主色调)
        if (root instanceof android.widget.ImageButton) {
            ((android.widget.ImageButton) root).setImageTintList(csl);
        }
        
        // 适配 DatePicker 选中日期的背景色 (图标主色调)
        // MaterialDatePicker 内部使用 TextView 显示日期，选中的日期会有一个 Checked 状态
        if (root instanceof TextView && root.isClickable()) {
             // 此处通过状态判断动态设置背景色，如果该方案由于 Material 库封装太深无法生效，
             // 则需要通过 ContextThemeWrapper 覆写 colorPrimary 属性。
        }
    }

    private void initToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setTitle(noteId != -1 ? "编辑笔记" : "新建笔记");
    }

    private void initWeatherAndLocation() {
        if (!PrefUtils.isShowWeather(this)) return;
        weatherClient = new Weather(this, new Weather.WeatherUpdateListener() {
            @Override public void onWeatherUpdate(String weather, String temp) {
                if (binding != null && binding.cbIncludeWeather.isChecked()) {
                    binding.tvWeather.setText(weather + " " + temp);
                    binding.tvWeather.setAlpha(1.0f); 
                }
            }
            @Override public void onError(String message) {
                if (binding != null && binding.cbIncludeWeather.isChecked()) binding.tvWeather.setText(message);
            }
        });
    }

    private void initImportanceSpinner() {
        String[] levels = {"重要且紧急", "重要不紧急", "紧急不重要", "不重要不紧急"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, levels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerImportance.setAdapter(adapter);
    }

    private void initCheckboxes() {
        binding.layoutTimeContainer.setVisibility(PrefUtils.isShowTimestamp(this) ? View.VISIBLE : View.GONE);
        binding.layoutLocationContainer.setVisibility(PrefUtils.isShowLocation(this) ? View.VISIBLE : View.GONE);
        binding.layoutWeatherContainer.setVisibility(PrefUtils.isShowWeather(this) ? View.VISIBLE : View.GONE);

        binding.cbIncludeTime.setOnCheckedChangeListener((bv, isChecked) -> {
            binding.tvDate.setAlpha(isChecked ? 1.0f : 0.2f);
            if (isChecked && !isProgrammaticChange && TextUtils.isEmpty(binding.tvDate.getText())) updateDateText(System.currentTimeMillis());
        });
        binding.cbIncludeWeather.setOnCheckedChangeListener((bv, isChecked) -> {
            binding.tvWeather.setAlpha(isChecked ? 1.0f : 0.2f);
            if (isChecked && !isProgrammaticChange) { binding.tvWeather.setText("获取中..."); fetchCurrentWeather(); }
        });
        binding.cbIncludeLocation.setOnCheckedChangeListener((bv, isChecked) -> {
            binding.tvLocation.setAlpha(isChecked ? 1.0f : 0.2f);
            binding.ivRefreshLocation.setAlpha(isChecked ? 0.5f : 0.1f);
            if (isChecked && !isProgrammaticChange) { binding.tvLocation.setText("定位中..."); fetchCurrentLocation(); }
        });
        binding.ivRefreshLocation.setOnClickListener(v -> { if (binding.cbIncludeLocation.isChecked()) { startRefreshAnimation(v); binding.tvLocation.setText("正在刷新..."); fetchCurrentLocation(); } });
        
        binding.cbRemind.setOnCheckedChangeListener((bv, isChecked) -> {
            if (isChecked) {
                binding.cbOnceRemind.setChecked(false); checkNotificationPermission();
                binding.tvRemindTime.setAlpha(1.0f);
                if (bv.isPressed()) showDailyTimePicker();
            } else { binding.tvRemindTime.setAlpha(0.4f); if (!isProgrammaticChange) binding.tvRemindTime.setText("未设置"); }
            updateAdvanceButtonState(); 
        });
        binding.cbOnceRemind.setOnCheckedChangeListener((bv, isChecked) -> {
            if (isChecked) {
                binding.cbRemind.setChecked(false); checkNotificationPermission();
                binding.tvOnceRemindTime.setAlpha(1.0f);
                if (bv.isPressed()) showOnceReminderPicker();
            } else {
                binding.tvOnceRemindTime.setAlpha(0.4f);
                if (!isProgrammaticChange) { binding.tvOnceRemindTime.setText("未设置"); selectedOnceDate = ""; }
            }
            updateAdvanceButtonState(); 
        });
        binding.tvRemindTime.setOnClickListener(v -> { if (binding.cbRemind.isChecked()) showDailyTimePicker(); });
        binding.tvOnceRemindTime.setOnClickListener(v -> { if (binding.cbOnceRemind.isChecked()) showOnceReminderPicker(); });
        binding.btnSetReminder.setOnClickListener(v -> showMinutePickerDialog());
        updateAdvanceButtonState();
    }

    private void startRefreshAnimation(View v) {
        RotateAnimation rotate = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(800); rotate.setRepeatCount(1); v.startAnimation(rotate);
    }

    private void updateAdvanceButtonState() {
        boolean anyRemind = binding.cbRemind.isChecked() || binding.cbOnceRemind.isChecked();
        binding.btnSetReminder.setEnabled(anyRemind);
        binding.btnSetReminder.setAlpha(anyRemind ? 1.0f : 0.4f);
    }

    private void initNewNoteDefaults() {
        binding.spinnerImportance.setSelection(PrefUtils.getDefaultImportance(this));
        binding.cbIncludeTime.setChecked(PrefUtils.isShowTimestamp(this));
        binding.cbIncludeWeather.setChecked(PrefUtils.isShowWeather(this));
        binding.cbIncludeLocation.setChecked(PrefUtils.isShowLocation(this));
        if (binding.layoutTimeContainer.getVisibility() == View.VISIBLE) updateDateText(System.currentTimeMillis());
        if (binding.layoutWeatherContainer.getVisibility() == View.VISIBLE && binding.cbIncludeWeather.isChecked()) fetchCurrentWeather();
        if (binding.layoutLocationContainer.getVisibility() == View.VISIBLE && binding.cbIncludeLocation.isChecked()) fetchCurrentLocation();
    }

    private void fetchCurrentWeather() {
        if (weatherClient != null) {
            weatherClient.startAutoUpdate();
            new Handler(Looper.getMainLooper()).postDelayed(() -> { if (weatherClient != null) weatherClient.stopAutoUpdate(); }, 5000);
        }
    }

    private void fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            binding.tvLocation.setText("无权限"); return;
        }
        new Thread(() -> {
            String locationResult = "定位失败";
            try {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                Location loc = null;
                List<String> providers = lm.getProviders(true);
                for (String p : providers) {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (loc == null || l.getAccuracy() < loc.getAccuracy())) loc = l;
                }
                if (loc != null) {
                    Geocoder g = new Geocoder(this, Locale.CHINA);
                    List<Address> addrs = g.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                    if (addrs != null && !addrs.isEmpty()) { Address a = addrs.get(0); locationResult = a.getLocality() + "·" + a.getSubLocality(); }
                }
            } catch (Exception ignored) {}
            final String finalResult = locationResult;
            runOnUiThread(() -> {
                if (binding.layoutLocationContainer.getVisibility() == View.VISIBLE && binding.cbIncludeLocation.isChecked()) {
                    binding.tvLocation.setText(finalResult); binding.tvLocation.setAlpha(1.0f);
                }
            });
        }).start();
    }

    private void saveNote() {
        String title = binding.etTitle.getText().toString().trim();
        String content = binding.etContent.getText().toString().trim();
        if (title.isEmpty() && content.isEmpty()) { Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show(); return; }
        String importance = binding.spinnerImportance.getSelectedItem().toString();
        if ("重要不紧急".equals(importance)) {
            if (!binding.cbRemind.isChecked() && !binding.cbOnceRemind.isChecked()) { Toast.makeText(this, "“重要不紧急”任务必须设置提醒时间", Toast.LENGTH_LONG).show(); return; }
            if (reminderMinutes == 0) { Toast.makeText(this, "“重要不紧急”任务必须设置提前提醒时间", Toast.LENGTH_LONG).show(); showMinutePickerDialog(); return; }
        }
        long timestamp = (binding.layoutTimeContainer.getVisibility() == View.VISIBLE && binding.cbIncludeTime.isChecked()) ? System.currentTimeMillis() : 0;
        String finalWeather = (binding.layoutWeatherContainer.getVisibility() == View.VISIBLE && binding.cbIncludeWeather.isChecked()) ? binding.tvWeather.getText().toString() : "";
        String location = (binding.layoutLocationContainer.getVisibility() == View.VISIBLE && binding.cbIncludeLocation.isChecked()) ? binding.tvLocation.getText().toString() : "";
        String dt = binding.cbRemind.isChecked() ? binding.tvRemindTime.getText().toString() : "";
        String od = "", ot = "";
        if (binding.cbOnceRemind.isChecked()) {
            String txt = binding.tvOnceRemindTime.getText().toString();
            if (txt.contains("/")) { od = selectedOnceDate; ot = txt.substring(txt.lastIndexOf(" ") + 1).trim(); }
        }
        final int adv = reminderMinutes; final String fdt = dt, fod = od, fot = ot;
        new Thread(() -> {
            Note note = new Note(); note.setTitle(title); note.setContent(content); note.setTimestamp(timestamp);
            note.setImportance(importance); note.setReminderTime(fdt); note.setReminderDate(fod); note.setOnceReminderTime(fot);
            note.setReminderMinutes(adv); note.setWeather(finalWeather); note.setLocation(location); note.setAiGenerated(currentNoteIsAi);
            if (noteId != -1) { note.setId(noteId); db.noteDao().update(note); } else { noteId = db.noteDao().insert(note); }
            SystemReminderHelper.addEnhancedReminder(this, note);
            ReminderManager.cancelReminder(this, noteId); 
            if (binding.cbRemind.isChecked() && !fdt.isEmpty()) ReminderManager.setDailyReminder(this, noteId, title, fdt, adv);
            if (binding.cbOnceRemind.isChecked() && !fod.isEmpty() && !fot.isEmpty()) ReminderManager.setOnceReminder(this, noteId, title, fod, fot, adv);
            runOnUiThread(() -> { Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show(); finish(); });
        }).start();
    }

    private void updateDateText(long timestamp) {
        String dateStr = new SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault()).format(new Date(timestamp));
        binding.tvDate.setText(dateStr);
    }

    private void loadNoteData() {
        isProgrammaticChange = true;
        new Thread(() -> {
            Note note = db.noteDao().getNoteById(noteId);
            if (note != null) {
                runOnUiThread(() -> {
                    currentNoteIsAi = note.isAiGenerated(); binding.etTitle.setText(note.getTitle()); binding.etContent.setText(note.getContent());
                    ArrayAdapter<String> adapter = (ArrayAdapter<String>) binding.spinnerImportance.getAdapter();
                    int pos = adapter.getPosition(note.getImportance()); if (pos != -1) binding.spinnerImportance.setSelection(pos);
                    if (PrefUtils.isShowTimestamp(this)) { if (note.getTimestamp() > 0) { binding.cbIncludeTime.setChecked(true); updateDateText(note.getTimestamp()); } else binding.cbIncludeTime.setChecked(false); }
                    if (PrefUtils.isShowWeather(this)) {
                        String weather = note.getWeather();
                        if (currentNoteIsAi && "未成功获取天气戳".equals(weather)) { binding.cbIncludeWeather.setChecked(true); binding.tvWeather.setText("获取中..."); fetchCurrentWeather(); }
                        else { binding.tvWeather.setText(TextUtils.isEmpty(weather) ? "天气戳" : weather); binding.cbIncludeWeather.setChecked(!TextUtils.isEmpty(weather) && !"天气戳".equals(weather)); }
                    }
                    if (PrefUtils.isShowLocation(this)) {
                        String loc = note.getLocation(); binding.tvLocation.setText(TextUtils.isEmpty(loc) ? "地点戳" : loc);
                        binding.cbIncludeLocation.setChecked(!TextUtils.isEmpty(loc) && !"地点戳".equals(loc));
                    }
                    reminderMinutes = note.getReminderMinutes(); updateAdvanceButtonText();
                    if (!TextUtils.isEmpty(note.getReminderTime())) { binding.tvRemindTime.setText(note.getReminderTime()); binding.cbRemind.setChecked(true); binding.tvRemindTime.setAlpha(1.0f); }
                    if (!TextUtils.isEmpty(note.getReminderDate()) && !TextUtils.isEmpty(note.getOnceReminderTime())) {
                        selectedOnceDate = note.getReminderDate(); String displayDate = selectedOnceDate.replace("-", "/");
                        binding.tvOnceRemindTime.setText(displayDate + " " + note.getOnceReminderTime());
                        binding.cbOnceRemind.setChecked(true); binding.tvOnceRemindTime.setAlpha(1.0f);
                    }
                    isProgrammaticChange = false; updateAdvanceButtonState(); 
                });
            }
        }).start();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private int[] parseCurrentTime(String timeStr) {
        int[] time = new int[2]; Calendar c = Calendar.getInstance();
        time[0] = c.get(Calendar.HOUR_OF_DAY); time[1] = c.get(Calendar.MINUTE);
        if (timeStr != null && timeStr.contains(":")) {
            try {
                String rawTime = timeStr.trim(); if (rawTime.contains(" ")) rawTime = rawTime.substring(rawTime.lastIndexOf(" ") + 1);
                String[] parts = rawTime.split(":"); time[0] = Integer.parseInt(parts[0].trim()); time[1] = Integer.parseInt(parts[1].trim());
            } catch (Exception ignored) {}
        }
        return time;
    }

    private void showDailyTimePicker() {
        int[] time = parseCurrentTime(binding.tvRemindTime.getText().toString());
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H).setHour(time[0]).setMinute(time[1])
                .setTitleText("每日提醒时间").setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK).build();
        picker.addOnPositiveButtonClickListener(v -> binding.tvRemindTime.setText(String.format(Locale.getDefault(), "%02d:%02d", picker.getHour(), picker.getMinute())));
        picker.addOnCancelListener(dialog -> { if (binding.tvRemindTime.getText().equals("未设置")) binding.cbRemind.setChecked(false); });
        picker.addOnNegativeButtonClickListener(v -> { if (binding.tvRemindTime.getText().equals("未设置")) binding.cbRemind.setChecked(false); });
        themeMaterialFragment(picker);
        picker.show(getSupportFragmentManager(), "DAILY_TIME_PICKER");
    }

    private void showOnceReminderPicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择提醒日期").setSelection(MaterialDatePicker.todayInUtcMilliseconds()).build();
        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(selection);
            selectedOnceDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.getTime());
            String displayDate = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(calendar.getTime());
            showOnceTimePicker(displayDate);
        });
        datePicker.addOnCancelListener(dialog -> { if (binding.tvOnceRemindTime.getText().equals("未设置")) binding.cbOnceRemind.setChecked(false); });
        datePicker.addOnNegativeButtonClickListener(v -> { if (binding.tvOnceRemindTime.getText().equals("未设置")) binding.cbOnceRemind.setChecked(false); });
        themeMaterialFragment(datePicker);
        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void showOnceTimePicker(String displayDate) {
        int[] time = parseCurrentTime(binding.tvOnceRemindTime.getText().toString());
        MaterialTimePicker timePicker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H).setHour(time[0]).setMinute(time[1])
                .setTitleText("选择提醒时间").setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK).build();
        timePicker.addOnPositiveButtonClickListener(v -> binding.tvOnceRemindTime.setText(displayDate + " " + String.format(Locale.getDefault(), "%02d:%02d", timePicker.getHour(), timePicker.getMinute())));
        timePicker.addOnCancelListener(dialog -> { if (binding.tvOnceRemindTime.getText().equals("未设置")) binding.cbOnceRemind.setChecked(false); });
        timePicker.addOnNegativeButtonClickListener(v -> { if (binding.tvOnceRemindTime.getText().equals("未设置")) binding.cbOnceRemind.setChecked(false); });
        themeMaterialFragment(timePicker);
        timePicker.show(getSupportFragmentManager(), "ONCE_TIME_PICKER");
    }

    private void showMinutePickerDialog() {
        final NumberPicker np = new NumberPicker(this);
        np.setMinValue(0); np.setMaxValue(120); np.setValue(reminderMinutes);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER; np.setLayoutParams(params); container.addView(np);

        // 获取主题主色调作为兜底，防止直接使用黑色
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int defaultColor = typedValue.data;
        int textColor = PrefUtils.getTextMainColor(this, defaultColor);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("设置提前提醒(分钟)").setView(container).setPositiveButton("确定", (d, w) -> { reminderMinutes = np.getValue(); updateAdvanceButtonText(); }).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(d -> { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(textColor); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(textColor); });
        dialog.show();
    }

    private void updateAdvanceButtonText() {
        if (reminderMinutes == 0) binding.btnSetReminder.setText("准时提醒");
        else binding.btnSetReminder.setText("提前" + reminderMinutes + "分钟提醒");
    }
}
