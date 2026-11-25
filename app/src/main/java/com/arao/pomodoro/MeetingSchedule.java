package com.arao.pomodoro;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

record MeetingSchedule(int id, String name, int minute, boolean weekdays,
                       int lead, boolean startAlert, boolean enabled, int revision) {
    static final ZoneId NY = ZoneId.of("America/New_York");
    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US);
    boolean configured() { return minute >= 0; }
    boolean occurs(LocalDate day) {
        return configured() && enabled && (!weekdays || day.getDayOfWeek().getValue() <= 5);
    }
    Instant start(LocalDate day) {
        return day.atTime(LocalTime.ofSecondOfDay(minute * 60L)).atZone(NY).toInstant();
    }
    Instant next(Instant now, int advance) {
        LocalDate date = now.atZone(NY).toLocalDate().minusDays(1);
        for (int i = 0; i < 10; i++, date = date.plusDays(1)) {
            if (!occurs(date)) continue;
            Instant trigger = start(date).minusSeconds(advance * 60L);
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
