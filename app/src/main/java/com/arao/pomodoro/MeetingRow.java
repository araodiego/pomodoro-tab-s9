package com.arao.pomodoro;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import java.time.LocalDate;
import java.time.ZoneId;

@Entity(tableName = "meetings", indices = @Index(value = "id", unique = true))
public final class MeetingRow {
    @PrimaryKey @NonNull public String key = "";
    public int id;
    @NonNull public String name = "";
    @NonNull public String zone = "";
    public String date;
    public int minute, days, lead, revision;
    public boolean startAlert, enabled, deleted;

    static MeetingRow from(MeetingSchedule meeting) {
        MeetingRow row = new MeetingRow();
        row.key = meeting.key();
        row.id = meeting.id();
        row.name = meeting.name();
        row.zone = meeting.zone().getId();
        row.date = meeting.date() == null ? null : meeting.date().toString();
        row.minute = meeting.minute();
        row.days = meeting.days();
        row.lead = meeting.lead();
        row.revision = meeting.revision();
        row.startAlert = meeting.startAlert();
        row.enabled = meeting.enabled();
        return row;
    }

    MeetingSchedule schedule() {
        return new MeetingSchedule(id, name, minute, days == 31, lead, startAlert, enabled,
                revision, key, ZoneId.of(zone), date == null ? null : LocalDate.parse(date), days);
    }
}
