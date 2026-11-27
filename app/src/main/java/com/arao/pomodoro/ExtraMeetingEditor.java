package com.arao.pomodoro;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

final class ExtraMeetingEditor {
    private static final int[] LEADS = {0, 5, 10, 15};
    private static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    private final Activity activity;
    private final MeetingSchedule original;
    private final Consumer<List<MeetingSchedule>> saved;
    private final EditText name;
    private final Button dateButton, timeButton;
    private final CheckBox weekly, startAlert;
    private final CheckBox[] days = new CheckBox[7];
    private final Spinner zone, lead;
    private final View optionsView;
    private LocalDate date;
    private LocalTime time;
    private boolean saving;
    final AlertDialog dialog;

    ExtraMeetingEditor(Activity activity, MeetingSchedule original, Bundle draft,
                       Consumer<List<MeetingSchedule>> saved, Runnable dismissed) {
        this.activity = activity;
        this.original = original;
        this.saved = saved;
        ZonedDateTime suggested = ZonedDateTime.now(MeetingSchedule.SP).plusMinutes(15);
        date = draft != null ? LocalDate.parse(draft.getString("date"))
                : original != null ? original.date() : suggested.toLocalDate();
        time = draft != null ? LocalTime.ofSecondOfDay(draft.getInt("minute") * 60L)
                : original != null ? LocalTime.ofSecondOfDay(original.minute() * 60L)
                : suggested.toLocalTime().withSecond(0).withNano(0);

        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, padding);
        name = new EditText(activity);
        name.setSingleLine(true);
        name.setHint("Meeting name");
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        name.setText(draft != null ? draft.getString("name") : original == null ? "" : original.name());
        fields.addView(name);
        dateButton = new Button(activity);
        dateButton.setAllCaps(false);
        dateButton.setOnClickListener(v -> chooseDate());
        fields.addView(dateButton);
        timeButton = new Button(activity);
        timeButton.setAllCaps(false);
        timeButton.setOnClickListener(v -> new TimePickerDialog(activity, (picker, hour, minute) -> {
            time = LocalTime.of(hour, minute);
            updateLabels();
        }, time.getHour(), time.getMinute(), true).show());
        fields.addView(timeButton);

        LinearLayout options = new LinearLayout(activity);
        options.setOrientation(LinearLayout.VERTICAL);
        Button optionsButton = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        optionsButton.setAllCaps(false);
        optionsButton.setOnClickListener(v -> {
            boolean expanded = options.getVisibility() != View.VISIBLE;
            options.setVisibility(expanded ? View.VISIBLE : View.GONE);
            optionsButton.setText(expanded ? "Hide meeting options" : "Repeat, time zone and alerts");
        });
        fields.addView(optionsButton);
        fields.addView(options);
        boolean expanded = draft != null ? draft.getBoolean("options") : original != null;
        options.setVisibility(expanded ? View.VISIBLE : View.GONE);
        optionsButton.setText(expanded ? "Hide meeting options" : "Repeat, time zone and alerts");
        int mask = draft != null ? draft.getInt("days") : original == null ? 0 : original.days();
        weekly = new CheckBox(activity);
        weekly.setText("Repeat weekly");
        weekly.setChecked(draft != null ? draft.getBoolean("weekly") : mask != 0);
        options.addView(weekly);
        LinearLayout dayList = new LinearLayout(activity);
        dayList.setOrientation(LinearLayout.VERTICAL);
        options.addView(dayList);
        for (int i = 0; i < days.length; i++) {
            days[i] = new CheckBox(activity);
            days[i].setText(DAYS[i]);
            days[i].setChecked(mask == 0 ? i + 1 == date.getDayOfWeek().getValue() : (mask & (1 << i)) != 0);
            dayList.addView(days[i]);
        }
        dayList.setVisibility(weekly.isChecked() ? View.VISIBLE : View.GONE);
        weekly.setOnCheckedChangeListener((v, checked) -> {
            dayList.setVisibility(checked ? View.VISIBLE : View.GONE);
            updateLabels();
        });
        zone = spinner(options, "Time zone", new String[]{"São Paulo", "New York"});
        zone.setSelection(draft != null ? draft.getInt("zone")
                : original != null && original.zone().equals(MeetingSchedule.NY) ? 1 : 0);
        zone.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateLabels();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        startAlert = new CheckBox(activity);
        startAlert.setText("Alert at meeting start");
        startAlert.setChecked(draft != null ? draft.getBoolean("start") : original == null || original.startAlert());
        options.addView(startAlert);
        lead = spinner(options, "Advance reminder", new String[]{"Off", "5 minutes", "10 minutes", "15 minutes"});
        int leadIndex = 1;
        if (original != null) for (int i = 0; i < LEADS.length; i++) if (LEADS[i] == original.lead()) leadIndex = i;
        lead.setSelection(draft != null ? draft.getInt("lead") : leadIndex);

