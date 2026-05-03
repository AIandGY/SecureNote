package com.aigy.securenote.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.aigy.securenote.receiver.ReminderReceiver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class ReminderManager {

    private static final String TAG = "ReminderManager";
    
    // 准时提醒的 RequestCode 偏移量，防止覆盖提前提醒
    private static final int ON_TIME_OFFSET = 1000000; 

    /**
     * 设置基于 HH:mm 格式的每日提醒（支持提前+准时双重闹钟）
     */
    public static void setDailyReminder(Context context, long noteId, String title, String timeStr, int advanceMinutes) {
        if (timeStr == null || timeStr.isEmpty()) return;

        String[] parts = timeStr.split(":");
        int hour = Integer.parseInt(parts[0].trim());
        int minute = Integer.parseInt(parts[1].trim());

        // 1. 计算准时时间点
        Calendar calendarOnTime = Calendar.getInstance();
        calendarOnTime.set(Calendar.HOUR_OF_DAY, hour);
        calendarOnTime.set(Calendar.MINUTE, minute);
        calendarOnTime.set(Calendar.SECOND, 0);
        calendarOnTime.set(Calendar.MILLISECOND, 0);

        if (calendarOnTime.getTimeInMillis() <= System.currentTimeMillis()) {
            calendarOnTime.add(Calendar.DAY_OF_YEAR, 1);
        }

        // 2. 设置准时闹钟
        setExactReminder(context, noteId + ON_TIME_OFFSET, title, calendarOnTime.getTimeInMillis());

        // 3. 设置提前闹钟（如果 advanceMinutes > 0）
        if (advanceMinutes > 0) {
            Calendar calendarAdvance = (Calendar) calendarOnTime.clone();
            calendarAdvance.add(Calendar.MINUTE, -advanceMinutes);
            
            // 只有当提前提醒的时间点还在未来时才设置
            if (calendarAdvance.getTimeInMillis() > System.currentTimeMillis()) {
                setExactReminder(context, noteId, title + " (提前" + advanceMinutes + "分钟)", calendarAdvance.getTimeInMillis());
            }
        }
    }

    /**
     * 设置指定日期时间的提醒（支持提前+准时双重闹钟）
     */
    public static void setOnceReminder(Context context, long noteId, String title, String dateStr, String timeStr, int advanceMinutes) {
        if (dateStr == null || dateStr.isEmpty() || timeStr == null || timeStr.isEmpty()) return;

        try {
            String[] dateParts = dateStr.split("-");
            String[] timeParts = timeStr.split(":");

            // 1. 计算准时时间点
            Calendar calendarOnTime = Calendar.getInstance();
            calendarOnTime.set(Calendar.YEAR, Integer.parseInt(dateParts[0].trim()));
            calendarOnTime.set(Calendar.MONTH, Integer.parseInt(dateParts[1].trim()) - 1);
            calendarOnTime.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dateParts[2].trim()));
            calendarOnTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(timeParts[0].trim()));
            calendarOnTime.set(Calendar.MINUTE, Integer.parseInt(timeParts[1].trim()));
            calendarOnTime.set(Calendar.SECOND, 0);
            calendarOnTime.set(Calendar.MILLISECOND, 0);

            // 2. 设置准时闹钟
            if (calendarOnTime.getTimeInMillis() > System.currentTimeMillis()) {
                setExactReminder(context, noteId + ON_TIME_OFFSET, title, calendarOnTime.getTimeInMillis());
            }

            // 3. 设置提前闹钟
            if (advanceMinutes > 0) {
                Calendar calendarAdvance = (Calendar) calendarOnTime.clone();
                calendarAdvance.add(Calendar.MINUTE, -advanceMinutes);
                if (calendarAdvance.getTimeInMillis() > System.currentTimeMillis()) {
                    setExactReminder(context, noteId, title + " (提前" + advanceMinutes + "分钟)", calendarAdvance.getTimeInMillis());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "解析日期时间失败: " + e.getMessage());
        }
    }

    /**
     * 统一底层设置闹钟方法
     */
    public static void setExactReminder(Context context, long requestId, String title, long triggerTimeMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        // 传递原始 note_id，确保点击通知能正确跳转
        long originalNoteId = requestId >= ON_TIME_OFFSET ? requestId - ON_TIME_OFFSET : requestId;
        intent.putExtra("note_id", originalNoteId);
        intent.putExtra("note_title", title);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, (int) requestId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMillis, pendingIntent);
            }
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Log.d(TAG, "闹钟设置成功 [" + requestId + "] -> " + title + " @ " + sdf.format(triggerTimeMillis));
        } catch (Exception e) {
            Log.e(TAG, "设置闹钟失败: " + e.getMessage());
        }
    }

    /**
     * 取消提醒（同时取消提前和准时的）
     */
    public static void cancelReminder(Context context, long noteId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        long[] idsToCancel = {noteId, noteId + ON_TIME_OFFSET};
        for (long id : idsToCancel) {
            Intent intent = new Intent(context, ReminderReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, (int) id, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        }
    }

    public static void setReminder(Context context, long noteId, String title, String timeStr, int advanceMinutes) {
        setDailyReminder(context, noteId, title, timeStr, advanceMinutes);
    }
}
