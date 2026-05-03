package com.aigy.securenote.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.aigy.securenote.MainActivity;

/**
 * 统一提醒接收器
 * 震动逻辑：每个提醒点震动 2 次（提前点 2 次 + 准时点 2 次 = 共 4 次）
 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "note_reminder_channel";
    private static final String TAG = "ReminderReceiver";

    // 简化后的震动模式：0ms延迟, 200ms震动, 100ms停顿, 200ms震动 (共2次)
    private static final long[] SIMPLE_VIBRATE_PATTERN = { 0, 200, 100, 200 };

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, ">>> 接收到提醒广播 <<<");
        
        long noteId = intent.getLongExtra("note_id", -1);
        String noteTitle = intent.getStringExtra("note_title");

        if (noteTitle == null || noteTitle.isEmpty()) {
            noteTitle = "您有一条笔记提醒";
        }

        // 1. 执行 2 次震动
        triggerVibration(context);
        
        // 2. 显示系统通知
        showNotification(context, noteId, noteTitle);
    }

    private void triggerVibration(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(SIMPLE_VIBRATE_PATTERN, -1));
            } else {
                vibrator.vibrate(SIMPLE_VIBRATE_PATTERN, -1);
            }
        }
    }

    private void showNotification(Context context, long noteId, String title) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "笔记重要提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.enableVibration(true);
            channel.setVibrationPattern(SIMPLE_VIBRATE_PATTERN);
            notificationManager.createNotificationChannel(channel);
        }

        Intent nextIntent = new Intent(context, MainActivity.class);
        nextIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, (int) noteId, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("笔记提醒")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setVibrate(SIMPLE_VIBRATE_PATTERN)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_SOUND);

        notificationManager.notify((int) noteId, builder.build());
    }
}
