package com.aigy.securenote;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
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
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.Database.Note;
import com.aigy.securenote.Statusbar.MainTitleController;
import com.aigy.securenote.Statusbar.Permission;
import com.aigy.securenote.Statusbar.Weather;
import com.aigy.securenote.databinding.ActivityMainBinding;
import com.aigy.securenote.databinding.DialogConnectedClientsBinding;
import com.aigy.securenote.databinding.ItemConnectedClientBinding;
import com.aigy.securenote.service.AppStatusService;
import com.aigy.securenote.service.LanServerService;
import com.aigy.securenote.utils.AiAssistant;
import com.aigy.securenote.utils.PrefUtils;
import com.aigy.securenote.utils.SpeechHelper;
import com.aigy.securenote.utils.SystemReminderHelper;
import com.aigy.securenote.utils.ReminderManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 应用主界面 Activity
 * 管理三个主要功能模块：便签、密码本、日程表
 */
public class MainActivity extends AppCompatActivity implements NotesFragment.NoteInteractionListener, PasswordsFragment.PasswordInteractionListener {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private MainTitleController titleController;
    private AppDatabase db;
    private boolean isFirstLaunch = true;
    private Permission permissionHelper;

    // 局域网服务绑定状态
    private LanServerService lanService;
    private boolean isLanBound = false;

