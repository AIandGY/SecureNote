package com.aigy.securenote.Database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface PasswordDao {
    @Insert
    long insert(PasswordEntry password);

    @Update
    void update(PasswordEntry password);

    @Delete
    void delete(PasswordEntry password);

    @Query("DELETE FROM passwords WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM passwords ORDER BY timestamp DESC")
    LiveData<List<PasswordEntry>> getAllPasswords();

    @Query("SELECT * FROM passwords ORDER BY timestamp DESC")
    List<PasswordEntry> getAllPasswordsSync();

    @Query("SELECT * FROM passwords WHERE id = :id")
    PasswordEntry getPasswordById(long id);

    /**
     * 根据标题、账号和密码查找记录，用于导入去重
     */
    @Query("SELECT * FROM passwords WHERE title = :title AND account = :account AND password = :password LIMIT 1")
    PasswordEntry findPassword(String title, String account, String password);
}