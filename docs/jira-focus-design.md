# Current JIRA: three design options

Branch: `work/jira-focus`, based on the existing v0.2 timer. Option 1 is implemented in version 0.3-work. The other options remain design alternatives.

The work variant should make one current JIRA key easy to read while preserving the physical-style timer, manual five-minute break flow, and focus/break colors.

Public examples use `PROJECT-1234`. The selected work item and optional project prefix belong in local device preferences, rather than in committed examples or application defaults.

## 1. Top focus card — recommended starting point

Place a high-contrast card between the app header and the dial. Show a small CURRENT JIRA label, the full key in large type, and an edit affordance. Keep the timer and its buttons below.

- Strength: the key has its own clear location and can be read without competing with the countdown.
- Tradeoff: the card uses some vertical space, making the dial slightly smaller in short windows.
- Phone behavior: retain the card above the dial and wrap the controls as the existing app does.

## 2. Key inside the dial

Put the full key inside the cream center of the physical dial, above the countdown. Move the FOCUS/BREAK status below the countdown. Tapping the key opens the editor.

- Strength: the key and time are in the same place, with a compact overall layout.
- Tradeoff: the key and countdown share limited space; long keys need careful type sizing and accessibility treatment.
- Phone behavior: maintain the full key on one line when possible, providing a larger alternative presentation when text scaling makes it necessary.

## 3. Dedicated JIRA panel

Use a landscape split layout. A left panel shows a large current key and a clear change action. The right panel contains the physical dial and its controls.

- Strength: highest prominence for a tablet kept on a desk stand.
- Tradeoff: the timer becomes smaller and the landscape layout changes more substantially.
- Phone or narrow-window behavior: stack the JIRA panel above the timer.

## Shared interaction rules

1. Tapping the key or its edit control opens a small editor with one field and Save/Cancel actions.
2. Accept a full key and normalize it to uppercase. An optional locally configured project prefix can make number-only entry possible.
3. Store one current key locally and restore it after rotation, app restart, and timer phase changes.
4. Changing the JIRA preserves the running timer, its deadline, and the selected focus duration.
5. Keep the key visible in focus and break modes. During a break, label it RETURN TO to preserve context for the next focus session.
6. Preserve the existing green focus and blue break backgrounds. Maintain an explicit phase label alongside color.
7. Allow the timer to work before a key is set, using Set current JIRA as the empty-state action.

## Review before implementation

- Compare the three concept mockups in the conversation and choose one layout.
- Test readability at a normal tablet viewing distance and with larger system text.
- Check a long key, an empty key, editing during focus, a paused break, rotation, and a narrow phone window.
- Implement and validate the chosen option on this branch. Keep `main` on the personal timer until a deliberate merge is requested.
