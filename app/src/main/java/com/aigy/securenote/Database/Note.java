package com.aigy.securenote.Database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;

@Entity(tableName = "notes")
public class Note {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String title;
    private String content;
    private long timestamp;
    private String importance;
    
    // 动态优先级（记录变更后的状态，不覆盖原始优先级）
    private String effectiveImportance;

    // 每日提醒
    private String reminderTime; 
    
    // 指定日期提醒
    private String reminderDate; // yyyy-MM-dd
    private String onceReminderTime; // HH:mm
    
    private int reminderMinutes;
    private String weather;     
    private String location;
    private boolean isAiGenerated; // 独立识别位

    public Note() {
    }

    @Ignore
    public Note(String title, String content, long timestamp, String importance, String reminderTime, int reminderMinutes, String weather, String location) {
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.importance = importance;
        this.reminderTime = reminderTime;
        this.reminderMinutes = reminderMinutes;
        this.weather = weather;
        this.location = location;
    }

    // Getter 和 Setter

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getImportance() { return importance; }
    public void setImportance(String importance) { this.importance = importance; }

    public String getEffectiveImportance() { return effectiveImportance; }
    public void setEffectiveImportance(String effectiveImportance) { this.effectiveImportance = effectiveImportance; }

    public String getReminderTime() { return reminderTime; }
    public void setReminderTime(String reminderTime) { this.reminderTime = reminderTime; }

    public String getReminderDate() { return reminderDate; }
    public void setReminderDate(String reminderDate) { this.reminderDate = reminderDate; }

    public String getOnceReminderTime() { return onceReminderTime; }
    public void setOnceReminderTime(String onceReminderTime) { this.onceReminderTime = onceReminderTime; }

    public int getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(int reminderMinutes) { this.reminderMinutes = reminderMinutes; }

    public String getWeather() { return weather; }
    public void setWeather(String weather) { this.weather = weather; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public boolean isAiGenerated() { return isAiGenerated; }
    public void setAiGenerated(boolean aiGenerated) { isAiGenerated = aiGenerated; }
}
