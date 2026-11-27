package com.arao.pomodoro;

import java.time.*;
import java.util.List;

public final class MeetingPlannerTest {
    private static int checks;
    private static void check(boolean condition,String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
    private static Instant instant(String value) { return Instant.parse(value); }
    private static MeetingSchedule schedule(int minute,boolean weekdays,int lead,boolean enabled) {
        return new MeetingSchedule(0,"Meeting",minute,weekdays,lead,true,enabled,1);
    }
    public static void main(String[] args) {
        MeetingSchedule m = schedule(600,true,5,true);
        MeetingSchedule second = schedule(705,true,5,true);
        LocalDate winter = LocalDate.parse("2026-11-25"), summer = LocalDate.parse("2026-07-15");
        check(MeetingSchedule.brazilTime(m.start(winter)).equals("12:00"),"winter start");
        check(MeetingSchedule.brazilTime(m.start(summer)).equals("11:00"),"summer start");
        check(MeetingSchedule.brazilTime(second.start(winter)).equals("13:45"),"later winter start");
        check(MeetingSchedule.brazilTime(second.start(summer)).equals("12:45"),"later summer start");
        check(MeetingSchedule.nyMinute(winter,LocalTime.of(12,0)) == 600,"winter edit");
        check(MeetingSchedule.nyMinute(summer,LocalTime.of(11,0)) == 600,"summer edit");
        check(m.next(instant("2026-11-25T14:50:00Z"),5).equals(instant("2026-11-25T14:55:00Z")),"advance");
        check(m.next(instant("2026-11-25T14:56:00Z"),0).equals(instant("2026-11-25T15:00:00Z")),"start after warning");
        check(m.next(instant("2026-10-30T15:00:00Z"),0).equals(instant("2026-11-02T15:00:00Z")),"fall DST weekend");
        check(m.next(instant("2027-03-12T16:00:00Z"),0).equals(instant("2027-03-15T14:00:00Z")),"spring DST weekend");
        check(schedule(600,false,5,true).next(instant("2026-11-28T10:00:00Z"),0)
                .equals(instant("2026-11-28T15:00:00Z")),"weekends enabled");
        check(schedule(600,true,5,false).next(instant("2026-11-25T10:00:00Z"),0)==null,"disabled");
        check(schedule(-1,true,5,true).next(instant("2026-11-25T10:00:00Z"),0)==null,"unconfigured");
        Instant now = instant("2026-11-25T14:25:00Z");
        check(MeetingPlanner.today(List.of(second,m),now).get(0).meeting().minute()==600,"chronological ribbon");
        check(MeetingPlanner.next(List.of(second,m),now).meeting().minute()==600,"next meeting");
        check(MeetingPlanner.until(now,m.start(winter)).equals("in 35 min"),"readable time left");
        check(MeetingPlanner.today(List.of(m),instant("2026-11-28T14:25:00Z")).isEmpty(),"empty weekend");
        long occurrence = m.start(winter).toEpochMilli();
        check(MeetingPlanner.validReminder(m,false,occurrence,instant("2026-11-25T14:55:00Z")),"valid advance");
        check(MeetingPlanner.validReminder(m,true,occurrence,instant("2026-11-25T15:00:00Z")),"valid start");
        check(!MeetingPlanner.validReminder(m,true,occurrence,instant("2026-11-25T14:59:59Z")),"early rejected");
        check(!MeetingPlanner.validReminder(m,true,occurrence,instant("2026-11-25T15:01:00Z")),"stale rejected");
        check(!MeetingPlanner.validReminder(schedule(601,true,5,true),true,occurrence,instant("2026-11-25T15:00:00Z")),"edited rejected");
        check(!MeetingPlanner.validReminder(schedule(600,true,0,true),false,occurrence,instant("2026-11-25T14:55:00Z")),"advance disabled");
        check(!MeetingPlanner.validReminder(schedule(600,true,5,false),true,occurrence,instant("2026-11-25T15:00:00Z")),"meeting disabled");
        LocalDate later = LocalDate.parse("2026-12-15");
        MeetingSchedule oneOff = new MeetingSchedule(2, "Review", 30, false, 5, true, true, 1,
                "review", MeetingSchedule.SP, later, 0);
        Instant start = instant("2026-12-15T03:30:00Z");
        check(oneOff.next(now, 0).equals(start), "one-time meeting several weeks away");
        check(oneOff.next(start, 0) == null, "one-time meeting never repeats");
        check(MeetingPlanner.validReminder(oneOff, false, start.toEpochMilli(), start.minusSeconds(300)),
                "start and reminder use the meeting's time zone");
        check(MeetingPlanner.today(List.of(oneOff), start).size() == 1, "one-time meeting on My Day");
        MeetingSchedule weekly = new MeetingSchedule(3, "Weekly", 840, false, 5, true, true, 1,
                "weekly", MeetingSchedule.NY, LocalDate.parse("2027-03-08"), 1);
        check(weekly.next(instant("2027-03-08T18:00:00Z"), 0).equals(instant("2027-03-08T19:00:00Z")),
                "weekly meeting before US daylight saving time");
        check(weekly.next(instant("2027-03-08T19:00:00Z"), 0).equals(instant("2027-03-15T18:00:00Z")),
                "weekly meeting keeps its New York local time after DST");
        System.out.println("Meeting schedule checks passed: "+checks);
    }
}