        updateLabels();
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(fields);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(original == null ? "Add meeting" : "Edit meeting").setView(scroll)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", null);
        if (original != null) builder.setNeutralButton("Delete", null);
        dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> save());
            if (original != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                        new AlertDialog.Builder(activity).setTitle("Delete meeting?")
                                .setMessage(original.name()).setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", (choice, which) -> submit(original, true)).show());
            }
        });
        dialog.setOnDismissListener(d -> dismissed.run());
        optionsView = options;
    }

    private Spinner spinner(LinearLayout parent, String label, String[] entries) {
        TextView caption = new TextView(activity);
        caption.setText(label);
        parent.addView(caption);
        Spinner spinner = new Spinner(activity);
        spinner.setContentDescription(label);
        spinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, entries));
        parent.addView(spinner);
        return spinner;
    }

    private void updateLabels() {
        LocalDate today = LocalDate.now(selectedZone());
        String day = date.equals(today) ? "Today"
                : date.equals(today.plusDays(1)) ? "Tomorrow"
                : date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US));
        dateButton.setText((weekly.isChecked() ? "Starts " : "") + day);
        timeButton.setText(MeetingSchedule.TIME.format(time));
    }

    private ZoneId selectedZone() {
        return zone.getSelectedItemPosition() == 0 ? MeetingSchedule.SP : MeetingSchedule.NY;
    }

    private void chooseDate() {
        new DatePickerDialog(activity, (picker, year, month, day) -> {
            date = LocalDate.of(year, month + 1, day);
            if (!weekly.isChecked()) {
                for (int i = 0; i < days.length; i++) days[i].setChecked(i + 1 == date.getDayOfWeek().getValue());
            }
            updateLabels();
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show();
    }

    private int dayMask() {
        int mask = 0;
        for (int i = 0; i < days.length; i++) if (days[i].isChecked()) mask |= 1 << i;
        return mask;
    }

    private void save() {
        String title = name.getText().toString().trim();
        if (title.isEmpty()) {
            name.setError("Enter a meeting name");
            name.requestFocus();
            return;
        }
        int mask = weekly.isChecked() ? dayMask() : 0;
        if (weekly.isChecked() && mask == 0) {
            Toast.makeText(activity, "Choose at least one day", Toast.LENGTH_SHORT).show();
            return;
        }
        int revision = original == null ? 1 : original.revision() + 1;
        MeetingSchedule meeting = new MeetingSchedule(original == null ? -1 : original.id(),
                title, time.getHour() * 60 + time.getMinute(), mask == 31,
                LEADS[lead.getSelectedItemPosition()], startAlert.isChecked(), true, revision,
                original == null ? UUID.randomUUID().toString() : original.key(), selectedZone(), date, mask);
        if (original == null && meeting.next(Instant.now(), 0) == null) {
            Toast.makeText(activity, "Choose a future meeting time", Toast.LENGTH_SHORT).show();
            return;
        }
        submit(meeting, false);
    }

    private void submit(MeetingSchedule meeting, boolean delete) {
        saving = true;
        dialog.setCancelable(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        if (original != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
        MeetingStore.save(activity, meeting, delete, meetings -> {
            dialog.dismiss();
            saved.accept(meetings);
        }, () -> {
            saving = false;
            dialog.setCancelable(true);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
            if (original != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
        });
    }

    Bundle draft() {
        if (saving) return null;
        Bundle data = new Bundle();
        data.putInt("id", original == null ? -1 : original.id());
        data.putString("date", date.toString());
        data.putInt("minute", time.getHour() * 60 + time.getMinute());
        data.putString("name", name.getText().toString());
        data.putInt("days", dayMask());
        data.putBoolean("weekly", weekly.isChecked());
        data.putInt("zone", zone.getSelectedItemPosition());
        data.putInt("lead", lead.getSelectedItemPosition());
        data.putBoolean("start", startAlert.isChecked());
        data.putBoolean("options", optionsView.getVisibility() == View.VISIBLE);
        return data;
    }
}
