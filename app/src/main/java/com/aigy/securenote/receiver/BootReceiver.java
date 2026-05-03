package com.aigy.securenote.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.aigy.securenote.Database.AppDatabase;
import com.aigy.securenote.Database.Note;
import com.aigy.securenote.utils.ReminderManager;

import java.util.List;

/**
 * 监听手机开机广播，用于恢复之前设置的闹钟
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("BootReceiver", "手机开机，正在恢复笔记提醒...");
            
            // 在子线程中查询数据库并重新设置闹钟
            new Thread(() -> {
                AppDatabase db = AppDatabase.getDatabase(context);
                // 获取所有设置了提醒时间的笔记
                // 注意：这里需要你根据实际情况筛选，暂定查询所有
                List<Note> notes = db.noteDao().getAllNotesSync(); 
                if (notes != null) {
                    for (Note note : notes) {
                        if (note.getReminderTime() != null && !note.getReminderTime().isEmpty()) {
                            ReminderManager.setReminder(context, note.getId(), note.getTitle(), 
                                    note.getReminderTime(), note.getReminderMinutes());
                        }
                    }
                }
            }).start();
        }
    }
}
