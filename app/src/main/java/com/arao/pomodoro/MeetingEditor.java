package com.arao.pomodoro;

import android.app.*;
import android.os.Bundle;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.function.Consumer;

final class MeetingEditor {
    private final Activity activity;
    private final MeetingSchedule original;
    private final CheckBox enabled, weekdays, start;
    private final Spinner lead;
    private final TimePicker picker;
    private final TextView preview;
    private final int[] leadValues = {0,5,10,15};
    private final LocalDate referenceDate;
    private boolean saving;
    final AlertDialog dialog;

    MeetingEditor(Activity activity,MeetingSchedule original,Bundle draft,
                  Consumer<List<MeetingSchedule>> saved,Runnable dismissed) {
        this.activity = activity;
        this.original = original;
        Instant now = Instant.now();
        // Use the date being edited explicitly, including the applicable DST offset.
        referenceDate = draft == null ? now.atZone(MeetingSchedule.SP).toLocalDate()
                : LocalDate.parse(draft.getString("date"));
        LocalTime time = original.configured()
                ? referenceDate.atTime(LocalTime.NOON).atZone(MeetingSchedule.SP).withZoneSameInstant(MeetingSchedule.NY)
                    .toLocalDate().atTime(LocalTime.ofSecondOfDay(original.minute()*60L))
                    .atZone(MeetingSchedule.NY).withZoneSameInstant(MeetingSchedule.SP).toLocalTime()
                : LocalTime.of(12,0);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20*activity.getResources().getDisplayMetrics().density);
        box.setPadding(padding,0,padding,padding);
        TextView label = new TextView(activity);
        label.setText(activity.getString(R.string.meeting_edit_date,referenceDate.toString()));
        label.setTextSize(16);
        box.addView(label);
        picker = new TimePicker(activity);
        picker.setIs24HourView(true);
        picker.setHour(draft == null ? time.getHour() : draft.getInt("hour"));
        picker.setMinute(draft == null ? time.getMinute() : draft.getInt("minute"));
        box.addView(picker);
        preview = new TextView(activity);
        preview.setTextSize(15);
        box.addView(preview);
        enabled = check(box,"Meeting enabled",draft == null ? original.enabled() : draft.getBoolean("enabled"));
        weekdays = check(box,"Monday–Friday only",draft == null ? original.weekdays() : draft.getBoolean("weekdays"));
        start = check(box,"Alert at meeting start",draft == null ? original.startAlert() : draft.getBoolean("start"));
        TextView leadLabel = new TextView(activity);
        leadLabel.setText(R.string.meeting_advance);
        box.addView(leadLabel);
        lead = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Off","5 minutes","10 minutes","15 minutes"});
        lead.setAdapter(adapter);
        int selected = 1;
        for (int i = 0; i < leadValues.length; i++) if (leadValues[i] == original.lead()) selected = i;
        lead.setSelection(draft == null ? selected : draft.getInt("lead"));
        box.addView(lead);
        picker.setOnTimeChangedListener((view,h,m) -> updatePreview());
        updatePreview();
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(box);
        dialog = new AlertDialog.Builder(activity).setTitle(original.name()).setView(scroll)
                .setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            picker.clearFocus();
            MeetingSchedule updated = new MeetingSchedule(original.id(), original.name(), nyMinute(),
                    weekdays.isChecked(), leadValues[lead.getSelectedItemPosition()], start.isChecked(),
                    enabled.isChecked(), original.revision() + 1);
            saving = true;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            MeetingStore.save(activity, updated, false, meetings -> {
                dialog.dismiss();
                saved.accept(meetings);
            }, () -> {
                saving = false;
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            });
        }));
        dialog.setOnDismissListener(d -> dismissed.run());
    }
    private CheckBox check(LinearLayout box,String label,boolean value) {
        CheckBox view = new CheckBox(activity);
        view.setText(label); view.setChecked(value);
        box.addView(view);
        return view;
    }
    private int nyMinute() {
        return MeetingSchedule.nyMinute(referenceDate, LocalTime.of(picker.getHour(),picker.getMinute()));
    }
    private void updatePreview() {
        LocalTime ny = LocalTime.ofSecondOfDay(nyMinute()*60L);
        ZonedDateTime now = referenceDate.atTime(LocalTime.of(picker.getHour(),picker.getMinute()))
                .atZone(MeetingSchedule.SP).withZoneSameInstant(MeetingSchedule.NY);
        java.time.zone.ZoneOffsetTransition transition = MeetingSchedule.NY.getRules().nextTransition(now.toInstant());
        String text = "New York: " + MeetingSchedule.TIME.format(ny) + " " + DateTimeFormatter.ofPattern("z",Locale.US).format(now);
        if (transition != null) {
            Instant after = transition.getInstant().plusSeconds(86400);
            ZonedDateTime shifted = after.atZone(MeetingSchedule.NY).toLocalDate().atTime(ny)
                    .atZone(MeetingSchedule.NY).withZoneSameInstant(MeetingSchedule.SP);
            text += "\nAfter next New York clock change: " + MeetingSchedule.TIME.format(shifted) + " São Paulo";
        }
        preview.setText(text);
    }
    Bundle draft() {
        if (saving) return null;
        picker.clearFocus();
        Bundle data = new Bundle();
        data.putInt("id",original.id()); data.putString("date",referenceDate.toString());
        data.putInt("hour",picker.getHour()); data.putInt("minute",picker.getMinute());
        data.putBoolean("weekdays",weekdays.isChecked()); data.putBoolean("enabled",enabled.isChecked());
        data.putBoolean("start",start.isChecked()); data.putInt("lead",lead.getSelectedItemPosition());
        return data;
    }
}
