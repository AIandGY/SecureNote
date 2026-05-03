package com.aigy.securenote.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.util.Log;
import android.widget.Toast;

import com.aigy.securenote.Database.Note;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class SystemReminderHelper {
    private static final String TAG = "SystemReminderHelper";

    /**
     * 添加增强提醒。仅支持在“准时提醒”时间生效。
     * 提前提醒禁止生效。
     */
    public static void addEnhancedReminder(Context context, Note note) {
        if (!PrefUtils.isEnhancedReminder(context)) return;

        // 核心修改：addEnhancedReminder 仅由 saveNote 在保存时调用一次。
        // 它会根据 note.getReminderTime() 或 note.getOnceReminderTime() 设置系统级闹钟。
        // 这里的逻辑始终是指向“准时”时间点。
        
        if (note.getReminderTime() != null && note.getReminderTime().contains(":")) {
            setDailySystemAlarm(context, note);
        } 
        else if (note.getReminderDate() != null && !note.getReminderDate().isEmpty() && 
                 note.getOnceReminderTime() != null && !note.getOnceReminderTime().isEmpty()) {
            setOnceSystemCalendarEvent(context, note);
        }
    }

    private static void setDailySystemAlarm(Context context, Note note) {
        try {
            String[] parts = note.getReminderTime().split(":");
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim());

            ArrayList<Integer> days = new ArrayList<>();
            for (int i = 1; i <= 7; i++) days.add(i);

            // 系统闹钟设置为准时时间
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, "笔记: " + note.getTitle())
                    .putExtra(AlarmClock.EXTRA_DAYS, days)
                    .putExtra(AlarmClock.EXTRA_VIBRATE, true)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            context.startActivity(intent);
            showToast(context, "系统每日闹钟已同步");
        } catch (Exception e) {
            Log.e(TAG, "设置每日闹钟失败", e);
        }
    }

    private static void setOnceSystemCalendarEvent(Context context, Note note) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date targetDate = sdf.parse(note.getReminderDate() + " " + note.getOnceReminderTime());
            if (targetDate == null) return;

            long targetMillis = targetDate.getTime();
            long calId = getPrimaryCalendarId(context);
            if (calId == -1) {
                showToast(context, "同步失败：找不到系统日历账户");
                return;
            }

            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.DTSTART, targetMillis);
            values.put(CalendarContract.Events.DTEND, targetMillis + 30 * 60 * 1000); 
            values.put(CalendarContract.Events.TITLE, "笔记: " + note.getTitle());
            values.put(CalendarContract.Events.DESCRIPTION, note.getContent());
            values.put(CalendarContract.Events.CALENDAR_ID, calId); 
            values.put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            values.put(CalendarContract.Events.HAS_ALARM, 1); 

            Uri uri = context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
            if (uri != null) {
                long eventId = Long.parseLong(uri.getLastPathSegment());
                ContentValues reminderValues = new ContentValues();
                reminderValues.put(CalendarContract.Reminders.EVENT_ID, eventId);
                // 准时提醒，提醒分钟设置为0
                reminderValues.put(CalendarContract.Reminders.MINUTES, 0);
                reminderValues.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                context.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI, reminderValues);
                
                showToast(context, "单次提醒已同步至日历");
            }
        } catch (SecurityException se) {
            showToast(context, "请先在个性化设置中开启日历权限");
        } catch (Exception e) {
            Log.e(TAG, "日历同步异常", e);
        }
    }

    private static long getPrimaryCalendarId(Context context) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, 
                    new String[]{CalendarContract.Calendars._ID}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return -1;
    }

    private static void showToast(Context context, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> 
            Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show()
        );
    }
}
