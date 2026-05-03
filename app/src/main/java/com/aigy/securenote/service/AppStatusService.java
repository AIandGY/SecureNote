package com.aigy.securenote.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.aigy.securenote.MainActivity;
import com.aigy.securenote.R;

/**
 * 应用运行状态监测服务
 * 作用：在通知栏显示应用正在运行，随进程启动而显示，随进程结束而消失。
 */
public class AppStatusService extends Service {
    private static final String CHANNEL_ID = "AppStatusChannel";
    private static final int NOTIFICATION_ID = 2001;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("应用正常运行中")
                .setSmallIcon(R.drawable.icons_write) // 统一使用编辑图标
                .setContentIntent(pendingIntent)
                .setOngoing(true) // 设置为常驻
                .setPriority(NotificationCompat.PRIORITY_MIN) // 设为最低优先级，避免打扰用户
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();

        // 启动为前台服务
        startForeground(NOTIFICATION_ID, notification);
        
        return START_STICKY; // 如果被杀掉，系统会尝试重启服务
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "应用运行状态",
                    NotificationManager.IMPORTANCE_MIN // 在通知栏静默显示
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
