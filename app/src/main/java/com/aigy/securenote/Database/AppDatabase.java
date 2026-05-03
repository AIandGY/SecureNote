package com.aigy.securenote.Database;

import android.content.Context;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * 优化后的数据库构建类
 * 修复：移除与自动迁移冲突的手动迁移对象，确保新字段 isEncrypted 正确创建
 */
@Database(
        entities = {Note.class, PasswordEntry.class},
        version = 2,
        exportSchema = true,
        autoMigrations = {
                // 核心：利用 Room 2.4+ 的自动迁移功能处理增加字段的逻辑
                // 只要在 PasswordEntry 中设置了 @ColumnInfo(defaultValue = "0")，Room 就会自动生成正确的 SQL
                @AutoMigration(from = 1, to = 2)
        }
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract NoteDao noteDao();
    public abstract PasswordDao passwordDao();

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "secure_note_db")
                            .allowMainThreadQueries()
                            // 修复：删除了 .addMigrations(MIGRATION)，避免手动空逻辑覆盖自动迁移
                            .fallbackToDestructiveMigrationOnDowngrade()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}