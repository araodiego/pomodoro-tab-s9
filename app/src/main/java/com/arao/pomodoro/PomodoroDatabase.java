package com.arao.pomodoro;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = MeetingRow.class, version = 1, exportSchema = true)
public abstract class PomodoroDatabase extends RoomDatabase {
    public abstract MeetingDao meetings();
}
