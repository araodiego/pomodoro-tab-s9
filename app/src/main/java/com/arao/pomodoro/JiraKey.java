package com.arao.pomodoro;

import java.util.Locale;

public final class JiraKey {
    private JiraKey() {}

    /** Number-only entry reuses the previously saved project prefix. */
    public static String normalize(String input, String previous) {
        String value = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
        if (value.matches("[0-9]+") && previous != null && previous.contains("-")) {
            value = previous.substring(0, previous.lastIndexOf('-')) + "-" + value;
        }
        return value.matches("[A-Z][A-Z0-9]{1,29}-[1-9][0-9]{0,9}") ? value : null;
    }
}
