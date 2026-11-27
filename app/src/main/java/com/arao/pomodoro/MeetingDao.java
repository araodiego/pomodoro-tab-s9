package com.arao.pomodoro;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface MeetingDao {
    @Query("SELECT * FROM meetings WHERE deleted = 0 ORDER BY id")
    List<MeetingRow> all();

    @Query("SELECT COALESCE(MAX(id), 1) + 1 FROM meetings")
    int nextId();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void importPresets(MeetingRow... presets);

    @Insert
    void insert(MeetingRow row);

    @Update
    int update(MeetingRow row);
}