    private final ServiceConnection lanConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            lanService = ((LanServerService.LocalBinder) service).getService();
            isLanBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isLanBound = false;
        }
    };

    // AI 与 语音识别相关相关变量
    private SpeechHelper speechHelper;
    private AiAssistant aiAssistant;
    private Snackbar aiSnackbar;
    private ObjectAnimator pulseAnimator;
    private final Handler longPressHandler = new Handler(Looper.getMainLooper());
    private boolean isLongPressTriggered = false;
    private static final long LONG_PRESS_TIME = 800;

    // 筛选与排序状态
    private int currentSortMode = 0;
    private String currentPriorityFilter = "全部";

    // Fragment 管理
    private NotesFragment notesFragment;
    private PasswordsFragment passwordsFragment;
    private ScheduleFragment scheduleFragment;
    private Fragment currentFragment;

    private static final String TAG_NOTES = "notes_fragment";
    private static final String TAG_PASSWORDS = "passwords_fragment";
    private static final String TAG_SCHEDULE = "schedule_fragment";

    // 预设选项
    private final String[] sortOptions = {
            "修改时间 (从新到旧)", "修改时间 (从旧 to 新)", "标题 (A - Z)", "标题 (Z - A)", "优先级 (高 -> 低)", "优先级 (低 -> 高)"
    };

    private final String[] priorityOptions = {
            "重要且紧急", "重要不紧急", "紧急不重要", "不重要不紧急", "全部显示"
    };

    private final BroadcastReceiver minuteUpdateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_TIME_TICK.equals(intent.getAction())) syncNoteStatuses();
        }
    };

    private final BroadcastReceiver lanStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (LanServerService.ACTION_LAN_STATE_CHANGED.equals(intent.getAction())) invalidateOptionsMenu();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            applySavedNightMode();
            EdgeToEdge.enable(this);
            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());

            if (!checkDatabaseCompatibility()) return;

            aiAssistant = new AiAssistant(this);
            setSupportActionBar(binding.titleLayout.toolbar);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

            ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, 0, systemBars.right, 0);
                binding.bottomNavigation.setPadding(0, 0, 0, systemBars.bottom);
                return insets;
            });

            setupNavigation();
            initComponents();
            setupBottomNavigation(savedInstanceState);
            startAppStatusService();
            checkAndStartLanService();
            showGuideIfNeeded();

            IntentFilter lanFilter = new IntentFilter(LanServerService.ACTION_LAN_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(lanStateReceiver, lanFilter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(lanStateReceiver, lanFilter);
            }

        } catch (Exception e) { Log.e(TAG, "MainActivity 初始化失败", e); }
    }

    private void checkAndStartLanService() {
        if (PrefUtils.isLanServiceEnabled(this)) {
            Intent serviceIntent = new Intent(this, LanServerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
            else startService(serviceIntent);
            // 绑定服务以便获取设备列表
            bindService(serviceIntent, lanConnection, BIND_AUTO_CREATE);
        }
    }

    /**
     * 弹出原生对话框显示已连接的局域网设备信息
     */
    private void showConnectedDevicesDialog() {
        if (!isLanBound || lanService == null) {
            Toast.makeText(this, "正在初始化局域网服务...", Toast.LENGTH_SHORT).show();
            return;
        }

        List<LanServerService.ClientInfo> clients = lanService.getConnectedClients();
        if (clients.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("当前在线设备")
                    .setMessage("暂无设备连接至该服务")
                    .setPositiveButton("管理服务", (d, w) -> startActivity(new Intent(this, LanServiceActivity.class)))
                    .setNegativeButton("返回", null)
                    .show();
            return;
        }

        // 使用 ViewBinding 加载对话框自定义视图
        DialogConnectedClientsBinding dialogBinding = DialogConnectedClientsBinding.inflate(getLayoutInflater());
        dialogBinding.rvConnectedClients.setLayoutManager(new LinearLayoutManager(this));

        // 声明一个数组来持有对话框引用，以便在列表为空时关闭
        final androidx.appcompat.app.AlertDialog[] dialogHolder = new androidx.appcompat.app.AlertDialog[1];

        // 创建适配器并实现踢出逻辑
        ClientAdapter adapter = new ClientAdapter(clients, client -> {
            // 执行服务端踢出逻辑
            lanService.disconnectClient(client.deviceId);

            // 执行拉黑逻辑：构造黑名单规则并保存
            PrefUtils.BlacklistRule rule = new PrefUtils.BlacklistRule(
                    client.deviceName, client.deviceId, client.ip, client.os, client.browser);
            rule.blockDeviceId = true;

            List<PrefUtils.BlacklistRule> blacklist = PrefUtils.getLanBlacklist(this);
            blacklist.add(rule);
            PrefUtils.saveLanBlacklist(this, blacklist);

            Toast.makeText(this, "已踢出并拉黑设备：" + client.deviceName, Toast.LENGTH_SHORT).show();

            // 若所有设备都已断开，则自动关闭对话框
            if (lanService.getConnectedClients().isEmpty() && dialogHolder[0] != null) {
                dialogHolder[0].dismiss();
            }
        });

        dialogBinding.rvConnectedClients.setAdapter(adapter);

        dialogHolder[0] = new MaterialAlertDialogBuilder(this)
                .setTitle("已连接设备 (" + clients.size() + ")")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("管理服务", (d, w) -> startActivity(new Intent(this, LanServiceActivity.class)))
                .setNegativeButton("关闭", null)
                .show();
    }

    /**
     * 局域网在线客户端列表适配器
     */
    private static class ClientAdapter extends RecyclerView.Adapter<ClientAdapter.VH> {
        private final List<LanServerService.ClientInfo> list;
        private final OnKickListener listener;

        public ClientAdapter(List<LanServerService.ClientInfo> list, OnKickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemConnectedClientBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            LanServerService.ClientInfo client = list.get(position);
            holder.binding.tvDeviceName.setText(client.deviceName);
            holder.binding.tvIp.setText("IP: " + client.ip);
            holder.binding.tvEnv.setText(String.format("环境: %s | %s ", client.os, client.browser));
            holder.binding.tvDuration.setText(String.format("连接时长: %s", client.getDuration()));


            // 绑定踢出并拉黑按钮点击事件
            holder.binding.btnKick.setOnClickListener(v -> {
                int currentPos = holder.getAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION) {
                    listener.onKick(client);
                    list.remove(currentPos);
                    notifyItemRemoved(currentPos);
                    notifyItemRangeChanged(currentPos, list.size());
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        interface OnKickListener {
            void onKick(LanServerService.ClientInfo client);
        }

        static class VH extends RecyclerView.ViewHolder {
            ItemConnectedClientBinding binding;
            VH(ItemConnectedClientBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (currentFragment instanceof ScheduleFragment) ((ScheduleFragment) currentFragment).handleTouch(ev);
        return super.dispatchTouchEvent(ev);
    }

    private boolean checkDatabaseCompatibility() {
        try {
            db = AppDatabase.getDatabase(this);
            db.noteDao().getNoteCount();
            return true;
        } catch (Exception e) {
            new Handler(Looper.getMainLooper()).post(this::showRescueDialog);
            return false;
        }
    }

    private void showRescueDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("数据兼容性异常")
                .setMessage("由于应用升级，当前本地数据库架构已冲突。建议先导出全量数据备份，随后重置数据库以继续使用。")
                .setCancelable(false)
                .setPositiveButton("全量导出并重置", (dialog, which) -> performRescueExport())
                .setNegativeButton("丢弃旧数据", (dialog, which) -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("确认丢弃？")
                            .setMessage("此操作将永久抹除所有旧数据。")
                            .setPositiveButton("确定", (d, w) -> resetDatabaseAndRestart())
                            .setNegativeButton("返回", (d, w) -> showRescueDialog())
                            .show();
                })
                .show();
    }

    private void performRescueExport() {
        new Thread(() -> {
            try {
                android.database.sqlite.SQLiteDatabase rawDb = openOrCreateDatabase("secure_note_db", MODE_PRIVATE, null);
                com.google.gson.JsonObject rescueJson = new com.google.gson.JsonObject();
                android.database.Cursor tables = rawDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'", null);

                while (tables.moveToNext()) {
                    String tableName = tables.getString(0);
                    com.google.gson.JsonArray tableArray = new com.google.gson.JsonArray();
                    android.database.Cursor data = rawDb.rawQuery("SELECT * FROM " + tableName, null);
                    while (data.moveToNext()) {
                        com.google.gson.JsonObject row = new com.google.gson.JsonObject();
                        for (int i = 0; i < data.getColumnCount(); i++) row.addProperty(data.getColumnName(i), data.getString(i));
                        tableArray.add(row);
                    }
                    data.close();
                    rescueJson.add(tableName, tableArray);
                }
                tables.close(); rawDb.close();
                java.io.File backupFile = new java.io.File(getExternalFilesDir(null), "RescueBackup_" + System.currentTimeMillis() + ".json");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(backupFile)) {
                    fos.write(rescueJson.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, "救援数据已保存至：" + backupFile.getPath(), Toast.LENGTH_LONG).show();
                    resetDatabaseAndRestart();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show(); resetDatabaseAndRestart(); });
            }
        }).start();
    }

    private void resetDatabaseAndRestart() {
        deleteDatabase("secure_note_db");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
            finish();
        }, 1000);
    }

    private void setupBottomNavigation(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            notesFragment = (NotesFragment) getSupportFragmentManager().findFragmentByTag(TAG_NOTES);
            passwordsFragment = (PasswordsFragment) getSupportFragmentManager().findFragmentByTag(TAG_PASSWORDS);
            scheduleFragment = (ScheduleFragment) getSupportFragmentManager().findFragmentByTag(TAG_SCHEDULE);
        }
        if (notesFragment == null) notesFragment = new NotesFragment();
        if (passwordsFragment == null) passwordsFragment = new PasswordsFragment();
        if (scheduleFragment == null) scheduleFragment = new ScheduleFragment();

        if (savedInstanceState == null) {
            switchFragment(notesFragment, TAG_NOTES);
            binding.bottomNavigation.setSelectedItemId(R.id.navigation_notes);
            updateTitleBar(true);
        } else {
            int selectedId = binding.bottomNavigation.getSelectedItemId();
            if (selectedId == R.id.navigation_notes) { currentFragment = notesFragment; updateTitleBar(true); }
            else if (selectedId == R.id.navigation_passwords) { currentFragment = passwordsFragment; updateTitleBar(false); }
            else if (selectedId == R.id.navigation_schedule) { currentFragment = scheduleFragment; updateTitleBar(false); }
        }

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_schedule) { switchFragment(scheduleFragment, TAG_SCHEDULE); updateTitleBar(false); return true; }
            else if (itemId == R.id.navigation_notes) { switchFragment(notesFragment, TAG_NOTES); updateTitleBar(true); return true; }
            else if (itemId == R.id.navigation_passwords) { switchFragment(passwordsFragment, TAG_PASSWORDS); updateTitleBar(false); return true; }
            return false;
        });
    }

    private int getFragmentIndex(Fragment fragment) {
        if (fragment instanceof ScheduleFragment) return 0;
        if (fragment instanceof NotesFragment) return 1;
        if (fragment instanceof PasswordsFragment) return 2;
        return 0;
    }

    private void switchFragment(Fragment fragment, String tag) {
        if (currentFragment == fragment) return;
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (currentFragment != null) {
            int currentIndex = getFragmentIndex(currentFragment);
            int targetIndex = getFragmentIndex(fragment);
            if (targetIndex > currentIndex) transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
            else transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
            transaction.hide(currentFragment);
        }
        if (!fragment.isAdded()) transaction.add(R.id.nav_host_fragment, fragment, tag);
        else transaction.show(fragment);
        transaction.commit();
        currentFragment = fragment;
    }

    private void updateTitleBar(boolean isNotes) {
        if (binding.titleLayout == null) return;
        if (isNotes) {
            binding.titleLayout.poem.setVisibility(View.VISIBLE);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);
        } else {
            binding.titleLayout.poem.setVisibility(View.GONE);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(true);
                if (currentFragment instanceof PasswordsFragment) getSupportActionBar().setTitle(getString(R.string.title_passwords));
                else if (currentFragment instanceof ScheduleFragment) ((ScheduleFragment) currentFragment).refreshTitle();
            }
        }
        invalidateOptionsMenu();
    }

    public void updateScheduleTitle(int offset) {
        if (getSupportActionBar() == null) return;
        String title = offset == 0 ? "本周日程表" : (offset < 0 ? "上" + Math.abs(offset) + "周日程表" : "下" + offset + "周日程表");
        getSupportActionBar().setTitle(title);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem wifiItem = menu.findItem(R.id.action_wifi_service);
        MenuItem filterItem = menu.findItem(R.id.action_filter);
        MenuItem lockItem = menu.findItem(R.id.action_lock);
        if (wifiItem != null) wifiItem.setVisible(PrefUtils.isLanServiceEnabled(this));
        if (filterItem != null) filterItem.setVisible(currentFragment == notesFragment);
        if (lockItem != null) lockItem.setVisible((currentFragment == passwordsFragment) && PrefUtils.isEncryptionEnabled(this));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override public void onEnterSelectionMode() { binding.bottomNavigation.setVisibility(View.GONE); }
    @Override public void onExitSelectionMode() { binding.bottomNavigation.setVisibility(View.VISIBLE); }
    @Override public void onAiLongPressSetup(View view) { setupAiLongPress(view); }
    @Override public void checkSecondaryGuides() { checkSecondaryGuidesInternal(); }
    @Override public void onAuthenticationCancelled() { binding.bottomNavigation.setSelectedItemId(R.id.navigation_notes); switchFragment(notesFragment, TAG_NOTES); updateTitleBar(true); }

    private void showGuideIfNeeded() {
        if (!PrefUtils.isGuideShown(this)) {
            binding.guideOverlay.setVisibility(View.VISIBLE);
            startPulseAnimation(binding.guideAnchor);
            binding.guideOverlay.setOnClickListener(v -> {
                stopPulseAnimation(binding.guideAnchor);
                binding.guideOverlay.setVisibility(View.GONE);
                PrefUtils.setGuideShown(this, true);
                checkSecondaryGuidesInternal();
            });
        }
    }

    private void checkSecondaryGuidesInternal() {
        if (binding.guideOverlay.getVisibility() == View.VISIBLE) return;
        new Thread(() -> {
            int count = db.noteDao().getNoteCount();
            runOnUiThread(() -> {
                if (count >= 1 && !PrefUtils.isSelectionGuideShown(this)) {
                    binding.guideSelectionOverlay.setVisibility(View.VISIBLE); startPulseAnimation(binding.guideSelectionHighlight);
                    binding.guideSelectionOverlay.setOnClickListener(v -> { stopPulseAnimation(binding.guideSelectionHighlight); binding.guideSelectionOverlay.setVisibility(View.GONE); PrefUtils.setSelectionGuideShown(this, true); checkSecondaryGuidesInternal(); });
                } else if (count >= 3 && !PrefUtils.isFilterGuideShown(this)) {
                    binding.guideFilterOverlay.setVisibility(View.VISIBLE); startPulseAnimation(binding.guideFilterHighlight);
                    binding.guideFilterOverlay.setOnClickListener(v -> { stopPulseAnimation(binding.guideFilterHighlight); binding.guideFilterOverlay.setVisibility(View.GONE); PrefUtils.setFilterGuideShown(this, true); });
                }
            });
        }).start();
    }

    private void startPulseAnimation(View target) {
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(target, PropertyValuesHolder.ofFloat("scaleX", 1.1f), PropertyValuesHolder.ofFloat("scaleY", 1.1f));
        animator.setDuration(800); animator.setRepeatCount(ObjectAnimator.INFINITE); animator.setRepeatMode(ObjectAnimator.REVERSE); animator.start();
        target.setTag(animator);
    }

    private void stopPulseAnimation(View target) {
        Object animator = target.getTag();
        if (animator instanceof ObjectAnimator) ((ObjectAnimator) animator).cancel();
        target.setScaleX(1.0f); target.setScaleY(1.0f);
    }

    private void startAppStatusService() {
        Intent serviceIntent = new Intent(this, AppStatusService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
        else startService(serviceIntent);
    }

    private void applySavedNightMode() {
        int mode = PrefUtils.getNightMode(this);
        int nightMode = (mode == 1) ? AppCompatDelegate.MODE_NIGHT_NO : (mode == 2 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (notesFragment != null) notesFragment.applyPersonalization();
        registerReceiver(minuteUpdateReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        syncNoteStatuses(); applyCustomColors();
        if (titleController != null) titleController.startLifecycle();
        // 重新绑定局域网服务
        if (PrefUtils.isLanServiceEnabled(this)) {
            bindService(new Intent(this, LanServerService.class), lanConnection, BIND_AUTO_CREATE);
        }
    }

    private void applyCustomColors() {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true);
        int defaultColor = typedValue.data;
        int iconColor = PrefUtils.getIconMainColor(this, defaultColor);
        if (notesFragment != null && notesFragment.getWriteButton() != null) notesFragment.getWriteButton().setBackgroundTintList(ColorStateList.valueOf(iconColor));
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(minuteUpdateReceiver); } catch (Exception ignored) {}
        if (titleController != null) titleController.stopLifecycle();
        if (isLanBound) { unbindService(lanConnection); isLanBound = false; }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(lanStateReceiver); } catch (Exception ignored) {}
    }

    private void syncNoteStatuses() {
        if (notesFragment == null || notesFragment.getAdapter() == null) return;
        List<Note> notesSnapshot = new ArrayList<>(notesFragment.getAdapter().getNotes());
        new Thread(() -> {
            for (Note note : notesSnapshot) {
                String effective = NoteAdapter.calculateEffectiveImportance(this, note);
                if (!effective.equals(note.getEffectiveImportance())) { note.setEffectiveImportance(effective); db.noteDao().update(note); }
            }
            runOnUiThread(() -> { if (notesFragment != null) notesFragment.applyFilterAndSort(currentSortMode, currentPriorityFilter); });
        }).start();
    }

    private void setupAiLongPress(View writeButton) {
        writeButton.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isLongPressTriggered = false; v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(200).start();
                    longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_TIME); return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                    longPressHandler.removeCallbacks(longPressRunnable);
                    if (!isLongPressTriggered) v.performClick();
                    return true;
            }
            return false;
        });
    }

    private void vibrateFeedback() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(50);
        }
    }

    private final Runnable longPressRunnable = () -> { isLongPressTriggered = true; vibrateFeedback(); checkRecordPermissionAndStart(); };
    private void checkRecordPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 200);
        else startAiFlow();
    }

    private void startAiFlow() {
        cleanupSpeech(); if (aiSnackbar != null) aiSnackbar.dismiss();
        aiSnackbar = Snackbar.make(binding.main, "AI助手正在倾听...", Snackbar.LENGTH_INDEFINITE); aiSnackbar.show();
        startGlobalPulseAnimation();
        speechHelper = new SpeechHelper(this, new SpeechHelper.SpeechCallback() {
            @Override public void onResult(String text) { runOnUiThread(() -> { if (aiSnackbar != null) aiSnackbar.setText("AI助手正在整理笔记..."); }); processTextWithAi(text); }
            @Override public void onError(String error) { runOnUiThread(() -> { stopAiVisualEffects(); Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show(); cleanupSpeech(); }); }
        });
        speechHelper.startListening();
    }

    private void cleanupSpeech() { if (speechHelper != null) { speechHelper.destroy(); speechHelper = null; } }
    private void startGlobalPulseAnimation() {
        if (notesFragment == null || notesFragment.getWriteButton() == null) return;
        View writeButton = notesFragment.getWriteButton();
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(writeButton, PropertyValuesHolder.ofFloat("scaleX", 1.15f), PropertyValuesHolder.ofFloat("scaleY", 1.15f));
        pulseAnimator.setDuration(600); pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE); pulseAnimator.setRepeatMode(ObjectAnimator.REVERSE); pulseAnimator.start();
    }

    private void stopAiVisualEffects() {
        runOnUiThread(() -> {
            if (pulseAnimator != null) pulseAnimator.cancel();
            if (notesFragment != null && notesFragment.getWriteButton() != null) { View writeButton = notesFragment.getWriteButton(); writeButton.setScaleX(1.0f); writeButton.setScaleY(1.0f); }
            if (aiSnackbar != null) aiSnackbar.dismiss();
        });
    }

    private void processTextWithAi(String text) {
        aiAssistant.processNote(text, new AiAssistant.AiCallback() {
            @Override public void onSuccess(List<AiAssistant.NoteResult> results) { saveAiNotes(results); cleanupSpeech(); }
            @Override public void onError(String error) { stopAiVisualEffects(); runOnUiThread(() -> Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show()); cleanupSpeech(); }
        });
    }

    private void saveAiNotes(List<AiAssistant.NoteResult> results) {
        boolean needWeather = PrefUtils.isShowWeather(this), needLocation = PrefUtils.isShowLocation(this), needTimestamp = PrefUtils.isShowTimestamp(this);
        final String[] weatherSnap = {"未成功获取天气戳"}; final CountDownLatch latch = new CountDownLatch(needWeather ? 1 : 0);
        if (needWeather) {
            new Weather(this, new Weather.WeatherUpdateListener() {
                @Override public void onWeatherUpdate(String weather, String temp) { weatherSnap[0] = weather + " " + temp; latch.countDown(); }
                @Override public void onError(String message) { latch.countDown(); }
            }).startAutoUpdate();
            new Handler(Looper.getMainLooper()).postDelayed(latch::countDown, 800);
        }
        new Thread(() -> {
            try { latch.await(1, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            String realLocation = needLocation ? fetchCurrentLocationSync() : ""; long lastId = -1;
            for (AiAssistant.NoteResult result : results) {
                Note note = new Note(); note.setTitle(result.title); note.setContent(result.content); note.setTimestamp(needTimestamp ? System.currentTimeMillis() : 0);
                note.setImportance(result.importance); note.setReminderTime(result.dailyTime); note.setReminderDate(result.onceDate); note.setOnceReminderTime(result.onceTime);
                note.setReminderMinutes(result.advanceMinutes); note.setWeather(needWeather ? weatherSnap[0] : ""); note.setLocation(realLocation); note.setAiGenerated(true);
                long id = db.noteDao().insert(note); note.setId(id); lastId = id;
                SystemReminderHelper.addEnhancedReminder(this, note);
                if (result.reminderType == 1 && !result.dailyTime.isEmpty()) ReminderManager.setDailyReminder(this, id, result.title, result.dailyTime, result.advanceMinutes);
                else if (result.reminderType == 2 && !result.onceDate.isEmpty() && !result.onceTime.isEmpty()) ReminderManager.setOnceReminder(this, id, result.title, result.onceDate, result.onceTime, result.advanceMinutes);
            }
            final long finalLastId = lastId;
            runOnUiThread(() -> {
                stopAiVisualEffects(); String msg = results.size() > 1 ? "已创建 " + results.size() + " 条 AI 笔记" : "AI 笔记已保存";
                Snackbar.make(binding.main, msg, Snackbar.LENGTH_LONG).setAction("查看最后一条", v -> { Intent intent = new Intent(this, writeActivity.class); intent.putExtra("note_id", finalLastId); startActivity(intent); }).show();
                checkSecondaryGuidesInternal();
            });
        }).start();
    }

    private String fetchCurrentLocationSync() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return "";
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) {
                List<Address> addrs = new Geocoder(this, Locale.CHINA).getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                if (addrs != null && !addrs.isEmpty()) return addrs.get(0).getLocality() + "·" + addrs.get(0).getSubLocality();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void setupNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (notesFragment != null && notesFragment.isSelectionMode()) notesFragment.exitSelectionMode();
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); setEnabled(true); }
            }
        });
    }

    private void initComponents() {
        if (binding.titleLayout != null) titleController = new MainTitleController(binding.titleLayout);
        permissionHelper = new Permission(this, new Permission.PermissionCallback() {
            @Override public void onPermissionGranted() { handlePostLocationPermissionFlow(); }
            @Override public void onPermissionDenied() { handlePostLocationPermissionFlow(); }
        });
        permissionHelper.checkAndRequestLocationPermission();
    }

    private void handlePostLocationPermissionFlow() {
        if (isFirstLaunch) {
            if (titleController != null) titleController.init();
            new Handler(Looper.getMainLooper()).postDelayed(() -> permissionHelper.checkAndRequestBatteryOptimization(), 1000); isFirstLaunch = false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        binding.titleLayout.toolbar.post(() -> {
            View filterView = binding.titleLayout.toolbar.findViewById(R.id.action_filter);
            if (filterView != null) {
                filterView.setOnLongClickListener(v -> {
                    if (currentFragment == notesFragment && v.getId() == R.id.action_filter) { vibrateFeedback(); showPriorityFilterDialog(); return true; }
                    return false;
                });
            }
            View wifiView = binding.titleLayout.toolbar.findViewById(R.id.action_wifi_service);
            if (wifiView != null) {
                // 点击局域网图标显示已连接设备弹窗
                wifiView.setOnClickListener(v -> showConnectedDevicesDialog());
                wifiView.setOnLongClickListener(v -> false);
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) startActivity(new Intent(this, SettingsActivity.class));
        else if (id == R.id.action_filter) showSortDialog();
        else if (id == R.id.action_lock) performManualLock();
        else if (id == R.id.action_wifi_service) {
            // 点击局域网图标显示已连接设备弹窗
            showConnectedDevicesDialog();
            return true;
        }
        return true;
    }

    private void performManualLock() {
        if (passwordsFragment != null) passwordsFragment.lockVault();
        binding.bottomNavigation.setSelectedItemId(R.id.navigation_notes);
        switchFragment(notesFragment, TAG_NOTES); updateTitleBar(true);
        Toast.makeText(this, "密码本已锁定", Toast.LENGTH_SHORT).show();
    }

    private void showSortDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("选择排序方式")
                .setSingleChoiceItems(sortOptions, currentSortMode, (dialog, which) -> {
                    currentSortMode = which;
                    if (notesFragment != null) notesFragment.applyFilterAndSort(currentSortMode, currentPriorityFilter);
                    dialog.dismiss();
                }).show();
    }

    private void showPriorityFilterDialog() {
        int checkedItem = 4;
        for (int i = 0; i < priorityOptions.length; i++) {
            if (priorityOptions[i].equals(currentPriorityFilter) || (currentPriorityFilter.equals("全部") && priorityOptions[i].equals("全部显示"))) { checkedItem = i; break; }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("筛选特定优先级")
                .setSingleChoiceItems(priorityOptions, checkedItem, (dialog, which) -> {
                    currentPriorityFilter = priorityOptions[which].equals("全部显示") ? "全部" : priorityOptions[which];
                    if (notesFragment != null) notesFragment.applyFilterAndSort(currentSortMode, currentPriorityFilter);
                    dialog.dismiss();
                    Toast.makeText(this, currentPriorityFilter.equals("全部") ? "已显示全部笔记" : "只显示：" + currentPriorityFilter, Toast.LENGTH_SHORT).show();
                }).setNegativeButton("取消", null).show();
    }

    public int getCurrentSortMode() { return currentSortMode; }
    public String getCurrentPriorityFilter() { return currentPriorityFilter; }
}
