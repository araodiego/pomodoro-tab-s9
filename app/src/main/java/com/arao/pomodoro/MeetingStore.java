package com.arao.pomodoro;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.List;

final class MeetingStore {
    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("meetings", Context.MODE_PRIVATE);
    }
    static MeetingSchedule load(Context context, int id) {
        SharedPreferences p = prefs(context);
        String k = id + ".";
        return new MeetingSchedule(id, id == 0 ? "Daily Scrum" : "Support Handover",
                p.getInt(k + "minute", -1), p.getBoolean(k + "weekdays", true),
                p.getInt(k + "lead", 5), p.getBoolean(k + "start", true),
                p.getBoolean(k + "enabled", true), p.getInt(k + "revision", 0));
    }
    static List<MeetingSchedule> all(Context c) { return Arrays.asList(load(c, 0), load(c, 1)); }

    @android.annotation.SuppressLint("ApplySharedPref")
    static void save(Context c, MeetingSchedule m) {
        String k = m.id() + ".";
        prefs(c).edit().putInt(k + "minute", m.minute()).putBoolean(k + "weekdays", m.weekdays())
            .putInt(k + "lead", m.lead()).putBoolean(k + "start", m.startAlert())
            .putBoolean(k + "enabled", m.enabled()).putInt(k + "revision", m.revision()).commit();
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
