package com.arao.pomodoro;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

record MeetingSchedule(int id, String name, int minute, boolean weekdays,
                       int lead, boolean startAlert, boolean enabled, int revision,
                       String key, ZoneId zone, LocalDate date, int days) {
    static final ZoneId NY = ZoneId.of("America/New_York");
    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US);
    MeetingSchedule(int id, String name, int minute, boolean weekdays, int lead,
                    boolean startAlert, boolean enabled, int revision) {
        this(id, name, minute, weekdays, lead, startAlert, enabled, revision,
                id == 0 ? "daily-scrum" : "support-handover", NY, null, weekdays ? 31 : 127);
    }
    boolean preset() { return id < 2; }
    boolean configured() { return minute >= 0; }
    boolean occurs(LocalDate day) {
        if (!configured() || !enabled || (date != null && day.isBefore(date))) return false;
        return days == 0 ? day.equals(date) : (days & (1 << (day.getDayOfWeek().getValue() - 1))) != 0;
    }
    Instant start(LocalDate day) {
        return day.atTime(LocalTime.ofSecondOfDay(minute * 60L)).atZone(zone).toInstant();
    }
    Instant next(Instant now, int advance) {
        LocalDate day = now.atZone(zone).toLocalDate().minusDays(1);
        if (date != null && day.isBefore(date)) day = date;
        for (int i = 0; i < 10; i++, day = day.plusDays(1)) {
            if (!occurs(day)) continue;
            Instant trigger = start(day).minusSeconds(advance * 60L);
            if (trigger.isAfter(now)) return trigger;
        }
        return null;
    }
    static String brazilTime(Instant time) { return TIME.format(time.atZone(SP)); }
    static int nyMinute(LocalDate brazilDate, LocalTime brazilTime) {
        LocalTime time = brazilDate.atTime(brazilTime).atZone(SP).withZoneSameInstant(NY).toLocalTime();
        return time.getHour() * 60 + time.getMinute();
    }
}
