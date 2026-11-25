package com.arao.pomodoro;

import java.time.*;
import java.util.*;

final class MeetingPlanner {
    record Occurrence(MeetingSchedule meeting, Instant start) {}
    static List<Occurrence> today(List<MeetingSchedule> meetings, Instant now) {
        LocalDate today = now.atZone(MeetingSchedule.SP).toLocalDate();
        List<Occurrence> days = new ArrayList<>();
        for (MeetingSchedule meeting : meetings) {
            for (LocalDate day = today.minusDays(1); !day.isAfter(today.plusDays(1)); day = day.plusDays(1)) {
                if (!meeting.occurs(day)) continue;
                Instant start = meeting.start(day);
                if (start.atZone(MeetingSchedule.SP).toLocalDate().equals(today))
                    days.add(new Occurrence(meeting, start));
            }
        }
        days.sort(Comparator.comparing(Occurrence::start));
        return days;
    }
    static Occurrence next(List<MeetingSchedule> meetings, Instant now) {
        Occurrence next = null;
        for (MeetingSchedule meeting : meetings) {
            Instant start = meeting.next(now, 0);
            if (start != null && (next == null || start.isBefore(next.start())))
                next = new Occurrence(meeting, start);
        }
        return next;
    }
    static String until(Instant now, Instant start) {
        long mins = (Math.max(0, Duration.between(now, start).getSeconds()) + 59) / 60;
        if (mins < 60) return "in " + mins + " min";
        return "in " + mins / 60 + "h " + mins % 60 + "min";
    }
    static boolean validReminder(MeetingSchedule m, boolean start, long occurrence, Instant now) {
        if (!m.configured() || !m.enabled() || (start ? !m.startAlert() : m.lead() == 0)) return false;
        Instant when = Instant.ofEpochMilli(occurrence);
        LocalDate day = when.atZone(MeetingSchedule.NY).toLocalDate();
        if (!m.occurs(day) || !m.start(day).equals(when)) return false;
        long late = Duration.between(when.minusSeconds(start ? 0 : m.lead()*60L), now).toMillis();
        return late >= 0 && late < 60000;
    }
}
