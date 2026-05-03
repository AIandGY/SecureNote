package com.aigy.securenote.Database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NoteDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Note note);

    @Update
    void update(Note note);

    @Delete
    void delete(Note note);

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    LiveData<List<Note>> getAllNotes();

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    List<Note> getAllNotesSync(); 

    @Query("SELECT * FROM notes WHERE id = :id")
    Note getNoteById(long id);

    /**
     * 根据标题、内容和时间戳查找笔记，用于导入去重
     */
    @Query("SELECT * FROM notes WHERE title = :title AND content = :content AND timestamp = :timestamp LIMIT 1")
    Note findNote(String title, String content, long timestamp);

    @Query("DELETE FROM notes WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM notes")
    void deleteAllNotes();

    @Query("SELECT COUNT(*) FROM notes")
    int getNoteCount();
}
