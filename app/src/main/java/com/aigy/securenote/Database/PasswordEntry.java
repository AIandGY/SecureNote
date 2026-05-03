package com.aigy.securenote.Database;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 密码实体类
 * 优化：增加了加密状态标识符并设置了数据库默认值，确保迁移兼容性
 */
@Entity(tableName = "passwords")
public class PasswordEntry {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String title;
    private String account;
    private String password;
    private String note;
    private long timestamp;
    
    // 新增标识符：标识当前条目是否已处于加密状态
    // 0: 未加密 (明文), 1: 已加密 (密文)
    // 修复：指定 defaultValue = "0" 以解决 Room 迁移时 NOT NULL 约束导致的错误
    @ColumnInfo(defaultValue = "0")
    private int isEncrypted = 0;

    public PasswordEntry() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getIsEncrypted() { return isEncrypted; }
    public void setIsEncrypted(int isEncrypted) { this.isEncrypted = isEncrypted; }
}