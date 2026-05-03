package com.aigy.securenote.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.Database.Note;
import com.aigy.securenote.MainActivity;
import com.aigy.securenote.R;
import com.aigy.securenote.Statusbar.Weather;
import com.aigy.securenote.utils.AiAssistant;
import com.aigy.securenote.utils.PrefUtils;
import com.aigy.securenote.utils.ReminderManager;
import com.aigy.securenote.utils.SpeechHelper;
import com.aigy.securenote.utils.SystemReminderHelper;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 前台录音服务
 * 负责在后台执行语音识别，并通过 AI 自动创建笔记。
 * 已优化：通知现在会以高优先级在屏幕顶部弹出显示。
 */
public class QuickRecordService extends Service {
    private static final String TAG = "QuickRecordService";
    // 更新渠道ID以确保高重要性设置生效
    private static final String CHANNEL_ID = "QuickRecordChannel_Urgent";
    private static final int NOTIFICATION_ID = 1001;

    private SpeechHelper speechHelper;
    private AiAssistant aiAssistant;
    private AppDatabase db;

    @Override
    public void onCreate() {
        super.onCreate();
        db = AppDatabase.getDatabase(this);
        aiAssistant = new AiAssistant(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 初始启动通知
        Notification notification = createNotification("正在启动录音...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            serviceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            startForeground(NOTIFICATION_ID, notification, serviceType);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        
        startListening();
        return START_NOT_STICKY;
    }

    /**
     * 开启百度语音识别
     */
    private void startListening() {
        speechHelper = new SpeechHelper(this, new SpeechHelper.SpeechCallback() {
            @Override
            public void onResult(String text) {
                // 当识别完成开始处理时，更新通知并确保弹出
                updateNotification("正在处理并整理笔记...");
                processTextWithAi(text);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(QuickRecordService.this, "录音失败: " + error, Toast.LENGTH_SHORT).show();
                stopSelf();
            }
        });
        
        speechHelper.startListening();
        updateNotification("正在倾听您的指令...");
        Toast.makeText(this, "已开始即时记录", Toast.LENGTH_SHORT).show();
    }

    private void processTextWithAi(String text) {
        aiAssistant.processNote(text, new AiAssistant.AiCallback() {
            @Override
            public void onSuccess(List<AiAssistant.NoteResult> results) {
                saveNotesWithContext(results);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(QuickRecordService.this, "AI 处理失败: " + error, Toast.LENGTH_SHORT).show();
                stopSelf();
            }
        });
    }

    private void saveNotesWithContext(List<AiAssistant.NoteResult> results) {
        boolean needWeather = PrefUtils.isShowWeather(this);
        boolean needLocation = PrefUtils.isShowLocation(this);
        boolean needTimestamp = PrefUtils.isShowTimestamp(this);

        final String[] weatherSnap = {"未成功获取天气戳"};
        final CountDownLatch latch = new CountDownLatch(needWeather ? 1 : 0);

        if (needWeather) {
            Weather wClient = new Weather(this, new Weather.WeatherUpdateListener() {
                @Override public void onWeatherUpdate(String weather, String temp) { 
                    weatherSnap[0] = weather + " " + temp; 
                    latch.countDown();
                }
                @Override public void onError(String message) {
                    latch.countDown();
                }
            });
            wClient.startAutoUpdate();
            new Handler(Looper.getMainLooper()).postDelayed(latch::countDown, 800);
        }

        new Thread(() -> {
            try {
                latch.await(1, TimeUnit.SECONDS); 
            } catch (InterruptedException ignored) {}

            String realLocation = needLocation ? fetchCurrentLocationSync() : "";
            
            for (AiAssistant.NoteResult result : results) {
                Note note = new Note();
                note.setTitle(result.title);
                note.setContent(result.content);
                note.setTimestamp(needTimestamp ? System.currentTimeMillis() : 0);
                note.setImportance(result.importance);
                note.setReminderTime(result.dailyTime);
                note.setReminderDate(result.onceDate);
                note.setOnceReminderTime(result.onceTime);
                note.setReminderMinutes(result.advanceMinutes);
                note.setWeather(needWeather ? weatherSnap[0] : ""); 
                note.setLocation(realLocation);
                note.setAiGenerated(true);

                long id = db.noteDao().insert(note);
                note.setId(id);
                SystemReminderHelper.addEnhancedReminder(this, note);
                
                if (result.reminderType == 1 && !result.dailyTime.isEmpty()) {
                    ReminderManager.setDailyReminder(this, id, result.title, result.dailyTime, result.advanceMinutes);
                } else if (result.reminderType == 2 && !result.onceDate.isEmpty() && !result.onceTime.isEmpty()) {
                    ReminderManager.setOnceReminder(this, id, result.title, result.onceDate, result.onceTime, result.advanceMinutes);
                }
            }
            stopSelf();
        }).start();
    }

    private String fetchCurrentLocationSync() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return "";
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc != null) {
                Geocoder g = new Geocoder(this, Locale.CHINA);
                List<Address> addrs = g.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                if (addrs != null && !addrs.isEmpty()) {
                    return addrs.get(0).getLocality() + "·" + addrs.get(0).getSubLocality();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "定位异常", e);
        }
        return "";
    }

    /**
     * 创建通知渠道
     * 重要性设为 IMPORTANCE_HIGH 以允许悬浮通知弹出
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, 
                    "即时记录提醒", 
                    NotificationManager.IMPORTANCE_HIGH); // 高重要性
            channel.setDescription("显示 AI 语音记录的处理状态");
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    /**
     * 构建通知对象
     * 优先级设为 PRIORITY_MAX 以支持在各版本系统弹出
     */
    private Notification createNotification(String content) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("即刻清单 - AI 正在工作")
                .setContentText(content)
                .setSmallIcon(R.drawable.icons_write) // 确保图标符合主题
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX) // 最高优先级
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setDefaults(Notification.DEFAULT_ALL) // 触发声音和震动，确保弹出
                .build();
    }

    private void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(content));
        }
    }

    @Override
    public void onDestroy() {
        if (speechHelper != null) speechHelper.destroy();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
