package com.arao.pomodoro;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import androidx.room.Room;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

final class MeetingStore {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static PomodoroDatabase database;

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("meetings", Context.MODE_PRIVATE);
    }
    private static MeetingSchedule legacy(Context context, int id) {
        SharedPreferences p = prefs(context);
        String k = id + ".";
        return new MeetingSchedule(id, id == 0 ? "Daily Scrum" : "Support Handover",
                p.getInt(k + "minute", -1), p.getBoolean(k + "weekdays", true),
                p.getInt(k + "lead", 5), p.getBoolean(k + "start", true),
                p.getBoolean(k + "enabled", true), p.getInt(k + "revision", 0));
    }
    private static MeetingDao dao(Context context) {
        if (database == null) {
            PomodoroDatabase opened = Room.databaseBuilder(context, PomodoroDatabase.class, "pomodoro.db").build();
            opened.meetings().importPresets(MeetingRow.from(legacy(context, 0)), MeetingRow.from(legacy(context, 1)));
            database = opened;
        }
        return database.meetings();
    }
    private static List<MeetingSchedule> all(MeetingDao dao) {
        List<MeetingSchedule> result = new ArrayList<>();
        for (MeetingRow row : dao.all()) result.add(row.schedule());
        return result;
    }
    static MeetingSchedule find(List<MeetingSchedule> schedules, int id) {
        for (MeetingSchedule meeting : schedules) if (meeting.id() == id) return meeting;
        return null;
    }
    static void read(Context c, Consumer<List<MeetingSchedule>> loaded, Runnable finished) {
        Context context = c.getApplicationContext();
        IO.execute(() -> {
            try {
                List<MeetingSchedule> meetings = all(dao(context));
                MAIN.post(() -> {
                    try { loaded.accept(meetings); }
                    finally { finished.run(); }
                });
            } catch (RuntimeException error) {
                Log.e("MeetingStore", "Could not read meetings", error);
                MAIN.post(finished);
            }
        });
    }
    static void save(Context c, MeetingSchedule meeting, boolean delete,
                     Consumer<List<MeetingSchedule>> saved, Runnable failed) {
        Context context = c.getApplicationContext();
        IO.execute(() -> {
            try {
                MeetingDao dao = dao(context);
                MeetingRow row = MeetingRow.from(meeting);
                row.deleted = delete;
                if (row.id < 0) {
                    row.id = dao.nextId();
                    dao.insert(row);
                } else if (dao.update(row) != 1) {
                    throw new IllegalStateException("Meeting no longer exists");
                }
                List<MeetingSchedule> meetings = all(dao);
                MAIN.post(() -> {
                    MeetingAlarms.cancel(context, row.id);
                    MeetingAlarms.refresh(context, meetings);
                    saved.accept(meetings);
                });
            } catch (RuntimeException error) {
                Log.e("MeetingStore", "Could not save meeting", error);
                MAIN.post(() -> {
                    Toast.makeText(context, "Could not save the meeting. Try again.", Toast.LENGTH_LONG).show();
                    failed.run();
                });
            }
        });
    }
    static String receipt(int id, boolean start, long occurrence) {
        return "delivered." + id + "." + start + "." + occurrence;
    }
    @android.annotation.SuppressLint("ApplySharedPref")
    static boolean claim(Context c, int id, boolean start, long occurrence) {
        SharedPreferences p = prefs(c);
        String key = receipt(id, start, occurrence);
        if (p.contains(key)) return false;
        SharedPreferences.Editor edit = p.edit().putLong(key, System.currentTimeMillis());
        long cutoff = System.currentTimeMillis() - 30L * 86400000;
        for (var entry : p.getAll().entrySet()) {
            if (entry.getKey().startsWith("delivered.") && entry.getValue() instanceof Long
                    && (Long) entry.getValue() < cutoff) edit.remove(entry.getKey());
        }
        return edit.commit();
    }
    static void pending(Context c, MeetingSchedule m, long occurrence) {
        prefs(c).edit().putLong("prompt." + m.id(), occurrence)
                .putInt("promptRevision." + m.id(), m.revision()).apply();
    }
    static long pending(Context c, MeetingSchedule m) {
        if (m == null) return 0;
        SharedPreferences p = prefs(c);
        long time = p.getLong("prompt." + m.id(), 0);
        long age = System.currentTimeMillis() - time;
        return m.enabled() && p.getInt("promptRevision." + m.id(), -1) == m.revision()
                && age >= 0 && age < 600000 ? time : 0;
    }
    static void acknowledge(Context c, int id, long occurrence) {
        if (prefs(c).getLong("prompt." + id, 0) == occurrence)
            prefs(c).edit().remove("prompt." + id).apply();
    }
}
