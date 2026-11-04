# P2-A Tasks / Remind — Titan 2 acceptance (candidate v8)

> Historical acceptance record for the build named below. Current release behavior and setup:
> [1.0.0 user guide](USER_GUIDE.md); current delivery state: [P2/P3 status](P2_P3_STATUS_20261103.md).

**Build lineage:** P1.2 #102 → P2-A foundation #103 → notification listener recovery #104 → Tasks/Remind candidate. Keep as draft until Android CI and Titan 2 device checks pass. Only one Full APK; sign locally on the owner's Mac.

## What is implemented

- TenSeven Tasks editor (title, details, due, reminder, priority, repeat, tags and optional linked Note ID), grouped list and complete/reschedule/delete.
- Remind view over the same private TaskStore. Both manual tasks and Hub-derived follow-ups appear here; no Calendar permission or external account required.
- From an expanded Hub notification, **Save follow-up** offers Later today, This evening, Tomorrow morning, Until complete, or Choose date/time. Saving does not remove the source notification or save its text, PendingIntent, sender, email address, or message body. Only generic label, package and SHA-256 of source key are retained. Re-saving active origin updates the same follow-up.
- Private on-device task files survive process death, relaunch and reboot; boot/time changes reschedule inexact AlarmManager alerts. Overdue items stay in-app regardless of Android 13+ POST_NOTIFICATIONS permission.
- A separately enabled optional Tasks launcher icon, with internal Tasks/Remind activity always accessible from Hub; disabling the icon never disables scheduler. Notes editor can create a task linked to its note ID. Existing `.note` files and format untouched.

## Test on Titan 2

1. In app settings enable the **Tasks** optional app icon, or open **Hub → More → Remind**. Create a task, set due date and a reminder, save and reopen. Confirm category grouping; check Complete / Tomorrow / Delete and that the task survives force-stop/relaunch.
2. Grant optional Android notification permission on Tasks/Remind, schedule a reminder ~15–30 minutes ahead, background the launcher and confirm a generic TenSeven notification arrives **approximately** at due time. Exact second guarantee is not promised; Android may defer inexact alarms.
3. Revoke POST_NOTIFICATIONS. Repeat with a short reminder and confirm item is visible as overdue in Remind without a system alert.
4. Enable Hub notification sources. Expand a WhatsApp/Signal/Mail conversation, choose **Save follow-up → Tomorrow morning**, then dismiss the original notification. Verify the Remind record remains after app force-stop and reboot. Verify no private message text is displayed in the task or lock screen.
5. Save the same active notification twice with different presets; verify one updated task. Try **Choose date/time**, cancel once (no record created), save once. Confirm task can be edited.
6. Open optional Notes, edit a note, choose **Create linked task / Remind**. Save and reopen; confirm linked note ID; verify original note remains unchanged.
7. Disable optional Notes/Tasks launcher icons, then use Hub → Remind and confirm tasks continue to exist and schedule. Test hardware keyboard Tab / arrows / Enter / Back and touchscreen on Titan 2.
8. Turn notification-listener permission off/on and verify Hub, Peek, lock counts recover as in #104. Verify wallpaper, BBOS7/10 theme and icon choice remain intact.

## Known gates / limitations

- Android CI, lint, Kotlin compile and Sonar analysis must pass against the exact integration head; no validated APK is attached to this branch yet.
- Android alarm scheduling is **inexact**. Force-stop from Settings suspends app execution until opened; reboot restoration requires the OS to allow BOOT_COMPLETED.
- Notification-origin follow-ups intentionally have a generic title; no encrypted notification snippet capture is implemented. Do not promise preservation of third-party actions.
- Background alerts are optional and never required for in-app Remind. No Google/WorkManager dependency is added.
- Full architectural extraction/lifecycle instrumentation and Sonar new-code gate tracked in #100 remain release gates. This candidate must **not** be called final P2-A acceptance until those gates pass.
